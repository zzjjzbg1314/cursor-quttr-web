# 非线性场景保存与读取契约验证

使用研究端实际分析并准备的场景作为夹具：恒速 2 的非线性图形关键帧场景可以同步；曲线变速 1→3、缺少 draft_keyframe_clock 且 photoAnimationReady=false 的场景明确拒绝保存。

两个针对性检查通过 Java 8 编译并执行。正例运行生产 synchronizeBrowserScene，捕获 upsertBrowserScene 的完整 JSON，再交给生产公开 detail 读取；逐字段比较原场景、持久化字符串与返回场景，确保图形关键帧及 nativeSpeedBinding 不丢失。反例断言 TEMPLATE_BROWSER_SCENE_ANIMATION_NOT_READY，并验证未调用场景写入。

夹具来源为 pengyouquan-web/storage/data/browser-template-parity/native-nonlinear-pipeline-validation-20260911/constant-backend 与 referenced-graph。仅替换测试目标模板和版本标识，并附上实际准备的 nativeEngine 清单。

测试使用真实服务与 ObjectMapper，repository、资源和运行包服务为 mock；不代表 D1/R2 实际往返、运行包下载或云端发布。没有生产代码修改，无新渲染或 SSIM，不需要重复基础渲染。曲速关键帧缺少研究端时间上下文声明的问题尚未修复，不能修改 readiness 标志冒充支持。
