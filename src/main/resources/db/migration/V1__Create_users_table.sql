-- 创建users表
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

-- 创建索引
CREATE INDEX IF NOT EXISTS "idx_users_challenge_reset_time" 
ON "public"."users" USING btree ( "challenge_reset_time" ) ;

CREATE INDEX IF NOT EXISTS "idx_users_wechat_openid" 
ON "public"."users" USING btree ( "wechat_openid" COLLATE "pg_catalog"."default" ) ;

CREATE INDEX IF NOT EXISTS "idx_users_wechat_unionid" 
ON "public"."users" USING btree ( "wechat_unionid" COLLATE "pg_catalog"."default" ) 
WHERE wechat_unionid IS NOT NULL ;
