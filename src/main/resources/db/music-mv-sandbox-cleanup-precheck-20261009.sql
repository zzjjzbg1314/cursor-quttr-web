-- 只读预检查：在执行清理脚本前，单独运行本文件并人工核对结果。
-- 本文件不会删除数据；查询结果不会自动阻止后续脚本，发现新增正式账务时必须停止。
-- 适用对象：Nurr Manabilang，2026-10-09 已核对为唯一网站测试账号。
SELECT user_id,display_name FROM music_mv_users;
SELECT user_id,provider,customer_id FROM music_mv_billing_customers;
SELECT user_id,provider,subscription_id,status,cancel_at_period_end,checkout_id
FROM music_mv_billing_subscriptions;
SELECT user_id,provider,payment_id,plan_key,allowance,revoked
FROM music_mv_billing_grants ORDER BY created_at;
SELECT user_id,grant_id,state,credits FROM music_mv_billing_reservations;
-- 当前预期：1个账号、1个沙盒客户、1条取消续费但仍有效的订阅；
-- 4笔Stripe发放全部revoked=1，另有1笔internal体验积分10；消费/预留0条。
-- 清理前必须在Stripe确认以下客户及订单仍是测试模式；数据库本身没有livemode字段。
-- 客户：cus_VOvIati8Do8oim；付款清单见清理脚本。
