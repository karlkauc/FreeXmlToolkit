# Working with files

How files get into the editor and what happens to them there: creating a file from a template or
schema, the file types the editor understands, and what the application does when a file changes
on disk.

## New File Dialog

> Creating a new file opens a guided dialog instead of silently
> opening an empty, untitled document. You can pick a starting template or an XSD schema, so
> a new file already has a sensible structure.

The **New File** dialog opens whenever you create a new document - from the toolbar **New**
button (Ctrl+N), the **Explorer** panel's **New file** action, or the **New File** card on the
Welcome screen. It walks you through a few simple choices and then opens (and optionally saves)
the new document.

| Field | What it does |
|-------|--------------|
| **File type** | Choose **XML**, **XSD**, **XSLT**, **Schematron**, **JSON**, **XProc**, **XQuery**, **XPath**, or **HTML**. The rest of the dialog adapts to your choice. |
| **Template** | A list of available templates - the built-in library plus your own templates from Settings - **automatically filtered to the selected file type**. The default is **"— None —"** (start blank). Picking a parameterized template prompts you for its values when the file is created. |
| **Schema** | *(plain XML only, and only when no template is chosen.)* Pick an XSD to base the document on - from your **Favorites**, your **Recent files**, or **Browse…** to select a file. The chosen schema is also bound to the new document, so it appears in the Validation panel and drives IntelliSense. |
| **Pre-fill mandatory nodes (empty)** | *(shown with the Schema option, on by default.)* When a schema is selected, the new document is pre-populated with all required elements and attributes (left empty) generated from that schema, giving you a ready-to-fill skeleton. |
| **Save to** | *(optional.)* Choose a location with **Browse…** to write the file to disk immediately and open it. Leave it empty to open the document as an **untitled** tab - you will be asked for a location the first time you Save. |

### What you get

- **A template** is rendered into the new document (prompting for any parameters first).
- **XML with a schema and "Pre-fill mandatory nodes"** produces a skeleton containing every
  mandatory element and attribute from that schema.
- **Otherwise** you get a minimal, valid starting point for the chosen type: an XML declaration
  for XML, an `xs:schema` skeleton for XSD, an `xsl:stylesheet` (version 3.0) skeleton for
  XSLT, an ISO-Schematron skeleton for Schematron, `{}` for JSON, a `p:declare-step`
  skeleton (with `source`/`result` ports and `p:identity`) for XProc, an
  `xquery version "3.1";` prolog for XQuery, and a minimal HTML5 page skeleton for HTML.

## Every Picked File Opens in the Editor {#picked-files}

Whichever way you pick a file, it is loaded into the editor as a tab (an open tab is reused):
**File → Open…** and **Open Recent**, the Welcome page's recent list, the Explorer and Favorites
panels, drag & drop onto the window or onto a panel row, and on every page the **Change** /
**Choose…** dialogs, the **recent** menus (Transform clock icon, Explorer *Transform with Other
Stylesheet* / *Validate with Other Schematron*) and the favorites star menus.

- An **input** (Transform or PDF/FOP XML input) becomes the active tab.
- A **stylesheet** opens behind the active tab while the input follows the active document,
  and in front when the input is a fixed file.
- A **schema, Schematron, JSON Schema, catalog or query target** opens behind the active
  tab, so the document it applies to stays in front.
- On a page in the main area (Signature, Schema Library) the file opens behind the page.

Not loaded: keystores and trust stores (binary files), and spreadsheets for import (the
converted XML opens instead).

## Files Changed Outside the Editor {#external-changes}

The shell watches the files of all open tabs. When another program changes or deletes one
of them, a dialog asks what to do - checked every two seconds while the FreeXmlToolkit
window is active, and immediately when you switch back to it.

| Situation | Choice | Result |
|-----------|--------|--------|
| **File changed on disk** | **Reload** | The tab shows the file's new content. Unsaved changes in that tab are discarded (the dialog warns you when there are any). Caret position and view mode (Text / Tree / Graphic) are kept. |
| | **Ignore** | The tab keeps its content and is marked as unsaved (**●**), because it now differs from the file. Saving overwrites the external change. You are not asked again until the file changes once more. |
| **File deleted on disk** | **Keep** | The tab stays open and is marked as unsaved (**●**). **Save** creates the file again. |
| | **Close** | The tab is closed, including any unsaved changes. |

Closing the dialog with **Esc** counts as **Ignore** / **Keep**. With several affected files
the dialogs appear one after the other, each with its tab brought to the front.

!!! note
    - Saving from the editor itself never triggers the dialog, and neither does a file that
      was merely touched or rewritten with identical content.
    - Changing a tab's file from elsewhere in the application - for example saving the right
      side of a diff, or exporting a result over a file that is open - is reported like any
      other outside change.
    - Untitled documents are not watched.

## Supported File Types

| Type | Extensions | Features |
|------|-----------|----------|
| **XML** | .xml, .wsdl | Text + graphic view, XSD/Schematron linking, IntelliSense, continuous validation |
| **XSD** | .xsd | Text + graphic view, Type Library, Type Editor, Schema Analysis, Documentation, Sample Data, Flatten |
| **XSLT** | .xsl, .xslt | XSLT editor + XML input + output preview, live transform, parameters, performance metrics |
| **XProc** | .xpl, .xproc | Pipeline editor (Text + Tree view), Run Pipeline via the embedded XML Calabash engine - see [XProc Pipelines](../transform-query/xproc.md#xproc-pipelines) |
| **XQuery** | .xq, .xquery, .xqm, .xqy | Query editor with highlighting + IntelliSense, Run Query against a selectable target - see [Query Documents](../transform-query/query-console.md#query-documents-the-target-selector) |
| **XPath** | .xpath | Single-expression query editor, Run Query against a selectable target - see [Query Documents](../transform-query/query-console.md#query-documents-the-target-selector) |
| **Schematron** | .sch, .schematron | Code editor + Visual Builder + Tester + Documentation Generator |
| **JSON** | .json, .jsonc, .json5 | Text + tree + editable **grid** view (see [The Grid for JSON](grid.md#json-grid)), JSONPath queries, JSON Schema validation. The JSON/JSONC/JSON5 flavor is detected from the content, so comments and trailing commas are accepted in any of the three. |
| **HTML** | .html, .htm, .xhtml | Rendered read-only **Preview** (the default view) + Text editing; also the format of HTML/XHTML transform results opened as editor tabs - see [HTML Preview](../../get-started/workspace-tour.md#html-preview) |

## Tools that read the saved file {#tools-that-read-the-saved-file}

Some tools read the document **from disk** rather than from the editor: **Generate Sample
XML…** and **Sample XML with Options…** (Schema panel), **Generate Documentation…** and
**signing** a document (Signature). If the document has **unsaved changes**, they first ask
*"Save changes to <file> first?"*:

- **Save and Continue** - saves the document, then runs the tool on the saved file.
- **Use Saved Version** - runs the tool on the file as it is on disk; your unsaved editor
  changes are not included.
- **Cancel** - does nothing.
