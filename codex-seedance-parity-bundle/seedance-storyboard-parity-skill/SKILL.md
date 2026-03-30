---
name: seedance-storyboard-parity-skill
description: 分镜师技能对齐版。用于基于导演讲戏本编写 Seedance 2.0 格式的动态视频提示词。
---

# Seedance 2.0 分镜编写技能

[技能说明]
    将导演的讲戏内容转化为 Seedance 2.0 格式的动态视频提示词。每个剧情点 = 一段完整叙事 = 一条提示词 = 一次 Seedance 2.0 生成任务。段落内可包含多组镜头。提示词中使用 @引用语法关联人物和场景素材，输出可直接复制到 Seedance 2.0 平台生成视频。

[文件结构]
    seedance-storyboard-parity-skill/
    ├── SKILL.md
    ├── references/
    │   └── seedance-prompt-methodology.md
    ├── examples/
    │   └── seedance-prompt-examples.md
    └── templates/
        └── seedance-prompts-template.md

[执行流程]

    第一步：读取上游产物
        - 读取导演讲戏本
        - 读取人物提示词
        - 读取场景道具提示词
        - 读取 references/seedance-prompt-methodology.md
        - 读取 examples/seedance-prompt-examples.md

    第二步：建立素材对应表
        - 根据人物和场景提示词文件，为本集涉及的素材分配 @引用编号
        - 只有在 character-prompts.md 和 scene-prompts.md 中有提示词的人物/场景才分配 @引用
        - 不在素材文件中的人物不进对应表，在提示词中直接用文字描述
        - 在文档头部建立对应表
        - 具体格式见 templates/seedance-prompts-template.md

        场景素材的编号规则（重要）：
        - scene-prompts.md 中的九宫格是生成格式
        - 生成后，用户会将九宫格中的每个格子单独提取出来，作为独立的场景参考图
        - 因此在素材对应表中，每个场景必须独立编号为一个 @图片，不能将整张九宫格作为一个 @图片
        - 编号顺序：先人物，再场景

        平台约束（单条提示词维度）：
        - 图片 ≤ 9 张 / 条
        - 视频 ≤ 3 个 / 条
        - 音频 ≤ 3 个 / 条
        - 总文件数 ≤ 12 个 / 条

    第三步：为每个剧情点编写 Seedance 2.0 提示词
        从导演阐述中提取并转化：
        - 运镜
        - 动作
        - 台词/声音
        - 光影
        - 节奏

    第四步：输出
        按照 templates/seedance-prompts-template.md 定义的格式输出。

[Seedance 2.0 提示词写法核心规则]

    叙事描述式
        - 用完整的导演式段落描述，不要关键词堆叠
        - Seedance 2.0 擅长理解长段叙事性提示词

    运镜描述
        - 用具体方向和动作描述镜头运动

    动作描述
        - 用具体的物理动作，避免抽象概念

    时长控制
        - 遵循导演建议的时长
        - 每个连续镜头内 1 拍 ≈ 2.5 秒屏幕时间
        - 多镜头段落：节拍分布在多个镜头切换中
        - 头尾安全区：每次 Seedance 生成前 0.5s 和后 0.5s 为安全区

    声音/台词
        - 台词用引号标注
        - 可指定语气/声音特征
        - 可指定背景音乐风格
        - 可指定环境音

[群演/一次性配角处理]
    - 不为这些人物分配 @引用编号
    - 在提示词中直接用文字描述其外观和动作
    - 外观描述从导演讲戏本的导演阐述中提取

[禁忌规则]
    - 不要使用否定句
    - 不要重复导演讲戏本和人物/场景提示词中已有的静态内容描述
    - 不要在一条提示词中塞入叙事上不相关的场景
    - 不要使用与导演讲戏本和人物/场景提示词矛盾的描述
    - 不要为不在 character-prompts.md 中的人物使用 @引用

[注意事项]
    - references/seedance-prompt-methodology.md 是核心方法论，必须遵守
    - 参考 examples/seedance-prompt-examples.md 了解官方推荐的提示词风格和结构
