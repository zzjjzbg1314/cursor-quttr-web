-- 只在创建新生成任务时登记，不回填历史歌曲。
CREATE TABLE IF NOT EXISTS ai_music_song_sync (
    job_id TEXT PRIMARY KEY,
    status TEXT NOT NULL DEFAULT 'pending',
    attempts INTEGER NOT NULL DEFAULT 0,
    retry_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TEXT,
    lease_token TEXT,
    last_error TEXT
);
