# Logo suite S1

The brand marks of FreeXmlToolkit: two tag brackets, blue and orange, form the X of XML.

| File | Use |
|---|---|
| `app-icon.svg` | App icon: the symbol on a navy rounded square |
| `symbol-color.svg`, `symbol-on-dark.svg` | The symbol alone, on light and on dark grounds |
| `wordmark-on-dark.svg` | "FreeXmlToolkit" with "Xml" in blue (documentation header) |
| `lockup-horizontal-color.svg`, `lockup-horizontal-on-dark.svg` | Symbol and wordmark side by side |

Colours: navy `#2a284c`, blue `#1373d9`, light blue `#4c9bf5` (on dark), orange `#f08c2e`, white.
Fonts: Inter Bold (wordmark), JetBrains Mono Bold (short mark `fxt`). Keep a clear space of 25 % of
the symbol height around it; do not distort, recolour, rotate or swap the two colours.

Every raster icon of the application and the installers (Windows ICO, macOS ICNS, Linux PNG,
window icons, splash) is drawn from this geometry by `scripts/make-app-icons.py`; the Windows
installer images by `scripts/make-installer-images.py`. Run both after a change.
