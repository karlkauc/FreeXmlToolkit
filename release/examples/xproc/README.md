# XProc 3.0 Pipeline Examples

[XProc 3.0](https://xproc.org/) is a W3C-community language for XML processing
pipelines: instead of running one stylesheet or one validation at a time, a
pipeline declares a chain of steps (transform, validate, split, merge, modify)
that documents flow through. FreeXmlToolkit executes pipelines with the
embedded [XML Calabash 3](https://xmlcalabash.com/) engine.

## Running a pipeline in FreeXmlToolkit

1. Open a `.xpl` file — it is a first-class editor document with XML syntax
   highlighting.
2. Open the FundsXML instance you want to process (for example
   `examples/xml/FundsXML4_Equity_Fund.xml`), or pick any file via the
   **Target** dropdown in the editor toolbar.
3. Press **Run Pipeline** (or `Ctrl+Enter`). The result appears in the
   **OUTPUT** panel below the editor — XML, CSV/text and JSON results are
   routed to the matching view automatically.

Pipelines that reference other files (stylesheets, Schematron schemas,
`p:document` hrefs) resolve relative paths against the pipeline file's
directory — keep the folder structure intact.

## The examples

All examples run against the bundled FundsXML4 instances in `../xml/`.

| File | Demonstrates | Output |
|------|--------------|--------|
| `01-identity.xpl` | Pipeline anatomy: `p:declare-step`, ports, `p:identity` | XML (input passthrough) |
| `02-extract-fund-core.xpl` | `p:delete` with XPath match patterns (slimming/redaction) | XML without Positions/FXRates |
| `03-positions-csv.xpl` | `p:xslt` reusing the bundled CSV stylesheet via relative href | CSV (one row per position) |
| `04-stamp-metadata.xpl` | Multi-step chaining: `p:add-attribute` + `p:insert`, computed options | XML with audit metadata |
| `05-for-each-shareclass.xpl` | `p:for-each` splitting, inline XSLT, `p:wrap-sequence` | XML share-class report |
| `06-choose-by-fund-type.xpl` | `p:variable`, `p:choose`/`p:when` branching on content | XML with category attributes |
| `07-schematron-report.xpl` | `p:validate-with-schematron`, reading a secondary port (`report@step`) | SVRL validation report |
| `08-fund-kpis-json.xpl` | XML→JSON conversion, JSON media type routing | JSON fund summary |

## Engine notes (Saxon-HE)

FreeXmlToolkit ships Saxon-HE, so a few standard steps are not available:

- `p:validate-with-xml-schema` and `p:xsl-formatter` require Saxon-EE — use
  the Validation activity (Xerces, full XSD 1.1) and the PDF/FOP activity
  instead.
- `p:validate-with-json-schema` is not supported in this bundle.
- `p:http-request` works but network access to remote schemas/documents is
  generally discouraged; the examples are fully offline.

See also: `../xpath/` and `../xquery/` for the query example collections, and
the user documentation section *XML Editor → Bundled Example Collections*.
