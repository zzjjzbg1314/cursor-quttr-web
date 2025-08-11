-- 数据库初始化脚本
-- 请手动在PostgreSQL中执行此脚本

-- 1. 创建users表
CREATE TABLE IF NOT EXISTS "public"."users"
(
 "id" uuid NOT NULL DEFAULT gen_random_uuid() ,
 "wechat_openid" text NOT NULL ,
 "wechat_unionid" text ,
 "nickname" text ,
 "avatar_url" text ,
 "gender" smallint ,
 "country" text ,
 "province" text ,
 "city" text ,
 "language" text DEFAULT 'zh_CN'::text ,
 "registration_time" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
 "challenge_reset_time" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
 "created_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
 "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
CONSTRAINT "pk_public_users" PRIMARY KEY ("id") ,
CONSTRAINT "uk_users_wechat_openid" UNIQUE ("wechat_openid") WITH (FILLFACTOR=100) ,
CONSTRAINT "users_gender_check"  CHECK (gender = ANY (ARRAY[0, 1, 2])) NOT VALID 
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
);

-- 创建users表索引
CREATE INDEX IF NOT EXISTS "idx_users_challenge_reset_time" 
ON "public"."users" USING btree ( "challenge_reset_time" ) ;

CREATE INDEX IF NOT EXISTS "idx_users_wechat_openid" 
ON "public"."users" USING btree ( "wechat_openid" ) ;

CREATE INDEX IF NOT EXISTS "idx_users_wechat_unionid" 
ON "public"."users" USING btree ( "wechat_unionid" ) 
WHERE wechat_unionid IS NOT NULL ;

-- 2. 创建posts表
CREATE TABLE IF NOT EXISTS "public"."posts"
(
 "post_id" uuid NOT NULL DEFAULT gen_random_uuid(),
 "user_id" uuid NOT NULL,
 "user_nickname" character varying(100) NOT NULL,
 "user_stage" character varying(50) NOT NULL,
 "title" character varying(255) NOT NULL,
 "content" text NOT NULL,
 "is_deleted" boolean NOT NULL DEFAULT false,
 "created_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 "like_count" integer NOT NULL DEFAULT 0,
 "comment_count" integer NOT NULL DEFAULT 0,
CONSTRAINT "pk_public_posts" PRIMARY KEY ("post_id"),
CONSTRAINT "fk_posts_user_id" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id") ON DELETE CASCADE
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
);

-- 创建posts表索引
CREATE INDEX IF NOT EXISTS "idx_posts_user_id" 
ON "public"."posts" USING btree ( "user_id" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_created_at" 
ON "public"."posts" USING btree ( "created_at" DESC ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_title" 
ON "public"."posts" USING btree ( "title" ) ;

CREATE INDEX IF NOT EXISTS "idx_posts_user_stage" 
ON "public"."posts" USING btree ( "user_stage" ) ;

-- 3. 创建comments表
CREATE TABLE IF NOT EXISTS "public"."comments"
(
 "comment_id" bigserial NOT NULL,
 "post_id" bigint NOT NULL,
 "user_id" uuid NOT NULL,
 "user_nickname" character varying(100) NOT NULL,
 "user_stage" character varying(50) NOT NULL,
 "content" text NOT NULL,
 "is_deleted" boolean NOT NULL DEFAULT false,
 "created_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
CONSTRAINT "pk_public_comments" PRIMARY KEY ("comment_id"),
CONSTRAINT "fk_comments_post_id" FOREIGN KEY ("post_id") REFERENCES "public"."posts" ("post_id") ON DELETE CASCADE,
CONSTRAINT "fk_comments_user_id" FOREIGN KEY ("user_id") REFERENCES "public"."users" ("id") ON DELETE CASCADE
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
);

-- 创建comments表索引
CREATE INDEX IF NOT EXISTS "idx_comments_post_id" 
ON "public"."comments" USING btree ( "post_id" ) ;

CREATE INDEX IF NOT EXISTS "idx_comments_user_id" 
ON "public"."comments" USING btree ( "user_id" ) ;

CREATE INDEX IF NOT EXISTS "idx_comments_user_nickname" 
ON "public"."comments" USING btree ( "user_nickname" ) ;

CREATE INDEX IF NOT EXISTS "idx_comments_user_stage" 
ON "public"."comments" USING btree ( "user_stage" ) ;

CREATE INDEX IF NOT EXISTS "idx_comments_created_at" 
ON "public"."comments" USING btree ( "created_at" ) ;

CREATE INDEX IF NOT EXISTS "idx_comments_is_deleted" 
ON "public"."comments" USING btree ( "is_deleted" ) ;

-- 添加表注释
COMMENT ON TABLE "public"."users" IS '用户表';
COMMENT ON TABLE "public"."posts" IS '帖子表';
COMMENT ON TABLE "public"."comments" IS '评论表';

-- 添加字段注释
COMMENT ON COLUMN "public"."users"."id" IS '用户ID';
COMMENT ON COLUMN "public"."users"."wechat_openid" IS '微信openid';
COMMENT ON COLUMN "public"."users"."wechat_unionid" IS '微信unionid';
COMMENT ON COLUMN "public"."users"."nickname" IS '用户昵称';
COMMENT ON COLUMN "public"."users"."avatar_url" IS '头像URL';
COMMENT ON COLUMN "public"."users"."gender" IS '性别';
COMMENT ON COLUMN "public"."users"."country" IS '国家';
COMMENT ON COLUMN "public"."users"."province" IS '省份';
COMMENT ON COLUMN "public"."users"."city" IS '城市';
COMMENT ON COLUMN "public"."users"."language" IS '语言';
COMMENT ON COLUMN "public"."users"."registration_time" IS '注册时间';
COMMENT ON COLUMN "public"."users"."challenge_reset_time" IS '挑战重置时间';
COMMENT ON COLUMN "public"."users"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."users"."updated_at" IS '更新时间';

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

COMMENT ON COLUMN "public"."comments"."comment_id" IS '评论ID';
COMMENT ON COLUMN "public"."comments"."post_id" IS '帖子ID';
COMMENT ON COLUMN "public"."comments"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."comments"."user_nickname" IS '用户昵称';
COMMENT ON COLUMN "public"."comments"."user_stage" IS '用户阶段';
COMMENT ON COLUMN "public"."comments"."content" IS '评论内容';
COMMENT ON COLUMN "public"."comments"."is_deleted" IS '是否删除';
COMMENT ON COLUMN "public"."comments"."created_at" IS '创建时间';
COMMENT ON COLUMN "public"."comments"."updated_at" IS '更新时间';

-- 4. 创建post_likes表
CREATE TABLE IF NOT EXISTS "public"."post_likes"
(
 "post_id" bigint NOT NULL ,
 "like_count" integer NOT NULL DEFAULT 0 ,
 "updated_at" timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP ,
CONSTRAINT "pk_public_post_likes" PRIMARY KEY ("post_id") ,
CONSTRAINT "post_likes_post_id_fkey" FOREIGN KEY ("post_id") REFERENCES "public"."posts"("post_id") ON UPDATE NO ACTION ON DELETE CASCADE ,
CONSTRAINT "post_likes_like_count_check"  CHECK (like_count >= 0) NOT VALID 
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
);

-- 创建post_likes表索引
CREATE INDEX IF NOT EXISTS "idx_post_likes_like_count" 
ON "public"."post_likes" USING btree ( "like_count" ) ;

CREATE INDEX IF NOT EXISTS "idx_post_likes_updated_at" 
ON "public"."post_likes" USING btree ( "updated_at" ) ;

-- 添加post_likes表注释
COMMENT ON TABLE "public"."post_likes" IS '帖子点赞表';
COMMENT ON COLUMN "public"."post_likes"."post_id" IS '帖子ID';
COMMENT ON COLUMN "public"."post_likes"."like_count" IS '点赞数';
COMMENT ON COLUMN "public"."post_likes"."updated_at" IS '更新时间';

-- 显示创建结果
SELECT 'Tables created successfully!' as result;
