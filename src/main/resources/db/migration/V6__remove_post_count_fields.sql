-- 从posts表中移除like_count和comment_count字段
-- 注意：这个迁移脚本会删除这两个字段及其数据

-- 1. 移除like_count字段
ALTER TABLE posts DROP COLUMN IF EXISTS like_count;

-- 2. 移除comment_count字段
ALTER TABLE posts DROP COLUMN IF EXISTS comment_count;

-- 3. 添加注释说明
COMMENT ON TABLE posts IS '帖子表（已移除点赞数和评论数字段）';
COMMENT ON COLUMN posts.post_id IS '帖子ID，UUID类型，主键';
COMMENT ON COLUMN posts.user_id IS '用户ID，UUID类型';
COMMENT ON COLUMN posts.user_nickname IS '用户昵称';
COMMENT ON COLUMN posts.user_stage IS '用户阶段';
COMMENT ON COLUMN posts.title IS '帖子标题';
COMMENT ON COLUMN posts.content IS '帖子内容';
COMMENT ON COLUMN posts.is_deleted IS '是否已删除';
COMMENT ON COLUMN posts.created_at IS '创建时间';
COMMENT ON COLUMN posts.updated_at IS '更新时间';

-- 注意：点赞数和评论数现在通过独立的表（post_likes和comments）来管理
-- 这样可以提供更好的数据一致性和性能
