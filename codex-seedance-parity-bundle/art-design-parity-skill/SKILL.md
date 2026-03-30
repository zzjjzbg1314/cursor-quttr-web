---
name: art-design-parity-skill
description: 复刻 Claude 版服化道设计技能。用于基于已审核通过的导演讲戏本，只为“新增”和“变体”人物/场景生成叙事描述式参考图提示词，更新 `assets/character-prompts.md` 与 `assets/scene-prompts.md`。
---

# 服化道设计对齐版

## 读取顺序

1. 已审核通过的 `outputs/<集数>/01-director-analysis.md`
2. `references/gemini-image-prompt-guide.md`
3. `examples/character-prompt-examples.md`
4. `examples/scene-prompt-examples.md`
5. `templates/art-design-template.md`
6. 现有资产库

## 执行流程

1. 只处理导演清单中标为“新增”或“变体”的条目。
2. 人物提示词必须明确：
   - 左半边面部特写
   - 右半边全身正面、侧面、背面三视图
   - 白色背景
3. 每个人物提示词都要写清：
   - 年龄、性别、族裔/地域特征
   - 五官、肤色、发型、体态
   - 服装款式、颜色、材质
   - 配饰、鞋子、整体气质
4. 场景提示词按宫格组织：
   - <= 9 个场景：3x3
   - 10-12 个场景：3x4
   - 13-16 个场景：4x4
5. 每个场景格子写清：
   - 地点类型与布局
   - 时间与光线方向
   - 色调和氛围
   - 关键家具、道具和视觉锚点
6. 按模板更新两个资产库。

## 写法原则

- 用完整叙事段落，不要关键词堆叠
- 所有描述都要可视化、可执行
- 情绪通过材质、光影、空间和细节传达
- 变体设计要说明与原版的关联和差异
