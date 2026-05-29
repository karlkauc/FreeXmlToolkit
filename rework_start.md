# UI-Rebuild — Analysephase-Prompt

> Kopiere den folgenden Block in eine frische Claude-Code-Session, um die
> Analyse- & Planungsphase für den UI-Umbau zu starten.

---

```
ROLLE & ZIEL
Du bist Senior-Architekt für die JavaFX-Desktop-App "FreeXmlToolkit".
Wir wollen die GESAMTE UI gemäß den fertigen Figma-Mockups neu aufbauen
(Konzeptwechsel: weg von getrennten Tool-Editoren, hin zu EINEM Unified
Editor mit Activity-Bar). Dies ist die ANALYSE- & PLANUNGSPHASE.
Schreibe in dieser Phase NOCH KEINEN Produktiv-Code – Ergebnis sind
Analyse- und Plandokumente.

ZIEL-DESIGN (Figma-Referenz)
Datei: "FreeXmlToolkit — UI Modernization"
Key:  oqJVcInD6RgKaQ4dYmMWYh
URL:  https://www.figma.com/design/oqJVcInD6RgKaQ4dYmMWYh
Lies die Mockups mit den Figma-Read-Tools (get_screenshot / get_metadata /
get_design_context / get_variable_defs) ein. Relevante Knoten:
 - Styleguide: node 0-1 (Farb-Tokens Light/Dark, Typografie Inter + JetBrains
   Mono, Spacing/Radius, Komponenten; Primär Indigo #3B5BDB + Marken-Akzent
   Orange #F08C2E).
 - Unified Editor: 28-2 (Light), 35-2 (Dark) — Activity-Bar (Explorer,
   Favorites, Validation, Transform, Schema, PDF/FOP, Signature, Help,
   Settings) + Seitenpanel + dateityp-bewusster Editor + Properties-Inspector
   + Statusbar + volle Toolbar.
 - Aktivitäts-Zustände: Schema/grafisch 37-2, Validation 40-2, Schematron
   43-2, Transform 46-2, Favorites 48-2, PDF/FOP 49-2, Signature 50-2,
   Welcome/Dashboard 52-2, Tree-View 55-2, Graphic+Grid 70-2.
Kern-Prinzipien des Ziel-Designs:
 - EIN Unified Editor; alle Funktionen über die Activity-Bar gebündelt.
 - Ansichtsmodi NUR Text · Tree · Graphic. KEIN eigener Grid-Modus – das
   Grid ist Teil von Graphic: aufeinanderfolgende gleichartige Knoten werden
   als eingebettetes, aufklappbares Grid (XMLSpy-Stil, vertikal verschachtelt)
   dargestellt.
 - Properties/Inspector in ALLEN Ansichten identisch (Node&XPath, Type&Facets,
   Cardinality&Use, Documentation&Refs).
 - Light/Dark über Design-Tokens; Icons = IconifyIcon (bi-*, bereits gebündelt).

HARTE RANDBEDINGUNGEN (nicht verhandelbar)
 1. KEIN Funktionsverlust: jede heute verfügbare Funktion muss im neuen
    Design weiter erreichbar sein. Erstelle dafür ein vollständiges
    Feature-Inventar mit Traceability-Matrix (Feature → heutiger Ort im Code
    → Ort/Activity im neuen Design).
 2. Eigener neuer Branch für den gesamten Umbau (lege ihn an; Analyse-Doku
    wird dort committed).
 3. Nicht mehr genutzter Code wird entfernt – aber erst nach Nachweis, dass
    er wirklich tot ist (statische Referenzanalyse + Feature-Matrix).
 4. PERFORMANCE bei großen Dateien hat hohe Priorität: Parsing, Editor
    (RichTextFX), Tree/Grid (Virtualisierung!), Validierung (XSD/Schematron,
    Batch), XSLT/XQuery (Saxon), FOP. Alles Ladbare async, lazy, UI-Thread
    nie blockieren.
 5. LEICHTE BEDIENBARKEIT hat hohe Priorität: View-State erhalten (auf/zu),
    Workflow-Kontinuität, konsistente Interaktion über alle Ansichten,
    Tastatur/Command-Palette, klare Fehlerdialoge.

PROJEKT-KONTEXT (vorher einlesen)
 - CLAUDE.md und .claude/rules/*.md (architecture, domain, quick-reference,
   xsd-editor-v2-details) für Architektur, Konventionen, Tech-Stack
   (Java 25, JavaFX 24, Saxon HE, Xerces XSD 1.1, FOP, RichTextFX, Log4j2,
   AtlantaFX, IconifyIcon).
 - Aktuelle Controller/FXML/Services, insbes. XSD-Editor V2 (controls/v2/,
   MVVM + Command-Pattern), MainController, die ~24 FXML-Seiten.

AUFGABE DER ANALYSEPHASE
A) Feature-Inventar: Vollständige, aus dem CODE abgeleitete Liste aller
   Funktionen (XML-Editor + IntelliSense, XSD-Editor V2 inkl. Type
   Library/Editor/Facets/Schema-Analyse/Flatten/Doku-Export, XSD-Validation
   single+batch 1.0/1.1, XSLT Viewer+Developer, XPath/XQuery, Schematron,
   FOP/PDF, Signaturen, JSON-Editor+JSONPath, Schema-Generator, Templates,
   Favorites, Settings, Help/About, Auto-Update, Spreadsheet-Konvertierung,
   …). Pro Feature: Einstiegspunkte, Controller, Services, FXML.
B) Architektur-Ist-Aufnahme: Layer, Datenflüsse, Threading, Abhängigkeiten;
   was ist wiederverwendbar (v.a. V2-Modell, Commands, Services) und was ist
   UI-spezifisch und wird ersetzt.
C) Gap-/Mapping-Analyse: Ziel-Design ↔ heutiger Code. Welche Activity/Panel
   bekommt welche Funktion. Was ist neu zu bauen (Activity-Bar, Unified-Shell,
   einheitlicher Inspector, eingebettetes Grid, Token/Theme-System), was wird
   gemappt, was entfällt.
D) Performance-Analyse: heutige Engpässe bei großen Dateien identifizieren;
   konkrete Strategien fürs neue Design (virtuelle Tree/Grid-Darstellung,
   inkrementelles Rendering, lazy Parsing, Streaming/SAX wo möglich,
   Hintergrund-Tasks, Caching). Mess-/Profiling-Plan + Ziel-Budgets vorschlagen.
E) Tot-Code-Kandidaten: nach dem Mapping auflisten (mit Begründung/Referenzen),
   getrennt von „unsicher".
F) Risiken & Annahmen; offene Entscheidungen als Fragen formulieren.
G) Migrationsstrategie & Phasenplan (inkrementell, jederzeit lauffähig):
   Reihenfolge, Meilensteine, Test-/Abnahmekriterien, Rollback.

DELIVERABLES (im neuen Branch committen, KEIN App-Code)
 - docs/superpowers/specs/<datum>-ui-rebuild-analysis.md  (A–F)
 - docs/superpowers/specs/<datum>-ui-rebuild-plan.md       (G, Phasenplan)
 - Feature-Traceability-Matrix (Tabelle) als Teil der Analyse.

VORGEHEN
 1. Erst Kontext einlesen (CLAUDE.md, rules, Code) und Mockups ansehen,
    DANN analysieren. Nutze parallele Explorations-Subagenten für Breite.
 2. Stelle mir gezielte Rückfragen, BEVOR du den Plan finalisierst
    (z.B. Scope-Grenzen, „Tree/Graphic vollständig neu vs. V2 weiternutzen",
    Theme-Persistenz, Mindest-Java/JavaFX, Test-Tiefe).
 3. Keine Produktivänderungen am App-Code in dieser Phase. Nur Analyse-/
    Plandokumente + Branch-Anlage.
 4. Belege Aussagen mit Datei-/Klassenreferenzen; rate nicht.

Starte mit Schritt 1 (Kontext + Mockups einlesen) und melde dann deine
Rückfragen.
```
