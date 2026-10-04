-- 已存在的积分表升级一次；历史赠送默认已展示，不补弹。新库直接使用完整建表文件。
ALTER TABLE music_mv_billing_grants ADD COLUMN welcome_notice_seen INTEGER NOT NULL DEFAULT 1 CHECK(welcome_notice_seen IN (0,1));
