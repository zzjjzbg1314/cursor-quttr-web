package com.example.cursorquitterweb.musicmv.aimusic;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 显式启用的真实歌曲验收，使用已核验的本地音频，写入该歌曲的时间轴并回读。 */
@EnabledIfEnvironmentVariable(named="STORYAI_ALIGNMENT_LIVE",matches="true")
class LocalLyricsAlignmentLiveTest {
    @Test void alignsPersistsAndReusesRealSong() throws Exception {
        ObjectMapper mapper=new ObjectMapper();D1DatabaseClient db=new D1DatabaseClient(mapper);
        ReflectionTestUtils.setField(db,"enabled",true);
        ReflectionTestUtils.setField(db,"baseUrl","https://api.cloudflare.com/client/v4");
        ReflectionTestUtils.setField(db,"accountId",System.getenv("MUSIC_MV_CLOUDFLARE_ACCOUNT_ID"));
        ReflectionTestUtils.setField(db,"databaseId",System.getenv("MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID"));
        java.lang.reflect.Field secret=D1DatabaseClient.class.getDeclaredField("apiToken");secret.setAccessible(true);secret.set(db,System.getenv("MUSIC_MV_CLOUDFLARE_API_TOKEN"));
        AiMusicJobRepository repo=new AiMusicJobRepository(db);
        Map<String,Object> selected=mapper.readValue(new File(System.getenv("STORYAI_ALIGNMENT_CANDIDATE")),Map.class);
        String owner=(String)selected.get("user_id"),id=(String)selected.get("candidate_id");
        LocalLyricsAlignmentService worker=new LocalLyricsAlignmentService(db,repo,mock(MusicMvInputAssetStorageService.class),mock(SongArchiveStorageService.class),mapper) {
            @Override InputStream openAudio(String actualOwner,Map<String,Object> row) throws IOException {
                assertEquals(owner,actualOwner);assertEquals(id,row.get("candidate_id"));
                return Files.newInputStream(Paths.get(System.getenv("STORYAI_ALIGNMENT_AUDIO")));
            }
        };
        ReflectionTestUtils.setField(worker,"python",System.getenv("MUSIC_MV_ALIGNMENT_PYTHON"));
        ReflectionTestUtils.setField(worker,"script","scripts/lyrics_alignment/align.py");
        ReflectionTestUtils.setField(worker,"model",System.getenv("MUSIC_MV_ALIGNMENT_MODEL"));
        try {
            Map<String,Object> row=repo.ownedLyricsCandidate(owner,id);
            assertEquals("SONG_LYRICS_PROCESSING",worker.request(owner,row).getCode());
            assertEquals(1,db.query("SELECT candidate_id FROM music_mv_lyric_alignment WHERE status='pending'").getRows().size(),"真实验收不得领取其他歌曲任务");
            long start=System.nanoTime();worker.runOnce();
            assertEquals("completed",db.query("SELECT status FROM music_mv_lyric_alignment WHERE candidate_id=?",id).firstRow().get("status"));
            SongLyricsTimelineService timeline=new SongLyricsTimelineService(repo,mock(AiMusicProviderRegistry.class),mapper);
            ReflectionTestUtils.setField(timeline,"alignment",worker);
            Map<String,Object> first=timeline.get(owner,id),second=timeline.get(owner,id);
            assertEquals(first,second);assertFalse(((List<?>)first.get("cues")).isEmpty());
            Map<String,Object> report=new LinkedHashMap<>();report.put("elapsedSeconds",(System.nanoTime()-start)/1e9);
            report.put("cueCount",((List<?>)first.get("cues")).size());report.put("source",first.get("source"));report.put("cacheReused",true);
            mapper.writeValue(new File("/tmp/storyai-alignment-live-report.json"),report);
        } finally {worker.close();}
    }
}
