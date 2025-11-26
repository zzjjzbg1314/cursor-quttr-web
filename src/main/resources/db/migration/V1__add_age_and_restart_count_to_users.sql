-- 为 users 表添加两个新字段：年龄（age）和重启次数（restart_count）
-- 日期: 2025-10-20
-- 描述: 添加用户年龄和重启次数字段以支持更详细的用户画像和统计
-- 注意: SQLite 不支持 ALTER TABLE ADD COLUMN 的多个列，需要分开执行
-- SQLite 不支持 COMMENT ON COLUMN，注释已移除

ALTER TABLE users ADD COLUMN age INTEGER;
ALTER TABLE users ADD COLUMN restart_count INTEGER;

