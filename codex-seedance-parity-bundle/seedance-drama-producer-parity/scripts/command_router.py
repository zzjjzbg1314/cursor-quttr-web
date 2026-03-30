#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

from project_config import load_config
from project_status import build_payload, render_markdown


HELP_TEXT = """可用指令：
- ~start [集数]：执行导演分析阶段，例如 ~start ep01
- ~design [集数]：执行服化道设计阶段，例如 ~design ep01
- ~prompt [集数]：执行分镜编写阶段，例如 ~prompt ep01
- ~status：显示当前项目进度
- ~help：显示所有可用指令和使用说明

说明：
- 集数参数可选，格式如 ep01、ep02
- 如果 script/ 中只有一个文件，可省略集数参数
- 如果有多个文件且未指定集数，应先明确目标集数
"""

FEICAI_ASCII = """███████╗███████╗██╗ ██████╗ █████╗ ██╗
██╔════╝██╔════╝██║██╔════╝██╔══██╗██║
█████╗  █████╗  ██║██║     ███████║██║
██╔══╝  ██╔══╝  ██║██║     ██╔══██║██║
██║     ███████╗██║╚██████╗██║  ██║██║
╚═╝     ╚══════╝╚═╝ ╚═════╝╚═╝  ╚═╝╚═╝"""


def parse_episode(command_text: str) -> str | None:
    match = re.search(r"(ep\d{2})", command_text, re.IGNORECASE)
    if not match:
        return None
    return match.group(1).lower()


def normalize_command(command_text: str) -> str:
    text = command_text.strip()
    if text.startswith("~"):
        text = text[1:]
    return text.strip().lower()


def infer_episode(payload: dict[str, object], requested: str | None) -> str | None:
    if requested:
        return requested
    episodes = payload.get("episodes", [])
    if isinstance(episodes, list) and len(episodes) == 1:
        episode = episodes[0]
        if isinstance(episode, dict):
            return str(episode.get("episode", ""))
    current = str(payload.get("current_episode", ""))
    return current or None


def route(root: Path, command_text: str) -> dict[str, object]:
    payload = build_payload(root)
    config = load_config(root)
    normalized = normalize_command(command_text)
    requested_episode = parse_episode(command_text)
    episode = infer_episode(payload, requested_episode)

    result: dict[str, object] = {
        "command": command_text,
        "normalized": normalized,
        "episode": episode or "",
        "status": "ok",
        "message": "",
        "action": "",
        "payload": payload,
    }

    if normalized.startswith("help"):
        result["action"] = "help"
        result["message"] = HELP_TEXT
        return result

    if normalized.startswith("status"):
        result["action"] = "status"
        result["message"] = render_markdown(payload)
        return result

    if normalized == "继续":
        episodes = payload.get("episodes", [])
        current = str(payload.get("current_episode", ""))
        next_episode = ""
        if isinstance(episodes, list):
            seen_current = False
            for item in episodes:
                if not isinstance(item, dict):
                    continue
                ep = str(item.get("episode", ""))
                stage = str(item.get("stage", ""))
                if ep == current:
                    seen_current = True
                    continue
                if seen_current and stage != "已完成":
                    next_episode = ep
                    break
        if next_episode:
            result["action"] = "start-next"
            result["episode"] = next_episode
            result["message"] = f"📺 **{current} 已完成，是否进入 {next_episode}？**\n\n输入 **~start {next_episode}** 开始下一集。"
        else:
            result["status"] = "blocked"
            result["message"] = "没有找到下一集未完成剧本。"
        return result

    if normalized.startswith("start"):
        if not episode:
            result["status"] = "blocked"
            result["message"] = "未识别到目标集数。请使用 `~start ep01` 这类形式，或确保项目里只有一个剧本文件。"
            return result
        if not config.get("visual_style") or not config.get("target_medium"):
            result["action"] = "collect-config"
            result["message"] = (
                "**在开始之前，请先告诉我一些基本信息：**\n\n"
                "**Q1：视觉风格**\n"
                "可以从预设中选择，也可以用自己的文字描述：\n\n"
                "预设选项：真人写实 | 3D CG | 皮克斯 | 迪士尼 | 国漫 | 日漫 | 韩漫\n\n"
                "**Q2：目标媒介**\n"
                "电影 | 短剧 | 漫剧 | MV | 广告"
            )
            return result
        result["action"] = "director-analysis"
        result["message"] = f"进入 {episode} 的导演分析阶段。"
        return result

    if normalized.startswith("design"):
        if not episode:
            result["status"] = "blocked"
            result["message"] = "未识别到目标集数。请使用 `~design ep01`。"
            return result
        analysis_path = root / "outputs" / episode / "01-director-analysis.md"
        if not analysis_path.exists():
            result["status"] = "blocked"
            result["message"] = f"⚠️ 请先完成该集的导演分析。\n\n输入 **~start {episode}** 开始分析。"
            return result
        result["action"] = "art-design"
        result["message"] = f"进入 {episode} 的服化道设计阶段。"
        return result

    if normalized.startswith("prompt"):
        if not episode:
            result["status"] = "blocked"
            result["message"] = "未识别到目标集数。请使用 `~prompt ep01`。"
            return result
        required = [
            root / "outputs" / episode / "01-director-analysis.md",
            root / "assets" / "character-prompts.md",
            root / "assets" / "scene-prompts.md",
        ]
        missing = [path.name for path in required if not path.exists()]
        if missing:
            result["status"] = "blocked"
            result["message"] = f"缺少前置文件：{', '.join(missing)}。请先完成对应阶段。"
            return result
        result["action"] = "storyboard"
        result["message"] = f"进入 {episode} 的分镜编写阶段。"
        return result

    result["status"] = "blocked"
    result["message"] = "未识别命令。请输入 `~help` 查看可用指令。"
    return result


def render_welcome(root: Path) -> str:
    payload = build_payload(root)
    return (
        f"{FEICAI_ASCII}\n\n"
        "👋 你好！我是废才，一名专业的 AI 电影制片人。\n\n"
        "我将协调导演、服化道和分镜师，帮你从剧本出发，生成可直接用于 Seedance 2.0 的视频提示词。\n\n"
        "**工作流程**：\n"
        "1. 导演分析剧本、拆解剧情、讲戏\n"
        "2. 服化道设计角色与场景的参考图提示词\n"
        "3. 分镜师编写 Seedance 2.0 视频提示词\n\n"
        "💡 **提示**：输入 **~help** 查看所有可用指令\n\n"
        "让我们开始吧！\n\n"
        f"{render_markdown(payload)}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="短剧制片人命令路由器。")
    subparsers = parser.add_subparsers(dest="command", required=True)

    help_parser = subparsers.add_parser("help")
    help_parser.add_argument("target_dir", nargs="?")

    status_parser = subparsers.add_parser("status")
    status_parser.add_argument("target_dir")

    welcome_parser = subparsers.add_parser("welcome")
    welcome_parser.add_argument("target_dir")

    route_parser = subparsers.add_parser("route")
    route_parser.add_argument("target_dir")
    route_parser.add_argument("command_text")
    route_parser.add_argument("--json", action="store_true")

    args = parser.parse_args()

    if args.command == "help":
        print(HELP_TEXT)
        return 0

    root = Path(args.target_dir).resolve()
    if args.command == "status":
        print(json.dumps(build_payload(root), ensure_ascii=False, indent=2))
        return 0

    if args.command == "welcome":
        print(render_welcome(root))
        return 0

    result = route(root, args.command_text)
    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(result["message"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
