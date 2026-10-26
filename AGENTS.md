# StoryAI Website Backend Agent Rules

## Authoritative role

- This repository is the authoritative backend for the StoryAI customer website in `/Users/zongjei/Documents/code/capcut`.
- It serves the `/api/music-mv/v1/**` website APIs. Local development normally listens on port `8080` and is selected by the frontend's `MUSIC_MV_BACKEND_URL`.
- `aiyingji-houduan` is not the StoryAI website backend. Never implement or redirect StoryAI website project, asset, template-catalog, or render flows there.
- `/Users/zongjei/Documents/code/pengyouquan-web` is the template-management and Mac renderer/research project. It synchronizes published template/runtime metadata into this backend; customer browsers must not call its port `8082` directly.

## Browser rendering direction

- For browser-capable published template versions, issue a browser render session instead of a Mac renderer queue item.
- Keep ownership checks for music, photos, projects, render sessions, and output artifacts in this backend.
- Browser-encoded output should be uploaded to managed storage and registered here so Library and result pages remain cross-device.

## Git 提交规则

- 每完成一个独立功能、代码修改或问题修复，并且相关检查通过后，必须立即创建一个 Git commit。
- commit message 必须使用中文，例如：`修复：照片替换后预览未更新`、`功能：支持批量上传照片`、`重构：简化模板加载流程`、`测试：补充照片槽位映射测试`。
- 提交前检查改动范围，只提交本次任务相关文件，不得包含用户已有的无关改动。
- 如果测试失败、修改尚未完成，或者无关改动无法安全拆分，则不要提交，并向用户说明原因。
- 默认只创建本地 commit，不自动 push。
- 完成后报告 commit hash、中文提交说明、测试结果和剩余未提交文件。
- 新增或修改代码注释时使用中文，但不要给显而易见的代码添加冗余注释。

## 网站十语言兼容要求

- 每次修改网站接口先评估多语言影响，即使用户未特别提出。网站支持 `en`、`zh-CN`、`zh-TW`、`ja`、`ko`、`es`、`pt-BR`、`de`、`fr`、`it`。
- 界面语言与演唱/歌词语言独立；修改生成、歌词辅助、参数校验、默认值、回调或跳转地址时，不得只允许中英文或把其他有效语言归为英文。
- 歌曲/歌词允许值与 `/Users/zongjei/Documents/code/capcut/lib/song-language-preference.ts` 保持一致。语言代码、业务枚举、模型名、用户原文保持稳定；不要翻译数据库键或根据界面语言改写用户歌词。
- 新增用户可见错误应返回稳定错误码，并检查前台十语言映射或合适的本地化兜底；后端日志不要求翻译九份，不将服务商原始错误直接当用户提示。
- 涉及网站用户可见行为时同时阅读前端 AGENTS.md 的多语言完成条件，检查前后端参数及文本长度限制。测试覆盖有效语言、未知值和界面/歌词语言不同的情况；不得为验收触发真实付费生成。
- 交付说明多语言影响及实际验证范围；纯内部调度、日志等无本地化影响的修改如实说明，无需无关改动词典。

## 模板标题多语言维护

- 模板标题与界面文案独立维护，`template_translations.name` 必须覆盖十种网站语言。保留人工中英文源标题，其他八种标题按本地表达翻译；描述同样覆盖十语言，保留人工中英文源描述，其他八种由同一后端服务翻译；源描述变化重新翻译，完整且未变时复用。
- 新增模板、修改源标题、发布模板均经过标题本地化服务。完整且源标题未变时复用已有译文；源标题改变先完成翻译再保存，服务失败明确阻断，禁止用英文或原文假充目标语言。
- 既有模板只补标题时使用受同步凭证保护的 `/internal/music-mv/v1/templates/{templateId}/titles/localize`：先 dry-run 留存备份，再携带版本和源标题校验执行。不得借此修改媒体、运行包或发布版本指针。

- 描述回填使用 `/internal/music-mv/v1/templates/{templateId}/descriptions/localize`，先 dry-run 保存备份，再携带 expectedRevision 执行；人工译稿另需 expectedSource。描述回填不得改标题、SEO、媒体、运行包或发布指针。新增、资料修改和发布均须完成十语言描述校验。
