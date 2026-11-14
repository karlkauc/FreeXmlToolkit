# PK-Meldewesen (FMA)

The **PK-Meldewesen** extension supports the reports Austrian pension funds (*Pensionskassen*)
send to the Austrian Financial Market Authority (FMA). One taxonomy describes all three of them:

| Report | Regulation | Module | Root element |
|---|---|---|---|
| Quarterly report (*Quartalsmeldung*) | PK-QMV 2025 | `pfq` | `Nationale_Meldung_pfq` |
| Annual report (*Jahresmeldung*) | PK-FJMV 2025 | `pfa` | `Nationale_Meldung_pfa` |
| ECB report (*EZB-Meldung*) | Regulation (EU) 2018/231 | `pfe` | `EZB_Meldung_pfe` |

The reports have no XML namespace; they are recognised by their root element. The FMA's
taxonomy **2.9.0 Hotfix2** (as of 21.07.2026: the three XSDs, the documentation and the
Business-Templates, Dictionary, Positionsbeschreibung and Validierungen workbooks) is shipped with
the application.

## Switching it on

The extension is **off by default**. Tick **PK-Meldewesen (FMA)** in
**Settings → Extensions → INSTALLED EXTENSIONS** - it applies at once. When you open a report while
it is off, a bar above the editor offers it once (**Enable PK-Meldewesen** / **Not now**).
Administrators can remove it completely with `plugin.pk-meldewesen.enabled=false`, see
[Administration](administration.md).

## What it does

- **Schema binding:** a report gets the schema of its module from the taxonomy in use, by its root
  element. This also works when the report's `xsi:noNamespaceSchemaLocation` points to a path on
  the sender's network (e.g. `\\server\share\pk_meldung_pfq_2.9.0.xsd`) that cannot be reached -
  validation (**F8**), IntelliSense, the grid and the Inspector then use the FMA schema.
- **Status bar badge:** the active report is labelled with its kind and period, e.g.
  **PK-QMV Q1/2026**, **PK-FJMV 2025** or **PK-EZB Q4/2025**; a click opens the side panel.
- **PK-Meldewesen activity** (side panel):
    - **ACTIVE REPORT** - module, pension fund number, report type (e.g. *Testmeldung*), LEI and
      the taxonomy the report declares;
    - **CHECK** - **Validate Active Report** validates it against its schema (as **F8** does);
    - **TAXONOMY** - the taxonomy in use (a note appears when the report declares another one),
      **Open Schema in Editor** (the schema of the active report's module), **FMA Documentation
      (PDF)** and **Open Taxonomy Folder** with the workbooks.
- **Help menu:** links to the FMA's pension fund reporting page and to the FMA Incoming Platform,
  where the reports are submitted.

The taxonomy is installed under `~/.freeXmlToolkit/pk-meldewesen/<version>/`.

## Large reports

Real quarterly reports can be 150 MB and more. The extension reads only the start of a document
to recognise it. For files above 50 MB the editor offers the [Large File Viewer](large-files.md),
which validates against the bound schema while streaming the file.

## Planned

The rule checks of the FMA's *Validierungen* workbook with a report, grid formulas and filters,
labels for template, row and column codes, preparing the file for submission (name convention
`pk.modul.stichtag.lfd-nr[.zusatz].xml`, ZIP) and importing newer taxonomies follow in the next
versions.
