# 网站接口请求摘要

覆盖 `/api/music-mv/v1/**`，复用现有请求过滤器，每次请求结束只记录一行。

- 固定字段：requestId、HTTP 方法、路由模板、HTTP 状态码、elapsedMs。
- 可选业务字段：响应顶层的 code、retryable、status、available、enabled、jobId、projectId、assetId、remaining、received。没有值不输出；只接受短标识、数字和布尔值。
- 未处理异常只记异常类型，业务异常附错误码，不记录异常消息或重复堆栈。不改变异常处理或响应。
- 不记录正文、查询参数、Cookie、Authorization、支付密钥、下载签名链接。媒体响应不缓存、不解析。
- HTTP 200 不代表业务可用，应结合 available 或业务 status 判断。

正常请求 INFO，4xx WARN，5xx ERROR。独立 logger `musicmv.requests` 在 prod 环境也启用 INFO，输出控制台及运行目录 `logs/system.log`；ERROR 同时进入 `logs/error.log`。沿用既有滚动规则。

浏览器网络面板的响应头 `X-Request-Id` 与日志对应，网站代理透传该响应头。请求未匹配控制器时路径记为 unmatched，避免输出任意用户路径。

验证：过滤器、业务摘要 MockMvc、原有计费控制器定向测试；前端类型/本地化检查和代理请求编号测试。没有改变渲染执行、支付或九语言文案，不需要视频渲染验证。
