# 三节点部署后线上只读回归

日期：2026-11-03，北京时间。中国 kejiapi.cn、新加坡 globalkejiapi.asia、美国 globalkejiapi.top 全部覆盖。通过域名检查，未登录服务器核验进程 SHA、实际物理节点或负载均衡后所有实例。

## 结果

| 节点 | 主批次成功 | 有成功样本的请求变体 | 成功请求中位耗时 | 最大耗时 | gzip 响应数 |
| --- | ---: | ---: | ---: | ---: | ---: |
| https://kejiapi.cn | 77/78 | 26/26 | 333.5ms | 2250.2ms | 77 |
| https://globalkejiapi.asia | 78/78 | 26/26 | 636.2ms | 1137.7ms | 78 |
| https://globalkejiapi.top | 78/78 | 26/26 | 821.0ms | 1530.4ms | 78 |

主批次 26 个公开 GET 请求变体，每节点各 3 次，共 234 次。233 次 HTTP 200、JSON 可解析且 success=true；中国音乐完整列表一次 TLS 握手失败，未收到 HTTP 响应，不能判定业务接口错误。该请求其他两次成功，追加 3 次复测也全部成功。所有节点均未观察到 HTTP 4xx/5xx 或 success=false。

另做 9 次社区专项请求：每台首页降序、第二页降序、首页升序，全部通过页码、尺寸、条数、总数下界、帖 ID 不重复、统计非负、未删除和排序检查。只核对返回结构及内部一致性，未连接数据库逐条核验评论真实数量。

三节点均观察到 gzip，curl --compressed 解码后 JSON 正常；这确认协商与解码可用，不代表手机端真实完整体验验收。不在线注入故障，因此未验证生产失败后缓存恢复。

## 网络与测量边界

先强制直连，保存了 31 条完成记录：26 次成功、5 次 DNS 或连接超时。随后停止直连批次，改用本机已有 127.0.0.1:7890 代理完成三节点全批次；主批次的 remote_ip 是本机代理地址，不能当作服务器 IP。直连中途停止，可能有已发出但未形成记录的在途请求。

不能把直连 DNS 超时直接判为服务器宕机；也不能把代理复测通过当作全球用户网络均正常。各请求单独建连，耗时包含 DNS、TLS、代理和网络，不是服务器执行时间，也不是 P95/P99。不同节点所用业务数据不要求一致。未核验 DNS 权威配置、证书剩余有效期、部署版本、资源占用和服务日志。

## 覆盖清单

- `/api/articles/all?page=0&size=10`
- `/api/articles/cn/all?page=0&size=10`
- `/api/articles/cn/grouped-by-type`
- `/api/articles/grouped-by-type`
- `/api/bootstrap`
- `/api/breathe/getAllBreathe`
- `/api/channels/all`
- `/api/meditate-videos/cn/getAllMeditateVideos?page=0&size=10`
- `/api/meditate-videos/getAllMeditateVideos?page=0&size=10`
- `/api/music/cn/getAllMusic`
- `/api/music/cn/page?page=0&size=10`
- `/api/music/getAllMusic`
- `/api/music/page?page=0&size=10`
- `/api/posts/getAllPosts?page=0&size=5`
- `/api/posts/getAllPosts?page=0&size=5&sortBy=created_at&sortDir=asc`
- `/api/posts/getAllPosts?page=1&size=5`
- `/api/pure-musics`
- `/api/quotes/getAllQuotes`
- `/api/quotes/page?page=0&size=10`
- `/api/sister-motivation-videos/getAllVideos?page=0&size=10`
- `/api/sister-motivation-videos/list`
- `/api/video-scenarios/cn/getAllVideoScenarios?page=0&size=10`
- `/api/video-scenarios/getAllVideoScenarios?page=0&size=10`
- `/api/videos/cn/getAllVideos?page=0&size=10`
- `/api/videos/getAllVideos?page=0&size=10`
- `/api/white-noises`

覆盖公开启动、中文及国际视频／音乐／文章／冥想／视频场景、名言、白噪音、纯音乐、呼吸、频道、激励视频及社区分页。26 为请求变体数量，包括同一社区接口不同分页／排序，并非 26 个独立业务接口。

遵守此前授权：线上只读。不触发创建、修改、删除、登录、支付、推送、同步、清缓存或其他有副作用的管理接口；没有冒充全量 503 个接口业务验收。

## 证据与复跑

public-probes.jsonl：234 次主批次元数据；recheck.jsonl：3 次复测；post-validation.json：9 次社区专项；direct-partial.jsonl：直连已完成记录；summary.json：节点汇总。正文解析后丢弃，不保存用户帖子内容。

公开探测可执行 `python3 docs/qa/quittr-three-node-deployment-2026-11-03/probe_public.py --output /tmp/quittr-public-new-run`。会产生新的线上只读请求，沿用运行环境代理。社区专项可执行 `python3 docs/qa/quittr-three-node-deployment-2026-11-03/check-posts.py --output /tmp/quittr-posts-new-run`。

本轮仅新增回归材料，无业务代码或生产配置变更。按仓库规则本地提交，不推送；原有歌词、歌曲导入及特效未提交改动保留。
