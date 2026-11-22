# Logo suite S1

The brand marks of FreeXmlToolkit: two tag brackets, blue and orange, form the X of XML.

Open [`index.html`](index.html) in a browser for the full guidelines: every mark and lockup, clear
space and minimum sizes, colours, typography, examples and where each file is used.

| Files | Use |
|---|---|
| `app-icon.svg`, `app-icon-light.svg`, `app-icon-circle.svg`, `app-icon-mono-black.svg` | App icon: the symbol on a navy rounded square; light, circle (avatars) and one-colour versions |
| `symbol-{color,on-dark,black,white}.svg` | The symbol alone |
| `fxt-{color,on-dark,black,white}.svg` | Short mark for the command line (`fxt-cli`) |
| `wordmark-{color,on-dark,black,white}.svg` | "FreeXmlToolkit" with "Xml" in blue |
| `lockup-horizontal-*.svg`, `lockup-stacked-*.svg`, `lockup-claim-*.svg` | Symbol and wordmark together; with the claim "Your Complete XML Workstation" |
| `examples/` | Screens used by `index.html` (splash, documentation header) |

Colours: navy `#2a284c`, blue `#1373d9`, light blue `#4c9bf5` (on dark), orange `#f08c2e`, white.
Fonts: Inter Bold (wordmark), JetBrains Mono Bold (short mark `fxt`). Keep a clear space of 25 % of
the symbol height around it; do not distort, recolour, rotate or swap the two colours.

Every raster icon of the application and the installers (Windows ICO, macOS ICNS, Linux PNG,
window icons, splash) is drawn from this geometry by `scripts/make-app-icons.py`; the Windows
installer images by `scripts/make-installer-images.py`. Run both after a change.
