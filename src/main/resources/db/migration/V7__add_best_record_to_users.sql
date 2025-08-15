-- 在 users 表中添加 best_record 字段，用于记录最佳挑战成绩天数
ALTER TABLE "users"
ADD COLUMN "best_record" int DEFAULT 1;

-- 添加字段注释
COMMENT ON COLUMN "public"."users"."best_record" IS '最佳挑战成绩天数';

-- 为现有用户设置默认值（如果字段允许为空的话）
UPDATE "users" SET "best_record" = 1 WHERE "best_record" IS NULL;

-- 确保字段不为空
ALTER TABLE "users" ALTER COLUMN "best_record" SET NOT NULL;
