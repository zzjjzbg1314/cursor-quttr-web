# 订阅后端全场景测试用例与覆盖审计

审计日期：2026-10-08。适用版本：当前工作区的订阅实现。范围：网站后端 `cursor-quttr-web` 的计费接口、Stripe 网关、D1 账本、音乐生成扣费及前端服务端代理的直接接口合同。

本文件是可执行的测试设计与现状审计，不是“所有用例已通过”的报告。每行均给出输入/操作、响应及账本断言；新增设计尚未全部转换成自动化代码，也未操作真实用户、远程数据库或真实支付。既有测试主要为 Mock 服务、官方 SDK 对本地 HTTP 服务、实际 SQL 对本地 SQLite；不能替代部署环境的 Stripe/D1 验收。

## 1. 当前业务基线

- Starter：月付 USD 9.99，300 积分；Creator：月付 USD 29.99，1000 积分。应用只接受套餐枚举，渠道价格使用后端配置。页面金额和 Stripe Price 的实际金额/币种必须另行核对；配置前缀通过不代表远程对象正确。
- 当前为自动续费月订阅。每账号永久体验积分 10；每次音乐生成预占 10，成功消费、明确失败释放、受理结果未知保留占用。两个候选歌曲只收一次费用。
- 月度发放以已付发票 ID 为幂等键，仅支持 `subscription_create`、`subscription_cycle`。按账单行真实账期生效，到期不结转；体验积分独立保留。
- 取消续费不撤回当期已付积分；Stripe 确认订阅结束才允许重新订阅。欠费、暂停、待付款均不能通过购买另一份订阅绕过。
- 没有充值包、年付购买、主动期中升降级接口，也没有试用购买入口。试用、暂停等外部状态仍必须安全处理。
- 退款、贷项、争议目前撤回对应整张发票的额度，包括部分退款；不修改已经消费的任务记录。争议胜诉、退款失败、贷项撤销不自动恢复。此为现状边界，不代表最终商业政策已确定。
- 正常 status 可核对最新发票补偿漏发；显式返回参数核对指定 Checkout。不能仅凭 URL、订阅 active 或已有体验积分确认付款。
- 当前代码已允许显式 live 模式且密钥前缀匹配；**不代表正式收费验收已完成**。旧文档中“仅支持沙盒”“返回 account”“返回页不补偿”“切换套餐只能等过期”等描述已经落后于代码。本文件采用当前源码，正式验收仍需独立完成。

## 2. 测试环境、数据与统一断言

### 2.1 环境分层

| 层级 | 方法 | 能证明什么 | 不能证明什么 |
| --- | --- | --- | --- |
| L1 | JUnit/Mockito，构造原始签名通知 | 分支、校验、调用顺序、失败时不确认事件 | 渠道真实状态、D1 并发语义 |
| L2 | 实际 SQL + SQLite；SDK + 本地 HTTP 服务 | SQL约束、账期、SDK参数与幂等键 | 生产D1网络/事务、Stripe账户配置 |
| L3 | 隔离部署 + 独立D1 + Stripe沙盒 | 代理、登录、真实Price、回调、Test Clock续费闭环 | 正式银行卡收单与生产运营策略 |
| L4 | 另行安排的正式上线验收 | 正式配置、监控、渠道政策 | 本次未执行 |

测试必须使用用户 A/B、独立客户 C_A/C_B、独立订阅 S_A/S_B、发票 I1/I2 和事件 E1/E2。不得复用正式用户的数据构造取消、退款或争议。测试密钥从进程环境读取，证据中不记录密钥、完整卡号、完整支付URL或个人邮箱。

时间参数使用固定 UTC 秒：T0=2000000000，T1=T0+1000，T2=T1+1000。仓储方法可传入固定 now；Service 使用 Instant.now 的用例以相对未来账期构造，不靠等待一个月。端到端续费用 Stripe Test Clock，若原客户不支持关联时钟，应新建独立时钟客户并从初次购买开始，不修改旧客户冒充时钟场景。

### 2.2 所有用例必查的不变量

1. 数据只属于认证账号。外部客户端 token 是服务入口凭证，不能代替用户登录；请求体 userId/customerId/credits/priceId 不决定归属或额度。
2. `(provider,payment_id)` 每发票最多一笔 grant；`(provider,event_id)` 最多一笔成功处理记录；`(user_id,request_id)` 最多一笔 reservation；每用户最多一条共享订阅入口。
3. 未核实付款不得发放；grant 的用户、渠道、发票、订阅、套餐、额度、真实账期必须一致。Webhook 原文验签、环境匹配、业务成功后才标 processed。
4. 可用余额 = 当前有效、未撤回 grant 额度之和 − 这些 grant 下 reserved/consumed 总和。边界是 `start <= now < end`；internal/welcome 的结束占位不表示真实过期。
5. 任务成功只消费一次；终态不因重复/迟到回调反转；过期 grant 的释放不变成下一期可用积分。
6. 失败前后同时断言“不该发生的调用”：不得重复创建可支付订单、跨账号发放、重复音乐提交、盲目清空未知占用或恢复已撤回额度。
7. 每次故障注入必须断言数据库失败后的恢复结果，而不只检查 HTTP 状态。恢复重用原事件/原发票/原请求号。

### 2.3 覆盖标记

- **已覆盖**：既有自动化覆盖这一行的核心断言，见第 5 节方法索引；不意味着该行的全部参数组合或真实环境已验收。
- **部分**：存在相关测试，但本行完整组合、HTTP入口或故障点尚未直接验证。
- **待补**：设计已写出，未找到对应直接自动化证据。
- **风险**：源码显示限制或缺少防护；表中预期是安全目标/需决定的政策，不可标通过。
- **沙盒**：需要 L3 真实集成；本次不执行。

P0：重复收费、错发/超扣、跨账号、不可恢复账务；P1：核心生命周期与故障恢复；P2：兼容、展示合同及运维体验。任何 P0 风险未关闭不得以测试数量宣称整体通过。

## 3. 全场景用例

每行是一个独立用例。分号分隔连续步骤；表中的“无写入”不包括正常认证自身维护。应分别保存响应、渠道查证快照、操作前后D1快照及调用次数；以下所有金额/积分断言都须同时验证 B 账号完全不变。

### A. 配置与能力开关

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| A01 | P1 | billing.enabled=false；读 capabilities/credits/status/history；调用 reserve/settle | available=false；不访问计费表；不调用Stripe；history为空 | 已覆盖 |
| A02 | P0 | enabled=true；Stripe配置为空；A首次读credits | 体验10到账一次；checkoutAvailable=false；支付调用503 BILLING_UNAVAILABLE | 已覆盖 |
| A03 | P0 | test模式配live key；live模式配test key | 支付不可用且无订单写入；不能自动切换正式收款 | 已覆盖 |
| A04 | P1 | 参数化 sk/rk 测试/正式、未知mode、null/空key | 仅明确匹配模式及完整配置可ready；拒绝其他组合 | 部分 |
| A05 | P1 | 分别缺webhook/price/portal；无效前缀 | requireReady返回503；无Stripe调用 | 部分 |
| A06 | P0 | price存在但币种/金额/周期/账号错误；实际打开Checkout | 必须与页面USD月付9.99/29.99一致；错误对象不能完成错误收费 | 沙盒 |
| A07 | P0 | starterPrice与creatorPrice配置成同一个ID | 安全目标：配置失败，不能Creator收费后发Starter额度；当前ready未检查相同ID | 风险 |
| A08 | P1 | 合法https站点、localhost/127.0.0.1 http、尾斜杠；生成URL | 返回规范本地化pricing；不出现旧account或重复斜杠 | 部分 |
| A09 | P0 | siteUrl为外部http、带凭据/query/fragment、无host、格式破损 | 拒绝不安全URL；无渠道创建；应有稳定错误响应 | 部分 |
| A10 | P1 | 九语言、大小写、null、未知locale、路径注入 | 九语言保持；其他回落en；不允许外部跳转或拼接query | 已覆盖 |
| A11 | P0 | test/live同库且同user复用既有customer/subscription | 安全目标：环境隔离，不能误查客户或错标账务；表只存provider未存mode | 风险 |
| A12 | P1 | 数据库未建表/缺权限/缺迁移，enabled=true | 明确失败不返回伪造0或成功；发布前阻止使用不完整结构 | 待补 |

### B. 登录、接口合同与权限

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| B01 | P0 | 未登录参数化credits/history/usage/status/checkout/portal/ack | 401 AUTH_REQUIRED；billing service无调用，账本无写入 | 部分 |
| B02 | P0 | 过期/篡改会话、登出后旧cookie；访问上述接口 | 401；不能用旧cookie读余额或发起购买 | 待补 |
| B03 | P0 | 缺失/错误X-Music-Mv-Client-Token；携带合法用户cookie | 按入口token配置拒绝；不进入账务；正确token仍需登录 | 部分 |
| B04 | P0 | A登录body伪造userId=B/customerId=C_B/priceId/credits | owner只取A；price只取plan配置；B数据完全不变 | 部分 |
| B05 | P0 | A查询B的合法Checkout ID | 404 BILLING_CHECKOUT_INVALID或安全冲突；不读发票发放、不泄露B | 已覆盖 |
| B06 | P1 | status sessionID为空、超长、路径字符、无cs_前缀 | 非空非法ID400；不调用checkoutSession；空值正常status | 部分 |
| B07 | P1 | checkout plan null/空/unknown/年付/充值包 | 400 BILLING_PLAN_INVALID；无客户或订单创建 | 待补 |
| B08 | P1 | POST空body/坏JSON/类型错；GET offset非数字；错误HTTP方法 | 稳定4xx，不出现500、原始堆栈；不得产生账务副作用 | 待补 |
| B09 | P0 | 第三方Origin跨站提交checkout/portal；伪造客户端头 | 按实际cookie/CSRF/CORS策略阻断；不能仅以token转发视为通过 | 待补 |
| B10 | P1 | 无登录但读取capabilities、合法签名webhook | 两者可用；webhook不要求网站会话；仍严格验签 | 部分 |
| B11 | P1 | 模拟所有ApiException与非ApiException账务错误 | ApiException保留code/retryable；其他错误也需安全且稳定，不能泄漏凭据 | 部分 |
| B12 | P1 | 重复ack、未知notice ID、伪造ID | 兼容ack无DB访问、不发放新积分；仍要求登录 | 已覆盖 |
| B13 | P0 | 短期访问凭证到期但长期登录cookie有效；调用checkout | 先刷新访问凭证再重试同动作；长期cookie不能直接执行受保护支付，不能发起两笔订单 | 部分 |
| B14 | P0 | 合法签名访问cookie、长期会话已撤销；点击portal/checkout | 按当前访问凭证撤销窗口设计验收；不得宣称所有API实时查DB验登录；登出清两cookie | 部分 |
| B15 | P1 | A/B切账号、session刷新、两个浏览器同时登出其中一个 | user不串；仅撤销对应会话；余额响应不可跨账号缓存 | 部分 |
| B16 | P1 | 长期cookie缺失/短cookie无效，匿名浏览价格页再点击购买 | 公共价格页可看；支付接口要求登录；不提前写客户/checkout | 部分 |

### C. 创建、恢复与切换Checkout

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| C01 | P0 | A无客户；首次购买Starter | 同用户客户幂等键；保存唯一customer；共享slot；mode=subscription、quantity=1、服务器Price | 部分 |
| C02 | P0 | A无客户；首次购买Creator | 同C01；应使用CreatorPrice；最终paid仅授1000，不授300 | 部分 |
| C03 | P0 | 同账号双标签同时首次购买同套餐 | 最多一个可支付Checkout；同slot/token幂等；DB唯一约束，另请求安全恢复/冲突 | 部分 |
| C04 | P0 | 两节点同时买不同套餐 | 最多一个open订单；严格先关闭旧单再新建；不能出现双订阅 | 待补 |
| C05 | P1 | 已有owned open同套餐；再次checkout | 返回原URL；保存原token，不重置到期时间；不新建订单 | 已覆盖 |
| C06 | P0 | create被Stripe受理但响应超时；同请求重试 | 保留token，用原参数及幂等键恢复，不能换key再建 | 已覆盖 |
| C07 | P0 | 创建成功但saveCheckout数据库失败；进程重启重试 | 恢复同会话并保存；渠道最多一单 | 部分 |
| C08 | P1 | 原订单剩余不足30分钟；无本地checkout_id | 原参数重放找回；不能因为本地剩余短自动丢单 | 已覆盖 |
| C09 | P0 | Stripe明确expires_at参数拒绝；查询无匹配且分页完整 | 查证后清旧slot，建立新单；不把普通超时当可清理依据 | 已覆盖 |
| C10 | P0 | 恢复列表has_more=true/缺has_more/非array | BILLING_REVIEW_REQUIRED；不清占用、不新建 | 已覆盖 |
| C11 | P0 | 恢复列表同用户同expires匹配两个会话 | 人工核查冲突；不任取一个或清slot | 待补 |
| C12 | P0 | Starter open切Creator；再次点击 | 查旧归属；expire旧单成功；确认expired；再创建Creator | 已覆盖 |
| C13 | P0 | expire超时/失败/返回仍open | 保留旧占用；不创建另一可支付订单 | 已覆盖 |
| C14 | P0 | expire时用户并发支付旧单变complete | 不清单、不新买；刷新已发生订阅，进入确认/管理 | 已覆盖 |
| C15 | P1 | 已知expired旧会话；购买 | 查无当前订阅后释放旧token并重建 | 已覆盖 |
| C16 | P1 | owned open旧返回/account；同套餐购买 | 先expire再新建返回pricing；已支付旧会话不重建 | 已覆盖 |
| C17 | P0 | open URL为空；另账号/客户/环境/mode错误会话 | 拒绝；不保存错误URL、不expire他人单 | 部分 |
| C18 | P0 | 有active/trialing/past_due/unpaid/paused/incomplete当前订阅 | 409 BILLING_ALREADY_SUBSCRIBED；没有checkout创建/slot替换 | 部分 |
| C19 | P0 | canceled/incomplete_expired旧complete；无其他订阅 | 核实旧sub ID/customer/mode、再次查无进行中订阅，再释放旧token购买 | 已覆盖 |
| C20 | P0 | 旧complete但sub未结束/外部归属/缺sub ID | 保留占用；不能凭本地过期时间释放已付订单 | 已覆盖 |
| C21 | P0 | 原订阅结束查证后另节点同步新subscription或token | clear条件写入不能擦新数据；最多3轮后安全409 | 部分 |
| C22 | P0 | 另一支付渠道有订阅/未过期slot；尝试Stripe | 禁止；不调用渠道建单或覆盖归属 | 已覆盖 |
| C23 | P1 | 另一渠道过期slot且无订阅；尝试Stripe | 条件claim才允许接管；临界expires=now需专测 | 部分 |
| C24 | P0 | Stripe客户create成功DB写失败；重复请求 | 固定客户幂等键恢复；不得把重复客户映射给另一账号 | 待补 |

### D. 首购、返回页与漏发补偿

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| D01 | P0 | Starter paid发票通知成功；读status/credits/history | grant300+welcome10=310；唯一发票grant、唯一成功event | 部分 |
| D02 | P0 | Creator paid首购 | grant1000+welcome10=1010；不是累计两档额度 | 部分 |
| D03 | P0 | complete但unpaid、open、expired、payment mode；返回status | paymentConfirmed=false；无invoice发放；已有体验余额不算付款 | 已覆盖 |
| D04 | P0 | 手工拼billing=return/成功query但会话未付 | 不能发额度或确认支付；以服务端渠道查证为准 | 部分 |
| D05 | P0 | paid Checkout、Webhook漏失；显式sessionID查询 | 核实customer/user/sub/invoice/mode；grant一次；重读余额并确认 | 已覆盖 |
| D06 | P0 | paid Checkout、无query；本地已存旧会话ID | 兼容恢复原会话并补偿；没有新checkout创建 | 已覆盖 |
| D07 | P0 | 正常status发现current.latest_invoice已付但未发 | 补最新发票；返回余额更新；不查原首购会话；不泄漏_latestInvoice | 已覆盖 |
| D08 | P0 | 已有grant；反复status/重放Webhook/两个不同event同invoice | 总grant仍1；不得再加300/1000；事件去重与发票去重分别验证 | 部分 |
| D09 | P0 | paid Checkout的invoice ID/customer/sub或owner不符 | 冲突/拒绝；不发、不确认、不修改B余额 | 已覆盖 |
| D10 | P0 | 发票live缺失/不符、open/draft/void/uncollectible | 显式补偿拒绝；日常未付发票不发放；无假成功 | 已覆盖 |
| D11 | P0 | grant已撤回；再刷返回页/普通status/重放invoice.paid | 唯一grant仍revoked；不恢复额度；paymentConfirmed不因余额而true | 部分 |
| D12 | P1 | 只有永久体验10；订阅刚created且未付 | 不显示付款已确认；月度grant为0 | 部分 |
| D13 | P0 | 连续漏两期以上通知；最新发票已付 | 安全目标：定位全部漏期；当前只自动补最新一期，历史漏期需人工对账 | 风险 |
| D14 | P0 | 旧订阅已结束且末期通知漏；无保存checkout/用户不访问 | 需要对账发现；当前没有全量补偿作业，不应假定全覆盖 | 风险 |
| D15 | P1 | 支付成功后浏览器关页/换设备/重启服务 | Webhook独立到账；重新登录读正确余额，不依赖旧tab | 沙盒 |
| D16 | P1 | 真实成功/取消返回九语言URL | pricing存在、session参数保留；取消不误确认、不产生grant | 沙盒 |

### E. 发票解析、环境与金额边界

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| E01 | P0 | paid create/cycle，Starter/Creator已知price，quantity=1 | 300/1000，使用真实行period；完整grant字段正确 | 已覆盖 |
| E02 | P0 | 未知price、产品ID冒充price、缺price | 502 BILLING_REVIEW_REQUIRED；不grant、不processed；配置修正可重试 | 已覆盖 |
| E03 | P0 | quantity 0/2/null/缺失/非整数/1.5 | 安全目标：仅整数1允许；当前asInt转换缺少原始数值类型/精度验证 | 风险 |
| E04 | P0 | 两条可授额度行/额外非proration费用行 | 拒绝歧义；不拿首行发额度，不先写后报错 | 已覆盖 |
| E05 | P0 | 只有proration或空lines；create/cycle | 缺额度行拒绝；不能发月额度 | 已覆盖 |
| E06 | P0 | paid reason=subscription_update/manual等 | 不授月度；仍可确认已成功处理支持的忽略事件 | 已覆盖 |
| E07 | P0 | proration + 一条正常月费行 | 只取正常行；总授一次月度，不加差价额度 | 待补 |
| E08 | P0 | lines.has_more=true | 不processed；必须核查完整数据；不能只看第一页发放 | 部分 |
| E09 | P0 | 缺sub、line/invoice sub冲突 | 拒绝；没有grant或确认事件 | 已覆盖 |
| E10 | P1 | line sub缺失但invoice parent有合法sub | 正确使用parent；账期来自正常行 | 部分 |
| E11 | P0 | start<=0、end<=start、缺period、负值/巨值 | 非法账期拒绝；真实合法未来账期可保存但不到点不生效 | 部分 |
| E12 | P0 | 合法signed通知，远端invoice.id空/与事件ID不同 | 安全目标：重新读对象身份必须一致；webhook grant路径未显式比对ID | 风险 |
| E13 | P0 | price匹配但币种/价格间隔错误，发票amount_paid=0 | 当前按price+paid发额度；需明确折扣/零额/账单外结清政策并验证，不能称实际扣款成功 | 风险 |
| E14 | P1 | 税项、折扣、账户余额抵扣，仍正常月付单行 | 渠道账单金额按配置；额度不因税率/折扣重复增加；异常额外行需评估兼容 | 沙盒 |

### F. Webhook安全、幂等、乱序与中断

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| F01 | P0 | 缺签名/错secret/正文改一个字节/非JSON | 400 BILLING_SIGNATURE_INVALID；无DB写入、Stripe调用或processed | 部分 |
| F02 | P0 | 过期签名；合法重送重新签名 | 旧签名拒绝；新签名按发票/事件幂等，不按旧timestamp放行 | 已覆盖 |
| F03 | P0 | event.livemode缺失/错模式；ID空 | 400 BILLING_MODE_INVALID/BILLING_EVENT_INVALID；无账务变动 | 部分 |
| F04 | P0 | 完全相同event重复/并发到达 | 成功event最多1、grant最多1；并发时DB约束有效 | 部分 |
| F05 | P0 | 不同event同paid invoice；先status补偿再通知 | invoice唯一键保证一次；每合法事件可有独立processed | 部分 |
| F06 | P0 | 通知invoice.paid而Stripe返回未付/错环境 | 不授且不processed；返回非2xx，允许渠道重试 | 已覆盖 |
| F07 | P0 | Stripe查询异常/超时；再次同event请求 | 首次不processed，重试恢复一次授予 | 已覆盖 |
| F08 | P0 | DB grant前失败/后processed前失败 | 不成功确认；重试不重复授；后一种已有grant应恢复event | 待补 |
| F09 | P0 | revoke批次中断/processed写入失败 | 重试补齐撤回；旧额度不被重放支付恢复 | 待补 |
| F10 | P0 | 旧subscription.updated到达晚于deleted/新状态 | 重读渠道当前状态；不得旧payload覆盖新状态 | 已覆盖 |
| F11 | P0 | subscription事件customer尚未映射 | 失败且不processed；映射恢复后可重试，不建错误owner | 部分 |
| F12 | P1 | 不支持的事件类型，包括payment_failed/action_required | 当前忽略账务并记processed；不可误发。状态靠updated/status刷新，不等于已实现失败通知 | 待补 |
| F13 | P0 | paid和refund顺序反转；并发各100次本地注入 | 最终grant撤回且事件唯一；两个执行顺序均一致 | 部分 |
| F14 | P1 | malformed缺data/object/type、缺invoice/charge/payment_intent | 安全目标：必要字段缺失不得静默处理成功；当前部分分支存在空字段忽略风险 | 风险 |
| F15 | P1 | 沙盒回调真实端点经www/CDN/WAF/代理 | 直达2xx，不301/308或登录重定向；保留原文及Stripe-Signature | 沙盒 |
| F16 | P1 | 冷启动/慢DB/网络超时，Stripe重复投递 | 所有重试最终收敛；无重复积分；保留失败证据与渠道投递记录 | 沙盒 |

### G. 订阅状态、续费、取消与到期

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| G01 | P1 | active + 未取消 + 最新发票已授 | status active/plan/periodEnd；无invoice二次查询/旧checkout查询 | 已覆盖 |
| G02 | P1 | cancel_at_period_end=true，账期未结束 | active保留、取消标记true；当期余额不变；不能买第二订阅 | 已覆盖 |
| G03 | P1 | 门户取消后恢复续费，重复/乱序updated | 渠道最新cancel=false；下次正常续期；不得撤回当期grant | 待补 |
| G04 | P0 | 到T1-1、T1、T1+1，旧月度余额尚有余 | T1前可用；T1起旧月度0；welcome仍在；历史不删除 | 已覆盖 |
| G05 | P0 | 到T1，paid renewal invoice I2到达 | I2新增300/1000；旧剩余额不结转；I1/I2各1grant | 部分 |
| G06 | P0 | 连续3个月正常扣款/更换月份长短/跨年 | 每真实账期独立grant；不推算30天、不重复发、到期边界正确 | 沙盒 |
| G07 | P0 | renewal失败，status past_due/unpaid、invoice open | 不授新额度；旧到期失效；welcome可用；保留订阅并管理账单 | 已覆盖 |
| G08 | P0 | renewal待认证/需换卡；门户修复后同invoice paid | 不提前授；成功后补本期一次；不新建并行订阅 | 沙盒 |
| G09 | P1 | 首付incomplete最终incomplete_expired | 过期无额度；渠道确认结束后允许新买，不沿用complete假成功 | 部分 |
| G10 | P1 | trialing，未付trial发票 | 不凭trialing发额度；只返回真实状态；未来paid转active才授 | 待补 |
| G11 | P1 | paused及pause_collection但status=active | 两种情况不混同；不自动授；当前snapshot不含pause_collection，展示政策需核查 | 风险 |
| G12 | P0 | canceled立即取消但grant尚在有效期 | 当前按grant有效期仍可用；明确是否允许此管理操作，不能把取消自动当退款 | 部分 |
| G13 | P0 | 正常到期canceled + 原complete订单残留 | status清当前subscription；新买核实旧单后可用 | 已覆盖 |
| G14 | P0 | Stripe有2个进行中订阅/重复订阅异常 | 安全目标：人工核查而不是任取首项；当前activeSubscription只选第一项 | 风险 |
| G15 | P0 | subscriptions has_more=true，或缺data/字段错误 | 分页已拒绝；畸形响应不能当无订阅清状态；后者待防护 | 部分 |
| G16 | P1 | item.current_period_end与本地grant.end不同 | 返回真实item日期；不以旧额度到期代替下次扣款 | 已覆盖 |
| G17 | P1 | 未知price/多个item/items分页或缺period_end | 不推测套餐与续期时间；需要review；当前unknown plan/date可null | 部分 |
| G18 | P1 | Stripe宕机，status失败；同时credits/history可访问 | 状态明确异常不假称订阅取消；账本读取仍独立；不清slot | 部分 |

### H. 门户、套餐变更及未支持业务

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| H01 | P0 | A有客户；调用portal九语言 | 仅C_A、固定configuration和本地化pricing return_url；无新grant | 待补 |
| H02 | P1 | 无customer的A调用portal | 404 BILLING_CUSTOMER_MISSING；不创建客户/订阅 | 待补 |
| H03 | P0 | 其他渠道拥有订阅/有效slot；调用Stripe portal | 阻止跨渠道管理；不把另一渠道当Stripe客户 | 部分 |
| H04 | P1 | 门户配置不存在/错误环境/返回URL空/渠道异常 | 安全错误且可恢复；不能返回200空URL造成无反应 | 部分 |
| H05 | P1 | 门户查看账单/更新卡/账期末取消/返回站点 | 权限与商户配置一致；回站同步真实状态；账本不多授 | 沙盒 |
| H06 | P1 | 已订阅选择另一档；直接调用checkout绕前台 | 409；当前不支持升级，应无错误差价扣款 | 已覆盖 |
| H07 | P0 | 管理员在Stripe外部改套餐产生差价发票 | subscription_update不发月额度；下一cycle按实际白名单price授；不允许静默改商业政策 | 部分 |
| H08 | P1 | 请求年付/一次性/充值包/优惠码试用购买 | 本站无入口且非法plan拒绝；不得暗自用已有月付处理 | 待补 |

### I. 积分预占、生成、结算与账期交叉

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| I01 | P0 | 新A反复credits/reserve触发welcome | welcome:<A>唯一；10一次，永久，不按日补；B独立 | 已覆盖 |
| I02 | P0 | A有效余额0/9/10/11；发起一次生成 | 0/9返回402 BILLING_QUOTA_EXHAUSTED且不submit；10/11预占10 | 部分 |
| I03 | P0 | 有月度和welcome；各生成一次 | 先最早到期月度，后welcome；reservation.grant_id准确 | 已覆盖 |
| I04 | P0 | 余额10，多节点同时100个不同request | 最多1次成功reserve/submit；余额非负；实际D1并发需实测 | 部分 |
| I05 | P0 | 同user/request重复生成；相同及不同内容 | 相同已建任务复用；不同内容409；预占/付费submit只一次 | 部分 |
| I06 | P0 | reserve成功但job建表/attempt/event前失败 | 保留占用、不重试供应商；未知孤立reservation可查，需人工核对 | 部分 |
| I07 | P0 | 供应商明确未受理；settle failed重复 | reserved→released一次；同原账期恢复10；无消费 | 部分 |
| I08 | P0 | 供应商超时/不明异常/空taskId/受理后保存失败 | 保持reserved/submission_unknown；不释放、不再次提交 | 部分 |
| I09 | P0 | 正常完成有两个候选歌曲；回调重复 | reserved→consumed一次；总耗10，非20；统计一任务 | 部分 |
| I10 | P0 | failed终态后completed迟到，反向也测试 | 结算不能反转；job真终态与reservation最终一致 | 部分 |
| I11 | P0 | job已completed/failed，结算写失败后读credits | refreshBalance按owner补consumed/released；其他用户不变 | 已覆盖 |
| I12 | P1 | job queued/unknown/缺失，读取/全局reconcile | 不误释放；缺失任务进入人工清单，不靠时间自动返还 | 部分 |
| I13 | P0 | T1前reserve，T1后failed释放旧账期 | 释放留在旧grant；新期余额不增加，welcome不凭空补发 | 已覆盖 |
| I14 | P0 | T1前reserve，T1后completed | 仍消费原grant；不再从新期重复扣；历史保留 | 部分 |
| I15 | P0 | 当前额度总额>=10，但分别两个grant各5 | 当前reserve单grant不跨grant凑额，返回不足；需确认是否保留此产品规则 | 风险 |
| I16 | P0 | 已撤回/未来/已过期grant仍有额度；reserve | 不可选这些grant；不超扣其他用户 | 已覆盖 |
| I17 | P0 | 退款时有reserved任务；后续成功/失败 | 不恢复撤回grant；已受理任务费用需运营核对；所有记录可追溯 | 部分 |
| I18 | P1 | credits连续读取；外部Stripe不可用 | 一次有序DB batch赠送→对账→余额；零Stripe查询 | 已覆盖 |
| I19 | P0 | refreshBalance batch缺项/某语句失败/DB超时 | 不返回未经核实余额；异常必须可追踪；无假0 | 部分 |
| I20 | P1 | billing禁用后生成/再启用；存量任务结算 | 当前禁用不reserve/settle；防止运行期错误切换让历史免费或悬挂，需上线操作约束 | 待补 |

### J. 退款、贷项、争议与风险事件

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| J01 | P0 | 全额退款charge.refunded，关联I1 | 根据payment_intent查invoicePayments；I1 revoked=1，余额扣除剩余；event成功后记录 | 部分 |
| J02 | P0 | 部分退款charge.refunded | 当前撤回整张发票剩余积分；必须明确政策，不能宣称按退款比例 | 风险 |
| J03 | P0 | credit_note.created I1；重复通知 | revoke先于processed；revocation唯一；I1剩余额度不可用 | 已覆盖 |
| J04 | P0 | refund/credit_note先到paid后到，或status补偿后到 | 后建grant默认revoked=1；不能恢复额度 | 已覆盖 |
| J05 | P0 | charge.dispute.created；查真实charge与invoice | 仅关联发票撤回；查失败不processed；其他账期不撤回 | 部分 |
| J06 | P0 | invoicePayments多行/has_more=true/无data字段 | 多行分别核查；分页不完整失败；缺字段不能当已处理 | 部分 |
| J07 | P0 | payment_intent缺失、charge关联错误/环境不符 | 安全目标：人工核查而不是成功忽略；查回charge环境缺显式验证 | 风险 |
| J08 | P1 | 已消费全部月额度后退款 | 历史消费保留；不生成负“返还”；撤回事实仍可查 | 部分 |
| J09 | P0 | refund失败/贷项作废/争议胜诉通知 | 当前无自动恢复；不能静默重新发积分；需要明确人工恢复流程 | 风险 |
| J10 | P0 | 退款一期后后续正常续费；再重放老paid | 老期仍revoked；新paid I2正常一次授；旧事件不能影响I2 | 待补 |

### K. 明细、统计与数据库完整性

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| K01 | P1 | history all及7种合法filter，多于20行 | 先过滤后分页；最多20，21条探测hasMore；稳定按时间/id排序 | 已覆盖 |
| K02 | P1 | offset -1/100000/100001；未知kind | 合法边界可读；非法400 BILLING_HISTORY_INVALID；无SQL拼接 | 部分 |
| K03 | P0 | A/B各有grant与任务；history/usage/generations | 只A；两候选歌曲不重复统计；title不串账号 | 已覆盖 |
| K04 | P1 | reserved/consumed/released/revoked混合 | released amount=0，不伪造+10新收入；revoked当前amount=0 | 已覆盖 |
| K05 | P1 | 过期/退款grant；查看earned/spent | 当前earned是全部历史allowance，spent仅consumed；不可等同净收入/当前余额 | 部分 |
| K06 | P1 | 时间相同多条；多页读期间新增记录 | 同快照顺序稳定；offset在实时新增时可能重复/遗漏，记录接口边界 | 待补 |
| K07 | P0 | paymentId跨provider相同；同provider重复/不同user | 跨provider各自幂等；同provider不可重复错发；共享订阅slot不能覆盖 | 已覆盖 |
| K08 | P0 | 错user/provider/token clear/save；并发sync | 条件写入不改新token/别账号/已有subscription | 已覆盖 |
| K09 | P0 | grant引用不存在/跨owner；数据库foreign_keys设置 | 安全目标：拒绝非法引用或检测孤立账务；本地SQLite测试并不等于D1约束实证 | 待补 |
| K10 | P1 | 初始化SQL执行两次；非空库升级/旧字段 | 幂等新建；不得用初始化替代增量迁移；保留历史不破坏唯一约束 | 待补 |

### L. 部署、性能、监控与恢复

| ID | 级别 | 准备与操作 | 接口/数据库预期 | 覆盖 |
| --- | --- | --- | --- | --- |
| L01 | P0 | 原始JSON含空白/Unicode，经网站代理发送有效签名 | 后端验签成功；不重新序列化；原字节、签名正确转发 | 沙盒 |
| L02 | P0 | 部署两地区/两节点；用户请求与webhook不同路由 | 同一业务账本/正确客户映射；不能回调到另一数据库导致丢发 | 沙盒 |
| L03 | P1 | Stripe 429/5xx/网络断开/坏JSON | 502稳定可重试；10s连接/20s读/最多1网络重试；slot保留 | 部分 |
| L04 | P1 | 代理后端不可达；回调目标重定向；页面超时 | 503可重试或明确失败；不假装成功；代理不吞错误；无无限loading | 待补 |
| L05 | P1 | DB耗时、Stripe耗时、批量刷新并发；记录p50/p95/p99 | 分别衡量credits与status；credits不依赖Stripe；status已授不重复查发票/首购；SLO由实测确定 | 待补 |
| L06 | P0 | 节点重启、版本混跑、配置更换；重发失败event | 幂等键与SQL合同兼容；老节点不能旧account返回或覆盖新账务 | 沙盒 |
| L07 | P1 | 触发渠道/DB失败；查日志响应监控 | 只安全type/status/code/requestId；可关联事件、发票、owner；不泄漏密钥/卡号/URL | 部分 |
| L08 | P0 | 漏通知、孤立reservation、不同Stripe/D1余额；执行对账 | 差异可定位、人工复核；不能直接改余额/删slot掩盖；缺后台恢复工具需记录 | 待补 |
| L09 | P1 | 一用户反复点击/刷status/webhook垃圾流量 | 合理限流、保护线程与渠道配额；合法Stripe重试不丢失；当前需部署层核查 | 待补 |
| L10 | P1 | 账期跨时区/DST、浏览器日期格式、九语言 | 后端UTC秒准确；客户端只格式化；实际收费账期不被本地时间改变 | 部分 |

## 4. 审计发现与优先级

| 发现 | 证据/范围 | 结论与下一步 |
| --- | --- | --- |
| 多期漏通知没有全量恢复 | status只核对current.latest_invoice或保存的Checkout | 最新期补偿已有测试；D13/D14必须加入人工对账和历史账单验收，不能称“支付不会漏” |
| 异常多订阅只取第一条 | activeSubscription遍历返回首个非结束状态 | G14需安全冲突检测；不能把分页检查当重复订阅检测 |
| 两套餐Price相同没有配置拒绝 | ready只检查前缀；allowance优先Starter | A07需配置校验与回归；属于可确认的防护缺口 |
| test/live表内无模式维度 | customer/grant/event等以provider隔离，未存mode | 应使用独立环境与DB；A11要求验收部署隔离，不能只检查key前缀 |
| Webhook对象字段完整性不足 | paid路径未比对重新读取invoice.id；refund缺payment_intent可直接processed | E12/F14/J07需畸形响应测试与安全失败策略 |
| 部分退款撤整期，恢复事件未接入 | revoke按invoice整体；没有dispute.closed/credit_note.voided恢复分支 | J02/J09是产品政策与实现边界；先决定恢复策略，不自行按比例改账 |
| 跨grant凑额未实现 | reserve挑一笔能覆盖全部credits的grant | I15用例明确现状；当前正常300/1000/10整十额度一般不触发，未来加购或补偿可能触发 |
| 状态未表示pause_collection | status返回status/cancel/plan/periodEnd | G11不能把暂停收款等同paused；明确是否允许运营启用 |
| 错误响应并非所有分支稳定 | Handler只处理ApiException/MethodArgumentNotValid；若干分支抛IllegalStateException | B08/B11需HTTP级测试，避免只断言Java异常就当用户收到可理解错误 |
| 本地测试与实际部署存在距离 | Mockito、SQLite、本地HTTP SDK，不调用真实Stripe/D1 | L3并发、原文代理、真实续费、退款、通知重送是独立发布验收门槛 |
| 旧说明和代码冲突 | subscription-journey、stripe-sandbox中的历史章节 | 历史保留为审计记录；新测试以本文件基线和当前代码为准，后续应整理部署指南 |

以上是代码边界和防护缺口；并未在生产环境复现所有异常，也没有把外部畸形响应假设当作已发生事故。本次不改变这些业务规则。

## 5. 现有自动化证据与执行结果

测试文件缩写：BS=BillingSettingsTest，SV=MusicBillingServiceTest，RP=BillingRepositoryTest，CT=MusicBillingControllerTest，GW=StripeGatewayTest，AG=AiMusicGenerationServiceTest。均位于 `src/test/java/com/example/cursorquitterweb/musicmv/` 对应包中。

核心映射：A→BS/SV配置测试；B→CT身份测试及SV归属测试；C→SV checkout恢复/切换/结束保护+RP条件写入+GW SDK；D/E/F/G→SV付款/通知/续费测试+RP发票唯一与撤回SQL；H目前门户专门测试不足；I→RP预占/结算SQL+SV reserve/settle+AG quota与提交安全；J→RP退款顺序与SV credit_note；K→RP明细统计；L→GW合同及代理代码，真实部署证据不足。

重要：SV的Mock测试里grant调用次数不是实际入账次数，真正去重另由RP的实际唯一约束验证；RP的重复SQL顺序执行也不是多进程D1并发测试。这两种证据不能混写成生产并发验收。

本次执行命令（全部离线/本地夹具，无真实支付/歌曲生成）：

```sh
mvn -q -Dtest=BillingSettingsTest,MusicBillingServiceTest,BillingRepositoryTest,MusicBillingControllerTest,StripeGatewayTest,AiMusicGenerationServiceTest test
```

自动化方法索引及真实执行计数在文末由当前源码与Surefire报告生成。既有AG整类包括部分歌曲工作台场景，因此总数不得称为“全部都是订阅用例”。

## 6. 必须完成的沙盒验收步骤

每项使用独立测试账号，记录渠道事件ID、发票ID、订阅ID、grant ID、UTC到账时间、回调HTTP状态和前后余额，不保存完整支付URL。

1. **Starter首购闭环**：登录→创建Checkout→核对USD/月付/金额→测试卡支付→返回pricing→验证grant300及welcome10。**先不打开status返回页**也应通过Webhook到账，以排除刷新补偿掩盖坏回调。再重送同通知，余额仍310。
2. **Creator独立首购**：另一账号同流程，应1010，核对另一个Price。已订阅Starter不能靠新Checkout升级。
3. **漏回调补偿**：仅在测试部署故意暂停回调接收→付款→通过status核实补偿→恢复并重送事件→只有一笔grant。不要停生产Webhook。
4. **续费三周期**：独立Test Clock客户初购→推进到账期结束→等待时钟ready及事件完成→核对I2/I3、真实period、旧余额不结转；每期重放一次通知验证幂等。
5. **续费失败与恢复**：使用Stripe官方测试支付方式构造拒付/需要认证→推进时钟→不授新期额度→更新方式/完成认证→同发票paid后只授一次。
6. **取消与恢复续费**：门户取消→当前期仍可用→恢复续费后标志恢复。另一个账号取消并推进期末，旧积分失效、重新订阅可用，无旧complete订单阻塞。
7. **退款及争议**：独立测试支付分别做全额退款、部分退款、贷项、争议；核对当前整体撤回政策，重送通知和paid不得恢复。恢复事件的人工处理边界单独验收。
8. **多节点并发**：隔离D1余额10，通过实际API并发不同请求号，音乐供应商使用可控Stub；最多一次受理。并发Checkout及Webhook同步验证唯一约束。
9. **网站代理与域名**：真实Stripe投递最终www URL，核对无308/登录拦截、签名原文完整、正确后端/数据库；九语言的付款和门户返回均pricing。
10. **错误恢复**：测试节点故意中断DB或Stripe调用，恢复后同事件/请求号继续，不能清账本换号“解决”。

真实测试卡、时钟限制、事件模型以Stripe官方文档为准；不用真实卡、不触发实际收费音乐生成。Test Clock加速支付时间不能替代后端余额查询时的真实now，离线时间边界仍应由固定时钟夹具验证。

## 7. 发布通过条件及执行记录模板

- P0安全断言全部有自动化及适用的L3证据；待决定政策需先明确；有风险的用例不能按预期猜测填“通过”。
- 首购两档、Webhook独立到账/重送、漏发补偿、正常续费、失败恢复、取消期末、再次订阅、退款撤回全部闭环。
- 同发票授予最多一次，余额无负值，B用户不变，所有已确认事件业务已成功或有明确安全忽略策略。
- 与生产路径一致的D1并发、原文代理、多节点路由通过；正式/live单独验收。
- 无真实外部调用的测试不能冒充第三方端到端；不能将未支持升级/充值的拒绝用例作为功能已实现。

每个用例执行记录：`ID / 版本commit / 环境 / 数据组 / 输入与操作 / 实际HTTP与code / 实际Stripe状态 / grant、event、reservation前后差异 / 调用次数 / 耗时 / 通过或失败或阻塞 / 证据路径 / 缺陷编号 / 清理结果 / 执行人及UTC时间`。

故障恢复原则：先定位未处理事件、未授发票或孤立reservation；只重送/补偿已核实对象；不得直接加余额、删除有效Checkout占用、恢复revoked grant或重跑付费生成。清理只针对本次专属测试账号，不删除真实账务历史。

## 8. 官方依据

- [订阅通知与状态](https://docs.stripe.com/billing/subscriptions/webhooks)：订阅变化异步，包含付款失败、需要认证、退款与争议；本站目前仅处理其中部分事件。
- [Test Clock](https://docs.stripe.com/billing/testing/test-clocks)：在测试环境推进时间以验证订阅生命周期。
- [退款](https://docs.stripe.com/refunds)：支持全额及部分退款；积分如何撤回是本站政策，需要独立断言。


## 9. 本次执行快照与现有方法索引

本文件共 **174 条独立场景用例**。设计数量与自动化方法数为不同口径。

设计覆盖标记统计：已覆盖55条、部分67条、沙盒12条、风险14条、待补26条。这些是证据/设计状态，非逐条执行结果。

| 测试类 | 执行 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| BillingSettingsTest | 1 | 0 | 0 | 0 |
| MusicBillingServiceTest | 44 | 0 | 0 | 0 |
| BillingRepositoryTest | 11 | 0 | 0 | 0 |
| MusicBillingControllerTest | 2 | 0 | 0 | 0 |
| StripeGatewayTest | 3 | 0 | 0 | 0 |
| AiMusicGenerationServiceTest | 20 | 0 | 0 | 0 |

合计 81 个现有自动化方法通过，0失败、0错误、0跳过；其中AG整类并非全部属于计费断言。未执行L3真实Stripe/D1、正式收费、真实歌曲生成或视频渲染。

### BillingSettingsTest

源码：`src/test/java/com/example/cursorquitterweb/musicmv/billing/BillingSettingsTest.java`。

- `returnUrlsPreserveAllSupportedLanguages`

### MusicBillingServiceTest

源码：`src/test/java/com/example/cursorquitterweb/musicmv/billing/MusicBillingServiceTest.java`。

- `reminderUsesBalanceAndLegacyAcknowledgementDoesNotAccessDatabase`
- `productionKeysAndIncompleteConfigurationRemainDisabled`
- `welcomeCreditsDoNotDependOnPaymentConfiguration`
- `paidCheckoutRepairsMissingGrantAndReturnsFreshBalance`
- `ordinaryStatusRecoversSavedOrderWithoutReturnQuery`
- `unpaidCheckoutNeverRetrievesInvoiceOrGrants`
- `foreignCheckoutOrInvoiceCannotGrant`
- `invalidInvoiceStateNeverGrantsDuringReconciliation`
- `latestPaidRenewalRepairsCreditsWithoutOriginalCheckoutAndDoesNotExposeInvoiceId`
- `failedOrPendingRenewalPreservesStatusAndNeverGrants`
- `foreignOrWrongEnvironmentRenewalCannotGrant`
- `paidProrationDoesNotReceiveMonthlyAllowance`
- `renewalCompensationFollowedByWebhookSharesSameInvoiceIdentity`
- `endedSubscriptionDoesNotReconcileInvoicesAndAllowsPlanSelection`
- `unknownPriceDoesNotAcknowledgeWebhookAndCanRetryAfterConfigurationFix`
- `malformedOrAmbiguousAllowanceDoesNotGrantOrAcknowledge`
- `liveModeMustBeExplicitAndMatchTheKey`
- `liveWebhookOnlyGrantsMatchingLiveInvoice`
- `reserveFailsClosedAndUnknownRequestsAreNotReleased`
- `disabledBillingDoesNotTouchUninitializedTables`
- `paidInvoiceGrantsFromServerPriceAndDeduplicatesEvents`
- `invalidStaleAndLiveEventsNeverGrant`
- `providerFailureDoesNotAcknowledgeEvent`
- `existingSubscriptionCannotPurchaseAnother`
- `creditNotesRecordRevocationBeforeEventAcknowledgement`
- `creditBalanceNeverCallsStripeAndIncludesPendingHolds`
- `subscriptionDateUsesStripeItemPeriodInsteadOfCreditGrantExpiry`
- `cancellationKeepsPaidPeriodAndDoesNotRevokeCredits`
- `anotherProviderSubscriptionBlocksStripeBeforeCreatingCheckout`
- `checkoutReadsSharedSlotAndVerifiesTheSameSession`
- `lostCheckoutResponseCanBeRecoveredWithinLastThirtyMinutes`
- `providerTimeoutPreservesOriginalTokenInsteadOfCreatingAnotherOrder`
- `changingPlanExpiresOldCheckoutBeforeOpeningNewOne`
- `legacyAccountReturnIsExpiredBeforeNewPricingCheckout`
- `failedExpiryDoesNotReleaseOrReplaceCheckout`
- `expiredValidationWithoutChannelOrderAllowsFreshCheckout`
- `expiredValidationRecoversLegacyOrderAndDoesNotClearIt`
- `incompleteChannelSearchCannotReleaseUnknownCheckout`
- `knownExpiredCheckoutIsReplacedAndCompletedRaceRemainsBlocked`
- `endedPaidSubscriptionReleasesOldCheckoutAndAllowsNewPurchase`
- `nonEndedOrForeignOldSubscriptionNeverReleasesPaidCheckout`
- `completedOrForeignCheckoutCannotBeReplaced`
- `subscriptionNotificationUsesVerifiedCurrentStateNotStalePayload`
- `creditsUsesOneBalanceRefreshAndDisabledBillingDoesNotQuery`

### BillingRepositoryTest

源码：`src/test/java/com/example/cursorquitterweb/musicmv/billing/BillingRepositoryTest.java`。

- `reminderRequiresUntouchedWelcomeAndNoPendingGeneration`
- `actualSqlProtectsAllowanceExpiryReplayAndRefundOrdering`
- `reconciliationOnlyChangesRequestedOwner`
- `creditsAreReservedAtomicallyAndRefundsStayInTheirOriginalPeriod`
- `providersHaveIndependentPaymentsButShareOneSubscriptionSlot`
- `welcomeIsPermanentUniqueAndSpentAfterExpiringCredits`
- `historySeparatesOwnersAndReleasedCreditsWithoutInventingRefunds`
- `usageCountsEachGenerationOnceAndFiltersBeforePagination`
- `balanceRefreshBatchesGrantReconciliationAndReadInOrder`
- `incompleteBalanceBatchFailsInsteadOfReturningAnUnverifiedBalance`
- `clearingCheckoutCannotEraseOtherOwnerProviderNewTokenOrSubscription`

### MusicBillingControllerTest

源码：`src/test/java/com/example/cursorquitterweb/musicmv/billing/MusicBillingControllerTest.java`。

- `missingSessionReturnsUnauthorizedThroughScopedHandler`
- `identityAlwaysComesFromAuthenticatedSession`

### StripeGatewayTest

源码：`src/test/java/com/example/cursorquitterweb/musicmv/billing/StripeGatewayTest.java`。

- `officialSdkSendsSubscriptionContractWithStableIdempotency`
- `onlyExplicitExpiryValidationAllowsCheckoutReconciliation`
- `recoveryListsCustomerSessionsAndExpiresVerifiedSessionThroughSdk`

### AiMusicGenerationServiceTest

源码：`src/test/java/com/example/cursorquitterweb/musicmv/aimusic/AiMusicGenerationServiceTest.java`。

- `quotaRejectionHappensBeforeAnyPaidProviderSubmission`
- `staleWorkerDiscardsEntireSnapshotAfterAnotherWorkerCompletes`
- `ordinaryPollingReturnsWhileProviderIsBlockedAndDoesNotResubmit`
- `failedQueryDoesNotAdvanceSuccessfulSyncTimestampAndReleasesLease`
- `recentClaimThrottlesManualRefreshWithoutPretendingProviderWasChecked`
- `completedMissingAudioStillSchedulesRecoveryButReadySongsDoNot`
- `signedCallbackRecoversUnknownSubmissionWithoutSubmittingAgain`
- `callbackWithoutProtectedJobCannotBindUnknownTask`
- `extensionCanReuseOriginalLyricsButCoverRequiresExactLyrics`
- `customDurationRequiresSupportedProviderModelAndMode`
- `exposesOnlyUserFacingGenerationSettingsAndToleratesLegacyRows`
- `mapsAdvancedModeToCustomProviderCommandWithExactLyricsAndControls`
- `rejectsAdvancedVocalSongWithoutExactLyrics`
- `rejectsProviderCallbackWhenProtectedJobDoesNotOwnProviderTask`
- `serverSideSyncQueriesPersistedTaskWithoutSubmittingAgain`
- `listsOwnedSongCandidatesWithValidatedWorkspaceControls`
- `acceptsCombinedLibraryFilters`
- `selectingCandidateDoesNotDownloadOrMaterializeAudio`
- `rejectsUnknownWorkspaceSortBeforeBuildingSql`
- `returnsOpaqueCursorAndUsesItForTheNextLibraryPage`

## 10. 数据库验收查询与独立证据

以下是只读核查模板；`:user`、`:invoice`、`:event`、`:now`必须绑定测试参数，不拼接用户输入。每次故障测试在操作前后运行，并结合Stripe真实对象核对。SQL查出“没有重复”不能证明“应有记录均存在”，必须同时查缺失发票与事件。

```sql
-- 用户客户、订阅入口与原始占用，核对归属和并发后是否保留新token。
SELECT * FROM music_mv_billing_customers WHERE user_id=:user;
SELECT * FROM music_mv_billing_subscriptions WHERE user_id=:user;
-- 指定发票的发放身份与账期；预期至多一行。
SELECT grant_id,provider,payment_id,user_id,subscription_id,plan_key,
       allowance,period_start,period_end,revoked,created_at
FROM music_mv_billing_grants
WHERE user_id=:user AND provider='stripe' AND payment_id=:invoice;
-- 成功处理与退款事实要分开确认；grant不证明event已被成功处理。
SELECT * FROM music_mv_billing_events WHERE provider='stripe' AND event_id=:event;
SELECT * FROM music_mv_billing_revocations WHERE provider='stripe' AND payment_id=:invoice;
-- 逐grant核对额度守恒：剩余不能为负；released不再扣分。
SELECT g.grant_id,g.allowance,g.revoked,g.period_start,g.period_end,
       COALESCE(SUM(CASE WHEN r.state='reserved' THEN r.credits ELSE 0 END),0) AS reserved,
       COALESCE(SUM(CASE WHEN r.state='consumed' THEN r.credits ELSE 0 END),0) AS consumed,
       g.allowance-COALESCE(SUM(CASE WHEN r.state<>'released' THEN r.credits ELSE 0 END),0) AS unspent
FROM music_mv_billing_grants g
LEFT JOIN music_mv_billing_reservations r ON r.grant_id=g.grant_id
WHERE g.user_id=:user GROUP BY g.grant_id;
-- 独立重算当前可用余额，与credits.remaining逐项比对。
SELECT COALESCE(SUM(g.allowance-COALESCE((
  SELECT SUM(r.credits) FROM music_mv_billing_reservations r
  WHERE r.grant_id=g.grant_id AND r.state IN ('reserved','consumed')
),0)),0) AS expected_remaining
FROM music_mv_billing_grants g
WHERE g.user_id=:user AND g.revoked=0 AND g.period_start<=:now
  AND (g.period_end>:now OR (g.provider='internal' AND g.plan_key='welcome'));
-- 孤立/终态不一致的预占；未知任务应进入人工核查，不自动释放。
SELECT r.job_id,r.state,r.credits,r.grant_id,j.status
FROM music_mv_billing_reservations r LEFT JOIN ai_music_jobs j ON j.job_id=r.job_id
WHERE r.user_id=:user AND (j.job_id IS NULL
  OR (r.state='reserved' AND j.status IN ('completed','failed')));
```

每次沙盒支付还需独立比对：Price.amount/currency/recurring、Checkout.customer/client_reference_id/mode/livemode、Invoice.status/billing_reason/parent/lines.period、Subscription.status/cancel_at_period_end/items.current_period_end。不是只看左侧余额数字或前端“到账”提示。

并发用例以开始屏障同时发起两个服务实例请求，保存每个请求响应及渠道唯一对象数；分别在claim、Stripe受理、saveCheckout、grant、processed、revoke两个SQL、reserve后建job、settle位置注入失败。SQLite顺序重复用例只能验证幂等，不可填为该并发用例通过。


### 相邻后端合同的补充执行

| 测试类 | 执行 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| PaidSubmissionSafetyTest | 4 | 0 | 0 | 0 |
| MusicMvAuthenticationIsolationTest | 2 | 0 | 0 | 0 |
| MusicMvAuthServiceTest | 13 | 0 | 0 | 0 |
| D1DatabaseClientTest | 4 | 0 | 0 | 0 |

相邻合同同样只使用本地夹具；包括登录身份、访问凭证刷新、登出、D1计费batch与不重试写入、受理结果未知时不返还/不重提。

PaidSubmissionSafetyTest：

- `uncertainSongResponsesNeverReleaseOrResubmit`
- `persistenceFailureAfterAcceptanceDoesNotRefundOrResubmit`
- `lyricsClaimSurvivesUnknownResultAcrossServiceInstances`
- `lyricsConcurrentLoserAndReplayNeverSubmit`

MusicMvAuthenticationIsolationTest：

- `clientAndTemplateSyncCredentialsAreIndependent`
- `missingManualTokensAllowsLoopbackButRequiresRemotePairing`

MusicMvAuthServiceTest：

- `anonymousWorkspaceRemainsAvailableBeforeSignIn`
- `reservedUserWorkspaceCannotBeForgedWithoutSession`
- `signedInSessionOverridesCallerSuppliedWorkspace`
- `protectedMusicApiRequiresSignedInSession`
- `protectedMusicApiUsesSessionUserOnly`
- `sessionActivityWritesOnlyWhenDueAndRevocationStillTakesEffect`
- `sessionResponseIncludesUtcExpiryWithoutRenewingIt`
- `distinguishesInvalidSessionFromFirstAnonymousVisit`
- `logoutRevokesOnlyCurrentTokenAndClearsSecureCookie`
- `loginReturnsStoredSessionExpiryAndSetsThirtyDayCookie`
- `longCookieNeedsRefreshAndCannotExecuteProtectedBusiness`
- `sessionRefreshIssuesSecureAccessAndLogoutClearsBothCookies`
- `downloadFallbackVerifiesLongSessionAndRejectsRevokedOwner`

D1DatabaseClientTest：

- `retriesOneTransientReadFailure`
- `preservesExecutionStatisticsWithoutExposingQueryValuesAsMetricTags`
- `doesNotRetryMutatingQueries`
- `billingBatchUsesOneHttpRequestAndPreservesOrderedResultsWithoutRetryingWrites`

### 本次验证合计

后端合计 **104 项现有测试通过**（计费核心61项、歌曲服务20项、相邻安全/认证/D1合同23项）；前端服务端代理、路由与计费合同 **18项通过**。8条文档核查SQL已在本地SQLite初始化结构及体验10/月度300/预占10夹具上执行，独立余额重算为300。174条设计用例并未逐条执行，真实沙盒验收仍待完成。
