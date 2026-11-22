"""Draws every application icon from the logo geometry of logo suite S1 (release/brand/*.svg):
two tag brackets, blue and orange, forming the X of XML, on a navy rounded square.

Writes
  src/main/resources/img/logo.png, logo-{16..256}.png   window / taskbar icons (JavaFX stage)
  src/main/resources/img/logo-macos.png                  Dock icon at runtime (Apple icon grid)
  src/main/resources/img/logo-symbol.png                 symbol without background (splash)
  release/logo.ico, update-helper/icon/logo.ico          Windows (16-256 px)
  release/logo.icns                                      macOS bundle (16-1024 px, Apple icon grid)
  packaging/linux/share/icons/hicolor/<n>x<n>/apps/...   Linux (16-512 px)
  src/main/resources/xsdDocumentation/assets/logo.png    header of generated schema documentation

Usage: python3 scripts/make-app-icons.py      Needs Pillow (ICNS writing needs Pillow >= 9)."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
NAVY, BLUE, LIGHT_BLUE, ORANGE = "#2a284c", "#1373d9", "#4c9bf5", "#f08c2e"

# The symbol in the 512 px space of app-icon.svg: four strokes with round ends, 64 px wide.
STROKES = [((118, 122), (214, 256), "left"), ((214, 256), (118, 390), "left"),
           ((394, 122), (298, 256), "right"), ((298, 256), (394, 390), "right")]
RADIUS = 32
SS = 8  # supersampling


def capsule(draw, p0, p1, r, fill):
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    length = (dx * dx + dy * dy) ** 0.5
    nx, ny = -dy / length * r, dx / length * r
    draw.polygon([(x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny)], fill=fill)
    for x, y in (p0, p1):
        draw.ellipse([x - r, y - r, x + r, y + r], fill=fill)


def render(size, background=True, left=LIGHT_BLUE, right=ORANGE, grid=False):
    """The app icon at `size` px. background=False draws the symbol alone on transparency.
    grid=True follows the macOS icon grid: an 824/1024 rounded square with a soft shadow."""
    big = size * SS
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    if grid:
        inset, box = big * 100 / 1024, big * 824 / 1024
        shadow = Image.new("RGBA", (big, big), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle(
            [inset, inset + big * 12 / 1024, inset + box, inset + box + big * 12 / 1024],
            radius=box * 116 / 512, fill=(0, 0, 0, 90))
        img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(big * 14 / 1024)))
    else:
        inset, box = 0, big
    scale = box / 512
    draw = ImageDraw.Draw(img)
    if background:
        draw.rounded_rectangle([inset, inset, inset + box - 1, inset + box - 1], radius=116 * scale, fill=NAVY)
        sym_scale, ox, oy = scale, inset, inset
    else:
        # the symbol's own bounding box (86..426 x 90..422) fills the image
        sym_scale = big / 340
        ox, oy = -86 * sym_scale, -90 * sym_scale + (340 - 332) / 2 * sym_scale
    for p0, p1, side in STROKES:
        capsule(draw, (ox + p0[0] * sym_scale, oy + p0[1] * sym_scale),
                (ox + p1[0] * sym_scale, oy + p1[1] * sym_scale), RADIUS * sym_scale,
                left if side == "left" else right)
    return img.resize((size, size), Image.LANCZOS)


def save_png(img, path):
    path = ROOT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, optimize=True)


def main():
    img_dir = "src/main/resources/img/"
    save_png(render(512), img_dir + "logo.png")
    for s in (16, 24, 32, 48, 64, 128, 256):
        save_png(render(s), f"{img_dir}logo-{s}.png")
    save_png(render(1024, grid=True), img_dir + "logo-macos.png")
    save_png(render(512, background=False), img_dir + "logo-symbol.png")

    # schema documentation header: shown at 32 px, kept sharp on 2x screens
    save_png(render(64), "src/main/resources/xsdDocumentation/assets/logo.png")

    ico_sizes = [16, 20, 24, 32, 40, 48, 64, 128, 256]
    ico_images = [render(s) for s in ico_sizes]
    for target in ("release/logo.ico", "update-helper/icon/logo.ico"):
        ico_images[-1].save(ROOT / target, format="ICO", sizes=[(s, s) for s in ico_sizes],
                            append_images=ico_images[:-1])

    icns_sizes = [16, 32, 64, 128, 256, 512, 1024]
    icns_images = [render(s, grid=True) for s in icns_sizes]
    icns_images[-1].save(ROOT / "release/logo.icns", format="ICNS", append_images=icns_images[:-1])

    for s in (16, 24, 32, 48, 64, 128, 256, 512):
        save_png(render(s), f"packaging/linux/share/icons/hicolor/{s}x{s}/apps/io.github.karlkauc.FreeXmlToolkit.png")
    print("ok")


if __name__ == "__main__":
    main()
