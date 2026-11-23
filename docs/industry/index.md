---
description: Plugins for FundsXML, ISO 20022 and SEPA, ebInterface, FinDatEx, XBRL, and FMA and ESMA regulatory reporting.
---

# Industry Extensions

FreeXmlToolkit ships optional [extensions](manage-extensions.md) for industry standards. Each one has its own
page that explains its functions in detail:

| Extension | For | Page |
|---|---|---|
| **ISO 20022 Payments (SEPA)** | `pain`, `pacs` and `camt` payment messages; control sums and the EPC SEPA rules | [ISO 20022 Payments](iso20022.md) |
| **ebInterface** | Austrian XML e-invoices 4.1–6.1; line, tax and total calculations | [ebInterface](ebinterface.md) |
| **FinDatEx Templates** | EMT, EET, EPT and TPT (V7/V8) files as CSV or Excel | [FinDatEx Templates](findatex.md) |
| **XBRL (with Arelle)** | XBRL instances and inline XBRL reports, validated with Arelle | [XBRL](xbrl.md) |
| **PK-Meldewesen (FMA)** | Austrian pension fund reports to the FMA: quarterly (PK-QMV), annual (PK-FJMV) and ECB report | [PK-Meldewesen](pk-meldewesen.md) |
| **ESMA Reporting (EMIR, SFTR, MiFIR)** | EMIR Refit trade reports (auth.030), SFTR reports (auth.052) and MiFIR transaction reports (auth.016) with ESMA's validation rules | [ESMA Reporting](esma-reporting.md) |
| **KAG-Meldewesen (FMA)** | Reports of Austrian management companies to the FMA: AIFMD reporting (AIFM and AIF file) with the FMA's rules, money market fund report, derivative risk report (4. DeRiMV) | [KAG-Meldewesen](kag-meldewesen.md) |

The [FundsXML extension](fundsxml.md) is an extension as well, with its own page.

## Switching them on

The industry extensions are **off by default**: switch them on in **Settings → Extensions → INSTALLED EXTENSIONS**
and restart. When you open a document that a switched-off extension recognises (a SEPA payment, an e-invoice, a
FinDatEx template, an XBRL instance, an ESMA or a KAG report), a bar above the editor offers it once:
**Switch On** stores the setting, the extension is there after the next start. Administrators can keep an extension off for everyone with `plugin.<id>.enabled=false`
(`iso20022`, `ebinterface`, `findatex`, `xbrl`, `kag-meldewesen`, `esma-reporting`), see [Administration](../administer/policies.md).

## What every industry extension adds

- an **activity** in the Activity Bar with a side panel:
    - **ABOUT** — what the extension checks;
    - **ACTIVE DOCUMENT** — what the open document is (message type, invoice number, template,
      number of facts …), updated when you switch tabs;
    - **Check Active Document** — validates the document like **F8** (schema and the extension's rules); the
      problems go to the **PROBLEMS** panel below the editor, where a click jumps to the line, and are underlined
      in the text. The side panel shows how many errors and warnings the last check found; **Show in Problems**
      opens the list. FinDatEx and XBRL run their own check (XBRL needs the saved file) and put their problems
      into the PROBLEMS panel the same way;
    - **Check Report (HTML)** (ESMA Reporting, KAG-Meldewesen) — the findings grouped by rule with the field
      they belong to, as a page in the preview;
    - **TOOLS** — the extension's own actions (sample documents, new template files, Excel import, Arelle command);
    - **OPTIONS** (KAG-Meldewesen) — the extension's settings;
    - **CHECK AN IDENTIFIER** — checks a pasted IBAN (ISO 13616: country length and check digits),
      BIC (ISO 9362), LEI (ISO 17442), ISIN (ISO 6166) or Austrian VAT id (UID), depending on the extension; an LEI
      and an ISIN can be looked up online, and **Check Identifiers Online** checks every LEI (GLEIF) and ISIN (ESMA FIRDS,
      OpenFIGI) of the document ([Online Identifier Lookup](identifier-lookup.md));
- a **progress block** under the actions while a check, report or tool runs: a bar (with a count where the work can
  be counted - records of a KAG/ESMA report, requests of the online identifier check), what runs right now, the time
  so far and **Cancel** where the work can stop (between records, requests or steps; Arelle is ended at once). A
  second click on a running action is refused; with several actions at once the newest is shown with "(+1 more)";
- **checks** that run with every validation (Validation panel, **F8**, `fxt-cli validate`) and appear
  with the extension's name as source — ISO 20022 and ebInterface; FinDatEx and XBRL files are checked
  from their side panel;
- where the standard has official XML Schemas, **Schema Library entries** (source *Extension*), so
  documents get their schema bound automatically. Remote schemas are downloaded once and cached;
- for the reports with field catalogues (ESMA Reporting, KAG-Meldewesen): the **field number, name and rule**
  of an element in the Inspector, as tooltip in the grid and - when the mouse rests on a tag name - in the
  text editor (documents up to 2 MB).

| | Schemas | Checks with every validation | Sample / new documents | Identifier checker |
|---|---|---|---|---|
| ISO 20022 | 19 message versions | yes | SEPA credit transfer, SEPA direct debit | IBAN, BIC, LEI |
| ebInterface | versions 4.1–6.1 | yes | ebInterface 6.1 invoice | UID, IBAN, BIC |
| FinDatEx | — (field definitions bundled) | no, side panel | new EMT / EET / EPT / TPT files, Excel import | ISIN, LEI |
| XBRL | taxonomies via Arelle | no, side panel | — | — |
