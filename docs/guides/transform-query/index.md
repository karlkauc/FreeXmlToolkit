# Transform & query

XSLT, XQuery, XPath and XProc run inside the editor: the stylesheet or query is a normal editor
tab, the **Transform** panel holds stylesheet, input and parameters, and the result appears in
the **OUTPUT** panel below the editor.

| Task | Page |
|------|------|
| Run a stylesheet, set parameters, preview live, process many files | [Run a transformation](run-xslt.md) |
| Ask the open document a question with XPath, XQuery or JSONPath | [Query console](query-console.md) |
| Step through XSLT, XQuery or Schematron | [Debugger](debug.md) |
| Find the slow templates and functions | [Profile a transformation](profile.md) |
| Test stylesheets and Schematron rules | [Test with XSpec](xspec.md) |
| Run an XProc 3.0 pipeline | [XProc pipelines](xproc.md) |
| Use Saxon HE 13, xsltproc or another processor | [External XSLT processors](external-processors.md) |
| Copy a pattern to start from | [XSLT and XQuery examples](examples.md) |
| Keep a transformation with its input and output, check results against expected files | [Projects & scenarios](../projects.md) |

## Where everything is

| What | Where |
|------|-------|
| Edit the stylesheet or query | Open the `.xsl` / `.xq` file; it is a normal editor tab |
| Choose stylesheet and input | The **Transform** panel (activity bar → **Transform**): **STYLESHEET** and **INPUT** |
| Run | **Run Transform** in the panel, or **Ctrl+Enter** with the stylesheet active |
| See the result | The **OUTPUT** panel below the editor |
| Parameters, output method | The panel's **PARAMETERS** and **OUTPUT METHOD** sections |
| Queries | The [Query console](query-console.md) (**Ctrl+Shift+X**) |
| Debug | **Debug** next to **Run Transform**, or **Shift+F5** |
| Batch, profile, trace | The panel's **OPTIONS & TOOLS** section |

Every field of the panel: [Transform panel reference](../../reference/transform-panel.md).
