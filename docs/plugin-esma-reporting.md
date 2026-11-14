# ESMA Reporting (EMIR)

The **ESMA Reporting** extension supports ESMA's transaction reports in ISO 20022 - starting with
the **EMIR Refit derivatives trade report** (`auth.030.001.03`, ESMA usage guideline DATTAR 1.1.0),
which counterparties (also management companies for their funds) send to a trade repository.

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
- **Status bar badge:** `EMIR · 3 reports`; the side panel lists the reports per action type.
- **ESMA Reporting activity:** the document's summary, **Check Active Document**, an LEI/ISIN checker
  and an example error report (EROR) with fictitious data.

## Sources

ESMA: EMIR Refit incoming messages 1.1.0 (usage guidelines and schemas) and EMIR Refit validation
rules (esma74-362-2683). The field catalogue and the action-type table are generated with
`scripts/esma-emir-fields.py`.
