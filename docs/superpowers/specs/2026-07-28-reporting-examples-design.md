# Reporting-Oriented XPath/XQuery Examples (Design)

**Date:** 2026-07-28
**Status:** Approved

## Goal

The bundled example collections (`release/examples/xpath/`, `release/examples/xquery/`)
each demonstrate a single query per file. This extension adds *evaluation/reporting*
examples that combine multiple data sources (portfolio positions, asset master data,
transactions, share classes) and render results in output formats users actually
consume: CSV, ASCII tables/charts, Markdown tables, and JSON.

## Data foundation

Both bundled FundsXML4 instances support real joins:

- `Position/UniqueID` ↔ `AssetMasterData/Asset/UniqueID` (100 % coverage in both files)
- `Transaction/AssetUniqueID` ↔ `Asset/UniqueID`
- `Position/TotalValue/Amount[@ccy = Fund/Currency]` is single-valued in both files
  (equity fund: HUF, bond fund: EUR)

## New files

### XPath 21–32 (one XPath 3.1 expression per file, `let` + inline functions)

| # | Example | Format / technique |
|---|---------|--------------------|
| 21 | Position list with asset join (ISIN, name, type, country, value, %) | CSV + quote escaping |
| 22 | Aggregation per currency (count, sum, share) | CSV, `distinct-values` grouping |
| 23 | Top-10 positions, fixed column widths, header/separator rows | ASCII table, inline padding function |
| 24 | Asset-type allocation with `█` bar chart | ASCII chart |
| 25 | Share-class overview (name, ISIN, shares, price, TAV) | Markdown table |
| 26 | Country exposure with asset join, sorted | Markdown table |
| 27 | Fund KPIs as JSON object | JSON, `serialize` + maps |
| 28 | Positions as JSON array | JSON, arrays + maps |
| 29 | Transaction report: Transaction × Asset × Fund | 3-way join, `\|\|` |
| 30 | Grouped report per country (heading + indented lines) | multi-line string report |
| 31 | Bond maturity buckets (<1y, 1–5y, 5–10y, >10y) | date arithmetic + ASCII |
| 32 | Executive-summary one-pager | combination of all techniques |

### XQuery 12–17 (output method `text`, except 15 = JSON)

| # | Example | Format |
|---|---------|--------|
| 12 | Full position export with asset-master join + escaping function | CSV |
| 13 | KPI dashboard: boxes, tables, bars rendered as text | ASCII |
| 14 | Complete fund report (sections, tables) for pasting into docs | Markdown |
| 15 | Full fund export (maps/arrays, JSON serialization) | JSON |
| 16 | Cross-tab asset type × currency (pivot) | ASCII |
| 17 | Fund comparison via `doc()` ($comparisonFile) with share-class fallback | Markdown |

Example 17: queries are compiled without a base URI (both the Query Console and
`XsltTransformationEngine` compile from strings), so relative `doc()` references
can never resolve. The query therefore exposes a `$comparisonFile` variable for
an absolute `file:///` URI (two-fund mode) and otherwise falls back to a
share-class comparison of the active document — it always produces a real
side-by-side table.

## Constraints observed

- No bare `/` followed by `let` in XQuery (Saxon parse trap) — bind `/FundsXML4`.
- `TotalValue` carries one `Amount` per currency — filter on `@ccy`.
- Text output method requires `string-join` over element construction.
- Query Console renders atomic sequences line-by-line (`XmlServiceImpl.getXmlFromXpath`),
  so XPath examples return sequences of strings or a single joined string.

## Fixes uncovered by the new tests

- `XmlServiceImpl.getXmlFromXpath` never declared the `map`/`array`/`math`
  function namespaces, so bundled example 12 (`map:merge`) failed in the Query
  Console → prefixes are now pre-declared.
- Bundled example `19-nav-per-share-recon.xpath` used the XQuery-only
  `for ... let ... return` form, which is a syntax error in XPath → rewritten
  as `for ... return let ... return`.

## Test coverage

- `ExamplesIntegrityTest.allBundledXQueries_executeAgainstTheEquityExample`
  already executes every `.xq` file; the minimum count rises 11 → 17.
- New test: execute every `.xpath` file through `XmlService.getXmlFromXpath`
  against the equity example; assert a non-empty, error-free result.

## Documentation

- Extend `release/examples/xpath/README.md` (table rows 21–32).
- Add `release/examples/xquery/README.md` in the same style (files 01–17).
