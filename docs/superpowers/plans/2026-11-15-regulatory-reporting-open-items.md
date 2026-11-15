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

## 1. 4. DeRiMV (derivative risk report) – not built
The XSD is only on the FMA Incoming Platform and is not public. Needed:
1. The current FMA XSD, with every XSD it includes (a ZIP is fine).
2. If possible, a sample report. It is for local cross-checks only and never goes into the repo; anonymise it or make one up.
3. The FMA's technical description: field descriptions, validation rules, deadline and reporting period.
4. The file-name convention for submissions on the Incoming Platform.
5. Whether one file is reported per management company or per UCITS, and which fields are mandatory for commitment, relative VaR and absolute VaR.

Put the files in `regdata/fma/`, which is gitignored.

Planned checks:
- the XSD;
- the risk method per UCITS (commitment, relative VaR or absolute VaR);
- limits:
  - commitment ≤ 100 % of NAV;
  - absolute VaR ≤ 20 %;
  - relative VaR ≤ 2 × the reference portfolio;
- confidence level 99 %, holding period 20 days;
- whether the leverage is plausible;
- date logic.

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
