#!/usr/bin/env python3
"""Render the real MiniMessage templates into an SVG preview.

Generated from modules/core/src/main/resources/messages.yml so the picture cannot drift away from
what the plugin actually sends. Not an in-game screenshot: it reproduces the colours, gradients and
layout of the templates, nothing more.

    python3 docs/preview/render_messages.py
"""

import html
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
MESSAGES = ROOT / "modules/core/src/main/resources/messages.yml"
OUTPUT = ROOT / "docs/messages-preview.svg"

NAMED_COLOURS = {
    "black": "#000000", "dark_blue": "#0000aa", "dark_green": "#00aa00", "dark_aqua": "#00aaaa",
    "dark_red": "#aa0000", "dark_purple": "#aa00aa", "gold": "#ffaa00", "gray": "#aaaaaa",
    "dark_gray": "#555555", "blue": "#5555ff", "green": "#55ff55", "aqua": "#55ffff",
    "red": "#ff5555", "light_purple": "#ff55ff", "yellow": "#ffff55", "white": "#ffffff",
}

SAMPLE = {
    "player": "Steve", "alt": "Steve_Alt", "admin": "vanfek", "address": "203.0.113.42",
    "reason": "использование читов", "duration": "7д", "remaining": "6д 4ч 12м",
    "expires_at": "2026-08-13T18:20:00Z", "issued_at": "2026-08-06T18:20:00Z",
    "at": "2026-08-06 18:20", "last_seen": "2026-08-05 21:07", "page": "1",
    "action": "БАН", "entries": "1284", "hit_ratio": "97.4%", "hits": "48210",
    "misses": "1291", "stale": "3", "calls": "1291", "failures": "0",
    "average": "2.4 мс", "slowest": "38 мс", "events": "512",
    "status": "500", "message": "недоступен", "value": "3x", "target": "Steve",
}

PANELS = [
    ("Экран кика забаненного игрока", ["login.kick-temporary"]),
    ("Попытка написать в чат с запретом", ["chat.muted-temporary"]),
    ("/pp info", ["info.banned-temporary"]),
    ("/pp list", ["list.header", "list.entry-permanent", "list.entry-temporary"]),
    ("/pp history", ["history.header", "history.entry-temporary", "history.entry-warn"]),
    ("/pp alts", ["alts.header", "alts.entry"]),
    ("/pp status", ["status.header", "status.cache", "status.service", "status.redis-connected"]),
]

TAG = re.compile(r"<(/?)([a-z_]+)(?::([^>]*))?>")
LINE_HEIGHT = 22
PANEL_PAD = 16
PANEL_GAP = 18
TITLE_HEIGHT = 26
WIDTH = 1000


def load_templates():
    templates, path, buffer, block_key = {}, [], None, None

    for raw in MESSAGES.read_text(encoding="utf-8").splitlines():
        if block_key is not None:
            if raw.startswith("    ") or not raw.strip():
                buffer.append(raw[4:])
                continue

            templates[block_key] = "\n".join(buffer).rstrip()
            block_key, buffer = None, []

        if not raw.strip() or raw.lstrip().startswith("#"):
            continue

        indent = len(raw) - len(raw.lstrip())
        depth = indent // 2
        stripped = raw.strip()

        if ":" not in stripped:
            continue

        key, _, value = stripped.partition(":")
        value = value.strip()
        path = path[:depth] + [key]
        full = ".".join(path)

        if value in ("|-", "|"):
            block_key, buffer = full, []
        elif value:
            templates[full] = value.strip('"')

    if block_key is not None:
        templates[block_key] = "\n".join(buffer).rstrip()

    return templates


def substitute(template):
    for name, value in SAMPLE.items():
        template = template.replace(f"<{name}>", value)

    return template


def gradient_colour(start, end, position):
    def channel(index):
        low = int(start[1 + index * 2:3 + index * 2], 16)
        high = int(end[1 + index * 2:3 + index * 2], 16)

        return round(low + (high - low) * position)

    return "#%02x%02x%02x" % (channel(0), channel(1), channel(2))


def to_spans(line):
    spans, colours, bold = [], ["#aaaaaa"], [False]
    gradient, gradient_text = None, []
    cursor = 0

    def emit(text, colour, is_bold):
        if text:
            spans.append((text, colour, is_bold))

    for match in TAG.finditer(line):
        text = line[cursor:match.start()]
        cursor = match.end()

        if gradient is not None:
            gradient_text.append(text)
        else:
            emit(text, colours[-1], bold[-1])

        closing, name, argument = match.group(1), match.group(2), match.group(3)

        if name == "gradient":
            if closing:
                body = "".join(gradient_text)
                start, end = gradient

                for index, character in enumerate(body):
                    ratio = index / max(len(body) - 1, 1)
                    emit(character, gradient_colour(start, end, ratio), bold[-1])

                gradient, gradient_text = None, []
            else:
                parts = (argument or "#ffffff:#ffffff").split(":")
                gradient = (parts[0], parts[-1])
        elif name == "bold":
            bold.append(False) if closing else bold.append(True)

            if closing and len(bold) > 1:
                bold.pop()
                bold.pop() if len(bold) > 1 else None
        elif name in NAMED_COLOURS:
            if closing:
                if len(colours) > 1:
                    colours.pop()
            else:
                colours.append(NAMED_COLOURS[name])

    remainder = line[cursor:]

    if gradient is None:
        emit(remainder, colours[-1], bold[-1])

    return spans


def render_panel(title, keys, templates, top):
    lines = []

    for key in keys:
        template = templates.get(key)

        if template is None:
            print(f"missing key: {key}", file=sys.stderr)
            continue

        for line in substitute(template).split("\n"):
            lines.append(to_spans(line))

    height = TITLE_HEIGHT + PANEL_PAD * 2 + LINE_HEIGHT * max(len(lines), 1)
    parts = [
        f'<rect x="16" y="{top}" width="{WIDTH - 32}" height="{height}" rx="8" fill="#16161c" '
        f'stroke="#2a2a35"/>',
        f'<text x="32" y="{top + 22}" fill="#6f6f80" font-size="12" '
        f'font-family="Inter, Segoe UI, sans-serif">{html.escape(title)}</text>',
    ]

    baseline = top + TITLE_HEIGHT + PANEL_PAD

    for index, spans in enumerate(lines):
        y = baseline + index * LINE_HEIGHT
        tspans = []

        for text, colour, is_bold in spans:
            escaped = html.escape(text)
            weight = ' font-weight="700"' if is_bold else ""
            tspans.append(f'<tspan fill="{colour}"{weight}>{escaped}</tspan>')

        parts.append(
            f'<text x="32" y="{y}" font-size="14" '
            f'font-family="JetBrains Mono, DejaVu Sans Mono, Consolas, monospace" '
            f'xml:space="preserve">{"".join(tspans)}</text>'
        )

    return "\n".join(parts), height


def main():
    templates = load_templates()
    body, top = [], 56

    for title, keys in PANELS:
        rendered, height = render_panel(title, keys, templates, top)
        body.append(rendered)
        top += height + PANEL_GAP

    total = top + 12
    svg = "\n".join([
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{total}" '
        f'viewBox="0 0 {WIDTH} {total}">',
        f'<rect width="{WIDTH}" height="{total}" fill="#0e0e13"/>',
        '<text x="16" y="34" fill="#e6e6ef" font-size="17" '
        'font-family="Inter, Segoe UI, sans-serif" font-weight="600">'
        'ProxyPunishments — как выглядят сообщения</text>',
        *body,
        "</svg>",
    ])

    OUTPUT.write_text(svg + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT.relative_to(ROOT)} ({total}px tall, {len(PANELS)} panels)")


if __name__ == "__main__":
    main()
