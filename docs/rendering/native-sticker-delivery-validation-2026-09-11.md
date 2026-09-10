# 原生贴纸网站交付验证（2026-09-11）

## 结果

模板 tpl_c69a5822edfb2019（较早导入的同名0909）已完成迁移发布。实时查询 intake_2ce664c83bd24d6ebd4979f330fe12d7 为 published/completed，网站 currentVersionId 为 tplver_d6b9335b4d7d4a57821946151ef392f2。本地版本 tplver_7fc965afc851020a。

## 通用改造

按官方 Web 的 SDK 轨道类型与排序复用轨道，保留 Draft 轨道用于关联片段。按原始资源执行普通 INFO_STICKER 贴纸及附属动画，严格识别已声明的空动画集合。贴纸 Lua 静态依赖分析只收集实际文件，不引入完整包。

网站后端新增 v4 stickerPolicy=original_resources_v1 策略，要求 sticker 依赖包含 config.json 与 infoSticker.lua。BrowserNativeStickerContract 校验源身份、原始时钟、层顺序、变换、附属动画和资源映射；运行包交付及原生效果能力判定共用检查。未知关键帧、未支持贴纸种类和缺失依赖继续阻塞。

## 验证证据

后端23项聚焦测试通过：BrowserNativeRuntimeContractTest 11、BrowserNativeStickerContractTest 3、TemplateRuntimePackageServiceTest 7、BrowserNativeSceneCapabilitiesTest 2。真实契约审计确认1个原生贴纸、5组运行依赖。前端渲染相关221项测试及类型检查在渲染前通过。

本地任务 bparrun_f389f646d8e5e954：1080×1920、30fps、976帧，渲染24.916585秒；基础7点 SSIM 0.990909，关键3点0.992276。

网站任务 0909-native-sticker-website-20260911-attempt-1：1080×1920、30fps、976帧、32.533333秒视频。渲染24.563915秒，含准备等总耗时39.023550秒。semanticIntegrity=exact，videoEncodeCount=1，materializedIntermediateVideoCount=0，OPFS文件0，无Writer或原始帧残留。

网站 SSIM vpar_27689eaa177e0f4f：基础7点平均0.991135、最低0.987300、MAE 5.4017；关键3点平均0.992365、最低0.990079。10点均超过0.90，无区域排除、无时间对齐。这是抽样验收，不是976帧逐帧比较。

网站验证场景和运行资源来自8080候选交付。音乐使用原始参考视频用于基准验证，因此不能用本次结果声称完成客户所有上传、换图和编辑操作验证。运行包1852888字节。

原始参考 SHA256：806c3c7a84b3042ab8cdd157051ebff0d55863d3dbd6fe625b93c64ef3a01259。
网站输出 SHA256：dc86b3ef510669048bac89adabdfe9c6969cdfd5a60805150b3ff29bf32999d6。
渲染器指纹：71a2d9af575d153742a993d1072a45169f703a2584d85691c71949635457fd22。

证据目录：/Users/zongjei/Documents/code/pengyouquan-web/storage/data/browser-template-parity/native-migration-older-0909-20260911/，包含 website-render-result.json、website-parity/result.json、publication-status.json、website-published.json 和 website-output.mp4。

## 剩余目标

本次完成普通资源贴纸这一类能力和本模板迁移，不代表全部场景覆盖。继续验证客户手动换图、裁剪、撤销重做等操作，并审计剩余场景；不得据此退役所有自研实现。不重复渲染0908，不执行旧版回退渲染矩阵，不push。
