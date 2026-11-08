# Industry Plugins

FreeXmlToolkit ships optional [plugins](plugins.md) for industry standards. They are **off by
default**: switch them on in **Settings → Extensions → PLUGINS** and restart. Each one then adds

- an **activity** in the Activity Bar with a side panel: what the active document is, a
  **Check Active Document** button, sample documents and an identifier checker;
- **checks** that run with every validation (Validation panel, **F8**, `fxt-cli validate`) and appear
  with the plugin's name as source (ISO 20022 and ebInterface; FinDatEx and XBRL files are checked
  from their side panel);
- where the standard has official XML Schemas, **Schema Library entries** (source *Plugin*), so
  documents get their schema bound automatically. Remote schemas are downloaded once and cached.

The identifier checker in the side panels checks IBAN (ISO 13616, country length and check
digits), BIC (ISO 9362), LEI (ISO 17442), ISIN (ISO 6166) and the Austrian VAT id (UID).

## ISO 20022 Payments (SEPA)

For `pain`, `pacs` and `camt` messages (namespace `urn:iso:std:iso:20022:tech:xsd:…`).

**Schemas** of these versions are bound automatically, downloaded from iso20022.org:
pain.001.001.03/.09/.12, pain.002.001.03/.10, pain.007.001.09, pain.008.001.02/.08/.11,
pacs.002.001.10, pacs.004.001.09, pacs.008.001.08/.13, camt.052.001.08, camt.053.001.02/.08,
camt.054.001.02/.08, camt.056.001.08. Other versions: add them in the [Schema Library](schema-library.md).

**Checks** (all messages):

- IBAN (`IBAN`), BIC (`BICFI`, `BIC`, `AnyBIC`) and LEI (`LEI`) values, including check digits
- currency codes (`Ccy`) are three capital letters

Payment initiations `pain.001` and `pain.008`, and `pacs.008`:

- `GrpHdr/NbOfTxs` and `GrpHdr/CtrlSum` match the transactions of the message
- `PmtInf/NbOfTxs` and `PmtInf/CtrlSum` match the transactions of their payment group

Payment groups with service level `SEPA` (`PmtTpInf/SvcLvl/Cd`), after the EPC implementation
guidelines 2025 (SCT and SDD Core, customer-to-PSP):

- amounts in `EUR` only, between 0.01 and 999 999 999.99, at most two decimals
- `MsgId`, `PmtInfId`, `InstrId`, `EndToEndId` and `MndtId` use only the SEPA character set
  (`a-z A-Z 0-9 / - ? : ( ) . , ' +` and space), do not start or end with `/` and contain no `//`
- the charge bearer `ChrgBr` is `SLEV`

**Side panel:** the message type and name, the number of payment groups and transactions, their
total and whether it is SEPA; **New SEPA Credit Transfer (pain.001)** and **New SEPA Direct Debit
(pain.008)** open sample messages (schema-valid, version 2025) to start from.

## ebInterface (Austrian e-invoice)

For ebInterface invoices (root `Invoice`, namespace `http://www.ebinterface.at/schema/<version>/`).

**Schemas** of versions 4.1, 4.2, 4.3, 5.0, 6.0 and 6.1 are bound automatically, downloaded from the
standard's repository on GitHub (`austriapro/ebinterface-standards`).

**Checks**, after the ebInterface 6.1 specification:

| Check | Severity |
|---|---|
| Austrian VAT ids (`VATIdentificationNumber` starting with `ATU`): format and check digit; `ATU00000000` ("no UID") is accepted | error |
| `IBAN` and `BIC` | error |
| `PayableAmount` = `TotalGrossAmount` − `PrepaidAmount` + `RoundingAmount` (skipped with below-the-line items) | error |
| `LineItemAmount` = `Quantity` × `UnitPrice` (/ `BaseQuantity`), for lines without surcharges or reductions | warning |
| `TaxAmount` = `TaxableAmount` × `TaxPercent` / 100, for every tax item | warning |
| the taxable amounts of the tax summary (`Tax/TaxItem`) add up to the line amounts (without invoice-level surcharges or reductions) | warning |
| `TotalGrossAmount` = taxable amounts + taxes of the tax summary (without surcharges, reductions or other taxes) | warning |
| the due date is not before the invoice date | warning |

Amounts may differ by one cent of rounding. **Side panel:** document type, number, date, biller,
number of lines and payable amount; **New ebInterface 6.1 Invoice** opens a schema-valid sample.

## FinDatEx Templates (EMT, EET, EPT)

For the FinDatEx data templates: **EMT V4.3** (European MiFID Template), **EET V1.1.3** (European
ESG Template) and **EPT V2.1** (European PRIIPs Template). FinDatEx publishes no XML Schema; the
templates are exchanged as CSV or Excel files. The plugin carries the field definitions of the
official specifications (code, definition, codification, mandatory flag).

**Opening a file:** open the CSV file like any other file, or use **Open Excel Template File…** in
the side panel: the first sheet opens as semicolon-separated text (dates as `YYYY-MM-DD`, numbers
without formatting). Both layouts are recognised: one instrument per row under a header row of field
codes, or one field per row with one instrument per column. The separator (`;`, `,`, `|` or tab) is
detected. **New EMT / EET / EPT File** creates a file with the template's header row.

**Check Active Document** checks:

| Check | Severity |
|---|---|
| unknown columns, duplicate columns | warning / error |
| the template version (`00001_EMT_Version` …) is the one the checks follow | warning |
| dates (`YYYY-MM-DD`), date and time (`YYYY-MM-DD hh:mm:ss`), numbers, whole numbers and their range | error |
| closed code lists (`Y / N`, `Y / N / Neutral`, `R / P / B / N` …) | error |
| currencies (ISO 4217), LEIs (check digits), text lengths | error |
| the instrument identifier is a valid ISIN when its identification type is `1` | error |
| mandatory fields of the general sections (data set information, instrument or manufacturer information) are present and filled | warning |

Mandatory and conditional fields of the other sections depend on the reporting flags and on the
product; they are not checked. At most 500 value problems are listed per file. Problems name the row
(or instrument column), the instrument's identifier and the field, and jump to the line.

## XBRL (with Arelle)

For XBRL instances (root `xbrli:xbrl`) and inline XBRL reports (XHTML with `ix:` facts). XBRL
validation needs the taxonomy — XBRL 2.1, dimensions, calculations and formulas — which is what
[Arelle](https://arelle.org), the open-source XBRL processor, does. FreeXmlToolkit runs Arelle as a
separate program; it is **not bundled**:

1. Install it: `pip install arelle-release` (Python 3.11 or later). This puts `arelleCmdLine` on the PATH.
2. If it is not on the PATH, click **Choose Arelle Command…** in the XBRL side panel and pick the
   `arelleCmdLine` program (on Windows `arelleCmdLine.exe`).

**Side panel:** the kind of document, its number of facts, contexts and units, and which Arelle is
used. **Check Active Document** saves nothing itself — save the document first — and runs
`arelleCmdLine -f <file> -v` on it. Arelle's errors and warnings are listed with their code
(e.g. `[xbrl.4.6.2:numericUnit]`) and line; **Show All Problems** opens the full list. Information
messages (timings) are left out. A run stops after 10 minutes.

Arelle loads the taxonomies the document references from the internet (and caches them) with its
own network settings; the proxy settings of FreeXmlToolkit do not apply to it. XBRL documents are
not checked by the Validation panel or `fxt-cli validate`; use `arelleCmdLine` directly in scripts.
