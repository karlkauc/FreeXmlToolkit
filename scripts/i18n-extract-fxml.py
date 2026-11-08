#!/usr/bin/env python3
"""Moves the user-facing texts of the FXML layouts into the English bundle.

Every text, promptText, title, emptyText, caption and subtitle attribute of
src/main/resources/pages/**/*.fxml that is not a reference yet becomes "%<Layout>.<id or text>.<attr>",
and the text is appended to src/main/resources/i18n/messages.properties. Run it after adding
texts to FXML files; then add the German texts to messages_de.properties (I18nBundlesTest lists
the missing ones).
"""
import html
import pathlib
import re
import sys

PAGES = pathlib.Path("src/main/resources/pages")
BUNDLE = pathlib.Path("src/main/resources/i18n/messages.properties")
ATTRIBUTES = ("text", "promptText", "title", "emptyText", "caption", "subtitle")
ATTR = re.compile(r'(\s)(' + "|".join(ATTRIBUTES) + r')="([^"]*)"')
ID = re.compile(r'\s(?:fx:id|id)="([^"]+)"')


def tags(source):
    """Yields (start, end) of every start tag outside comments, CDATA and processing instructions."""
    i = 0
    n = len(source)
    while i < n:
        j = source.find("<", i)
        if j < 0:
            return
        if source.startswith("<!--", j):
            i = source.find("-->", j) + 3
            continue
        if source.startswith("<![CDATA[", j):
            i = source.find("]]>", j) + 3
            continue
        if source.startswith("<?", j) or source.startswith("</", j):
            i = source.find(">", j) + 1
            continue
        quote = None
        k = j + 1
        while k < n:
            c = source[k]
            if quote:
                if c == quote:
                    quote = None
            elif c in "\"'":
                quote = c
            elif c == ">":
                break
            k += 1
        yield j, k + 1
        i = k + 1


def slug(text):
    words = re.findall(r"[A-Za-z0-9]+", text)[:5]
    return "-".join(w.lower() for w in words) or "text"


def plain(value):
    text = html.unescape(value)
    if text[:2] in ("\\@", "\\%", "\\$", "\\#"):
        text = text[1:]
    return text


def escape(value):
    out = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "")
    return "\\" + out if out.startswith(" ") else out


def translatable(value):
    return value and value[0] not in "%$#@" and re.search(r"[A-Za-z]{2}", value)


def main():
    existing = BUNDLE.read_text(encoding="utf-8") if BUNDLE.exists() else ""
    keys = set(re.findall(r"^([^#\s][^=]*)=", existing, re.M))
    lines = []
    changed = 0
    for path in sorted(PAGES.rglob("*.fxml")):
        layout = path.stem
        source = path.read_text(encoding="utf-8")
        out = []
        last = 0
        file_lines = []
        for start, end in tags(source):
            tag = source[start:end]
            ident = ID.search(tag)

            def replace(m):
                attr, value = m.group(2), m.group(3)
                if not translatable(value):
                    return m.group(0)
                base = f"{layout}.{re.sub(r'[^A-Za-z0-9_-]', '-', ident.group(1)) if ident else slug(plain(value))}.{attr}"
                key = base
                number = 2
                while key in keys:
                    key = f"{base}-{number}"
                    number += 1
                keys.add(key)
                file_lines.append(f"{key}={escape(plain(value))}")
                return f'{m.group(1)}{attr}="%{key}"'

            new_tag = ATTR.sub(replace, tag)
            out.append(source[last:start])
            out.append(new_tag)
            last = end
        out.append(source[last:])
        if file_lines:
            path.write_text("".join(out), encoding="utf-8")
            lines.append(f"\n# {path.relative_to(PAGES)}")
            lines.extend(file_lines)
            changed += len(file_lines)
    with BUNDLE.open("a", encoding="utf-8") as f:
        f.write("\n".join(lines) + ("\n" if lines else ""))
    print(f"{changed} texts moved into {BUNDLE}", file=sys.stderr)


if __name__ == "__main__":
    main()
