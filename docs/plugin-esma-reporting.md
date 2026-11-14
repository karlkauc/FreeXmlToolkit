# ESMA Reporting (EMIR, SFTR, MiFIR)

The **ESMA Reporting** extension supports ESMA's transaction reports in ISO 20022 - starting with
the **EMIR Refit derivatives trade report** (`auth.030.001.03`, ESMA usage guideline DATTAR 1.1.0)
and the **SFTR securities financing transaction report** (`auth.052.001.02`, usage guideline 1.2.0),
which counterparties (also management companies for their funds) send to a trade repository, and the
**MiFIR transaction report** (`auth.016.001.01`), which investment firms send to their supervisor.

The extension is **off by default**: switch it on in **Settings → Extensions → INSTALLED EXTENSIONS**
(`plugin.esma-reporting.enabled`) and restart.

## What it does

- **Schema binding:** a report that starts with `Document` in the `auth.030.001.03` namespace is
  bound to ESMA's schema; a report in the business data envelope (`BizData`, head.003, with the
  business application header head.001) is bound to a driver schema that validates envelope, header
  and report together. The same driver is used for ESMA's money market fund report of the
  [KAG-Meldewesen](plugin-kag-meldewesen.md) extension.
- **ESMA's validation rules:** with every validation (**F8**, Validation panel, `fxt-cli validate`):
    - **per action type and level** - for each report (`New` = NEWT, `Mod` = MODI, `ValtnUpd` = VALU,
      `Crrctn` = CORR, `Termntn` = TERM, `Err` = EROR, `Rvv` = REVI, `PosCmpnt` = POSC; level from
      `Lvl`, trade or position) the fields ESMA marks *mandatory* must be reported and those marked
      *not applicable* must not: `EMIR-1.1 Field 1.1 · Reporting timestamp is mandatory for action
      type VALU`. The table comes from ESMA's *EMIR Refit validation rules* (esma74-362-2683);
    - the number of records in the header against the reports;
    - the UTI (an LEI followed by up to 32 letters or digits);
    - LEI and ISIN check digits, ISO 3166 country codes.

  Conditional fields (*C* in ESMA's table) depend on other fields' values and are not checked yet;
  ESMA's full rules text is in the Inspector.
- **Field labels:** every element shows its EMIR field number and name, the obligation per action
  type, ESMA's rules text and the details to be reported in the Inspector's DOCUMENTATION and as grid
  tooltip - also inside the envelope.
- **Status bar badge:** `EMIR · 3 reports`, `SFTR · 1 report`, `MiFIR · 2 transactions`; the side panel lists the reports per action type.
- **ESMA Reporting activity:** the document's summary, **Check Active Document**, an LEI/ISIN checker
  and an example error report (EROR) with fictitious data.

## SFTR

SFTR reports (`Document` in the `auth.052.001.02` namespace or in the `BizData` envelope) are checked
the same way, with ESMA's *SFTR validation rules* (ESMA74-362-1008): the table distinguishes the
action type (`New` = NEWT, `Mod` = MODI, `Err` = EROR, `EarlyTermntn` = ETRM, `PosCmpnt` = POSC,
`CollUpd` = COLU, `Crrctn` = CORR, `ValtnUpd` = VALU), the level (`LvlTp`: TCTN or PSTN) and the type
of SFT, read from the loan data (`RpTrad` repo, `BuySellBck` buy-sell back, `SctiesLndg` securities
lending, `MrgnLndg` margin lending) - `SFTR-2.3 Field 2.3 · Event date is mandatory for action type
COLU`. UTI, LEI and ISIN are checked too; **New SFTR Error Report (Example)** opens a fictitious report.

## MiFIR

MiFIR transaction reports (`Document` in the `auth.016.001.01` namespace or in the envelope) are
checked with the content rules of ESMA's *MiFIR transaction reporting validation rules*
(esma65-8-2594) that need no instrument reference data, GLEIF or MIC register; the rule id is ESMA's
error code:

- venue: no trading venue transaction code, branch country or waiver indicator for `XOFF`/`XXXX`
  (CON-030, CON-372, CON-610); a transmitted order is reported with `XOFF`/`XXXX` (CON-362);
- trading capacity: no transmission for `DEAL`/`MTCH` (CON-251); with `DEAL` the buyer or seller
  is the executing entity (CON-290) and the investment decision within the firm is reported
  (CON-570);
- LEIs of executing, submitting and transmitting firms, buyers, sellers and decision makers; ISINs
  of instrument and underlyings; branch countries; national identifiers start with a country code;
  CONCAT codes (20 characters, date of birth as in the person's data); dates of birth not after the
  trading date;
- currencies: ISO 4217 without the special codes (XAU, XXX …);
- the instrument fields the CFI code (field 43) makes mandatory or forbids: notional currencies,
  underlying index name and term, option type (and its value for calls, puts, others), strike price,
  exercise style, maturity and expiry date (CON-441 … CON-551); maturity and expiry not before the
  trading date.

**New MiFIR Transaction (Example)** opens a fictitious share purchase on a venue.

## Sources

ESMA: EMIR Refit incoming messages 1.1.0 (usage guidelines and schemas) and EMIR Refit validation
rules (esma74-362-2683); SFTR counterparty and TR data exchange 2023 and SFTR validation rules
(ESMA74-362-1008); MiFIR transaction reporting XML schema 1.1.0 (ESMA65-8-2598) and validation rules
(esma65-8-2594). The field catalogues and the action-type tables are generated with
`scripts/esma-emir-fields.py`, `scripts/esma-sftr-fields.py` and `scripts/esma-mifir-fields.py` (the
MiFIR element-to-field mapping of RTS 22 is part of that script).
