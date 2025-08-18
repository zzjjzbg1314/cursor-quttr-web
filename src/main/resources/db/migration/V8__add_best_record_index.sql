-- 为 users 表的 best_record 字段添加索引，优化排序查询性能
CREATE INDEX idx_users_best_record ON "users" ("best_record");

-- 添加索引注释
COMMENT ON INDEX "public"."idx_users_best_record" IS '用户最佳记录索引，用于优化按最佳记录排序的查询';
