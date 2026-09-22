-- 支付数据独立迁移；仅在指定的测试数据库执行，不在启动时自动修改数据库。
CREATE TABLE IF NOT EXISTS music_mv_billing_customers (
 user_id TEXT PRIMARY KEY, customer_id TEXT UNIQUE NOT NULL,
 checkout_token TEXT, checkout_plan TEXT, checkout_expires INTEGER,
 checkout_id TEXT, checkout_url TEXT, checkout_return_url TEXT
);
CREATE TABLE IF NOT EXISTS music_mv_billing_grants (
 invoice_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, subscription_id TEXT NOT NULL,
 plan_key TEXT NOT NULL, allowance INTEGER NOT NULL CHECK(allowance>0),
 period_start INTEGER NOT NULL, period_end INTEGER NOT NULL,
 revoked INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_billing_grants_user ON music_mv_billing_grants(user_id,period_end);
CREATE TABLE IF NOT EXISTS music_mv_billing_reservations (
 job_id TEXT PRIMARY KEY, user_id TEXT NOT NULL, request_id TEXT NOT NULL,
 invoice_id TEXT NOT NULL REFERENCES music_mv_billing_grants(invoice_id),
 state TEXT NOT NULL CHECK(state IN ('reserved','consumed','released')),
 created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,request_id)
);
CREATE INDEX IF NOT EXISTS idx_billing_reservations_invoice ON music_mv_billing_reservations(invoice_id,state);
CREATE TABLE IF NOT EXISTS music_mv_billing_events (
 event_id TEXT PRIMARY KEY, event_type TEXT NOT NULL, processed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS music_mv_billing_revocations (invoice_id TEXT PRIMARY KEY);
