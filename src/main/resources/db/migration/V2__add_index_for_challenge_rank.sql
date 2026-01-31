-- 加速基于 challenge_reset_time 的戒色排名查询
CREATE INDEX IF NOT EXISTS idx_users_challenge_rank
    ON users (challenge_reset_time, created_at, id);
