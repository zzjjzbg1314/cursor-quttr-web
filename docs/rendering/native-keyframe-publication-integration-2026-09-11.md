# 原始曲速关键帧发布契约与基础导出

## 修复

研究端 BrowserNativeRuntimePreparation 明确要求网站生产规划器输出 verifiedScene。规划成功后，只合并 capability 与 capabilityReport；原始图层、关键帧、曲速源声明、资源和时间参数必须语义相等，原始 Java 参数对象保留。最小包使用最终合并场景生成指纹，归档成功后才更新调用方场景。

原生图层过去在编译公共 Render IR 时又被自研关键帧时钟解析。现在 prepareTemplateRuntime 先选择有明确准备契约的引擎；原生引擎通过完整能力检查取得归属后，编译用于合成调度的 IR。原生已接管图层的原始关键帧及 null 时钟保留，由原生计划执行，不再进入自研插值。未提供原生归属的普通 IR 编译仍拒绝该时钟；未接管图层仍按原检查执行。

网站后端不放宽保存门禁。新增真实研究投影场景的服务回归，确认曲速场景可以保存并由公开详情原样读回；未验证场景继续拒绝。

## 检查与问题修复过程

- 前端 29 项相关测试、TypeScript 检查、脚本语法检查通过。
- Java 原生准备 5 项检查通过，覆盖资源收集、缺失源阻断、禁止改动原始场景、原始 2.0 数值表示保留。
- 真实 Java Draft 分析与准备：有效引用、匹配内嵌图形通过；缺失、冲突图形阻断。源数据与已上架版本均未修改。
- 后端 3 项服务检查通过：恒速原场景、验证后的曲速场景完整保存读取，以及未验证场景在写入前拒绝。使用真实服务及 ObjectMapper、模拟 repository/存储依赖，不是 D1/R2 实际发布。
- 首次基础验证在 IR 编译阶段报 Invalid Draft keyframe clock，尚未启动渲染；修复引擎归属后导出通过。
- 归档检查发现 Java/JS 数值序列化使包指纹与最终调用方场景不一致；修正为保留原始参数对象后，再次分析准备，并以生产 sceneFingerprint 方法核对最终场景与最小包指纹相等。最终代码后基础导出再次成功。

## 最终实际浏览器基础导出

验证标识 native-keyframe-publication-integration-20260911-final。场景来自原始 Draft 的 Java 分析与最终准备结果，保留 draft_keyframe_clock=null、原始 1→3 曲速及 CurveInOut 图形关键帧。未在页面手工把时钟设为恒速，未删除效果或关键帧。

- Chrome，1080×1920，30fps，0.6 秒，18 帧；单 worker，15 分钟硬超时。
- completed，渲染记录耗时 1.089810 秒，prepare 3.653560 秒，总流程 4.717305 秒。
- semanticIntegrity=exact，videoEncodeCount=1，materializedIntermediateVideoCount=0，OPFS 文件数 0，无 Writer 残留。
- 成片 131133 字节；SHA256 8944f9ead53ab4ae8081ec0b3c750bc10f5d4fcfcb958d14338db687df7bd6d0。ffprobe 核对 18 帧。
- 渲染器指纹 3e488234f2057a69e4a74f5e56e2ca3378b634d3e66121dfbb6868b21505bd87。
- 浏览器使用此次规划产生的 nativeEngine 描述和空资源交付。ZIP 仅清单；此验证不代表重新从网站后端下载 ZIP 或正式上架完整流程。

证据保存于研究项目 storage/data/browser-template-parity/native-keyframe-publication-integration-20260911/：原始与最终场景、最小包、失败证据、最终契约、页面归档、测试日志、最终结果及成片。临时页面、公开媒体副本及本轮浏览器标签已清理。

## 尚未完成

本次完成该类原始场景的本地分析准备、后端服务保存读取和真实浏览器基础导出。尚未进行实际云端发布往返；非线性组合没有独立官方参考成片，因此未运行新 SSIM，不宣称该组合已画面验收达标。0908 未重跑，旧模板未改动，未 push。

用户报告编辑页面播放预览卡顿，已明确定位为预览环节反馈；按用户最新指示暂缓优化，优先继续整体迁移。该卡顿原因尚未诊断，不能视为已解决。
