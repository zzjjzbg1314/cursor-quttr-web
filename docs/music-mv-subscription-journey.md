# 订阅体验路径（2026-10-04）

- 已认证账号首次读取积分或提交生成时，幂等发放 10 体验积分；登录后的全站积分请求会触发领取。账号只领取一次，不按天补充，不设有效期。
- 复用 `music_mv_billing_grants`，来源 `internal`、支付幂等键 `welcome:<userId>`、套餐 `welcome`。该来源不占用支付客户或订阅记录。
- 现有表要求 `period_end > period_start`，体验记录以 0/1 作为兼容占位；余额与预占 SQL 对这一来源明确豁免过期判断，不能把 1 当成体验到期时间。对外 `periodEnd` 只计算月度积分，`welcomeRemaining` 单独返回永久体验余额。无需改表或执行数据库迁移。
- 扣分优先使用最早到期的订阅发放，最后使用体验积分。每次生成仍为 10 积分，失败释放原预占；并发和重复任务由数据库条件写入保证不超扣。
- `available` 表示积分功能开启；`checkoutAvailable` 单独表示支付配置可用。读取/使用体验积分不依赖 Stripe；数据库异常仍返回错误，不伪造零余额。
- 创建新 Stripe Checkout 后，成功返回本地化 `/account?billing=return&checkout_session_id={CHECKOUT_SESSION_ID}`，取消返回 `/account?billing=canceled`。已创建的旧 Checkout 保留创建时的地址。
- 返回页通过 `/billing/status?checkout_session_id=...` 核对该会话的账号、Stripe 客户和环境，再检查已付款状态及对应 Webhook 发放记录。返回页不发积分，永久体验余额不能充当付款成功依据。
- 订阅状态新增 `plan`（starter/creator），用于前台标记当前套餐。
- `MUSIC_MV_BILLING_MODE` 默认 `test`。只有显式设为 `live` 且使用正式密钥时才能正式支付；密钥、价格、门户、Webhook 必须来自同一环境。Webhook 及 Invoice 的 livemode 必须匹配。不在此任务自动切换正式收费或修改线上配置。

验证：BillingRepositoryTest、BillingSettingsTest、MusicBillingServiceTest、MusicBillingControllerTest、AiMusicGenerationServiceTest，共 44 项通过。包括永久体验重复领取、未来余额、先扣到期积分、释放后恢复、Webhook 幂等、跨账号支付确认、测试/正式环境隔离与生成扣费合同。未调用真实 Stripe 或真实歌曲生成。
