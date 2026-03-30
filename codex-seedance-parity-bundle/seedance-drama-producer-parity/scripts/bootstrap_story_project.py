#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
from pathlib import Path


ROOT_TEMPLATES = {
    Path("assets/character-prompts.md"): Path("assets/templates/assets/character-prompts.md"),
    Path("assets/scene-prompts.md"): Path("assets/templates/assets/scene-prompts.md"),
}

EPISODE_TEMPLATES = {
    "01-director-analysis.md": Path("assets/templates/outputs/01-director-analysis.md"),
    "02-seedance-prompts.md": Path("assets/templates/outputs/02-seedance-prompts.md"),
}


def write_text(path: Path, text: str, force: bool) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and not force:
        print(f"跳过 {path}")
        return
    path.write_text(text, encoding="utf-8")
    print(f"写入 {path}")


def load_template(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def install_root_templates(skill_dir: Path, target_dir: Path, force: bool) -> None:
    for destination, template in ROOT_TEMPLATES.items():
        write_text(
            target_dir / destination,
            load_template(skill_dir / template),
            force,
        )


def install_episode_templates(skill_dir: Path, target_dir: Path, episode: str, force: bool) -> None:
    for filename, template in EPISODE_TEMPLATES.items():
        content = load_template(skill_dir / template).replace("__EPISODE__", episode)
        write_text(target_dir / "outputs" / episode / filename, content, force)


def ensure_episode_dir(target_dir: Path, episode: str) -> None:
    episode_dir = target_dir / "outputs" / episode
    episode_dir.mkdir(parents=True, exist_ok=True)
    print(f"确认目录 {episode_dir}")


def ensure_base_dirs(target_dir: Path) -> None:
    for directory in ("script", "assets", "outputs"):
        (target_dir / directory).mkdir(parents=True, exist_ok=True)


def ensure_project_config(target_dir: Path, force: bool) -> None:
    config_path = target_dir / ".producer-config.json"
    default_config = {
        "visual_style": "",
        "target_medium": "",
        "language": "中文",
        "last_episode": "",
    }
    if config_path.exists() and not force:
        print(f"跳过 {config_path}")
        return
    config_path.write_text(
        json.dumps(default_config, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"写入 {config_path}")


def ensure_agent_state(target_dir: Path, episode: str | None, force: bool) -> None:
    state_path = target_dir / ".agent-state.json"
    default_state = {
        "episode": episode or "",
        "director": "",
        "art-designer": "",
        "storyboard-artist": "",
    }
    if state_path.exists() and not force:
        print(f"跳过 {state_path}")
        return
    state_path.write_text(
        json.dumps(default_state, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"写入 {state_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="初始化 Codex 版短剧提示词生产项目结构。")
    parser.add_argument("target_dir", help="目标工作目录")
    parser.add_argument(
        "--episode",
        action="append",
        default=[],
        help="可重复传入，用于预创建某集目录并写入当前 episode 状态，例如 --episode ep01",
    )
    parser.add_argument(
        "--materialize-output-templates",
        action="store_true",
        help="显式把输出模板写入 outputs/epXX/；默认只创建目录，避免误判项目进度。",
    )
    parser.add_argument(
        "--force",
        action="store_true",
        help="覆盖已存在文件",
    )
    args = parser.parse_args()

    skill_dir = Path(__file__).resolve().parent.parent
    target_dir = Path(args.target_dir).resolve()
    target_dir.mkdir(parents=True, exist_ok=True)

    ensure_base_dirs(target_dir)
    install_root_templates(skill_dir, target_dir, args.force)
    ensure_project_config(target_dir, args.force)
    ensure_agent_state(target_dir, args.episode[0] if args.episode else None, args.force)

    for episode in args.episode:
        ensure_episode_dir(target_dir, episode)
        if args.materialize_output_templates:
            install_episode_templates(skill_dir, target_dir, episode, args.force)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
