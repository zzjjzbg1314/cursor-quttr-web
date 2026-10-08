-- 已有账务库增量升级；保留原有订阅、积分和订单，不删除或回填历史数据。
-- 跨实例查询令牌：旧查询不能覆盖较晚开始的订阅核对。
CREATE TABLE IF NOT EXISTS music_mv_billing_subscription_reads (
 user_id TEXT PRIMARY KEY, read_token TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS music_mv_billing_revocation_details (
 provider TEXT NOT NULL, payment_id TEXT NOT NULL,
 amount INTEGER NOT NULL CHECK(amount>=0), occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(provider,payment_id)
);
CREATE TRIGGER IF NOT EXISTS billing_grant_revocation_record
BEFORE UPDATE OF revoked ON music_mv_billing_grants
WHEN OLD.revoked=0 AND NEW.revoked=1
BEGIN
 INSERT INTO music_mv_billing_revocation_details(provider,payment_id,amount)
 SELECT OLD.provider,OLD.payment_id,MAX(0,OLD.allowance-(SELECT COALESCE(SUM(credits),0) FROM music_mv_billing_reservations WHERE grant_id=OLD.grant_id AND state<>'released'))
 ON CONFLICT DO NOTHING;
END;
-- 同用户约束也保护维护脚本和未来写入路径；不依赖连接是否开启外键。
CREATE TRIGGER IF NOT EXISTS billing_reservation_owner_insert
BEFORE INSERT ON music_mv_billing_reservations
WHEN NOT EXISTS(SELECT 1 FROM music_mv_billing_grants WHERE grant_id=NEW.grant_id AND user_id=NEW.user_id)
BEGIN SELECT RAISE(ABORT,'billing reservation owner mismatch'); END;
CREATE TRIGGER IF NOT EXISTS billing_reservation_owner_update
BEFORE UPDATE OF grant_id,user_id ON music_mv_billing_reservations
WHEN NOT EXISTS(SELECT 1 FROM music_mv_billing_grants WHERE grant_id=NEW.grant_id AND user_id=NEW.user_id)
BEGIN SELECT RAISE(ABORT,'billing reservation owner mismatch'); END;
CREATE TRIGGER IF NOT EXISTS billing_grant_owner_update
BEFORE UPDATE OF user_id ON music_mv_billing_grants
WHEN EXISTS(SELECT 1 FROM music_mv_billing_reservations WHERE grant_id=OLD.grant_id AND user_id<>NEW.user_id)
BEGIN SELECT RAISE(ABORT,'billing grant owner mismatch'); END;
