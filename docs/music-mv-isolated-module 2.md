# AI 音乐 MV 独立后端模块

本模块只负责网站后端与 Mac 原生渲染节点之间的音乐 MV 任务合同。它不会复用、修改或迁移 `cursor-quttr-web` 已有的生产业务表。

## 隔离边界

- 默认关闭：`MUSIC_MV_ENABLED=false`。关闭时不注册接口、D1 客户端、仓储、服务或后台任务。
- 独立 D1：只读取 `MUSIC_MV_CLOUDFLARE_D1_DATABASE_ID`，禁止填写现网 `cloudflare.d1.database-id`。
- 独立 R2：成片使用 `MUSIC_MV_CLOUDFLARE_R2_*`。是否与别的服务共用 Cloudflare 账号不影响数据库隔离，但不应复用现网 bucket。
- 独立鉴权：网站调用使用 `MUSIC_MV_CLIENT_TOKEN`；Mac 节点使用 `MUSIC_MV_RENDERER_TOKEN`。
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
```

如需将最终 MP4 存入独立 R2，再配置：

```text
MUSIC_MV_CLOUDFLARE_R2_ACCOUNT_ID=<account id>
MUSIC_MV_CLOUDFLARE_R2_BUCKET=<dedicated bucket>
MUSIC_MV_CLOUDFLARE_R2_ACCESS_KEY_ID=<access key>
MUSIC_MV_CLOUDFLARE_R2_SECRET_ACCESS_KEY=<secret>
MUSIC_MV_CLOUDFLARE_R2_PUBLIC_BASE_URL=<delivery base url>
```

R2 未配置时，模块只把渲染成片保存在 `MUSIC_MV_LOCAL_STORAGE_DIR`，不影响任务合同测试。

## 新 D1 初始化

仅对新建的独立 D1 执行：

```text
src/main/resources/db/music-mv-d1-schema.sql
```

不要对现网 D1 执行此文件。初始化后，需要把已经验收通过的模板元数据、版本、卡槽合同和 Mac 本地来源节点写入新库；草稿原件仍保留在对应 Mac 节点。

## API 合同

网站后端：

- `POST /api/music-mv/v1/render-jobs`
- `GET /api/music-mv/v1/render-jobs/{jobId}`
- `POST /api/music-mv/v1/render-jobs/{jobId}/cancel`
- `GET /api/music-mv/v1/render-jobs/{jobId}/output`

网站请求头：`X-Music-Mv-Client-Token`、`X-Music-Mv-Client-Id`。

Mac 渲染节点：

- `POST /internal/music-mv/v1/render-jobs/claim`
- `POST /internal/music-mv/v1/render-jobs/{jobId}/lease`
- `PUT /internal/music-mv/v1/render-jobs/{jobId}/output`
- `POST /internal/music-mv/v1/render-jobs/{jobId}/complete`
- `POST /internal/music-mv/v1/render-jobs/{jobId}/fail`
- `POST /internal/music-mv/v1/renderer/heartbeat`

Mac 使用 `X-Music-Mv-Renderer-Token`，上传成片时还需提供节点、租约和 SHA-256 专用请求头。

## 发布与回滚

1. 先保持 `MUSIC_MV_ENABLED=false` 发布并完成现网回归。
2. 创建全新的 D1，单独执行 schema，并核对 database id。
3. 配置独立令牌与 Mac 节点，但仍保持模块关闭。
4. 在受控环境启用模块，验证专用健康链路和一条基本原生渲染任务。
5. 出现异常时只需设回 `MUSIC_MV_ENABLED=false`；现网接口和现网 D1 不需要回滚。
