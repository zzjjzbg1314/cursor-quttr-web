package com.example.cursorquitterweb.musicmv.aimusic;

import com.example.cursorquitterweb.musicmv.repository.AiMusicJobRepository;
import com.example.cursorquitterweb.musicmv.support.RowUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 持久化登记的新歌曲在后台同步；失败退避重试，不发起付费生成。 */
@Service
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class AiMusicSongSyncService {
    private static final Logger LOG=LoggerFactory.getLogger(AiMusicSongSyncService.class);
    private final AiMusicJobRepository repository;
    private final SongArchiveStorageService storage;
    private final SongLyricsTimelineService lyrics;
    private final ObjectMapper mapper;
    private final AtomicBoolean busy=new AtomicBoolean();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"song-archive");t.setDaemon(true);return t;
    });
    public AiMusicSongSyncService(AiMusicJobRepository repository,SongArchiveStorageService storage,
                                 SongLyricsTimelineService lyrics,ObjectMapper mapper) {
        this.repository=repository;this.storage=storage;this.lyrics=lyrics;this.mapper=mapper;
    }
    @Scheduled(fixedDelayString="${music-mv.ai-music.archive.interval-ms:5000}",initialDelay=10000)
    public void schedule() {
        if(!busy.compareAndSet(false,true))return;
        try { worker.execute(()->{try {runOnce();} finally {busy.set(false);}}); }
        catch(RuntimeException e) {busy.set(false);throw e;}
    }
    void runOnce() {
        try {
            for(Map<String,Object> job:repository.pendingSongSync()) {
                String id=RowUtils.str(job,"job_id"),token=UUID.randomUUID().toString();
                if(!repository.claimSongSync(id,token))continue;
                boolean complete=false;
                try {complete=synchronize(job);} catch(Exception e) {LOG.warn("歌曲同步暂未完成：{}，{}",id,e.getClass().getSimpleName());}
                repository.finishSongSync(id,token,complete);
            }
        } catch(Exception e) { LOG.warn("歌曲同步任务等待重试：{}",e.getClass().getSimpleName()); }
    }
    boolean synchronize(Map<String,Object> job) throws Exception {
        List<Map<String,Object>> songs=repository.songSyncCandidates(RowUtils.str(job,"job_id"));
        if(songs.isEmpty())return false;
        boolean instrumental=mapper.readTree(RowUtils.str(job,"request_json")).path("instrumental").asBoolean(false);
        boolean complete=true;
        for(Map<String,Object> song:songs) {
            String id=RowUtils.str(song,"candidate_id");
            JsonNode raw=mapper.readTree(RowUtils.str(song,"raw_json")==null?"{}":RowUtils.str(song,"raw_json"));
            try {storage.audio(song);} catch(Exception e) {complete=false;failed(id,"audio",e);}
            if(raw==null || raw.path("storyaiCoverUrl").asText("").isEmpty()) {
                String url=RowUtils.str(song,"provider_image_url");
                if(url!=null && !url.trim().isEmpty()) {
                    try {storage.cover(song);} catch(Exception e) {complete=false;failed(id,"cover",e);}
                }
            }
            if(!instrumental) {
                try {lyrics.synchronize(RowUtils.str(job,"user_id"),id);}
                catch(Exception e) {complete=false;failed(id,"lyrics",e);}
            }
        }
        return complete;
    }
    private void failed(String id,String part,Exception e) {
        LOG.warn("歌曲同步分项等待重试：{}，{}，{}",id,part,e.getClass().getSimpleName());
    }
    @PreDestroy public void close() {worker.shutdownNow();}
}
