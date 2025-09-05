-- 创建 books 表
CREATE TABLE "public"."books" (
    "id" UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "title" VARCHAR NOT NULL,
    "posturl" VARCHAR,
    "pdfurl" VARCHAR,
    "createAt" TIMESTAMPTZ DEFAULT NOW(),
    "updateAt" TIMESTAMPTZ DEFAULT NOW()
);

-- 添加注释
COMMENT ON TABLE "public"."books" IS '电子书信息表，存储书名、封面链接和PDF资源地址';
COMMENT ON COLUMN "public"."books"."id" IS '书籍唯一标识，UUID类型，自动生成';
COMMENT ON COLUMN "public"."books"."title" IS '书名';
COMMENT ON COLUMN "public"."books"."posturl" IS '书籍封面图片链接';
COMMENT ON COLUMN "public"."books"."pdfurl" IS 'PDF文件下载或访问链接';
COMMENT ON COLUMN "public"."books"."createAt" IS '创建时间（带时区）';
COMMENT ON COLUMN "public"."books"."updateAt" IS '更新时间（带时区）';

-- 创建索引以提高查询性能
CREATE INDEX idx_books_title ON "public"."books" ("title");
CREATE INDEX idx_books_create_at ON "public"."books" ("createAt");
CREATE INDEX idx_books_update_at ON "public"."books" ("updateAt");
CREATE INDEX idx_books_title_search ON "public"."books" USING gin (to_tsvector('chinese', "title"));
