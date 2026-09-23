-- 仅新增读取索引，不修改作品、素材或任务数据。
CREATE INDEX IF NOT EXISTS idx_music_mv_projects_recent_active ON music_mv_projects(user_id,updated_at DESC,project_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_music_mv_assets_recent_active ON music_mv_user_assets(user_id,kind,status,last_used_at DESC,created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_music_mv_render_jobs_owner_recent ON music_mv_render_jobs(client_id,created_at DESC,job_id DESC);
CREATE INDEX IF NOT EXISTS idx_music_mv_render_jobs_owner_completed ON music_mv_render_jobs(client_id,created_at DESC,job_id DESC) WHERE status='completed' AND output_storage_key IS NOT NULL;
