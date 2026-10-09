-- 清理后只读校验；不要在校验通过前恢复正式支付。
-- 下列5项应为0；若不为0，停止并重新检查，不要扩大删除范围。
SELECT 'stripe_customers' AS item,COUNT(*) AS remaining FROM music_mv_billing_customers WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
UNION ALL SELECT 'subscriptions',COUNT(*) FROM music_mv_billing_subscriptions WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0'
UNION ALL SELECT 'stripe_grants',COUNT(*) FROM music_mv_billing_grants WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0' AND provider='stripe'
UNION ALL SELECT 'subscription_reads',COUNT(*) FROM music_mv_billing_subscription_reads WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0'
UNION ALL SELECT 'orphan_reservations',COUNT(*) FROM music_mv_billing_reservations r LEFT JOIN music_mv_billing_grants g ON g.grant_id=r.grant_id WHERE g.grant_id IS NULL;
-- 两张撤销表中本次四个付款的残留均应为0。
SELECT 'revocations' AS item,COUNT(*) AS remaining FROM music_mv_billing_revocations WHERE provider='stripe' AND payment_id IN ('in_1UOAUFGj38FUksqYQ2Asi5Hk','in_1UOJUgGj38FUksqYPdHzkxjI','in_1UOJoyGj38FUksqY0dcqtyuw','in_1UORFwGj38FUksqYmCY4D2eI')
UNION ALL SELECT 'revocation_details',COUNT(*) FROM music_mv_billing_revocation_details WHERE provider='stripe' AND payment_id IN ('in_1UOAUFGj38FUksqYQ2Asi5Hk','in_1UOJUgGj38FUksqYPdHzkxjI','in_1UOJoyGj38FUksqY0dcqtyuw','in_1UORFwGj38FUksqYmCY4D2eI');
-- 应保留原用户1条，体验积分原记录1条、allowance=10、revoked=0。
SELECT user_id,display_name FROM music_mv_users WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0';
SELECT user_id,provider,plan_key,allowance,revoked FROM music_mv_billing_grants WHERE user_id='usr_0f5190d0a39f4e64ad3057bc38d10bb0';
-- 作品/素材/歌曲数量和Webhook去重记录应与备份一致，另外比较清理前后快照。
-- 此前已核对体验积分未消费：网站应显示10，订阅积分0，无当前套餐，可发起正式购买。
