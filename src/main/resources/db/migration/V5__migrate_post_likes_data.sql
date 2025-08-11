-- 数据迁移脚本：将帖子点赞数据从Long类型ID迁移到UUID类型ID
-- 注意：这个脚本需要在V4__update_post_likes_table_structure.sql执行后运行

-- 1. 创建临时表来存储现有数据
CREATE TEMP TABLE temp_post_likes AS 
SELECT 
    post_id,
    like_count,
    updated_at
FROM post_likes_old;

-- 2. 插入迁移后的数据到新表
-- 注意：这里需要根据实际情况调整UUID的生成方式
-- 如果原表中有数据，需要先备份并转换ID格式

-- 示例：如果原表中有数据，可以这样处理：
-- INSERT INTO post_likes (post_id, like_count, updated_at)
-- SELECT 
--     gen_random_uuid() as post_id,  -- 需要根据实际的posts表ID进行映射
--     like_count,
--     updated_at
-- FROM temp_post_likes;

-- 3. 清理临时表
DROP TABLE temp_post_likes;

-- 注意：实际的数据迁移需要根据具体的业务逻辑和数据关系来设计
-- 建议在测试环境中先验证迁移脚本的正确性
