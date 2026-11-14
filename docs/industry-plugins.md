# Industry Extensions

FreeXmlToolkit ships optional [extensions](plugins.md) for industry standards. Each one has its own
page that explains its functions in detail:

| Extension | For | Page |
|---|---|---|
| **ISO 20022 Payments (SEPA)** | `pain`, `pacs` and `camt` payment messages; control sums and the EPC SEPA rules | [ISO 20022 Payments](plugin-iso20022.md) |
| **ebInterface** | Austrian XML e-invoices 4.1–6.1; line, tax and total calculations | [ebInterface](plugin-ebinterface.md) |
| **FinDatEx Templates** | EMT, EET, EPT and TPT (V7/V8) files as CSV or Excel | [FinDatEx Templates](plugin-findatex.md) |
| **XBRL (with Arelle)** | XBRL instances and inline XBRL reports, validated with Arelle | [XBRL](plugin-xbrl.md) |
| **PK-Meldewesen (FMA)** | Austrian pension fund reports to the FMA: quarterly (PK-QMV), annual (PK-FJMV) and ECB report | [PK-Meldewesen](plugin-pk-meldewesen.md) |
| **KAG-Meldewesen (FMA)** | Reports of Austrian management companies to the FMA: AIFMD reporting (AIFM and AIF file) with the FMA's rules | [KAG-Meldewesen](plugin-kag-meldewesen.md) |

The [FundsXML extension](fundsxml-extensions.md) is an extension as well, with its own page.

## Switching them on

The industry extensions are **off by default**: switch them on in **Settings → Extensions → INSTALLED EXTENSIONS**
and restart. Administrators can keep an extension off for everyone with `plugin.<id>.enabled=false`
(`iso20022`, `ebinterface`, `findatex`, `xbrl`, `kag-meldewesen`), see [Administration](administration.md).

## What every industry extension adds

- an **activity** in the Activity Bar with a side panel:
    - **ABOUT** — what the extension checks;
    - **ACTIVE DOCUMENT** — what the open document is (message type, invoice number, template,
      number of facts …), updated when you switch tabs;
    - **Check Active Document** — runs the extension's checks; the first ten problems are shown,
      **Show All Problems** opens the full list;
    - the extension's own actions (sample documents, new template files, Excel import, Arelle command);
    - **CHECK AN IDENTIFIER** — checks a pasted IBAN (ISO 13616: country length and check digits),
      BIC (ISO 9362), LEI (ISO 17442), ISIN (ISO 6166) or Austrian VAT id (UID), depending on the extension;
- **checks** that run with every validation (Validation panel, **F8**, `fxt-cli validate`) and appear
  with the extension's name as source — ISO 20022 and ebInterface; FinDatEx and XBRL files are checked
  from their side panel;
- where the standard has official XML Schemas, **Schema Library entries** (source *Extension*), so
  documents get their schema bound automatically. Remote schemas are downloaded once and cached.

| | Schemas | Checks with every validation | Sample / new documents | Identifier checker |
|---|---|---|---|---|
| ISO 20022 | 19 message versions | yes | SEPA credit transfer, SEPA direct debit | IBAN, BIC, LEI |
| ebInterface | versions 4.1–6.1 | yes | ebInterface 6.1 invoice | UID, IBAN, BIC |
| FinDatEx | — (field definitions bundled) | no, side panel | new EMT / EET / EPT / TPT files, Excel import | ISIN, LEI |
| XBRL | taxonomies via Arelle | no, side panel | — | — |
