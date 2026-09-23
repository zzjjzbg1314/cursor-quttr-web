package com.example.cursorquitterweb.musicmv.aimusic;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.util.UriComponentsBuilder;
import com.example.cursorquitterweb.musicmv.service.*;
import com.example.cursorquitterweb.musicmv.dto.MusicMvRenderJobCreateRequest;
import com.example.cursorquitterweb.musicmv.support.*;

@Service
@ConditionalOnProperty(prefix = "music-mv", name = "enabled", havingValue = "true")
public class AiVoiceService {
    private final D1DatabaseClient d1;
    private final AiMusicProviderRegistry providers;
    private final MusicMvInputAssetStorageService storage;
    private volatile boolean initialized;
    private final com.github.benmanes.caffeine.cache.Cache<String, Map<String,Object>> polling =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(1000)
                    .expireAfterWrite(java.time.Duration.ofSeconds(3)).build();
    public AiVoiceService(D1DatabaseClient d1, AiMusicProviderRegistry providers, MusicMvInputAssetStorageService storage) {
        this.d1 = d1; this.providers = providers; this.storage = storage;
    }
    // 新增独立表采用幂等初始化，已有音乐和渲染表不受影响。
    private synchronized void init() {
        if (initialized) return;
        d1.query("CREATE TABLE IF NOT EXISTS ai_music_voices (id TEXT PRIMARY KEY,user_id TEXT NOT NULL,name TEXT NOT NULL,task_id TEXT,status TEXT NOT NULL,phase TEXT NOT NULL,phrase TEXT,voice_id TEXT,error_message TEXT,created_at TEXT DEFAULT CURRENT_TIMESTAMP,updated_at TEXT DEFAULT CURRENT_TIMESTAMP)");
        d1.query("CREATE INDEX IF NOT EXISTS idx_music_voices_owner ON ai_music_voices(user_id,created_at)");
        initialized = true;
    }
    private SunoApiAiMusicProvider provider() {
        AiMusicProvider p = providers.require("sunoapi");
        if (!(p instanceof SunoApiAiMusicProvider)) throw error("Voice provider is unavailable");
        return (SunoApiAiMusicProvider) p;
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> call(HttpMethod method, String path, Map<String,Object> body) {
        Object data = provider().voiceRequest(method,path,body).get("data");
        if (!(data instanceof Map)) throw error("Voice provider returned an invalid response");
        return (Map<String,Object>) data;
    }
    public List<Map<String,Object>> list(String owner) {
        init(); return d1.query("SELECT id,name,status,phase,phrase,error_message FROM ai_music_voices WHERE user_id=? ORDER BY created_at DESC LIMIT 100",owner).getRows();
    }
    private Map<String,Object> owned(String owner,String id) {
        init(); Map<String,Object> row = d1.query("SELECT * FROM ai_music_voices WHERE user_id=? AND id=?",owner,id).firstRow();
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND,"VOICE_NOT_FOUND","Voice was not found");
        return row;
    }
    private Map<String,Object> view(Map<String,Object> row) {
        Map<String,Object> result = new LinkedHashMap<>();
        for(String key: Arrays.asList("id","name","status","phase","phrase","error_message")) result.put(key,row.get(key));
        return result;
    }
    public Map<String,Object> create(String owner,String name,MusicMvRenderJobCreateRequest.Asset asset,double start,double end) {
        init(); storage.requireOwnedCloudAsset(owner,asset,"music");
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start || end > 480) throw error("Select a valid voice sample segment");
        String id=IdUtils.token("voice");
        d1.query("INSERT INTO ai_music_voices(id,user_id,name,status,phase) VALUES(?,?,?,'submitting','phrase')",id,owner,name);
        Map<String,Object> body=new LinkedHashMap<>(); body.put("voiceUrl",asset.getUrl()); body.put("vocalStartS",start); body.put("vocalEndS",end); body.put("language","en");
        try {
            Map<String,Object> data=call(HttpMethod.POST,"validate",body);
            String task=RowUtils.str(data,"taskId"); if(task==null || task.isEmpty()) throw error("Missing voice task ID");
            d1.query("UPDATE ai_music_voices SET task_id=?,status='processing' WHERE id=?",task,id);
        } catch(RuntimeException ex) { fail(id,ex.getMessage()); throw ex; }
        return view(owned(owner,id));
    }
    private void fail(String id,String message) { d1.query("UPDATE ai_music_voices SET status='failed',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",message,id); }
    public Map<String,Object> get(String owner,String id) {
        Map<String,Object> row=owned(owner,id);
        String status=RowUtils.str(row,"status");
        if(!"processing".equals(status)) return view(row);
        String key = owner + "\n" + id + "\n" + row.get("phase") + "\n" + row.get("task_id");
        return polling.get(key, ignored -> refreshProcessing(owner, id, row));
    }
    private Map<String,Object> refreshProcessing(String owner, String id, Map<String,Object> row) {
        String task=RowUtils.str(row,"task_id");
        String endpoint="phrase".equals(RowUtils.str(row,"phase")) ? "validate-info" : "record-info";
        String path=UriComponentsBuilder.fromPath(endpoint).queryParam("taskId",task).build().encode().toUriString();
        Map<String,Object> data=call(HttpMethod.GET,path,null);
        String next=RowUtils.str(data,"status");
        if("fail".equals(next) || "processing_validate_fail".equals(next)) fail(id,RowUtils.str(data,"errorMessage"));
        else if("phrase".equals(RowUtils.str(row,"phase")) && "wait_validating".equals(next)) {
            String phrase=RowUtils.str(data,"validateInfo");
            if(phrase!=null && !phrase.trim().isEmpty()) d1.query("UPDATE ai_music_voices SET status='awaiting_verification',phrase=? WHERE id=?",phrase,id);
        } else if("voice".equals(RowUtils.str(row,"phase")) && "success".equals(next)) {
            String voice=RowUtils.str(data,"voiceId");
            if(voice!=null && !voice.isEmpty()) d1.query("UPDATE ai_music_voices SET status='ready',voice_id=? WHERE id=?",voice,id);
        }
        return view(owned(owner,id));
    }
    public Map<String,Object> verify(String owner,String id,MusicMvRenderJobCreateRequest.Asset asset) {
        Map<String,Object> row=owned(owner,id); storage.requireOwnedCloudAsset(owner,asset,"music");
        Map<String,Object> claimed=d1.query("UPDATE ai_music_voices SET status='submitting' WHERE id=? AND user_id=? AND status='awaiting_verification' RETURNING id",id,owner).firstRow();
        if(claimed==null) throw error("This voice is not waiting for a verification recording");
        Map<String,Object> body=new LinkedHashMap<>(); body.put("taskId",row.get("task_id"));body.put("verifyUrl",asset.getUrl());body.put("voiceName",row.get("name"));
        try {
            Map<String,Object> data=call(HttpMethod.POST,"generate",body);
            String task=RowUtils.str(data,"taskId");if(task==null || task.isEmpty()) throw error("Missing voice generation task ID");
            d1.query("UPDATE ai_music_voices SET task_id=?,phase='voice',status='processing' WHERE id=?",task,id);
        } catch(RuntimeException ex) { fail(id,ex.getMessage());throw ex; }
        return view(owned(owner,id));
    }
    public String resolve(String owner,String id) {
        Map<String,Object> row=owned(owner,id);
        if(!"ready".equals(row.get("status"))) throw error("Wait for voice verification to finish");
        if(!Boolean.TRUE.equals(call(HttpMethod.POST,"check-voice",Collections.singletonMap("task_id",row.get("task_id"))).get("isAvailable"))) throw error("This voice is not currently available. Try again later");
        return RowUtils.str(row,"voice_id");
    }
    private ApiException error(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"VOICE_REQUEST_INVALID",message); }
}
