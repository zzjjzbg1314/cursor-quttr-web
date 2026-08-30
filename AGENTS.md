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
