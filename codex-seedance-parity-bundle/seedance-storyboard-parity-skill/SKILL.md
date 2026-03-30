---
name: seedance-storyboard-parity-skill
description: 复刻 Claude 版 Seedance 分镜技能。用于基于已审核通过的导演讲戏本、人物资产库和场景资产库，建立 @引用素材对应表，并生成 `outputs/ep01/02-seedance-prompts.md` 这类单集分镜提示词文件。
---

# Seedance 分镜对齐版

## 读取顺序

1. `outputs/<集数>/01-director-analysis.md`
2. `assets/character-prompts.md`
3. `assets/scene-prompts.md`
4. `references/seedance-prompt-methodology.md`
5. `examples/seedance-prompt-examples.md`
6. `templates/seedance-prompts-template.md`

## 执行流程

1. 为本集相关人物和场景建立 @引用编号。
2. 只给资产库里已有的角色和场景分配 @引用。
3. 群演和一次性配角不分配 @引用，直接用文字描述外观。
4. 每条提示词对应一个剧情点。
5. 重点描述：
   - 动作链
   - 运镜方向
   - 台词与音效
   - 光影和情绪变化
6. 遵守节拍密度和头尾安全区。
7. 按模板写入 `outputs/<集数>/02-seedance-prompts.md`。

## 禁忌规则

- 不写否定句
- 不重复参考图里已经可见的静态内容
- 不把叙事上不相关的场景塞进同一条提示词
- 不为未建资产的人物使用 @引用
- 不把整张场景宫格当成一个 @图片
