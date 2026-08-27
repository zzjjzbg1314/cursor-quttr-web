# AI 音乐 MV 独立后端模块

本模块负责网站模板目录、Cloudflare Images/Stream 媒体、发布状态，以及浏览器音乐 MV 渲染任务合同。Mac 只负责模板研究与同步，不领取用户渲染任务。它不会复用、修改或迁移 `cursor-quttr-web` 已有的生产业务表。

## 隔离边界

- 默认关闭：`MUSIC_MV_ENABLED=false`。关闭时不注册接口、D1 客户端、仓储、服务或后台任务。
- 独立 D1：只读取 `MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID`，禁止填写现网 `cloudflare.d1.database-id`。
- 独立 R2：最终用户成片使用 `MUSIC_MV_CLOUDFLARE_R2_*`，不复用现网 bucket。
- 独立媒体：模板封面进入 Cloudflare Images，完整模板 MV 进入 Cloudflare Stream。
- 独立鉴权：网站调用使用 `MUSIC_MV_CLIENT_TOKEN`；Mac 模板同步使用 `MUSIC_MV_TEMPLATE_SYNC_TOKEN`。
- 独立路径：外部 `/api/music-mv/v1/**`；内部 `/internal/music-mv/v1/**`。
- 独立代码包：`com.example.cursorquitterweb.musicmv`。

## 启用前配置

```text
MUSIC_MV_ENABLED=true
MUSIC_MV_D1_ENABLED=true
MUSIC_MV_CLOUDFLARE_ACCOUNT_ID=<Cloudflare account id>
MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID=<new independent D1 database id>
MUSIC_MV_CLOUDFLARE_D1_API_TOKEN=<token limited to the new D1>
MUSIC_MV_CLIENT_TOKEN=<website-to-module secret>
MUSIC_MV_TEMPLATE_SYNC_TOKEN=<Mac-template-sync-to-module secret>

MUSIC_MV_CLOUDFLARE_IMAGES_ACCOUNT_ID=<Cloudflare account id>
MUSIC_MV_CLOUDFLARE_IMAGES_API_TOKEN=<Images read/edit token>
MUSIC_MV_CLOUDFLARE_IMAGES_DELIVERY_BASE_URL=https://imagedelivery.net/<account-hash>
MUSIC_MV_CLOUDFLARE_STREAM_ACCOUNT_ID=<Cloudflare account id>
MUSIC_MV_CLOUDFLARE_STREAM_API_TOKEN=<Stream read/edit token>
MUSIC_MV_CLOUDFLARE_STREAM_DELIVERY_BASE_URL=https://customer-<code>.cloudflarestream.com

# AI 写歌供应商（默认使用 sunoapi.org，可由 provider 配置替换）
MUSIC_MV_AI_PROVIDER=sunoapi
MUSIC_MV_SUNOAPI_API_KEY=<sunoapi.org API key>
# 可选；不配置时使用 API key 派生回调保护令牌
MUSIC_MV_SUNOAPI_CALLBACK_TOKEN_SECRET=<dedicated random secret>
MUSIC_MV_PUBLIC_BASE_URL=https://<public website backend origin>

# C 端登录（至少配置一种）
MUSIC_MV_GOOGLE_CLIENT_ID=<Google Web OAuth client id>
MUSIC_MV_APPLE_CLIENT_ID=<Apple Services ID>
MUSIC_MV_APPLE_REDIRECT_URI=https://<website origin>/create
# 可选，默认 30 天，范围 1-90 天
MUSIC_MV_AUTH_SESSION_DAYS=30
```

如需将最终用户渲染成片存入独立 R2，再配置：

```text
MUSIC_MV_CLOUDFLARE_R2_ACCOUNT_ID=<account id>
MUSIC_MV_CLOUDFLARE_R2_BUCKET=<dedicated bucket>
MUSIC_MV_CLOUDFLARE_R2_ACCESS_KEY_ID=<access key>
MUSIC_MV_CLOUDFLARE_R2_SECRET_ACCESS_KEY=<secret>
MUSIC_MV_CLOUDFLARE_R2_PUBLIC_BASE_URL=<delivery base url>
```

调试阶段默认把浏览器渲染成片保存在 `MUSIC_MV_LOCAL_STORAGE_DIR`。生产环境设置
`MUSIC_MV_BROWSER_OUTPUT_STORAGE=r2` 后，浏览器才会把成片直传到独立 R2；已有成片仍按各自
`output_storage_key` 从原存储位置读取，不会因切换配置而迁移或删除。

## 新 D1 初始化

不要在控制台向任何已有数据库手工粘贴整份 SQL。初始化能力默认关闭，只有下面两个值同时配置，且都和实际的独立 D1 UUID 完全一致时才允许执行：

```text
MUSIC_MV_D1_ALLOW_SCHEMA_INITIALIZE=true
MUSIC_MV_EXPECTED_D1_DATABASE_ID=<same new independent D1 database id>
```

使用模板同步专用令牌调用：

```http
POST /internal/music-mv/v1/templates/schema/initialize
X-Music-Mv-Template-Sync-Token: <template sync token>
Content-Type: application/json

{"expectedDatabaseId":"<same new independent D1 database id>"}
```

初始化器会先读取 `sqlite_master`，再以幂等方式补齐本模块表；它只使用 `MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID` 指向的专用 Music MV D1，不读取旧 Quittr 数据源。完成后会核对 18 张专用表、12 个固定一级分类和 schema SHA-256，并写入 `music_mv_schema_metadata`。其中 `music_mv_users`、`music_mv_user_identities`、`music_mv_user_sessions` 只属于 Music MV 用户体系，与旧站用户表无关联。

不要对现网 D1 执行 `src/main/resources/db/music-mv-d1-schema.sql`。初始化后，由 Mac 的模板晋升协议幂等写入已经验收通过的模板元数据、双语信息、版本、卡槽合同、验收证据和来源节点。封面通过一次性地址上传到 Images，完整 MV 通过 TUS 上传到 Stream；草稿原件仍保留在对应 Mac 节点。

## API 合同

网站后端模板目录：

- `GET /api/music-mv/v1/template-categories`
- `GET /api/music-mv/v1/templates`
- `GET /api/music-mv/v1/templates/{templateId}`
- `GET /api/music-mv/v1/admin/templates`
- `GET /api/music-mv/v1/admin/templates/{templateId}`
- `GET /api/music-mv/v1/admin/renderer-nodes`
- `PATCH /api/music-mv/v1/admin/templates/{templateId}`
- `POST /api/music-mv/v1/admin/templates/{templateId}/actions/{action}`

网站后端渲染任务：

- `POST /api/music-mv/v1/render-jobs`
- `GET /api/music-mv/v1/render-jobs/{jobId}`
- `POST /api/music-mv/v1/render-jobs/{jobId}/cancel`
- `GET /api/music-mv/v1/render-jobs/{jobId}/output`

网站后端 AI 写歌：

- `POST /api/music-mv/v1/songs`：以 `requestId` 幂等创建写歌任务
- `GET /api/music-mv/v1/songs/{jobId}`：读取状态、候选歌曲和事件
- `GET /api/music-mv/v1/songs/{jobId}?refresh=true`：回调丢失时主动向供应商对账
- `POST /api/music-mv/v1/songs/{jobId}/candidates/{candidateId}/select`：选择候选并固化到 Music MV 素材存储；响应中的 `renderMusicAsset` 可直接传入渲染任务 `music`

网站用户登录：

- `GET /api/music-mv/v1/auth/providers`：读取 Google/Apple 登录可用状态和公开客户端配置
- `POST /api/music-mv/v1/auth/sso`：验证 Google/Apple ID token，建立 HttpOnly 会话，并认领当前匿名浏览器的歌曲与 MV 任务
- `GET /api/music-mv/v1/auth/session`：读取当前登录用户
- `POST /api/music-mv/v1/auth/logout`：撤销当前会话
- 未登录时 `X-Music-Mv-Client-Id` 仍表示浏览器匿名工作区；登录后服务端只使用会话中的 `usr_*`，不会信任前端伪造的用户 ID。
- 会话 Cookie 只保存随机明文令牌；D1 仅保存 SHA-256 摘要。默认 `HttpOnly`、`SameSite=Lax`，HTTPS 下自动启用 `Secure`。

SunoAPI 回调边界：

- `POST /api/music-mv/v1/provider-webhooks/sunoapi/music`
- sunoapi.org 官方回调合同未提供请求签名；模块在每个回调 URL 中加入由服务端密钥和内部 job id 派生的不可猜令牌，并再次核对回调 task id 与 job/provider attempt 的绑定关系。
- `MUSIC_MV_PUBLIC_BASE_URL` 必须是 sunoapi.org 可以访问的 HTTPS 网站后端地址。
- SunoAPI task id、状态和原始响应只保存在 provider attempt；网站前端只看到稳定的 Music MV 写歌任务与候选合同。
- SunoAPI 生成资源仅保留 15 天，因此选中候选时会复制到模块自己的 R2；R2 未配置的本地开发环境则进入本地素材存储。

备用 KIE 回调边界：

- `POST /api/music-mv/v1/provider-webhooks/kie/music`
- 回调必须同时通过 `X-Webhook-Timestamp`、`X-Webhook-Signature` 的 HMAC-SHA256 校验和五分钟重放窗口。
- KIE task id、状态和原始响应只保存在 provider attempt；网站前端只看到稳定的 Music MV 写歌任务与候选合同。
- KIE 生成资源仅保留有限时间，因此选中候选时会复制到模块自己的 R2；R2 未配置的本地开发环境则进入本地素材存储。

网站使用 `X-Music-Mv-Client-Token` 与 `X-Music-Mv-Client-Id`。

浏览器渲染只在开始时向服务端领取一次尝试凭证，完成后上传成品并提交成功；失败、取消时各提交一次终态。编码进度、切换标签后的暂停/继续全部保存在当前浏览器内，不轮询写入 D1，读取任务也不得产生 D1 更新。

Mac 拉取任务、心跳、续租和成片回传接口已经下线。用户成片只走浏览器渲染尝试与浏览器成片上传接口，模板可用性不再依赖 Mac 在线心跳。

Mac 模板同步节点：

- `POST /internal/music-mv/v1/templates/schema/initialize`（一次性、默认禁用）
- `GET /internal/music-mv/v1/templates/migration-readiness`
- `POST /internal/music-mv/v1/templates/promotions`
- `POST /internal/music-mv/v1/templates/{templateId}/versions/{versionId}/media/images/upload-session`
- `POST /internal/music-mv/v1/templates/{templateId}/versions/{versionId}/media/videos/upload-session`
- `POST /internal/music-mv/v1/templates/{templateId}/versions/{versionId}/media/{mediaId}/complete`
- `POST /internal/music-mv/v1/templates/{templateId}/versions/{versionId}/publish`

同步使用 `X-Music-Mv-Template-Sync-Token`。Images/Stream API token 永远只保存在网站后端，Mac 只收到一次性上传地址。

Mac 端只使用以下新配置，不再读取历史 `PQ_CLOUD_TEMPLATE_*`：

```text
PQ_MUSIC_MV_TEMPLATE_ENABLED=true
PQ_MUSIC_MV_TEMPLATE_BACKEND_BASE_URL=<website backend>
PQ_MUSIC_MV_TEMPLATE_SYNC_TOKEN=<same template sync secret>
PQ_MUSIC_MV_TEMPLATE_CLIENT_TOKEN=<same website client secret>
PQ_MUSIC_MV_TEMPLATE_NODE_ID=<stable Mac node id>
PQ_MUSIC_MV_TEMPLATE_CATALOG_MODE=migration
```

## 发布与回滚

1. 先保持 `MUSIC_MV_ENABLED=false` 发布并完成现网回归。
2. 创建全新的 D1，单独执行 schema，并核对 database id。
3. 配置独立令牌与 Mac 节点，但仍保持模块关闭。
4. 先保持 Mac `catalog-mode=migration`，迁移并核对 4 个模板、26 个卡槽、4 张封面和 4 条完整 MV。
5. Mac 调用 `GET /admin/api/template-library/cloud-migration/audit` 执行双读核对。只有 `readyToSwitchCloud=true`，且 4 个模板、26 个卡槽、Images、Stream、exact 验收、Mac 节点心跳和原生草稿备份全部一致时才能切换。
6. 在受控环境验证一条网站请求到 Mac 原生渲染的基本任务，再切换 `catalog-mode=cloud`。
7. 出现目录异常时 Mac 退回 `catalog-mode=migration`；出现模块级异常时设回 `MUSIC_MV_ENABLED=false`。现网接口和现网 D1 不需要回滚。

## Mac 原生草稿备份

模板同步和晋升前，Mac 会为当前不可变版本创建内容寻址 ZIP。备份与本地 H2 分离，恢复只会进入 staging，不会覆盖正式模板草稿：

- `GET /admin/api/template-library/templates/{templateId}/versions/{versionId}/backups`
- `POST /admin/api/template-library/templates/{templateId}/versions/{versionId}/backups`
- `POST /admin/api/template-library/templates/{templateId}/versions/{versionId}/backups/{backupId}/verify`
- `POST /admin/api/template-library/templates/{templateId}/versions/{versionId}/backups/{backupId}/restore`

Mac 节点超过 90 秒没有心跳时，网站模板目录会把 `sourceAvailability` 计算为 `unavailable`，不再把相关任务分配给该节点；超过 5 分钟在节点管理接口中显示为离线。
