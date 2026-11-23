#!/usr/bin/env python3
"""Builds the static website in website/ (freexmltoolkit.org) from the page sources in website-src/.

website-src/<lang>/<path>/index.html (and en/404.html) hold what differs per page:

    title: Features – FreeXmlToolkit
    description: One sentence for search engines and link previews.
    nav: features                      (optional: the main menu entry to mark as current)
    head: <script type="application/ld+json">…</script>     (optional, may be repeated)

    <main id="main"> … </main>

Header, footer and the <head> come from this script, once per language (CHROME). In the page body
    {{root}}  is the relative path to the site root (assets/, docs/),
    {{home}}  the relative path to the root of the page's language (other pages).
English pages are written to website/<path>/, German ones to website/de/<path>/. A page that exists
in both languages gets hreflang links and a language switch in the header. sitemap.xml is written too.

Screenshots: IMAGES maps website/assets/img/<name>.webp to its source in docs/img/ (made by
`./gradlew docScreenshots`); a missing or older .webp is converted (needs Pillow with WebP support).

Never edit website/**/*.html by hand - the next build overwrites it. The other assets (css, js,
brand, fonts), .htaccess and robots.txt are not generated.

Usage: python3 scripts/build-website.py [--check]      (--check: exit 1 if website/ is not up to date)
"""
import datetime
import html
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "website-src"
SITE = ROOT / "website"
BASE_URL = "https://freexmltoolkit.org/"
LANGS = ("en", "de")
SPONSOR = "https://github.com/sponsors/karlkauc"
GITHUB = "https://github.com/karlkauc/FreeXmlToolkit"

# (key, target below the language root or the site root, label per language)
NAV = [
    ("features", "{{home}}features/", {"en": "Features", "de": "Funktionen"}),
    ("industries", "{{home}}industries/", {"en": "Industries", "de": "Branchen"}),
    ("enterprise", "{{home}}enterprise/", {"en": "Enterprise", "de": "Unternehmen"}),
    ("ai", "{{home}}ai/", {"en": "AI", "de": "KI"}),
    ("compare", "{{home}}compare/", {"en": "Compare", "de": "Vergleich"}),
    ("docs", "{{root}}docs/", {"en": "Docs", "de": "Doku"}),
]

CHROME = {
    "en": {
        "skip": "Skip to content", "home_label": "FreeXmlToolkit home", "menu": "Menu", "nav_label": "Main",
        "download": "Download", "tagline": "Free and open source under Apache 2.0.",
        "trademarks": "Product names are trademarks of their owners.",
        "switch": ("de", "Deutsch"),
        "footer": [
            ("Product", [("{{home}}features/", "Features"), ("{{home}}industries/", "Industries"),
                         ("{{home}}enterprise/", "Enterprise"), ("{{home}}ai/", "AI &amp; automation"),
                         ("{{home}}compare/", "Compare"), ("{{home}}screenshots/", "Screenshots")]),
            ("Project", [("{{home}}news/", "News"), ("{{home}}about/", "About"), (SPONSOR, "Sponsor"),
                         (GITHUB, "GitHub")]),
            ("Help", [("{{root}}docs/", "Documentation"), ("{{home}}faq/", "FAQ"),
                      ("{{root}}docs/reference/faq/", "Troubleshooting"),
                      (GITHUB + "/issues/new/choose", "Report a problem")]),
            ("Legal", [("{{home}}imprint/", "Imprint"), ("{{home}}privacy/", "Privacy"),
                       ("{{root}}docs/reference/licenses/", "Licenses")]),
        ],
    },
    "de": {
        "skip": "Zum Inhalt springen", "home_label": "FreeXmlToolkit Startseite", "menu": "Menü",
        "nav_label": "Hauptmenü", "download": "Download",
        "tagline": "Frei und Open Source unter Apache 2.0.",
        "trademarks": "Produktnamen sind Marken ihrer Inhaber.",
        "switch": ("en", "English"),
        "footer": [
            ("Produkt", [("{{home}}features/", "Funktionen"), ("{{home}}industries/", "Branchen"),
                         ("{{home}}enterprise/", "Unternehmen"), ("{{home}}ai/", "KI &amp; Automatisierung"),
                         ("{{home}}compare/", "Vergleich"), ("{{home}}screenshots/", "Screenshots")]),
            ("Projekt", [("{{home}}news/", "Neuigkeiten"), ("{{home}}about/", "Über das Projekt"),
                         (SPONSOR, "Sponsern"), (GITHUB, "GitHub")]),
            ("Hilfe", [("{{root}}docs/", "Dokumentation (englisch)"), ("{{home}}faq/", "Fragen und Antworten"),
                       ("{{root}}docs/reference/faq/", "Fehlerbehebung (englisch)"),
                       (GITHUB + "/issues/new/choose", "Problem melden")]),
            ("Rechtliches", [("{{home}}imprint/", "Impressum"), ("{{home}}privacy/", "Datenschutz"),
                             ("{{root}}docs/reference/licenses/", "Lizenzen")]),
        ],
    },
}


# website/assets/img/<key>.webp <- docs/img/<value>
IMAGES = {
    "accent": "unified-shell-accent-violet.png",
    "batch-validation": "xsd-validation-batch.png",
    "bookmarks": "unified-shell-bookmarks.png",
    "compare-folders": "compare-folders.png",
    "compare-xml": "compare-xml.png",
    "encryption": "encrypt-recipients.png",
    "grid-formulas": "unified-shell-grid-cell-formulas.png",
    "inspector": "unified-shell-inspector.png",
    "intellisense": "xml-editor-intellisense.png",
    "json-grid": "unified-shell-json-grid.png",
    "large-file": "large-file-structure.png",
    "merge": "merge-three-way.png",
    "problems": "problems-grouped.png",
    "profile-table": "profile-table.png",
    "profiler": "profile-flame-graph.png",
    "project-settings": "project-settings.png",
    "quick-fix": "schematron-quick-fix.png",
    "sample-xml": "xsd-sample-generator.png",
    "scenario-check": "project-scenario-check.png",
    "schema-analysis": "unified-shell-schema-statistics.png",
    "schema-documentation": "xsd-documentation.png",
    "schema-library": "unified-shell-schema-library-mappings.png",
    "schema-overview": "unified-shell-schema-overview.png",
    "search-in-files": "search-in-files.png",
    "signature-validation": "signature-validation.png",
    "spreadsheet-converter": "spreadsheet-converter-export.png",
    "transform": "unified-shell-transform.png",
    "type-editor": "xsd-type-editor.png",
    "type-frames": "unified-shell-schema-type-frames.png",
    "validation": "unified-shell-validation.png",
    "xspec": "xspec-result.png",
}


def images(check):
    """Converts the screenshots that are missing or older than their source; -> names that are stale."""
    stale = []
    for name, source in IMAGES.items():
        src, out = ROOT / "docs" / "img" / source, SITE / "assets" / "img" / (name + ".webp")
        if not src.exists():
            sys.exit(f"docs/img/{source} is missing (image {name}.webp)")
        if out.exists() and (check or out.stat().st_mtime >= src.stat().st_mtime):
            continue  # --check runs on a fresh checkout too, where file times say nothing
        stale.append(out)
        if not check:
            from PIL import Image
            Image.open(src).convert("RGB").save(out, "WEBP", quality=82, method=6)
    return stale


def parse(path):
    """-> (meta dict with 'head' as a list, body)"""
    text = path.read_text(encoding="utf-8")
    front, _, body = text.partition("\n\n")
    meta = {"head": []}
    for line in front.splitlines():
        key, _, value = line.partition(":")
        if key == "head":
            meta["head"].append(value.strip())
        else:
            meta[key.strip()] = value.strip()
    for key in ("title", "description"):
        if not meta.get(key):
            sys.exit(f"{path.relative_to(ROOT)}: no {key}")
    return meta, body.rstrip("\n")


def link(target, depth_root, depth_home):
    """Resolves {{root}} / {{home}}; a link to the folder the page is in becomes './'."""
    out = target.replace("{{root}}", depth_root).replace("{{home}}", depth_home)
    return out or "./"


def resolve(text, root, home):
    text = re.sub(r'(href|src)="\{\{(root|home)\}\}"',
                  lambda m: f'{m.group(1)}="{(root if m.group(2) == "root" else home) or "./"}"', text)
    return text.replace("{{root}}", root).replace("{{home}}", home)


def render(lang, rel, meta, body, counterpart):
    """rel: path of the page below its language root ('' = home, 'features/', '404.html')."""
    c = CHROME[lang]
    is_404 = rel == "404.html"
    lang_prefix = "" if lang == "en" else lang + "/"
    url_path = lang_prefix + rel
    if is_404:
        root = home = "/"
    else:
        home = "../" * rel.count("/")
        root = home + ("" if lang == "en" else "../")
    url = BASE_URL + url_path
    title, description = meta["title"], meta["description"]
    current = meta.get("nav")
    o = []
    o.append("<!doctype html>")
    o.append(f'<html lang="{lang}">')
    o.append("<head>")
    o.append('<meta charset="utf-8">')
    o.append('<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">')
    o.append(f"<title>{title}</title>")
    o.append(f'<meta name="description" content="{description}">')
    o.append(f'<link rel="canonical" href="{url}">')
    if counterpart:
        for code in LANGS:
            o.append(f'<link rel="alternate" hreflang="{code}" href="{BASE_URL}{"" if code == "en" else code + "/"}{rel}">')
        o.append(f'<link rel="alternate" hreflang="x-default" href="{BASE_URL}{rel}">')
    o.append('<meta name="theme-color" content="#2a284c">')
    o.append('<meta property="og:type" content="website">')
    o.append('<meta property="og:site_name" content="FreeXmlToolkit">')
    o.append(f'<meta property="og:title" content="{title}">')
    o.append(f'<meta property="og:description" content="{description}">')
    o.append(f'<meta property="og:url" content="{url}">')
    o.append(f'<meta property="og:image" content="{BASE_URL}assets/brand/og-image.png">')
    o.append('<meta name="twitter:card" content="summary_large_image">')
    o.append(f'<link rel="icon" href="{root}assets/brand/app-icon.svg" type="image/svg+xml">')
    o.append(f'<link rel="icon" href="{root}assets/brand/favicon-32.png" sizes="32x32" type="image/png">')
    o.append(f'<link rel="apple-touch-icon" href="{root}assets/brand/apple-touch-icon.png">')
    o.append(f'<link rel="stylesheet" href="{root}assets/css/site.css">')
    o.append(f'<script src="{root}assets/js/site.js" defer></script>')
    o.extend(meta["head"])
    o.append("</head>")
    o.append("<body>")
    o.append(f'<a class="skip" href="#main">{c["skip"]}</a>')
    o.append('<header class="site-header">')
    o.append('<div class="wrap">')
    o.append(f'<a class="brand" href="{link("{{home}}", root, home)}" aria-label="{c["home_label"]}">'
             f'<img src="{root}assets/brand/lockup-horizontal-on-dark.svg" alt="" width="181" height="30"></a>')
    o.append(f'<button class="menu-toggle" type="button" aria-expanded="false" aria-controls="site-nav">{c["menu"]}</button>')
    nav = []
    for key, target, labels in NAV:
        mark = ' aria-current="page"' if key == current else ""
        nav.append(f'<a href="{link(target, root, home)}"{mark}>{labels[lang]}</a>')
    if counterpart:
        code, label = c["switch"]
        other = root + ("" if code == "en" else code + "/") + rel
        nav.append(f'<a class="lang" href="{other or "./"}" hreflang="{code}" lang="{code}">{label}</a>')
    mark = ' aria-current="page"' if current == "download" else ""
    nav.append(f'<a class="btn" href="{link("{{home}}download/", root, home)}"{mark}>{c["download"]}</a>')
    o.append(f'<nav class="site-nav" id="site-nav" aria-label="{c["nav_label"]}">{"".join(nav)}</nav>')
    o.append("</div>")
    o.append("</header>")
    o.append(resolve(body, root, home))
    o.append('<footer class="site-footer on-navy">')
    o.append('<div class="wrap">')
    o.append('<div class="foot-grid">')
    o.append(f'<div class="foot-brand"><img src="{root}assets/brand/lockup-horizontal-on-dark.svg" alt="FreeXmlToolkit" '
             f'width="181" height="30"><p>{c["tagline"]}</p></div>')
    for heading, links in c["footer"]:
        items = "".join(f'<li><a href="{link(t, root, home)}">{label}</a></li>' for t, label in links)
        o.append(f"<div><h2>{heading}</h2><ul>{items}</ul></div>")
    o.append("</div>")
    o.append(f'<div class="foot-bottom"><span>© 2024–{YEAR} Karl Kauc</span><span>{c["trademarks"]}</span></div>')
    o.append("</div>")
    o.append("</footer>")
    o.append("</body>")
    o.append("</html>")
    return "\n".join(o) + "\n"


YEAR = 2026


def pages():
    """-> {(lang, rel): source path}"""
    found = {}
    for lang in LANGS:
        base = SRC / lang
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.html")):
            rel = path.relative_to(base).as_posix()
            if rel.endswith("index.html"):
                rel = rel[: -len("index.html")]
            found[(lang, rel)] = path
    return found


def target_of(lang, rel):
    name = rel if rel.endswith(".html") else rel + "index.html"
    return SITE / ("" if lang == "en" else lang) / name


def sitemap(found, old):
    """lastmod stays what it was for pages whose HTML did not change."""
    today = datetime.date.today().isoformat()
    lines = ['<?xml version="1.0" encoding="UTF-8"?>',
             '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">']
    for (lang, rel), changed in found:
        if rel.endswith(".html"):
            continue
        url = BASE_URL + ("" if lang == "en" else lang + "/") + rel
        lastmod = today if changed or url not in old else old[url]
        lines.append(f"  <url><loc>{url}</loc><lastmod>{lastmod}</lastmod></url>")
    lines.append("</urlset>")
    return "\n".join(lines) + "\n"


def main():
    check = "--check" in sys.argv[1:]
    found = pages()
    stale, order = images(check), []
    for (lang, rel), path in found.items():
        meta, body = parse(path)
        other = [code for code in LANGS if code != lang and (code, rel) in found]
        text = render(lang, rel, meta, body, bool(other) and rel != "404.html")
        target = target_of(lang, rel)
        changed = not target.exists() or target.read_text(encoding="utf-8") != text
        order.append(((lang, rel), changed))
        if changed:
            stale.append(target)
            if not check:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(text, encoding="utf-8")
    sm = SITE / "sitemap.xml"
    old = dict(re.findall(r"<loc>(.*?)</loc><lastmod>(.*?)</lastmod>", sm.read_text(encoding="utf-8"))) if sm.exists() else {}
    order.sort(key=lambda item: (LANGS.index(item[0][0]), SITEMAP_ORDER.index(item[0][1]) if item[0][1] in SITEMAP_ORDER else 99, item[0][1]))
    text = sitemap(order, old)
    if not sm.exists() or sm.read_text(encoding="utf-8") != text:
        stale.append(sm)
        if not check:
            sm.write_text(text, encoding="utf-8")
    generated = {target_of(lang, rel) for lang, rel in found}
    orphans = [p for p in SITE.rglob("*.html") if p not in generated and SITE / "docs" not in p.parents]
    for p in orphans:
        print(f"no source for {p.relative_to(ROOT)} (delete it or add website-src/…)")
    print(f"{len(found)} pages, {len(stale)} {'out of date' if check else 'written'}")
    return 1 if (check and stale) or orphans else 0


SITEMAP_ORDER = ["", "features/", "features/editing/", "features/schemas/", "features/validation/",
                 "features/transform/", "features/publish-security/", "features/compare-convert/",
                 "features/projects-cli/", "screenshots/", "industries/", "enterprise/", "ai/", "compare/", "download/", "faq/", "news/", "about/",
                 "imprint/", "privacy/"]

if __name__ == "__main__":
    sys.exit(main())
