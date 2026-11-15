# Nächste Meldewesen-Erweiterungen: FMA KAG-Meldungen (4. DeRiMV, AIFM, Geldmarktfonds) und ESMA-Transaktionsmeldungen (EMIR, SFTR, MiFIR)

## Context
Die PK-Meldewesen-Erweiterung ist fertig. Für das österreichische KAG-Umfeld hast du drei weitere Bereiche gewählt:

- **FMA, über die Incoming-Plattform (XML nach XSD):**
  - Derivate-Risikomeldung nach 4. DeRiMV
  - AIFM-Meldung (Annex IV)
  - Geldmarktfonds-Meldung (MMF-VO Art. 37)
- **ESMA-Meldungen im ISO-20022-Format:**
  - EMIR Refit: auth.030
  - SFTR: auth.052
  - MiFIR: auth.016

Ziel: dieselbe Tiefe wie beim PK-Meldewesen. Dazu gehören Erkennung, gebündelte Schemas, fachliche Prüfregeln mit Zeilen, Beschriftungen, Abgabe-Vorbereitung und Doku.

Vorher wird das, was PK und die neuen Erweiterungen gemeinsam brauchen, in einen gemeinsamen Baustein gezogen. So entsteht kein dritter Taxonomie-Store.

## Phase 0 – Unterlagen beschaffen (vor dem Code)
- **ESMA (öffentlich, wird gebündelt mit Quellenangabe):**
  - AIFMD-Reporting-XSDs (DATAIF/DATAMAN) und die IT-Guidance mit den Validierungsregeln
  - MMF-Reporting-XSD und die Validierungsregeln
  - EMIR-Refit-XSDs (auth.030/031/…) und die Validierungsregeln (Excel)
  - SFTR-XSDs (auth.052 u. a.) und die Validierungsregeln
  - MiFIR-XSD (auth.016) und die Validierungsregeln
  - Download per `ConnectionService` als Skript. Wenn das blockiert ist: Ablage durch dich in `regdata/` (lokal, gitignored).
- **FMA:**
  - XSD der 4. DeRiMV und die FMA-Fassung des AIFM-Schemas, falls es von ESMA abweicht.
  - Die FMA blockiert automatische Downloads (wie bei PK). Die ZIPs/XSDs legst du deshalb in `regdata/fma/` ab.
- **Echte Meldungen:** nur lokal zur Gegenprobe, nie ins Repo. Fixtures werden synthetisch erzeugt (Generator-Skript wie beim PK-Beispiel; LEI/ISIN mit gültigen Prüfziffern).

## Phase 1 – Gemeinsamer Baustein `plugins/regreport/` (Refactoring aus PK)
Ziel: Gemeinsames aus PK herausziehen; PK verhält sich danach genau wie vorher (gleiche Tests grün).

- **`RegTaxonomyStore`** (aus `PkTaxonomy`)
  - Versionen unter `~/.freeXmlToolkit/<extension>/<version>/`
  - gebündelte Version, `importZip` (Zip-Slip-Schutz, Größenlimit, Staging)
  - stabile `_active/`-Kopien, `setActiveVersion`, Versionsordnung
  - Die Versionserkennung wird ein Parameter (Regex auf XSD-Namen bzw. Version aus `targetNamespace`).
  - `PkTaxonomy` wird ein dünner Adapter.
- **`RegSchemaIndex`**
  - liest aus jeder XSD des aktiven Stands `targetNamespace` und die globalen Wurzelelemente
  - daraus entstehen automatisch die `PluginSchema`-Registrierung und die Erkennung (Wurzelname + Namespace)
  - Neu gegenüber PK: Meldungen mit Namespace.
- **`RegRuleRunner`** (aus `PkRuleCheck`/`PkLocator`/`PkRuleValidator`)
  - Prüfregeln als XSLT mit Ergebnisbaum `results/…/c` und als einfache Java-Regeln (`Rule`-Interface)
  - Zeilenzuordnung über einen pro Erweiterung definierbaren Locator (XPath-artige Schlüssel → Zeile, ein StAX-Durchlauf)
- **`RegSubmission`** (aus `PkSubmission`): bereinigen (Grid-Anmerkungen, UTF-8), Dateinamensmuster als Parameter, laufende Nummer, ZIP.
- **`RegLabels`** (aus `PkDictionary`/`PkDescriber`)
  - Beschriftungen aus Workbooks oder aus `xs:documentation` der XSDs
  - JSON-Cache je Version; Anbindung über den vorhandenen `ElementDescriber`
- **Panel:** `PkMeldewesenPanel`-Abschnitte als wiederverwendbare FXML-Bausteine: Taxonomie-Auswahl + Import, Prüfoptionen, Abgabe.
- **Tests:** alle `Pk*Test` unverändert grün, plus Tests für `RegSchemaIndex` (Namespace-Erkennung).

## Phase 2 – Erweiterung „FMA KAG-Meldewesen“ (`plugins/kagmeldewesen/`, Id `kag-meldewesen`, standardmäßig aus)
| Meldung | Erkennung | Prüfungen (Auswahl) |
|---|---|---|
| **4. DeRiMV** | Wurzel/Namespace aus der FMA-XSD | XSD; eine Datei je KAG (§ 2a); Pflichtangaben KAG-Name/Bankleitzahl; je OGAW Methode Commitment / relativer / absoluter VaR; Grenzen (Commitment ≤ 100 % NAV, absoluter VaR ≤ 20 %, relativer VaR ≤ 2× Referenzportfolio); Konfidenz 99 %, Haltedauer 20 Tage; Plausibilität Hebel; Datumslogik (Stichtag/Meldeperiode) |
| **AIFM Annex IV** | ESMA-/FMA-Namespace, AIFM- vs. AIF-Meldung | XSD + ESMA-Validierungsregeln (FilingType INIT/AMND, Berichtszeitraum ↔ Meldefrequenz, LEI/ISIN-Prüfziffern, Summen der Aufschlüsselungen, Pflichtfelder je Meldeinhalt) |
| **Geldmarktfonds** | ESMA-MMF-Namespace | XSD + ESMA-Regeln (WAM/WAL-Grenzen je Fondstyp, Liquiditätsquoten täglich/wöchentlich, Stresstest-Felder, Summen) |

- **Regeln:**
  - ESMA-Regeln werden aus dem jeweiligen ESMA-Validierungs-Workbook in einen Regelkatalog (JSON) übernommen. Ein Generator-Skript liegt unter `scripts/`, wie bei FinDatEx.
  - Was als XPath ausdrückbar ist, läuft generisch; der Rest als Java-Regel.
  - Die DeRiMV-Grenzen werden nach Verordnungstext codiert.
- **Weitere Funktionen:**
  - Labels: aus Workbook bzw. XSD-Dokumentation (Inspector, Grid-Tooltip, Hover)
  - Berichts-HTML je Meldung
  - Abgabe-Dialog mit Dateinamen nach FMA-Vorgabe; die Konvention wird in Phase 0 geklärt
  - Badge („DeRiMV Q3/2026“, „AIFM Annex IV“, „MMF Q2/2026“)
  - Hinweisbalken; Seitenpanel nach PK-Muster; Workflow-Farbe; i18n EN+DE
- **Doku:** `docs/plugin-kag-meldewesen.md`, `industry-plugins.md`, `plugins.md`, `whats-new.md`, `mkdocs.yml`, Screenshot-Generator.
- **Je Meldung ein eigener Commit:** zuerst Gerüst + DeRiMV, dann AIFM, dann MMF.

## Phase 3 – Erweiterung „ESMA-Transaktionsmeldungen“ (`plugins/esmareporting/`, Id `esma-reporting`)
Eigene Erweiterung, nicht im ISO-20022-Zahlungsplugin, weil andere Nutzer, Regeln und ESMA-Schemas.

- **Erkennung:** ISO-20022-Namespace + Nachricht (`auth.030` EMIR, `auth.052` SFTR, `auth.016` MiFIR, auch die Antwort- und Statusnachrichten). Die vorhandenen Bausteine `Iso20022Validator.messageOf` und `XmlScan` werden wiederverwendet.
- **Prüfungen:**
  - XSD (ESMA-Fassung, gebündelt)
  - ESMA-Validierungsregeln aus den Workbooks: Pflicht/optional/nicht anwendbar je Aktionstyp (NEWT/MODI/CORR/TERM/EROR/REVI/POSC/VALU …), Formate (UTI, LEI, ISIN, UPI, CFI), Datumslogik, Wertebereiche
  - Je Meldung eine Zeile pro Befund mit Feldnummer der ESMA-Tabelle
- **Labels:** Feldnummer + Name aus den ESMA-Tabellen (z. B. „2.1 UTI“).
- **Hilfen:**
  - Zähler je Aktionstyp und Gegenpartei im Panel
  - Query-Console-Snippets (Meldungen je Aktionstyp, fehlende UTI, Dubletten)
  - Beispielmeldungen
- **Keine Übermittlung:** Die Meldungen gehen an Transaktionsregister bzw. die FMA über deren Kanäle; die Erweiterung prüft und bereitet vor.
- **Reihenfolge:** EMIR (am verbreitetsten bei KAGs), dann SFTR, dann MiFIR; je ein Commit.

## Wiederverwendung (vorhanden)
- **PK-Meldewesen**, Ausgangspunkt des Refactorings: `PkTaxonomy`, `PkRuleCheck`, `PkLocator`, `PkRuleValidator`, `PkDictionary`, `PkDescriber`, `PkCodeHover`, `PkSubmission`, `PkSubmissionDialog`, `PkExtras`, `PkMeldewesenPanel` (alle unter `plugins/pkmeldewesen/`).
- **Plugin-API:**
  - `PluginContext.addSchema` mit Root-Element-Bindung
  - `addValidator`, `addElementDescriber`, `addDocumentBadge`, `addActivity`
- **Weitere Bausteine:**
  - `plugin/XmlScan`
  - `service/finance/FinancialIdentifiers` (LEI, ISIN, BIC)
  - `Iso20022Validator`
  - `SaxonResourceAccess.restrictedProcessor`
  - `ValidationRunner`, `LargeFileValidation`
  - `DocumentationRunner` für die Schema-Doku
  - `scripts/findatex-fields.py` als Muster für den Workbook→JSON-Generator

## Verifikation
- **Tests je Phase:**
  - Runner-Läufe der neuen Testklassen (synthetische Fixtures mit eingebauten Verstößen → erwartete Regel-Ids + Zeilen)
  - Guard-Tests: `FxmlContractTest`, `I18nBundlesTest`, `IconifyIconCoverageTest`, `AccessibilityAuditTest`, `WorkflowTokensCssSyncTest`/`WorkflowTest`, `ActivityTest`, `DialogCreationGuardTest`, `PluginManagerTest`
- **Phase 1 (Refactoring):** alle bestehenden `Pk*Test` und `EditorHostPkReportTest` unverändert grün; Gegenprobe mit einer echten Meldung (lokal, gleiche Befundzahlen wie vorher).
- **Gegenprobe mit echten Meldungen:** wo du welche hast (DeRiMV, Annex IV, EMIR), lokal; nie ins Repo.
- **Abschluss:**
  - Commit je Teil, Push, CI-Runs prüfen (`gh run list`, fehlgeschlagene Shards auswerten)
  - volle Suite lokal
  - Screenshots über `docScreenshots`
