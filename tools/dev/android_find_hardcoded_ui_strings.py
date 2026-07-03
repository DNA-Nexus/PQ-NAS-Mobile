#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path


DEFAULT_ROOTS = [
    Path("app/src/main/java"),
]

# Android/Kotlin-rivejä, joissa näkyvä UI-teksti yleisimmin piileskelee.
UI_CONTEXT_RE = re.compile(
    r"""
    \bText\s*\(\s*"|
    \bTextFieldValue\s*\(\s*"|
    \bOutlinedTextField\b|
    \bBasicTextField\b|
    \bshowSnackbar\s*\(\s*"|
    \bcontentDescription\s*=\s*"|
    \bplaceholder\s*=\s*\{\s*Text\s*\(\s*"|
    \blabel\s*=\s*\{\s*Text\s*\(\s*"|
    \btitle\s*=\s*\{\s*Text\s*\(\s*"|
    \btext\s*=\s*"|
    \bstatus\s*=\s*"|
    \bStatus\s*=\s*"|
    \bmutableStateOf\s*\(\s*"|
    \bClipData\.newPlainText\s*\(\s*"|
    \.setTitle\s*\(\s*"|
    \.setSubtitle\s*\(\s*"|
    \.setNegativeButtonText\s*\(\s*"
    """,
    re.X,
)

STRING_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Teknisiä arvoja, jotka eivät ole käyttäjälle näkyvää käännöstekstiä.
# Tämä lista on tarkoituksella maltillinen: mieluummin vähän false positiveja
# kuin että oikea UI-teksti jää löytymättä.
ALLOW_EXACT = {
    "",
    "/",
    ".",
    "..",
    "admin",
    "contacts",
    "files",
    "server",
    "scan_pair",
    "pair_confirm",
    "Move",
    "Copy",
    "Upload",
    "Rename",
    "Delete",
    "Create text file",
    "phone_uploads",
    "current",
    "custom",
    "version",
    "keep_both",
    "reject",
    "dark",
    "bright",
    "cpunk_orange",
    "win_classic",
    "GET",
    "POST",
    "PUT",
    "DELETE",
    "PATCH",
    "Content-Type",
    "application/json",
    "application/octet-stream",
    "text/plain",
    "image/*",
    "audio/*",
    "video/*",
    "file",
    "folder",
    "workspace",
    "♪",
    "owner",
    "editor",
    "viewer",
}

ALLOW_PREFIXES = (
    "http://",
    "https://",
    "content://",
    "file://",
    "data:",
    "#",
    "Bearer ",
    "application/",
    "image/",
    "audio/",
    "video/",
    "text/",
    "android.",
    "com.",
    "org.",
)

ALLOW_LINE_PARTS = (
    "import ",
    "package ",
    "R.string.",
    "stringResource(",
    "context.getString(",
    ".getString(",
    "BuildConfig.",
    "Log.",
    "println(",
    "JSONObject",
    ".put(",
    ".optString(",
    ".getString(",
    "Json",
    "Retrofit",
)

KOTLIN_ESCAPES_RE = re.compile(r"\\[nrt\"']")


def strip_kotlin_templates(value: str) -> str:
    # Ignore strings that only render dynamic values/counters/errors.
    # Example: "${currentIndex + 1} / ${videoFiles.size}" -> " / "
    out = re.sub(r"\$\{[^}]*\}", "", value)
    out = re.sub(r"\$[A-Za-z_][A-Za-z0-9_]*", "", out)
    return out


def is_probably_technical(value: str, line: str) -> bool:
    v = value.strip()

    if v in ALLOW_EXACT:
        return True

    literal_part = strip_kotlin_templates(v).strip()
    if literal_part != v and re.fullmatch(r"[\s:/.,•×+\-()\[\]{}]*", literal_part):
        return True

    if any(v.startswith(prefix) for prefix in ALLOW_PREFIXES):
        return True

    # Numerot, prosentit, hex-värit ja hyvin lyhyet tekniset tokenit.
    if re.fullmatch(r"[0-9.,:%/\-+ ]+", v):
        return True

    if re.fullmatch(r"#[0-9a-fA-F]{3,8}", v):
        return True

    if re.fullmatch(r"[a-z0-9_./:-]{1,32}", v) and " " not in v:
        return True

    # Format-template ilman varsinaista tekstiä, esim. "%1$s • %2$s".
    if re.fullmatch(r"[%0-9$sdfoxX. •:,_/\-+()]+", v):
        return True

    # Jos rivillä on JSON/API-henkinen käyttö, se ei yleensä ole UI-teksti.
    if any(part in line for part in ALLOW_LINE_PARTS):
        return True

    # Polkumaiset arvot.
    if "/" in v and " " not in v:
        return True

    return False


def classify(line: str) -> str:
    if "Text(" in line or "text =" in line:
        return "TEXT"
    if "showSnackbar" in line:
        return "SNACKBAR"
    if "status" in line or "Status" in line:
        return "STATUS"
    if "contentDescription" in line:
        return "A11Y"
    if "mutableStateOf" in line:
        return "STATE_DEFAULT"
    if "ClipData.newPlainText" in line:
        return "CLIP_LABEL"
    if "setTitle" in line or "setSubtitle" in line or "setNegativeButtonText" in line:
        return "ANDROID_PROMPT"
    return "UI_LITERAL"


def scan_file(path: Path, include_all_strings: bool) -> list[tuple[int, str, str, str]]:
    findings = []
    lines = path.read_text(encoding="utf-8", errors="ignore").splitlines()

    for line_no, line in enumerate(lines, start=1):
        stripped = line.strip()

        if not include_all_strings and not UI_CONTEXT_RE.search(stripped):
            continue

        if any(part in stripped for part in ALLOW_LINE_PARTS):
            continue

        for m in STRING_RE.finditer(stripped):
            value = m.group(1)

            if KOTLIN_ESCAPES_RE.search(value):
                # Pidetään mukana, mutta siistitään tulostetta vähän.
                display = value.encode("utf-8").decode("unicode_escape")
            else:
                display = value

            if is_probably_technical(display, stripped):
                continue

            findings.append((line_no, classify(stripped), display, stripped))

    return findings


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Find likely hardcoded user-visible Kotlin UI strings in Android sources."
    )
    ap.add_argument(
        "--root",
        action="append",
        default=[],
        help="Root directory to scan. Defaults to app/src/main/java. Can be repeated.",
    )
    ap.add_argument(
        "--all-strings",
        action="store_true",
        help="Scan all string literals, not only UI-looking lines. More noisy.",
    )
    ap.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Limit output count.",
    )
    ap.add_argument(
        "--exclude-path",
        action="append",
        default=[],
        help="Skip files whose path contains this text. Can be repeated.",
    )
    ap.add_argument(
        "--strict",
        action="store_true",
        help="Return non-zero if findings are found.",
    )
    args = ap.parse_args()

    roots = [Path(p) for p in args.root] if args.root else DEFAULT_ROOTS

    all_findings = []

    for root in roots:
        if not root.exists():
            print(f"SKIP missing root: {root}")
            continue

        for path in sorted(root.rglob("*.kt")):
            # Generated/build folders should not be scanned.
            parts = set(path.parts)
            if "build" in parts or ".gradle" in parts:
                continue

            path_text = str(path)
            if any(excluded in path_text for excluded in args.exclude_path):
                continue

            for line_no, kind, value, raw in scan_file(path, args.all_strings):
                all_findings.append((path, line_no, kind, value, raw))

    if args.limit:
        all_findings = all_findings[:args.limit]

    for path, line_no, kind, value, raw in all_findings:
        print(f"{path}:{line_no}: [{kind}] {value}")
        print(f"    {raw}")

    if not all_findings:
        print("OK: no likely hardcoded Kotlin UI strings found")
        return 0

    print()
    print(f"Likely hardcoded UI strings found: {len(all_findings)}")
    return 1 if args.strict else 0


if __name__ == "__main__":
    raise SystemExit(main())
