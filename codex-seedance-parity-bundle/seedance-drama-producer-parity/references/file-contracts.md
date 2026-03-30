# 文件契约

## script/

- 源剧本文件放在这里
- 推荐文件名：`ep01-xxx.md`
- 单集最多一个主输入文件

## .agent-state.json

保持以下结构：

```json
{
  "episode": "ep01",
  "director": "",
  "art-designer": "",
  "storyboard-artist": ""
}
```

## .producer-config.json

保持以下结构：

```json
{
  "visual_style": "",
  "target_medium": "",
  "language": "中文",
  "last_episode": ""
}
```

开始 `~start` 之前，至少要补齐：

- `visual_style`
- `target_medium`

## outputs/<集数>/01-director-analysis.md

至少包含：

- 集数和项目信息
- 导演讲戏本
- 人物清单
- 场景清单
- 给服化道的交接
- 给分镜的交接
- 风险与未决问题

## assets/character-prompts.md

至少包含：

- 人物条目标题
- 创建集数和状态
- 出图要求
- 正向提示词
- 与原版差异说明（如为变体）

## assets/scene-prompts.md

至少包含：

- 集数宫格标题
- 宫格规格
- 每格对应场景
- 视觉规范
- 每格重点元素

## outputs/<集数>/02-seedance-prompts.md

至少包含：

- 素材对应表
- 每个剧情点的提示词小节
- @引用与素材对应关系
- 连续性说明
- 审核状态
