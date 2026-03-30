#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


EPISODE_RE = re.compile(r"(ep\d{2})", re.IGNORECASE)


def detect_episode(name: str) -> str | None:
    match = EPISODE_RE.search(name)
    if not match:
        return None
    return match.group(1).lower()


def read_text(path: Path) -> str:
    if not path.exists():
        return ""
    return path.read_text(encoding="utf-8")


def has_design_content(episode: str, character_text: str, scene_text: str) -> bool:
    character_hit = re.search(
        rf"[\(（]\s*{re.escape(episode)}\s+(新增|变体)",
        character_text,
        re.IGNORECASE,
    )
    scene_hit = re.search(rf"##\s*{re.escape(episode)}\s+场景宫格", scene_text, re.IGNORECASE)
    return bool(character_hit or scene_hit)


def episode_status(root: Path, episode: str, character_text: str, scene_text: str) -> dict[str, str]:
    analysis_path = root / "outputs" / episode / "01-director-analysis.md"
    prompt_path = root / "outputs" / episode / "02-seedance-prompts.md"

    has_analysis = analysis_path.exists()
    has_prompts = prompt_path.exists()
    has_design = has_design_content(episode, character_text, scene_text)

    if has_prompts:
        return {"stage": "已完成", "summary": "已完成"}
    if has_analysis and has_design:
        return {"stage": "分镜编写阶段", "summary": "进行中"}
    if has_analysis:
        return {"stage": "服化道设计阶段", "summary": "进行中"}
    return {"stage": "导演分析阶段", "summary": "未开始"}


def load_agent_state(root: Path, current_episode: str | None) -> str:
    state_path = root / ".agent-state.json"
    if not state_path.exists():
        return "全新会话"
    try:
        state = json.loads(state_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return "全新会话"
    if not current_episode or state.get("episode") != current_episode:
        return "全新会话"
    if any(state.get(key) for key in ("director", "art-designer", "storyboard-artist")):
        return "已恢复"
    return "全新会话"


def render_markdown(payload: dict[str, object]) -> str:
    lines: list[str] = []
    lines.append("📊 **项目进度检测**")
    lines.append("")
    lines.append("**剧本文件**：")

    episodes: list[dict[str, str]] = payload["episodes"]  # type: ignore[assignment]
    if not episodes:
        lines.append("- 未发现剧本文件")
    else:
        for episode in episodes:
            lines.append(f"- {episode['file']} [{episode['summary']}]")

    lines.append("")
    lines.append(f"**当前集数**：{payload['current_episode'] or '无'}")
    lines.append(f"**当前阶段**：{payload['current_stage'] or '等待剧本'}")
    lines.append(f"**Agent 状态**：{payload['agent_state']}")
    lines.append(f"**下一步**：{payload['next_step']}")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="检测短剧提示词项目状态。")
    parser.add_argument("target_dir", help="项目根目录")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    args = parser.parse_args()

    root = Path(args.target_dir).resolve()
    script_dir = root / "script"
    character_text = read_text(root / "assets" / "character-prompts.md")
    scene_text = read_text(root / "assets" / "scene-prompts.md")

    payload: dict[str, object] = {
        "episodes": [],
        "current_episode": "",
        "current_stage": "",
        "agent_state": "全新会话",
        "next_step": "请先创建 script/ 并放入带 ep 编号的剧本文件",
    }

    script_files = sorted(script_dir.glob("*.md")) if script_dir.exists() else []
    discovered: list[tuple[str, Path]] = []
    for path in script_files:
        episode = detect_episode(path.name)
        if episode:
            discovered.append((episode, path))

    if not discovered:
        if args.json:
            print(json.dumps(payload, ensure_ascii=False, indent=2))
        else:
            print(render_markdown(payload))
        return 0

    episode_rows: list[dict[str, str]] = []
    first_incomplete: str | None = None
    first_stage: str | None = None

    for episode, path in discovered:
        status = episode_status(root, episode, character_text, scene_text)
        episode_rows.append(
            {
                "episode": episode,
                "file": path.name,
                "summary": status["summary"],
                "stage": status["stage"],
            }
        )
        if status["stage"] != "已完成" and first_incomplete is None:
            first_incomplete = episode
            first_stage = status["stage"]

    current_episode = first_incomplete or episode_rows[-1]["episode"]
    current_stage = first_stage or "已完成"
    agent_state = load_agent_state(root, current_episode)

    next_step_map = {
        "导演分析阶段": f"输入 ~start {current_episode}",
        "服化道设计阶段": f"输入 ~design {current_episode}",
        "分镜编写阶段": f"输入 ~prompt {current_episode}",
        "已完成": "当前所有已识别集数均已完成，可继续下一集或提出修改意见",
    }

    payload = {
        "episodes": episode_rows,
        "current_episode": current_episode,
        "current_stage": current_stage,
        "agent_state": agent_state,
        "next_step": next_step_map[current_stage],
    }

    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print(render_markdown(payload))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
