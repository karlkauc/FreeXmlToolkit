# Project file (.fxtproj)

All paths are relative to the folder of the project file, so the project can be moved or checked
out elsewhere. The file can be edited by hand; the open project picks up the change when the file
is saved.

```json
{
  "name": "Orders",
  "folders": ["."],
  "schemaBindings": [
    { "files": "data/**/*.xml", "schema": "schemas/order.xsd", "scenario": "Report" },
    { "files": "**/*.xml", "schematron": "rules/common.sch" },
    { "files": "**/*.json", "schema": "schemas/order.schema.json" }
  ],
  "catalogs": ["schemas/catalog.xml"],
  "scenarios": [
    {
      "name": "Report",
      "stylesheet": "xsl/report.xsl",
      "input": "${file}",
      "parameters": { "year": "2026" },
      "outputFormat": "HTML",
      "output": "out/${name}.html"
    },
    {
      "name": "Invoice PDF",
      "stylesheet": "xsl/invoice-fo.xsl",
      "input": "${file}",
      "outputFormat": "PDF",
      "output": "pdf/${name}.pdf"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `name` | Shown in the Explorer and the Transform panel; default: the file name |
| `folders` | The project's folders; the first one is the Explorer workspace (default `.`) |
| `schemaBindings` | `files` (pattern) → `schema`, `schematron`, `scenario`, `xsdVersion` (`auto`, `1.0`, `1.1`), `xmlVersion` (`auto`, `1.0`, `1.1`), `schematronBinding` (`xslt`, `xslt2`, `xslt3`) (each optional); first match per part — the versions see [Language Versions](standards-versions.md) |
| `catalogs` | OASIS XML catalogs |
| `scenarios` | `name`, `stylesheet`, optional `input` (`${file}` = the file it runs for), `parameters`, `outputFormat` (`XML`, `HTML`, `XHTML`, `TEXT`, `JSON`, `PDF`; default from `xsl:output`), `output` (`${name}`, `${dir}`), `processor` (PDF only: `builtin` or the name of an [external XSL-FO processor](../guides/publish-pdf.md#external-xsl-fo-processors) from the settings; default: the default processor), `xsltProcessor` (XSLT and PDF: `builtin` = Saxon HE or the name of an [external XSLT processor](../guides/transform-query/index.md#external-xslt-processors) from the settings; default: the default XSLT processor; an unknown name fails the run), `expected` (like `output`), `compare` (optional: `ignore` list of XPaths, `tolerance`, `ignoreOrder`, `keys`, `placeholders`, `ignoreComments`, `exactWhitespace`), `languageVersion` (optional: XSLT `1.0`/`2.0`/`3.0` or XQuery `1.0`/`3.0`/`3.1`, see [Language Versions](standards-versions.md)) |

Project files from before these additions keep working unchanged.
