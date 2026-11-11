# Validation Panel

Open the **Validation** panel from the activity bar to validate the active document.

> The panel offers a SOURCES section, a Single file / Batch mode toggle, a primary
> **Run Validation** button, a status line, and - in Batch mode - a color-coded RESULTS
> list.

## Sources

The **SOURCES** section shows the schemas bound to the active document, and its rows
follow the document's type: for XML-family documents it shows the **XSD** and
**Schematron** rows, for JSON documents a single **JSON Schema** row.
Each row has two lines: the first carries the icon, a caption (**XSD**, **Schematron** or
**JSON Schema**), the favorites button and **Change**; the second shows the bound file's
name (long names are shortened in the middle - hover for the full path). An empty row
shows a hint such as *"No XSD bound — choose or drop a file"*, and clicking it works like
**Change**.
Click the **Change** button next to a source to pick a different file. **Every**
row carries a **star button** next to its *Change* button: on the XSD row it opens a
quick-select menu of your favorited XSD schemas, on the Schematron row a menu of your
favorited Schematron files, and on the JSON Schema row a menu of your favorited JSON
files - pick one to bind it in a single click, without browsing the
file system. The menus group entries by favorites folder when you use more than one, and
are grayed out while you have no matching favorites. (See
[Favorites](favorites-system.md).)

**All source rows are drop targets**: drop an `.xsd` file from
your file manager onto the XSD row, a `.sch` / `.schematron` file onto the Schematron
row, or a `.json` schema onto the JSON Schema row, to bind it to the active document in
one move - the row glows **green** while a
loadable file hovers over it and **red** for a wrong file type (which is rejected). The
dropped file behaves exactly like one picked via **Change** or a favorite, including
appearing in the recent list.

- **The referenced XSD binds automatically.** When the XML declares its schema
  (`xsi:schemaLocation` / `xsi:noNamespaceSchemaLocation` - local or remote), that XSD is
  bound when the file is opened, so the declared schema is the default. The
  declaration is also re-checked against the **current editor content before
  every validation run** (Run Validation, the toolbar's Validate, and live validation):
  a schema reference you add or change in the editor is picked up immediately - even in
  unsaved or untitled documents - and a removed reference downgrades validation to a
  well-formedness check (the status bar shows **"No XSD"**). A declared location is first
  looked up in your XML catalogs and Schema Library mappings; remote `https` schema URLs
  that no catalog redirects are downloaded to the schema cache, and if the download fails,
  a Schema Library mapping for the document's namespace is used instead. The status bar
  shows how the schema was found - plain **"XSD: name"** for a declared location,
  **"(catalog)"**, **"(library)"** or **"(manual)"** otherwise (see
  [Status Bar](unified-shell.md#status-bar)). A schema you picked yourself (via **Change**, a favorite, or
  the status bar) is never overridden.
- **JSON documents work the same way**: a top-level `"$schema"` member pointing at a
  schema file or URL auto-binds that **JSON Schema** on open, is re-checked before every
  validation run, and a manual choice always wins. (Values pointing at `json-schema.org`
  meta-schemas are dialect declarations, not bindings, and are ignored.) See
  [JSON Editor](json-editor.md) for details and the supported drafts.
- **Click a bound source name to open the file in the editor** - one click on the XSD or
  Schematron name opens it as a tab for direct editing.

!!! tip
    You can also bind a schema **without opening the Validation panel**: click the
    **schema indicator** in the status bar (for XML it reads **"No XSD"**, **"XSD: name"**,
    **"Detecting XSD…"** or **"XSD error"**; for JSON documents the same states read
    **"No JSON Schema"**, **"JSON Schema: name"**, and so on; see
    [Status Bar](unified-shell.md#status-bar)) or click the editor toolbar's **Schema** button
    (**Set XSD Schema…**) and pick a schema file. For XML the binding drives both
    **IntelliSense** and **schema validation**; for JSON it drives **validation**.

## Running a Validation

1. Pick a mode with the **Single file | Batch** toggle.
2. Click **Run Validation**.

- **Single file** validates the active document against the bound XSD and/or Schematron.
- **Batch** validates a whole set of XML files. In Batch mode, **Run Validation** opens a
  small menu with two ways to pick the files:
    - **Select XML files…** - a file chooser where you pick one or more XML files.
    - **Select folder…** - a folder chooser; every `*.xml` file in the folder **and all of
      its subfolders** is validated.

  The **RESULTS** list then shows one row per file with a status icon (red ✕ = errors,
  orange ⚠ = warnings only, green ✓ = valid) and a badge with the problem count. Select a
  row to see that file's problems in the panel's **PROBLEMS** list; double-click to open
  the file. The plain-text batch report opens from the **REPORTS & CHECKS** section
  (**Open Last Batch Report**).

The **status line** below the button summarises the run: **Valid**, **1 problem** /
**N problems**, or - when no schema is bound and only the well-formedness check ran -
**Well-formed · no schema bound** (shown neutral, not green - in the panel and in the
Inspector's badge: bind an XSD in SOURCES to validate against a schema).

The **RESULTS** list and the panel's own **PROBLEMS** list are only shown in **Batch**
mode. In **Single file** mode the problems are listed in the PROBLEMS panel below the
editor, and the side panel shows a single row such as *"3 problems — show below the
editor"* - click it to expand and focus that PROBLEMS panel.

## Problems

Problems appear in two places:

- The **PROBLEMS** list in the side panel (**Batch mode only**): the problems of the file
  selected in RESULTS. **Export Problems to Excel** in the **REPORTS & CHECKS** section
  saves the current list as an `.xlsx` workbook.
- The **PROBLEMS panel below the editor**: it appears automatically when
  validation finds problems, shows error/warning counts in its header, and can be collapsed
  to just the header. Drag its top edge to change its height (the height is remembered). Each row shows the message and the file/line in a monospaced label.

Every row in the PROBLEMS panel below the editor carries a
**source badge** - **XSD**, **Schematron**, **Well-formed**, or **JSON Schema** - so when a
run combines several checks you can tell at a glance where each problem comes from.
Hovering a **Schematron** row shows a **tooltip** with the full message, the failed
rule/test expression, and the XPath of the failing node.

Long messages are cut off in the row. **Right-click** a row in the PROBLEMS panel below the
editor to copy it: **Copy Message** (just the message text), **Copy Problem** (severity,
source, message, file and line/column, plus rule and context for Schematron) or **Copy All
Problems** (one problem per line). **Ctrl+C** (**Cmd+C** on macOS) copies the selected row
like Copy Problem.

While the [AI assistant](ai-assistant.md) is on, the same menu - in the PROBLEMS panel and in the side panel's
list - also offers **Fix with AI** and **Explain with AI** (below any quick fixes). A fix is applied to a copy
and validated against the same schema and Schematron first; the **AI** panel below the editor shows it side by
side with the result of the checks, and **Apply** makes it one edit (Ctrl+Z undoes it). See
[Fix or explain a validation problem](ai-assistant.md#fix-or-explain-a-validation-problem).

Selecting a problem in either list shows it **in the view you are in**:

- **Text** - the caret jumps to its line and, for XML and XSD errors, to the reported column.
- **Tree** - the element of that line is selected and scrolled into view.
- **Graphic** - the grid selects the element's row; inside a repeating group it opens the table,
  selects the cell (the attribute's cell when the message names an attribute) and opens table
  cells on the way to errors nested deeper. The row or cell flashes briefly, the red problem
  marker stays. For an XSD the diagram selects the schema node.

The view does not change. This works for
**Schematron problems too**: the failing rule's context node is resolved back to its
line in the XML, so a click navigates straight to the offending element.

Schematron problems whose rules define **quick fixes** (SQF)
can be corrected in place: right-click the problem row in either list and pick a fix
from the **Quick Fix** submenu, or use the **yellow lightbulb** that appears in the
editor gutter on lines with fixable problems (click it, or press **Alt+Enter** /
**Ctrl+.** on the line). See [Schematron Quick Fixes](schematron-quick-fixes.md).

## Detailed Schematron Report

> A full report of the last Schematron run, opened as a tool tab
> and saveable as HTML or SVRL.

After validating a document that has a Schematron file bound, open the report in
either of two ways:

- Click the **Validation Report** row in the Validation panel's **REPORTS & CHECKS** section (it is
  enabled once a run with a bound Schematron has finished).

The report opens as a **Schematron Report** tool tab showing the document name, the
Schematron file, a severity summary (errors / warnings), and a table with one row per
finding:

| Column | Content |
|--------|---------|
| **Severity** | Error or Warning |
| **Line** | Line in the XML document |
| **Message** | The rule's message text |
| **Rule / Test** | The Schematron test expression that failed |
| **Context (XPath)** | The XPath of the failing node |
| **Fix** | A lightbulb button on findings that offer [Schematron Quick Fixes](schematron-quick-fixes.md) — click it to apply a fix directly from the report |

**Click a row** to jump straight to that line in the editor. Two buttons export the
report:

- **Save Report (HTML)** - a self-contained HTML file you can archive or share.
- **Save SVRL (XML)** - the raw SVRL output (Schematron Validation Report Language)
  of the run, for further processing by other tools.

## The Tool Sections and the ⋮ Menu

The tools are labelled rows (icon + name) in two collapsible sections at the top of
the panel. Both start open; click a header to collapse it.

**REPORTS & CHECKS** - results and exports of a run:

| Row | What It Does |
|-----|--------------|
| **Validation Report** | Open the [detailed Schematron report](#detailed-schematron-report) of the last validation run (enabled after such a run) |
| **Export Problems to Excel** | Save the current problems as an Excel workbook (enabled while there are problems) |
| **Open Last Batch Report** | Open the plain-text report of the last batch run |

FundsXML validation lives in the [FundsXML panel](fundsxml-extensions.md) (when the
FundsXML extension is enabled).

**SCHEMATRON AUTHORING** - tools for writing Schematron rules (usable without a run):

| Row | What It Does |
|-----|--------------|
| **Rule Templates** | Insert ready-made Schematron rule patterns |
| **Schematron Tester** | Run the Schematron rules against an XML file |
| **Rule Builder** | Build rules visually |
| **Check Rules** | Run an error detector over the Schematron itself and show a categorised issue table |
| **Schematron Documentation** | Open the Schematron documentation generator |

The panel header's ⋮ (overflow) menu keeps only the **Validate while typing** toggle for
continuous (debounced) validation.

> **Check Rules** inspects the Schematron file for problems and lists them
> by category - XML syntax, structural, XPath, semantic, and best-practice issues - so you can
> fix mistakes in the rules before you rely on them. See
> [Schematron Validation](schematron-support.md) for details.
