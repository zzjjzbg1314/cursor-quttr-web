# 网站后端原生场景 v2 契约交付

网站后端 BrowserNativeRuntimeContract 增加 browser-native-scene-runtime-v2 支持，保留 v1。SDK 同源目录、文件清单、资源归属、动画绑定和模型声明沿用严格检查。没有改动已上架模板或版本指针。

两个直接影响的 Java 测试类共 13 项通过，覆盖契约和 TemplateRuntimePackageService 的交付结果，验证 v2 描述保持原值，未交付依赖、错误路径和未知版本继续拒绝。服务测试使用存储与仓库模拟，不代表生产发布成功。

## 基础验证

任务 migration-native-v2-backend-export：新 0909 诊断资源契约的描述经当前后端 Java 校验器处理，14 个依赖，再进入正式前端 createBrowserRuntimeSession 与 renderBrowserMusicMv。通过 textOverrides 将第一层文字修改为 Our Story，并替换第一张照片。三层文字保留原始材质、字体、变换和动画。

成功输出 1080×1920、30 fps、1049 帧、34.966667 秒 H.264 视频，32266647 字节。渲染记录耗时 23.129855 秒，阶段总耗时 24.559195 秒，监督总耗时 32.557 秒。semanticIntegrity=exact，videoEncodeCount=1，materializedIntermediateVideoCount=0。ffprobe 验证帧数和时长；第 1 秒画面确认 Our Story 与替换照片可见。

单一工作进程与 15 分钟硬超时；无 Writer 残留。Chrome 扩展下载残留如有则记录文件头、大小和哈希后清理。未执行 SSIM。

## 尚未完成

本次没有重启 8080，也没有经过登录后的保存、发布和重新加载流程。研究端自动编译仍只生成 v1，下一步需补齐 v2 原始文字投影及最小依赖选择，再进行正式发布链路验证。未迁移已上架版本，未退役旧实现。
