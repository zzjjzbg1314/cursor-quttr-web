package com.example.cursorquitterweb.musicmv.aiphoto;

import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.*;

@Repository
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class AiPhotoJobRepository {
    private final D1DatabaseClient db;
    public AiPhotoJobRepository(D1DatabaseClient db) { this.db = db; }
    public Map<String,Object> owned(String owner, String id) {
        return db.query("SELECT * FROM music_mv_ai_photo_jobs WHERE user_id=? AND job_id=?", owner, id).firstRow();
    }
    public Map<String,Object> request(String owner, String request) {
        return db.query("SELECT * FROM music_mv_ai_photo_jobs WHERE user_id=? AND request_id=?",owner,request).firstRow();
    }
    public Map<String,Object> create(String owner, String request, String id, String fingerprint,
                                     String template, String version, String slot, String source, String sourceHash,
                                     String recipe, String baseUrl) {
        db.query("INSERT INTO music_mv_ai_photo_jobs(job_id,user_id,request_id,input_fingerprint,template_id,version_id,slot_key,source_asset_id,source_sha256,recipe_json,base_url,status) "
            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,'queued') ON CONFLICT(user_id,request_id) DO NOTHING",
            id,owner,request,fingerprint,template,version,slot,source,sourceHash,recipe,baseUrl);
        return request(owner,request);
    }
    public List<Map<String,Object>> pending() {
        // 崩溃发生于提交过程中时，不能自动再次提交付费请求。
        db.query("UPDATE music_mv_ai_photo_jobs SET status='submission_unknown',error_code='AI_PHOTO_SUBMISSION_UNKNOWN',lease_token=NULL WHERE status='submitting' AND lease_until<CURRENT_TIMESTAMP");
        db.query("UPDATE music_mv_ai_photo_jobs SET status='failed',error_code='AI_PHOTO_TIMEOUT',lease_token=NULL WHERE status IN ('queued','generating','persisting') AND created_at<datetime('now','-30 minutes')");
        return db.query("SELECT * FROM music_mv_ai_photo_jobs WHERE status IN ('queued','generating','persisting') AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP) AND (next_poll_at IS NULL OR next_poll_at<=CURRENT_TIMESTAMP) ORDER BY created_at LIMIT 8").getRows();
    }
    public Map<String,Object> claim(String id, String state, String token) {
        return db.query("UPDATE music_mv_ai_photo_jobs SET status=CASE WHEN status='queued' THEN 'submitting' ELSE status END,lease_token=?,lease_until=datetime('now','+3 minutes'),updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND status=? AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP) RETURNING *",token,id,state).firstRow();
    }
    public void update(String id, String token, String state, String task, String url, String asset, String error) {
        db.query("UPDATE music_mv_ai_photo_jobs SET status=?,provider_task_id=COALESCE(?,provider_task_id),output_url=COALESCE(?,output_url),result_asset_id=COALESCE(?,result_asset_id),error_code=?,lease_token=NULL,lease_until=NULL,next_poll_at=datetime('now','+5 seconds'),updated_at=CURRENT_TIMESTAMP WHERE job_id=? AND lease_token=? AND status IN ('submitting','generating','persisting')",
            state,task,url,asset,error,id,token);
    }
    public void cancel(String owner, String id) {
        db.query("UPDATE music_mv_ai_photo_jobs SET status='canceled',lease_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND job_id=? AND status IN ('queued','submitting','generating','persisting','submission_unknown')",owner,id);
    }
}
