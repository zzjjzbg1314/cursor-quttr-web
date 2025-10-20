-- 为 users 表添加两个新字段：年龄（age）和重启次数（restart_count）
-- 日期: 2025-10-20
-- 描述: 添加用户年龄和重启次数字段以支持更详细的用户画像和统计

ALTER TABLE "public"."users"
ADD COLUMN "age" INT,
ADD COLUMN "restart_count" INT;

-- 添加注释
COMMENT ON COLUMN "public"."users"."age" IS '用户年龄';
COMMENT ON COLUMN "public"."users"."restart_count" IS '用户重启挑战的次数';

