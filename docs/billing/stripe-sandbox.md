# Suno Film：Stripe 沙盒接入

## 当前边界

仅支持 Sandbox。正式环境密钥会被拒绝；默认不开启计费。不能将此版本作为正式收费上线验收。

已调用官方 `stripe_implementation_planner` 并接受方案，guide_id 为 `iguide_61VRiEFDUDAuX9jPW41HvJKvqgg1d`：网页、托管 Checkout、固定月费、提前付款、Customer Portal、账期末取消、不做期中升降级。音乐积分由网站后台管理，不使用后付费用量计费。视频制作不扣音乐积分。

Stripe Java SDK 33.4.2，固定 API 2026-08-26.dahlia；版本来自本次 Maven Central 稳定版清单和 SDK 常量。不要使用技能文件里较旧的 API 版本创建回调。

## 已创建的沙盒资源

账户：`acct_1UIK5yHvJKvqgg1d`（bitestar 沙盒，livemode=false）。

| 套餐 | USD/月 | 积分/账期 | Price ID |
| --- | ---: | ---: | --- |
| Starter | 9.99 | 300 | price_1UIKOgHvJKvqgg1dqPdGhy17 |
| Creator | 29.99 | 1000 | price_1UIKOrHvJKvqgg1dBa9Jy3JQ |

Portal configuration：`bpc_1UIKQNHvJKvqgg1dPfuO3g15`。允许查看账单、更新支付方式、账期末取消，不允许更改套餐。充值包暂未接入。

## 后端配置

将下列变量配置在后端进程环境。密钥不要提交 Git，不要发到聊天里。MCP OAuth 仅授权助手访问 Stripe，不能代替网站服务端 API 密钥。

```dotenv
MUSIC_MV_BILLING_ENABLED=true
STRIPE_SECRET_KEY=填写本沙盒的服务端测试密钥
STRIPE_WEBHOOK_SECRET=填写本回调端点或本地监听器的签名密钥
STRIPE_STARTER_PRICE_ID=price_1UIKOgHvJKvqgg1dqPdGhy17
STRIPE_CREATOR_PRICE_ID=price_1UIKOrHvJKvqgg1dBa9Jy3JQ
STRIPE_PORTAL_CONFIGURATION_ID=bpc_1UIKQNHvJKvqgg1dPfuO3g15
MUSIC_MV_BILLING_SITE_URL=http://localhost:3000
```

先对明确指定的测试 D1 数据库执行 `src/main/resources/db/music-mv-billing-schema.sql`，再启用。建表不在应用启动时自动执行。使用独立测试部署和测试数据库，不能将沙盒购买发放到正式用户账户。本站既有音乐供应商接口不随 Stripe 沙盒自动变成免费；支付测试不要顺带发起真实收费音乐生成。

本轮未提供密钥，未执行 D1 建表，未修改运行服务配置或重新启动服务。

## 回调与路由

- `GET /api/music-mv/v1/billing/capabilities`：是否可用、沙盒标识。
- `GET /api/music-mv/v1/billing/credits`：当前用户可用积分、预占积分和到期时间；只读本地账本，不查询 Stripe。
- `GET /api/music-mv/v1/billing/status`：当前用户余额与订阅状态。
- `POST /api/music-mv/v1/billing/checkout`：仅接收 plan、locale，用户来自会话，价格来自后台白名单。
- `POST /api/music-mv/v1/billing/portal`：只进入当前用户的 Portal。
- `POST /api/music-mv/v1/billing/webhook`：原文验签，不需要网站登录。

前端代理保留原始流和 Stripe-Signature。公开回调 URL 为 `https://你的测试网站/api/music-mv/v1/billing/webhook`，必须直达处理器，不经过登录重定向。Webhook API 版本固定为上述 SDK 版本。

监听 `customer.subscription.created`、`customer.subscription.updated`、`customer.subscription.deleted`、`invoice.paid`、`credit_note.created`、`charge.refunded`、`charge.dispute.created`。本地可使用 Stripe CLI 将这些事件转发到 `http://localhost:8080/api/music-mv/v1/billing/webhook`，使用监听器输出的签名密钥。远程 Stripe 不能直接访问 localhost。

## 积分规则

- 只根据验签成功后重新读取的已付账单发额度；不根据返回页面、前端价格或 Checkout 成功跳转发放。
- 按账单行覆盖的真实账期发放，按支付渠道 + payment_id 幂等；月度额度不结转，账期结束失效。
- 创建生成任务前，在单条条件写入中检查有效额度并预占，用户+request_id 唯一。并发请求不能超额。
- 每次生成预占 10 积分，通常返回两个歌曲版本；成功只扣一次，明确失败返还 10 积分；结果未知保留占用，不能因为接口超时自动返还。
- 读取余额或开始生成时，根据持久化最终任务状态补偿未完成的结算。
- 如果预占成功但建任务前数据库/进程故障，保留占用并阻止重复提交，需要人工核对；不自动冒险重新调用供应商。
- 首版退款、贷项通知或争议会撤回对应账单剩余额度，包括部分退款。撤回记录独立保存，即使通知先于发放，也不能随后误发。
- 争议撤销、贷项撤销的自动恢复未实现，需核对后处理；正式上线前需要确定部分退款和恢复政策。
- 重复通知只记一次；业务中断不确认回调，让 Stripe 重试。可在 Stripe Workbench 重发失败事件；尚无独立后台重放界面。
- 当前读取订阅状态使用 Stripe 实时 API；供应商暂不可用时会明确返回错误，不伪造账户状态。
- 同一用户跨渠道共用一个订阅/结账入口，未结束的 Checkout 使用同一幂等键和同一返回地址；另一套餐需等待订单过期，防止并行买两份订阅。

## 验证与尚未完成

已覆盖本地 SQLite 实际额度 SQL、Webhook 签名/过期签名/正式事件拒绝、重复发放、失败重试、同用户订阅冲突、账号鉴权、退款先到、官方 SDK 请求参数、额度不足时不调用音乐供应商。前端覆盖支付跳转白名单、生成拒绝恢复、中英文检查和浏览器模拟接口交互。

仍需配置沙盒 API 密钥、签名密钥、初始化测试数据库，然后完成真实网站登录→沙盒付款→Webhook到账→额度扣减/返还→续费失败/取消/退款的验收。当前本地测试与 MCP 成功创建套餐不等于真实网站支付闭环通过。

正式上线前另需完成商户资料、隐私/条款/退款政策、税务设置、Smart Retries与邮件设置、监控/人工对账、异常任务恢复、正式模式实现及独立验收。不要只把测试密钥替换为正式密钥。

## 官方参考

- https://docs.stripe.com/billing/subscriptions/build-subscriptions?payment-ui=checkout&ui=stripe-hosted
- https://docs.stripe.com/customer-management/integrate-customer-portal
- https://docs.stripe.com/webhooks

## 全新系统初始化

当前尚未正式上线，直接采用积分模型，不维护旧次数系统、不做数据换算或历史用户兼容。只保留一份建表入口：`src/main/resources/db/music-mv-billing-schema.sql`。

1. 在指定的全新测试数据库执行建表脚本。
2. 配置 Stripe 沙盒密钥、价格与回调，然后部署前后端。
3. 验收登录、测试付款、积分到账、生成扣分、失败退还、续费与取消。

Starter 每月 300 积分，Creator 每月 1000 积分；每次生成预占 10 积分，成功消费、明确失败退还。月度积分到期不结转；取消续费后本期仍可使用。本地视频导出免费，不增加充值包或新的积分类型。

每笔任务明确写入积分数，不依赖数据库默认扣分值。保留支付回调去重、任务预占和退款撤回，防止重复发放、超扣与失败误扣。过期账期的退还仍只回到原账期。

本次仅调整代码和初始化说明，未删除或修改任何远程数据库数据，未部署或开启正式收款。

## 支付渠道与积分模型

当前只接通 Stripe 沙盒；PayPal 尚未实现，不展示入口。新建表直接采用以下模型，不提供旧结构迁移。

| 表 | 职责 | 唯一范围 |
| --- | --- | --- |
| customers | 网站用户对应渠道账户 | 用户 + 渠道；渠道 + 外部账户 ID |
| subscriptions | 当前订阅归属和结账占用 | 每用户一条，跨渠道共用 |
| grants | 每次付款获得的积分及有效期 | 内部 grant_id；渠道 + 外部付款编号 |
| reservations | 任务预占、消费、退还 | job_id；用户 + request_id；关联内部 grant_id |
| events | 已成功处理的支付通知 | 渠道 + event_id |
| revocations | 退款先到时记录撤回 | 渠道 + payment_id |

表名均以 `music_mv_billing_` 开头。Stripe 的 payment_id 当前取已付账单 ID；未来渠道需选取每期付款的稳定唯一编号，不能以订阅 ID 代替，否则续费无法再次发积分。积分消费无需读取渠道账户或外部付款编号。

订阅表中的状态是渠道查证后的快照。Stripe 结账前、查询账户时和相关回调到达时，重新查询当前订阅，避免旧回调覆盖新状态。取消续费但本期未结束仍保留订阅归属；渠道确认订阅结束才释放。跨渠道结账占用使用同一条条件写入；其他渠道的订阅或有效结账不能被覆盖。

未来接 PayPal：补充其结账、验签、付款核实和订阅状态读取；核实后调用共用账本的发放/撤回方法，并使用共享订阅入口。必须验证渠道间同时结账、通知乱序和每期去重。无需复制积分表，不需要通用支付插件框架。前端支付跳转目前仍只允许 Stripe，接入 PayPal 时另行增加经过验证的跳转及管理入口。
