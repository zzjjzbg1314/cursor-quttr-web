-- 仅为已有 v14 数据库补充歌曲索引，不重建模板、不改分类和验收记录。
CREATE INDEX IF NOT EXISTS idx_ai_music_jobs_user_library
    ON ai_music_jobs(user_id, status, job_id);

-- 确认实际索引字段后再推进版本；保留上次完整初始化的校验摘要。
UPDATE music_mv_schema_metadata
SET schema_version=15, updated_at=CURRENT_TIMESTAMP
WHERE schema_key='core' AND schema_version=14
  AND (SELECT group_concat(name, ',') FROM (
    SELECT name FROM pragma_index_info('idx_ai_music_jobs_user_library') ORDER BY seqno
  ))='user_id,status,job_id';
