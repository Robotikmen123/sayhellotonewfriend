#!/usr/bin/env python3
"""
WhatsApp iOS export parser.

Reads one or more `WhatsApp Chat - <name>.zip` files exported from iOS
("Export Chat → Attach Media"), separates the user's messages from the
counterpart's, and writes a JSONL stream for downstream persona building.

Usage:
    python scripts/extract_whatsapp.py \
        --me "Ali Yilmaz" \
        --out data/messages.jsonl \
        exports/*.zip
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable, Iterator


# iOS WhatsApp line format (Turkish locale and English both):
#   [12.03.2026 21:43:11] Ali Yilmaz: Selam knk
#   [3/12/26, 9:43:11 PM] Ali Yilmaz: Selam knk
LINE_RE = re.compile(
    r"^\[(?P<ts>[^\]]+)\]\s(?P<sender>[^:]+?):\s(?P<body>.*)$"
)

# Date formats we try in order. iOS uses the device locale, so we accept several.
DATE_FORMATS = [
    "%d.%m.%Y %H:%M:%S",
    "%d.%m.%Y, %H:%M:%S",
    "%d/%m/%Y, %H:%M:%S",
    "%d/%m/%y, %H:%M:%S",
    "%m/%d/%y, %I:%M:%S %p",
    "%m/%d/%Y, %I:%M:%S %p",
]

# Lines we strip because they're system noise, not real speech.
SYSTEM_PATTERNS = [
    "Messages and calls are end-to-end encrypted",
    "Mesajlar ve aramalar uçtan uca şifreli",
    "<Media omitted>",
    "<Medya dahil değil>",
    "image omitted",
    "video omitted",
    "audio omitted",
    "sticker omitted",
    "GIF omitted",
    "Contact card omitted",
    "Görüntüleme tek sefer",
]


@dataclass
class Message:
    ts: str  # ISO-8601
    sender: str
    body: str
    is_me: bool
    chat: str  # source chat filename (without .zip)


def parse_timestamp(raw: str) -> str | None:
    raw = raw.strip().replace(" ", " ").replace("‎", "")
    for fmt in DATE_FORMATS:
        try:
            return datetime.strptime(raw, fmt).isoformat()
        except ValueError:
            continue
    return None


def is_system(body: str) -> bool:
    return any(pat in body for pat in SYSTEM_PATTERNS)


def parse_chat_file(text: str, me: str, chat_name: str) -> Iterator[Message]:
    """Parse a `_chat.txt` body into a stream of Message objects."""
    current: Message | None = None
    for raw_line in text.splitlines():
        line = raw_line.rstrip("\r")
        # Strip leading LTR/RTL marks WhatsApp injects.
        line = re.sub(r"^[‎‏‪-‮]+", "", line)
        m = LINE_RE.match(line)
        if m:
            if current and not is_system(current.body):
                yield current
            ts = parse_timestamp(m.group("ts"))
            if ts is None:
                current = None
                continue
            sender = m.group("sender").strip()
            body = m.group("body").strip()
            current = Message(
                ts=ts,
                sender=sender,
                body=body,
                is_me=(sender.casefold() == me.casefold()),
                chat=chat_name,
            )
        else:
            # Continuation of a multi-line message.
            if current is not None and line:
                current.body += "\n" + line
    if current and not is_system(current.body):
        yield current


def iter_zips(paths: Iterable[Path]) -> Iterator[tuple[str, str]]:
    """Yield (chat_name, text) for each `_chat.txt` found in the given zips."""
    for p in paths:
        if not zipfile.is_zipfile(p):
            print(f"skip (not a zip): {p}", file=sys.stderr)
            continue
        chat_name = p.stem  # e.g. "WhatsApp Chat - Mehmet"
        with zipfile.ZipFile(p) as zf:
            for name in zf.namelist():
                if name.endswith("_chat.txt") or name.endswith(".txt"):
                    with zf.open(name) as f:
                        yield chat_name, f.read().decode("utf-8", errors="replace")
                    break


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("zips", nargs="+", type=Path, help="WhatsApp export .zip files")
    ap.add_argument("--me", required=True, help="Your display name exactly as it appears in the chat")
    ap.add_argument("--out", required=True, type=Path, help="Output JSONL path")
    ap.add_argument("--min-len", type=int, default=2, help="Drop messages shorter than this many chars")
    args = ap.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)

    total = 0
    mine = 0
    with args.out.open("w", encoding="utf-8") as out:
        for chat_name, text in iter_zips(args.zips):
            for msg in parse_chat_file(text, me=args.me, chat_name=chat_name):
                if len(msg.body) < args.min_len:
                    continue
                total += 1
                if msg.is_me:
                    mine += 1
                out.write(json.dumps(msg.__dict__, ensure_ascii=False) + "\n")

    print(f"parsed {total} messages, {mine} are yours → {args.out}", file=sys.stderr)
    if mine == 0:
        print(
            "WARNING: no messages matched --me. Open one of the .txt files in the "
            "zip and copy your name EXACTLY (including spaces/case) as it appears.",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
