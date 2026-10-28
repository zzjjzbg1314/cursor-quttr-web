# 本地低清预览服务

本机旧 8080 后端仍被其他任务使用时，可单独运行此入口，复用正式后端的
TemplatePreviewMp4Service、D1 仓库和 R2 存储实现。它只监听回环地址，
只提供经过独立同步令牌保护的 preview-mp4-low 三个接口，不扫描整个应用，
不启动原后端的定时任务、歌曲生成、支付或用户接口。

当前机器安装记录：

- 服务标签：ai.songtovideo.template-preview-low
- 端口：127.0.0.1:41239
- 运行快照、日志、私有配置：~/Library/Application Support/StoryAI/template-preview-low/
- 启动配置：~/Library/LaunchAgents/ai.songtovideo.template-preview-low.plist
- 8082 通过启动参数 --spring.config.additional-location=file:.../admin-8082.properties
  加载低清开关、独立地址和同步令牌。不要把此配置放进所有端口共用的 env.config。
- plist 和私有配置仅当前用户可读；不提交令牌或存储凭据。

服务由 launchd 随用户登录启动并在退出后恢复。查看状态：
launchctl print gui/501/ai.songtovideo.template-preview-low。
需要更新运行代码时，先编译和验证正式后端，将必要的编译资源与此入口复制到
运行快照，再重启此服务；不要重启 8080。

以后 8080 已更新并提供低清接口时，可删除 8082 的独立低清地址与令牌覆盖，
保留低清开关，恢复使用统一后端。先验证上架双份生成，再停止独立服务。
