# XPath 3.1 Examples (FundsXML4)

Thirty-two runnable XPath 3.1 expressions against FundsXML4 documents — one expression per
`.xpath` file, each with a `(: ... :)` comment header explaining what it does and which
language feature it showcases (maps, arrays, `fn:sort` with key functions, the arrow
operator `=>`, `let`, `||` concatenation, date arithmetic, …).

Files `01`–`20` are single-query basics; files `21`–`32` are **reporting examples**
that combine several data sources (positions ⋈ asset master data ⋈ transactions)
and render the result as CSV, ASCII table/chart, Markdown table or JSON.

**Target document:** `../xml/FundsXML4_Equity_Fund.xml` (they also work against any
other FundsXML4 file, e.g. `../xml/FundsXML_422_Bond_Fund.xml`).

## How to use

1. **Query Console** — open a FundsXML file in the editor, open the Query Console
   (XPath mode), and paste the expression from any file.
2. **Snippets menu** — copy the `.xpath` files to `~/.freeXmlToolkit/queries/xpath/`;
   they then appear as saved queries in the Query Console's snippets list.
3. **FundsXML content cache** — `.xpath` files inside a downloaded FundsXML examples
   package are seeded into the snippet repository automatically.

## The collection

| File | Shows |
|------|-------|
| `01-fund-names.xpath` | basic path + `string()` |
| `02-fund-nav.xpath` | attribute access, `concat` |
| `03-position-count.xpath` | `count` |
| `04-positions-over-5-percent.xpath` | numeric predicate, union in a path step |
| `05-total-portfolio-value.xpath` | `sum` with attribute predicate |
| `06-distinct-currencies.xpath` | `distinct-values` |
| `07-asset-types.xpath` | `fn:sort` (3.1) |
| `08-isin-list-sorted.xpath` | dedup + sort |
| `09-top-5-positions.xpath` | `fn:sort` with key function, `subsequence` |
| `10-percentage-sum.xpath` | arrow operator `=>` |
| `11-shareclass-navs-map.xpath` | map constructor, simple map operator `!` |
| `12-positions-per-currency.xpath` | `map:merge` grouping |
| `13-total-fees.xpath` | element-existence predicate |
| `14-largest-position.xpath` | sort + `last()` |
| `15-nav-age-in-days.xpath` | date arithmetic with `xs:dayTimeDuration` |
| `16-fund-summary-string.xpath` | `\|\|` concatenation, `format-number` |
| `17-buy-transactions.xpath` | filtering + formatting |
| `18-assets-without-lei.xpath` | negative predicate (data-quality check) |
| `19-nav-per-share-recon.xpath` | `let` expression, reconciliation logic |
| `20-position-ids-array.xpath` | array constructor, `array:size` |

## The reporting collection (21–32)

All of these join positions with `AssetMasterData/Asset` via `UniqueID` and
demonstrate a specific output format:

| File | Output | Shows |
|------|--------|-------|
| `21-positions-to-csv.xpath` | CSV | asset join, inline CSV-escaping function |
| `22-currency-aggregation-csv.xpath` | CSV | grouping via `distinct-values`, aggregation |
| `23-top10-ascii-table.xpath` | ASCII table | inline `lpad`/`rpad` padding functions |
| `24-asset-type-bar-chart.xpath` | ASCII chart | `█` bars, sorted allocation |
| `25-shareclass-markdown-table.xpath` | Markdown | pipe table of all share classes |
| `26-country-exposure-markdown.xpath` | Markdown | country grouping via asset join |
| `27-fund-kpis-json.xpath` | JSON | map constructor + `fn:serialize` (JSON method) |
| `28-positions-json-array.xpath` | JSON | array of maps, sorted by value |
| `29-transaction-report.xpath` | text | 3-way join Fund × Transaction × Asset |
| `30-country-grouped-report.xpath` | text | heading + indented detail lines per group |
| `31-bond-maturity-buckets.xpath` | ASCII chart | date arithmetic, bucket maps, scaled bars |
| `32-executive-summary.xpath` | text | multi-section one-pager combining everything |

> **Note:** In XPath (unlike XQuery FLWOR) a `for` clause cannot be followed by
> `let` directly — write `for ... return let ... return ...` instead. The
> reporting examples use this pattern throughout.

For full XQuery data-quality checks and reports see `../xquery/` (files `01`–`17`).
The official FundsXML example repository (<https://github.com/fundsxml/examples>)
covers XQuery analytics but no XPath collection — this one is FreeXmlToolkit-specific.
