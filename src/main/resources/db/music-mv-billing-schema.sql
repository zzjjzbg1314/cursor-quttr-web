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
