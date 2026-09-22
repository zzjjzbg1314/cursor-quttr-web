-- 歌词草稿独立迁移；不会修改已有歌曲或模板数据。
CREATE TABLE IF NOT EXISTS music_mv_lyrics_drafts (
 user_id TEXT NOT NULL,
 draft_id TEXT NOT NULL,
 document_json TEXT NOT NULL,
 revision INTEGER NOT NULL,
 write_marker TEXT NOT NULL,
 created_at TEXT NOT NULL,
 updated_at TEXT NOT NULL,
 PRIMARY KEY(user_id,draft_id)
);
CREATE INDEX IF NOT EXISTS idx_music_mv_lyrics_drafts_owner ON music_mv_lyrics_drafts(user_id,updated_at DESC);
