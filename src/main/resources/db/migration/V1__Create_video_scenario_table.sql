-- 创建视频场景表
CREATE TABLE "public"."video_scenario" (
    "videoId" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    "type" varchar,
    "title" varchar,
    "subtitle" varchar,
    "image" varchar,
    "audiourl" varchar,
    "videourl" varchar,
    "color" varchar,
    "quotes" varchar,
    "author" varchar,
    "createAt" timestamptz DEFAULT now(),
    "updateAt" timestamptz DEFAULT now()
);

-- 添加表注释
COMMENT ON TABLE "public"."video_scenario" IS '场景播放表，用于存储视频播放相关的元信息，包括标题、资源链接、样式和励志语录等';

-- 添加列注释
COMMENT ON COLUMN "public"."video_scenario"."videoId" IS '视频唯一标识，UUID类型，默认生成随机值';
COMMENT ON COLUMN "public"."video_scenario"."type" IS '视频类型，如冥想、激励、助眠等';
COMMENT ON COLUMN "public"."video_scenario"."title" IS '视频主标题';
COMMENT ON COLUMN "public"."video_scenario"."subtitle" IS '视频副标题';
COMMENT ON COLUMN "public"."video_scenario"."image" IS '封面图片URL';
COMMENT ON COLUMN "public"."video_scenario"."audiourl" IS '音频资源链接';
COMMENT ON COLUMN "public"."video_scenario"."videourl" IS '视频资源链接';
COMMENT ON COLUMN "public"."video_scenario"."color" IS '背景颜色，用于界面展示';
COMMENT ON COLUMN "public"."video_scenario"."quotes" IS '励志语录文本';
COMMENT ON COLUMN "public"."video_scenario"."author" IS '语录作者';
COMMENT ON COLUMN "public"."video_scenario"."createAt" IS '创建时间（带时区）';
COMMENT ON COLUMN "public"."video_scenario"."updateAt" IS '更新时间（带时区）';

-- 创建索引以提高查询性能
CREATE INDEX idx_video_scenario_type ON "public"."video_scenario" ("type");
CREATE INDEX idx_video_scenario_create_at ON "public"."video_scenario" ("createAt");
CREATE INDEX idx_video_scenario_update_at ON "public"."video_scenario" ("updateAt");
CREATE INDEX idx_video_scenario_title ON "public"."video_scenario" USING gin (to_tsvector('chinese', "title"));
CREATE INDEX idx_video_scenario_subtitle ON "public"."video_scenario" USING gin (to_tsvector('chinese', "subtitle"));
CREATE INDEX idx_video_scenario_quotes ON "public"."video_scenario" USING gin (to_tsvector('chinese', "quotes"));
