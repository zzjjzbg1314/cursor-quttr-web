# 网站后端接口性能审查报告

日期：2026-09-23。对象：suno.film 客户网站，Next.js 本地 3000 → Java 本地 8080 → 当前配置的 Cloudflare D1 / R2 / AI 服务。

## 结论与证据边界

当前结构可以支撑小规模使用，但存在明确的串行远程请求放大、列表排序不匹配、大场景重复传输和上游超时不一致。优先解决调用次数，再补准确匹配的索引；不能只靠加索引解决所有等待。

本报告覆盖网站后端 53 个方法与路径组合（含 4 个服务端回调）、2 个 Next.js 自身端点，以及媒体/运行资源外部依赖。按调用分支逐项审查，不把 8082 模板运营、同步与内部研究接口混为客户网站 API。Next.js 通配代理也是全体 API 的共同依赖，单独审查。支付模块本次纳入静态性能分析，但延续此前不实际支付的范围。

**完成的是接口全量静态分析 + 当前数据库结构核查 + 关键查询计划 + 15 类 GET 的各 3 次只读抽样，不是全部接口登录态压测。** 没有发起生成、上传、保存、删除、支付、迁移或完整渲染。未修改业务代码或数据。登录态接口未以未登录响应代替成功耗时；资产 readiness 的 401 只证明鉴权生效。

- HTTP 耗时：客户端从请求到读完整响应的时间，经过 3000 代理，含本地开发环境开销；无浏览器 Cookie。
- 每项仅 3 个顺序样本，不报告 P95/P99，不代表生产环境或并发容量；未清缓存，第一个样本也不能一概称为冷启动。
- 索引：读取当前 IDE 配置对应 D1 的 sqlite_master，确认真实存在，不仅检查仓库 SQL。当前运行服务若被其他会话改为另一数据库，仍需以部署配置再次核对。
- 查询计划：对关键筛选/排序核心 SQL 执行远程 EXPLAIN QUERY PLAN；计划中使用不存在的测试归属值，不读取其他用户正文。它不是全部复杂生产 SQL 的完整计划，不包含大数据或真实分布压测。
- “约 N 次调用”为代码路径核算，未计重试和所有可选分支；不等同于实测时间。

## 一、低频 HTTP 实测

单位 ms；均为完整响应耗时，非纯 SQL 耗时。模板 ID 仅用于重复读取已有公开模板。

| GET 路径（默认前缀 /api/music-mv/v1） | 状态 | 样本 1 / 2 / 3 | 响应字节数 | 判断 |
|---|---|---|---|---|
| `auth/providers` | 200/200/200 | 49.3 / 8.3 / 9.7 | 159 | 本次未见明显慢响应；不证明高负载表现 |
| `auth/session` | 200/200/200 | 6.7 / 5.9 / 5.6 | 35 | 未登录空会话；不能代表登录校验耗时 |
| `template-categories?locale=zh-CN` | 200/200/200 | 241.2 / 251.3 / 347.9 | 4067 | 本次未见明显慢响应；不证明高负载表现 |
| `template-tags?locale=zh-CN` | 200/200/200 | 569.1 / 621.0 / 614.1 | 1054 | 小字典仍有两次串行查询 |
| `templates?page=1&pageSize=24` | 200/200/200 | 877.3 / 677.4 / 655.8 | 39852 | 优先优化远程往返及查询/结果缓存 |
| `templates?tag=birthday&pageSize=24` | 200/200/200 | 1165.8 / 1195.6 / 881.7 | 36636 | 优先优化远程往返及查询/结果缓存 |
| `templates?q=birthday&pageSize=24` | 200/200/200 | 919.0 / 645.9 / 673.8 | 36636 | 优先优化远程往返及查询/结果缓存 |
| `billing/capabilities` | 200/200/200 | 13.1 / 6.6 / 7.0 | 34 | 本次未见明显慢响应；不证明高负载表现 |
| `text-optimization` | 200/200/200 | 5.9 / 5.5 / 4.7 | 18 | 本次未见明显慢响应；不证明高负载表现 |
| `assets/readiness` | 401/401/401 | 6.1 / 6.7 / 5.5 | 94 | 401，未测成功业务路径 |
| `templates/tpl_5c66ebce53924059` | 200/200/200 | 2028.1 / 20.4 / 8.5 | 93582 | 首样本 2 秒，后续缓存收益明显 |
| `templates/tpl_5c66ebce53924059/similar?locale=zh-CN&limit=6` | 200/200/200 | 697.3 / 654.6 / 694.9 | 9874 | 优先优化远程往返及查询/结果缓存 |
| `templates/tpl_5c66ebce53924059/versions/tplver_adc4c67c3e28456eac6dbfa76d1ea029` | 200/200/200 | 435.9 / 284.9 / 296.0 | 93582 | 缓存命中仍校验发布状态，保留此正确性约束 |
| `/api/site-manifest?lang=zh-CN` | 200/200/200 | 573.6 / 3.4 / 4.9 | 342 | 首样本包含开发环境首次访问开销 |
| `/api/browser-renderer-identity` | 200/200/200 | 109.1 / 55.1 / 94.4 | 129 | 开发模式计算本地指纹；生产读取预置值 |

D1 独立探针 `SELECT 1`：客户端完整耗时 1337.5 / 1178.1 / 833.6 ms，而服务端返回的执行 duration 为 0.2244 / 0.2366 / 0.2198 ms。该 Python 探针连接复用方式不同于 Java，不能直接作为每次 Java 查询耗时相乘；它用于证明网络/连接成本与 SQL 执行时间必须分别记录。

现有数据库规模：模板 125，版本 133，项目 158，上传素材 17，歌曲任务 12，候选歌曲 22，视频任务 67，歌词草稿 2。这是小数据样本，不能证明万级作品/模板时性能合格。场景 JSON 最大 3,565,886 字符，平均约 289,047 字符；字符数不是实际网络字节数。实测公开模板详情响应为 93,582 字节，其他模板可能大得多。

## 二、优先问题与改进依据

### P1-1：项目保存的请求数量随照片数线性放大

`MusicMvProjectDraftService.save / validateAssets` 对每个素材分别 findOwned，保存后再逐个 touch；`MusicMvProjectDraftRepository.saveSnapshot` 虽然使用事务 batch，但前后这些查询仍串行。

成功主路径业务 D1 往返：查 owner 1 次 + N 次素材归属查询 + 1 次事务 batch + 1 次保存回读 + N 次 touch + 2 次最终详情读取，即 **2N+5 次**。42 个素材绑定时为 89 次，加登录会话查询通常约 90 次。N 指请求 assets 数量，不是截图里的所有模板默认槽位；不要据此断言每个 42 槽模板都恰好触发 90 次。

建议：去重后批量验证素材归属，把 touch 放入受保存成功条件约束的批处理，减少重复回读；保留归属检查、事务及 revision 冲突保护。先以 1/10/42 个绑定测往返数和耗时，目标是远程往返不再随 N 增长。相比单纯建索引，这是更高收益项目。

### P1-2：导出准备与任务详情还有远程读取放大

`prepareBrowserAsync → requireSlotBindings` 已将默认模板素材批量查询，但用户上传素材仍逐个 `requireOwnedCloudAsset → r2Metadata`，读取 R2 元数据。重复素材也需考虑请求内去重。不能为了快跳过归属、过期与内容校验。

`GET render-jobs/{jobId}` 在 ready/rendering/uploading 等浏览器阶段会重建 browserRender：读 scene、歌曲、媒体、运行依赖，并返回事件；并非恒定的轻量状态读取。大型场景反复序列化/下载会加重轮询。

建议：状态查询与不可变场景会话分离，场景按版本/内容哈希缓存，签名 URL 有独立有效期；状态端点返回小 DTO。归属校验改为批量或受控并发，限定连接与内存。需要分别量化“提交→ready”和“浏览器首帧”，不能把之前 14 秒浏览器准备直接算作后端接口耗时。

### P1-3：列表索引能筛选，但不能完整支持排序

当前真实计划如下。TEMP B-TREE 表示额外排序，不等于整表扫描，也不代表现在必定很慢；用户数据增长后应优先验证。

| 查询 | 真实索引/计划 | 判断与候选调整 |
|---|---|---|
| 项目列表 | idx_music_mv_projects_user(user_id,status,updated_at DESC)；额外排序 | 查询未约束 status，打断排序前缀。考虑活动项目部分索引 (user_id,updated_at DESC,project_id)，条件 deleted_at IS NULL |
| 最近素材 | idx_music_mv_user_assets_library(user_id,kind,status,created_at DESC)；额外排序 | 实际先按 last_used_at DESC。考虑 (user_id,kind,status,last_used_at DESC,created_at DESC)，结合 deleted_at 过滤 |
| 视频作品库 | UNIQUE(client_id,request_id) 的自动索引；额外排序 | 缺少归属+时间索引。考虑 (client_id,created_at DESC,job_id DESC)；completed 分支再评估状态前缀或部分索引 |
| 歌曲作品库核心 JOIN | idx_ai_music_jobs_user_library + idx_ai_music_candidates_job；额外排序 | 已有归属索引，不应再声称完全没索引。跨多个 job 排 candidate 时间仍排序，标题/时长表达式排序也需单独计划 |
| 歌词草稿列表 | idx_music_mv_lyrics_drafts_owner(user_id,updated_at DESC) | 匹配，无必要重复加索引 |
| 项目详情 | project_id 主键 | 单条查询已有定位能力，通常不必加 (user_id,project_id) 冗余索引 |
| 音色列表 | 本次数据库没有 ai_music_voices 表 | 服务首次访问时建表/建索引，因此无法实测其计划；应迁移到部署期初始化 |

以上索引是**待验证候选，未执行创建**。最终 DDL 需比较真实完整 SQL、写放大、现有重复索引和数据分布，再在隔离数据验证。

### P1-4：超时预算与 HTTP 连接配置不统一

CloudflareRestTemplateFactory：连接/借连接超时 15 秒、读超时 120 秒；D1 SELECT 在 ResourceAccessException 后最多再试一次。SunoApi provider 也配置 120 秒读超时。前端 SSR 数据读取 8 秒，歌曲/歌词/文本优化代理 55 秒；其余代理请求未设置统一 deadline。

因此用户页面可能已报失败，而后台仍在等待甚至继续完成处理。需要统一请求 deadline、区分读/写重试、保持提交幂等，并监测连接池等待。工厂没有显式配置业务所需的总连接数/每主机并发，不能仅靠增加 Servlet 线程解决；本次没有测出实际池饱和，数值需运行时验证。

### P1-5：歌词与音色轮询每次访问外部供应商

`AiLyricsGenerationService.get` 直接 queryLyrics，无本地结果缓存；歌词 webhook 仅返回 204，不落结果。音色 processing 状态 GET 也同步查外部供应商并回读数据库。相比歌曲已有刷新租约/后台同步，这两条路径更容易让多标签页和重复轮询放大成本。

建议：任务状态持久化，最终结果缓存，运行中轮询限频与合并；回调校验完成后持久化；前端退避且只保持一个在途请求。歌曲现有 8 秒刷新间隔、租约和最多 2 个请求触发的刷新工作线程值得复用，但后台定时同步目前按任务串行执行，也应监测积压。

### P2-1：模板列表没有发挥小字典和公开结果缓存的收益

模板 COUNT 与列表 SELECT 串行执行；tag/category 校验增加查询。标签每次读标签表和翻译表两次，分类一次查询。SSR 有 60 秒 revalidate，但浏览器客户端通过通配代理 no-store；不能把 SSR 缓存当作所有访问均有缓存。

建议：公开分类/标签按 locale 缓存并在管理变更后失效；热门筛选页缓存或 count/list 合并批次，绑定发布状态失效。列表复杂 ORDER BY 包含 featured JSON 表达式、发布时间；现有 idx_templates_catalog 不完整覆盖排序。还从 scene_json 提取运行包大小，大字段宜预先存储轻量列。搜索为 lower + %关键词% + JSON/多个 EXISTS，普通字符串索引不能直接解决包含搜索；达到量级后单独评估全文检索。

模板详情已有 10 分钟 Caffeine 缓存，效果明显；版本详情命中后仍检查当前发布状态，是有意保证撤回/转私有及时生效，不应简单删除这次检查。

### P2-2：列表读取过量字段与分页边界

项目列表查询 VIEW 含 draft_json，但响应不返回 draft，属于不必要数据库传输。歌词列表先读取整个 document_json，再在 Java 移除 history/alternatives，最多 50 个草稿，每个最多 20 个快照。应新增摘要投影，而不是取消历史数据保存。视频列表读完整 JOB_COLUMNS，包含 request/result/evidence JSON，应改摘要 DTO。歌曲列表包含完整 lyrics，评估列表是否需要全文。

项目/素材/音色/草稿列表有 limit 上限，但缺乏完整下一页游标能力；这既限制访问也掩盖容量问题。视频与模板使用 OFFSET，深分页需评估。歌曲已有游标分页与 pageSize 上限，方向合理，但排序索引仍需匹配。

### P2-3：素材上传/读取占用服务端带宽

POST assets 先接收并暂存磁盘、计算哈希，再写 R2 本体和元数据，随后去重登记；重复文件不一定免上传成本。建议评估预签名直传+完成校验，保留内容大小、类型、哈希和归属校验。

GET assets/{id} 在后端返回短时 R2 重定向，而 Next.js 为音频同源/CORS主动跟随并代理流。因此此端点耗时包含媒体传输，不能用普通 JSON 接口的 300 ms 标准；生产应测首字节、吞吐、Range、取消传播及大文件内存。不要直接去掉代理破坏音频访问。

视频输出 R2 分支为签名跳转，本地分支为 64 KB 缓冲流式响应，方向合理；应分别统计“拿下载地址”和“下载全部视频”。浏览器渲染时间、上传时间、导出登记 API 时间也是三个指标。

### P2-4：支付状态查询包含全局对账和 Stripe 请求

billing/status 在配置启用后调用 repo.reconcile（不是仅当前用户的更新），然后余额、客户和 Stripe subscriptions。高频 GET 会触发跨用户对账和外网请求。建议独立后台对账，状态接口读本地订阅快照。支付幂等和账务逻辑本次不做功能验收、不实际支付。

### P2-5：缺少支持全站判断的统一耗时观测

D1DatabaseClient 当前解析丢弃 meta.duration / rows_read / rows_written，仅保留结果与 last_row_id。application.yml 默认包含 Spring Web DEBUG，需核实生产覆盖；详细参数日志会增大 I/O 并可能含业务数据，不能作为长期性能监控。

建议按规范化路由记录请求数、错误率、P50/P95/P99、D1 往返数/总耗时/服务端执行时间/扫描行、R2 请求数/字节、外部服务耗时、缓存命中和连接池等待。只记录脱敏标签，不记录歌词全文、token、签名 URL。前后端贯通 request ID，才能定位 1 秒发生在代理、Java、D1 还是供应商。

## 三、逐接口审查清单

下面每行是一个方法与路径。前缀均为 `/api/music-mv/v1`。除第一节明确给出成功样本外，均为**代码分析，成功路径耗时未实测**。受保护接口通常还需加一次会话 D1 查询；不同可选分支可能更多。PK 表示主键；“合理”仅指当前结构，不表示通过负载验收。

| 方法与路径 | 查询/索引与主要耗时来源 | 判断/建议 |
|---|---|---|
| `GET /assets` | recent 使用库索引但 last_used_at 额外排序；project JOIN | P1：匹配最近排序，分页 |
| `POST /assets` | 磁盘暂存+哈希+R2两次写+哈希索引去重/登记 | P2：直传会话；用MB/s与文件尺寸验收 |
| `GET /assets/readiness` | 登录校验+存储可用性判断 | 只抽样到 401，成功耗时未知 |
| `DELETE /assets/{assetId}` | PK、引用关联检查、R2删除、状态更新 | 保留引用检查；远程删除可用任务化收敛 |
| `GET /assets/{assetId}` | capability/元数据，R2；Next同源媒体代理 | 按首字节/吞吐衡量；支持取消与Range |
| `POST /auth/logout` | 会话 token 定位后撤销 | 索引方向合理，未执行退出 |
| `GET /auth/providers` | 配置读取，不查业务库 | 实测快速 |
| `GET /auth/session` | token_sha256 索引 JOIN user；5 分钟节流 touch | 匿名实测不代表登录态；短期缓存需考虑撤销 |
| `POST /auth/sso` | 外部身份验证；identity 唯一键；多次用户/session 写入 | 未实测；记录外部验证与落库分段，保留身份正确性 |
| `GET /billing/capabilities` | 配置判断 | 实测快速，不代表Stripe端到端 |
| `POST /billing/checkout` | 客户PK、幂等claim+Stripe多次调用 | 静态审查；未支付，保留幂等 |
| `POST /billing/portal` | 客户PK+Stripe portal | 外部延迟为主；未调用 |
| `GET /billing/status` | 全局reconcile+余额索引/客户PK+Stripe订阅 | P1启用后：移除GET内全局对账，读本地快照 |
| `POST /billing/webhook` | event PK幂等，grant/reservation索引与写入 | 尽快持久化/异步对账；不实际回放 |
| `GET /lyric-drafts` | owner/updated索引匹配；完整document_json后删history | P2：轻量摘要，补游标 |
| `GET /lyric-drafts/{id}` | 复合PK(user_id,draft_id) | 索引合理；大历史按需返回 |
| `PUT /lyric-drafts/{id}` | 读旧草稿+条件upsert+回读，最多20快照 | 3次业务D1；保留revision；自动保存合并请求 |
| `POST /lyrics` | 同步上游submit；加密任务handle，无任务结果表 | P1：异步状态与幂等/限流设计，未真实调用 |
| `GET /lyrics/{taskId}` | 验证handle后每次queryLyrics | P1：结果缓存、轮询合并和限频 |
| `GET /projects` | user_id 索引筛选，updated_at 额外排序，读 draft_json | P1：匹配排序索引；摘要投影/分页 |
| `DELETE /projects/{projectId}` | 归属详情 PK + softDelete PK | 结构可接受；避免读取大 draft |
| `GET /projects/{projectId}` | 项目 PK 查询+素材关联查询 | 两次业务 D1，可考虑批次；未实测 |
| `PUT /projects/{projectId}` | 2N+5 业务 D1，素材 PK 与批次写入 | P1：批量校验/touch；保留 revision 并发保护 |
| `POST /provider-webhooks/kie/music` | provider/task唯一键+候选upsert/事件 | 回调写入批量化，保留验签/幂等 |
| `POST /provider-webhooks/sunoapi/lyrics` | 仅接受并返回204，不保存结果 | 请求轻量但迫使GET依赖供应商轮询 |
| `POST /provider-webhooks/sunoapi/music` | provider/task唯一键+候选upsert/事件 | 同上；实际处理时延未测 |
| `GET /render-jobs` | client索引+额外排序，OFFSET；完整JOB_COLUMNS | P1：归属时间索引与摘要DTO，游标 |
| `POST /render-jobs` | client/request 唯一键；创建后异步准备 | 提交快不等于ready快；用户素材R2逐个验证 |
| `DELETE /render-jobs/{jobId}` | PK归属检查+对象清理/记录删除 | 区分DB与对象删除延迟，未执行 |
| `GET /render-jobs/{jobId}` | PK+事件job索引；活跃阶段重建browserRender | P1：状态与场景拆开，减少重复大JSON |
| `POST /render-jobs/{jobId}/browser-output/complete` | PK/attempt校验+R2 objectInfo 或本地尺寸+更新 | 无需完整重下载成片，方向合理；测HEAD耗时 |
| `POST /render-jobs/{jobId}/browser-output/fail` | PK状态写入+可能清理对象 | 限定清理耗时，记录失败原因 |
| `PUT /render-jobs/{jobId}/browser-output/local-upload` | PK会话校验+文件流/哈希 | 仅本地模式；按吞吐与磁盘容量测 |
| `POST /render-jobs/{jobId}/browser-output/upload-session` | PK/attempt校验；预签名或本地上传地址 | 预签名方向合理；不应代理R2大文件 |
| `POST /render-jobs/{jobId}/browser-render/start` | PK claim/租约+场景hash/回读场景 | 拆分轻量claim返回与不变场景 |
| `POST /render-jobs/{jobId}/cancel` | PK条件更新+事件及回读 | 合理保留条件状态转换；未真实取消 |
| `GET /render-jobs/{jobId}/output` | PK归属；R2签名跳转或本地Range流 | 分别统计地址返回与文件下载，不混算 |
| `GET /songs` | 用户任务索引 JOIN 候选，游标；跨任务排序/搜索 | 分页已合理，排序仍需优化，列表含lyrics |
| `POST /songs` | user/request 幂等索引、任务/尝试/事件多次写+同步上游submit | P1：提交deadline/异步化；不能自动重放新任务 |
| `GET /songs/{jobId}` | 任务PK+候选job索引；后台刷新限频/租约 | 方向合理；与外部刷新耗时分开监测 |
| `POST /songs/{jobId}/candidates/{candidateId}/select` | 归属JOIN查询+选择事务batch | 结构相对合理，不在此处重生成 |
| `GET /template-categories` | enabled 过滤，sort_order 排序；小字典 | 实测约 0.24–0.35 秒；缓存收益优先 |
| `GET /template-tags` | 标签+翻译两次查询；小表排序 | 实测约 0.6 秒；缓存字典/批量读取 |
| `GET /templates` | COUNT+SELECT，目录/关系索引，JSON与搜索表达式，OFFSET | P1/P2；见模板查询专项 |
| `GET /templates/{templateId}` | PK/slug 与关联读取；10 分钟详情缓存 | 首次 2 秒、后续 8–20 ms；大场景应拆分 |
| `GET /templates/{templateId}/similar` | 模板定位+相同分类候选列表 | 约 0.65–0.70 秒；公开结果可缓存 |
| `GET /templates/{templateId}/versions/{versionId}` | PK/版本键；缓存仍校验发布状态 | 本次 0.28–0.44 秒；保留状态约束 |
| `GET /text-optimization` | 配置可用性判断 | 实测快速 |
| `POST /text-optimization` | 同步非流式模型调用，读超时45秒 | P2：deadline留余量；并发上限/取消/任务化 |
| `GET /voices` | 首次DDL；后续user/created索引，limit100 | 实库表尚不存在；初始化移至部署期，补分页 |
| `POST /voices` | R2归属检查+insert+上游校验+update+回读 | 同步外部耗时；未调用，需任务化 |
| `GET /voices/{id}` | PK；processing时上游状态+可选更新+回读 | P1：processing合并/限频，最终态缓存 |
| `POST /voices/{id}/verify` | PK状态竞争控制+R2归属+外部generate+写回 | 保留条件claim；限制并发与请求deadline |

**Next.js 自身及间接依赖：**

| 依赖 | 检查结果 |
|---|---|
| GET /api/site-manifest | 无数据库，1 小时公开缓存；首次开发访问 573.6 ms，后续 3–5 ms |
| GET /api/browser-renderer-identity | 开发环境动态计算指纹，本次55–109 ms；生产读环境预置值，不应用开发耗时预测生产 |
| /api/music-mv/v1/[...path] 通配代理 | no-store；部分55秒timeout，其他无统一deadline；资产重定向被代理，输出重定向直接透传；需端到端取消和分阶段耗时 |
| R2 上传/下载、Cloudflare 图片/视频分发 | 不在Java接口索引范围；应单独测地区、首字节、缓存、Range、大小与吞吐 |
| 原生渲染SDK/WASM、运行资源、字体/模型 | 静态/外部资源等待属于端上准备链路；不能归咎于数据库；本轮不重复生成或渲染 |
| OIDC、Suno/Kie、文本模型、Stripe | 外部处理时长与网站接单接口区分；真实有成本调用未执行 |
| 8082运营/同步、/internal研究页面 | 不属于客户网站直接依赖；不把这些内部路由计入53个公众业务API。发布同步的后台容量需另立专项 |

## 四、建议推进次序与验收方式

1. **先补观测及项目保存批量化**：请求ID与分段计时；42绑定成功保存时，验证D1往返降为固定数量级，数据归属/revision行为不变。不得用去掉校验换速度。
2. **补项目、素材、视频库的匹配索引**：隔离数据上覆盖当前量级、每用户千条/万条；比较完整查询计划、扫描行、P95、写入成本；上线索引后再测，不能只凭EXPLAIN核心片段保证效果。
3. **拆开状态/场景、字典/列表缓存**：模板发布/撤回/变私有时失效，私有数据不跨用户缓存；签名URL不会因缓存延长而过期失效。
4. **统一外部请求预算、任务化和限频**：歌词/音色状态、提交、文本优化；保留幂等和失败恢复，明确池大小及排队上限。
5. **改善媒体传输和摘要分页**：再做直传、列表大字段、深分页；在真实部署区域验证，而非以本地开发首次编译速度验收。

建议的验收目标（尚未达到或承诺）：公开字典/已缓存摘要 P95 ≤200 ms；普通登录态列表/详情 P95 ≤500 ms；42绑定项目保存 P95 ≤1.5 s；轻量任务状态 P95 ≤300 ms。计时基准为稳定部署的同区域HTTP链路、明确数据量和并发；不含视频/歌曲生成及完整媒体上传下载。若继续跨洲访问D1 HTTP，应先根据往返基线重新定SLO。

## 五、尚未完成的性能验收

- 登录态每个成功端点的真实耗时、失败率及各分支；本次未借用/导出浏览器登录Cookie，也未创建额外会话。
- 写接口、删除、真实供应商提交、支付/退款、上传导出；未用401、模拟或历史成功结果冒充本次测试。
- 生产部署距离、真实用户设备/网络、并发压测、连接池饱和、CPU/堆/GC、生产P95/P99。
- 全量SQL在生产数据分布上的EXPLAIN/扫描行和索引收益；本次只执行明确列出的7个核心计划，其中音色表缺失未取得计划。
- 服务当前实际运行字节码与工作区源码可能存在差异；HTTP样本反映当前服务，源码审查反映当前工作区。两端已有他人未提交渲染改动，本次没有覆盖它们。

**因此：这是有实际数据支撑的全接口性能审查报告，不是“所有接口性能已验收通过”的结论。最明确、最先值得改的是项目保存的串行数据库访问。**

## 六、可追溯源码与证据

原始脱敏记录见同目录 `backend-performance-audit-2026-09-23-evidence.json`：53路由及控制器行号、45个HTTP样本、实际表/索引定义、核心EXPLAIN、聚合计数及D1探针。无API密钥、Cookie、作品内容或媒体访问签名。

关键源码（当前工作区）：
- [service/MusicMvProjectDraftService.java:49](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/MusicMvProjectDraftService.java:49)
- [repository/MusicMvProjectDraftRepository.java:17](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/repository/MusicMvProjectDraftRepository.java:17)
- [repository/MusicMvUserAssetRepository.java:65](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/repository/MusicMvUserAssetRepository.java:65)
- [repository/MusicMvRenderJobRepository.java:307](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/repository/MusicMvRenderJobRepository.java:307)
- [repository/AiMusicJobRepository.java:207](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/repository/AiMusicJobRepository.java:207)
- [service/MusicMvRenderJobService.java:169](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/MusicMvRenderJobService.java:169)
- [service/MusicMvRenderJobService.java:807](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/MusicMvRenderJobService.java:807)
- [service/MusicMvInputAssetStorageService.java:216](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/MusicMvInputAssetStorageService.java:216)
- [service/MusicMvTemplateCatalogService.java:158](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/MusicMvTemplateCatalogService.java:158)
- [repository/MusicMvTemplateCatalogRepository.java:181](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/repository/MusicMvTemplateCatalogRepository.java:181)
- [service/TemplateTagService.java:56](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/TemplateTagService.java:56)
- [service/D1DatabaseClient.java:104](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/D1DatabaseClient.java:104)
- [service/CloudflareRestTemplateFactory.java:14](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/CloudflareRestTemplateFactory.java:14)
- [service/MusicLyricsDraftService.java:21](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/service/MusicLyricsDraftService.java:21)
- [aimusic/AiLyricsGenerationService.java:68](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/aimusic/AiLyricsGenerationService.java:68)
- [aimusic/AiVoiceService.java:19](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/aimusic/AiVoiceService.java:19)
- [billing/MusicBillingService.java:24](/Users/zongjei/Documents/code/cursor-quttr-web/src/main/java/com/example/cursorquitterweb/musicmv/billing/MusicBillingService.java:24)
- [Next.js代理](/Users/zongjei/Documents/code/capcut/app/api/music-mv/v1/[...path]/route.ts:43)
- [SSR读取和缓存](/Users/zongjei/Documents/code/capcut/lib/music-mv-api.ts:14)
