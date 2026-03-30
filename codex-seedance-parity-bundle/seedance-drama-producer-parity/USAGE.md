# Seedance Drama Producer Parity 使用说明

## 3 分钟上手

如果你现在只想最快跑起来，按下面做。

### 1. 初始化项目

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/bootstrap_story_project.py <你的项目目录> --episode ep01
```

例子：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/bootstrap_story_project.py /Users/zongjei/Documents/code/my-drama-project --episode ep01
```

### 2. 放剧本

把剧本放到：

```text
<你的项目目录>/script/ep01-xxx.md
```

如果你手里已经有一个现成剧本文件，直接导入：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/import_episode_script.py <你的项目目录> --episode ep01 --source /path/to/你的剧本.md
```

### 3. 看当前状态

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_status.py <你的项目目录>
```

如果你想先看主控会怎么路由命令：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/command_router.py route <你的项目目录> "~start ep01" --json
```

### 4. 打开 Codex 新会话

进入你的项目目录后，直接说：

```text
Use $seedance-drama-producer-parity to run the short-drama pipeline in this project.
```

如果你想先把项目配置写好，也可以直接执行：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_config.py set <你的项目目录> --visual-style "真人写实" --target-medium "短剧"
```

### 5. 按阶段执行

在会话里按这个顺序：

```text
~status
~start ep01
~design ep01
~prompt ep01
```

### 6. 最终会得到 4 个关键文件

- `assets/character-prompts.md`
- `assets/scene-prompts.md`
- `outputs/ep01/01-director-analysis.md`
- `outputs/ep01/02-seedance-prompts.md`

## 一句话理解

`~start` 出导演讲戏本，`~design` 出角色和场景参考图提示词，`~prompt` 出最终 Seedance 视频提示词。

## 这是什么

这是一套给 Codex 用的短剧/视频提示词生产流水线。

它复刻了你原来 Claude 版的三阶段流程：

1. 导演分析
2. 服化道设计
3. Seedance 分镜编写

每个阶段都带两道审核：

1. 业务审核
2. 合规审核

## 安装位置

已安装到：

- `/Users/zongjei/.codex/skills/seedance-drama-producer-parity`
- `/Users/zongjei/.codex/skills/director-parity-skill`
- `/Users/zongjei/.codex/skills/art-design-parity-skill`
- `/Users/zongjei/.codex/skills/seedance-storyboard-parity-skill`
- `/Users/zongjei/.codex/skills/script-analysis-review-parity-skill`
- `/Users/zongjei/.codex/skills/art-direction-review-parity-skill`
- `/Users/zongjei/.codex/skills/seedance-prompt-review-parity-skill`
- `/Users/zongjei/.codex/skills/compliance-review-parity-skill`

主 skill 是：

- `/Users/zongjei/.codex/skills/seedance-drama-producer-parity/SKILL.md`

## 适用目录结构

你的项目目录建议长这样：

```text
project/
├── script/
│   └── ep01-xxx.md
├── assets/
│   ├── character-prompts.md
│   └── scene-prompts.md
├── outputs/
│   └── ep01/
│       ├── 01-director-analysis.md
│       └── 02-seedance-prompts.md
└── .agent-state.json
```

## 第一次使用

先初始化项目：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/bootstrap_story_project.py <你的项目目录> --episode ep01
```

例子：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/bootstrap_story_project.py /Users/zongjei/Documents/code/my-drama-project --episode ep01
```

初始化后会创建：

- `assets/character-prompts.md`
- `assets/scene-prompts.md`
- `.agent-state.json`
- `outputs/ep01/`

然后把剧本放到：

- `script/ep01-xxx.md`

或者直接导入现有剧本：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/import_episode_script.py <你的项目目录> --episode ep01 --source /path/to/script.md
```

## 查看当前进度

运行：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_status.py <你的项目目录>
```

它会告诉你：

- 当前有哪些剧本
- 当前处理到哪一集
- 当前阶段是什么
- 下一步该输入什么命令

## 在 Codex 里怎么用

建议开一个新会话，进入你的项目目录，然后直接说：

```text
Use $seedance-drama-producer-parity to run the short-drama pipeline in this project.
```

或者直接使用这套命令语义：

- `~help`
- `~status`
- `~start ep01`
- `~design ep01`
- `~prompt ep01`

## 这几个命令分别做什么

### `~status`

查看项目进度。

适合：

- 刚进入项目时
- 不确定跑到哪一步时
- 多集项目切换时

### `~start ep01`

进入导演分析阶段。

会做这些事：

1. 读取 `script/ep01-xxx.md`
2. 生成 `outputs/ep01/01-director-analysis.md`
3. 跑导演分析业务审核
4. 跑合规审核

产出重点包括：

- 导演讲戏本
- 人物清单
- 场景清单
- 给服化道和分镜的交接说明

### `~design ep01`

进入服化道设计阶段。

前提：

- `outputs/ep01/01-director-analysis.md` 已经存在并通过审核

会做这些事：

1. 读取导演讲戏本
2. 只处理“新增”和“变体”人物/场景
3. 更新 `assets/character-prompts.md`
4. 更新 `assets/scene-prompts.md`
5. 跑服化道业务审核
6. 跑合规审核

### `~prompt ep01`

进入 Seedance 分镜阶段。

前提：

- 导演讲戏本已完成
- 人物素材库已完成
- 场景素材库已完成

会做这些事：

1. 建立素材映射表
2. 给人物和场景分配 `@图片` 引用
3. 生成 `outputs/ep01/02-seedance-prompts.md`
4. 跑 Seedance 提示词业务审核
5. 跑合规审核

## `.agent-state.json` 是干什么的

这个文件用来记录当前集的子代理状态。

结构类似：

```json
{
  "episode": "ep01",
  "director": "",
  "art-designer": "",
  "storyboard-artist": ""
}
```

如果你需要手动操作，可以用：

重置当前集状态：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/agent_state.py reset <你的项目目录> --episode ep01
```

写入一个 agent id：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/agent_state.py set <你的项目目录> --episode ep01 --agent director --id abc123
```

读取一个 agent id：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/agent_state.py get <你的项目目录> --episode ep01 --agent director
```

## 推荐的真实使用顺序

1. 初始化项目目录
2. 放入 `script/ep01-xxx.md` 或用导入脚本导入现有剧本
3. 运行 `project_status.py`
4. 在 Codex 里执行 `~start ep01`
5. 检查导演讲戏本
6. 执行 `~design ep01`
7. 生成参考图
8. 执行 `~prompt ep01`
9. 检查最终 `02-seedance-prompts.md`

## 现成样例

我已经给你做了一个完整样例项目：

- `/Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project`

你可以先看这些文件：

- `/Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project/script/ep01-demo.md`
- `/Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project/outputs/ep01/01-director-analysis.md`
- `/Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project/assets/character-prompts.md`
- `/Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project/assets/scene-prompts.md`
- `/Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project/outputs/ep01/02-seedance-prompts.md`

## 最短上手命令

如果你现在就要开始，直接用：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_status.py /Users/zongjei/Documents/code/cursor-quttr-web/seedance-parity-demo-project
```

如果你要把自己的现成剧本接进来，最短命令是：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/import_episode_script.py /Users/zongjei/Documents/code/my-drama-project --episode ep01 --source /path/to/你的剧本.md
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_status.py /Users/zongjei/Documents/code/my-drama-project
```

然后在 Codex 新会话里进入这个目录，输入：

```text
Use $seedance-drama-producer-parity to run the short-drama pipeline in this project.
```

## 常见问题

### 为什么 `~status` 一直显示“等待剧本”

通常是因为：

- 你的项目目录下还没有 `script/`
- 剧本文件名里没有 `ep01` 这种集数标记
- 剧本文件不是 `.md`

推荐文件名：

- `ep01-第一集.md`
- `ep02-第二集.md`

### 为什么还没进入 `~design`

因为 `outputs/ep01/01-director-analysis.md` 还没真正生成，或者还没通过这一阶段审核。

### 为什么还没进入 `~prompt`

因为资产库里还没有识别到本集新增内容。至少要有：

- `assets/character-prompts.md` 中出现 `（ep01 新增）` 或 `（ep01 变体）`
- 或 `assets/scene-prompts.md` 中出现 `## ep01 场景宫格`

### 如何快速验证整套目录是不是正常

运行：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_status.py <你的项目目录>
```

如果输出里出现：

- `当前集数：ep01`
- `当前阶段：导演分析阶段`

说明目录结构和剧本命名已经正常。

### 为什么 `~start` 先问我两个问题

这是对齐原版主控行为。导演分析开始前必须先确认：

- 视觉风格
- 目标媒介

如果你不想在会话里再答一次，可以提前写入：

```bash
python3 /Users/zongjei/.codex/skills/seedance-drama-producer-parity/scripts/project_config.py set <你的项目目录> --visual-style "真人写实" --target-medium "短剧"
```
