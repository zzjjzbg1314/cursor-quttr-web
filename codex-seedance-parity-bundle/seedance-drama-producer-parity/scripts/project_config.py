#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
from pathlib import Path


DEFAULT_CONFIG = {
    "visual_style": "",
    "target_medium": "",
    "language": "中文",
    "last_episode": "",
}


def config_path(root: Path) -> Path:
    return root / ".producer-config.json"


def load_config(root: Path) -> dict[str, str]:
    path = config_path(root)
    if not path.exists():
        return dict(DEFAULT_CONFIG)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return dict(DEFAULT_CONFIG)
    merged = dict(DEFAULT_CONFIG)
    for key in DEFAULT_CONFIG:
        value = data.get(key, DEFAULT_CONFIG[key])
        merged[key] = value if isinstance(value, str) else str(value)
    return merged


def save_config(root: Path, config: dict[str, str]) -> None:
    config_path(root).write_text(
        json.dumps(config, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="读写短剧制片人项目配置。")
    subparsers = parser.add_subparsers(dest="command", required=True)

    init_parser = subparsers.add_parser("init")
    init_parser.add_argument("target_dir")
    init_parser.add_argument("--force", action="store_true")

    show_parser = subparsers.add_parser("show")
    show_parser.add_argument("target_dir")

    set_parser = subparsers.add_parser("set")
    set_parser.add_argument("target_dir")
    set_parser.add_argument("--visual-style")
    set_parser.add_argument("--target-medium")
    set_parser.add_argument("--language")
    set_parser.add_argument("--last-episode")

    args = parser.parse_args()
    root = Path(args.target_dir).resolve()
    root.mkdir(parents=True, exist_ok=True)

    if args.command == "init":
        path = config_path(root)
        if path.exists() and not args.force:
            print(f"跳过 {path}")
            return 0
        save_config(root, dict(DEFAULT_CONFIG))
        print(f"写入 {path}")
        return 0

    config = load_config(root)

    if args.command == "show":
        print(json.dumps(config, ensure_ascii=False, indent=2))
        return 0

    if args.visual_style is not None:
        config["visual_style"] = args.visual_style
    if args.target_medium is not None:
        config["target_medium"] = args.target_medium
    if args.language is not None:
        config["language"] = args.language
    if args.last_episode is not None:
        config["last_episode"] = args.last_episode
    save_config(root, config)
    print(json.dumps(config, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
