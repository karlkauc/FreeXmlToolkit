# Konzept: Workflow-Farben und einheitliches Branding der Unified Shell

## Context

Die Unified Shell ist farblich fast monochrom: die Activity Bar ist grau auf Navy, jeder
Primär-Button ist Brand-Blau, und alle Side-Panel-Aktionszeilen sowie die Shell-eigenen Menüs
(XSD-Tree `NodeContextMenu`, Favoriten, Schema Library, Saved Queries im Transform-Panel) haben
ungefärbte Icons. Nur die Editor-Toolbar (`shell.fxml`, Klassen `fxt-tool-*`) und die aus dem
V2-Editor übernommenen Kontextmenüs (Text-Editor, XSD-Diagramm, XML-/JSON-Grid) färben Aktionen,
dort aber widersprüchlich:

| Aktion | Text-Editor | XSD-Diagramm | XML-/JSON-Grid |
|---|---|---|---|
| Cut | DANGER | ACCENT | ACCENT |
| Copy | PRIMARY | NEUTRAL | NEUTRAL |
| Paste | SUCCESS | SUCCESS | NEUTRAL |
| Copy XPath | WARNING | WARNING | PURPLE |
| Move Up/Down | – | SUCCESS/DANGER | NEUTRAL |
| Duplicate | – | TEAL | TEAL |
| Format | TEAL (XML) / INFO (JSON) | – | – |

Die Welcome-Seite besitzt in `EditorWelcomePane.categoryColor()` ein eigenes Kategorie-Farbset
(acht Hex-Werte), das nirgends sonst vorkommt. Zwei Paletten existieren parallel:
`SemanticColors` (Bootstrap-Hex, kein Dark-Modus) und die themefähigen `-fxt-*`-Tokens in
`design-tokens.css` (Quelle der Wahrheit laut `STYLE_GUIDE.jsonc`).

**Ziel:** Ein User erkennt den aktiven Workflow (Validation, Transform, Schema, …) auf einen Blick
und findet Aktionen schnell, weil dieselbe Farbe überall dasselbe bedeutet. Die gesamte App bleibt
im Thunderbird-Supernova-Brand (Navy-Chrome, Blau, Orange).

**Entscheidungen mit dem User (2026-09-26):**

- **D1 Geschichtetes Konzept.** Jede Activity erhält eine Workflow-Akzentfarbe (Rail, Panel-Header,
  Primär-Button, Toolbar-Gruppe, Status-Badge). Darunter liegt eine einheitliche Aktions-Semantik
  für alle Menüs und Aktionszeilen. Beides innerhalb des Brand-Rahmens.
- **D2 (revidiert 2026-09-26 nach Sichtung in der App):** Jede Activity bekommt eine **eigene**
  Familie; nur Help und Settings bleiben neutral. Ursprünglich teilten Explorer/Search/Favorites
  und Schema/Schema Library je eine Farbe — auf der Rail nicht unterscheidbar. Neu: Search =
  Indigo, Favorites = Amber, Schema Library = Grape. Zehn Farbfamilien plus Neutral.
- **D3 Rail-Auswahl als Indikator** (Stil B: 3-px-Balken + getöntes Icon auf `chrome/rail/item-bg`).
  Entschieden 2026-09-26; die gefüllte Pille (Stil A) bleibt nur als verworfene Variante auf dem Sheet.
- **D4 Validate-Toolbar-Button in Validation-Grün** (`validation/fill`) statt Brand-Blau. Entschieden 2026-09-26.
- **D6 Transform bleibt Brand-Orange** (kein Teal-Fallback). Entschieden 2026-09-26.
- **D7 Der Dark-`fill`-Slot (z. B. `#1f6feb`) gilt nur für Workflow-Buttons.** Der Brand-Primär-Button
  behält `-fxt-primary` (Dark `#4c9bf5`). Entschieden 2026-09-26.
- **D5 TEAL und INDIGO** werden für Aktionen nicht mehr verwendet (nur noch Workflow/Dateityp).
- **Deliverable dieser Runde:** Figma-Seite `05 · Workflow Colours & Branding` in der Datei
  `FreeXmlToolkit — UI Modernization` (Key `oqJVcInD6RgKaQ4dYmMWYh`) plus diese Spec.
  **Keine Code-Änderung.** Abschnitt 7 listet die Stellen für die spätere Umsetzung.

---

## 1. Workflow-Palette

### 1.1 Zuordnung

| Familie | Activities | Farbton | Begründung |
|---|---|---|---|
| `workspace` | Explorer | Brand-Blau | der Arbeitsplatz; Default-Ansicht bleibt wie heute |
| `search` | Search | Indigo | Blau-Nachbar mit eigenem Ton, unterscheidbar von Explorer-Blau und Schema-Violett |
| `favorites` | Favorites | Amber | passt zum gesetzten Stern; weit weg von Transform-Orange (kühleres Gelb) |
| `validation` | Validation | Grün | Welcome-Kategorie ist bereits `#2f9e44`; Check-Semantik; Success-Tokens existieren |
| `transform` | Transform | Brand-Akzent Orange | Welcome „Transformation“ `#f08c00`, Legacy-XSLT `#e27429`; der Brand-Akzent bekommt einen Besitzer (Hero-Workflow) |
| `schema` | Schema | Violett | „Struktur = violett“ existiert schon (Cardinality, Schema-Toolbar-Gruppe) |
| `schema-library` | Schema Library | Grape | Geschwister von Schema, deshalb im Violett-Magenta-Bereich, aber klar heller und rötlicher |
| `pdf` | PDF / FOP | Rosé | PDF-Rot-Anklang ohne Danger-Rot: ein roter „Generate PDF“ wirkt destruktiv |
| `signature` | Signature | Cyan (`-fxt-info`) | „verifiziert“-Konvention; klar getrennt von Validation-Grün |
| `fundsxml` | FundsXML (Extension) | Teal | Finance-nah, aber nicht Validation-Grün; Regel für künftige Extensions ohne eigene Marke |
| `neutral` | Help, Settings | keine | Meta-Activities; Rail-Auswahl in Brand-Blau |

Farbtonabstände auf der Rail (Dark-Werte): Blau 212°, Cyan 187°, Teal 162°, Grün 130°, Orange 29°,
Rosé 335°, Violett 262°. Die nächsten Paare (Cyan/Teal, Teal/Grün) liegen in der Rail nie
nebeneinander und unterscheiden sich in der Helligkeit.

### 1.2 Slots je Familie

Erweitert das vorhandene `-fxt-tool-*`-Triplett (bg/border/fg) um zwei Slots:

| Slot | Verwendung | Kontrastanforderung |
|---|---|---|
| `accent` | Icon-Tint, Rail-Indikator und Rail-Icon, Toolbar-Gruppen-Icon, kleine Punkte | ≥ 3:1 auf der Fläche (Grafik) |
| `fg` | Text auf Fläche (Panel-Header-Caps, Badge-Text) | ≥ 4.5:1 auf Weiß bzw. `#161b22` |
| `fill` | gefüllter Primär-Button / primäre Aktionszeile mit **weißer** Beschriftung | ≥ 4.5:1 zu Weiß |
| `bg` | Tint (Badge-Hintergrund, Aktionszeilen-Hover, Welcome-Icon-Chip) | keine |
| `border` | Badge-/Chip-Rahmen | keine |

### 1.3 Werte

Kontrastangaben sind WCAG-Relativleuchtdichte-Schätzungen (± 0,2). Rail `#2A284C`, Weiß, Dark-Fläche
`#161b22`.

**Light** (Fläche `#ffffff`; die Rail ist immer Navy und nutzt in beiden Themes den Dark-`accent`)

| id | accent | fg (Kontrast auf Weiß) | fill (Weiß darauf) | bg | border |
|---|---|---|---|---|---|
| workspace | `#1373d9` (= primary) | `#126ccc` 5.2:1 | `#1373d9` 4.7:1 | `#eaf2fd` | `#c4dcf8` |
| search | `#4c5fd5` (= indigo) | `#3f51c9` 6.5:1 | `#3f51c9` 6.5:1 | `#eceefc` | `#c5cbf5` |
| favorites | `#e69500` | `#9a6400` 5.0:1 | `#9a6400` 5.0:1 | `#fff4d6` | `#f5dc9b` |
| validation | `#2f9e44` (= success) | `#1f7a35` 5.4:1 (= success-text) | `#1f7a35` 5.4:1 | `#eaf6ee` | `#bfe3cb` |
| transform | `#f08c2e` (= accent) | `#ac560d` 5.1:1 | `#b35a0e` 4.8:1 | `#fdf0e6` | `#f9cfae` |
| schema | `#6f42c1` (= purple) | `#6f42c1` 6.5:1 | `#6f42c1` 6.5:1 | `#f3f0ff` | `#d0bfff` |
| schema-library | `#be4bdb` | `#9c36b5` 5.8:1 | `#9c36b5` 5.8:1 | `#f8f0fc` | `#eebefa` |
| pdf | `#d6336c` | `#c2255c` 5.7:1 | `#c2255c` 5.7:1 | `#fff0f6` | `#fcc2d7` |
| signature | `#1098ad` (= info) | `#0b7285` 5.6:1 | `#0b7285` 5.6:1 | `#e7f6f8` | `#b9e3e9` |
| fundsxml | `#12a594` (= teal) | `#0b7a6e` 5.2:1 | `#0b7a6e` 5.2:1 | `#e6fcf5` | `#a8ecd6` |
| neutral | `#5a6472` | `#5a6472` 6.3:1 | `#5a6472` | `#f2f4f8` | `#dde1e7` |

**Dark** (Fläche `#161b22`, Rail `#2a284c`)

| id | accent (auf Rail / auf Fläche) | fg | fill (Weiß darauf) | bg | border |
|---|---|---|---|---|---|
| workspace | `#4c9bf5` 4.9:1 / 6.1:1 | `#4c9bf5` | `#1f6feb` 4.9:1 | `#14283f` | `#275077` |
| search | `#9775fa` 4.1:1 / 5.1:1 | `#9775fa` | `#5b4fd6` 5.9:1 | `#1e1b45` | `#3d3880` |
| favorites | `#ffd43b` 9.8:1 / 12.1:1 | `#ffd43b` | `#9a6400` 5.0:1 | `#332a10` | `#66541f` |
| validation | `#51cf66` 7.0:1 / 8.7:1 | `#51cf66` | `#238636` 4.6:1 | `#14301e` | `#2b5a3a` |
| transform | `#f59f46` 6.6:1 / 8.3:1 | `#f59f46` | `#b35a0e` 4.8:1 | `#2e2010` | `#5c3f1c` |
| schema | `#b197fc` 5.8:1 / 7.2:1 | `#b197fc` | `#7048e8` 5.6:1 | `#241b3d` | `#463374` |
| schema-library | `#da77f2` 5.3:1 / 6.5:1 | `#da77f2` | `#9c36b5` 5.8:1 | `#2e1a33` | `#5a3566` |
| pdf | `#f783ac` 5.8:1 / 7.3:1 | `#f783ac` | `#d6336c` 4.6:1 | `#33161f` | `#66293d` |
| signature | `#4dd4e8` 8.0:1 / 10:1 | `#4dd4e8` | `#0b7285` 5.6:1 | `#0e2a30` | `#1f4a53` |
| fundsxml | `#38d9a9` 7.5:1 / 9.3:1 | `#38d9a9` | `#0b7a6e` 5.2:1 | `#0f2b26` | `#1f5348` |
| neutral | `#9ba6b3` 4.9:1 / 6.2:1 | `#9ba6b3` | `#5a6472` | `#1c232c` | `#2a323d` |

`fg` ist bei Workspace und Transform bewusst etwas dunkler als `accent`/`fill`, damit Badge-Text auf dem
`bg`-Tint ≥ 4.5:1 erreicht (rechnerisch geprüft: Workspace 4.6, Transform 4.6; alle anderen Familien 4.9–5.8).

**Rail-Konstanten (modusunabhängig):** Rail `#2a284c`, Border `#3b3866`, Idle-Icon `#a5a3c4`
(3.6:1), Hover-Icon `#e9ecf1`, Item-Hover/Selected-Hintergrund `#3b3866`, Indikator 3 px. Auf
`#3b3866` bleiben alle Dark-Accents ≥ 3.7:1.

`workspace/fill` dark `#1f6feb` weicht bewusst vom Dark-Primär `#4c9bf5` ab: Weiß auf `#4c9bf5`
erreicht nur ~2.9:1 (siehe offene Frage 9.4).

---

## 2. Wo die Workflow-Farbe erscheint — und wo nicht

| Fläche | Idle | Hover | Selected / aktiv | Hinweis |
|---|---|---|---|---|
| Activity-Bar-Item | Icon `#a5a3c4`, keine Farbe | Bg `#3b3866`, Icon `#e9ecf1` (unverändert) | Bg `#3b3866` + 3-px-Indikator links in `wf/accent(dark)` + Icon **und** Label in `wf/accent(dark)` | Stil B (empfohlen). Stil A = gefüllte Pille `wf/fill` mit weißem Icon (heutiger Look, umgefärbt) als Variante. Help/Settings: weißes Icon, blauer Indikator. |
| Side-Panel-Header („VALIDATION“) | Caps-Text in `wf/fg` | – | – | Nur der Titeltext; ⋮ und Collapse-Icons bleiben grau. Keine Unterstreichung (der Rail-Indikator trägt die Form). |
| Primär-Button (`.fxt-primary-button`) | `wf/fill`, weiße Beschriftung | `derive(-10%)` | pressed `derive(-18%)` | Einer pro Panel. Sekundär-/Outline-Buttons bleiben neutral. |
| Primäre Aktionszeile (`.fxt-action-row-primary`) | wie Primär-Button | | | |
| Normale Aktionszeilen | Icon = **Aktionsfarbe**, Label text-primary | Bg `wf/bg` (ersetzt `-fxt-bg-subtle`), Icon behält Aktionsfarbe (heute springt es auf Primär, das entfällt) | – | Fokusring bleibt `-fxt-primary` (tastaturweit Brand). |
| Editor-Toolbar-Gruppen | Icon-Tint = Farbe des Workflows, zu dem die Gruppe gehört: Datei-Gruppe workspace-blau; Format/Compare/Template neutral-grau; Transform/Run transform; Schema schema; Validate validation | neutraler Hover (unverändert) | – | Validate wird gefüllt `validation/fill` (grün) statt Primär-Blau: der einzige gefüllte Toolbar-Button zeigt auf die Validation-Activity (D4, Variante). |
| Status-Bar „last run“-Badge | Chip `wf/bg` + `wf/border` + Text `wf/fg`; **Ergebnis-Icon in Semantikfarbe** (success/danger/warning) | – | – | z. B. „Transform · 120 ms“ oranger Chip mit grünem Check; „Validation · 3 errors“ grüner Chip mit rotem ✕. Schema-Indikator behält success/warning-Icons. |
| Welcome-Tool-Karten | Icon-Chip `wf/bg` + Icon `wf/accent` | Karten-Border → `wf/border` | – | Kategorie → Workflow: Validation→validation, Editing/Query/Organization→workspace, Transformation→transform, Tools→schema, Security→signature, Export→pdf. Ersetzt die acht Hex in `categoryColor()`. |
| Datei-Tabs / Dateityp-Icons | **keine** | | | Dateityp-Farben (xml/xsd/xslt) sind ein eigenes System und bleiben. |
| PROBLEMS / OUTPUT / RESULTS-Header | **keine** | | | Graue Caps wie heute; Severity-Chips behalten Semantikfarben. |
| Kontextmenüs | **keine** — nur Aktionsfarben | | | |
| Dialoge, Links, Selektion, Fokusring | **keine** — Brand-Primär | | | |

### Zurückhaltungsregeln

1. Höchstens **ein** gefülltes workflow-farbiges Element pro Side-Panel (die Primäraktion). Alles
   andere im Panel ist Tint, Text oder Icon.
2. Workflow-Farbe lebt auf Chrome, das den Workflow benennt (Rail, Header, Primär, Badge,
   Toolbar-Gruppe); nie auf Fließtext, Listen, Tabellen oder Editoren.
3. Semantische Ergebnisfarben (success/danger/warning) schlagen die Workflow-Farbe, wenn beide
   zutreffen.
4. Explorer (Workspace), Help und Settings nutzen Brand-Blau; die Default-Ansicht der App bleibt unverändert.
5. Nie zwei Workflow-Farbtöne auf einer Fläche, außer als 16-px-Icon-Tints in der Editor-Toolbar.
6. Aktionsfarben erscheinen nie auf Chrome; Workflow-Farben erscheinen nie in Menüs.

---

## 3. Einheitliche Aktions-Farbtabelle

Rollen → vorhandene Tokens (keine neuen Farben): CREATE→SUCCESS, DELETE→DANGER, MODIFY→ACCENT
(orange), NAVIGATE→INFO, STRUCTURE→PURPLE, TOOL→PRIMARY, NEUTRAL→NEUTRAL.

| Verb | Rolle → Token | Löst auf | Begründung |
|---|---|---|---|
| Add / Insert / New | CREATE → SUCCESS | – | erzeugt Inhalt |
| Paste | CREATE → SUCCESS | Text/XSD SUCCESS vs. Grid NEUTRAL | fügt Inhalt ein, gleiche Wirkung wie Add |
| Duplicate | CREATE → SUCCESS | war TEAL | erzeugt eine Kopie; TEAL hatte keine Bedeutung |
| Delete / Remove / Clear | DELETE → DANGER | – | destruktiv |
| Cut | DELETE → DANGER | Text DANGER vs. Grid/XSD ACCENT | entfernt aus dem Dokument; die Zwischenablage ist Nebeneffekt |
| Copy | NEUTRAL | Text PRIMARY vs. Grid NEUTRAL | nicht mutierend; hält Menüs ruhig |
| Copy XPath / Copy Path / Copy Node | NEUTRAL | WARNING vs. PURPLE | dasselbe Verb wie Copy; WARNING suggerierte fälschlich Vorsicht |
| Rename / Edit Value / Comment umschalten | MODIFY → ACCENT | – | In-Place-Änderung von Inhalt |
| Change Type / Edit Cardinality / Attribut setzen | STRUCTURE → PURPLE | Change Type war PRIMARY | ändert die Schema-Form, nicht den Inhalt |
| Move Up / Move Down / Reorder | STRUCTURE → PURPLE | SUCCESS/DANGER vs. NEUTRAL | Grün/Rot suggerierte Add/Delete; es ist strukturell und reversibel |
| Go to Definition / Go to line / Reveal / Open referenced | NAVIGATE → INFO | – | bewegt den Viewport, keine Änderung |
| Find / Replace | NAVIGATE → INFO | war ACCENT | Suche ist Navigation; Replace öffnet aus demselben Dialog |
| Format / Minify / Pretty print / Sort | TOOL → PRIMARY | TEAL vs. INFO; Sort SUCCESS/DANGER | Dokument-Tooling, nicht destruktiv; Blau = „die App arbeitet“ |
| Validate / Well-formed prüfen | SUCCESS | – | das Häkchen-Verb; passt zum Validation-Workflow |
| Run / Execute / Transform / Query (in Menüs) | TOOL → PRIMARY | – | Tooling in Menüs bleibt blau; der Run-Button des Panels trägt stattdessen die Workflow-Füllung |
| Expand / Collapse / Select All / View-Umschalter | NEUTRAL | Select All war PURPLE | nur Ansichtszustand |
| Undo / Redo | NEUTRAL | – | History |
| Open / Save / Save As / Export | TOOL → PRIMARY | Toolbar-Open war WARNING | Dateioperationen gehören zum Workspace (blau); New bleibt CREATE |
| Settings / Configure / Options | NEUTRAL | – | Meta |
| Favorite / Unfavorite | NEUTRAL; gesetzter Stern = WARNING | – | der gelbe Stern ist ein Zustand, keine Workflow-Farbe |

**Schichtung auf einer Aktionszeile:** Icon = Aktions-Token über `SemanticIcon`; Label
text-primary; Hover-Bg `wf/bg`; Primärzeile `wf/fill` mit weißem Icon und Label (Aktionsfarbe
unterdrückt). **Kontextmenüs:** nur Aktions-Token. Heute ungefärbte Shell-Menüs (XSD
`NodeContextMenu`, Favorites, Schema Library, Saved Queries) übernehmen dieselbe Tabelle.

---

## 4. Figma-Seite `05 · Workflow Colours & Branding`

Datei `FreeXmlToolkit — UI Modernization` (Key `oqJVcInD6RgKaQ4dYmMWYh`), Seite `256:4`:
https://www.figma.com/design/oqJVcInD6RgKaQ4dYmMWYh?node-id=256-4

| Frame | Node | Inhalt |
|---|---|---|
| `05.1 Styleguide · Workflow palette` (+ Dark) | `257:2` / `257:460` | Brand-Chips, 8 Familien × 5 Slots mit gebundenen Swatches, Kontraste, Zurückhaltungsregeln, „Wo erscheint sie“-Matrix |
| `05.2 Styleguide · Action rule table` | `258:2` | 7 Rollen, 20 Verben, Schichtungs-Demo, aufgelöste Inkonsistenzen |
| `05.3 Component sheet` (+ Dark) | `260:2` / `261:2` | Activity-Bar-Item (idle/hover/selected B/selected A), Panel-Header, Primär-Button (3 Zustände), Aktionszeile, Kontextmenü, Editor-Toolbar (Validate grün/blau), Status-Badges |
| `05.4 Welcome` (+ Dark) | `263:2` / `263:2319` | Tool-Karten mit Workflow-Chips |
| `05.5 Editor / Explorer` | `263:222` | Workspace-Blau, Toolbar-Tints, Validation-Badge |
| `05.6 Validation` (+ Dark) | `262:2` / `263:2539` | grüner Indikator/Header/Run, Badge |
| `05.7 Transform` | `263:822` | Orange |
| `05.8 Schema` | `263:1402` | Violett, eingefügte Primärzeile „Generate Documentation…“ |
| `05.9 Signature` | `263:1922` | Cyan |

Alle Farben sind an Figma-Variablen der Collection `Color` gebunden (siehe 5.4); Dark-Frames per
explizitem Collection-Modus. Die Screens sind Klone der Seite-03-Screens mit gepatchter Rail
(Navy statt Hellgrau), Header, Primäraktion, Toolbar-Tints und Status-Badge. Der Icon-Store der
Datei (`fxt/icons`, Shared Plugin Data) wurde von 81 auf 121 Bootstrap-Icons erweitert.

---

## 5. Token-Mapping (für die spätere Umsetzung)

### 5.1 CSS (`design-tokens.css`, Light in `.root`, Dark in `.root.fxt-theme-dark`)

```
-fxt-wf-<id>-accent  -fxt-wf-<id>-fg  -fxt-wf-<id>-fill  -fxt-wf-<id>-bg  -fxt-wf-<id>-border
```
für `<id>` ∈ workspace, search, favorites, validation, transform, schema, schema-library, pdf,
signature, fundsxml, neutral (66 Tokens je Modus), plus `-fxt-wf-<id>-rail` (= Dark-accent in beiden Modi) und Aliase
`-fxt-action-create`, `-fxt-action-delete`, `-fxt-action-modify`, `-fxt-action-navigate`,
`-fxt-action-structure`, `-fxt-action-tool`, `-fxt-action-neutral` auf die bestehenden
Semantik-Tokens.

### 5.2 Java (`controls/theme/`)

- `DesignTokens.ColorToken`: Einträge `WF_<ID>_<SLOT>` (40) mit Light/Dark-Werten aus 1.3.
- Neues `enum Workflow { WORKSPACE, VALIDATION, TRANSFORM, SCHEMA, PDF, SIGNATURE, FUNDSXML, NEUTRAL }`
  mit `accent()`, `fg()`, `fill()`, `bg()`, `border()` → `ColorToken` und `cssClass()` → `fxt-wf-<id>`.
- `Activity.workflow()` liefert die Familie (eine je Activity; Help/Settings → NEUTRAL).
- Neues `enum ActionColor { CREATE, DELETE, MODIFY, NAVIGATE, STRUCTURE, TOOL, NEUTRAL }` mit
  `token()` → `ColorToken`; `SemanticIcon.paint(icon, ActionColor)` als Overload.
- `PanelAction` erhält ein optionales Feld `ActionColor color` (Default NEUTRAL).

### 5.3 CSS-Hook

`ActivityBar` setzt `fxt-wf-<id>` auf den Rail-Button, `UnifiedShellView` auf den Side-Panel-Root.
Ein generierter Selektor-Block je Familie, z. B.
```
.fxt-wf-validation .fxt-primary-button,
.fxt-wf-validation .fxt-action-row-primary { -fx-background-color: -fxt-wf-validation-fill; }
.fxt-wf-validation .fxt-side-panel-title  { -fx-text-fill: -fxt-wf-validation-fg; }
.fxt-wf-validation .fxt-action-row:hover  { -fx-background-color: -fxt-wf-validation-bg; }
.fxt-activity-button.fxt-wf-validation:selected .fxt-activity-indicator { -fx-background-color: -fxt-wf-validation-rail; }
```

### 5.4 Figma-Variable ↔ Token

| Figma (Collection `Color`, Modi Light/Dark) | CSS | Java |
|---|---|---|
| `workflow/<id>/accent` | `-fxt-wf-<id>-accent` | `ColorToken.WF_<ID>_ACCENT` |
| `workflow/<id>/fg` | `-fxt-wf-<id>-fg` | `ColorToken.WF_<ID>_FG` |
| `workflow/<id>/fill` | `-fxt-wf-<id>-fill` | `ColorToken.WF_<ID>_FILL` |
| `workflow/<id>/bg` | `-fxt-wf-<id>-bg` | `ColorToken.WF_<ID>_BG` |
| `workflow/<id>/border` | `-fxt-wf-<id>-border` | `ColorToken.WF_<ID>_BORDER` |
| `workflow/<id>/rail` (Alias auf Dark-accent) | `-fxt-wf-<id>-rail` | `Workflow.rail()` |
| `chrome/rail/{bg,border,icon-idle,icon-hover,item-bg}` | bestehende Literale in `unified-shell.css` 17–84 | – |
| `action/{create,…,neutral}` (Aliase) | `-fxt-action-*` | `ActionColor` |

---

## 6. Betroffene Code-Stellen (Umsetzung folgt separat, keine Änderung jetzt)

| Datei | Änderung |
|---|---|
| `src/main/resources/css/design-tokens.css` | 40 wf-Tokens × 2 Modi, 8 rail-Aliase, 7 action-Aliase |
| `src/main/java/org/fxt/freexmltoolkit/controls/theme/DesignTokens.java` | `ColorToken`-Einträge, `Workflow`, `ActionColor` |
| `…/controls/theme/SemanticIcon.java` | `paint(icon, ActionColor)` |
| `…/controls/shell/Activity.java` | `workflow()` |
| `…/controls/shell/ActivityBar.java` | Klasse `fxt-wf-<id>`, Indikator-Node (Stil B) |
| `src/main/resources/css/unified-shell.css` | Rail-Selected (Z. 17–84), Side-Panel-Titel (~134), Primär-Button (~1812), Aktionszeilen (2912–2957), Status-Badge (~340–380, ~1878), `fxt-tool-accent` (~596), Welcome-Icon-Chip (~1306) |
| `…/controls/shell/UnifiedShellView.java` | `fxt-wf-<id>` an Side-Panel-Root; Status-Badge „last run“ |
| `…/controls/shell/editor/PanelAction.java`, `PanelActionList.java`, `SidePanelLayout.java` | `ActionColor` je Zeile; Header |
| `ValidationPanel`, `TransformPanel`, `FopPanel`, `SignaturePanel`, `FundsXmlPanel`, `TypeLibraryPanel`, `SchemaLibraryPanel`, `HelpPanel`, `ExplorerPanel`, `FavoritesActivityPanel` | `ActionColor` je `PanelAction`; Run-Buttons erben `fill` per CSS |
| `…/controls/shell/editor/EditorWelcomePane.java` (Z. 195–206, 428–489) | `categoryColor()` → `Workflow`-Lookup; Tool-Karten-Chips |
| `src/main/resources/pages/shell.fxml` (Z. 93–211) | Toolbar-Klassen: Open → primary, Validate → `fxt-tool-validation` |
| `XmlContextMenuManager`, `ContextMenuManagerV2`, `JsonContextMenuManager`, `XsdContextMenuFactory`, `XmlGridContextMenu`, `JsonGridContextMenu`, `controls/shell/schema/NodeContextMenu`, Menüs in `FavoritesActivityPanel`/`FavoritesManagerView`/`SchemaLibraryPanel`/`TransformPanel` | Icons per `ActionColor` gemäß Abschnitt 3 |
| `util/ContextMenuFactory.java`, `css/context-menu-theme.css` `.menu-icon-*` | toter Code, entfernen |
| `STYLE_GUIDE.jsonc` (Tab-Farben Z. 52–68, `iconColors` Z. 834–858, `panelActions` Z. 920–945) | Workflow-Palette und Aktionstabelle dokumentieren |
| `docs/unified-shell.md`, `docs/img/*` | Beschreibung + Screenshots |
| `src/test/java/…/ShellCssStabilityTest.java`, `SemanticColorGuardTest` | Ratchet für neue Selektoren; keine neuen Inline-Hex |

---

## 7. Migrationsreihenfolge

1. Tokens (CSS + `ColorToken`) — rein additiv, kein sichtbarer Effekt.
2. `Workflow`/`ActionColor`-Enums + `Activity.workflow()`.
3. Rail (Indikator, Klasse) → sofort sichtbarer Workflow-Bezug.
4. Side-Panels (Header, Primär-Button, Aktionszeilen).
5. Editor-Toolbar (Gruppen-Tints, Validate).
6. Menüs (Aktionstabelle), toten Code entfernen.
7. Welcome (Tool-Karten, Kategorie-Farben), Status-Badge.
8. Doku, Style Guide, Screenshots.

---

## 8. Verifikation der Umsetzung (später)

- `SemanticColorGuardTest` und `ShellCssStabilityTest` grün; `IconifyIconCoverageTest` grün.
- Light/Dark-Umschaltung färbt Rail-Indikator, Header, Primär-Button und Badges live um
  (`ThemeManager`-Listener), Screenshot-Vergleich per `docScreenshots`.
- Kontrast-Check: `fg` auf Fläche und Weiß auf `fill` ≥ 4.5:1 in beiden Modi (Testklasse über
  `ColorToken`-Werte rechnen lassen).

---

## 9. Entschiedene Fragen (2026-09-26)

1. **Rail-Auswahl:** Stil B (Indikator). → D3
2. **Validate-Toolbar-Button:** grün. → D4
3. **Transform:** bleibt Brand-Orange; die Schichtung hält MODIFY-Orange (Menü-Icons) und
   Warning-Chips (PROBLEMS) auf anderen Flächen. → D6
4. **Dark-`fill`:** nur für Workflow-Buttons; Brand-Primär-Button unverändert. → D7
5. **Icon-Store:** erledigt, 121 Icons in der Figma-Datei.

Keine offenen Fragen mehr; die Umsetzung kann nach Abschnitt 7 starten.
