-- 删除用户表中不再需要的字段
-- 这些字段已经在数据库中通过ALTER TABLE语句删除

-- 删除的字段包括：
-- - wechat_openid: 微信openid
-- - wechat_unionid: 微信unionid  
-- - country: 国家
-- - province: 省份
-- - city: 城市

-- 注意：此文件仅用于记录字段删除的历史，实际删除操作已在数据库中执行
-- ALTER TABLE "public"."users" 
-- DROP COLUMN "wechat_openid",
-- DROP COLUMN "wechat_unionid",
-- DROP COLUMN "country",
-- DROP COLUMN "province",
-- DROP COLUMN "city";

-- 更新后的用户表结构更加简洁，只保留必要的字段：
-- - id: 用户ID
-- - nickname: 昵称
-- - avatar_url: 头像URL
-- - gender: 性别
-- - language: 语言
-- - registration_time: 注册时间
-- - challenge_reset_time: 挑战重置时间
-- - best_record: 最佳记录
-- - created_at: 创建时间
-- - updated_at: 更新时间
