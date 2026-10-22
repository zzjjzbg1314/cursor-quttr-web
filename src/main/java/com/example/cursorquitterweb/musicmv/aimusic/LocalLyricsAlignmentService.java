package com.example.cursorquitterweb.musicmv.aimusic;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest.Asset;
import com.example.cursorquitterweb.musicmv.support.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** 持久化对齐任务：单工作进程、跨实例租约、源版本校验、失败停止，成功结果原子保存。 */
@Service
@ConditionalOnProperty(prefix="music-mv",name="enabled",havingValue="true")
public class LocalLyricsAlignmentService {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LocalLyricsAlignmentService.class);
    private final D1DatabaseClient db;
    private final AiMusicJobRepository songs;
    private final MusicMvInputAssetStorageService inputs;
    private final SongArchiveStorageService archive;
    private final ObjectMapper mapper;
    @Value("${MUSIC_MV_ALIGNMENT_PYTHON:}") private String python="";
    @Value("${MUSIC_MV_ALIGNMENT_SCRIPT:scripts/lyrics_alignment/align.py}") private String script;
    @Value("${MUSIC_MV_ALIGNMENT_MODEL:small}") private String model="small";
    @Value("${MUSIC_MV_SUNO_TIMELINE_TOKEN:}") private String sunoToken="";
    private boolean schemaReady;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"lyrics-alignment");t.setDaemon(true);return t;});
    private volatile Process activeProcess;
    public LocalLyricsAlignmentService(D1DatabaseClient db,AiMusicJobRepository songs,MusicMvInputAssetStorageService inputs,
                                       SongArchiveStorageService archive,ObjectMapper mapper) {
        this.db=db;this.songs=songs;this.inputs=inputs;this.archive=archive;this.mapper=mapper;
    }
    public boolean configured() {return python!=null&&!python.trim().isEmpty();}
    static String digest(String value) {try {return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));} catch(Exception e) {throw new IllegalStateException(e);}}
    String fingerprint(Map<String,Object> row) {
        return digest("alignment-ctc-anchors-v1\n"+model+"\n"+RowUtils.str(row,"storage_sha256")+"\n"+RowUtils.str(row,"lyrics"));
    }
    boolean current(Map<String,Object> row,JsonNode raw) {
        JsonNode meta=raw==null?null:raw.get("storyaiAlignmentV1");
        return meta==null || fingerprint(row).equals(meta.path("sourceHash").asText());
    }
    private synchronized void schema() {
        if(schemaReady)return;
        db.query("CREATE TABLE IF NOT EXISTS music_mv_lyric_alignment(candidate_id TEXT PRIMARY KEY,owner_id TEXT NOT NULL,source_hash TEXT NOT NULL,status TEXT NOT NULL,lease_token TEXT,lease_until TEXT,error_code TEXT,updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        schemaReady=true;
    }
    public void enqueueImported(String owner,Map<String,Object> result) {
        if(!configured() || !(result.get("candidates") instanceof List))return;
        for(Object value:(List<?>)result.get("candidates")) {
            if(!(value instanceof Map))continue;
            String id=String.valueOf(((Map<?,?>)value).get("candidateId"));
            Map<String,Object> row=songs.ownedLyricsCandidate(owner,id);
            if(row!=null&&eligible(row))enqueue(owner,row);
        }
    }
    boolean eligible(Map<String,Object> row) {
        String text=RowUtils.str(row,"lyrics"),sha=RowUtils.str(row,"storage_sha256");
        return configured()&&text!=null&&!text.trim().isEmpty()&&text.length()<=50000&&sha!=null&&sha.matches("[a-f0-9]{64}");
    }
    private Map<String,Object> enqueue(String owner,Map<String,Object> row) {
        schema();
        String id=RowUtils.str(row,"candidate_id"),hash=fingerprint(row);
        db.query("INSERT INTO music_mv_lyric_alignment(candidate_id,owner_id,source_hash,status) VALUES (?,?,?,'pending') "
                +"ON CONFLICT(candidate_id) DO UPDATE SET source_hash=excluded.source_hash,status='pending',lease_token=NULL,lease_until=NULL,error_code=NULL,updated_at=CURRENT_TIMESTAMP "
                +"WHERE music_mv_lyric_alignment.owner_id=excluded.owner_id AND music_mv_lyric_alignment.source_hash<>excluded.source_hash",id,owner,hash);
        return db.query("SELECT status,error_code FROM music_mv_lyric_alignment WHERE candidate_id=? AND owner_id=?",id,owner).firstRow();
    }
    public ApiException request(String owner,Map<String,Object> row) {
        if(!eligible(row))return failure("SONG_LYRICS_UNAVAILABLE",false);
        Map<String,Object> task=enqueue(owner,row);
        if(task!=null&&"failed".equals(RowUtils.str(task,"status")))return failure("SONG_LYRICS_ALIGNMENT_FAILED",false);
        return failure("SONG_LYRICS_PROCESSING",true);
    }
    static ApiException failure(String code,boolean retryable) {
        return new ApiException(HttpStatus.CONFLICT,code,"Song lyrics could not be synchronized. Please try again.",retryable,null);
    }
    @Scheduled(fixedDelayString="${music-mv.alignment.interval-ms:5000}",initialDelay=15000)
    public void schedule() {
        if(!configured()||!busy.compareAndSet(false,true))return;
        executor.execute(()->{try {runOnce();}catch(Exception error) { log.warn("歌词对齐队列暂不可用：{}",error.getClass().getSimpleName()); }finally {busy.set(false);}});
    }
    void runOnce() throws Exception {
        schema();
        List<Map<String,Object>> tasks=db.query("SELECT candidate_id,owner_id,source_hash FROM music_mv_lyric_alignment WHERE status='pending' OR (status='processing' AND lease_until<CURRENT_TIMESTAMP) ORDER BY updated_at LIMIT 1").getRows();
        for(Map<String,Object> task:tasks) {
            String id=RowUtils.str(task,"candidate_id"),owner=RowUtils.str(task,"owner_id"),hash=RowUtils.str(task,"source_hash"),token=UUID.randomUUID().toString();
            if(db.query("UPDATE music_mv_lyric_alignment SET status='processing',lease_token=?,lease_until=datetime('now','+20 minutes') WHERE candidate_id=? AND source_hash=? AND (status='pending' OR (status='processing' AND lease_until<CURRENT_TIMESTAMP)) RETURNING candidate_id",token,id,hash).firstRow()==null)continue;
            try {
                Map<String,Object> row=songs.ownedLyricsCandidate(owner,id);
                if(row==null||!hash.equals(fingerprint(row)))throw new IllegalArgumentException("Source changed");
                Map<String,Object> response=original(row);
                String source="suno-original";
                if(response==null) {response=align(owner,row);source="forced-alignment";}
                Map<String,Object> timeline=SongLyricsTimelineService.normalize(id,mapper.valueToTree(response));
                timeline.put("source",source);
                Map<String,Object> metadata=new LinkedHashMap<>();metadata.put("sourceHash",hash);metadata.put("source",source);
                metadata.put("engine",response.get("engine"));metadata.put("model",response.get("model"));metadata.put("quality",response.get("quality"));
                // 保存时再次校验源数据及租约，编辑过歌词或音频的旧任务不能覆盖新结果。
                db.batch(Arrays.asList(
                    D1Statement.of("UPDATE ai_music_candidates SET raw_json=json_set(CASE WHEN json_valid(raw_json) THEN raw_json ELSE '{}' END,'$.storyaiAlignedWordsV1',json(?),'$.storyaiLyricsTimelineV1',json(?),'$.storyaiAlignmentV1',json(?)) WHERE candidate_id=? AND lyrics=? AND storage_sha256=? AND job_id IN (SELECT job_id FROM ai_music_jobs WHERE user_id=?) AND EXISTS(SELECT 1 FROM music_mv_lyric_alignment WHERE candidate_id=? AND lease_token=? AND source_hash=?)",
                        mapper.writeValueAsString(((Map)response.get("data")).get("alignedWords")),mapper.writeValueAsString(timeline),mapper.writeValueAsString(metadata),id,RowUtils.str(row,"lyrics"),RowUtils.str(row,"storage_sha256"),owner,id,token,hash),
                    D1Statement.of("UPDATE music_mv_lyric_alignment SET status='completed',lease_until=NULL,lease_token=NULL,updated_at=CURRENT_TIMESTAMP WHERE candidate_id=? AND lease_token=?",id,token)));
            } catch(Exception e) {
                log.warn("歌词对齐失败，任务已停止：{}，{}",id,e.getClass().getSimpleName());
                db.query("UPDATE music_mv_lyric_alignment SET status='failed',error_code='SONG_LYRICS_ALIGNMENT_FAILED',lease_until=NULL,lease_token=NULL,updated_at=CURRENT_TIMESTAMP WHERE candidate_id=? AND lease_token=?",id,token);
            }
        }
    }
    Map<String,Object> original(Map<String,Object> row) {
        String request=RowUtils.str(row,"import_request_id");
        if(request==null||!request.matches("suno:[a-fA-F0-9-]{36}"))return null;
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(10000);factory.setReadTimeout(10000);
        HttpHeaders headers=new HttpHeaders();if(sunoToken!=null&&!sunoToken.isEmpty())headers.setBearerAuth(sunoToken);
        try {
            String body=new RestTemplate(factory).exchange("https://studio-api.prod.suno.com/api/gen/"+request.substring(5)+"/aligned_lyrics/v2/",HttpMethod.GET,new HttpEntity<>(headers),String.class).getBody();
            JsonNode words=mapper.readTree(body).path("aligned_words");
            Map<String,Object> response=new LinkedHashMap<>();response.put("code",200);response.put("data",Collections.singletonMap("alignedWords",words));
            SongLyricsTimelineService.normalize(RowUtils.str(row,"candidate_id"),mapper.valueToTree(response));
            return response;
        } catch(Exception unavailable) {return null;}
    }
    Map<String,Object> align(String owner,Map<String,Object> row) throws Exception {
        Path dir=Files.createTempDirectory("storyai-align-");
        try {
            Path audio=dir.resolve("audio"),lyrics=dir.resolve("lyrics.txt"),output=dir.resolve("result.json");
            try(InputStream in=openAudio(owner,row);OutputStream out=Files.newOutputStream(audio)) {
                MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long size=0;int n;
                while((n=in.read(buffer))!=-1) {size+=n;if(size>100L*1024*1024)throw new IOException("Audio too large");digest.update(buffer,0,n);out.write(buffer,0,n);}
                if(size!=RowUtils.lng(row,"storage_size_bytes")||!hex(digest.digest()).equals(RowUtils.str(row,"storage_sha256")))throw new IOException("Audio integrity mismatch");
            }
            Files.write(lyrics,RowUtils.str(row,"lyrics").getBytes(StandardCharsets.UTF_8));
            Process process=new ProcessBuilder(python,Paths.get(script).toAbsolutePath().toString(),"--audio",audio.toString(),"--lyrics",lyrics.toString(),"--output",output.toString(),"--model",model)
                    .redirectOutput(dir.resolve("worker.log").toFile()).redirectErrorStream(true).start();
            activeProcess=process;
            try {if(!process.waitFor(15,TimeUnit.MINUTES)||process.exitValue()!=0)throw new IOException("Alignment worker failed");}
            finally {process.destroyForcibly();activeProcess=null;}
            if(Files.size(output)>4*1024*1024)throw new IOException("Alignment output too large");
            Map<String,Object> result=mapper.readValue(Files.readAllBytes(output),Map.class);
            SongLyricsTimelineService.normalize(RowUtils.str(row,"candidate_id"),mapper.valueToTree(result));
            return result;
        } finally {try(java.util.stream.Stream<Path> files=Files.walk(dir)){files.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}}
    }
    InputStream openAudio(String owner,Map<String,Object> row) throws IOException {
        if(!"upload".equals(RowUtils.str(row,"provider_code")))return archive.openArchivedAudio(row);
        Asset asset=new Asset();asset.setUrl(RowUtils.str(row,"storage_url"));asset.setSha256(RowUtils.str(row,"storage_sha256"));
        asset.setSizeBytes(RowUtils.lng(row,"storage_size_bytes"));asset.setFileName(RowUtils.str(row,"storage_file_name"));asset.setContentType(RowUtils.str(row,"storage_content_type"));
        return inputs.openOwnedMusic(owner,asset);
    }
    private static String hex(byte[] bytes) {StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    @PreDestroy public void close() {executor.shutdownNow();Process p=activeProcess;if(p!=null)p.destroyForcibly();}
}
