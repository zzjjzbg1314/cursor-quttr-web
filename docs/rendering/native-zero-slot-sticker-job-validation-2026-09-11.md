# 纯贴纸模板渲染任务入口修复

## 原因与修复

客户编辑组件能够提交零照片请求，已验收入库与同步也允许明确的零照片场景，但网站 `MusicMvRenderJobService.verifiedNoPhotoSlotScene` 只列出文字、固定图片和视频。纯贴纸场景因此在 `prepareBrowserAsync` 被标记为 `MV_RENDER_TEMPLATE_HAS_NO_SLOTS`，尚未执行浏览器渲染。这是后端任务入口遗漏，不能用先前独立基础渲染成功代替该入口检查。

新增通用贴纸归属检查：先通过现有 nativeEngine v4 运行契约与最小依赖验证，再要求 `stickerPolicy=original_resources_v1`、原始贴纸源身份一致、附件已绑定、资源为 sticker 类型且有对应交付。缺少源、未知附属效果、遮罩、转场和未支持混合继续阻断。完整时间、关键帧和着色执行仍交给现有生产规划器，不在后端另写一份渲染器。

规则对应前端 `compileNativeSceneStickerPlan` 的原始源与绑定语义；该规划器已经以原始 Lua/config 进入官方执行器。没有按任何模板 ID 或资源 ID 放行。测试夹具保留此前真实纯贴纸场景的原始图层/源和原生描述，将 runtimePackage 的交付结构投影为网站保存的 runtimeDelivery；不包含冗余精灵图集或媒体地址。

## 验证

- 新测试在旧生产代码上复现拒绝（1 项失败，错误码如上）。
- 修复后后端服务 28 项、控制器及原生契约 18 项通过。新测试内部覆盖 14 种输入：合法贴纸通过，缺策略、旧引擎、缺源、未绑定、身份错配、缺绑定、错误资源类型、缺依赖、缺 Lua、未知附属效果、误标照片、多余照片绑定、未就绪均拒绝。
- 前端贴纸规划 6 项和 TypeScript 检查通过。
- 后端任务准备测试使用真实 service、模拟 repository；没有创建真实 D1 模板或渲染任务。

最终代码后的独立浏览器基础导出：`native-sticker-slot-validation`，单 worker，15 分钟硬超时，Chrome nativeEngine，1080×1920、30fps、16.133333 秒、484 帧；completed，渲染记录耗时 5.133470 秒，prepare 4.231055 秒，运行总耗时 9.331790 秒。semanticIntegrity=exact，videoEncodeCount=1，materializedIntermediateVideoCount=0，OPFS 文件数 0。ffprobe 确认 H.264、484 帧。

成片 `/Users/zongjei/Downloads/native-sticker-slot-validated.mp4`，1,141,594 字节，SHA256 `f88716cf648dfba6c482f104af0392f5adce97df1c61c08c255299469a44cf66`，渲染器指纹 `7eb65be869c6c53fbd26f319b9012595cfc9349c25e2291a171f9c12dc81b804`。

最小资源 ZIP 456,794 字节，SDK 使用共享同源资产。音频沿用先前纯贴纸测试成片的音轨作为测试输入，不作为官方参考视频。此次没有独立官方参考，因此未运行新 SSIM；不宣称纯贴纸已通过完整云端发布/客户导出往返或画面一致性验收。当前正式模板未变，0908 未重跑。

临时网页及公开测试媒体已清理，测试标签已关闭；紧凑日志保留 `/tmp/native-sticker-slot-fix/`。本次未 push。
