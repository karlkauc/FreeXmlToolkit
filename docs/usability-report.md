# Usability-Report & Verbesserungsvorschläge

> Kritische UX-Bewertung der Unified Shell mit priorisierten, umsetzbaren Maßnahmen.
> Erstellt 2026-07-01. Grundlage: Code-Analyse (`controls/shell/`, `util/DialogHelper`,
> `PanelStatus`, `ToastNotification`, `css/design-tokens.css`, `STYLE_GUIDE.jsonc`) und die
> aktuellen Screenshots in `docs/img/unified-shell-*.png`.

## Ziel

FreeXmlToolkit ist eine mächtige Business-Desktop-App für XML/XSD/XSLT/Schematron, aufgebaut als
VS-Code-artige „Unified Shell". Dieser Report bewertet die App gegen sechs Vorgaben:

1. Einfacher, sehr effektiver Workflow
2. Klar ersichtlich, **wo** Aktionen gesetzt werden können
3. Standardisiertes, einheitliches Aussehen
4. Mächtig, aber leicht bedienbar; Farben + einfaches Handling unterstützen Workflows
5. **Sprechende** Fehlermeldungen (Ursache **und** Behebung/Optionen)
6. Häufige Aktionen sehr einfach zugänglich

**Gesamteindruck:** Solide, moderne Basis mit klaren Stärken (exzellente Tooltip-Abdeckung,
navigierbares Validierungs-Panel, sauberes Light/Dark-Modell, konsequente Icon+Text-Kontextmenüs).
Die Schwächen liegen weniger in „fehlenden Features" als in **Inkonsistenz und Redundanz**: drei
konkurrierende Farbsysteme, generische Fehlermeldungen ohne Lösungsweg, uneinheitliche
Discoverability (mal Toolbar, mal `⋮`-Menü, mal versteckt) und mehrfach vorhandene Einstiegspunkte
für dieselbe Aktion.

Severity-Legende: 🔴 hoch (bremst Workflow / verwirrt spürbar) · 🟡 mittel · 🟢 Politur

---

## 1 · Effektiver Workflow — Redundanz, State-Verlust, blockierende Modals

- 🔴 **State-Verlust beim Aktivitätswechsel.** Seitenpanels werden bei jedem Wechsel neu erzeugt
  (`UnifiedShellView.java`, `sidePanelHost.getChildren().setAll(new …Panel(...))`); nur
  `ValidationPanel` wird gecached. Eingaben in PDF-Metadaten (Title/Author/Subject),
  Signatur-Formularen (X.500-DN) oder Transform-Parametern gehen verloren, sobald man kurz nach
  Explorer/Validation wechselt. → **Empfehlung:** Panels cachen (Instanz pro Activity halten,
  nur Sichtbarkeit umschalten) — analog zum bereits gecachten `ValidationPanel`.
- 🟡 **Routine-Erfolg blockiert per Modal.** Ein fertiger `ToastNotification`-Control
  (INFO/SUCCESS/WARNING/ERROR, Fade-out) existiert, ist aber **nur in einer Klasse** verdrahtet
  (`XmlCanvasView.showToast()`). Alle „Exported to …"-Bestätigungen in den v2-Views
  (`SchemaStatisticsView`, `QualityChecksView`) sind blockierende `Alert.showAndWait()`.
  → **Empfehlung:** Toast als App-weiten Erfolgs-/Info-Kanal etablieren; Modals nur noch für Fehler,
  Rückfragen und destruktive Aktionen.
- 🟡 **Dieselbe Aktion an drei Stellen.** Transform ist erreichbar über Toolbar, Explorer-Transform-
  Leiste **und** Transform-Activity; Validate über Toolbar (F8) und Validation-Panel; „Recent files"
  in Explorer-Tab, Welcome-Liste und Welcome-Stats; Favorites hat zwei Häuser (Explorer-Tab +
  Favorites-Activity). → **Empfehlung:** je Aktion **eine primäre Heimat** definieren, Rest klar als
  Shortcut/Kontext kennzeichnen (nicht als gleichwertigen Zweit-Button).

## 2 · „Wo kann ich Aktionen setzen?" — Discoverability

- 🔴 **Icon-only Activity-Bar & Schema-Toolbar.** Die acht Activity-Icons und die acht Buttons der
  Schema-Seitenleiste haben **keine Textlabels**, nur Tooltips. Mehrere Glyphen sind verwechselbar
  (`bi-diagram-3` Schema vs. `bi-file-earmark-code` FundsXML vs. `bi-file-earmark-pdf`).
  → **Empfehlung:** Activity-Bar-Labels als Setting (Business-Fokus → default-on); Schema-Toolbar-
  Icons mit Textlabels oder eindeutigeren Glyphen + sichtbaren Tooltips.
- 🟡 **Kernfunktionen zwei Ebenen tief im `⋮`-Menü.** Schematron-Authoring und JSON-Schema-
  Validierung liegen im Overflow des Validation-Panels. Overflow-`⋮` gibt es nur in drei Panels
  (Explorer/Validation/Transform), die anderen zeigen alles inline — inkonsistent.
  → **Empfehlung:** ein einheitliches Overflow-Muster; mächtige, aber regelmäßig genutzte Features
  sichtbarer platzieren.
- 🟡 **Keine Menüleiste (File/Edit/…).** Es fehlt die klassische Entdeckungsfläche, die Business-User
  erwarten und die Shortcuts auflisten würde. → **Empfehlung:** schlanke MenuBar **oder** eine
  durchsuchbare Command-Palette (die Suchpille „Ctrl K" existiert bereits — als Aktions-Palette
  ausbauen).

## 3 · Standardisiertes Aussehen — drei konkurrierende Farbsysteme

- 🔴 **Drei Quellen der Wahrheit für Farben mit unterschiedlichen Hex-Werten.**
  `design-tokens.css` (`-fxt-*`, success `#2f9e44`, danger `#e03131`); Legacy
  `-bg-/-text-/-spacing-*` in `app-theme.css`/`dark-theme.css` (versorgt Kontextmenüs, Dialoge,
  Toolbar-Theme); Bootstrap-Palette in `STYLE_GUIDE.jsonc` + hartkodiert im Java-Code
  (success `#28a745`, danger `#dc3545`). Dasselbe „Erfolgs-Grün" rendert im Shell anders als im
  XSD-Kontextmenü. Die „Single Source of Truth" ist in nur **2 von ~22** Stylesheets verdrahtet.
- 🔴 **≈795 hartkodierte Hex-Werte in ~71 Java-Dateien** (561 distinkte Farben). Worst Offenders:
  `XsdContextMenuFactory` (69), `XsdQualityExporter` (56), `TypeLibraryView` (44).
  → **Empfehlung:** **Ein** Token-System (`-fxt-*`); Legacy-Tokens ablösen; semantische Farben im
  Java-Code über zentrale Konstanten/Lookup statt Inline-Hex; Guard-Test analog
  `IconifyIconCoverageTest` gegen neue Inline-Hex.
- 🟡 **STYLE_GUIDE.jsonc ist veraltet.** Nennt Ikonli `FontIcon` (Code nutzt `IconifyIcon`) und Fonts
  Roboto/Segoe (Code: Inter/JetBrains Mono). → **Empfehlung:** an den Ist-Zustand angleichen.
- 🟡 **Zwei „Primary-Button"-Stile** (`fxt-primary-button` in Panels vs. `fxt-tool-*` in der
  Toolbar), Semantik nirgends erklärt. → **Empfehlung:** Semantik dokumentieren, Rollen vereinheitlichen.

## 4 · Farben & Handling zur Workflow-Unterstützung

- 🟢 **Gute Ansätze vorhanden:** Validierungs-Severity mit Farbe+Icon+Badges, „Well-formed"/„Valid"-
  Unterscheidung, farbcodierte Output-Warnings. → **Empfehlung:** dieses Muster konsequent auf alle
  Workflow-Stati ausdehnen (Transform, PDF, Signatur, Batch) — einheitliche Statusfarben
  (laufend/erfolg/warnung/fehler) statt teils nur Textstatus.
- 🟡 **Fehlendes Fortschritts-/Cancel-Feedback bei Panel-Tasks.** Transform, XSLT, PDF-Generierung,
  Batch-Validierung zeigen nur Text-Status („Validating…") ohne Spinner/Progressbar/Abbrechen; lange
  Läufe wirken „eingefroren". Positiv-Vorbild: verzögerter Lade-Spinner (`EditorHost`) und
  Live-Progress-Log der Doku-Generierung. → **Empfehlung:** einheitliche Progress-/Cancel-Affordanz.

## 5 · Sprechende Fehlermeldungen — Ursache **und** Lösungsweg

- 🔴 **Dominantes Anti-Muster: `"<X> failed: " + e.getMessage()`.** Die rohe Exception-Message wird
  ohne Interpretation und ohne nächsten Schritt an den User gekippt (z. B. `EditorHost` URL-Fetch,
  `SettingsPanel` Template speichern, `SchematronTester`, `SchemaStatisticsView`).
  → **Empfehlung:** Fehler-Template mit drei Teilen: **Was ist passiert · (technisches Detail
  einklappbar) · Was kann ich tun**. `DialogHelper.showException` hat bereits die einklappbare
  Stacktrace-Fläche — die zweizeilige Header/Detail-Struktur wird aber fast nie genutzt (Caller
  übergeben `header=null`).
- 🔴 **Gold-Standard existiert — nur nicht als Vorlage genutzt.** Der Update-Fehler (`AboutDialog`)
  nennt Ursache **und** zwei Optionen („You can try again or download the update manually from
  GitHub."). → **Empfehlung:** diese Struktur zum verbindlichen Muster machen (kuratierte
  Remedy-Texte je Fehlerklasse).
- 🟡 **Vier parallele Meldungssysteme, uneinheitlich adoptiert** (`DialogHelper`, `PanelStatus`,
  `ToastNotification` (verwaist), rohe `new Alert(...)` in Alt-Code); teils falsche/kopierte
  Dialog-Titel. → **Empfehlung:** Alt-Code auf `DialogHelper`+`PanelStatus`+Toast migrieren.
- 🟡 **Still verschluckte Fehler** (JSON-Parse im Tree-View, Favoriten-Ladefehler, Update-Dialog-
  Öffnungsfehler). → **Empfehlung:** loggen **und** dezent (Toast) rückmelden.
- 🟡 **JSON-Validierungsprobleme nicht klickbar** (Zeile `-1`). → **Empfehlung:** Zeilenauflösung
  nachrüsten.

## 6 · Häufige Aktionen leicht zugänglich — Toolbar & Shortcuts

- 🔴 **Flache 18-Button-Toolbar ohne Priorisierung/Gruppierung.** Eine `FlowPane` mit ~18
  gleichgewichteten Icon+Label-Buttons (Info-Gruppe allein 11 Buttons); bricht bei schmalem Fenster
  mehrzeilig um. Häufig (New/Open/Save/Undo/Format/Validate) und selten (Spreadsheet Converter,
  Insert Template, Type Editor) stehen visuell gleichwertig. → **Empfehlung:** primäre Aktionen
  prominent, sekundäre in ein „More"-/Overflow-Cluster; klarere Hierarchie (nur Haupt-CTA farbig).
- 🔴 **14 von 18 Toolbar-Buttons nicht kontext-gated.** Nur Validate/Transform/GenerateDocs/
  TypeEditor werden je Dateityp aktiviert/deaktiviert. Format, Minify, Set-Schema, Save etc. sind
  auch auf dem leeren Welcome-Screen klickbar. → **Empfehlung:** alle dokumentabhängigen Aktionen
  deaktivieren, wenn kein/unpassendes Dokument offen ist (`EditorActions.applicableFor`).
- 🟡 **Dünne, inkonsistente Shortcut-Abdeckung.** Echte `setAccelerator` nur im XML-Grid-Kontextmenü;
  globale Shortcuts handgerollt. **Undo/Redo nicht shell-gebunden** (Ctrl+Y ≠ RichTextFX-Default-
  Redo → Mismatch-Risiko), **Format/Minify ohne Shortcut**. → **Empfehlung:** globale Accelerator
  konsolidieren (inkl. Undo/Redo, Format), in Tooltips/Command-Palette anzeigen.
- 🟢 **Stärken bestätigt:** Open/Save/Validate/New sind ein Klick entfernt; Tooltip-Abdeckung
  exzellent und kodiert Shortcuts. Diese Basis erhalten.

---

## Priorisierte Roadmap

**Quick Wins (hoher Nutzen, geringer Aufwand)**

1. Fehler-Template „Was · Detail · Lösung" + kuratierte Remedy-Texte je Fehlerklasse; `header`-Zeile
   von `DialogHelper` nutzen (Muster: `AboutDialog`).
2. `ToastNotification` als App-weiten Erfolgs-/Info-Kanal verdrahten; Routine-„Exported…"-Modals ersetzen.
3. Dokumentabhängige Toolbar-Buttons kontext-gaten (alle statt nur 4).
4. Activity-Bar-/Schema-Toolbar-Labels (Setting default-on) für klare Discoverability.
5. Fehlende globale Shortcuts ergänzen (Undo/Redo shell-gebunden, Format).

**Mittel**

6. Seitenpanels cachen → kein State-Verlust beim Activity-Wechsel.
7. Einheitliche Progress-/Cancel-Affordanz für Transform/PDF/Batch.
8. Toolbar in Primär- vs. Overflow-Cluster gliedern; Farbsemantik der Buttons vereinheitlichen+dokumentieren.
9. Redundante Einstiegspunkte je Aktion auf eine primäre Heimat reduzieren.

**Größer (Standardisierung)**

10. Farbsysteme auf `-fxt-*` konsolidieren; Legacy-Tokens ablösen; Inline-Hex durch zentrale
    semantische Konstanten ersetzen; Guard-Test gegen neue Inline-Hex.
11. STYLE_GUIDE.jsonc auf Ist-Zustand aktualisieren (IconifyIcon, Inter/JetBrains Mono).
12. Command-Palette (Ctrl K) zur zentralen, durchsuchbaren Aktionsfläche ausbauen.

---

## Umsetzungsstatus

Die Quick Wins werden mit **Fehlermeldungen als Leitpriorität** umgesetzt (Schritte 1–5 oben).
Die Positionen 6–12 sind als Backlog dokumentiert.
