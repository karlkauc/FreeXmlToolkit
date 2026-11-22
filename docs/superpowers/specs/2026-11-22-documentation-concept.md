# Dokumentation: Konzept, Gap-Analyse und grafische Anpassung

Stand 22. November 2026 · Entwurf zur Freigabe · betrifft `docs/`, `mkdocs.yml`, `docs/overrides/`

Die Benutzerdokumentation wird vom werkzeugweise gewachsenen Nachschlagewerk zum Handbuch mit
sieben Bereichen, die nach Aufgabe und Leser gegliedert sind, und bekommt das Erscheinungsbild der
Logo-Suite S1. Inhaltlich ist fast alles vorhanden: 50 der 69 Seiten wandern nur an einen neuen Ort,
14 werden geteilt oder zusammengeführt, rund 20 Seiten entstehen neu (davon 7 Bereichs-Startseiten).

Grundlagen dieses Dokuments:

- Live-Site https://karlkauc.github.io/FreeXmlToolkit/, alle 67 Sitemap-Seiten und 122 Bilder abgerufen am 22.11.2026
- Quellen `docs/*.md` (69 Seiten ohne `superpowers/`), `mkdocs.yml`, `docs/overrides/`
- Marke und Website: Logo-Suite S1 und Website-Plan (claude.ai/artifact/Q4bqyCZ3DwHAMEZjRyBYvR, …/V1f8iDEvprbo84ytzQMJ5G)
- Festlegungen: MkDocs Material bleibt · Englisch, Struktur für späteres Deutsch · Produkt-Website entsteht getrennt

---

## 1. Befund

### 1.1 Inhalt und Struktur

| Bereich | Befund | Beleg |
|---|---|---|
| Startseite | Wirbt und erklärt zugleich: Download-Karten, 10 Feature-Tabellen, Normen, Shortcuts, Sponsor. 2.360 Wörter, 12 Tabellen. „Your First Steps" steht nach rund 2.000 Wörtern | `/` |
| Navigation | 13 Tabs, davon 2 externe Links (Download, Sponsor). Keine Bereichs-Startseiten (`navigation.indexes` aus) | `mkdocs.yml` |
| Gliederung | Nach Werkzeug und nach den alten Tabs der App, nicht nach Aufgabe. 6 Seiten beginnen mit „The standalone … tab has been retired" | `json-editor/`, `schematron-support/`, `xsd-tools/`, `xsd-validation/`, `xslt-viewer/`, `xslt-developer/` |
| Einstieg | „Getting Started" enthält MCP Server, AI Assistant, Execution Statistics, File Associations. Ein Tutorial gibt es nicht | Tab „Getting Started" |
| Seitenlänge | unified-shell 15.600 Wörter (39 H2), xsd-tools 11.300, whats-new 9.300, fundsxml 5.900, xml-editor 5.800, grid 5.100 (2 H2) | – |
| Versteckte Themen | Compare & Merge (1.088 Wörter), Search (836), Sign & Encrypt (847), Export View gibt es nur als Abschnitt in `unified-shell/` | `unified-shell/#…` |
| Dopplungen | Validierung auf 3 Seiten in 2 Tabs; XSLT auf 4 Seiten; Inspector ganz in `xml-editor/` wiederholt; Shortcut-Tabellen auf 5 Seiten | `validation-panel/`, `xsd-validation/`, `schema-support/`; `transform-panel/`, `xslt-viewer/`, `xslt-developer/`, `debugger/` |
| Lesergruppen | Endnutzer, Administratoren, Plugin-Entwickler und Contributor in einer Navigation. `plugin-development/` steht unter „Industry Standards", `technology-stack/` unter „Reference" | – |
| Seitenaufbau | Keine einheitlichen Abschnitte für Voraussetzungen und nächste Schritte; nur rund 10 Seiten haben einen „Related"-Hinweis | – |
| Fehlende Seiten | Tutorial, Glossar, Systemvoraussetzungen, Umsteiger-Seite, CI-Rezepte | – |
| Veraltet | `comparison/` nennt Version 3.1 (aktuell 3.4.0), `accessibility/` ist ein Audit von 2.10, `inspector/` und `context-sensitive-intellisense/` nennen 2.6.0 | – |
| Begriffe | „Plugins", „Extensions", „Industry Extensions" für dasselbe; `SECURITY/` (Funktionen) neben `security-policy/` (Meldeweg) | `plugins/`, `industry-plugins/`, `plugin-development/` |
| Titel ≠ Navigation | Seite „XSLT Viewer", Navigation „XSLT Quick Start & Examples" | `xslt-viewer/` |

Was gut ist und bleibt: keine kaputten Links oder Bilder; Screenshots fast durchgehend 1680×1000 im
hellen Modus; die Plugin-Seiten folgen einer Vorlage (Switching it on → Which documents → Schemas →
Side panel → Checks → Typical workflow → What is not checked → Troubleshooting); `command-line/` und
`administration/` sind saubere Referenzen; explizite Anker (`{#id}`) halten Deep-Links stabil.

### 1.2 Erscheinungsbild (grafische Analyse)

| Element | Heute | Problem |
|---|---|---|
| Logo, Favicon | `img/logo.png`, altes Dokument-Symbol in Navy `#0e537c` mit rotem XML-Schild | Nicht die Marke S1; PNG statt SVG |
| Titel | „FreeXmlToolkit Documentation" neben dem Logo | Wortmarke und Titel doppeln sich |
| Primärfarbe | `#4a90e2`, Verlauf im Header `#4a90e2 → #357abd`, Tabs `#357abd → #2a6ca8` | App und Marke verwenden `#1373d9` auf Navy `#2a284c`; Verläufe kommen in der Marke nicht vor |
| Akzent | Orange `#fc9040` | Marke: `#f08c2e` |
| Fußzeile, Banner | Teal-Verlauf `#2A9D8F → #264653` | Teal ist keine Markenfarbe; drittes Farbklima neben Blau und Orange |
| Schrift | Roboto / Roboto Mono (Material-Standard) | App und Marke: Inter / JetBrains Mono |
| Tabellenkopf | fest `#f6f8fa` / `#24292f` | Im Dunkelmodus helle Balken |
| Hinweise | 31 Admonitions, aber rund 260 Zeilen `> **Note:**` als Zitat | Zwei Darstellungen für dasselbe; Zitate tragen keine Bedeutung (Tipp/Warnung) |
| Bilder | Keine Bildunterschriften, keine Markierungen; Ausreißer von 320×720 bis 1606×73 | Leser muss den relevanten Bereich im Vollbild selbst finden |
| Dunkelmodus | Nur 5 Light/Dark-Bildpaare; `unified-shell-validation-dark.png` wird auch im hellen Modus gezeigt | Helle Screenshots blenden auf dunkler Seite |
| Diagramme | Keine; ASCII-Skizzen in Codeblöcken auf 6 Seiten | Abläufe (Validierungsstufen, Projekt-Bindungen) nur als Text |
| Startseite | Emoji als Plattform-Symbole, Animation 4,2 MB | Stilbruch zur App (Bootstrap Icons); lange Ladezeit |
| Navigation | 13 Tabs | Bricht unter rund 1.400 px um; Download und Sponsor sind keine Doku-Bereiche |
| Bestand | 7 unbenutzte Bilder, 3 inhaltsgleiche Duplikate | `main-window.png`, `xml-editor-tree.png`, … |

---

## 2. Konzept (Greenfield)

### 2.1 Für wen die Dokumentation ist

| Leser | Kommt mit der Frage | Braucht | Vorwissen |
|---|---|---|---|
| **Einsteiger und Umsteiger** (von XMLSpy, Oxygen, Notepad++) | Wie fange ich an? Wo ist, was ich kenne? | Installation, Tutorial, Tour durch den Arbeitsbereich, Begriffe | XML ja, die App nein |
| **XML-Entwickler** (XSD, XSLT, XQuery, Schematron) | Wie mache ich X? | Anleitung je Aufgabe, Debugger, Referenz zu Versionen und Shortcuts | hoch |
| **Fachanwender Datenstandards** (FundsXML, ISO 20022, FinDatEx, XBRL, FMA-/ESMA-Meldewesen) | Wie prüfe ich meine Meldung, was bedeutet dieser Fehler? | Eine Seite je Standard mit Ablauf, Prüfregeln, Fehlerbehebung | Fachlich hoch, XML gering |
| **Automatisierer** (Build-Pipelines, AI-Agenten) | Wie rufe ich das ohne Oberfläche auf? | Befehlsreferenz, Exit-Codes, CI-Rezepte, MCP-Werkzeuge | hoch |
| **Administratoren und IT-Sicherheit** | Darf ich das ausrollen? Was sendet es? Wie sperre ich Funktionen? | Verteilung, Richtlinien, Offline-Betrieb, Telemetrie, Bedrohungsmodell | Betrieb ja, XML nein |
| **Plugin-Entwickler und Contributor** | Wie erweitere ich es, wie trage ich bei? | Plugin-API, Technik, Contributing | Java |

Nicht Zielgruppe der Doku: Interessenten, die noch entscheiden. Sie bedient die Produkt-Website
(Funktionen, Vergleich, Download).

### 2.2 Was die Dokumentation bewirken soll

1. **Schneller Erfolg:** Ein neuer Nutzer hat nach 15 Minuten eine Datei geöffnet, validiert und transformiert.
2. **Auffindbarkeit:** Jede Aufgabe ist über Navigation oder Suche in höchstens zwei Schritten erreichbar. Eine Aufgabe steht auf genau einer Seite.
3. **Verlässlicher Aufbau:** Jede Seite hat genau einen Typ und den Aufbau dieses Typs (Abschnitt 2.4).
4. **Getrennte Wege:** Administratoren, Automatisierer und Entwickler haben je einen Bereich und müssen keine Endnutzer-Seiten lesen.
5. **Handbuch statt Prospekt:** Hero, Download-Karten, Vergleich und Sponsoring gehen an die Website. Die Doku verlinkt dorthin.
6. **Ein Produkt:** Doku, App und Website zeigen dasselbe Logo, dieselben Farben und Schriften.
7. **Stabile Adressen:** Links aus App, README und CLI-Hilfe funktionieren weiter.
8. **Pflegbar:** Keine Seite über 2.500 Wörter (Ausnahme: Referenztabellen); Shortcuts, Einstellungen und Versionen stehen an einer Stelle.

Messbar nach dem Umbau: höchstens 7 Tabs; 0 Seiten mit „retired tab"-Hinweis; 0 Zitat-Notizen;
jede Anleitung hat „Before you start" und „Next steps"; `mkdocs build --strict` ohne Warnung.

### 2.3 Sitemap

Sieben Bereiche, jeder mit einer Startseite (`navigation.indexes`). Ordner = Bereich, damit eine
deutsche Fassung später dieselbe Struktur spiegeln kann. „neu" = Seite existiert heute nicht.

```
Docs home                                  Wegweiser nach Rolle, keine Werbung              (neu)

1 Get started            get-started/
  Install                                  Pakete, Systemvoraussetzungen, Updates
  First steps                              Tutorial: öffnen, validieren, transformieren     (neu)
  Tour of the workspace                    Activity Bar, Panels, Ansichten, Tabs            (aus unified-shell)
  Coming from another editor               Begriffe und Wege für XMLSpy-/Oxygen-Nutzer      (neu)
  Get help                                 FAQ, Fehler melden, Beispiele                    (neu)

2 Guides                 guides/
  Edit XML               edit-xml/         Text, Tree, Grid · Auto-completion · Formatting
                                           · Search & replace in files (neu, aus unified-shell)
                                           · Templates · Favorites · Bookmarks · Large files
  JSON & YAML            json-yaml/        Editor · Grid · JSON Schema
  Design schemas         design-schemas/   Graphic view · Overview · Types · Analysis
                                           · Documentation · Sample XML · Flatten · Schema Library
  Validate               validate/         XSD, DTD, RELAX NG · Schematron · Quick Fixes
                                           · Folders and projects · Working with problems
  Transform & query      transform-query/  Run XSLT · Query console (XPath, XQuery, JSONPath)
                                           · XProc · Debug · Profile · XSpec · External processors
  Publish                publish/          PDF with XSL-FO
  Compare, merge, convert compare-convert/ Compare & merge (neu) · XML ↔ JSON ↔ YAML · Excel/CSV
  Sign & encrypt         sign-encrypt/     Signatures · Encryption
  Projects & scenarios   projects/
  AI assistant           ai-assistant/

3 Industry standards     industry/
  Overview · FundsXML · ISO 20022 · ebInterface · FinDatEx · XBRL
  · PK-Meldewesen · KAG-Meldewesen · ESMA reporting · Identifier lookup · Manage extensions

4 Automate               automate/
  fxt-cli overview · Command reference (validate & query · transform & publish
  · compare, merge, convert · sign & encrypt · lookups) · Exit codes and output
  · CI recipes (neu) · MCP server

5 Administer             administer/
  Deploy and silent install · Policies and defaults · Operation without internet
  · Security features · Threat model · Privacy & telemetry · Code signing · File associations

6 Reference              reference/
  Workspace (menus, toolbar, status bar, Inspector, panels) · Keyboard shortcuts · Settings
  · Standards and language versions · Schema support · XsdDoc tags · Project file format
  · Execution statistics · Glossary (neu) · FAQ & troubleshooting
  · What's new (eine Seite je Hauptversion) · Accessibility · Licenses

7 Extend & contribute    extend/
  Writing plugins · Technology stack · Contributing · Code of conduct · Security policy
```

Nicht mehr in der Navigation: Download, Sponsor, Comparison. Sie gehören zur Website; bis diese
online ist, bleibt `comparison/` unter Reference und die Download-Karten bleiben auf der Docs home.

Regeln für die Zuordnung:

- **Guides** beantworten „Wie mache ich …?" und sind nach Aufgabe benannt (Verb), nicht nach Panel.
- **Reference** beschreibt, was es gibt: jedes Feld, jeder Befehl, jede Taste. Guides verlinken dorthin, statt Tabellen zu wiederholen.
- Ein Thema, das Endnutzer und Administrator betrifft (Extensions, Telemetrie, AI), hat eine Nutzerseite und einen Abschnitt „For administrators" mit Verweis auf die Richtlinien.
- Begriff für Erweiterungen: **Extension** in der Oberfläche und für Nutzer, **Plugin** nur für die Programmierschnittstelle.

### 2.4 Seitentypen und ihr Aufbau

| Typ | Zweck | Aufbau | Beispiele |
|---|---|---|---|
| **Tutorial** | Lernen durch Tun, ein fester Weg | Ergebnis in einem Satz · Dauer · Schritte mit Bild · „What you learned" · Next steps | First steps |
| **Guide** | Eine Aufgabe erledigen | Ein Satz Zweck · Before you start · nummerierte Schritte · Varianten · Troubleshooting · Next steps | Run XSLT, Validate with Schematron |
| **Reference** | Nachschlagen | Ein Satz Umfang · Tabellen · keine Schritte · Verweis auf Guides | Keyboard shortcuts, Command reference |
| **Explanation** | Verstehen, warum | Fließtext, Diagramm, keine Schritte | Threat model, Schema support |
| **Standard page** | Ein Branchenstandard | Bestehende Plugin-Vorlage (Switching it on … Troubleshooting) | ISO 20022 |
| **Section index** | Orientierung | Eyebrow · ein Absatz · Kartenraster zu den Seiten | jede Bereichs-Startseite |

Für alle Seiten: erster Satz sagt, was die Seite leistet; Hinweise nur als Admonition (`note`, `tip`,
`warning`, `info`); Bilder mit Unterschrift; UI-Texte fett und wörtlich; ein Screenshot zeigt den
Ausschnitt, um den es geht, nicht das ganze Fenster.

---

## 3. Gap-Analyse

### 3.1 Zusammenfassung

| Aktion | Seiten | Was |
|---|---|---|
| Verschieben (Inhalt bleibt, teils ergänzt) | 50 | Neuer Ordner, Vorlage und Hinweise anpassen |
| Teilen | 10 | unified-shell, xsd-tools, xml-editor, grid, command-line, transform-panel, projects, validation-panel, administration, whats-new |
| Zusammenführen / auflösen | 4 | xsd-validation, xslt-viewer, xslt-developer, profiled-xml-generation |
| Neu schreiben | 1 + rund 20 neue | index.md; neue Seiten siehe 3.3 |
| Löschen | 1 | xml-editor-features (Weiterleitung) |
| Bleibt ausgeschlossen | 2 | ThreadPoolArchitecture, testing/auto-update-windows |
| An die Website | 1 + Teile | comparison (bis dahin unter Reference); Hero, Download, Feature-Tabellen, Sponsor aus index |

Aufwand: S = bis 1 Stunde, M = halber Tag, L = 1 bis 2 Tage.

### 3.2 Alle bestehenden Seiten

| Nr. | Seite (`docs/`) | Ziel | Aktion | Aufw. |
|---|---|---|---|---|
| 1 | index.md | Docs home | Neu schreiben als Wegweiser nach Rolle; Feature-Tabellen, Normen, Sponsor an die Website; Download-Karten bis zum Website-Start behalten | M |
| 2 | installation.md | get-started/install | Ergänzen: Systemvoraussetzungen, Updates, je Plattform ein Bild; Content-Tabs je Betriebssystem | S |
| 3 | unified-shell.md | mehrere | Teilen, siehe 3.4 | L |
| 4 | keyboard-shortcuts.md | reference/keyboard-shortcuts | Verschieben; wird einzige Quelle, Tabellen auf 4 anderen Seiten entfallen | S |
| 5 | settings.md | reference/settings | Verschieben; Übersicht aller Einstellungsgruppen voranstellen | S |
| 6 | inspector.md | reference/workspace/inspector | Verschieben; Versionshinweis 2.6.0 entfernen | S |
| 7 | file-associations.md | administer/file-associations | Verschieben; von Install verlinken | S |
| 8 | execution-statistics.md | reference/execution-statistics | Verschieben | S |
| 9 | command-line.md | automate/ | Teilen: Übersicht (behält `/command-line/`), 5 Referenzseiten nach Befehlsgruppe, Exit codes and output; „In a CI pipeline" wird CI recipes | M |
| 10 | mcp-server.md | automate/mcp-server | Verschieben | S |
| 11 | ai-assistant.md | guides/ai-assistant | Verschieben; Abschnitt „For administrators" | S |
| 12 | xml-editor.md | guides/edit-xml/ | Teilen: Startseite + „Text, Tree, Grid" + „Formatting"; Excel/CSV-Konverter nach compare-convert; Abschnitte Inspector, Search, Schematron, XPath, Templates, Favorites, Shortcuts auf Verweise kürzen | L |
| 13 | grid.md | guides/edit-xml/grid, guides/json-yaml/grid | Teilen nach XML und JSON; die zwei H2 mit 5.100 Wörtern in Aufgaben gliedern (Tabellen, Filter, Formeln, Sortieren) | M |
| 14 | large-files.md | guides/edit-xml/large-files | Verschieben | S |
| 15 | context-sensitive-intellisense.md | guides/edit-xml/auto-completion | Verschieben, umbenennen; 2.6.0 entfernen | S |
| 16 | query-console.md | guides/transform-query/query-console | Verschieben | S |
| 17 | language-versions.md | reference/standards-versions | Verschieben; mit der Normen-Tabelle aus index zusammenführen | S |
| 18 | template-management.md | guides/edit-xml/templates | Verschieben | S |
| 19 | favorites-system.md | guides/edit-xml/favorites | Verschieben | S |
| 20 | bookmarks.md | guides/edit-xml/bookmarks | Verschieben | S |
| 21 | json-editor.md | guides/json-yaml/ | Verschieben; „retired tab" entfernen; Konvertierung nach compare-convert; eigene Shortcut-Tabelle entfällt | M |
| 22 | xsd-tools.md | guides/design-schemas/ | Teilen in 7 Seiten (Graphic view, Overview, Types, Analysis, Documentation, Sample XML, Flatten); „Supported XSD Features" nach reference/schema-support; Nummerierung entfernen | L |
| 23 | xsddoc-tags.md | reference/xsddoc-tags | Verschieben; `xsddoc-tags.html` durch Redirect-Plugin ersetzen | S |
| 24 | profiled-xml-generation.md | guides/design-schemas/sample-xml | Zusammenführen mit Abschnitt 7 aus xsd-tools | M |
| 25 | xsd-validation.md | guides/validate/xsd, …/folders-projects | Auflösen: Einzeldatei nach „XSD, DTD, RELAX NG", Batch nach „Folders and projects"; „retired tab" entfernen | M |
| 26 | schema-support.md | reference/schema-support | Verschieben; „How to Use Schemas" nach guides/validate | S |
| 27 | schema-library.md | guides/design-schemas/schema-library | Verschieben | S |
| 28 | transform-panel.md | guides/transform-query/run-xslt, reference/workspace/transform-panel, …/external-processors | Teilen: Aufgabe (Running a Transformation), Feldreferenz (SCENARIO … START als normale Überschriften), External processors als eigene Seite | L |
| 29 | projects.md | guides/projects, reference/project-file | Teilen: Anleitung und Dateiformat; Bilder ergänzen (heute 0) | M |
| 30 | xslt-viewer.md | guides/transform-query/run-xslt | Auflösen: Quick Start wird Einstieg der Anleitung, „XSLT Examples" eigene Beispielseite; Titel/Navigation angleichen | M |
| 31 | xslt-developer.md | guides/transform-query/ | Auflösen: Live Preview und Parameter nach run-xslt, XQuery nach query-console, Profiling nach profile, XSpec nach xspec; Debugger-Abschnitt entfällt | M |
| 32 | debugger.md | guides/transform-query/debug | Verschieben; nummerierte Schritte für den ersten Lauf, Abschluss „Next steps"; URL `/debugger/` weiterleiten | M |
| 33 | xproc.md | guides/transform-query/xproc | Verschieben, ausbauen (heute 45 Zeilen): Beispiel-Pipeline, Bild | S |
| 34 | pdf-generator.md | guides/publish/pdf | Verschieben | S |
| 35 | validation-panel.md | guides/validate/, …/problems | Teilen: Bereichs-Startseite mit dem Validierungsablauf als Diagramm, „Working with problems"; Feldbeschreibung nach reference | M |
| 36 | schematron-support.md | guides/validate/schematron | Verschieben; „retired tab" entfernen; Bilder ergänzen (heute 0) | S |
| 37 | schematron-quick-fixes.md | guides/validate/schematron-quick-fixes | Verschieben | S |
| 38 | fundsxml-extensions.md | industry/fundsxml | Verschieben; auf die Standard-Vorlage bringen; „Grid Formulas and Filters" nach guides/edit-xml/grid | M |
| 39 | industry-plugins.md | industry/ (Startseite) | Verschieben; Kartenraster; Titel „Industry standards" | S |
| 40 | plugin-iso20022.md | industry/iso20022 | Verschieben | S |
| 41 | plugin-ebinterface.md | industry/ebinterface | Verschieben | S |
| 42 | plugin-findatex.md | industry/findatex | Verschieben | S |
| 43 | plugin-xbrl.md | industry/xbrl | Verschieben | S |
| 44 | plugin-pk-meldewesen.md | industry/pk-meldewesen | Verschieben | S |
| 45 | plugin-kag-meldewesen.md | industry/kag-meldewesen | Verschieben; an die Vorlage angleichen | S |
| 46 | plugin-esma-reporting.md | industry/esma-reporting | Verschieben; an die Vorlage angleichen | S |
| 47 | identifier-lookup.md | industry/identifier-lookup | Verschieben | S |
| 48 | plugins.md | industry/manage-extensions | Verschieben; Admin-Schlüssel nach administer/policies | S |
| 49 | plugin-development.md | extend/writing-plugins | Verschieben | S |
| 50 | SECURITY.md | administer/security-features | Verschieben, Slug klein; `/SECURITY/` weiterleiten | S |
| 51 | digital-signatures.md | guides/sign-encrypt/signatures | Verschieben | S |
| 52 | xml-encryption.md | guides/sign-encrypt/encryption | Verschieben | S |
| 53 | telemetry.md | administer/privacy-telemetry | Verschieben; `/telemetry/` weiterleiten (`TelemetryInfoDialog`) | S |
| 54 | code-signing-policy.md | administer/code-signing | Verschieben | S |
| 55 | threat-model.md | administer/threat-model | Verschieben | S |
| 56 | offline-operation.md | administer/offline | Verschieben | S |
| 57 | administration.md | administer/policies, administer/deploy | Teilen: Richtlinien und „Silent installation" | S |
| 58 | whats-new.md | reference/whats-new/ | Teilen: aktuelle Version auf der Startseite, eine Seite je Hauptversion; „Where the former tabs went" in die FAQ | M |
| 59 | faq.md | reference/faq | Verschieben; in „Questions" und „Troubleshooting" gliedern | S |
| 60 | technology-stack.md | extend/technology-stack | Verschieben | S |
| 61 | comparison.md | Website `/compare` | Version auf 3.4.0; bis zum Website-Start unter Reference, danach Weiterleitung | S |
| 62 | accessibility.md | reference/accessibility | Verschieben; Audit auf aktuellen Stand; „For developers" nach extend | M |
| 63 | licenses.md | reference/licenses | Verschieben | S |
| 64 | contributing.md | extend/contributing | Verschieben | S |
| 65 | code-of-conduct.md | extend/code-of-conduct | Verschieben | S |
| 66 | security-policy.md | extend/security-policy | Verschieben; Titel „Reporting a vulnerability" zur Abgrenzung von Security features | S |
| 67 | xml-editor-features.md | – | Löschen, Weiterleitung auf guides/edit-xml/ | S |
| 68 | ThreadPoolArchitecture.md | – | Bleibt in `exclude_docs` (intern) | – |
| 69 | testing/auto-update-windows.md | – | Bleibt in `exclude_docs` (intern) | – |

### 3.3 Neue Seiten

| Seite | Inhalt | Quelle | Aufw. |
|---|---|---|---|
| Docs home | Sechs Karten nach Rolle, Suche, „What's new" in einer Zeile | neu, Teile aus index | M |
| 7 Bereichs-Startseiten | Ein Absatz und Kartenraster | neu | je S |
| First steps | Beispiel öffnen (`ExampleFiles`), Schema binden, Fehler beheben, XSLT ausführen | „Your First Steps" aus index, ausgebaut | M |
| Tour of the workspace | Beschriftetes Übersichtsbild, Ansichten, Tabs | unified-shell (Overview, View Modes, Tabs, Welcome) | M |
| Coming from another editor | Tabelle „dort heißt es … hier findest du es …" | neu, „Where the former tabs went" als Muster | M |
| Get help | FAQ, Issue melden, Beispiele, Diskussionen | „Need Help?" aus index | S |
| Search & replace in files | Search-Panel | unified-shell „Search Panel" | S |
| Compare & merge | Text- und XML-Vergleich, Ordner, Drei-Wege-Merge | unified-shell „Compare & Merge" | M |
| Convert formats | XML ↔ JSON ↔ YAML, Excel/CSV | json-editor, xml-editor | M |
| Sign & encrypt (Startseite) | Seite und Aktionen im Überblick | unified-shell „Sign & Encrypt Page" | S |
| Profile, XSpec | je eine Anleitung | xslt-developer | je S |
| CI recipes | GitHub Actions, GitLab, JUnit-Report, Exit-Codes | command-line „In a CI pipeline", ausgebaut | M |
| Glossary | Activity, Panel, Inspector, Scenario, Binding, Extension, View mode … | neu | M |
| Workspace reference | Menüs, Toolbar, Statusleiste, Export View | unified-shell | M |

### 3.4 Aufteilung von `unified-shell.md` (39 Abschnitte)

| Abschnitte | Ziel |
|---|---|
| Overview · Getting Started · View Modes · Working with Editor Tabs · Welcome / Dashboard · Drag and Drop | get-started/workspace-tour |
| New File Dialog · Every Picked File Opens in the Editor · Files Changed Outside the Editor · Tools that read the saved file | guides/edit-xml/ (Working with files) |
| Menu Bar · Toolbar · Panel Toggles · Editor Toolbar Document Actions · Status Bar · Supported File Types · Help Menu & About · After an Export · Export View | reference/workspace |
| Explorer Panel · Favorites Panel · Bookmarks Panel | reference/workspace, Anleitungsteile zu favorites / bookmarks |
| Search Panel | guides/edit-xml/search-in-files |
| Compare & Merge | guides/compare-convert/compare-merge |
| Sign & Encrypt Page | guides/sign-encrypt/ |
| Schema Panel: Sample-Data Generation | guides/design-schemas/sample-xml |
| Jump to Validation Errors | guides/validate/problems |
| XSD Views & Tools · XSLT Features · XProc Pipelines · Transform Panel · Validation Panel · PDF / FOP Panel · FundsXML Panel · Schema Library Page · Settings Page · Keyboard Shortcuts · Properties Inspector · Query Console | Entfallen (20 bis 30 Wörter, verweisen nur weiter) |
| Where the former tabs went | reference/faq |

`/unified-shell/` leitet auf die Tour weiter; die explizit gesetzten Anker (`#export-view`,
`#signature-panel`, `#schema-library-page`, `#editor-tabs`, …) brauchen je eine Weiterleitung auf
die neue Seite.

---

## 4. Grafisches Zielbild

Die Doku übernimmt die Regeln der Logo-Suite S1 und die Bausteine des Website-Prototyps, bleibt aber
ein Handbuch: ruhiger, dichter, ohne Hero.

### 4.1 Tokens

| Rolle | Hell | Dunkel | Herkunft |
|---|---|---|---|
| Kopfzeile, Tabs, Fußzeile | `#2a284c` | `#2a284c` | Navy S1, App-Chrome |
| Seite / Fläche | `#ffffff` / `#f5f7fa` | `#17162b` / `#201f3a` | Website-Prototyp |
| Text / gedämpft | `#2a284c` / `#5a6472` | `#eef0f8` / `#b4b9d0` | Prototyp, App |
| Linie | `#dde1e7` | `#3a3960` | App `-fxt-border` |
| Link, Aktion | `#1068c4` (Text), `#1373d9` (Fläche) | `#7db8fa` | App `-fxt-primary-text` / `-fxt-primary` |
| Akzent | `#f08c2e` | `#f08c2e` | Orange S1; nur als Linie, Marke, Fokusring, nie als Text auf Weiß |
| Hinweis note / tip / warning / danger | `#1373d9` / `#2f9e44` / `#f08c00` / `#e03131` | `#4c9bf5` / `#51cf66` / `#fab005` / `#ff6b6b` | Semantikfarben der App |
| Schrift | Inter 400/500/600/700 | – | App, Wortmarke |
| Code, Eyebrow, Versionen | JetBrains Mono 500/700 | – | App, Kurzmarke |
| Radius | 14 px Karten, 12 px Bilder, 10 px Knöpfe | – | Prototyp |

Bereichsfarben (nur Eyebrow und Kartensymbol der Bereichs-Startseite, aus den Workflow-Farben der App):
Validate `#2f9e44`, Transform `#f08c2e`, Schemas `#6f42c1`, Sign & encrypt `#1098ad`, PDF `#d6336c`,
Industry `#12a594`.

### 4.2 Bausteine

| Baustein | Festlegung |
|---|---|
| Kopfzeile | Flaches Navy, Lockup „auf dunkel" als SVG, dahinter „Docs" in JetBrains Mono; Suche; Links „Website" und „Download" rechts; kein Verlauf |
| Tabs | 7 Bereiche auf Navy, aktiver Tab mit 2 px oranger Unterlinie |
| Banner | Entfällt als Dauerelement; nur bei einem neuen Release, Navy mit hellblauem Link |
| Docs home | Eyebrow „DOCUMENTATION", Titel, ein Satz, darunter sechs Karten nach Rolle und eine Zeile „New in 3.4"; keine Feature-Tabellen |
| Bereichs-Startseite | Eyebrow in Bereichsfarbe, ein Absatz, Kartenraster (Symbol aus Bootstrap Icons, Titel, eine Zeile) |
| Inhaltsseite | Typ-Marke über dem Titel (GUIDE, REFERENCE, TUTORIAL), erster Satz als Lead in 18 px, rechts das Inhaltsverzeichnis |
| Schritte | Nummerierte Liste; UI-Texte fett; Tastenkürzel als `kbd` (`pymdownx.keys`) |
| Hinweise | Nur Admonitions, vier Typen, linke Linie 3 px in Semantikfarbe, Fläche `#f5f7fa` |
| Verweise | „Next steps" als zwei bis drei Karten am Seitenende; Listenzeichen und Pfeile mit `›` |
| Bilder | Rahmen 1 px, Radius 12 px, Unterschrift in JetBrains Mono 12 px; Ausschnitt statt Vollbild; nummerierte Marken in Orange, wo ein Bild mehrere Stellen erklärt |
| Dunkelmodus | Light/Dark-Paare für alle Bilder auf Tutorial- und Tour-Seiten; übrige helle Bilder bekommen im Dunkelmodus einen hellen Rahmen statt Vollkontrast |
| Diagramme | Mermaid (über `pymdownx.superfences`) für Validierungsablauf, Projekt-Bindungen, Debug-Ablauf; Farben aus den Tokens |
| Tabellen | Kopf über Tokens (behebt den Dunkelmodus), Zebra entfällt, Zeilenlinie `--line` |
| Fußzeile | Navy, Lockup, Lizenzzeile, Links GitHub / Website / Sponsor |
| Favicon, Social | Aus `web/` und `social/` der Suite |

### 4.3 Umsetzung in MkDocs Material

- `mkdocs.yml`: `theme.font: {text: Inter, code: JetBrains Mono}`, `theme.logo` und `favicon` auf die SVG aus der Suite, `site_name: FreeXmlToolkit Docs`, Features `navigation.indexes`, `navigation.instant`, `content.action.edit`, `toc.follow`; `navigation.expand` entfällt.
- `docs/overrides/stylesheets/extra.css`: Tokens als `--md-*`- und `--fxt-*`-Variablen für `[data-md-color-scheme="default"]` und `"slate"`; Verläufe und Teal entfernen; Karten, Eyebrow, Typ-Marke, Bildrahmen, Admonitions.
- `docs/overrides/main.html`: Banner nur noch bedingt; `docs/overrides/partials/` für Kopf- und Fußzeile, falls CSS nicht reicht.
- Plugins: `mkdocs-redirects` (alte URLs), später `mkdocs-static-i18n` (Deutsch).

---

## 5. Fahrplan

| Stufe | Inhalt | Dateien | Ergebnis |
|---|---|---|---|
| 1 Erscheinungsbild | Tokens, Schriften, Logo, Favicon, Kopf- und Fußzeile, Admonitions, Tabellenkopf | `extra.css`, `main.html`, `mkdocs.yml`, `docs/img/` | Alte Struktur im neuen Bild; sofort veröffentlichbar |
| 2 Navigation | 7 Bereiche, Ordner, Bereichs-Startseiten, Weiterleitungen, 50 Seiten verschieben | `mkdocs.yml`, `requirements-docs.txt`, `docs/**` | Neue Sitemap, alte Links leiten weiter |
| 3 Inhalte | Teilen und Zusammenführen (14 Seiten), neue Seiten, Vorlagen, Zitat-Notizen zu Admonitions | `docs/**` | Eine Aufgabe = eine Seite |
| 4 Bilder | Neues Logo in der App, dann `xvfb-run ./gradlew docScreenshots`; Light/Dark-Paare; Unterschriften; unbenutzte Bilder löschen | `docs/img/`, `*DocScreenshotGenerator` | Einheitliche Bilder |
| 5 Abgabe an die Website | Download, Vergleich, Sponsor aus der Doku; Umzug auf die Subdomain | `docs/index.md`, `download.js` | Doku ist reines Handbuch |
| später | Deutsche Fassung: Einstieg und die Meldewesen-Seiten zuerst | `mkdocs-static-i18n` | – |

Stufe 1 ist unabhängig und der schnellste sichtbare Gewinn. Stufe 2 und 3 sollten vor dem Umzug
auf die Subdomain abgeschlossen sein, damit Weiterleitungen nur einmal eingerichtet werden.

### Adressen, die weiter funktionieren müssen

| Adresse | Verwendet in | Maßnahme |
|---|---|---|
| `/` | `ProjectLinks.DOCS_URL`, About-Dialog, AppStream-Metainfo, README | bleibt |
| `/xml-editor/#xmlexcelcsv-converter` | `SpreadsheetConverterDialog` | Konstante auf die neue Seite ändern; alte Seite leitet weiter |
| `/telemetry/` | `TelemetryInfoDialog`, README | Weiterleitung, Konstante nachziehen |
| `/command-line/` | `FxtCli --help`, README | Weiterleitung auf automate/ |
| `/SECURITY/`, `/threat-model/` | `.github/SECURITY.md` | Weiterleitung |
| 18 README-Links (`/debugger/`, `/grid/`, `/xsd-tools/`, `/faq/`, …) | `README.md`, `CONTRIBUTING.md`, Issue-Vorlage | Weiterleitung, README nachziehen |

Ältere installierte Versionen der App rufen die alten Adressen auf; deshalb sind Weiterleitungen
nötig, auch wenn die Konstanten geändert werden. Anker werden von `mkdocs-redirects` nicht
weitergeleitet: `xml-editor/` bleibt daher als Seite mit dem Abschnitt bestehen, bis die App-Version
mit der neuen Adresse verbreitet ist.

---

## 6. Offene Punkte

- Logo-Paket: Im Repo liegen noch keine Dateien der Suite S1 (336 Dateien als ZIP im Chat). Für Stufe 1 werden Lockup „auf dunkel", Symbol und Favicons als Dateien gebraucht.
- Reihenfolge mit der Website: Solange sie nicht online ist, bleiben Download-Karten und Vergleich in der Doku.
- Screenshots zeigen bis zu einem App-Release mit neuem Logo das alte Zeichen im Fenster.
- `whats-new/` je Hauptversion teilen oder ganz zur Website (`/news`) geben und in der Doku nur die aktuelle Version führen.
- Deutsch: Umfang der ersten Fassung (Vorschlag: Get started und die drei österreichischen Meldewesen-Seiten).
- Self-Hosting der Schriften statt Google Fonts, passend zu „ohne Tracking" der Website.
