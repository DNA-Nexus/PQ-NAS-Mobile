#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from android_i18n_lib import (
    DEFAULT_ALLOWED_SAME_AS_BASE,
    DEFAULT_BASE_DIR,
    DEFAULT_LANG_DIR,
    DEFAULT_RES_DIR,
    die,
    format_location,
    load_allow_file,
    load_strings,
    placeholder_signature,
    strings_path,
    wanted,
)


def main() -> int:
    ap = argparse.ArgumentParser(description="Audit Android strings.xml translations.")
    ap.add_argument("--res-dir", default=str(DEFAULT_RES_DIR))
    ap.add_argument("--base-dir", default=DEFAULT_BASE_DIR)
    ap.add_argument("--lang-dir", default=DEFAULT_LANG_DIR)
    ap.add_argument("--prefix", action="append", default=[], help="String name prefix to check; can be repeated.")
    ap.add_argument("--allow-same", action="append", default=[], help="String key allowed to equal base; can be repeated.")
    ap.add_argument("--allow-file", default="", help="Optional newline-separated allow-same key file.")
    ap.add_argument("--strict", action="store_true", help="Return non-zero if problems are found.")
    args = ap.parse_args()

    res_dir = Path(args.res_dir)
    base_path = strings_path(res_dir, args.base_dir)
    lang_path = strings_path(res_dir, args.lang_dir)

    base, base_dupes = load_strings(base_path)
    lang, lang_dupes = load_strings(lang_path)

    allow = set(DEFAULT_ALLOWED_SAME_AS_BASE)
    allow.update(args.allow_same or [])
    allow.update(load_allow_file(Path(args.allow_file)) if args.allow_file else set())

    prefixes = args.prefix or []
    base_keys = [k for k in base if wanted(k, prefixes)]
    base_keyset = set(base_keys)
    lang_keyset = {k for k in lang if wanted(k, prefixes)}

    problems = 0

    if base_dupes:
        print(f"\n[{args.base_dir}] duplicate string names:")
        for k in sorted(base_dupes):
            print(f"  DUPLICATE {k}")
        problems += len(base_dupes)

    if lang_dupes:
        print(f"\n[{args.lang_dir}] duplicate string names:")
        for k in sorted(lang_dupes):
            print(f"  DUPLICATE {k}")
        problems += len(lang_dupes)

    missing = [k for k in base_keys if k not in lang_keyset]
    extra = sorted(k for k in lang_keyset if k not in base_keyset)

    if missing:
        print(f"\n[{args.lang_dir}] Missing translations:")
        for k in missing:
            print(format_location(lang_path, None, f"MISSING_IN_{args.lang_dir.upper().replace('-', '_')}", f"{k} = {base[k].value}"))
        problems += len(missing)

    if extra:
        print(f"\n[{args.lang_dir}] Extra translations not in base:")
        for k in extra:
            print(format_location(lang_path, lang[k], f"EXTRA_IN_{args.lang_dir.upper().replace('-', '_')}", lang[k].raw_line))
        problems += len(extra)

    suspicious = []
    placeholder_mismatch = []

    for k in base_keys:
        if k not in lang:
            continue

        b = base[k]
        t = lang[k]

        if "??" in t.value or t.value.strip() == "?":
            suspicious.append((k, "question marks", t))

        if t.value == b.value and k not in allow:
            suspicious.append((k, "same as base", t))

        bp = placeholder_signature(b.value)
        tp = placeholder_signature(t.value)
        if bp != tp:
            placeholder_mismatch.append((k, bp, tp, t))

    if suspicious:
        print(f"\n[{args.lang_dir}] Suspicious translations:")
        for k, reason, item in suspicious:
            print(format_location(lang_path, item, "SUSPICIOUS", f"{k} = {item.value}  # {reason}"))
        problems += len(suspicious)

    if placeholder_mismatch:
        print(f"\n[{args.lang_dir}] Placeholder mismatches:")
        for k, bp, tp, item in placeholder_mismatch:
            print(format_location(lang_path, item, "PLACEHOLDER_MISMATCH", f"{k} base={bp} target={tp}"))
        problems += len(placeholder_mismatch)

    if problems:
        print(f"\nProblems found: {problems}")
        return 1 if args.strict else 0

    print("OK: no missing/suspicious Android string keys found")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
