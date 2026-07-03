#!/usr/bin/env python3
from __future__ import annotations

from dataclasses import dataclass
from html import unescape
from pathlib import Path
import re
import sys


DEFAULT_RES_DIR = Path("app/src/main/res")
DEFAULT_BASE_DIR = "values"
DEFAULT_LANG_DIR = "values-fi"

# These may intentionally stay identical in Finnish because they are product,
# module, theme, or protocol names rather than normal UI prose.
DEFAULT_ALLOWED_SAME_AS_BASE = {
    "app_name",
    "app_display_name",
    "share_manager",
    "contacts",
    "drop_zone",
    "echo_stack",
    "circle_stack",
    "theme_cpunk_orange_label",
    "theme_win_classic_label",
    "language_english_label",
    "language_finnish_label",

    # Product / technical labels that are intentionally identical in Finnish.
    "dna_nexus_files",
    "versions_hash",
    "versions_id_value",
    "shares_type_state",

    # Brand kickers, URL labels, examples, and placeholders.
    "echo_stack_header_kicker",
    "echo_stack_url_label",
    "echo_stack_url_placeholder",
    "drop_zone_header_kicker",
    "drop_zone_name_placeholder",
    "drop_zone_footer_text_placeholder",

    # Placeholder/status templates where Finnish intentionally keeps the same shape.
    "admin_status_action_running",
    "admin_status_action_ok",

    # Natural Finnish technical/media label.
    "media_status_video",

    # Default device label is a product/platform name.
    "pair_default_device_name",
}


@dataclass(frozen=True)
class AndroidString:
    name: str
    value: str
    raw_value: str
    line_no: int
    raw_line: str


_STRING_RE = re.compile(
    r"<string\b(?P<attrs>[^>]*\bname=\"(?P<name>[^\"]+)\"[^>]*)>"
    r"(?P<value>.*?)</string>",
    re.S,
)

_NAME_RE = re.compile(r'name="([^"]+)"')

_PLACEHOLDER_RE = re.compile(
    r"(?<!%)%(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z]"
)


def die(msg: str) -> None:
    print(f"ERROR: {msg}", file=sys.stderr)
    raise SystemExit(2)


def strings_path(res_dir: Path, values_dir: str) -> Path:
    return res_dir / values_dir / "strings.xml"


def load_allow_file(path: Path | None) -> set[str]:
    if path is None:
        return set()
    if not path.exists():
        die(f"missing allow file: {path}")
    out = set()
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].strip()
        if line:
            out.add(line)
    return out


def normalize_value(raw: str) -> str:
    text = raw.strip()
    # Keep inner Android markup comparison stable enough for review output.
    text = re.sub(r"\s+", " ", text)
    return unescape(text)


def line_number_for_offset(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def raw_line_for_line(text: str, line_no: int) -> str:
    lines = text.splitlines()
    if 1 <= line_no <= len(lines):
        return lines[line_no - 1].rstrip()
    return ""


def load_strings(path: Path) -> tuple[dict[str, AndroidString], list[str]]:
    if not path.exists():
        die(f"missing file: {path}")

    text = path.read_text(encoding="utf-8")
    strings: dict[str, AndroidString] = {}
    dupes: list[str] = []

    for m in _STRING_RE.finditer(text):
        attrs = m.group("attrs")
        if 'translatable="false"' in attrs:
            continue

        name = m.group("name")
        raw_value = m.group("value")
        value = normalize_value(raw_value)
        line_no = line_number_for_offset(text, m.start())
        raw_line = raw_line_for_line(text, line_no)

        if name in strings:
            dupes.append(name)

        strings[name] = AndroidString(
            name=name,
            value=value,
            raw_value=raw_value,
            line_no=line_no,
            raw_line=raw_line,
        )

    return strings, dupes


def wanted(name: str, prefixes: list[str]) -> bool:
    return not prefixes or any(name.startswith(prefix) for prefix in prefixes)


def placeholders(value: str) -> list[str]:
    out = []
    for m in _PLACEHOLDER_RE.finditer(value):
        token = m.group(0)
        if token == "%%":
            continue
        out.append(token)
    return out


def placeholder_signature(value: str) -> list[str]:
    # Keep exact tokens, because Android positional placeholders matter.
    return sorted(placeholders(value))


def format_location(path: Path, item: AndroidString | None, marker: str, detail: str) -> str:
    if item is None:
        return f"{path}:?: [{marker}] {detail}"
    return f"{path}:{item.line_no}: [{marker}] {detail}"
