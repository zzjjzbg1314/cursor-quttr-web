---
name: compliance-review-parity-skill
description: 作为每个阶段的第二道审核，检查 Claude 对齐版导演讲戏本、服化道提示词和 Seedance 提示词的合规风险。用于识别真人肖像、版权 IP、政治敏感、色情低俗、过度血腥暴力和平台误杀高风险内容，并给出 `pass`、`revise` 或 `block`。
---

# 合规审核对齐版

## 审核方式

1. 先读 `references/checklist.md`
2. 审核当前阶段产物
3. 结论只分为：
   - `pass`
   - `revise`
   - `block`
4. 只引用最小必要原文
5. 如果可改，给出安全修改方向

## 输出格式

- `pass`：说明未触碰平台红线
- `revise`：给出问题位置、风险点和修改建议
- `block`：说明触碰绝对红线，不能继续下游
