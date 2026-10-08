-- 全新积分系统初始化；支付渠道编号与内部积分编号分开，不执行旧数据迁移。
CREATE TABLE IF NOT EXISTS music_mv_billing_customers (
 user_id TEXT NOT NULL, provider TEXT NOT NULL, customer_id TEXT NOT NULL,
 PRIMARY KEY(user_id,provider), UNIQUE(provider,customer_id)
);
-- 每个用户只保留一个当前订阅入口，跨支付渠道共用结账占用。
CREATE TABLE IF NOT EXISTS music_mv_billing_subscriptions (
 user_id TEXT PRIMARY KEY, provider TEXT NOT NULL,
 subscription_id TEXT, status TEXT, cancel_at_period_end INTEGER NOT NULL DEFAULT 0,
 checkout_token TEXT, checkout_plan TEXT, checkout_expires INTEGER,
 checkout_id TEXT, checkout_url TEXT, checkout_return_url TEXT,
 UNIQUE(provider,subscription_id)
);
CREATE TABLE IF NOT EXISTS music_mv_billing_grants (
 grant_id TEXT PRIMARY KEY, user_id TEXT NOT NULL,
 provider TEXT NOT NULL, payment_id TEXT NOT NULL, subscription_id TEXT NOT NULL,
 plan_key TEXT NOT NULL, allowance INTEGER NOT NULL CHECK(allowance>0),
 period_start INTEGER NOT NULL, period_end INTEGER NOT NULL CHECK(period_end>period_start),
 revoked INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(provider,payment_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_grants_user ON music_mv_billing_grants(user_id,period_end);
CREATE TABLE IF NOT EXISTS music_mv_billing_reservations (
 job_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, request_id TEXT NOT NULL,
 grant_id TEXT NOT NULL REFERENCES music_mv_billing_grants(grant_id),
 state TEXT NOT NULL CHECK(state IN ('reserved','consumed','released')),
 credits INTEGER NOT NULL CHECK(credits>0),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,request_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_reservations_grant ON music_mv_billing_reservations(grant_id,state);
CREATE TABLE IF NOT EXISTS music_mv_billing_events (
 provider TEXT NOT NULL, event_id TEXT NOT NULL, event_type TEXT NOT NULL,
 processed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(provider,event_id)
);
CREATE TABLE IF NOT EXISTS music_mv_billing_revocations (
 provider TEXT NOT NULL, payment_id TEXT NOT NULL, PRIMARY KEY(provider,payment_id)
);
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
