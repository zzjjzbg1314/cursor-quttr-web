# 网站登录方式与账号归属

## 已实现的行为

Google 保留现有入口。新增 Apple 网页授权（官方 JS 弹窗）和邮箱六位验证码。账号菜单的“登录方式”显示已绑定身份，并允许在已登录状态下绑定 Google、Apple 或邮箱。

作品、订阅、余额继续使用原 `music_mv_users.user_id`。本次不搬迁作品，不合并账务，不修改账单表。

- 邮箱验证码证明邮箱控制权后，匹配 `music_mv_user_identities` 中唯一、已验证邮箱的原用户。邮箱大小写和首尾空格统一处理；不改写 Gmail 点号、加号后缀或其他别名。
- 新的第三方身份即使声明相同已验证邮箱，也不能直接接管原账号；返回 `AUTH_LINK_REQUIRED`，要求先登录原账号再显式绑定。
- Apple 隐藏邮箱与原邮箱不同，系统无法推断两者是同一人。老用户应先用 Google 或原邮箱登录，再在账号菜单绑定 Apple。弹窗有对应提示。已创建两个独立账号时不会自动合并，也不会把任一余额转移。
- 已绑定到另一个用户的身份、多个历史用户共用的已验证邮箱、停用或删除的原账号都明确阻断。未验证邮箱不作为账号恢复依据。
- 同一数据库事务内检查归属、创建用户和身份；并发首次登录不会建立两个工作区。绑定后保持原会话与原资料，不用 Apple 隐藏邮箱覆盖主邮箱。

## 启用前置条件

未执行生产部署、远程数据库迁移或真实用户登录。配置缺失时界面显示未配置，按钮不冒充可用。

1. 在**网站专用 Music MV D1** 执行 `src/main/resources/db/migrations/music-mv-auth-methods.sql`。这是独立的幂等增量迁移，不运行模板目录初始化、不修改模板版本元数据、不触碰其他产品的数据库。
2. 在后端秘密配置中设置以下键（例如现有 `secrets/music-mv-auth.properties`，不得提交密钥）：

```properties
music-mv.auth.apple.client-id=<Apple Developer 中登记的 Services ID>
music-mv.auth.apple.redirect-uri=<已登记的 HTTPS Return URL>
music-mv.auth.email.resend-api-key=<Resend API key>
music-mv.auth.email.from=<已验证域名的发件人>
music-mv.auth.email.code-secret=<至少32字符的独立随机密钥，多实例保持一致>
music-mv.auth.email.hourly-limit=100
```

Apple 必须启用对应 Services ID 的 Sign in with Apple、关联主 App ID，并配置域名和 Return URL；localhost/IP 不能作为 Apple Return URL。本实现验证 Apple 签名 ID token、issuer、audience、有效期和服务端 nonce，不需要把 Apple 私钥交给浏览器。真实授权需要在已登记的 HTTPS 域名上验收；编辑器等带严格 COOP 的页面还必须验证弹窗兼容性，不能用隔离 UI 测试宣称 Apple 全站实测通过。

邮件使用 Resend HTTPS API，发件域名需要验证；若已有其他邮件服务，可替换 `MusicMvAuthEmailSender`，验证码与账号绑定逻辑不变。发送主题与正文覆盖十种网站语言。

3. 先部署后端及迁移，再部署前端。在测试账号上验收：Google 原账号 → 原邮箱验证码 → 同一用户 ID/作品/积分；登录原账号 → 绑定 Apple（含隐藏邮箱）→ 退出后 Apple 登录 → 同一用户 ID/作品/积分；冲突身份不合并。
4. 不要清除旧身份或用户数据。若关闭新方式，撤掉对应配置即可；原 Google 身份和会话继续保留。

## 安全边界

- 验证码有效期10分钟、最多5次尝试、成功后原子消费；数据库仅保存带服务端密钥的 HMAC，不保存明文验证码。
- 每个挑战绑定单独 HttpOnly/SameSite Cookie，并绑定登录或目标用户的绑定意图。并发初次发起 Apple 与邮箱流程不会覆盖彼此的验证 Cookie。
- 数据库限流跨实例生效：每邮箱60秒间隔、每邮箱每小时5次；每浏览器每种方式每小时10次；邮件全站每小时100次（可配置），Apple挑战独立1000次。上线时按实际量设置邮件预算并监控；不会自动提高预算或回退无限发送。
- 邮件发送失败使验证码立即失效，不返回成功、不记录邮件正文或服务商敏感错误。Resend 请求带幂等键。
- 前端代理对认证写请求要求 JSON，并拒绝跨源请求；绑定接口重新检查数据库中的有效会话。
- 网站错误码在前端统一映射并翻译，第三方未知错误使用现有本地化兜底。

## 验证记录（2026-10-25）

本地真实 SQLite 运行生产仓储 SQL：原邮箱找回原ID、隐藏邮箱显式绑定、未验证邮箱隔离、历史重复邮箱、停用账号、并发创建、冲突身份、验证码一次性消费/过期/错误上限/浏览器及目标绑定、发信失败关闭。邮件全部为测试替身。

本地 RSA/JWKS 测试验证 Apple 正确签名、错误 nonce、错误 issuer/audience、过期和签名篡改。未使用真实 Apple 用户或发信服务。

前端运行类型/本地化检查、认证请求和会话回归；真实 AuthDialog 与 LocaleProvider 配合隔离接口，在十种语言、1440×900 与390×844检查登录初态和绑定验证码错误态，共40种布局。初始 Google/Apple 在隔离页显示未配置，不把它当作真实第三方授权成功。证据在本机 `/tmp/auth-ui-review/`。

本次不影响渲染计划或执行，无需渲染或SSIM。
