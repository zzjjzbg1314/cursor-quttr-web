-- 创建音乐表
-- 对应数据库表: public.music

CREATE TABLE "public"."music"
(
 "id" uuid NOT NULL DEFAULT gen_random_uuid() ,
 "title" varchar ,
 "subtitle" varchar ,
 "time" varchar ,
 "image" varchar ,
 "videourl" varchar ,
 "audiourl" varchar ,
 "createAt" timestamp with time zone DEFAULT now() ,
 "updateAt" timestamp with time zone DEFAULT now() ,
 "quotes" varchar ,
 "author" varchar ,
CONSTRAINT "pk_public_music" PRIMARY KEY ("id") 
)
WITH (
    FILLFACTOR = 100,
    OIDS = FALSE
)
;

-- 创建索引
CREATE INDEX "idx_music_createat" 
ON "public"."music" USING btree ( "createAt" ) 
;

CREATE INDEX "idx_music_updateat" 
ON "public"."music" USING btree ( "updateAt" ) 
;

-- 创建标题索引用于搜索
CREATE INDEX "idx_music_title" 
ON "public"."music" USING btree ( "title" ) 
;

-- 创建作者索引用于搜索
CREATE INDEX "idx_music_author" 
ON "public"."music" USING btree ( "author" ) 
;
