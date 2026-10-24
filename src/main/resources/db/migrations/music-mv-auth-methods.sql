-- 登录挑战独立迁移，不改变模板目录版本或计费数据。
CREATE TABLE IF NOT EXISTS music_mv_auth_challenges (
  challenge_id TEXT PRIMARY KEY,
  kind TEXT NOT NULL,
  browser_hash TEXT NOT NULL,
  target_user_id TEXT,
  email TEXT,
  secret_hash TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  consumed INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_music_mv_auth_challenge_email ON music_mv_auth_challenges(email,created_at);
CREATE INDEX IF NOT EXISTS idx_music_mv_auth_challenge_browser ON music_mv_auth_challenges(browser_hash,created_at);
CREATE INDEX IF NOT EXISTS idx_music_mv_auth_challenge_created ON music_mv_auth_challenges(created_at);
CREATE INDEX IF NOT EXISTS idx_music_mv_verified_identity_email
  ON music_mv_user_identities(lower(trim(provider_email)),email_verified,user_id);
