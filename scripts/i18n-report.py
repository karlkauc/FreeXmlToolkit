#!/usr/bin/env python3
"""Lists Java files that still contain hard-coded user-facing English texts (heuristic): string
literals that start with a capital letter and contain a space, outside comments, log calls,
exceptions and I18n calls. The worklist for moving texts into i18n/messages*.properties.

Usage: scripts/i18n-report.py [--top N] [--show FILE]
"""
import pathlib
import re
import sys

ROOT = pathlib.Path("src/main/java/org/fxt/freexmltoolkit")
LITERAL = re.compile(r'"([A-Z][A-Za-z\']*(?: [^"\\]*|…)[^"\\]*)"')
SKIP_LINE = re.compile(r'^\s*(//|\*|/\*)|logger\.|LOG\.|log\.(debug|info|warn|error|trace)|LogManager|'
                       r'Exception\(|I18n\.|assert|@DisplayName|System\.(out|err)|\.matches\(|Pattern\.compile')


def literals(path):
    found = []
    for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
        if SKIP_LINE.search(line):
            continue
        for m in LITERAL.finditer(line):
            text = m.group(1)
            if re.search(r"[a-z]{2}", text):
                found.append((number, text))
    return found


def main():
    args = sys.argv[1:]
    if "--show" in args:
        path = pathlib.Path(args[args.index("--show") + 1])
        for number, text in literals(path):
            print(f"{number:5}  {text}")
        return
    top = int(args[args.index("--top") + 1]) if "--top" in args else 40
    counts = sorted(((len(literals(p)), p) for p in ROOT.rglob("*.java")), reverse=True)
    total = sum(c for c, _ in counts)
    print(f"{total} candidate texts in {sum(1 for c, _ in counts if c)} files")
    for count, path in counts[:top]:
        if count:
            print(f"{count:5}  {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
