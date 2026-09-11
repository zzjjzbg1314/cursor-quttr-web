# 零照片贴纸浏览器会话交付检查

在任务入口修复 dc3f10a 后，沿实际 `MusicMvRenderJobService.get` → `browserRenderView` → `BrowserRuntimeContractFactory` 检查后续交付，未发现第二处非空照片条件。

新增跨服务契约测试 `deliversZeroPhotoStickerSessionWithOriginalSourceAndMinimalDependencies`，使用真实服务与原始贴纸夹具，模拟 repository、媒体存储和最小包签名服务，不联网、不保存或上架模板。

验证：

- 已准备任务返回 renderMode=browser、slotBindings=[]，原始场景对象完整保留。
- 原始贴纸图像经独立精确资源接口交付；nativeEngine 描述及最小包地址保留，无扁平化视频替代。
- 不查询默认照片，不将存储私有 objectKey/errorMessage 交付给浏览器。
- 非所属用户仍被拒绝；精确资源不可用时错误继续上抛，不改成空资源或静默跳过。

新增测试通过，完整该服务 29 项测试通过。此次只增加测试，不改变产品执行代码，没有启动真实渲染或 SSIM，无需重启服务。该证据补齐网站任务会话构造边界，不代表实际 D1/R2 发布、签名下载、客户导出保存全链路验收。
