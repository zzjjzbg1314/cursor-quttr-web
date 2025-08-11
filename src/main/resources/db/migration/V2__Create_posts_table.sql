-- 创建posts表
CREATE TABLE IF NOT EXISTS "public"."posts"
(
 "post_id" bigserial NOT NULL,
 "user_id" bigint NOT NULL,
 "user_nickname" character varying(100) NOT NULL,
 "user_stage" character varying(50) NOT NULL,
 "title" character varying(255) NOT NULL,
 "content" text NOT NULL,
 "is_deleted" boolean NOT NULL DEFAULT false,
 "created_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 "like_count" integer NOT NULL DEFAULT 0,
 "comment_count" integer NOT NULL DEFAULT 0,
CONSTRAINT "pk_public_posts" PRIMARY KEY ("post_id")
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS "idx_posts_user_id" 
ON "public"."posts" USING btree ( "user_id" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_user_nickname" 
ON "public"."posts" USING btree ( "user_nickname" COLLATE "pg_catalog"."default" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_user_stage" 
ON "public"."posts" USING btree ( "user_stage" COLLATE "pg_catalog"."default" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_created_at" 
ON "public"."posts" USING btree ( "created_at" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_like_count" 
ON "public"."posts" USING btree ( "like_count" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_comment_count" 
ON "public"."posts" USING btree ( "comment_count" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_is_deleted" 
ON "public"."posts" USING btree ( "is_deleted" ) ;

-- 添加注释
COMMENT ON TABLE "public"."posts" IS '帖子表';
COMMENT ON COLUMN "public"."posts"."post_id" IS '帖子ID';
COMMENT ON COLUMN "public"."posts"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."posts"."user_nickname" IS '用户昵称';
COMMENT ON COLUMN "public"."posts"."user_stage" IS '用户阶段';
COMMENT ON COLUMN "public"."posts"."title" IS '帖子标题';
COMMENT ON COLUMN "public"."posts"."content" IS '帖子内容';
COMMENT ON COLUMN "public"."posts"."is_deleted" IS '是否删除';
COMMENT ON COLUMN "public"."posts"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."posts"."updated_at" IS '更新时间';
COMMENT ON COLUMN "public"."posts"."like_count" IS '点赞数';
COMMENT ON COLUMN "public"."posts"."comment_count" IS '评论数';
