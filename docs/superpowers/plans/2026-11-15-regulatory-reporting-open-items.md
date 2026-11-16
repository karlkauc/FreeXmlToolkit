# Regulatory reporting – open items (2026-11-15)

Plan: [2026-11-14-regulatory-reporting-extensions.md](2026-11-14-regulatory-reporting-extensions.md).

## Status
- Last commit with green CI: `bf99fe5ad`.
- Done:
  - PK-Meldewesen.
  - KAG-Meldewesen: AIFMD AIF/AIFM with the FMA's validation rules, and money market funds (ESMA auth.093).
  - ESMA Reporting:
    - EMIR: action matrix, conditional fields and date order;
    - SFTR: the same checks;
    - MiFIR.
- User docs: `docs/plugin-kag-meldewesen.md`, `docs/plugin-esma-reporting.md`, `docs/plugin-pk-meldewesen.md`.

## 1. 4. DeRiMV (derivative risk report) – done 2026-11-16
Built from the FMA's XSD of 2016 (`regdata/fma/DeRiMV - Novelle 2016-Okt.zip`): `DerimvRules`, bundled schema,
field labels, example, badge. The report has no confidence level, holding period, reference portfolio or NAV, so
those planned checks were dropped. Still unknown: the Incoming Platform's file-name convention and the submission
deadline; the FMA has no rule catalogue for this report.

## 2. AIFMD submission dialog – blocked
- The FMA's file-name convention for AIFMD reports (AIF and AIFM). No public source was found.
- The FMA's national code lists for AIFMD, if the FMA requires values beyond the ESMA lists.
- Whether the FMA has its own version of the AIFMD schema that differs from ESMA v1.2.

## 3. Rules that need outside data – left out on purpose
- EMIR/SFTR:
  - what kind of venue a MIC code stands for;
  - GLEIF (LEI status);
  - the UPI database;
  - EIC code lists;
  - the list of EEA countries;
  - the time a trade repository receives a report;
  - working days;
  - earlier reports of the same UTI.
- MiFIR: instrument reference data (FIRDS) and GLEIF.
- AIFMD: the FMA's code lists (see 2).
- Open question: is any of these sources available as a file (for example a GLEIF export or FIRDS) that could be read as an optional local file?

## Rules
- Real reports, LEIs, ISINs and fund names never go into the repo. Fixtures are synthetic: made-up LEIs and ISINs with valid check digits.
- Downloaded regulator documents live in `regdata/`, which is gitignored. fma.gv.at allows downloads with a browser user agent.
