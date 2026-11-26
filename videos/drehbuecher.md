# Drehbücher der Lernvideos – Stichwortfassung

Vorgaben für die Lernvideos von FreeXmlToolkit. Pro Video: Ziel-Laufzeit, Beispieldateien, Kapitel mit Zeit und
Szenen als Stichpunkte. Daraus entstehen beim Umsetzen Sprechtext (DE + EN), `videos/<id>.json` und die Szenario-Klasse.

**Grundsätze**
- Ein Video = eine Aufgabe. Länge 4–8 Minuten; was mehr braucht, wird geteilt.
- Zwei Serien: **Grundlagen** (der Reihe nach ansehen) und **Vertiefung** (einzeln nach Bedarf).
- Jedes Thema kommt nur in einem Video ausführlich vor; andere Videos verweisen darauf (→ Nr.).

**Wie die Länge entsteht (gemessen an den ersten neuen Filmen)**
- Die Stimme spricht etwa **160 Wörter pro Minute**, Pausen eingerechnet (gemessen an Nr. 2: 837 Wörter → 5:26). Titel- und Schlusskarte zusammen ≈ 0:20.
- Die ersten Filme wurden zu kurz (2:30–3:10 statt 4–8 min), weil jeder Stichpunkt nur einen Satz bekam (≈ 15 s).
- Ab jetzt ist **ein Stichpunkt = eine Szene ≈ 30 s ≈ 80 Wörter** mit drei Teilen:
  **zeigen** (was passiert im Bild) · **erklären** (wozu, was man sieht) · **Tipp oder typischer Fehler**.
- Faustregel: **2 Stichpunkte pro Minute**. Die Kapitelzeiten in Klammern summieren sich zur Ziel-Laufzeit.

**Regeln für diese Datei**
- `Ziel-Laufzeit:` eine Zeit (`6:00`), ungefähr (`ca. 6 min`) oder **leer** = Länge ergibt sich aus den Stichpunkten.
- Kapitelzeit `(1:30)` = Vorgabe für das Kapitel; ohne Zeit wird die Laufzeit gleichmäßig verteilt.
- Kapitel oder Stichpunkt streichen = Zeile löschen; Neues einfach dazuschreiben (dann Zeiten anpassen).
- `Stand:` = was es heute gibt. `Offen:` = was vor der Aufnahme noch zu klären oder vorzubereiten ist.

## Übersicht

| # | Id | Titel | Stand heute (DE) | Ziel |
|---|----|-------|------------------|------|
| | | **Serie Grundlagen** | | **54 min** |
| 1 | workspace-tour | Ein Rundgang durch die Arbeitsfläche | online, 4:48 | 5:00 |
| 2 | edit-xml-text | XML im Text-Editor schreiben | online, 6:18 | 7:00 |
| 3 | validate-xml | Eine XML-Datei gegen ihr Schema prüfen | online, 6:38 | 7:00 |
| 4 | xml-grid | Das XML-Grid: ein Dokument als Tabellen bearbeiten | online, 7:32 | 8:00 |
| 5 | xsd-graphic | Ein XSD-Schema grafisch bearbeiten | online, 7:19 | 8:00 |
| 6 | xslt-run | Ein XSLT-Stylesheet ausführen | online, 3:56 | 4:00 |
| 7 | schematron-quick-fixes | Probleme mit Schematron-Schnellkorrekturen beheben | online, 6:06 | 7:00 |
| 8 | projects | Projekte: Schemas und Szenarien für einen ganzen Ordner | online, 6:09 | 8:00 |
| | | **Serie Vertiefung** | | **97 min** |
| 9 | xslt-debug | Ein XSLT-Stylesheet debuggen | online, 6:24 | 8:00 |
| 10 | query-console | XPath und XQuery in der Abfragekonsole | online, 4:28 | 6:00 |
| 11 | schema-library | Schemas finden, verwalten und erzeugen | online, 4:45 | 6:00 |
| 12 | compare-merge | Vergleichen und Zusammenführen | online, 4:28 | 6:00 |
| 13 | convert | XML, JSON, YAML und Excel umwandeln | online, 4:23 | 6:00 |
| 14 | xsd-types | XSD: Übersicht und Typen | online, 5:12 | 7:00 |
| 15 | xsd-analysis | XSD: Analyse und Dokumentation | online, 3:36 | 5:00 |
| 26 | xsd-documentation | Ein Schema als Website dokumentieren | in Arbeit | 8:00 |
| 16 | publish-pdf | PDF mit XSL-FO erzeugen | online, 3:06 | 6:00 |
| 17 | large-files | Sehr große XML-Dateien | online, 3:05 | 4:00 |
| 18 | xspec | Stylesheets testen mit XSpec | online, 2:43 | 5:00 |
| 19 | profiler | Langsame Stylesheets und Abfragen finden | online, 5:08 | 4:00 |
| 20 | xproc | XProc-Pipelines ausführen | online, 2:17 | 4:00 |
| 21 | command-line | Automatisieren mit der Kommandozeile fxt-cli | online, 2:46 | 6:00 |
| 22 | sign-encrypt | XML signieren und verschlüsseln | online, 3:16 | 7:00 |
| 23 | ai-assistant | KI-Assistent und MCP-Server | online, 3:01 | 7:00 |
| 24 | settings | Einstellungen und Darstellung | online, 2:28 | 5:00 |
| 25 | administration | FreeXmlToolkit im Unternehmen verwalten | online, 3:09 | 5:00 |

Summe: 25 Videos, ca. 2,5 Stunden. Die Filme 1, 2, 3, 4 und 6 werden mit diesen Drehbüchern neu aufgenommen.

---

# Serie Grundlagen

---

## 1 · workspace-tour — Ein Rundgang durch die Arbeitsfläche

Ziel-Laufzeit: 5:00 · Stand: neu, 2:59 – wird verlängert
Beispiel: Erststart, „Beispiel öffnen“ → order.xml, order.xsd, order-summary.xslt

1. Die Startseite (1:00)
   - Erster Start: was FreeXmlToolkit ist (XML, XSD, XSLT, Schematron, JSON in einer Anwendung), Startseite als Ausgangspunkt
   - Karten Neu / Öffnen / Ordner / URL; Liste „Was möchten Sie tun?“ führt direkt ins passende Werkzeug; Tipp: Zuletzt verwendete Dateien
2. Die vier Bereiche (0:30)
   - Beispiel öffnen, dann Aktivitätsleiste, Seitenleiste, Editor, Eigenschaften, Menü- und Statusleiste im Überblick
3. Die Aktivitätsleiste (1:00)
   - Explorer, Suche, Validierung, Umwandeln, Schema, PDF – je ein Satz, wofür; farbige Markierung zeigt das aktive Werkzeug
   - Seiten statt Seitenleiste: Schema-Bibliothek, Signieren, Einstellungen öffnen sich in der Mitte; zurück über den Tab
4. Dateien wiederfinden (0:45)
   - Explorer: geöffnete Editoren, Arbeitsordner als Baum; Favorit mit Strg D, Reiter Favoriten / Zuletzt verwendet, Stern in der Kopfleiste
5. Tabs und Ansichten (0:45)
   - Tabs verschieben, Strg Tab, Tab-Kontextmenü (Neu laden, Pfad kopieren, im Dateimanager zeigen); Text / Baum / Grafik, die Stelle bleibt erhalten
6. Eigenschaften, Statusleiste, Menüs (1:00)
   - Eigenschaften: Attribute, Typ, Dokumentation aus dem Schema, Wert ändern → Text ändert sich mit, Strg Z
   - Statusleiste: Position, Typ, Schema, Kodierung; Menüs mit Kürzeln, Hilfe → Tastenkürzel; Seitenleiste und Eigenschaften ausblenden; Wegweiser zu 2–8

Offen: Drag & Drop aus dem Dateimanager weglassen (unter Xvfb kaum aufnehmbar).

---

## 2 · edit-xml-text — XML im Text-Editor schreiben

Ziel-Laufzeit: 7:00 · Stand: neu, 2:40 – wird verlängert
Beispiel: order.xml + order.xsd; neue leere Datei
Doku: guides/edit-xml/index.md, auto-completion.md, templates.md, find-replace.md, bookmarks.md

1. Eine neue Datei (1:00)
   - Strg N: Dateityp, Vorlage, Speicherort; warum ein Gerüst aus dem Schema Zeit spart (alle Pflichtelemente)
   - Neue Datei aus order.xsd erzeugen und kurz ansehen, was entstanden ist; Tipp: sofort F8 → gültig
2. Autovervollständigung (1:30)
   - Woher der Editor weiß, was erlaubt ist (gebundenes Schema); spitze Klammer → nur erlaubte Elemente an dieser Stelle
   - Leerzeichen im Starttag → Attribute, Pflichtattribute markiert; Schreibmarke landet zwischen den Anführungszeichen
   - Strg Leertaste jederzeit; Werte mit fester Auswahl (Aufzählungen) erscheinen als Liste; Tag schließt sich selbst
3. Vorlagen (0:45)
   - Strg T, Liste und Vorschau, Parameter-Dialog; eigene Vorlagen und Vorlagenordner in den Einstellungen
4. Formatieren und falten (0:45)
   - Komprimieren → einzeilig, Formatieren → sauber eingerückt (typischer Fall: Datei aus einem System, das alles in eine Zeile schreibt)
   - Falten am Rand; Tipp: große Dateien abschnittsweise lesen
5. Navigieren (1:15)
   - Strg-Klick auf einen Elementnamen → Deklaration im Schema; zurück über den Tab
   - Lesezeichen: Klick in den Rand, F2 / Umschalt F2, nummerierte Lesezeichen mit Strg Umschalt Ziffer und Sprung mit Strg Ziffer
   - Aktivität Lesezeichen: alle Lesezeichen aller Dateien, benennen; Lesezeichen wandern beim Bearbeiten mit
6. Suchen und Ersetzen (1:00)
   - Strg F: Treffer markiert, Enter weiter; Optionen (Groß/klein, ganze Wörter, regulärer Ausdruck)
   - Strg H: Ersetzen einzeln oder alle, ein Schritt Rückgängig; Strg Umschalt F: Suche in Ordnern, Treffer nach Datei
7. Die Baumansicht (0:45)
   - Knoten wählen, Kontextmenü: Hinzufügen, Umbenennen, Duplizieren, Löschen; jede Änderung sofort im Text, Stelle bleibt beim Wechsel

---

## 3 · validate-xml — Eine XML-Datei gegen ihr Schema prüfen

Ziel-Laufzeit: 7:00 · Stand: neu, 2:44 – wird verlängert
Beispiel: order.xml + order.xsd; Ordner mit 5 Bestellungen, 2 davon ungültig (wird im Szenario angelegt)

1. Worum es geht (0:45)
   - Schema als Vertrag zwischen Sender und Empfänger; was „wohlgeformt“ und was „gültig“ heißt, an einem Beispiel
2. Wie das Schema gefunden wird (1:15)
   - xsi:noNamespaceSchemaLocation / schemaLocation im Dokument; Statusleiste zeigt „XSD: order.xsd“
   - Ohne Angabe: Schema-Bibliothek nach Namespace (→ 11); von Hand binden über die Statusleiste oder Schema ▾
   - Typischer Fehler: falscher Pfad in schemaLocation → Statusleiste zeigt kein Schema
3. Mit F8 prüfen (1:30)
   - F8 → gültig; Fehler einbauen (Menge „two“), Markierung in der Zeile, Problembereich
   - Meldung lesen: was cvc-… bedeutet, welche Regel, welcher Wert; Klick springt zur Stelle
   - Beheben, F8, gültig; Tipp: „Während der Eingabe validieren“ für sofortige Rückmeldung
4. Mehrere Fehler im Griff (1:30)
   - Drei Fehler auf einmal (Muster, Länge, Nachkommastellen); Zähler filtern nach Fehlern / Warnungen / Hinweisen
   - Suche in den Meldungen; Gruppieren nach Regel bei vielen gleichen Fehlern
   - Kopieren und Export nach Excel; Sprung ins Grid zur fehlerhaften Zelle
5. Beheben mit der Autovervollständigung (0:45)
   - Fehlendes Pflichtattribut: F8 meldet es, Leerzeichen im Starttag schlägt genau dieses vor (→ 2)
6. Ganze Ordner prüfen (1:15)
   - Panel Validierung: Quellen XSD und Schematron, Einzeldatei / Stapel
   - Stapellauf über 5 Dateien: Ergebnisliste, fehlerhafte Datei anklicken → ihre Probleme; Stapelbericht öffnen; Ausblick Schematron (→ 7)

---

## 4 · xml-grid — Das XML-Grid: ein Dokument als Tabellen bearbeiten

Ziel-Laufzeit: 8:00 · Stand: neu, 2:43 – wird verlängert
Beispiel: order.xml + order.xsd; neu: orders.xml (mehrere Bestellungen mit je eigenen Artikeln)

1. Warum ein Grid (0:45)
   - Wiederholte Daten im Text: viel Scrollen, leicht verrutscht; Grid zeigt dieselben Daten als Tabellen
2. Der Aufbau (1:15)
   - Blöcke: Name links, Wert oder Kinder rechts; Attribute als eigene Zeilen, Typsymbole
   - Auf- und zuklappen, Alle aufklappen; Zoom, Optimale Breiten, Umbruch; Tipp: Vorschauzeile eines zugeklappten Blocks
3. Wiederholte Elemente als Tabelle (1:30)
   - Artikel als Zeilen, Attribute als Spalten; sortieren über den Pfeil, Rückgängig stellt die Reihenfolge wieder her
   - Tabelle kippen über die Ecke; Listenansicht statt Tabelle
   - Verschachtelte Zellen (orders.xml): Zelle öffnen zeigt eine Tabelle in der Tabelle, Plus / Minus für eine ganze Spalte
4. Zellen bearbeiten (1:15)
   - Doppelklick, Enter / Escape; Zahlenfeld mit Pfeilen, Datumsauswahl, Auswahlliste bei Aufzählungen
   - Ungültiger Wert → roter Rahmen mit Grund; Tastatur: Pfeile, F2 zum Umbenennen
5. Zeilen, Spalten und Tabellenkalkulation (1:15)
   - Kontextmenü Tabelle: Zeile einfügen / anhängen / löschen, Spalte einfügen und umbenennen
   - Kopieren als tabulatorgetrennter Text nach Excel, Einfügen aus Excel legt neue Zeilen an
6. Filter und Formeln (1:15)
   - Filter mit XQuery-Ausdruck, Trichterzeile, aus- und einschalten, entfernen; Filter ändert nur die Ansicht
   - Formel „Summe“ mit Menge × Preis, rechnet bei jeder Änderung neu; im Dokument speichern als Verarbeitungsanweisung
7. Struktur umbauen (0:45)
   - Ziehen und Ablegen mit Ziellinie; Eingabehilfen-Seitenleiste: Geschwister, Kinder, Werte aus dem Schema
8. Zurück zum Text (0:00–0:30, im Rest enthalten)
   - Alle Änderungen im Quelltext, Rückgängig über alle Ansichten

---

## 5 · xsd-graphic — Ein XSD-Schema grafisch bearbeiten

Ziel-Laufzeit: 8:00 · Stand: alte Kurzfassung 1:29
Beispiel: order.xsd (alle Änderungen entstehen im Video)

1. Warum grafisch (0:45)
   - Verschachtelte Typen im Quelltext schwer zu überblicken; Ansicht Grafik zeigt die Struktur auf einen Blick
2. Das Diagramm lesen (1:30)
   - Karten aufklappen; Name und Typ; Sequenz-Symbol zwischen den Kindkarten (Reihenfolge), Auswahl (choice) zum Vergleich
   - Optional = gestrichelt, mehrfach = doppelter Rand mit Kardinalität (0..1, 1..∞); Attribute mit @ und „req“
   - Titel-Chip oben links mit Art, Typ und Inhaltsmodell; Zoom, Verschieben, Übersicht behalten
3. Benannte Typen und der Typ-Editor (1:00)
   - Typrahmen: Inhalt kommt aus einem benannten Typ, hier schreibgeschützt (Hinweis in den Eigenschaften)
   - Doppelklick auf den Rahmentitel öffnet den Typ-Editor als eigenen Tab; Änderungen gelten überall, wo der Typ verwendet wird
4. Elemente und Attribute hinzufügen (1:30)
   - Rechtsklick → Hinzufügen → Element: Name mit Prüfung (keine Leerzeichen), Vorkommen min/max
   - Typauswahl: eigene einfache und komplexe Typen, eingebaute Datentypen mit Suche; Bereich Mehr (Standardwert, fester Wert)
   - Attribut mit Verwendung optional/Pflicht und Dokumentation; neue Karte erscheint sofort
5. Im Eigenschaftenbereich bearbeiten (1:15)
   - Kardinalität ändern, Typ und Facetten (Muster PO und vier Ziffern, Länge), Dokumentation in mehreren Sprachen
   - Identitätsbedingung unique für sku anlegen; Bedingungen auf den Karten und auf den Zielknoten anzeigen
6. Ziehen und Ablegen (0:45)
   - Ziellinie davor/danach, Rahmen = hinein; ungültiges Ziel rot mit Begründung; Strg-Ziehen kopiert
7. Nur gültige Änderungen (0:30)
   - Ausgegraute Menüpunkte, Ersetzen-Dialog mit Erklärung: der Editor erzeugt nie ein ungültiges Schema
8. Speichern und exportieren (0:45)
   - „In Schema speichern“, Quelltext prüfen, Strg S; Ansicht als PNG / SVG / PDF, als Bild kopieren; mehr zu Typen (→ 14)

Offen: Knopf im Ersetzen-Dialog („Remove type and add sequence“) ist noch nicht übersetzt.

---

## 6 · xslt-run — Ein XSLT-Stylesheet ausführen

Ziel-Laufzeit: 4:00 · Stand: neu, 3:08 – wird verlängert
Beispiel: order.xml + order-summary.xslt (mit Parameter currency)

1. Das Werkzeug Umwandeln (1:00)
   - Was XSLT tut (aus XML wird HTML, Text, anderes XML); Stylesheet wählen über Ändern, Menü mit Datei oder offenen Tabs, Ablegen auf die Zeile
   - Eingabe = aktiver Editor, Ausgabemethode Automatisch / XML / Text / JSON, Einrücken; Start: Templates, Modus, benanntes Template
2. Ausführen und Ergebnis (1:00)
   - Transformation ausführen, Vorschau bei HTML, Text, Dauer und Zeichen
   - Als Tab öffnen, Browser, Speichern; Meldungen von xsl:message unter dem Ergebnis; Fehler mit Zeile und Sprung
3. Parameter und Live-Vorschau (1:00)
   - Parameter werden aus dem Stylesheet gelesen (Typ, Pflicht); CHF eintragen, neu ausführen
   - Live-Vorschau beim Tippen; Stylesheet-Datei beobachten; Automatisch ausführen
4. Andere Prozessoren (1:00)
   - Mitgeliefertes Saxon HE; externer Prozessor xsltproc / Saxon HE 13 hinzufügen und wählen
   - Grenzen: Live-Vorschau, Debugger, Profiler immer mit Saxon HE; Ausblick Debuggen (→ 9)

---

## 7 · schematron-quick-fixes — Probleme mit Schematron-Schnellkorrekturen beheben

Ziel-Laufzeit: 7:00 · Stand: alte Kurzfassung 1:00
Beispiel: release/examples/schematron/quickfix-demo.xml + quickfix-demo.sch

1. Wozu Schematron (1:00)
   - Regeln, die ein Schema nicht ausdrücken kann: Abhängigkeiten zwischen Werten, Geschäftsregeln, Hinweise statt Fehler
   - Die Bücherliste: was an ihr falsch ist (writer statt author, ISBN mit Leerzeichen, veraltete Elemente)
2. Die Regeldatei lesen (1:15)
   - pattern und rule mit context: wo eine Regel gilt; assert (muss gelten) und report (darf nicht vorkommen), Meldungstexte
   - sqf:fix am assert und das zugehörige sqf:fix-Element: Titel, Beschreibung, Aktion
3. Schematron wählen und prüfen (1:00)
   - Panel Validierung, Schematron wählen (Datei oder Ablegen), gemerkt pro Dokument
   - F8; Probleme mit Quelle Schematron, Tooltip mit Testausdruck und XPath; Klick springt zur Stelle
4. Die Glühbirne (1:15)
   - Glühbirne am Rand, Liste der Korrekturen mit Beschreibung; anwenden ändert nur diese Stelle, danach neu geprüft
   - Alt Enter am Cursor; mehrere Korrekturen zur Wahl; Strg Z nimmt eine Korrektur in einem Schritt zurück
5. Aus der Problemliste (1:00)
   - Rechtsklick → Schnellkorrektur (ISBN mit Bindestrichen, veraltetes Element entfernen); Korrektur mit Eingabe (Verlag)
   - Validierungsbericht mit Spalte Beheben; Hinweis, wenn erst neu validiert werden muss
6. Eine eigene Korrektur schreiben (1:30)
   - Neue Regel „jedes Buch braucht eine Sprache“ + sqf:fix mit sqf:add; speichern, prüfen, anwenden
   - Die vier Aktionen add / delete / replace / stringReplace an der Demo-Datei
   - Während der Eingabe validieren; Regeln prüfen; Ausblick Schematron debuggen (→ 9)

Offen: Schematron-Auswahl muss im Bild passieren (heute vorab).

---

## 8 · projects — Projekte: Schemas und Szenarien für einen ganzen Ordner

Ziel-Laufzeit: 8:00 · Stand: alte Kurzfassung 1:07
Beispiel: Projekt „Shop orders“ (shop.fxtproj, orders/, schemas/, stylesheets/); neu: Szenario „Order totals“ mit XML-Ausgabe + expected/order-1043.xml (absichtlich abweichend)

1. Wozu Projekte (0:45)
   - Viele Dateien, immer dieselben Schemas und Transformationen; eine Datei .fxtproj hält das fest, fürs ganze Team
2. Anlegen und öffnen (1:15)
   - Datei → Projekt → Neues Projekt; Name, Ordner hinzufügen; Explorer zeigt die Projektordner als Wurzeln
   - Projekt öffnen per Doppelklick auf die .fxtproj, Startseite „Projekte“, zuletzt geöffnete Projekte
3. Zuordnungen (1:30)
   - Tabelle Dateien → Schema / Schematron / Szenario; Muster orders/*.xml und **/*.xml; erste passende Zuordnung gewinnt
   - Explorer-Kontextmenü „Im Projekt zuordnen“ für markierte Dateien
   - Tooltip im Explorer zeigt, was für eine Datei gilt; Zuordnung gewinnt vor schemaLocation
4. Validieren (1:00)
   - Datei ohne schemaLocation: Schema kommt vom Projekt (Statusleiste „(Projekt)“); F8 prüft gegen Schema und Schematron
   - Projekt validieren: alle Dateien auf einmal, eine mit Preis unter null, Sprung zur Stelle
5. Szenarien (1:30)
   - Szenario-Dialog: Stylesheet, Eingabe ${file}, Ausgabe out/${name}.html, Methode, Parameter
   - Im Umwandeln-Panel ausführen; Ergebnis im Ordner out; als Szenario speichern
   - Szenario für mehrere markierte Dateien auf einmal ausführen
6. Regressionsprüfung (1:15)
   - Erwartetes Ergebnis festlegen; Prüfen → Kreuz bei Abweichung; Unterschiede-Ansicht
   - Übernehmen, wenn die Änderung gewollt ist; Szenarien prüfen fürs ganze Projekt
7. Teilen (0:45)
   - Projektdatei als JSON, Kataloge; Projektdatei ins Repository; dieselben Prüfungen auf der Kommandozeile (→ 21)

Offen: Dateidialog bei „Neues Projekt“ im Szenario bedienbar machen. Projekt muss für die Startseite in „Zuletzt“ stehen.

---

# Serie Vertiefung

---

## 9 · xslt-debug — Ein XSLT-Stylesheet debuggen

Ziel-Laufzeit: 8:00 · Stand: alte Kurzfassung 1:06 (enthält noch das Ausführen)
Beispiel: order.xml; neu: order-debug.xslt (Parameter currency, Variable total, Funktion f:line-total, xsl:message, ein nie laufendes Template)
Voraussetzung: Video 6

1. Wozu ein Debugger (0:30)
   - Falsches Ergebnis, aber keine Fehlermeldung: zusehen statt raten
2. Haltepunkt und Start (1:15)
   - Klick in den Rand, Debuggen; markierte Zeile, Debug-Bereich im Überblick (Variablen, Überwachung, Aufrufliste, Meldungen, Haltepunkte, Ausgabe)
   - Eingabe anzeigen: welcher Knoten der Eingabe gerade verarbeitet wird
3. Schritt für Schritt (1:15)
   - Fortsetzen bis zum nächsten Halt, Überspringen Zeile für Zeile
   - Hineinspringen in die Funktion, Herausspringen; Anhalten, Neu starten, Stoppen; Tastenkürzel
4. Den Zustand ansehen (1:30)
   - Variablen inkl. Kontextknoten, position, last, Parameter; geänderte Werte hervorgehoben
   - Inline-Werte im Editor, Wert beim Überfahren; Überwachungsausdrücke
   - Aufrufliste mit Rahmenwechsel; Auswerten mit Alt F8 im Wertefenster
5. Gezielt anhalten (2:00)
   - Bedingter Haltepunkt (@quantity > 2), Trefferzahl (genau beim N-ten Mal)
   - Logpoint mit {xpath} statt xsl:message einzufügen; Bis zum Cursor (Strg F10)
   - Haltepunkte-Liste, deaktivieren, Stummschalten; Anhalten bei Fehlern und bei xsl:message
6. Ausgabe und Abdeckung (1:00)
   - Ausgabe bisher, Doppelklick springt zur erzeugenden Zeile; Abdeckung zeigt nie gelaufene Zeilen und Templates
7. Auch für XQuery und Schematron (0:30)
   - Kurz zeigen; Ausblick Profiler (→ 19)

Offen: Rand-Kontextmenü der Haltepunkte und Trefferzahl-Optionen noch nicht übersetzt.

---

## 10 · query-console — XPath und XQuery in der Abfragekonsole

Ziel-Laufzeit: 6:00
Beispiel: order.xml; neu: eine .xq-Datei

1. Die Konsole öffnen (0:45)
   - Kopfleiste oder Strg K; Aufbau: Ausdruck oben, Ergebnis unten; Eingabe-Auswahl: Dokument, Datei, Ordner
2. XPath (1:30)
   - Erster Ausdruck (alle Artikel), Autovervollständigung für Elemente und Funktionen
   - Treffer im Dokument markieren und anspringen; Zählen und Summieren (count, sum)
   - Fehler im Ausdruck: Meldung mit Stelle, korrigieren
3. XQuery (1:15)
   - Mehrzeilig, FLWOR mit for, where, order by, return; Ergebnis als XML oder Tabelle
   - Variablen, trace für Zwischenwerte, Ausführungsplan
4. Abfragedokumente (0:45)
   - .xq-Datei ausführen, Ergebnis im Ausgabebereich; Debuggen und Profilieren (→ 9, 19)
5. Merken und Versionen (1:15)
   - Snippets speichern / laden, mitgelieferte Beispiele, Verlauf mit Pfeiltasten
   - Sprachversionen im Expertenmodus (XPath 1.0 / 2.0 / 3.1), Kompatibilitätsprüfung
6. Tipps (0:30)
   - Namespaces im Ausdruck, Ergebnis kopieren oder exportieren

---

## 11 · schema-library — Schemas finden, verwalten und erzeugen

Ziel-Laufzeit: 6:00
Beispiel: order.xml (ohne Schema-Angabe) + order.xsd; der mitgelieferte Katalog

1. Woher kommt das Schema? (0:45)
   - Reihenfolge: lokale Datei, Bibliothek, Download; heruntergeladene Schemas als lokale Kopie neben dem Schema
2. Die Schema-Bibliothek (1:15)
   - Seite und Abschnitte; Zuordnung Namespace → Schema und Dateiname → Schema anlegen
   - Mitgelieferte Standards, SchemaStore für JSON, Cache leeren, offline arbeiten
3. XML-Kataloge (1:00)
   - Was ein Katalog tut (Adresse → lokale Datei); Katalog einbinden, mitgeliefertes Beispiel ausprobieren
4. Schema aus XML erzeugen (1:15)
   - Aus dem offenen Dokument oder mehreren Dateien; Ergebnis im Typ-Editor ansehen
   - Was danach zu prüfen ist: zu enge Typen, optionale Elemente, Aufzählungen
5. Beispiel-XML aus einem Schema (1:15)
   - Schnellstart: Pflichtelemente oder alles; Profile für realistische Daten
   - Gleich validieren; DTD und RELAX NG werden ebenfalls geprüft
6. Abschluss (0:30)
   - Zusammenfassung, Verweis auf Projekte (→ 8)

---

## 12 · compare-merge — Vergleichen und Zusammenführen

Ziel-Laufzeit: 6:00
Beispiel: neu: zwei Fassungen von order.xml, ein Ordnerpaar, drei Fassungen für den Merge

1. Warum XML anders vergleichen (0:45)
   - Textvergleich zeigt Rauschen: Einrückung, Attributreihenfolge, Zeilenumbrüche
2. Zwei Dateien vergleichen (1:00)
   - Dialog Dateien vergleichen; Textmodus mit Zeilenmarken
   - Sprung zum nächsten Unterschied, Übersicht am Rand
3. Als XML vergleichen (1:00)
   - Strukturelle Unterschiede mit XPath; Verschiebungen und CDATA als „ähnlich“
   - Geänderter Preis als echter Unterschied
4. Unwichtiges ausblenden (1:00)
   - XPaths ignorieren (Zeitstempel), Zahlen mit Toleranz
   - Platzhalter im erwarteten Dokument für Werte, die sich jedes Mal ändern
5. Ordner vergleichen (0:45)
   - Neu / gelöscht / geändert, Datei im Vergleich öffnen
6. Drei Fassungen zusammenführen (1:00)
   - Basis, meine, deine; automatisch übernommene Änderungen
   - Konflikte lösen; compare / merge auf der Kommandozeile
7. Abschluss (0:30)

---

## 13 · convert — XML, JSON, YAML und Excel umwandeln

Ziel-Laufzeit: 6:00
Beispiel: order.xml; neu: eine kleine Excel-Datei mit Artikeln

1. XML ↔ JSON ↔ YAML (1:15)
   - Bestellung nach JSON, Ergebnis als neuer Tab; strukturierte vs. W3C-Abbildung
   - Zurück nach XML; YAML für Konfigurationen
2. JSON und YAML bearbeiten (1:00)
   - Text, Baum, Grid wie bei XML; gegen JSON Schema prüfen
3. XML nach Excel / CSV (1:15)
   - Zeile pro Knoten (XPath-Liste) oder Tabelle pro Datensatz; Vorschau im Dialog
   - Datensatz wählen, Spalten als relative Pfade
4. Excel-Aussehen und Typen (0:45)
   - Kopfzeile, Filter, Zahlen und Datum als echte Typen, Blatt mit Infos
5. Von Excel zurück nach XML (1:00)
   - Layout wird erkannt; fremde Tabelle wird zu Datensätzen
   - Geänderte Tabelle einlesen und gegen das Schema prüfen
6. Kommandozeile und Abschluss (0:45)
   - convert auf der Kommandozeile für viele Dateien

---

## 14 · xsd-types — XSD: Übersicht und Typen

Ziel-Laufzeit: 7:00
Beispiel: order.xsd
Voraussetzung: Video 5

1. Die Ansicht Übersicht (1:30)
   - Alle globalen Komponenten als Tabelle mit Art, Name, Details, Annotation
   - Einfügen und Anhängen je Art; sortieren und verschieben (Strg Pfeil, Ziehen)
   - Untere Reiter: Details, Attribute, Bedingungen, Assertions, Namespaces
2. Umbenennen, Imports, Namespaces (1:15)
   - Umbenennen mit allen Verweisen; Imports und Includes hinzufügen und ändern
   - Namespaces und Präfixe verwalten
3. Einfache Typen (1:30)
   - Einschränkung mit Facetten: Muster, Aufzählung, Länge, Ziffern, Bereich
   - Liste und Union; Facetten im Eigenschaftenbereich
   - Typischer Fehler: Facette, die der Basistyp nicht erlaubt
4. Komplexe Typen (1:30)
   - Typ-Editor: Inhalt bearbeiten, Erweiterung und Einschränkung eines Basistyps
   - Abstrakte Typen; Typbibliothek mit Verwendungen
   - Typ ändern und sehen, wo er überall gilt
5. XSD 1.1 (0:45)
   - Assertions mit XPath; Alternativen
6. Abschluss (0:30)

---

## 15 · xsd-analysis — XSD: Analyse und Dokumentation

Ziel-Laufzeit: 5:00
Beispiel: ein größeres Schema (z. B. FundsXML)

1. Schema-Analyse (1:30)
   - Statistik: Anzahl Elemente, Typen, Tiefe
   - Qualitätsprüfungen: ungenutzte Typen, fehlende Dokumentation, Zyklen
   - Identitätsbedingungen und XPath-Prüfung der Selektoren
2. Bericht (0:45)
   - Als HTML / PDF / Excel speichern
3. Dokumentation erzeugen (1:30)
   - HTML / PDF / Word; Optionen (Diagramme, Beispiele, Bedingungen)
   - Sprache der Dokumentation; technische Notizen im Schema
4. Aufräumen (0:45)
   - Includes einbetten (glätten), ungenutzte Typen entfernen; xsd-doc auf der Kommandozeile
5. Abschluss (0:30)

---

## 26 · xsd-documentation — Ein Schema als Website dokumentieren

Ziel-Laufzeit: 8:00
Beispiel: neu: shop-order.xsd (Beschreibungen en/de, @since/@see/@deprecated, Beispielwerte, key, assert)
Reihenfolge: in der Serie direkt nach Nr. 15. Die Website wird in einem echten Browser gezeigt (Chrome for Testing).

1. Vorbereitung (1:00)
   - Warum: Leser ohne XSD-Kenntnisse, statische Website
   - Im Schema: xs:documentation mit xml:lang, xs:appinfo-Tags, Beispielwerte
2. Generator (2:00)
   - Werkzeuge → Dokumentation generieren; Quelle, Ausgabeordner, HTML
   - Optionen: Markdown, Bedingungen, SVG-Übersicht, Datenwörterbuch, Favicon, Bildformat
   - Sprachen ermitteln, Ersatzsprache; Generieren mit Fortschritt
3. Die Website (3:30)
   - Startseite; Elementseite mit Diagramm (Kästen sind Links), Beschreibung, Eigenschaften, Kindelemente, Quelltext
   - Seit / Siehe auch / Veraltet; Sprachwahl; Schlüssel und Assertions
   - Typenseiten; Datenwörterbuch mit Excel; SVG-Übersicht; Suche
4. Weitergeben (1:15)
   - Ordner auf Webserver / Intranet / Freigabe; PDF und Word mit ihren Optionen
   - fxt-cli xsd-doc in einer Build-Pipeline
5. Abschluss (0:15)

---

## 16 · publish-pdf — PDF mit XSL-FO erzeugen

Ziel-Laufzeit: 6:00
Beispiel: neu: order.xml + ein kleines XSL-FO-Stylesheet (Rechnung)

1. Wie es funktioniert (0:45)
   - XML + XSL-FO-Stylesheet → FO-Zwischenformat → PDF mit Apache FOP
2. Das PDF/FOP-Panel (1:15)
   - XML und Stylesheet wählen; Metadaten Titel, Autor, Thema
   - Optionen, z. B. PDF/A
3. Erzeugen und ansehen (1:15)
   - PDF erzeugen, Vorschau im Tab
   - Meldungen von FOP lesen (z. B. Inhalt zu breit)
4. Fehler finden (1:00)
   - Stylesheet debuggen (→ 9); FO-Zwischenergebnis ansehen
5. Externe FO-Prozessoren (1:15)
   - Antenna House / XEP / eigener Befehl einrichten und testen
   - Szenario mit PDF-Ausgabe (→ 8); fop auf der Kommandozeile
6. Abschluss (0:30)

---

## 17 · large-files — Sehr große XML-Dateien

Ziel-Laufzeit: 4:00
Beispiel: neu: erzeugte XML-Datei mit einigen hundert MB (Skript)

1. Wann der Viewer kommt (0:45)
   - Angebot ab 50 MB; Datei sofort lesbar, auch Gigabytes; Text blättern und suchen
2. Struktur (1:00)
   - Baum seitenweise, Gehe zu XPath oder Kind Nr.
   - Datensätze nach CSV / Excel exportieren
3. Einen Teil bearbeiten (1:00)
   - Fenster im Grid öffnen, ändern, Übernehmen / Verwerfen; Datei wird nur an dieser Stelle geändert
4. Statistik und Validierung (1:00)
   - Werte und Anzahl je Pfad; Prüfung gegen XSD im Strom
5. Abschluss (0:15)

---

## 18 · xspec — Stylesheets testen mit XSpec

Ziel-Laufzeit: 5:00
Beispiel: order.xml + order-summary.xslt; neu: order-summary.xspec

1. Warum testen (0:45)
   - Stylesheets ändern sich, Ergebnisse sollen bleiben; Tests als Sicherheitsnetz
2. Ein erster Test (1:15)
   - Neue XSpec-Datei aus Vorlage; Szenario mit Eingabe und Erwartung
   - Mehrere Szenarien in einer Datei
3. Ausführen und lesen (1:15)
   - Bestanden / fehlgeschlagen, Unterschied zwischen erwartet und tatsächlich
   - Test reparieren oder Stylesheet korrigieren
4. XQuery und Schematron testen (1:15)
   - Vorlagen für XQuery und Schematron; xspec auf der Kommandozeile
5. Abschluss (0:30)

---

## 19 · profiler — Langsame Stylesheets und Abfragen finden

Ziel-Laufzeit: 4:00
Beispiel: order-summary.xslt; neu: größere Eingabe mit einigen tausend Artikeln

1. Profiling-Lauf (0:45)
   - Im Umwandeln-Panel „Profiling-Lauf“; XQuery über Ausführen ▾ → Abfrage profilieren
2. Das Profil lesen (1:00)
   - Templates und Funktionen mit Gesamt- und Eigenzeit; Aufrufbaum; teure Zeilen
3. Die Flammengrafik (1:00)
   - Breite Balken = viel Zeit; hineinzoomen
4. Beheben und neu messen (1:00)
   - Teure Stelle ändern (z. B. Schlüssel statt Suche), neu messen; transform --profile auf der Kommandozeile
5. Abschluss (0:15)

---

## 20 · xproc — XProc-Pipelines ausführen

Ziel-Laufzeit: 4:00
Beispiel: mitgelieferte XProc-Beispiele

1. Was eine Pipeline ist (0:45)
   - Schritte hintereinander: prüfen, umwandeln, speichern, in einer Datei
2. Eine erste Pipeline (1:00)
   - Mitgeliefertes Beispiel öffnen, Schritte lesen, Pipeline ausführen
3. Ergebnis lesen (1:00)
   - Ausgaben je Port, Meldungen, Fehler mit Schritt
4. Grenzen (1:00)
   - Nicht verfügbare Schritte; Kommandozeile
5. Abschluss (0:15)

---

## 21 · command-line — Automatisieren mit der Kommandozeile fxt-cli

Ziel-Laufzeit: 6:00
Beispiel: Ordner mit Bestellungen, Projekt „Shop orders“ (→ 8)

1. Warum Kommandozeile (0:45)
   - Dieselben Prüfungen ohne Oberfläche, für Skripte und CI; wo fxt-cli liegt, --help
2. validate (1:15)
   - Datei, Ordner, Projekt, Schematron
   - Exit-Codes und Ausgabe; Bericht als JUnit / JSON
3. transform und query (1:15)
   - XSLT mit Parametern und Ausgabedatei
   - XPath wie grep über viele Dateien
4. scenario --check (1:00)
   - Regressionsprüfung, JUnit-Bericht
5. In der CI (1:15)
   - Rezept für GitHub Actions / GitLab; fehlgeschlagene Prüfung stoppt den Build
6. Abschluss (0:30)

Offen: Terminal-Aufnahme oder Textkarten?

---

## 22 · sign-encrypt — XML signieren und verschlüsseln

Ziel-Laufzeit: 7:00
Beispiel: order.xml; Beispiele aus release/examples/encryption

1. Wozu (0:45)
   - Echtheit (Signatur) und Vertraulichkeit (Verschlüsselung); Aufbau der Seite, Schlüsselspeicher
2. Zertifikat erstellen (1:15)
   - RSA / EC, Angaben zum Inhaber, Passwort; was erzeugt wird
3. Signieren und prüfen (1:30)
   - Signaturarten (eingebettet, umhüllend, losgelöst), Algorithmen
   - Prüfen: gültig; Datei ändern → Prüfung schlägt fehl
4. Verschlüsseln (1:15)
   - Element / Inhalt / Dokument; Empfänger mit Zertifikat, Schlüssel oder Passwort
5. Entschlüsseln und untersuchen (1:15)
   - Entschlüsseln; Inspect zeigt Aufbau und Algorithmen
   - Element verschlüsseln direkt aus dem Editor-Menü
6. Kommandozeile und Abschluss (1:00)
   - sign, verify, encrypt, decrypt; Passwörter über Umgebungsvariablen

---

## 23 · ai-assistant — KI-Assistent und MCP-Server

Ziel-Laufzeit: 7:00
Beispiel: order.xml mit einem Fehler; ein eingerichteter Anbieter

1. Was der Assistent kann (0:45)
   - Erklären, beheben, Abfragen und Stylesheets erzeugen; Vorschläge werden geprüft, angewendet wird nur von Ihnen
2. Einrichten und Datenschutz (1:30)
   - Einstellungen → KI: Anbieter (lokal oder Cloud), Schlüssel
   - Datenstufen Schema / Struktur / Auszüge; Vorschau vor dem Senden
3. Einen Fehler erklären und beheben (1:15)
   - Aus dem Problembereich; Vorschlag mit Prüfbericht; übernehmen, Rückgängig
4. Frage → XPath / XQuery (1:00)
   - In der Abfragekonsole: Frage in Worten, geprüfter Ausdruck
5. Stylesheet aus einem Beispiel (1:15)
   - Zielbeispiel vorgeben, Stylesheet erzeugen und prüfen
   - Nachfragen zum Verfeinern
6. MCP-Server (1:15)
   - fxt-cli mcp für externe Agenten; Werkzeuge
   - Sicherheit: nur freigegebene Ordner, schreibt nichts

Offen: Welcher Anbieter / welches Modell für die Aufnahme (reproduzierbar, ohne Kosten)?

---

## 24 · settings — Einstellungen und Darstellung

Ziel-Laufzeit: 5:00

1. Die Seite Einstellungen (0:45)
   - Gruppen in der Seitenleiste, Suche nach einer Einstellung
2. Darstellung (1:15)
   - Hell / Dunkel / System, Akzentfarbe
   - Textgröße, Dichte, hoher Kontrast, Sprache
3. Editor (1:00)
   - Schrift, Zeilenabstand, Syntaxfarben; Zoom mit Strg Mausrad für alle Editoren
4. Netzwerk und Updates (1:00)
   - Proxy, Anmeldung, Verbindung testen; Update-Prüfung
5. Datenschutz (0:45)
   - Was gesendet wird, ansehen und abschalten
6. Abschluss (0:15)

Offen: Darstellung danach wieder auf Hell / Blau setzen.

---

## 25 · administration — FreeXmlToolkit im Unternehmen verwalten

Ziel-Laufzeit: 5:00
Beispiel: neu: policy.properties / defaults.properties
Zielgruppe: Administratoren

1. Richtlinien (1:00)
   - policy.properties (gesperrt) und defaults.properties (Vorgabe); wo die Dateien je Betriebssystem liegen
   - Beispiel: Update-Prüfung abschalten
2. Gesperrte Einstellungen (0:45)
   - In der Anwendung erkennen: Schloss, ausgegraut
3. Funktionen abschalten (1:00)
   - Updates, KI, externe Prozessoren, Plugins, Online-Abfragen
4. Installation (1:00)
   - Stille Installation, Dateizuordnungen, Offline-Betrieb
5. Sicherheit (1:00)
   - XXE- und Erweiterungsschutz; Telemetrie für alle abschalten
6. Abschluss (0:15)
