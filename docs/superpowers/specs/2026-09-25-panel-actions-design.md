# Konzept: Erkennbare, stabile Panel-Aktionen in der Unified Shell

## Context

Die Side-Panels (Explorer, Schema, Validation, Transform, FOP, Signature, FundsXML, Help, Favorites, Schema Library) bieten „weiterführende Aktionen“ an: XSD-Dokumentation erzeugen, Sample-XML, Flatten, Schema-Analyse, Transform/Validate aus dem Explorer, Schematron-Tools, Debug-XSLT usw. Zwei Probleme:

1. **Schlecht erkennbar / schwer auffindbar.** Jedes Panel löst das anders: Icon-Only-Reihen ohne Text (Schema, Schema Library), abgeschnittene Buttons „▶ T…“ (Explorer), unscheinbare „Change“-Hyperlinks (Validation/Transform/FOP/Signature), Aktionen in ⋮-Overflow-Menüs versteckt (Validation: Schematron-Tools; Transform: Debug/Batch/Statistik; Explorer: Open file/Clear recent), gefüllte Buttons (FundsXML/Help/Signature). Es gibt keinen gemeinsamen Baustein; acht Panels haben je eine eigene private Button-Fabrik, `sourceRow()` ist viermal kopiert. Der Style Guide sagt zu Panel-Aktionen nichts.
2. **Größe springt beim Klicken/Auswählen.** Ursache verifiziert: die Legacy-Theme-Sheets definieren `.button:pressed` und `.button:default:pressed` mit `-fx-background-insets: 1 0 0 0; -fx-padding: 6px 15px 4px 15px` (`fxt-theme.css:558/636`, `light-theme.css:158/195`, `dark-theme.css:192/229`). Keine Shell-Klasse (`fxt-tool-button`, `fxt-sp-action`, `fxt-primary-button`, `fxt-secondary-button`, `fxt-quick-card`, `fxt-tool-card`) überschreibt `:pressed`; Klasse+Pseudoklasse gewinnt, also springt jeder Panel-Button beim Drücken um 6–22 px (Icon-Reihen re-wrappen sogar). Zusätzlich: `.fxt-seg:selected` (`unified-shell.css:1773`) und `.fxt-sig-nav:selected` (`:1990`) schalten auf **bold** → Text-Reflow; `.fxt-tool-button` erbt bold + 1 px Rahmen vom Legacy-`.button`.

**Entscheidungen mit dem User (2026-09-25):** Zielmuster = beschriftete Aktionsliste (Icon + Text, volle Breite, unter Sektionsüberschrift). Umfang = alle Panels; ⋮-Menüs behalten nur Schalter, Aktionen wandern in die sichtbare Liste; „Change“-Links werden sichtbare Ghost-Buttons.

**Ergebnis:** Ein wiederverwendbarer Baustein, ein Look für alle Panels, kein Layout-Sprung in irgendeinem Zustand, dokumentiert im Style Guide und per Test-Ratchet abgesichert.

---

## 1. Designprinzipien

- **Aktion = Zeile mit Icon + Text.** Nie Icon-only für weiterführende Aktionen. Tooltip nur, wenn er Zusatzinfo bringt.
- **Gruppierung unter Sektionsüberschrift** („TOOLS“, „DOCS & RESOURCES“, …) über den vorhandenen `SidePanelLayout.sectionHeader` (klappbar).
- **Zustände ändern nur Farbe.** Hover = subtiler Hintergrund, Pressed = etwas dunkler, Focus = Ring über zweite Background-Füllung (kein Border), Disabled = Opacity. Padding/Höhe/Font-Weight sind in allen Zuständen identisch.
- **Feste Zeilenhöhe 28 px**, Ellipsis statt Umbruch.
- **Primäraktion** („Run Validation“, „Generate PDF“) bleibt der gefüllte Primary-Button; er wird nur größenstabil.
- **Ausnahme Schema Library:** die drei CRUD-Toolbars (Add/Edit/Remove/Enable/… je Tab, 16 Buttons, selektionsgebunden) bleiben kompakt als Icon-Toolbar, werden aber größenstabil (feste 26×26 px, `fxt-sp-action`). Sie sind Listen-Bearbeitung, keine weiterführenden Aktionen.

---

## 2. Gemeinsamer Baustein

Package `org.fxt.freexmltoolkit.controls.shell.editor` (neben `SidePanelLayout.java`, package-private wie die Panels).

**`PanelAction`** (Record + Wither, unveränderlich)
```java
record PanelAction(String id, String iconLiteral, String label, String tooltip, Runnable onAction,
                   ObservableValue<Boolean> disabledWhen, ObservableValue<Boolean> visibleWhen, boolean primary)
static PanelAction of(String id, String iconLiteral, String label, Runnable onAction)
PanelAction tooltip(String) / disabledWhen(...) / visibleWhen(...) / primary()
```

**`PanelActionList extends VBox`** (Style-Klasse `fxt-action-list`)
```java
PanelActionList(PanelAction... actions)
Button add(PanelAction action)        // gibt den Button zurück (FopPanel hält Felder, HelpPanel tauscht Sponsor-Icon)
Button button(String id)
List<String> labels()                 // Anzeigereihenfolge; ersetzt TypeLibraryPanel.toolNames()
static VBox section(String title, boolean collapsed, PanelActionList list)  // Header + Liste via SidePanelLayout.sectionHeader
```
Zeile = `Button(label, IconifyIcon 15px)`, Klassen `fxt-action-row` (+ `fxt-action-row-primary`), `setId`, `maxWidth=MAX`, `CENTER_LEFT`, `mnemonicParsing=false`, `wrapText=false`, `textOverrun=ELLIPSIS`; `disableProperty`/`visible+managed` gebunden, wenn angegeben. Keine Button-Subklasse nötig.

**`SourceRow extends HBox`** (ersetzt die vier identischen `sourceRow()`-Kopien)
```java
SourceRow(String iconLiteral, Label nameLabel, Runnable changeAction, Node... extras)
Button changeButton()                 // Klassen fxt-action-row, fxt-action-row-inline; Icon bi-folder2-open
```
Behält Klasse `fxt-vp-source-row` (Drag-Drop-Glow und Klick-zum-Öffnen bleiben unangetastet).

---

## 3. CSS

**A. Legacy-Sprung beheben (wirkt app-weit, auch Dialoge/Toolbar):** In `fxt-theme.css`, `light-theme.css`, `dark-theme.css` aus `.button:pressed` **und** `.button:default:pressed` die beiden Zeilen `-fx-background-insets: 1 0 0 0;` und `-fx-padding: 6px 15px 4px 15px;` entfernen (6 Rules). Verifiziert: das sind die einzigen `:pressed`-Regeln im gesamten CSS, die Geometrie ändern; nichts hängt davon ab. Farbe bleibt.

**B. Shell-Klassen härten (`unified-shell.css`):**
- `:pressed`-Farbregeln (Tokens statt `#e9ecef`) für `.fxt-tool-button`, `.fxt-sp-action`, `.fxt-primary-button`, `.fxt-secondary-button`, `.fxt-quick-card`, `.fxt-tool-card`.
- `.fxt-tool-button` (`:486`): `-fx-font-weight: normal; -fx-border-width: 0;` (Editor-Toolbar-Variante `:525` ist eigen gescoped, Screenshot prüfen).
- `.fxt-seg:selected` (`:1773`) und `.fxt-sig-nav:selected` (`:1990`): `font-weight: bold` streichen (Primary-Textfarbe + Hintergrund bleiben als Selektionssignal).
- `.fxt-vp-change` (`:1738`) entfernen, sobald der letzte Hyperlink weg ist.

**C. Neuer Block `.fxt-action-*`** (nach `.fxt-sp-section-header`, ~`:2820`):
```css
.fxt-action-list { -fx-padding: 0 8 4 8; -fx-spacing: 1; }
.fxt-action-row { transparent; insets 0; radius 6; border 0; padding 0 8 0 10;
                  pref/min/max-height 28; center-left; graphic-text-gap 8;
                  12.5px normal; -fxt-text-primary; ellipsis; cursor hand }
.fxt-action-row .iconify-icon { -fxt-text-secondary }
.fxt-action-row:hover { bg -fxt-bg-subtle }  :hover .iconify-icon { -fxt-primary }
.fxt-action-row:armed, :pressed { bg derive(-fxt-bg-subtle,-8%); insets 0; padding wie Basis }
.fxt-action-row:focused { bg -fxt-primary, -fxt-bg-surface; insets 0, 1 }   /* Ring ohne Border */
.fxt-action-row:disabled { opacity .45 }
.fxt-action-row-primary { bg -fxt-primary; text -fxt-on-primary } (+hover/pressed derive)
.fxt-action-row-inline { height 22; padding 0 6; 11.5px; text -fxt-primary }   /* „Change“, „Add parameter“ */
```
Jede Zustandsregel wiederholt Padding/Insets identisch; keine Regel fasst Font-Weight an.

---

## 4. Migration pro Panel

| Panel | Ersetzt | Neu | ⋮ behält | Gelöscht |
|---|---|---|---|---|
| **TypeLibraryPanel** (Schema) | FlowPane `#schema-tools`, 7 Icon-Buttons | Sektion **TOOLS** (offen) über dem Filter; Ids `schema-tool-*` bleiben, Labels = bisherige Tooltips | – | `toolButton()`; `toolNames()` → `labels()` |
| **ValidationPanel** | 3× sourceRow; PROBLEMS-Header-Icons (Excel, Report); ⋮-Aktionen | **SCHEMATRON TOOLS** (eingeklappt): Rule Templates, Tester, Rule Builder, Check Rules, Validation Report (`disabledWhen` kein Report), Documentation; dazu Export problems to Excel (`disabledWhen` leer), Open last batch report, Validate against FundsXML (bedingt). „Run Validation“ bleibt Primary im Run-Box | „Validate while typing“ | `sourceRow()`, `CollapsibleSection.trailing` |
| **TransformPanel** | 2× sourceRow; „Add parameter“-Link → Inline-Row; ⋮-Aktionen | **TOOLS** (eingeklappt, nach Run-Box): Debug XSLT…, Batch Transform…, Execution Statistics | Live preview, Watch stylesheet, Profile, Trace, Auto-open | `sourceRow()`; XPath/XQuery-Inline-Buttons → `fxt-secondary-button` |
| **FopPanel** | 2× sourceRow; Preview/Open PDF | **RESULT**-Liste: Preview PDF, Open PDF (Felder via `add()`-Rückgabe) | – | `sourceRow()`, `toolButton()` |
| **SignaturePanel** | 2× sourceRow; „Validate (Details)“ | Eine Liste: `Validate Signature` als `primary()`-Row + `Validate (Details)`; `fxt-sig-nav` nur CSS-Fix | – | `sourceRow()`, `toolButton()` |
| **FundsXmlPanel** | 8 gefüllte Buttons, Titel-Labels | `PanelActionList.section`: MANAGEMENT / VALIDATE / DOCS & RESOURCES | – | `button()`, `sectionTitle()` |
| **HelpPanel** | 11 Buttons | PROJECT (GitHub, Sponsor, About, Shortcuts, Report a Problem…) / DOCUMENTATION (3) / UPDATES (2 + Statuslabel) | – | `button()` |
| **FavoritesActivityPanel** | Add current, Manage… | 2-zeilige Liste über dem Suchfeld | – | Inline-Konstruktion |
| **SchemaLibraryPanel** | 3 Icon-Toolbars | **bleibt kompakt**: `fxt-tool-button` → `fxt-sp-action` + feste 26×26 px; Tooltips bleiben | – | – |
| **ExplorerPanel** | 3 Leisten (Picker + „T…“-Button), ⋮ | siehe unten | ⋮ entfällt (keine Schalter mehr) | `flatAction()` bleibt für die 3 Header-Icons (VS-Code-Muster, jetzt stabil) |

**Explorer-Umbau** (Leisten ersetzt durch eine klappbare Sektion **TOOLS**, Picker und Aktion bleiben benachbart, nichts wird abgeschnitten):
```
▾ TOOLS
  📄 stylesheet.xsl            ▾    ← MenuButton #explorer-stylesheet, volle Breite, flach
     ▶ Transform selected file(s)   ← Row #explorer-transform
  ▦ rules.sch                  ▾    ← #explorer-schematron
     ▶ Validate with Schematron     ← Row #explorer-validate
  ⬡ schema.xsd                 ▾    ← #explorer-xsd (Rebinding validiert automatisch, keine Row)
  ────
  📂 Open file…   🧹 Clear recent   ← Rows aus dem früheren ⋮
```
Private Hilfe `pickerBlock(MenuButton, PanelAction)` → `VBox(picker, PanelActionList(run))`; `FileDropSupport.install` bleibt auf dem MenuButton. Ids `#explorer-transform`/`#explorer-validate` bleiben (Schematron-Tests).

---

## 5. Umsetzungsreihenfolge (App läuft nach jedem Schritt)

0. **Konzept als Spec ablegen:** `docs/superpowers/specs/2026-09-25-panel-actions-design.md` (Inhalt dieses Plans), committen.
1. **CSS-Stabilität** (3.A + 3.B) + `ShellCssStabilityTest` (reiner JUnit-Ratchet, Geschwister von `SemanticColorGuardTest`). Behebt das Springen sofort für alle bestehenden Buttons.
2. **Baustein + erster Konsument:** `PanelAction`, `PanelActionList`, `SourceRow`, CSS-Block 3.C, `PanelActionListTest`, `SourceRowTest`; Migration `TypeLibraryPanel` (`TypeLibraryPanelTest` läuft unverändert über `toolNames()`-Delegation).
3. **Validation + Transform:** SourceRow, ⋮-Split, TOOLS-Listen; `ValidationPanelTest:226` (`…InOverflowMenu` → `…AsActionRows`); `docs/unified-shell.md` ⋮-Abschnitte (~597, ~1053) und Sources (~927).
4. **FOP, Signature, FundsXML, Help, Favorites:** Massenmigration, Fabriken löschen, Test-Lookups auf `fxt-tool-button` prüfen.
5. **Explorer:** Umbau, `ExplorerPanelTest:168` anpassen, Doku ~664–750.
6. **Schema Library kompakt-fix, `STYLE_GUIDE.jsonc`** (neuer Abschnitt `components.panelActions` + CSS-Klassen + Pattern-Snippet), **Screenshots** `xvfb-run -a -s "-screen 0 1680x1050x24" ./gradlew docScreenshots`.

Nach jedem Schritt commit + push (Memory: feedback_auto_commit_push).

---

## 6. Tests

- **`ShellCssStabilityTest`** (pure JUnit): scannt `src/main/resources/css/*.css`; schlägt fehl, wenn ein `:pressed`/`:hover`/`:focused`/`:armed`/`:selected`-Block `-fx-padding`, `-fx-background-insets` (≠0), `-fx-border-width` oder `-fx-font-weight` deklariert, außer der Wert ist identisch mit der Basisregel derselben Klasse. Das ist der Ratchet gegen Rückfall.
- **`PanelActionListTest`** (TestFX, Sheets in App-Reihenfolge inkl. Legacy-Theme): Labels + Icon vorhanden, `labels()`-Reihenfolge, Klick feuert Runnable, `disabledWhen`/`visibleWhen` (visible **und** managed), `primary()`-Klasse, kein Tooltip ohne Angabe; **Geometrie:** `layoutBounds`, `padding`, `font` vor/nach `pseudoClassStateChanged(pressed/hover/focused)` + `applyCss(); layout()` identisch, zusätzlich für `fxt-primary-button` und `fxt-tool-button` (beweist Schritt 1).
- **`SourceRowTest`**: `changeButton()` feuert, Extras vor Change, Klasse `fxt-vp-source-row`.
- Angepasst: `TypeLibraryPanelTest`, `ValidationPanelTest`, `ExplorerPanelTest`, ggf. Fop/Signature/Help/FundsXml-Tests.
- Bekannte Abhängigkeiten: `UnifiedShellExplorerSchematronTest:65`, `ExplorerPanelSchematronTest:178` (Ids bleiben).

---

## 7. Risiken

- **Tab-Reihenfolge** wird länger (jede Row fokussierbar) – gewollter Accessibility-Gewinn; Focus-Ring über Füllung, kein Layout-Shift.
- **Lange Labels:** längstes ist „Generate Sample XML (Advanced)…“, passt bei 250 px; Ellipsis, nie Wrap.
- **Dark Mode:** nur `-fxt-*`-Tokens; `derive(-fxt-bg-subtle,-8%)` auf dunklem Grund per Screenshot prüfen.
- **`.fxt-tool-button`-Reset** (bold/border) trifft auch Nicht-Panel-Nutzer (QueryConsole u. a., siehe CSS-Kommentar `:480`) → visueller Durchgang nach Schritt 1.
- **TestFX-Laufzeit** (`forkEvery=1`): nur zwei neue UI-Testklassen; Ratchet ist reiner JUnit.
- **Screenshots** in `docs/img` veralten ab Schritt 3; einmal am Ende regenerieren.

---

## 8. Verifikation (End-to-End)

1. `./gradlew test --tests "org.fxt.freexmltoolkit.controls.theme.ShellCssStabilityTest"` und die neuen Editor-Tests grün; `IconifyIconCoverageTest` und `SemanticColorGuardTest` grün.
2. App unter Xvfb starten (`./gradlew run`), Screenshots von Explorer, Schema, Validation, Transform, FundsXML in Light und Dark aufnehmen und lesen: Aktionen als beschriftete Zeilen sichtbar, ⋮ nur noch Schalter, „Change“ als Button.
3. Manuell/TestFX: Button gedrückt halten → `layoutBounds` unverändert (Test in `PanelActionListTest`); Segment-Toggle wechseln → keine Textverschiebung.
4. `docScreenshots` regenerieren, Doku-Bilder sichten, `docs/unified-shell.md` und `STYLE_GUIDE.jsonc` aktualisiert.
