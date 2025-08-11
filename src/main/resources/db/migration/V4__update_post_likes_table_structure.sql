-- 更新帖子点赞表结构，将post_id从BIGINT改为UUID
-- 注意：这个迁移脚本需要在数据迁移完成后执行

-- 1. 删除外键约束
ALTER TABLE post_likes DROP CONSTRAINT IF EXISTS post_likes_post_id_fkey;

-- 2. 删除旧表
DROP TABLE IF EXISTS post_likes;

-- 3. 创建新表
CREATE TABLE "public"."post_likes"
(
    "post_id" UUID NOT NULL,
    "like_count" integer NOT NULL DEFAULT 0,
    "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "pk_public_post_likes" PRIMARY KEY ("post_id"),
    CONSTRAINT "post_likes_like_count_check" CHECK (like_count >= 0) NOT VALID,
    FOREIGN KEY ("post_id") REFERENCES "public"."posts" ("post_id") ON DELETE CASCADE
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
);

-- 4. 添加注释
COMMENT ON TABLE post_likes IS '帖子点赞表';
COMMENT ON COLUMN post_likes.post_id IS '帖子ID，UUID类型，主键';
COMMENT ON COLUMN post_likes.like_count IS '点赞数量';
COMMENT ON COLUMN post_likes.updated_at IS '更新时间';
