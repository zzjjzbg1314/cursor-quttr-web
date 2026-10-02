-- 仅用于已安装旧版次数计费表的数据库，停用计费并备份后执行一次。
-- 新数据库直接使用 music-mv-billing-schema.sql，不执行此文件。
-- 通过 D1 的事务批处理执行两条语句；重复执行会在新增列时报错，必须整体回滚。
ALTER TABLE music_mv_billing_reservations ADD COLUMN credits INTEGER NOT NULL DEFAULT 10 CHECK(credits>0);
UPDATE music_mv_billing_grants SET allowance=allowance*10;
