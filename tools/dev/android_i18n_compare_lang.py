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
    placeholder_signature,
    strings_path,
    wanted,
)


def main() -> int:
    ap = argparse.ArgumentParser(description="Compare one Android values-* strings.xml file against base values/strings.xml.")
    ap.add_argument("--res-dir", default=str(DEFAULT_RES_DIR))
    ap.add_argument("--base-dir", default=DEFAULT_BASE_DIR)
    ap.add_argument("--lang-dir", default=DEFAULT_LANG_DIR)
    ap.add_argument("--prefix", action="append", default=[])
    ap.add_argument("--show", choices=["summary", "same", "missing", "extra", "different", "placeholders", "all"], default="summary")
    ap.add_argument("--format", choices=["lines", "json"], default="lines")
    ap.add_argument("--limit", type=int, default=0)
    args = ap.parse_args()

    res_dir = Path(args.res_dir)
    base_path = strings_path(res_dir, args.base_dir)
    lang_path = strings_path(res_dir, args.lang_dir)

    base, _ = load_strings(base_path)
    lang, _ = load_strings(lang_path)

    prefixes = args.prefix or []
    base_keys = [k for k in base if wanted(k, prefixes)]
    lang_keys = [k for k in lang if wanted(k, prefixes)]

    base_keyset = set(base_keys)
    lang_keyset = set(lang_keys)

    missing = [k for k in base_keys if k not in lang_keyset]
    extra = [k for k in lang_keys if k not in base_keyset]
    common = [k for k in base_keys if k in lang_keyset]
    same = [k for k in common if lang[k].value == base[k].value]
    different = [k for k in common if lang[k].value != base[k].value]
    placeholders = [
        k for k in common
        if placeholder_signature(base[k].value) != placeholder_signature(lang[k].value)
    ]

    if args.show == "summary":
        print(f"compare: {base_path} -> {lang_path}")
        print(f"prefixes: {prefixes or ['<all>']}")
        print(f"base keys:     {len(base_keys)}")
        print(f"target keys:   {len(lang_keys)}")
        print(f"common:        {len(common)}")
        print(f"missing:       {len(missing)}")
        print(f"extra:         {len(extra)}")
        print(f"same_as_base:  {len(same)}")
        print(f"different:     {len(different)}")
        print(f"placeholders:  {len(placeholders)}")
        return 0

    groups = {
        "same": same,
        "missing": missing,
        "extra": extra,
        "different": different,
        "placeholders": placeholders,
    }

    if args.show == "all":
        selected = []
        for label in ["missing", "extra", "same", "different", "placeholders"]:
            selected.extend((label, k) for k in groups[label])
    else:
        selected = [(args.show, k) for k in groups[args.show]]

    if args.limit:
        selected = selected[:args.limit]

    if args.format == "json":
        out = {}
        for label, k in selected:
            if label == "missing":
                out[k] = {"base": base[k].value, "target": None}
            elif label == "extra":
                out[k] = {"base": None, "target": lang[k].value}
            else:
                out[k] = {"base": base.get(k).value if k in base else None, "target": lang.get(k).value if k in lang else None}
        print(json.dumps(out, ensure_ascii=False, indent=2))
        return 0

    for label, k in selected:
        marker = label.upper()
        if label == "missing":
            print(format_location(lang_path, None, marker, f"{k} = {base[k].value}"))
        elif label == "extra":
            print(format_location(lang_path, lang[k], marker, lang[k].raw_line))
        elif label == "placeholders":
            print(format_location(lang_path, lang[k], marker, f"{k} base={placeholder_signature(base[k].value)} target={placeholder_signature(lang[k].value)}"))
        else:
            print(format_location(lang_path, lang[k], marker, f"{k} = {lang[k].value}"))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
