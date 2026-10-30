# 火山方舟图片生成配置

适配日期：2026-10-30。仅修改 StoryAI 网站后端 `cursor-quttr-web`。不需要为本次适配新增数据库字段，不修改普通模板或客户浏览器渲染器。

## 申请模型和密钥

1. 登录 https://console.volcengine.com/ark ，选择用于 StoryAI 的项目。
2. 开通 **Doubao Seedream 4.5**，Model ID 为 `doubao-seedream-4-5-251128`，支持参考图编辑。当前适配器只接受此精确模型，不自动切换其他版本或自定义接入点。
3. 在同一项目的 API Key 管理中创建密钥并授权该模型。若启用 IP 白名单，包含网站后端的出口 IP。
4. 配置在后端服务器；不要填写到前端、浏览器或模板 JSON 中。

官方依据：
- 图片教程：https://docs.volcengine.com/docs/ark/image-generation-tutorial?lang=zh
- API：https://api.volcengine.com/api-docs/view?action=ImageGenerations&serviceCode=ark&version=2024-01-01
- 密钥权限：https://docs.volcengine.com/docs/ark/manage-api-keys?lang=zh

这属于公开 API 替代模型，不代表剪映内部 `aip_high_aes_general_v43s_jm`。原模板提示词保留；内部 `denoise` 和历史 `seed` 不映射、不发送到此适配器。

## 配置方式一：后端进程环境变量

```sh
MUSIC_MV_AI_PHOTO_ARK_ENABLED=true
ARK_API_KEY=在服务器填写真实密钥
```

上面是环境变量键值示例，需由服务管理器、容器或启动 shell 实际传给 Java 进程，不是创建任意 `.env` 文件就会自动读取。若需要独立图片密钥，设置 `MUSIC_MV_AI_PHOTO_ARK_API_KEY`，它优先于 `ARK_API_KEY`。未开启时默认关闭，与历史 Kie 配置独立。更新配置后重启后端。

## 配置方式二：外部 properties 文件

复制 `config/ai-photo-ark.properties.example` 到已被 Git 忽略的 `secrets/music-mv-ark.properties`，填入密钥并将 enabled 改为 true。当前本机准备位置为 `/Users/zongjei/Documents/code/cursor-quttr-web/secrets/music-mv-ark.properties`；部署服务器使用服务器自己的对应路径。

在原有 Java 启动命令末尾增加：

```sh
--spring.config.additional-location=optional:file:/你的后端目录/secrets/music-mv-ark.properties
```

如果已有 additional-location，追加逗号分隔的新文件路径，保留旧配置来源。也可通过 `SPRING_CONFIG_ADDITIONAL_LOCATION` 指定。文件包含真实密钥后应只对运行服务的用户开放读取权限。

默认仅允许精确域名 `ark-content-generation-v2-cn-beijing.tos-cn-beijing.volces.com` 下载生成图。不同账号/地域若实际返回其他域名，核对提供方响应后通过 `MUSIC_MV_AI_PHOTO_ARK_OUTPUT_HOSTS` 或 `music-mv.ai-photo.ark.output-hosts` 添加精确域名，多个用逗号分隔；不接受通配符。域名不匹配时阻断，不会为此重复提交生成。此默认域名尚未以本账号真实生成响应验收。

## 模板配方

`ai-photo-ark-recipe.example.json` 是该研究模板的完整替代配方，需绑定到受保护的模板版本场景 `aiPhotoRecipes[实际slotKey]`，并同步到网站后端。配置密钥本身不会自动发布模板、转换 Kie 配方或修改旧生成结果。参数当前只支持 `size=2K` 或 `4K`；建议先用 2K。配方变更后旧输入指纹失效，不复用旧结果冒充新生成。

请求为 `POST https://ark.cn-beijing.volces.com/api/v3/images/generations`，带 Bearer 密钥、原图 HTTPS URL、原提示词、指定模型和尺寸；固定 `sequential_image_generation=disabled`、`stream=false`、`response_format=url`、`watermark=true`，只接受一张有效结果。

后台任务先从 submitting 保存结果 URL 并进入 persisting，之后下载、验证 JPEG/PNG、登记自有资产，最后 ready。没有伪造远端 taskId 或使用 Kie 查询接口。生成请求超时/异常进入 submission_unknown，不自动重新扣费；已保存 URL 的下载或入库暂时失败只重试持久化。超过租约的中断提交沿用现有待核实机制。结果仍受资产归属、原图内容哈希、模板版本、配方和槽位校验；取消后的迟到更新由数据库条件更新拒绝。

## 验证边界

本轮用模拟 HTTP 响应验证官方请求结构与后端状态流转；不包含真实账号权限、额度、费用、结果下载域名或实际生成效果验收。未调用付费服务，未部署，未重新做视频渲染或 SSIM。浏览器协议、渲染规划及执行代码未变化，使用聚焦服务与直接合同测试验证。

对外接口和错误码保持不变，复用客户前端现有十语言生成中/失败文案。提示词按原文发送，测试覆盖十种网站语言与其他语言；不根据界面语言改写素材提示词。

检查结果：`ArkAiPhotoProviderTest` 6 项、`AiPhotoJobServiceTest` 16 项、`KieAiPhotoProviderTest` 6 项、`AiPhotoJobControllerTest` 2 项、`AiPhotoRenderBindingTest` 1 项、`OrdinaryTemplateRenderRegressionTest` 1 项、`MusicMvRenderJobServiceTest` 37 项，共 69 项通过，零失败。`git diff --check` 通过。
