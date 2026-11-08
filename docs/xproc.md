# XProc Pipelines

XProc 3.0 pipelines (`.xpl`, `.xproc`) are first-class editor
documents, executed with the embedded **XML Calabash 3** engine:

![An XProc pipeline after Run Pipeline, with its CSV result in the OUTPUT panel](img/unified-shell-xproc-pipeline.png)
*The bundled `03-positions-csv.xpl` example after **Run Pipeline**: the pipeline runs
the CSV export stylesheet (referenced with a relative href) against the Automatic
target and streams the result into the OUTPUT panel.*

- **Run Pipeline** (the toolbar's **Run** button or its ▾ menu, or `Ctrl+Enter`) runs the active pipeline. The
  primary input (`source` port) is the document chosen in the **Input** dropdown —
  by default the most recently active XML document, exactly like
  [Run Query and Run Transform](query-console.md#query-documents-the-target-selector).
- Relative `href`s in the pipeline (`p:document`, `p:xslt` stylesheets, Schematron
  schemas) resolve against the pipeline file's directory.
- Results appear in the **OUTPUT panel**; XML, HTML, text/CSV and JSON outputs are
  routed to the matching view automatically.
- Self-contained pipelines (no input ports, or ports with default bindings) run
  without a target.
- **File → New** offers an XProc skeleton (`p:declare-step` with `source`/`result`
  ports and `p:identity`).
- Ready-to-run examples ship in `examples/xproc/` — see
  [Bundled Example Collections](xml-editor.md#bundled-example-collections).

!!! note "Saxon-HE limitations"
    `p:validate-with-xml-schema` and `p:xsl-formatter` require Saxon-EE and are not
    available; use the Validation activity (Xerces, full XSD 1.1) and the PDF/FOP
    activity instead. `p:validate-with-json-schema` is not supported in this bundle.

## Restrictions

Pipelines are programs, so by default they run with restrictions:

- Pipelines that use **`p:os-exec`** (run a program), **`p:run`** (run a pipeline computed at run
  time) or **`p:http-request`** are not run, also when the step is in an imported library. The
  message names the step.
- Documents, imports and `doc()` may only be **local files**; loading from `http:`, `https:` or
  `ftp:` fails with a message that says so.
- Reading and writing local files (`p:store`, `p:file-copy`, `p:file-delete`, …) works as before,
  with your rights: run only pipelines you trust.

**Settings → Network & Security → Run XProc pipelines without restrictions** lifts all of it (an
administrator can lock it, see [Administration & Policies](administration.md)). The same rules apply
to `fxt-cli transform`. See also the [Threat Model](threat-model.md).
