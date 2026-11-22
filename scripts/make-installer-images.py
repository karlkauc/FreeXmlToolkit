"""Draws release/win-banner.png (493x58) and release/win-dialog.png (493x312) for the Windows
installer (see release/README_WINDOWS_IMAGES.md): logo suite S1 (navy ground, the symbol, orange as the
one accent), Inter. Run scripts/make-app-icons.py first: it draws img/logo-symbol.png.
WiX writes black text at the top left of the banner and right of x=180 on the dialog image, so
those areas stay light. Usage: python3 scripts/make-installer-images.py [output-folder]
Needs Pillow."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
ROOT = str(Path(__file__).resolve().parent.parent)
OUT = sys.argv[1] if len(sys.argv) > 1 else ROOT + "/release"
FONTS = ROOT + "/src/main/resources/css/fonts/"
K = 3  # supersampling

def font(name, size):
    return ImageFont.truetype(FONTS + name, size * K)

def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

def hexc(h):
    h = h.lstrip('#'); return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))

NAVY, PRIMARY, ACCENT = hexc('2a284c'), hexc('1373d9'), hexc('f08c2e')
CANVAS, WHITE, MUTED = hexc('f5f7fa'), (255, 255, 255), hexc('c9d6e8')
logo = Image.open(ROOT + "/src/main/resources/img/logo-symbol.png").convert("RGBA")

def gradient(w, h, stops, vertical=True):
    img = Image.new("RGB", (w, h))
    px = img.load()
    n = h if vertical else w
    for i in range(n):
        t = i / max(1, n - 1)
        # piecewise over stops [(pos, color)]
        for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
            if p0 <= t <= p1:
                col = lerp(c0, c1, (t - p0) / max(1e-6, p1 - p0)); break
        if vertical:
            for x in range(w): px[x, i] = col
        else:
            for y in range(h): px[i, y] = col
    return img

def glyphs(img, box, color, size, rows):
    """Draws faint text on a transparent layer and blends it in (text drawn straight onto an RGB
    image ignores the alpha value)."""
    f = font("Inter-SemiBold.ttf", size)
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    x0, y0, x1, y1 = box
    for (fx, fy, text) in rows:
        ld.text((x0 + fx * (x1 - x0), y0 + fy * (y1 - y0)), text, font=f, fill=color)
    base = img.convert("RGBA")
    base.alpha_composite(layer)
    img.paste(base.convert("RGB"))

# ---------------- dialog 493x312 ----------------
W, H = 493 * K, 312 * K
STRIP = 164 * K
dlg = Image.new("RGB", (W, H), WHITE)
# right side: white fading to the app canvas at the bottom
dlg.paste(gradient(W - STRIP, H, [(0, WHITE), (0.55, WHITE), (1, CANVAS)]), (STRIP, 0))
# left strip: navy (activity rail) to primary blue, diagonal feel via two gradients
strip = gradient(STRIP, H, [(0, NAVY), (1, NAVY)])
dlg.paste(strip, (0, 0))
d = ImageDraw.Draw(dlg, "RGBA")
# faint code glyphs in the strip
glyphs(dlg, (0, 0, STRIP, H), (255, 255, 255, 26), 26,
       [(0.06, 0.70, "</>"), (0.52, 0.80, "<x/>"), (0.10, 0.88, "{ }"), (0.58, 0.62, "</>")])
# thin accent line between strip and text area
d.rectangle([STRIP - 2 * K, 0, STRIP, H], fill=ACCENT + (255,))
# the symbol straight on the navy strip, as on the app icon
size = 92 * K
lg = logo.resize((size, size), Image.LANCZOS)
cx, top = STRIP // 2, 46 * K
dlg.paste(lg, (cx - size // 2, top), lg)
# product name + tagline
name_f, tag_f = font("Inter-SemiBold.ttf", 15), font("Inter-Regular.ttf", 9)
# the name as the wordmark draws it: "Xml" in light blue
parts = [("Free", (255, 255, 255, 255)), ("Xml", hexc('4c9bf5') + (255,)), ("Toolkit", (255, 255, 255, 255))]
x = cx - sum(d.textlength(t, font=name_f) for t, _ in parts) / 2
for text, col in parts:
    d.text((x, top + size + 16 * K), text, font=name_f, fill=col)
    x += d.textlength(text, font=name_f)
tag = "XML · XSD · XSLT · XQuery"
d.text((cx - d.textlength(tag, font=tag_f) / 2, top + size + 38 * K), tag, font=tag_f, fill=(255, 255, 255, 190))
dlg = dlg.resize((493, 312), Image.LANCZOS)
dlg.save(OUT + "/win-dialog.png")

# ---------------- banner 493x58 ----------------
W, H = 493 * K, 58 * K
ban = gradient(W, H, [(0, WHITE), (0.62, WHITE), (1, hexc('e8f1fc'))], vertical=False)
d = ImageDraw.Draw(ban, "RGBA")
# a slanted brand band at the right end
band_x = 393 * K
d.polygon([(band_x, 0), (W, 0), (W, H), (band_x - 22 * K, H)], fill=PRIMARY + (255,))
band = gradient(W - band_x + 22 * K, H, [(0, NAVY), (1, NAVY)], vertical=False).convert("RGBA")
mask = Image.new("L", band.size, 0)
ImageDraw.Draw(mask).polygon([(22 * K, 0), (band.size[0], 0), (band.size[0], H), (0, H)], fill=255)
ban.paste(band, (band_x - 22 * K, 0), mask)
d.polygon([(band_x - 8 * K, 0), (band_x - 4 * K, 0), (band_x - 26 * K, H), (band_x - 30 * K, H)], fill=ACCENT + (255,))
glyphs(ban, (band_x, 0, W, H), (255, 255, 255, 34), 16, [(0.05, 0.08, "</>"), (0.08, 0.55, "<x/>")])
size = 44 * K
lg = logo.resize((size, size), Image.LANCZOS)
ban.paste(lg, (W - size - 12 * K, (H - size) // 2), lg)
ban = ban.resize((493, 58), Image.LANCZOS)
ban.save(OUT + "/win-banner.png")
print("ok")
