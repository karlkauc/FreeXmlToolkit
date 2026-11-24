#!/usr/bin/env python3
"""Checks the static website in website/ (freexmltoolkit.org) before it is uploaded.

- every page has lang, <title>, a meta description and a canonical link;
- every internal link, image, stylesheet and script points to an existing file;
- links to the tutorial videos (the videos/ folder of the server) point to a file in build/website-videos/;
- links into the documentation (the docs/ folder of the server, built from docs/ by MkDocs) point to an
  existing page in docs/;
- header and footer are the same on every page of a language (apart from the relative path prefix,
  aria-current and the language switch); every page exists in English and in German;
- every <img> has an alt attribute; no inline style attributes (the Content-Security-Policy forbids them).

Usage: python3 scripts/check-website.py   (exit code 1 when something is wrong)
"""
import html.parser
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SITE = ROOT / "website"
DOCS = ROOT / "docs"
DOCS_URL = "https://freexmltoolkit.org/docs/"
LANGUAGES = ["de"]  # folders below the root that hold the pages in another language
DOCS_DIR = SITE / "docs"  # only on the server: scripts/upload-website.py --docs puts the MkDocs build there
VIDEOS_DIR = SITE / "videos"  # only on the server: scripts/upload-website.py --videos puts build/website-videos there
VIDEOS_BUILD = ROOT / "build" / "website-videos"  # made by scripts/website-videos.py


class Page(html.parser.HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs, self.imgs_without_alt, self.styles, self.ids = [], 0, 0, set()
        self.lang = self.title = self.description = self.canonical = None
        self._in_title = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "style" in a:
            self.styles += 1
        if "id" in a:
            self.ids.add(a["id"])
        if tag == "html":
            self.lang = a.get("lang")
        elif tag == "title":
            self._in_title = True
        elif tag == "meta" and a.get("name") == "description":
            self.description = a.get("content")
        elif tag == "link" and a.get("rel") == "canonical":
            self.canonical = a.get("href")
        if tag == "img" and "alt" not in a:
            self.imgs_without_alt += 1
        for key in ("href", "src"):
            if a.get(key) and not (tag == "link" and a.get("rel") == "canonical") and tag != "meta":
                self.refs.append(a[key])

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_title:
            self.title = (self.title or "") + data


def docs_page_exists(rest):
    """rest: the path below the documentation root, e.g. guides/edit-xml/grid/"""
    rest = rest.split("#")[0].strip("/")
    if not rest:
        return (DOCS / "index.md").exists()
    return (DOCS / (rest + ".md")).exists() or (DOCS / rest / "index.md").exists()


def chrome(text, tag):
    m = re.search(rf"<{tag}\b.*?</{tag}>", text, re.S)
    if not m:
        return None
    part = m.group(0)
    part = re.sub(r'(href|src)="(\.\./)+', r'\1="', part)
    part = part.replace(' aria-current="page"', "").replace('href="./"', 'href=""')
    # the language switch points to the page's counterpart; a page without one has no switch
    part = re.sub(r'<a class="lang"[^>]*>.*?</a>', "", part)
    return part


def main():
    problems = []
    pages = sorted(SITE.rglob("*.html"))
    headers, footers = {}, {}
    for page in pages:
        rel = page.relative_to(SITE)
        text = page.read_text(encoding="utf-8")
        p = Page()
        p.feed(text)
        if not p.lang:
            problems.append(f"{rel}: no lang attribute")
        if not p.title or not p.title.strip():
            problems.append(f"{rel}: no title")
        if not p.description:
            problems.append(f"{rel}: no meta description")
        if not p.canonical:
            problems.append(f"{rel}: no canonical link")
        if p.imgs_without_alt:
            problems.append(f"{rel}: {p.imgs_without_alt} image(s) without alt")
        if p.styles:
            problems.append(f"{rel}: {p.styles} inline style attribute(s)")
        for ref in p.refs:
            if ref.startswith(DOCS_URL):
                if not docs_page_exists(ref[len(DOCS_URL):]):
                    problems.append(f"{rel}: documentation page not found: {ref}")
                continue
            if re.match(r"^(https?:|mailto:)", ref):
                continue
            path, _, anchor = ref.partition("#")
            if not path:
                if anchor and anchor not in p.ids:
                    problems.append(f"{rel}: anchor #{anchor} not on the page")
                continue
            base = SITE if path.startswith("/") else page.parent
            target = (base / path.lstrip("/")).resolve()
            if target == DOCS_DIR or DOCS_DIR in target.parents:
                if not docs_page_exists(target.relative_to(DOCS_DIR).as_posix()):
                    problems.append(f"{rel}: documentation page not found: {ref}")
                continue
            if VIDEOS_DIR in target.parents:
                if not (VIDEOS_BUILD / target.relative_to(VIDEOS_DIR)).exists():
                    problems.append(f"{rel}: video file not in build/website-videos (run scripts/website-videos.py): {ref}")
                continue
            if target.is_dir():
                target = target / "index.html"
            if not target.exists():
                problems.append(f"{rel}: broken link {ref}")
        if rel.name != "404.html":
            headers[rel] = chrome(text, "header")
            footers[rel] = chrome(text, "footer")
        language = rel.parts[0] if rel.parts[0] in LANGUAGES else "en"
        if p.lang != language:
            problems.append(f"{rel}: lang is {p.lang}, expected {language}")
        if language == "en" and rel.name != "404.html" and (SITE / "de").is_dir() and not (SITE / "de" / rel).exists():
            problems.append(f"{rel}: no German page de/{rel}")
        if language != "en" and not (SITE / rel.relative_to(language)).exists():
            problems.append(f"{rel}: no English page")
    for name, parts in (("header", headers), ("footer", footers)):
        for language in ["en"] + LANGUAGES:   # header and footer are the same within one language
            variants = {}
            for rel, part in parts.items():
                if (rel.parts[0] if rel.parts[0] in LANGUAGES else "en") == language:
                    variants.setdefault(part, []).append(str(rel))
            if len(variants) > 1:
                groups = sorted(variants.values(), key=len)
                problems.append(f"{name} differs on: {', '.join(groups[0])}")
    for line in problems:
        print(line)
    print(f"{len(pages)} pages checked, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
