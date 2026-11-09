# XSpec Test Examples

[XSpec](https://github.com/xspec/xspec) is a unit-test framework for XSLT, XQuery and
Schematron: a `.xspec` file describes scenarios — an input, a call or a context — and what
the code under test must return. FreeXmlToolkit bundles XSpec 3 and runs the tests with its
Saxon-HE engine; nothing else needs to be installed.

**The `.xsl`, `.xqm` and `.sch` files are the code under test, the `.xspec` files the tests.**
Run the tests with Tools → Run XSpec Tests on an open `.xspec` file (not with Transform).

## Writing your own

**File → New…**, type **XML**, template **XSpec test (XSLT)** (or XQuery, Schematron): a
skeleton with one scenario. Save it as `name.xspec` next to the code it tests.

## Running the tests

**In FreeXmlToolkit**

1. Open a `.xspec` file from this folder.
2. Choose **Tools → Run XSpec Tests**. The **XSpec** tab shows the summary (passed,
   failed, pending) and every scenario with its tests. Failed scenarios are expanded, and
   **Expected** / **Actual** show the difference.
3. **Run Again** after you changed the code or the tests, **HTML Report** opens XSpec's own
   report, **Export JUnit…** writes a JUnit XML report for a CI server.

**On the command line**

```bash
fxt-cli xspec 01-xslt-functions.xspec                    # summary on standard output
fxt-cli xspec 0[1-4]-*.xspec                             # several files, exit code 1 on a failure
fxt-cli xspec 04-schematron.xspec -r junit.xml           # JUnit XML report
fxt-cli xspec 02-xslt-templates.xspec -f html -r report.html
```

The stylesheet, query module and Schematron under test are found relative to the `.xspec`
file, and the examples read `../xml/FundsXML4_Equity_Fund.xml` — keep the folder structure.

## The examples

| File | Tests | Demonstrates | Result |
|------|-------|--------------|--------|
| `01-xslt-functions.xspec` | `fund-utils.xsl` | `x:call function`, `x:param`, `x:expect select` and `test` with `$x:result`, nested scenarios | 11 passed |
| `02-xslt-templates.xspec` | `fund-utils.xsl` | `x:context` inline and from a file (`href` + `select`, or the whole document), XML expectations, the `...` wildcard, a `pending` scenario | 6 passed, 1 pending |
| `03-xquery-module.xspec` | `fund-queries.xqm` | testing an XQuery library module (`query` + `query-at`), parameters from a file and inline XML, sequence results | 5 passed |
| `04-schematron.xspec` | `fund-rules.sch` | `x:expect-assert`, `x:expect-not-assert`, `x:expect-report` with `count`, `x:expect-valid` (warnings do not count) | 8 passed |
| `05-failing-demo.xspec` | `fund-utils.xsl` | **fails on purpose** (2 of 3 tests) to show Expected/Actual, the JUnit export and exit code 1 | 1 passed, 2 failed |

The code under test:

- `fund-utils.xsl` — functions `f:weight-class`, `f:format-amount`, `f:is-valid-isin`
  (ISIN check digit) and template rules turning `Position` elements into report lines. Run it on
  a FundsXML file directly (Transform) and it writes a position report per fund.
- `fund-queries.xqm` — XQuery functions `q:total-nav`, `q:positions-over`, `q:currencies`.
- `fund-rules.sch` — Schematron rules for ISIN length, currency codes, the sum of the
  position weights and concentrated positions (a warning).

A good way to start: break something in `fund-utils.xsl` (for example change the 10 % bound
of `f:weight-class`), run `01-xslt-functions.xspec` again and watch the failures.

See also: *Testing with XSpec* in the user documentation (`docs/xslt-developer.md`), and the
other collections in `../xslt/`, `../xquery/` and `../schematron/`.
