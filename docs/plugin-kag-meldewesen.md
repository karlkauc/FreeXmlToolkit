# KAG-Meldewesen (FMA)

The **KAG-Meldewesen** extension supports reports of Austrian management companies (KAG, AIFM) to the
Austrian Financial Market Authority (FMA):

- the **AIFMD reporting** (Annex IV of Regulation (EU) 231/2013, § 22 AIFMG): the **AIFM file** and
  the **AIF file** in ESMA's XML format;
- the **money market fund report** (Article 37 of Regulation (EU) 2017/1131, quarterly via the FMA
  to ESMA) in ESMA's ISO 20022 format;
- the **derivative risk report** of UCITS management companies (§ 2a of the 4. DeRiMV, quarterly) in
  the FMA's XML format.

| Report | Root element | Schema |
|---|---|---|
| AIFM file | `AIFMReportingInfo` | ESMA `AIFMD_DATMAN_V1.2.xsd` |
| AIF file | `AIFReportingInfo` | ESMA `AIFMD_DATAIF_V1.2.xsd` |
| Money market fund report | `BizData` (head.003) with `Document` (auth.093), or `Document` alone | ESMA MMF schemas v04.1 |
| Derivative risk report | `Derivatemeldung` (no namespace) | FMA `Derivatemeldung.xsd` + `DerivateTypes.xsd` (2016) |

The extension is **off by default**: switch it on in **Settings → Extensions → INSTALLED EXTENSIONS**
(`plugin.kag-meldewesen.enabled`) and restart.

## What it does

![The KAG-Meldewesen panel with the example AIF file, checked](img/plugin-kag-meldewesen.png)

- **Schema binding:** both files are bound to ESMA's schemas v1.2 (shipped with the application) by
  their root element - validation (**F8**), IntelliSense, the grid and the Inspector use them.
- **FMA rules:** the FMA's *Prüfregeln AIF / AIFM Reporting File* (as of 29.01.2024) run with every
  validation (**F8**, Validation panel, `fxt-cli validate`). Each finding names the rule
  (`FMA-AIF-<field>`, `FMA-AIFM-<field>` after the field number of ESMA's tables) and the field, and
  jumps to its line:
    - reporting member state `AT`, reporting period (start date matches the period type, end date
      after the start and inside the period), change quarter of a reporting obligation change;
    - the blocks an AIF content type requires (principal information, individual information,
      leverage under Article 24(2) and 24(4)), nothing below `AIFCompleteDescription` when the
      no-reporting flag is set;
    - conditional fields: share class, master-feeder, base currency and FX rate, instrument
      identification (ISIN, AII), asset types `NTA`, market code types `MIC` / `OTC` / `NOT`,
      private equity only fields, risk measure types (`NET_DV01`, `NET_CS01`, `VEGA_EXPO`, `VAR` …),
      counterparty and CCP exposures, redemption rights, borrowing sources;
    - percentages that add up to 100 % (strategies, geographical focus of NAV and AuM, investor
      concentration, trading and clearing mechanisms, portfolio liquidity, investor groups,
      financing liquidity), with 0.5 percentage points for rounding;
    - rankings: the values of a ranked list do not grow with the ranking;
    - NAV not above the AuM, main instruments not above the AuM (1 % tolerance), inception date
      before the reporting period, monthly values only for months of the reporting period;
    - LEI and ISIN check digits.

  The checks of the national codes against the lists of the Incoming Platform can only run there.
- **Field labels:** select an element (text, tree or grid) and the Inspector's DOCUMENTATION starts
  with its ESMA field number and name, the M/C/O/F obligation, the FMA's rule and ESMA's technical
  guidance; the grid shows the same as tooltip of the element's name.
- **Status bar badge:** `AIFMD AIF Y1/2025`, `AIFMD AIFM Q4/2025`, `MMF Q1–Q4/2026`, `DeRiMV Q4/2025`; a click
  opens the side panel.
- **KAG-Meldewesen activity:** what the active document is, **Check Active Document**, an identifier
  checker (LEI, ISIN, BIC) and two examples (**New AIF File**, **New AIFM File**) with fictitious data.
- **Help menu:** **FMA AIFMD Reporting** opens the FMA's page with the rules and samples.

![The FMA rules finding a reporting period that ends before it starts and an LEI with wrong check digits](img/plugin-kag-meldewesen-problems.png)

Submit the files on the FMA Incoming Platform. The schemas are installed under
`~/.freeXmlToolkit/kag-meldewesen/aifmd-1.2/`.

## Money market fund report

The report is bound to ESMA's schemas v04.1 - the envelope (`BizData`, head.003), the business
application header (head.001) and the report (auth.093) together - and checked with the application
rules of ESMA's *MMF validation rules v07* that need only the file (`ESMA-MMF-<field code>`):

- header: sender `AT` (the FMA), recipient `EU`, message definition `auth.093.001.01`, creation date
  after the reporting end date;
- reporting year from 2020, period ranges (from/to quarter), start and end date inside the quarters,
  the authorising country equal to the sender, no two reports of one MMF and period in a file;
- share classes (one flagged with the highest NAV), inception, merger and liquidation dates;
- daily and weekly maturing assets below 100 %, the liquidity profile (four ranges, each once,
  adding up to 100 %), the yield breakdowns (each range type once), the input factor of the
  macro-economic stress scenarios;
- assets: counterparty name without LEI, no accrued interest for the valuation methods `MTMO` and
  `AMCS`, reset dates after the reporting end date, amounts in the base currency next to the EUR
  amounts;
- ISO 4217 currencies, ISO 3166 and EU countries, ISIN and LEI check digits.

Checks against ESMA's register (authorised MMFs, earlier reports) only run at ESMA. **New Money
Market Fund Report (Example)** opens a fictitious report.

## Derivative risk report (4. DeRiMV)

The whole report of a management company is one XML file (§ 2a Abs. 1): the company's name, bank code
(BLZ), year and quarter as attributes of `Derivatemeldung`, then one `InvestmentFonds` per UCITS with the
items of § 2a Abs. 2 (as amended by BGBl. II Nr. 242/2016): FMA code, ISIN, fund name, global exposure at
the reporting date and the maximum of the period in percent, the method (`Comm`, `aVaR`, `rVaR`) and,
optionally, the fund's own limit. It is bound to the FMA's schema of 2016 (installed under
`~/.freeXmlToolkit/kag-meldewesen/derimv-2016/`) and checked with these rules (`DeRiMV-<item>`):

| Rule | Check | Severity |
|---|---|---|
| `DeRiMV-4` | ISIN check digit; every ISIN once in the file | error |
| `DeRiMV-7` | exposure at the reporting date not above the period maximum | error |
| `DeRiMV-7` | commitment approach at most 100 % of the net asset value, absolute VaR at most 20 % | error |
| `DeRiMV-7` | relative VaR at most 200 % (twice the reference portfolio's VaR) | warning |
| `DeRiMV-9` | absolute VaR: period maximum not above the fund's own limit, own limit not above 20 % | warning |
| `DeRiMV-period` | the quarter has ended; reports from the reporting date 31 December 2016 | warning |

The FMA publishes no rule catalogue for this report, so the rules follow the regulation. The report
carries no confidence level, holding period, reference portfolio or net asset value - these VaR
parameters cannot be checked on the file. The relative VaR check is only a warning: reports give the
ratio to the reference portfolio (limit 200 %), while the formula of § 16 Abs. 1 Z 3 since 2016 measures
the excess over it (limit 100 %), and the regulation does not say which of the two the field holds. The
FMA codes per fund are provided on the Incoming
Platform. **New Derivative Risk Report (Example)** opens a fictitious report.

## Sources

ESMA: AIFMD reporting XML documents v1.2 (2013-1361) and IT technical guidance rev 6 (2013-1358).
FMA: *Informationen zum Inhalt und zur Übermittlung des AIFMD-Reportings* (December 2023) and the
*Prüfregeln* (29.01.2024). ESMA: MMF reporting XML schemas v04.1 and validation rules v07
(esma65-8-6480). The field catalogues are generated with `scripts/aifmd-fields.py` and
`scripts/esma-mmf-fields.py`. 4. DeRiMV (BGBl. II Nr. 266/2011 as amended by BGBl. II Nr. 242/2016) and
the FMA's XSD of 6 October 2016; its field labels (`derimv/derimv-fields.json`) are written by hand.
