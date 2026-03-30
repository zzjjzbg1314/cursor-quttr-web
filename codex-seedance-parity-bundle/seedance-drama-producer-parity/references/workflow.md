# 工作流

## 阶段顺序

1. 导演分析
2. 服化道设计
3. 分镜编写

## 阶段一：导演分析

- 输入：`script/<集数>-*.md`
- 技能：`$director-parity-skill`
- 产出：`outputs/<集数>/01-director-analysis.md`
- 审核：
  - `$script-analysis-review-parity-skill`
  - `$compliance-review-parity-skill`

## 阶段二：服化道设计

- 输入：通过审核的导演讲戏本
- 技能：`$art-design-parity-skill`
- 产出：
  - `assets/character-prompts.md`
  - `assets/scene-prompts.md`
- 审核：
  - `$art-direction-review-parity-skill`
  - `$compliance-review-parity-skill`

## 阶段三：分镜编写

- 输入：
  - 通过审核的导演讲戏本
  - 通过审核的人物素材库
  - 通过审核的场景素材库
- 技能：`$seedance-storyboard-parity-skill`
- 产出：`outputs/<集数>/02-seedance-prompts.md`
- 审核：
  - `$seedance-prompt-review-parity-skill`
  - `$compliance-review-parity-skill`

## 双审核规则

- 专业审核先于合规审核
- 任一审核失败，都回到当前阶段修改
- 不允许跳过审核直接流入下游

## 跨集规则

- `assets/` 是跨集累计库
- `outputs/` 按集数分目录
- 新集开始前重置 `.agent-state.json`
