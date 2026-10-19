package com.example.cursorquitterweb.musicmv.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import java.time.Duration;
import java.util.LinkedHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import com.example.cursorquitterweb.musicmv.aimusic.AiMusicGenerationService;
import com.example.cursorquitterweb.musicmv.support.ApiException;
import com.example.cursorquitterweb.musicmv.support.RowUtils;

@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class MusicMvSongImportService {
    private final MusicMvUserAssetService assets;
    private final MusicMvInputAssetStorageService storage;
    private final D1DatabaseClient d1;
    private final AiMusicGenerationService songs;
    private final R2StorageService r2;
    private final AiMusicJobRepository repository;
    public MusicMvSongImportService(MusicMvUserAssetService assets, MusicMvInputAssetStorageService storage,
                                    D1DatabaseClient d1, AiMusicGenerationService songs, R2StorageService r2, AiMusicJobRepository repository) {
        this.assets=assets; this.storage=storage; this.d1=d1; this.songs=songs; this.r2=r2; this.repository=repository;
    }
    Map<String,Object> upload(String userId, String requestId, String fileName, long size, double duration,
                                    InputStream input, String baseUrl) throws IOException {
        String lower=fileName==null?"":fileName.toLowerCase(Locale.ROOT);
        boolean wav=lower.endsWith(".wav"), mp3=lower.endsWith(".mp3"), m4a=lower.endsWith(".m4a");
        if ((!wav&&!mp3&&!m4a)||size<=0||size>100L*1024*1024||!Double.isFinite(duration)||duration<=0||duration>900)
            throw invalid();
        if (!storage.isCloudStorageConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "CLOUD_INPUT_STORAGE_REQUIRED", "Secure cloud storage is temporarily unavailable", true, null);
        PushbackInputStream buffered=new PushbackInputStream(input,12);
        byte[] header=new byte[12]; int count=0, read;
        while (count<header.length&&(read=buffered.read(header,count,header.length-count))!=-1) count+=read;
        buffered.unread(header,0,count);
        boolean validWav=count>=12&&new String(header,0,4,StandardCharsets.US_ASCII).equals("RIFF")
                &&new String(header,8,4,StandardCharsets.US_ASCII).equals("WAVE");
        boolean validMp3=count>=3&&((header[0]=='I'&&header[1]=='D'&&header[2]=='3')
                ||((header[0]&255)==255&&(header[1]&224)==224&&(header[1]&6)!=0&&(header[2]&240)!=240));
        boolean validM4a=count>=12&&new String(header,4,4,StandardCharsets.US_ASCII).equals("ftyp");
        if (wav?!validWav:m4a?!validM4a:!validMp3) throw invalid();
        Map<String,Object> asset=assets.upload(userId,null,"music",fileName,wav?"audio/wav":m4a?"audio/mp4":"audio/mpeg",size,buffered,baseUrl);
        String assetId=RowUtils.str(asset,"assetId");
        String stable=UUID.nameUUIDFromBytes((userId+":"+requestId).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
        String job="amj_"+stable, attempt="ama_"+stable, candidate="amc_"+stable;
        String title=fileName.replaceAll("(?i)\\.(mp3|wav|m4a)$","");
        title=title.substring(0,Math.min(title.length(),160));
        // 上传记录复用歌曲选择契约；不调用生成服务、不记作曲消费，原子写入确保重试安全。
        d1.batch(Arrays.asList(
            D1Statement.of("INSERT OR IGNORE INTO ai_music_jobs (job_id,user_id,client_id,request_id,status,stage,progress,primary_provider_code,active_attempt_id,selected_candidate_id,request_fingerprint,request_json,retryable,created_at,updated_at,completed_at,provider_synced_at) VALUES (?,?,?,?,'completed','candidate_selected',1,'upload',?,?,?,'{}',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", job,userId,userId,requestId,attempt,candidate,RowUtils.str(asset,"sha256")),
            D1Statement.of("INSERT OR IGNORE INTO ai_music_provider_attempts (attempt_id,job_id,provider_code,provider_task_id,status,attempt_number,request_json,submission_unknown,created_at,updated_at,completed_at) VALUES (?,?,'upload',?,'completed',1,'{}',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",attempt,job,assetId),
            D1Statement.of("INSERT OR IGNORE INTO ai_music_candidates (candidate_id,job_id,attempt_id,provider_code,provider_task_id,provider_audio_id,status,title,duration_seconds,storage_key,storage_url,storage_sha256,storage_size_bytes,storage_file_name,storage_content_type,selected,raw_json,created_at,updated_at) VALUES (?,?,?,'upload',?,?,'stored',?,?,?,?,?,?,?,?,1,'{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",candidate,job,attempt,assetId,assetId,title,Double.valueOf(duration),assetId,asset.get("url"),asset.get("sha256"),asset.get("sizeBytes"),asset.get("fileName"),asset.get("contentType"))
        ));
        return songs.get(userId,job,false);
    }
    public Map<String,Object> prepare(String userId,String fileName,long size) {
        validateFile(fileName,size);
        if (!r2.isConfigured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CLOUD_INPUT_STORAGE_REQUIRED","Secure cloud storage is temporarily unavailable",true,null);
        String key=prefix(userId)+System.currentTimeMillis()+"_"+UUID.randomUUID().toString().replace("-","")+ extension(fileName);
        String contentType=key.endsWith(".wav")?"audio/wav":key.endsWith(".m4a")?"audio/mp4":"audio/mpeg";
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("objectKey",key); result.put("contentType",contentType); result.put("expiresAt",Long.valueOf(System.currentTimeMillis()+900000));
        result.put("uploadUrl",r2.presignedPutUrl(key,contentType,size,java.util.Collections.emptyMap(),Duration.ofMinutes(15)));
        return result;
    }
    public Map<String,Object> complete(String userId,String key,String fileName,double duration,String baseUrl) throws IOException {
        if (key==null||!key.startsWith(prefix(userId))||!key.substring(prefix(userId).length()).matches("[0-9]{13}_[a-f0-9]{32}\\.(mp3|wav|m4a)")) throw invalid();
        String requestId="import:"+key;
        Map<String,Object> existing=repository.byClientRequest(userId,requestId);
        if(existing!=null&&!existing.isEmpty()) return songs.get(userId,RowUtils.str(existing,"job_id"),false);
        long created=Long.parseLong(key.substring(prefix(userId).length()).split("_")[0]);
        if(System.currentTimeMillis()-created>3600000||created>System.currentTimeMillis()+60000) throw invalid();
        long size=r2.size(key); validateFile(fileName,size);
        if(!key.endsWith(extension(fileName))) throw invalid();
        Map<String,Object> result;
        try(InputStream input=r2.openRange(key,0,size-1)) { result=upload(userId,requestId,fileName,size,duration,input,baseUrl); }
        try { r2.delete(key); } catch(RuntimeException ignored) { /* 遗留暂存文件由定时清理回收。 */ }
        return result;
    }
    private String extension(String fileName) { return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT); }
    private String prefix(String userId) { return "music-mv-imports/"+UUID.nameUUIDFromBytes(userId.getBytes(StandardCharsets.UTF_8)).toString().replace("-","")+"/"; }
    private void validateFile(String fileName,long size) {
        if(fileName==null||!(fileName.toLowerCase(Locale.ROOT).endsWith(".mp3")||fileName.toLowerCase(Locale.ROOT).endsWith(".wav")||fileName.toLowerCase(Locale.ROOT).endsWith(".m4a"))||size<=0||size>100L*1024*1024) throw invalid();
    }
    @Scheduled(cron="${music-mv.render.import-cleanup-cron:0 45 3 * * ?}")
    public void cleanup() {
        if(!r2.isConfigured())return;
        for(String key:r2.listKeys("music-mv-imports/")) {
            String name=key.substring(key.lastIndexOf('/')+1);
            if(name.matches("[0-9]{13}_[a-f0-9]{32}\\.(mp3|wav|m4a)")&&System.currentTimeMillis()-Long.parseLong(name.split("_")[0])>86400000) r2.delete(key);
        }
    }
    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST,"MUSIC_UPLOAD_INVALID",
            "Choose a playable MP3, WAV or M4A file up to 100 MB and 15 minutes."); }
}
