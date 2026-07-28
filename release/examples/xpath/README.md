# XPath 3.1 Examples (FundsXML4)

Twenty runnable XPath 3.1 expressions against FundsXML4 documents — one expression per
`.xpath` file, each with a `(: ... :)` comment header explaining what it does and which
language feature it showcases (maps, arrays, `fn:sort` with key functions, the arrow
operator `=>`, `let`, `||` concatenation, date arithmetic, …).

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

For full XQuery data-quality checks see `../xquery/` (files `01`–`10`).
The official FundsXML example repository (<https://github.com/fundsxml/examples>)
covers XQuery analytics but no XPath collection — this one is FreeXmlToolkit-specific.
