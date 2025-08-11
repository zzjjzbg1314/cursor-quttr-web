-- 更新评论表结构，将ID字段从BIGINT改为UUID
-- 注意：这个迁移脚本需要在数据迁移完成后执行

-- 1. 删除外键约束
ALTER TABLE comments DROP CONSTRAINT IF EXISTS comments_post_id_fkey;

-- 2. 删除索引
DROP INDEX IF EXISTS idx_comments_post_id;
DROP INDEX IF EXISTS idx_comments_user_id;
DROP INDEX IF EXISTS idx_comments_created_at;
DROP INDEX IF EXISTS idx_comments_user_stage;

-- 3. 删除旧表
DROP TABLE IF EXISTS comments;

-- 4. 创建新表
CREATE TABLE comments (
    comment_id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    user_nickname VARCHAR(100) NOT NULL,
    user_stage VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES posts(post_id) ON DELETE CASCADE
);

-- 5. 重新创建索引
CREATE INDEX idx_comments_post_id ON comments(post_id);
CREATE INDEX idx_comments_user_id ON comments(user_id);
CREATE INDEX idx_comments_created_at ON comments(created_at DESC);
CREATE INDEX idx_comments_user_stage ON comments(user_stage);

-- 6. 添加注释
COMMENT ON TABLE comments IS '评论表';
COMMENT ON COLUMN comments.comment_id IS '评论ID，UUID类型';
COMMENT ON COLUMN comments.post_id IS '帖子ID，UUID类型';
COMMENT ON COLUMN comments.user_id IS '用户ID，UUID类型';
COMMENT ON COLUMN comments.user_nickname IS '用户昵称';
COMMENT ON COLUMN comments.user_stage IS '用户阶段';
COMMENT ON COLUMN comments.content IS '评论内容';
COMMENT ON COLUMN comments.is_deleted IS '是否已删除';
COMMENT ON COLUMN comments.created_at IS '创建时间';
COMMENT ON COLUMN comments.updated_at IS '更新时间';
