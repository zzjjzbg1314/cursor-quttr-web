-- 为 posts 表添加 avatar_url 字段，用于存储用户头像链接
ALTER TABLE "public"."posts"
ADD COLUMN "avatar_url" text;

-- 可选：添加字段注释，说明字段含义
COMMENT ON COLUMN "public"."posts"."avatar_url" IS '用户头像链接';
