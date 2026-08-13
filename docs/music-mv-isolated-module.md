# AI 音乐 MV 独立后端模块

本模块负责网站模板目录、Cloudflare Images/Stream 媒体、发布状态，以及网站后端与 Mac 原生渲染节点之间的音乐 MV 任务合同。它不会复用、修改或迁移 `cursor-quttr-web` 已有的生产业务表。

## 隔离边界

- 默认关闭：`MUSIC_MV_ENABLED=false`。关闭时不注册接口、D1 客户端、仓储、服务或后台任务。
- 独立 D1：只读取 `MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID`，禁止填写现网 `cloudflare.d1.database-id`。
- 独立 R2：最终用户成片使用 `MUSIC_MV_CLOUDFLARE_R2_*`，不复用现网 bucket。
- 独立媒体：模板封面进入 Cloudflare Images，完整模板 MV 进入 Cloudflare Stream。
- 独立鉴权：网站调用使用 `MUSIC_MV_CLIENT_TOKEN`；Mac 渲染任务使用 `MUSIC_MV_RENDERER_TOKEN`；Mac 模板晋升使用 `MUSIC_MV_TEMPLATE_SYNC_TOKEN`。
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
MUSIC_MV_RENDERER_TOKEN=<Mac-renderer-to-module secret>
MUSIC_MV_TEMPLATE_SYNC_TOKEN=<Mac-template-sync-to-module secret>

MUSIC_MV_CLOUDFLARE_IMAGES_ACCOUNT_ID=<Cloudflare account id>
MUSIC_MV_CLOUDFLARE_IMAGES_API_TOKEN=<Images read/edit token>
MUSIC_MV_CLOUDFLARE_IMAGES_DELIVERY_BASE_URL=https://imagedelivery.net/<account-hash>
MUSIC_MV_CLOUDFLARE_STREAM_ACCOUNT_ID=<Cloudflare account id>
MUSIC_MV_CLOUDFLARE_STREAM_API_TOKEN=<Stream read/edit token>
MUSIC_MV_CLOUDFLARE_STREAM_DELIVERY_BASE_URL=https://customer-<code>.cloudflarestream.com

# AI 写歌供应商（首个适配器为 KIE，可由 provider 配置替换）
MUSIC_MV_AI_PROVIDER=kie
MUSIC_MV_KIE_API_KEY=<KIE API key>
MUSIC_MV_KIE_WEBHOOK_HMAC_KEY=<KIE settings webhook HMAC key>
MUSIC_MV_PUBLIC_BASE_URL=https://<public website backend origin>
```

如需将最终用户渲染成片存入独立 R2，再配置：

```text
MUSIC_MV_CLOUDFLARE_R2_ACCOUNT_ID=<account id>
MUSIC_MV_CLOUDFLARE_R2_BUCKET=<dedicated bucket>
MUSIC_MV_CLOUDFLARE_R2_ACCESS_KEY_ID=<access key>
MUSIC_MV_CLOUDFLARE_R2_SECRET_ACCESS_KEY=<secret>
MUSIC_MV_CLOUDFLARE_R2_PUBLIC_BASE_URL=<delivery base url>
```

R2 未配置时，模块只把用户渲染成片保存在 `MUSIC_MV_LOCAL_STORAGE_DIR`，不影响模板目录与任务合同测试。

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

初始化器会先读取 `sqlite_master`：只含 D1 保留表 `_cf_KV` 的新库可以初始化；只含本模块表的半完成库可以幂等续传；发现 `users`、`posts` 等任何非 Music MV 表会直接拒绝。完成后会核对 15 张专用表、12 个固定一级分类和 schema SHA-256，并写入 `music_mv_schema_metadata`。成功后立即把 `MUSIC_MV_D1_ALLOW_SCHEMA_INITIALIZE` 改回 `false`。

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

KIE 回调边界：

- `POST /api/music-mv/v1/provider-webhooks/kie/music`
- 回调必须同时通过 `X-Webhook-Timestamp`、`X-Webhook-Signature` 的 HMAC-SHA256 校验和五分钟重放窗口。
- KIE task id、状态和原始响应只保存在 provider attempt；网站前端只看到稳定的 Music MV 写歌任务与候选合同。
- KIE 生成资源仅保留有限时间，因此选中候选时会复制到模块自己的 R2；R2 未配置的本地开发环境则进入本地素材存储。

网站使用 `X-Music-Mv-Client-Token` 与 `X-Music-Mv-Client-Id`。

Mac 渲染节点：

- `POST /internal/music-mv/v1/render-jobs/claim`
- `POST /internal/music-mv/v1/render-jobs/{jobId}/lease`
- `PUT /internal/music-mv/v1/render-jobs/{jobId}/output`
- `POST /internal/music-mv/v1/render-jobs/{jobId}/complete`
- `POST /internal/music-mv/v1/render-jobs/{jobId}/fail`
- `POST /internal/music-mv/v1/renderer/heartbeat`

Mac 渲染使用 `X-Music-Mv-Renderer-Token`。

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
