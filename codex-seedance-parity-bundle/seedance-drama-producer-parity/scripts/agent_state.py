#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
from pathlib import Path


VALID_AGENTS = {"director", "art-designer", "storyboard-artist"}


def state_path(root: Path) -> Path:
    return root / ".agent-state.json"


def load_state(root: Path) -> dict[str, str]:
    path = state_path(root)
    if not path.exists():
        return {
            "episode": "",
            "director": "",
            "art-designer": "",
            "storyboard-artist": "",
        }
    return json.loads(path.read_text(encoding="utf-8"))


def save_state(root: Path, state: dict[str, str]) -> None:
    state_path(root).write_text(
        json.dumps(state, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def reset_state(root: Path, episode: str) -> dict[str, str]:
    state = {
        "episode": episode,
        "director": "",
        "art-designer": "",
        "storyboard-artist": "",
    }
    save_state(root, state)
    return state


def main() -> int:
    parser = argparse.ArgumentParser(description="维护短剧流水线的 .agent-state.json。")
    subparsers = parser.add_subparsers(dest="command", required=True)

    reset_parser = subparsers.add_parser("reset")
    reset_parser.add_argument("target_dir")
    reset_parser.add_argument("--episode", required=True)

    set_parser = subparsers.add_parser("set")
    set_parser.add_argument("target_dir")
    set_parser.add_argument("--episode", required=True)
    set_parser.add_argument("--agent", required=True, choices=sorted(VALID_AGENTS))
    set_parser.add_argument("--id", required=True)

    get_parser = subparsers.add_parser("get")
    get_parser.add_argument("target_dir")
    get_parser.add_argument("--episode", required=True)
    get_parser.add_argument("--agent", required=True, choices=sorted(VALID_AGENTS))

    show_parser = subparsers.add_parser("show")
    show_parser.add_argument("target_dir")

    args = parser.parse_args()

    if args.command == "reset":
        state = reset_state(Path(args.target_dir).resolve(), args.episode)
        print(json.dumps(state, ensure_ascii=False, indent=2))
        return 0

    root = Path(args.target_dir).resolve()
    state = load_state(root)

    if args.command == "set":
        if state.get("episode") != args.episode:
            state = reset_state(root, args.episode)
        state[args.agent] = args.id
        save_state(root, state)
        print(json.dumps(state, ensure_ascii=False, indent=2))
        return 0

    if args.command == "get":
        if state.get("episode") != args.episode:
            print("")
            return 0
        print(state.get(args.agent, ""))
        return 0

    print(json.dumps(state, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
