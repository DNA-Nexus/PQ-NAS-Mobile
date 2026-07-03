#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from android_i18n_lib import (
    DEFAULT_BASE_DIR,
    DEFAULT_LANG_DIR,
    DEFAULT_RES_DIR,
    format_location,
    load_strings,
    strings_path,
    wanted,
)


def main() -> int:
    ap = argparse.ArgumentParser(description="Dump Android strings for review, especially target strings still identical to base.")
    ap.add_argument("--res-dir", default=str(DEFAULT_RES_DIR))
    ap.add_argument("--base-dir", default=DEFAULT_BASE_DIR)
    ap.add_argument("--lang-dir", default=DEFAULT_LANG_DIR)
    ap.add_argument("--prefix", action="append", default=[])
    ap.add_argument("--from-line", type=int, default=0)
    ap.add_argument("--to-line", type=int, default=0)
    ap.add_argument("--mode", choices=["same", "all", "different"], default="same")
    ap.add_argument("--format", choices=["lines", "json", "md", "kv"], default="lines")
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    res_dir = Path(args.res_dir)
    base_path = strings_path(res_dir, args.base_dir)
    lang_path = strings_path(res_dir, args.lang_dir)

    base, _ = load_strings(base_path)
    lang, _ = load_strings(lang_path)

    items = []
    for key, item in lang.items():
        if not wanted(key, args.prefix or []):
            continue

        if args.from_line and item.line_no < args.from_line:
            continue
        if args.to_line and item.line_no > args.to_line:
            continue

        exists_in_base = key in base
        same = exists_in_base and item.value == base[key].value

        if args.mode == "same" and not same:
            continue
        if args.mode == "different" and same:
            continue

        items.append((item.line_no, key, item, base.get(key), same, exists_in_base))

    items.sort(key=lambda x: (x[0], x[1]))

    if args.limit:
        items = items[:args.limit]

    if args.format == "json":
        print(json.dumps({key: item.value for _, key, item, _, _, _ in items}, ensure_ascii=False, indent=2))
        return 0

    if args.format == "kv":
        for _, key, item, _, same, exists_in_base in items:
            status = "NO_BASE_KEY" if not exists_in_base else ("SAME_AS_BASE" if same else "DIFF_FROM_BASE")
            print(f"{key} = {item.value}    # {status}")
        return 0

    if args.format == "md":
        print(f"# {args.lang_dir}: Android i18n review")
        print()
        for _, key, item, base_item, same, exists_in_base in items:
            status = "NO_BASE_KEY" if not exists_in_base else ("SAME_AS_BASE" if same else "DIFF_FROM_BASE")
            print(f"## {key}")
            print(f"Line: {item.line_no}")
            print(f"Status: {status}")
            if base_item is not None:
                print()
                print("Base:")
                print(base_item.value)
            print()
            print("Target:")
            print(item.value)
            print()
        return 0

    for _, key, item, _, same, exists_in_base in items:
        status = "NO_BASE_KEY" if not exists_in_base else ("SAME_AS_BASE" if same else "DIFF_FROM_BASE")
        print(format_location(lang_path, item, status, item.raw_line))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
