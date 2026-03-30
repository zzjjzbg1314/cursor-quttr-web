#!/usr/bin/env python3

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from bootstrap_story_project import (
    ensure_agent_state,
    ensure_base_dirs,
    ensure_episode_dir,
    ensure_project_config,
    install_root_templates,
)


def slugify_name(name: str) -> str:
    cleaned = name.strip()
    cleaned = re.sub(r"^ep\d{2}[-_]*", "", cleaned, flags=re.IGNORECASE)
    cleaned = re.sub(r"\s+", "-", cleaned)
    cleaned = re.sub(r"[\\/]+", "-", cleaned)
    cleaned = re.sub(r"-{2,}", "-", cleaned)
    cleaned = cleaned.strip("-")
    return cleaned or "script"


def import_script(skill_dir: Path, target_dir: Path, source_path: Path, episode: str, name: str | None, force: bool) -> Path:
    ensure_base_dirs(target_dir)
    install_root_templates(skill_dir, target_dir, force)
    ensure_project_config(target_dir, force)
    ensure_agent_state(target_dir, episode, force)
    ensure_episode_dir(target_dir, episode)

    suffix = source_path.suffix.lower() or ".md"
    dest_name = name or source_path.stem
    dest_name = slugify_name(dest_name)
    destination = target_dir / "script" / f"{episode}-{dest_name}{suffix}"
    destination.parent.mkdir(parents=True, exist_ok=True)

    if destination.exists() and not force:
        print(f"跳过 {destination}")
        return destination

    shutil.copyfile(source_path, destination)
    print(f"导入 {destination}")
    return destination


def main() -> int:
    parser = argparse.ArgumentParser(description="把现有剧本文件导入 Codex 短剧流水线项目。")
    parser.add_argument("target_dir", help="目标项目目录")
    parser.add_argument("--episode", required=True, help="集数，例如 ep01")
    parser.add_argument("--source", required=True, help="现有剧本文件路径")
    parser.add_argument("--name", help="导入后的文件名后缀，不含集数前缀和扩展名")
    parser.add_argument("--force", action="store_true", help="覆盖已存在的目标文件")
    args = parser.parse_args()

    skill_dir = Path(__file__).resolve().parent.parent
    target_dir = Path(args.target_dir).resolve()
    source_path = Path(args.source).expanduser().resolve()

    if not source_path.exists():
        raise SystemExit(f"源文件不存在: {source_path}")
    if not source_path.is_file():
        raise SystemExit(f"源路径不是文件: {source_path}")

    target_dir.mkdir(parents=True, exist_ok=True)
    import_script(skill_dir, target_dir, source_path, args.episode, args.name, args.force)
    print(f"项目目录 {target_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
