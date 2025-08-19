-- 添加手机号字段
ALTER TABLE "public"."users" 
ADD COLUMN "phone_number" text;

-- 为手机号字段创建唯一索引（允许空值）
CREATE UNIQUE INDEX "idx_users_phone_number_unique" 
ON "public"."users" ("phone_number") 
WHERE "phone_number" IS NOT NULL;

-- 为手机号字段添加注释
COMMENT ON COLUMN "public"."users"."phone_number" IS '用户手机号，唯一且允许为空';
