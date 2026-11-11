"""Builds docs/img/feature-tour.webp: an animated slideshow of the main features for the README and
the documentation's home page, made from the screenshots in docs/img/ (xvfb-run ./gradlew
docScreenshots). Each slide shows one screenshot with a caption band (app logo, feature, short
description, progress dots), held for HOLD_MS and cross-faded into the next one; it loops.
Usage: python3 scripts/make-feature-tour.py [output-file]. Needs Pillow with WebP support."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG = ROOT / "docs" / "img"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else IMG / "feature-tour.webp"
FONTS = ROOT / "src" / "main" / "resources" / "css" / "fonts"

# (screenshot, title, description) - the order of the tour
SLIDES = [
    ("unified-shell-welcome.png", "One workstation for XML",
     "XML, XSD, XSLT, XQuery, Schematron and JSON - Windows, macOS and Linux"),
    ("unified-shell-schema-graphic.png", "Visual XSD editor",
     "Design schemas as a diagram, with undo for every change - XSD 1.0 and 1.1"),
    ("unified-shell-schema-overview.png", "Schema overview",
     "Every global component in one table: insert, reorder, rename with references"),
    ("unified-shell-xml-grid.png", "XML grid",
     "Repeating elements as editable tables, with filters and formulas"),
    ("xml-editor-intellisense.png", "Schema-aware IntelliSense",
     "Offers only what the schema allows at the caret"),
    ("xml-editor-schematron.png", "Validation",
     "XSD 1.1, Schematron with Quick Fixes, JSON Schema, DTD and RELAX NG"),
    ("xslt-debugger-paused.png", "XSLT, XQuery and Schematron debugger",
     "Breakpoints, stepping, variables, watches, call stack and coverage"),
    ("xslt-developer-overview.png", "Transformations",
     "XSLT 3.0 and XQuery 3.1 with Saxon, live preview and a profiler"),
    ("unified-shell-query-console.png", "Query Console",
     "Ad-hoc XPath, XQuery and JSONPath against the open document"),
    ("ai-assistant-proposal.png", "Optional AI assistant",
     "A model of your choice - every answer checked before you see it; MCP server for agents"),
    ("fop-pdf-preview.png", "PDF with Apache FOP",
     "XSL-FO to PDF with in-app preview and PDF/A output"),
    ("unified-shell-schema-statistics.png", "Schema analysis and documentation",
     "Quality checks, statistics, and HTML, Word or PDF documentation"),
    ("unified-shell-json-grid.png", "JSON editing",
     "Text, tree and grid views, JSON Schema, conversion to XML and YAML"),
    ("unified-shell-signature.png", "XML digital signatures",
     "Sign and verify documents, create certificates"),
    ("unified-shell-overview-dark.png", "Light and dark",
     "A modern interface in English and German"),
]

WIDTH = 1280
SHOT_H = round(WIDTH * 1000 / 1680)   # the screenshots are 1680 x 1000 (some a few pixels taller)
BAND_H = 64
HOLD_MS = 3000
FADE_FRAMES = 3
FADE_MS = 70
QUALITY = 80

NAVY, PRIMARY, WHITE, MUTED = (0x2a, 0x28, 0x4c), (0x13, 0x73, 0xd9), (255, 255, 255), (0xc9, 0xd6, 0xe8)


def font(name, size):
    return ImageFont.truetype(str(FONTS / name), size)


TITLE_F, TEXT_F = font("Inter-SemiBold.ttf", 22), font("Inter-Regular.ttf", 15)
LOGO = Image.open(ROOT / "src" / "main" / "resources" / "img" / "logo.png").convert("RGBA").resize((40, 40), Image.LANCZOS)


def band(index, title, text):
    img = Image.new("RGB", (WIDTH, BAND_H))
    px = img.load()
    for x in range(WIDTH):  # navy -> primary
        t = x / (WIDTH - 1)
        col = tuple(int(NAVY[i] + (PRIMARY[i] - NAVY[i]) * t) for i in range(3))
        for y in range(BAND_H):
            px[x, y] = col
    img.paste(LOGO, (14, (BAND_H - 40) // 2), LOGO)
    d = ImageDraw.Draw(img)
    d.text((68, 9), title, font=TITLE_F, fill=WHITE)
    d.text((68, 38), text, font=TEXT_F, fill=MUTED)
    n = len(SLIDES)
    r, gap = 4, 14
    x0 = WIDTH - 20 - (n - 1) * gap
    for i in range(n):
        cx, cy = x0 + i * gap, BAND_H // 2
        if i == index:
            d.ellipse((cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1), fill=WHITE)
        else:
            d.ellipse((cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1), outline=MUTED, width=1)
    return img


def slide(index):
    name, title, text = SLIDES[index]
    shot = Image.open(IMG / name).convert("RGB")
    w, h = shot.size
    shot = shot.crop((0, 0, w, min(h, round(w * 1000 / 1680))))  # same aspect for all, cut at the bottom
    shot = shot.resize((WIDTH, SHOT_H), Image.LANCZOS)
    frame = Image.new("RGB", (WIDTH, SHOT_H + BAND_H))
    frame.paste(shot, (0, 0))
    frame.paste(band(index, title, text), (0, SHOT_H))
    return frame


def main():
    slides = [slide(i) for i in range(len(SLIDES))]
    frames, durations = [], []
    for i, current in enumerate(slides):
        frames.append(current)
        durations.append(HOLD_MS)
        following = slides[(i + 1) % len(slides)]
        for k in range(1, FADE_FRAMES + 1):
            frames.append(Image.blend(current, following, k / (FADE_FRAMES + 1)))
            durations.append(FADE_MS)
    frames[0].save(OUT, save_all=True, append_images=frames[1:], duration=durations, loop=0,
                   quality=QUALITY, method=6)
    print(f"{OUT}: {len(slides)} slides, {len(frames)} frames, {OUT.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
