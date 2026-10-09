-- 用途：转正式支付前，清理唯一测试账号的已核对沙盒账务；不是删除账号。
-- 目标账号：usr_0f5190d0a39f4e64ad3057bc38d10bb0（Nurr Manabilang）。
-- 保留：music_mv_users、身份/登录会话、作品/素材/歌曲、internal体验积分及其消费记录。
-- 本文件仅供审核，尚未在远程数据库执行。
--
-- 执行前：
-- 1. 先运行同目录 precheck 文件，确认数据没有新增正式付款或其他账号关联。
-- 2. 导出完整数据库备份，并保存本账号账务快照；保管备份中的会话和订单敏感信息。
-- 3. 暂停网站支付/生成入口及后台账务写入，并禁用Stripe沙盒Webhook投递。
--    单独关闭购买按钮不足以阻止旧Webhook；Stripe现有测试订阅仍须在沙盒侧结束。
-- 4. 普通SQLite须在外层事务内执行；Cloudflare D1请将本文件的删除语句
--    作为一次原子batch提交，不逐条联网执行，不向D1发送BEGIN/COMMIT。
-- 5. 清理完成、校验通过后再启用正式配置和正式Webhook。
--
-- 删除严格限定账号 + provider + 四个已确认沙盒付款ID，不能改成全表清空。
-- 第一步：先移除这四笔测试发放对应的消费/预留记录，避免外键引用残留。
-- 当前消费/预留为空；此语句仍限定到对应发放，不删除体验积分的消费。
DELETE FROM music_mv_billing_reservations
WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0'
AND grant_id IN (
 SELECT grant_id FROM music_mv_billing_grants
 WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
 AND payment_id IN ('in_1UOAUFGj38FUksqYQ2Asi5Hk','in_1UOJUgGj38FUksqYPdHzkxjI','in_1UOJoyGj38FUksqY0dcqtyuw','in_1UORFwGj38FUksqYmCY4D2eI')
);
-- 第二步：通过仍存在的发放记录限定归属，移除相关撤销详情和撤销标记。
DELETE FROM music_mv_billing_revocation_details
WHERE provider='stripe' AND payment_id IN (
 SELECT payment_id FROM music_mv_billing_grants
 WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
 AND payment_id IN ('in_1UOAUFGj38FUksqYQ2Asi5Hk','in_1UOJUgGj38FUksqYPdHzkxjI','in_1UOJoyGj38FUksqY0dcqtyuw','in_1UORFwGj38FUksqYmCY4D2eI')
);
DELETE FROM music_mv_billing_revocations
WHERE provider='stripe' AND payment_id IN (
 SELECT payment_id FROM music_mv_billing_grants
 WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
 AND payment_id IN ('in_1UOAUFGj38FUksqYQ2Asi5Hk','in_1UOJUgGj38FUksqYPdHzkxjI','in_1UOJoyGj38FUksqY0dcqtyuw','in_1UORFwGj38FUksqYmCY4D2eI')
);
-- 第三步：移除四笔测试积分，保留internal/welcome的10体验积分。
DELETE FROM music_mv_billing_grants
WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
AND payment_id IN ('in_1UOAUFGj38FUksqYQ2Asi5Hk','in_1UOJUgGj38FUksqYPdHzkxjI','in_1UOJoyGj38FUksqY0dcqtyuw','in_1UORFwGj38FUksqYmCY4D2eI');
-- 第四步：移除已确认的当前沙盒订阅以及同一行上的收银台占用。
-- 如果订阅/订单ID已经改变，或出现新增Stripe积分，则保留关联，要求重新核对。
DELETE FROM music_mv_billing_subscriptions
WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
AND subscription_id='sub_1UORFxGj38FUksqY8tRcw7WE'
AND checkout_id='cs_test_a1ZuCw5QFN5sbCMSvHRflwYDWD5A468hLQXeBOMXAnhigNDv14HGSWbd0p'
AND NOT EXISTS (SELECT 1 FROM music_mv_billing_grants WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe');
-- 第五步：订阅入口确已清除才清理查询令牌；保留表和触发器。
DELETE FROM music_mv_billing_subscription_reads
WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0'
AND NOT EXISTS (SELECT 1 FROM music_mv_billing_subscriptions WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0');
-- 第六步：移除测试客户关联，正式支付时重新创建正式客户；不删除Stripe侧客户。
DELETE FROM music_mv_billing_customers
WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
AND customer_id='cus_VOvIati8Do8oim'
AND NOT EXISTS (SELECT 1 FROM music_mv_billing_subscriptions WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0')
AND NOT EXISTS (SELECT 1 FROM music_mv_billing_grants WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe');
-- 特意保留music_mv_billing_events：该表没有账号列，是Webhook去重记录，
-- 不属于余额/明细来源；保留能防止重复旧通知被当成新事件处理。
-- 仍必须禁用沙盒Webhook并切换live模式，去重记录不能拦截尚未处理的新沙盒事件。
-- 本脚本不改变Stripe侧订阅、不切换配置，也不删除用户或作品。
