# Drehbücher der Lernvideos – Stichwortfassung

Grobe Vorgaben für die Lernvideos von FreeXmlToolkit. Pro Video: Ziel-Laufzeit, Beispieldateien, Kapitel mit
Stichwörtern. Daraus entstehen beim Umsetzen Sprechtext (DE + EN), `videos/<id>.json` und die Szenario-Klasse.

**Grundsätze**
- Ein Video = eine Aufgabe. Länge 4–8 Minuten; was mehr braucht, wird geteilt.
- Zwei Serien: **Grundlagen** (der Reihe nach ansehen) und **Vertiefung** (einzeln nach Bedarf).
- Jedes Thema kommt nur in einem Video ausführlich vor; andere Videos verweisen darauf (→ Nr.).

**Regeln für diese Datei**
- `Ziel-Laufzeit:` eine Zeit (`6:00`), ungefähr (`ca. 6 min`) oder **leer** = Länge ergibt sich aus den Stichwörtern.
- Optional pro Kapitel eine Zeit in Klammern, z. B. `(~1:30)`; sonst wird die Laufzeit gleichmäßig verteilt.
- Faustregel: 1 Minute ≈ 120 gesprochene Wörter ≈ 3–5 Stichpunkte.
- Kapitel streichen = Zeile löschen; neue Kapitel/Stichpunkte einfach dazuschreiben.
- `Offen:` = was vor der Aufnahme noch zu klären oder vorzubereiten ist.

## Übersicht

| # | Id | Titel | heute (DE) | Ziel-Laufzeit |
|---|----|-------|------------|---------------|
| | | **Serie Grundlagen** | | **54 min** |
| 1 | workspace-tour | Ein Rundgang durch die Arbeitsfläche | 1:13 | 5:00 |
| 2 | edit-xml-text | XML im Text-Editor schreiben | neu | 7:00 |
| 3 | validate-xml | Eine XML-Datei gegen ihr Schema prüfen | 1:14 | 7:00 |
| 4 | xml-grid | Das XML-Grid: ein Dokument als Tabellen bearbeiten | 1:10 | 8:00 |
| 5 | xsd-graphic | Ein XSD-Schema grafisch bearbeiten | 1:29 | 8:00 |
| 6 | xslt-run | Ein XSLT-Stylesheet ausführen | neu (bisher Teil von xslt-debug) | 4:00 |
| 7 | schematron-quick-fixes | Probleme mit Schematron-Schnellkorrekturen beheben | 1:00 | 7:00 |
| 8 | projects | Projekte: Schemas und Szenarien für einen ganzen Ordner | 1:08 | 8:00 |
| | | **Serie Vertiefung** | | **97 min** |
| 9 | xslt-debug | Ein XSLT-Stylesheet debuggen | 1:07 | 8:00 |
| 10 | query-console | XPath und XQuery in der Abfragekonsole | neu | 6:00 |
| 11 | schema-library | Schemas finden, verwalten und erzeugen | neu | 6:00 |
| 12 | compare-merge | Vergleichen und Zusammenführen | neu | 6:00 |
| 13 | convert | XML, JSON, YAML und Excel umwandeln | neu | 6:00 |
| 14 | xsd-types | XSD: Übersicht und Typen | neu | 7:00 |
| 15 | xsd-analysis | XSD: Analyse und Dokumentation | neu | 5:00 |
| 16 | publish-pdf | PDF mit XSL-FO erzeugen | neu | 6:00 |
| 17 | large-files | Sehr große XML-Dateien | neu | 4:00 |
| 18 | xspec | Stylesheets testen mit XSpec | neu | 5:00 |
| 19 | profiler | Langsame Stylesheets und Abfragen finden | neu | 4:00 |
| 20 | xproc | XProc-Pipelines ausführen | neu | 4:00 |
| 21 | command-line | Automatisieren mit der Kommandozeile fxt-cli | neu | 6:00 |
| 22 | sign-encrypt | XML signieren und verschlüsseln | neu | 7:00 |
| 23 | ai-assistant | KI-Assistent und MCP-Server | neu | 7:00 |
| 24 | settings | Einstellungen und Darstellung | neu | 5:00 |
| 25 | administration | FreeXmlToolkit im Unternehmen verwalten | neu | 5:00 |

Summe: 25 Videos, ca. 2,5 Stunden.
Umbau gegenüber heute: `xslt-debug` wird zu 6 (Ausführen) + 9 (Debuggen); `series.json` bekommt die zwei Serien.

---

# Serie Grundlagen

---

## 1 · workspace-tour — Ein Rundgang durch die Arbeitsfläche

Ziel-Laufzeit: 5:00
Beispiel: Erststart, „Beispiel öffnen“ → order.xml, order.xsd, order-summary.xslt

1. Die Startseite – vier Bereiche im Überblick; Neu / Öffnen / Ordner; „Was möchten Sie tun?“; Beispiel öffnen
2. Die Aktivitätsleiste – Explorer, Suche, Validierung, Umwandeln, Schema, PDF; Seiten statt Seitenleiste (Schema-Bibliothek, Einstellungen)
3. Dateien wiederfinden – Explorer-Baum, geöffnete Editoren, Favorit mit Strg D, Zuletzt verwendet
4. Tabs und Ansichten – Tabs, Tab-Kontextmenü; Text / Baum / Grafik, die Stelle bleibt beim Wechsel erhalten
5. Eigenschaften und Statusleiste – Inspector zeigt und ändert Werte; Statusleiste: Position, Typ, Schema, Kodierung
6. Menüs und Platz – Menüleiste, Hilfe → Tastenkürzel; Seitenleiste / Eigenschaften ein- und ausblenden; Wegweiser zu den Videos 2–8

Offen: Drag & Drop aus dem Dateimanager weglassen (unter Xvfb kaum aufnehmbar).

---

## 2 · edit-xml-text — XML im Text-Editor schreiben

Ziel-Laufzeit: 7:00
Beispiel: order.xml + order.xsd; neue leere Datei
Doku: guides/edit-xml/index.md, auto-completion.md, templates.md, find-replace.md, bookmarks.md

1. Eine neue Datei – Neu, Dateityp, Vorlage im Neu-Dialog
2. Autovervollständigung – Elemente nach Schema, Werte je Pfad, Attribute, Tags schließen sich selbst; Strg Leertaste
3. Vorlagen – einfügen, Platzhalter und Parameter, eigene Vorlagen
4. Formatieren und falten – Pretty Print, Code Folding
5. Navigieren – Gehe zu Definition ins Schema, Gehe zu Zeile; Lesezeichen: Klick in den Rand, F2, nummerierte mit Strg Umschalt 1, Lesezeichen-Panel
6. Suchen und Ersetzen – im Dokument, in allen Ansichten; Suche in Dateien
7. Die Baumansicht – Knoten wählen, umbenennen, verschieben; Änderungen von außen → Neu laden

---

## 3 · validate-xml — Eine XML-Datei gegen ihr Schema prüfen

Ziel-Laufzeit: 7:00
Beispiel: order.xml + order.xsd; neu: Ordner mit 5 Bestellungen, 2 davon ungültig

1. Worum es geht – XML + Schema = Vertrag
2. Wie das Schema gefunden wird – xsi:noNamespaceSchemaLocation; Anzeige in der Statusleiste; von Hand binden; Bibliothek (→ 11)
3. Mit F8 prüfen – gültig; Fehler einbauen; Markierung beim Tippen; Problembereich; Klick springt zur Stelle; beheben
4. Mehrere Fehler im Griff – nach Schwere filtern, suchen, gruppieren, kopieren, exportieren
5. Beheben mit der Autovervollständigung – fehlendes Element / Pflichtattribut ergänzen (→ 2)
6. Ganze Ordner prüfen – Panel Validierung, Quellen, Stapel, Ergebnisliste, Bericht; Ausblick Schematron (→ 7)

Offen: Genauen Wortlaut der Xerces-Meldungen bei der Aufnahme prüfen.

---

## 4 · xml-grid — Das XML-Grid: ein Dokument als Tabellen bearbeiten

Ziel-Laufzeit: 8:00
Beispiel: order.xml + order.xsd; neu: orders.xml (mehrere Bestellungen mit je eigenen Artikeln)

1. Warum ein Grid – wiederholte Daten sind im Text mühsam; Umschalten auf Grafik
2. Der Aufbau – Blöcke, Name links / Wert rechts, Attribute; auf-/zuklappen; Zoom, optimale Breiten
3. Wiederholte Elemente als Tabelle – sortieren, kippen; verschachtelte Zellen öffnen (orders.xml)
4. Bearbeiten mit typgerechter Eingabe – Doppelklick, Zahlenfeld, Datumsauswahl; ungültiger Wert → roter Rahmen; Tastatur
5. Zeilen, Spalten und Tabellenkalkulation – Zeile / Spalte einfügen und löschen; nach Excel kopieren, aus Excel einfügen
6. Filter und Formeln – Filter (price > 20); Formel „Summe“ rechnet mit
7. Struktur umbauen – Ziehen und Ablegen; Eingabehilfen-Seitenleiste
8. Zurück zum Text – alles im Quelltext; Rückgängig über alle Ansichten

Offen: Einfügen braucht vorbereitete Zwischenablage. Prüfen, ob Menge 0 im Zahlenfeld eingebbar ist.

---

## 5 · xsd-graphic — Ein XSD-Schema grafisch bearbeiten

Ziel-Laufzeit: 8:00
Beispiel: order.xsd (alle Änderungen entstehen im Video)

1. Das Diagramm lesen – Karten aufklappen; optional = gestrichelt, mehrfach = doppelter Rand; Attribute, Pflicht
2. Benannte Typen und der Typ-Editor – Typrahmen, schreibgeschützt; Doppelklick → Typ-Editor
3. Elemente und Attribute hinzufügen – Dialog: Name, Typauswahl, Vorkommen, Dokumentation
4. Im Eigenschaftenbereich bearbeiten – Kardinalität, Typ und Facetten, Dokumentation, Identitätsbedingung; Bedingungen auf den Karten
5. Ziehen und Ablegen – Ziel-Linie / -Rahmen; ungültiges Ziel rot; Strg-Ziehen kopiert
6. Nur gültige Änderungen – ausgegraute Menüpunkte, Ersetzen-Dialog
7. Speichern und exportieren – „In Schema speichern“, Quelltext, Strg S; Ansicht als PNG / SVG / PDF; mehr zu Typen (→ 14)

Offen: Knopf im Ersetzen-Dialog („Remove type and add sequence“) ist noch nicht übersetzt.

---

## 6 · xslt-run — Ein XSLT-Stylesheet ausführen

Ziel-Laufzeit: 4:00
Beispiel: order.xml + order-summary.xslt

1. Das Umwandeln-Panel – Stylesheet, Eingabe, Ausgabemethode
2. Ausführen und Ergebnis – Ausgabebereich, Dauer; Ergebnis speichern
3. Parameter und Live-Vorschau – Parameter setzen; Eingabe ändern, Ergebnis folgt sofort
4. Andere Prozessoren – Saxon HE 13 hinzufügen, xsltproc; im Panel wählen; Ausblick Debuggen (→ 9)

---

## 7 · schematron-quick-fixes — Probleme mit Schematron-Schnellkorrekturen beheben

Ziel-Laufzeit: 7:00
Beispiel: release/examples/schematron/quickfix-demo.xml + quickfix-demo.sch

1. Wozu Schematron – Regeln, die ein Schema nicht ausdrücken kann
2. Die Regeldatei lesen – pattern, rule, assert, report; sqf:fix am assert
3. Schematron wählen und prüfen – Panel Validierung, F8, Problembereich
4. Die Glühbirne – Korrekturen wählen, anwenden, Alt Enter, Rückgängig
5. Aus der Problemliste – Rechtsklick → Schnellkorrektur; Korrektur mit Eingabe (Verlag)
6. Eine eigene Korrektur schreiben – neue Regel + sqf:fix mit sqf:add; add / delete / replace / stringReplace; während der Eingabe validieren

Offen: Schematron-Auswahl muss im Bild passieren (heute vorab).

---

## 8 · projects — Projekte: Schemas und Szenarien für einen ganzen Ordner

Ziel-Laufzeit: 8:00
Beispiel: Projekt „Shop orders“ (shop.fxtproj, orders/, schemas/, stylesheets/); neu: Szenario „Order totals“ mit XML-Ausgabe + expected/order-1043.xml (absichtlich abweichend)

1. Wozu Projekte – viele Dateien, gleiche Schemas und Transformationen
2. Anlegen und öffnen – Neues Projekt, Explorer mit Projektordnern, Startseite „Projekte“
3. Zuordnungen – Dateien → Schema / Schematron / Szenario; Muster orders/*.xml; Explorer-Kontextmenü, Tooltip
4. Validieren – Schema kommt vom Projekt; F8; Projekt validieren
5. Szenarien – Dialog (${file}, out/${name}.html); ausführen; als Szenario speichern; mehrere Dateien auf einmal
6. Regressionsprüfung – erwartetes Ergebnis, Prüfen, Unterschiede, Übernehmen
7. Teilen – Projektdatei ins Repository; dieselben Prüfungen auf der Kommandozeile (→ 21)

Offen: Dateidialog bei „Neues Projekt“ im Szenario bedienbar machen. Projekt muss für die Startseite in „Zuletzt“ stehen.

---

# Serie Vertiefung

---

## 9 · xslt-debug — Ein XSLT-Stylesheet debuggen

Ziel-Laufzeit: 8:00
Beispiel: order.xml; neu: order-debug.xslt (Parameter currency, Variable total, Funktion f:line-total, xsl:message, ein nie laufendes Template)
Voraussetzung: Video 6

1. Haltepunkt und Start – Klick in den Rand; Debuggen; Debug-Bereich im Überblick; Eingabe anzeigen
2. Schritt für Schritt – Fortsetzen, Überspringen, Hineinspringen, Herausspringen; Neu starten / Stoppen
3. Den Zustand ansehen – Variablen, Inline-Werte, Hover; Überwachung; Aufrufliste; Auswerten Alt F8
4. Gezielt anhalten – bedingter Haltepunkt, Trefferzahl, Logpoint, Bis zum Cursor (Strg F10), Stummschalten
5. Ausgabe und Abdeckung – Ausgabe bisher, Sprung zur Quelle; nie gelaufene Zeilen
6. Auch für XQuery und Schematron – kurz zeigen; Ausblick Profiler (→ 19)

Offen: Rand-Kontextmenü der Haltepunkte und Trefferzahl-Optionen noch nicht übersetzt.

---

## 10 · query-console — XPath und XQuery in der Abfragekonsole

Ziel-Laufzeit: 6:00
Beispiel: order.xml; neu: eine .xq-Datei

1. Die Konsole öffnen – aus der Kopfleiste, Aufbau, Eingabe-Auswahl (Dokument, Datei, Ordner)
2. XPath – Ausdruck, Autovervollständigung, Treffer im Dokument markieren und anspringen; Fehlermeldungen
3. XQuery – mehrzeilig, FLWOR, Ergebnis als XML; Variablen, trace
4. Abfragedokumente – .xq-Datei ausführen
5. Merken – Snippets speichern / laden, Beispiele; Sprachversionen im Expertenmodus

---

## 11 · schema-library — Schemas finden, verwalten und erzeugen

Ziel-Laufzeit: 6:00
Beispiel: order.xml (ohne Schema-Angabe) + order.xsd; der mitgelieferte Katalog

1. Woher kommt das Schema? – lokale Datei, Bibliothek, Download
2. Die Schema-Bibliothek – Zuordnung Namespace / Dateiname → Schema; mitgelieferte Standards, Cache, offline
3. XML-Kataloge – Katalog einbinden, mitgeliefertes Beispiel
4. Schema aus XML erzeugen – aus dem Dokument oder mehreren Dateien, danach prüfen
5. Beispiel-XML aus einem Schema – Schnellstart, Profile; DTD und RELAX NG werden auch unterstützt

---

## 12 · compare-merge — Vergleichen und Zusammenführen

Ziel-Laufzeit: 6:00
Beispiel: neu: zwei Fassungen von order.xml, ein Ordnerpaar, drei Fassungen für den Merge

1. Warum XML anders vergleichen – Text zeigt Rauschen (Einrückung, Attributreihenfolge)
2. Als XML vergleichen – strukturelle Unterschiede, Verschiebungen
3. Unwichtiges ausblenden – XPaths ignorieren, Zahlen mit Toleranz, Platzhalter
4. Ordner vergleichen – neu / gelöscht / geändert
5. Drei Fassungen zusammenführen – Konflikte lösen; compare / merge auf der Kommandozeile

---

## 13 · convert — XML, JSON, YAML und Excel umwandeln

Ziel-Laufzeit: 6:00
Beispiel: order.xml; neu: eine kleine Excel-Datei mit Artikeln

1. XML ↔ JSON ↔ YAML – strukturierte vs. W3C-Abbildung; JSON / YAML in Text, Baum, Grid
2. XML nach Excel / CSV – Zeile pro Knoten oder Tabelle pro Datensatz; Vorschau
3. Excel-Aussehen und Typen – Kopfzeile, Filter, Zahlen und Datum
4. Von Excel zurück nach XML – Layout wird erkannt; fremde Tabelle wird zu Datensätzen
5. Hin und zurück – geänderte Tabelle einlesen und prüfen; convert auf der Kommandozeile

---

## 14 · xsd-types — XSD: Übersicht und Typen

Ziel-Laufzeit: 7:00
Beispiel: order.xsd
Voraussetzung: Video 5

1. Die Ansicht Übersicht – alle globalen Komponenten, einfügen, sortieren, verschieben
2. Umbenennen mit Verweisen; Imports, Includes, Namespaces
3. Einfache Typen – Einschränkung, Facetten (Muster, Aufzählung, Länge, Ziffern), Liste, Union
4. Komplexe Typen – Typ-Editor, Erweiterung / Einschränkung, abstrakt, Typbibliothek
5. XSD 1.1 – Assertions, Alternativen

---

## 15 · xsd-analysis — XSD: Analyse und Dokumentation

Ziel-Laufzeit: 5:00
Beispiel: ein größeres Schema (z. B. FundsXML)

1. Schema-Analyse – Statistik, Typen, Qualitätsprüfungen, Identitätsbedingungen, XPath-Prüfung
2. Bericht – HTML / PDF / Excel
3. Dokumentation erzeugen – HTML / PDF / Word, Optionen, Sprache, technische Notizen
4. Aufräumen – Includes einbetten (glätten), ungenutzte Typen entfernen; xsd-doc auf der Kommandozeile

---

## 16 · publish-pdf — PDF mit XSL-FO erzeugen

Ziel-Laufzeit: 6:00
Beispiel: neu: order.xml + ein kleines XSL-FO-Stylesheet (Rechnung)

1. Wie es funktioniert – XML + Stylesheet → FO → PDF
2. Das PDF/FOP-Panel – Dateien, Metadaten, Optionen (z. B. PDF/A)
3. Erzeugen und ansehen – Vorschau, Meldungen von FOP verstehen
4. Fehler finden – Stylesheet debuggen (→ 9), FO ansehen
5. Externe FO-Prozessoren – Antenna House / XEP / eigener Befehl; Szenario mit PDF (→ 8), fop auf der Kommandozeile

---

## 17 · large-files — Sehr große XML-Dateien

Ziel-Laufzeit: 4:00
Beispiel: neu: erzeugte XML-Datei mit einigen hundert MB (Skript)

1. Wann der Viewer kommt – ab 50 MB, sofort lesbar; Text blättern und suchen
2. Struktur – Baum seitenweise, Gehe zu XPath, Datensätze nach CSV / Excel
3. Einen Teil bearbeiten – Fenster im Grid, Übernehmen / Verwerfen
4. Statistik und Validierung – Werte je Pfad, Prüfung gegen XSD im Strom

---

## 18 · xspec — Stylesheets testen mit XSpec

Ziel-Laufzeit: 5:00
Beispiel: order.xml + order-summary.xslt; neu: order-summary.xspec

1. Warum testen – Stylesheets ändern sich, Ergebnisse sollen bleiben
2. Ein erster Test – Szenario, Erwartung
3. Ausführen und lesen – bestanden / fehlgeschlagen, Unterschied
4. XQuery und Schematron testen; xspec auf der Kommandozeile

---

## 19 · profiler — Langsame Stylesheets und Abfragen finden

Ziel-Laufzeit: 4:00
Beispiel: order-summary.xslt; neu: größere Eingabe mit einigen tausend Artikeln

1. Profiling-Lauf – im Umwandeln-Panel; XQuery über Ausführen ▾
2. Das Profil lesen – Gesamt- und Eigenzeit, Aufrufbaum, Zeilen
3. Die Flammengrafik – breite Balken = viel Zeit
4. Beheben und neu messen; transform --profile auf der Kommandozeile

---

## 20 · xproc — XProc-Pipelines ausführen

Ziel-Laufzeit: 4:00
Beispiel: mitgelieferte XProc-Beispiele

1. Was eine Pipeline ist – Schritte hintereinander (prüfen, umwandeln, speichern)
2. Eine erste Pipeline – .xpl öffnen, ausführen
3. Ergebnis lesen – Ausgaben, Fehler
4. Grenzen – nicht verfügbare Schritte; Kommandozeile

---

## 21 · command-line — Automatisieren mit der Kommandozeile fxt-cli

Ziel-Laufzeit: 6:00
Beispiel: Ordner mit Bestellungen, Projekt „Shop orders“ (→ 8)

1. Warum Kommandozeile – dieselben Prüfungen ohne Oberfläche; wo fxt-cli liegt, --help
2. validate – Datei, Ordner, Projekt, Schematron; Exit-Codes
3. transform und query – XSLT mit Parametern, XPath wie grep
4. scenario --check – Regressionsprüfung, JUnit-Bericht
5. In der CI – Rezept für GitHub Actions / GitLab

Offen: Terminal-Aufnahme oder Textkarten?

---

## 22 · sign-encrypt — XML signieren und verschlüsseln

Ziel-Laufzeit: 7:00
Beispiel: order.xml; Beispiele aus release/examples/encryption

1. Wozu – Echtheit und Vertraulichkeit; Aufbau der Seite, Schlüsselspeicher
2. Zertifikat erstellen – RSA / EC, Angaben, Passwort
3. Signieren und prüfen – Signaturarten; geänderte Datei → Prüfung schlägt fehl
4. Verschlüsseln – Element / Inhalt / Dokument; Empfänger (Zertifikat, Schlüssel, Passwort)
5. Entschlüsseln und untersuchen – Inspect; Element verschlüsseln aus dem Editor-Menü
6. Kommandozeile – sign, verify, encrypt, decrypt

---

## 23 · ai-assistant — KI-Assistent und MCP-Server

Ziel-Laufzeit: 7:00
Beispiel: order.xml mit einem Fehler; ein eingerichteter Anbieter

1. Was der Assistent kann – Vorschläge werden geprüft, angewendet wird nur von Ihnen
2. Einrichten und Datenschutz – Anbieter, Schlüssel; Datenstufen, Vorschau vor dem Senden
3. Einen Fehler erklären und beheben – Vorschlag mit Prüfbericht, übernehmen, Rückgängig
4. Frage → XPath / XQuery – in der Abfragekonsole
5. Stylesheet aus einem Beispiel – erzeugen, mit Nachfragen verfeinern
6. MCP-Server – fxt-cli mcp für externe Agenten; nur freigegebene Ordner, schreibt nichts

Offen: Welcher Anbieter / welches Modell für die Aufnahme (reproduzierbar, ohne Kosten)?

---

## 24 · settings — Einstellungen und Darstellung

Ziel-Laufzeit: 5:00
Beispiel: –

1. Die Seite Einstellungen – Gruppen, Suche
2. Darstellung – Hell / Dunkel / System, Akzentfarbe, Textgröße, Dichte, hoher Kontrast, Sprache
3. Editor – Schrift, Zeilenabstand, Syntaxfarben, Zoom mit Strg Mausrad
4. Netzwerk und Updates – Proxy, Verbindung testen; Update-Prüfung
5. Datenschutz – Telemetrie ansehen und abschalten

Offen: Darstellung danach wieder auf Hell / Blau setzen.

---

## 25 · administration — FreeXmlToolkit im Unternehmen verwalten

Ziel-Laufzeit: 5:00
Beispiel: neu: policy.properties / defaults.properties
Zielgruppe: Administratoren

1. Richtlinien – policy.properties (gesperrt) und defaults.properties (Vorgabe); wo die Dateien liegen
2. Gesperrte Einstellungen in der Anwendung erkennen
3. Funktionen abschalten – Updates, KI, externe Prozessoren, Plugins, Online-Abfragen
4. Installation – stille Installation, Dateizuordnungen, Offline-Betrieb
5. Sicherheit – XXE- und Erweiterungsschutz, Telemetrie für alle abschalten
