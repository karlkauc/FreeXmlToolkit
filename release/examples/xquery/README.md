# XQuery 3.1 Examples (FundsXML4)

Seventeen runnable XQuery 3.1 scripts against FundsXML4 documents — one query per
`.xq` file with a documentation header. Files `01`–`11` are data-quality checks and
analyses that render HTML reports; files `12`–`17` are **reporting/export examples**
that combine several data sources and produce plain-text output formats.

**Target document:** `../xml/FundsXML4_Equity_Fund.xml` (they also work against any
other FundsXML4 file, e.g. `../xml/FundsXML_422_Bond_Fund.xml`).

## How to use

1. **Query Console** — open a FundsXML file in the editor, open the Query Console
   (XQuery mode), and paste the query from any file.
2. **Transform panel** — load the `.xq` file as an XQuery transformation.

## Data-quality checks (01–11, HTML output)

| File | Checks |
|------|--------|
| `01-portfolio-nav-reconciliation.xq` | sum of position values vs. reported NAV |
| `02-currency-consistency.xq` | FX rates present for foreign-currency positions |
| `03-date-validation.xq` | NAV dates vs. content date |
| `04-percentage-sum-validation.xq` | position percentages sum to 100% |
| `05-bond-calculation-check.xq` | bond market value = nominal × price formula |
| `06-identifier-uniqueness.xq` | duplicate UniqueIDs |
| `07-required-fields-check.xq` | mandatory position fields present |
| `08-data-completeness-score.xq` | completeness scoring per position |
| `09-directory-batch-report.xq` | batch analysis pattern (`collection()`) |
| `10-comprehensive-dq-summary.xq` | all checks combined into one scored report |
| `11-fund-lookthrough-analysis.xq` | fund-of-fund look-through candidates |

## Reporting & export (12–17, text/JSON output)

All of these join positions with `AssetMasterData/Asset` via `UniqueID`:

| File | Output | Shows |
|------|--------|-------|
| `12-positions-csv-export.xq` | CSV | full position export, CSV-escaping helper, `order by` |
| `13-ascii-kpi-dashboard.xq` | ASCII | boxed header, KPI block, top-5 table, `#` allocation bars |
| `14-markdown-fund-report.xq` | Markdown | multi-section report (overview, share classes, top 10, FX exposure) |
| `15-json-fund-export.xq` | JSON | maps/arrays + `fn:serialize` with the JSON output method |
| `16-asset-currency-pivot.xq` | ASCII | two-dimensional pivot (asset type × currency) with totals |
| `17-two-fund-comparison.xq` | Markdown | `doc()` + `doc-available` with graceful fallback |

> **Note on `doc()` (example 17):** queries pasted into the Query Console are
> compiled without a base URI, so *relative* `doc()` references cannot resolve.
> Set the `$comparisonFile` variable to an absolute `file:///...` URI to enable
> the two-fund mode; without it the query falls back to comparing the share
> classes of the active document.

> **Parse trap:** binding the whole document with a bare `let $root := /` followed
> by another `let` is a syntax error — bind the root element instead:
> `let $root := /FundsXML4`.

The official FundsXML example repository (<https://github.com/fundsxml/examples>)
covers XQuery analytics; the reporting/export series is FreeXmlToolkit-specific.
