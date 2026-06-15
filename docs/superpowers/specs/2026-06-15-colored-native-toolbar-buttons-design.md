# Design: Farbige, native Toolbar-Buttons mit Settings-Steuerung

**Datum:** 2026-06-15
**Status:** Approved (pending user spec review)
**Branch:** main (Feature-Branch wird beim Plan angelegt)

## Ziel

Die Buttons in der Editor-Toolbar (Action Bar: New, Open, Save, …) sollen
mehr wie native Windows-Buttons aussehen (erhaben, mit Rahmen/Fläche) und
**semantisch farbig hinterlegt** sein. Die Darstellung — mit/ohne Text und
Icon-Größe klein/groß — soll über die Settings steuerbar sein.

Aktueller Zustand: Die Toolbar-Buttons (`UnifiedShellView.buildEditorToolbar()`,
Style-Klasse `.fxt-tool-button`) sind flache, transparente Icon-only-Buttons.
Einzige Ausnahme ist der „Validate"-Button (Text + Akzentfarbe).

## Entscheidungen (aus dem Brainstorming)

- **Look:** Native, leicht erhaben — **soft-getönter** Hintergrund in der
  semantischen Farbe (~12 % Farbton) + farbiger Rahmen + farbiges Icon/Text.
  Hover/Pressed vertieft die Farbe. (Nicht: voll gefüllte, grelle Buttons.)
- **Farbe:** Semantisch je Aktion (alle Buttons farbig).
- **Settings:** Zwei getrennte Optionen — Checkbox „Beschriftung anzeigen"
  + Icon-Größe klein/groß.
- **Umfang:** Alle Toolbar-Buttons inkl. Panel-Toggle-Buttons.
- **Defaults:** `toolbar.show.labels = false`, `toolbar.icon.size = small`
  → beim ersten Start ändern sich nur die Farben; Text/Größe wie bisher.

## Komponenten & Änderungen

### 1. Farb-Kategorien (Design-Tokens)

Sechs semantische Kategorien, als CSS-Klassen am Button:
`fxt-tool-primary | fxt-tool-success | fxt-tool-info | fxt-tool-warning |
fxt-tool-danger | fxt-tool-neutral`.

Farb-Zuordnung der bestehenden Buttons:

| Kategorie | Basisfarbe | Buttons |
|-----------|-----------|---------|
| success | `#28a745` (grün) | New |
| primary | `#3b5bdb` (blau) | Open, Save, Save As, Save All, Type Editor, Validate |
| info | `#17a2b8` (cyan) | Format, Minify, Insert Template, Compare, Spreadsheet, Query Console, Transform, Generate Docs |
| warning | `#ffc107` (gelb) | Set Schema |
| neutral | — (grau) | Undo, Redo, Panel-Toggles (links/Inspector) |
| danger | `#dc3545` (rot) | reserviert für zukünftige Delete-/Destruktiv-Aktionen |

Pro Kategorie werden in `css/design-tokens.css` (Light **und** Dark) drei
Tokens definiert: weicher Hintergrund, Rahmenfarbe, Vordergrund (Icon/Text),
z. B. `-fxt-tool-success-bg`, `-fxt-tool-success-border`, `-fxt-tool-success-fg`.
Dark-Theme nutzt dieselben Hues mit angepasster Helligkeit/Deckkraft.

### 2. CSS (`css/unified-shell.css`)

- `.fxt-tool-button` neu: nativer Look — `-fx-border-color`, `-fx-border-radius`,
  `-fx-background-radius`, weicher Hintergrund, Padding, `-fx-cursor: hand`,
  `-fx-graphic-text-gap`. Hover/Pressed-States vertiefen Hintergrund & Rahmen.
- Kategorie-Varianten `.fxt-tool-button.fxt-tool-{primary|success|info|warning|danger|neutral}`
  setzen Hintergrund-/Rahmen-/Icon-/Textfarbe aus den Tokens.
- Größen-Varianten `.fxt-tool-button.fxt-tool-small` / `.fxt-tool-large`
  steuern Padding und Font-Größe des Labels (Icon-Größe wird im Java-Code gesetzt).
- `.fxt-validate-button` wird auf die neue `primary`-Variante zurückgeführt
  (kein Sonderfall mehr, sofern Verhalten identisch bleibt).

### 3. Button-Erzeugung (`UnifiedShellView`)

- `toolButton(...)` erhält zwei neue Parameter: `String label` (Klartext) und
  eine Farb-Kategorie (Enum `ToolColor` oder die CSS-Klassen-Konstante).
- Jeder Aufruf in `buildEditorToolbar()` (und `documentActionButton`) übergibt
  Label + Kategorie.
- Label wird per `setText(label)` gesetzt, aber nur angezeigt, wenn
  `toolbar.show.labels` aktiv ist; sonst Icon-only (Label bleibt im Tooltip).
- Icon-Größe (`iconSize`) und die Größen-CSS-Klasse (`fxt-tool-small/large`)
  werden beim Bauen aus `toolbar.icon.size` abgeleitet
  (small ≈ 18 px, large ≈ 24 px — finale Werte im Plan).
- Neue private Methode `rebuildEditorToolbar()` baut die Toolbar neu (für
  Live-Refresh nach Settings-Änderung).

### 4. Settings-Persistenz (`PropertiesService` / `PropertiesServiceImpl`)

Neue API analog zum bestehenden `isUseSmallIcons()`-Muster:

```java
boolean isToolbarShowLabels();          // default false
void    setToolbarShowLabels(boolean);
String  getToolbarIconSize();           // "small" | "large", default "small"
void    setToolbarIconSize(String);
```

Persistenz über die bestehenden Keys in `FreeXmlToolkit.properties`
(`toolbar.show.labels`, `toolbar.icon.size`). Robustes Parsen mit Default-Fallback.

> Hinweis für die Umsetzung: Vorhandenes `isUseSmallIcons()` prüfen — falls es
> bereits die Toolbar betrifft, integrieren statt duplizieren; andernfalls
> dedizierte Toolbar-Settings verwenden.

### 5. Settings-UI (`SettingsPanel`)

In der „GENERAL"-Card:
- Checkbox „Show toolbar button labels" ↔ `toolbar.show.labels`.
- Auswahl „Toolbar icon size: Small / Large" (RadioButtons oder ChoiceBox)
  ↔ `toolbar.icon.size`.
- Laden in `loadSettings()`, Speichern in `saveSettings()` (gleiches Muster
  wie `smallIcons`).

### 6. Live-Refresh

Der bestehende `setOnSaved`-Callback in `UnifiedShellView`
(`activityBar.refresh(); reloadPanelPrefs();`) wird um
`rebuildEditorToolbar()` erweitert, damit Farb-/Text-/Größenänderungen sofort
ohne Neustart sichtbar werden.

## Datenfluss

```
Settings (SettingsPanel) --save--> PropertiesService --persist--> properties-Datei
        |                                   ^
   onSaved-Callback                         | read beim Toolbar-Bau
        v                                   |
UnifiedShellView.rebuildEditorToolbar() ----+
        |
   toolButton(label, color, ...) --> Button (CSS-Klassen: fxt-tool-button,
                                     fxt-tool-<color>, fxt-tool-<size>)
        |
   CSS (unified-shell.css + design-tokens.css) --> finales Aussehen
```

## Fehlerbehandlung / Graceful Degradation

- Unbekannter/leerer `toolbar.icon.size`-Wert → Fallback `small`.
- Fehlende Properties → Defaults.
- Keine neuen Icons → `IconifyIconCoverageTest` bleibt grün.

## Tests

- **Unit:** `PropertiesService`-Defaults + Round-Trip (set→get→reload) für die
  zwei neuen Settings.
- **Bestehend:** `IconifyIconCoverageTest` (keine neuen `bi-*`-Literale).
- **Optional (leicht):** Test, dass `toolButton` bei aktivem Label `getText()`
  setzt und die erwartete Farb-/Größen-Style-Klasse trägt.

## Nicht im Scope (YAGNI)

- Frei konfigurierbare Farben pro Button durch den Nutzer.
- Umordnen/Ein-/Ausblenden einzelner Toolbar-Buttons.
- Änderungen an der linken Activity-Bar (nur die Editor-Toolbar).
