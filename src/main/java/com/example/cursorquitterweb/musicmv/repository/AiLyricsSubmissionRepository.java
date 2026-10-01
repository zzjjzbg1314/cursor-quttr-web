package com.example.cursorquitterweb.musicmv.repository;

import java.util.Map;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.cursorquitterweb.musicmv.service.D1DatabaseClient;

@Repository
@ConditionalOnProperty(prefix="music-mv", name="enabled", havingValue="true")
public class AiLyricsSubmissionRepository {
    private final D1DatabaseClient db;
    public AiLyricsSubmissionRepository(D1DatabaseClient db) { this.db = db; }
    public Map<String,Object> find(String owner, String request) {
        return db.query("SELECT prompt,task_handle FROM ai_lyrics_submissions WHERE user_id=? AND request_id=?", owner, request).firstRow();
    }
    public boolean claim(String owner, String request, String prompt) {
        // 占用不自动过期：崩溃或超时后宁可等待核查，也不能重复付费提交。
        return db.query("INSERT INTO ai_lyrics_submissions(user_id,request_id,prompt) VALUES(?,?,?) ON CONFLICT(user_id,request_id) DO NOTHING RETURNING request_id", owner, request, prompt).firstRow() != null;
    }
    public void save(String owner, String request, String handle) {
        db.query("UPDATE ai_lyrics_submissions SET task_handle=? WHERE user_id=? AND request_id=?", handle, owner, request);
    }
}
