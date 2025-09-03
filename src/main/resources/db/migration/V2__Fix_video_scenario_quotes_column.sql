-- 修正视频场景表的列名问题
-- 如果列名是 "Quotes"（大写），则重命名为 "quotes"（小写）以保持一致性

-- 检查列是否存在，如果存在则重命名
DO $$
BEGIN
    -- 检查是否存在大写的 "Quotes" 列
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'video_scenario' 
        AND column_name = 'Quotes'
    ) THEN
        -- 重命名列从 "Quotes" 到 "quotes"
        ALTER TABLE "public"."video_scenario" RENAME COLUMN "Quotes" TO "quotes";
        RAISE NOTICE '列 "Quotes" 已重命名为 "quotes"';
    END IF;
    
    -- 检查是否存在小写的 "quotes" 列
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'video_scenario' 
        AND column_name = 'quotes'
    ) THEN
        -- 如果都不存在，则添加列
        ALTER TABLE "public"."video_scenario" ADD COLUMN "quotes" varchar;
        RAISE NOTICE '添加了列 "quotes"';
    END IF;
END $$;

-- 更新列注释
COMMENT ON COLUMN "public"."video_scenario"."quotes" IS '励志语录文本';

-- 删除旧的索引（如果存在）
DROP INDEX IF EXISTS idx_video_scenario_quotes;

-- 重新创建索引
CREATE INDEX idx_video_scenario_quotes ON "public"."video_scenario" USING gin (to_tsvector('chinese', "quotes"));
