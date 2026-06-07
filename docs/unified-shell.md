# Unified Shell

> The application opens directly into the **Unified Shell** - one workspace that
> combines XML, XSD, XSLT, Schematron and JSON editing with all the validation,
> transformation, signing and documentation tools. The separate legacy tabs have
> been consolidated here.

## Overview

The Unified Shell is the single workspace for everything FreeXmlToolkit does. Instead of
switching between separate editor pages, you open files as tabs in the central **editor
host** and reach every tool through the **activity bar** on the left. You can work with an
XML file next to its XSD schema, XSLT stylesheets and Schematron rules at the same time.

![Unified Shell overview](img/unified-shell-overview.png)
*The Unified Shell: activity bar (left), Explorer side panel, editor host with an XML file
(Text/Tree/Graphic/Grid view toggle), the Properties inspector (right) and the status bar.*

### Layout

| Area | Purpose |
|------|---------|
| **Activity bar** (far left) | Switch tools / side panels: Explorer, Transform, Validation, Signature, Type Library, FOP/PDF, Favorites, Settings, Help. |
| **Side panel** | The panel for the selected activity (e.g. the Transform panel, the Validation panel). |
| **Editor host** (center) | Tabs of open documents, each with view modes - Text, Tree, Graphic (XSD), Grid (XML). |
| **Inspector** (right) | View **and edit** the selected node's properties from any view. |
| **Status bar** (bottom) | Caret position, validation status and a memory indicator. |

### Key Features

- **Multi-tab editing** - Open multiple files of different types in one view
- **Automatic file type detection** - Files are recognized by extension (.xml, .xsd, .xsl, .sch, .json)
- **View modes per document** - Text, Tree, Graphic (XSD) and Grid (XML), all over one shared model
- **Inspector editing everywhere** - edit node properties from the Text, Tree, Grid and Graphic views, not just one
- **Integrated XPath/XQuery** - the Transform panel queries any XML-based file
- **Search & Replace** - Ctrl+F / Ctrl+H across the editor
- **Favorites** - Quick access to frequently used files

## Getting Started

1. The Unified Shell opens automatically on startup.
2. Use the **Explorer** activity (or **File → Open**) to open files; **File → New** creates a new file.
3. Files open as tabs in the editor host - switch tabs by clicking their headers, and switch view modes (Text / Tree / Graphic / Grid) with the view toggle.

## Supported File Types

| Type | Extensions | Features |
|------|-----------|----------|
| **XML** | .xml | Text + graphic view, XSD/Schematron linking, IntelliSense, continuous validation |
| **XSD** | .xsd | Text + graphic view, Type Library, Type Editor, Schema Analysis, Documentation, Sample Data, Flatten |
| **XSLT** | .xsl, .xslt | XSLT editor + XML input + output preview, live transform, parameters, performance metrics |
| **Schematron** | .sch | Code editor + Visual Builder + Tester + Documentation Generator |
| **JSON** | .json, .jsonc, .json5 | Text + tree view, JSONPath queries, JSON Schema validation |

## Toolbar

The toolbar provides common operations and context-sensitive buttons:

### Always Visible
- **New** - Create XML, XSD, XSLT, Schematron, or JSON files
- **Open** (Ctrl+O) - Open one or more files
- **Save** (Ctrl+S) - Save the current tab
- **Save As** - Save the current tab under a new name (a file chooser opens, pre-set to the tab's file type)
- **Save All** (Ctrl+Shift+S) - Save every open tab at once
- **Recent** (Ctrl+Shift+R) - Recently opened files
- **Close** (Ctrl+W) - Close current tab
- **Validate** (F5) - Validate current document
- **Format** (Ctrl+Shift+F) - Pretty-print current document
- **Undo** (Ctrl+Z) / **Redo** (Ctrl+Y)
- **View** - Switch between Tabs, Side-by-Side, or Top-Bottom split views
- **Convert** - XML to/from Excel/CSV (Ctrl+E)
- **Templates** (Ctrl+T) - XML template system
- **Generator** (Ctrl+G) - Generate XSD from XML
- **Tools** - Open FOP (PDF Generation) or Digital Signatures as tool tabs

### Shown for XML Files
- **Console** - Toggle log output panel
- **XSLT** - Toggle embedded XSLT development panel
- **Template** - Toggle template development panel

### Shown for JSON Files
- **Minify** - Remove all whitespace from JSON
- **Schema** - Load/clear JSON Schema, validate against schema

### Shown for Schematron Files
- **Insert** - Quick-insert Pattern, Rule, Assert, or Report elements

## Panel Toggles

- **Linked** (Ctrl+L) - Show/hide linked files panel
- **XPath** (Ctrl+Shift+X) - Show/hide XPath/XQuery query panel
- **Properties** (Ctrl+Shift+P) - Show/hide properties and validation sidebar. For XML files,
  the properties inspector lets you view **and edit** a node's properties (element name,
  namespace, attributes, and text content) from **all three** views - Text, Tree, and Grid -
  not just the Grid view. For XSD files, the same inspector lets you edit a schema node's
  properties from **all three** XSD views - Text, Tree, and Graphic. See
  [Properties Inspector](#properties-inspector) below.
- **Favorites** (Ctrl+Shift+B) - Show/hide favorites panel

## XSD Views & Tools

When editing an XSD file, the editor host and the **Schema** activity provide:

![XSD schema in the Graphic view](img/unified-shell-schema-graphic.png)
*An XSD open in the Graphic view, with the Schema activity panel (Type Library, Flatten,
Statistics, Schema Quality, Generate Sample XML / Documentation) on the left.*

![XSD schema in the Tree view](img/unified-shell-schema-tree.png)
*The same schema in the Tree view - select a node to edit its properties in the inspector.*

- **Text** - Source code editing with syntax highlighting; moving the caret into a schema construct also lets you edit its properties in the Properties pane (see [Properties Inspector](#properties-inspector))
- **Graphic** - Visual XMLSpy-style schema diagram
- **Type Library** - Browse all types with filtering, search, and usage counts
- **Type Editor** - Edit ComplexTypes graphically, SimpleTypes with form editor
- **Analysis** - Schema statistics, identity constraints, quality checks
- **Documentation** - Generate HTML/Word/PDF documentation
- **Sample Data** - Generate sample XML from the schema
- **Flatten** - Merge included/imported schemas into a single file

## XSLT Features

When editing an XSLT file:

- **Transform** - Run XSLT transformation with XML input
- **Live Transform** - Auto-execute on changes
- **Parameters** - Define XSLT parameters (name/value pairs)
- **Output Format** - Choose XML, HTML, or Text output
- **Performance** tab - Execution time, throughput, star rating
- **Debug** tab - Messages, warnings, template execution trace
- **Open in Browser** - View HTML output in default browser

## Transform Panel

> **New in June 2026** - Added recent-stylesheet history, a watch-and-rerun option,
> result timing, and an XQuery result table.

The **Transform** panel (open it from the **Transform** icon in the activity bar on the
left) runs XSLT transformations and XQuery expressions against the active XML document.

### Choosing a Stylesheet

- **Set XSLT…** - Pick the XSLT stylesheet to apply.
- **Recent** - A drop-down menu listing the XSLT stylesheets you used most recently, so you
  can reapply one in a single click. The menu also offers **Clear recent** to empty the list.
- **Watch file** - When checked, the transform re-runs automatically whenever the chosen
  stylesheet changes on disk. This is handy while you edit a stylesheet in another tool and
  want to see the effect immediately.

### Running and Viewing Results

1. Set an XSLT stylesheet (or type an XQuery, see below).
2. Click **Transform** (XSLT) or **Run XQuery** (XQuery).
3. The result appears in the **RESULT** area.

The RESULT header shows a compact **timing stat** in the form `N ms · M chars` so you can
see how long the run took and how large the output is.

- **Browser** - Opens the result (typically HTML) in your system web browser.

### XQuery Result Table

The RESULT area has a **Text / Table** toggle:

- **Text** - Shows the raw result as text (the default).
- **Table** - When an XQuery returns a **sequence** of items, the result is shown as a table.
  Each item becomes a row, and the columns are taken from each item's child elements (or, if
  an item has no child elements, its attributes). A sequence of plain values is shown in a
  single **value** column.

To use it, write an XQuery that returns a sequence (for example
`for $x in /root/item return $x`), click **Run XQuery**, then switch the toggle to **Table**.

### Advanced XSLT tools

The Transform side panel's **Advanced** section adds:

- **Debug** - opens the stylesheet as a document with a breakpoint gutter and a
  Debug tool tab (step into/over/out, continue, stop; variables, call stack,
  breakpoints, and XPath watches).
- **Batch…** - runs the active stylesheet/XQuery over many XML files, with
  per-file results and "Save All".
- **Profile** / **Trace** - when checked, a transform also opens a read-only
  Profile (timings + per-template execution times) or Trace (template matches +
  `xsl:message` output) tool tab.

The XQuery console offers built-in **Examples** (simple, FLWOR, HTML report,
data-quality check).

> XSLT version selection (1.0/2.0/3.0) is intentionally not offered: Saxon HE
> auto-detects the version from the stylesheet's `version` attribute, so an
> explicit selector would be cosmetic.

## Validation Panel

Open the **Validation** panel from the activity bar to validate the active document.

### Binding a Schema

- **Set XSD…** - Pick an XSD schema to validate the document against.
- **Favorites** - A quick-select menu that lists your favorited XSD schemas. Pick one to bind
  it in a single click, without browsing the file system. (See [Favorites](favorites-system.md).)
- **Schematron…** - Pick a Schematron file to apply business-rule validation.

### Schematron Tools

When a Schematron file is involved, the panel offers a set of Schematron tools:

| Tool | What It Does |
|------|--------------|
| **Rule Templates** | Insert ready-made Schematron rule patterns |
| **Tester** | Run the Schematron rules against an XML file |
| **Rule Builder** | Build rules visually |
| **Check Rules** | Run an error detector over the Schematron itself and show a categorised issue table |
| **Documentation** | Open the Schematron documentation generator |

> **Check Rules (new in June 2026)** inspects the Schematron file for problems and lists them
> by category - XML syntax, structural, XPath, semantic, and best-practice issues - so you can
> fix mistakes in the rules before you rely on them. See
> [Schematron Validation](schematron-support.md) for details.

## Schema Panel: Sample-Data Generation

Open the **Schema** panel from the activity bar while an XSD file is active. Alongside type
browsing, the panel offers actions to generate sample XML from the schema:

- **Generate Sample XML** - The simple generator. It builds one sample document using
  mandatory-only / maximum-occurrence options and realistic example values.
- **Generate Sample XML (Advanced)…** - Opens a dialog for full control over how the sample
  data is built (see below).

### Advanced Sample-Data Generation

> **New in June 2026** - A rule-based generator with per-XPath strategies, batch output, and
> reusable profiles.

The advanced dialog turns the schema's XPaths into an editable table. For each XPath you
choose a generation **Strategy** plus a value or pattern:

| Strategy | What It Produces |
|----------|------------------|
| **Auto** | Type-based automatic value (the default) |
| **Fixed Value** | A fixed literal you type |
| **Sequence** | An auto-incrementing value from a pattern (for example `ORD-{seq:4}`) |
| **Enum Cycle** | Cycles through the allowed enumeration values |
| **Template** | A string built from a template with placeholders |
| **Random from List** | A random pick from a comma-separated list |
| **XPath Reference** | Copies the value from another XPath |
| **XSD Example** | An example value taken from the schema's annotations |
| **Omit / Empty / Null** | Skip the node, leave it empty, or set `xsi:nil` |

You can also set **batch options** - a **count** and a **file-name pattern** (for example
`order_001.xml`, `order_002.xml`) - and **Save** / **Load** named **profiles** so you can reuse
a configuration later.

The dialog can either generate a **single document** (which opens in a new tab) or a **batch**
of files written to a folder you choose. For a full walkthrough, see
[Profiled XML Generation](profiled-xml-generation.md) and the
[Sample XML Generator](xsd-tools.md#7-sample-xml-generator) section of the XSD Tools guide.

## Inspector (XSD Properties)

> **New in June 2026** - The XSD Properties inspector gained app-info editing, multi-language
> documentation, comment editing, and constraint deletion.

When an XSD file is open, the **Properties** inspector (Ctrl+Shift+P) shows the selected schema
node. In addition to name, type, cardinality, facets, and constraints, you can now:

- **Edit the node's `xs:appinfo`** - The machine-readable metadata attached to the node.
- **Edit multi-language `xs:documentation`** - One row per language. Use **Add language** to add
  a translation and the **✕** button to remove one.
- **Edit comments** - Select an XSD comment in the tree to edit its text. To add a new comment,
  use **Add Comment…** in a node's right-click context menu.
- **Delete a constraint** - In the **CONSTRAINTS** section, select a `key`, `keyref`, `unique`,
  or `assert` constraint and click **Delete constraint** to remove it.

## Signature Panel: Trust Validation

> **New in June 2026** - Real trust-chain validation against a trust store.

The **Signature** panel (open it from the activity bar) signs and validates XML signatures.
Alongside **Sign**, the basic **Validate**, the detailed **Validate (Details)**, and
**Create Certificate**, it now offers:

- **Validate (Trust)** - Performs full PKIX validation of the signing certificate chain against
  a **trust store**, producing a trust report (trusted / trust anchor / revocation / timestamp).
- **Trust store…** - Choose the trust store to validate against. It defaults to the JVM's
  built-in `cacerts` store.
- **Check revocation (OCSP/CRL)** - When checked, the validation also checks whether the
  certificate has been revoked, using OCSP or CRL.

See [XML Digital Signatures](digital-signatures.md) for full details.

## Settings Panel

> **Expanded in June 2026** - The Settings panel now exposes the full application
> configuration, grouped into sections.

Open the **Settings** panel from the gear icon at the bottom of the activity bar. Change any
option and click **Save Settings** to apply (theme changes apply immediately).

| Section | Options |
|---------|---------|
| **Theme** | Switch between **Light** and **Dark**. |
| **Editor** | XML indent and JSON indent (spaces); **Auto-format after loading**; **Pretty-print XSD on save**; **Pretty-print Schematron on load**. |
| **XSD** | **Auto-save** (with an interval in minutes); **Create backups on save** (with the number of versions to keep, and an optional **separate backup directory**). |
| **Parser** | **XML parser** engine (Xerces or Saxon); **Allow XSLT extension functions**. |
| **Temp & Cache** | **Use system temp folder** or a custom temp folder; **Clear Temp Folder** to free disk space. |
| **General** | **Check for updates on startup**; **Use small icons**. |
| **HTTP Proxy** | **Use system proxy**, or enter a proxy host and port. |

## Welcome / Dashboard

> **New in June 2026** - The welcome screen now shows live statistics and quick tips.

When no document is open, the editor shows a welcome dashboard with:

- **Stat cards** - At-a-glance counts of your **Recent files**, **Favorites**, **Templates**,
  and **Saved queries**.
- **Tips banner** - A short hint banner with handy shortcuts (for example, drag a file onto the
  window to open it, or use Ctrl+F / Ctrl+H to find and replace).
- **Recent files** list - Click an entry to reopen it.

## Status Bar

> **New in June 2026** - A memory monitor was added to the status bar.

The status bar at the bottom of the window includes a **memory monitor** showing the JVM heap
usage as **used / max MB**. **Click it** to run garbage collection, which can free memory after
working with large files.

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+O | Open file |
| Ctrl+S | Save current tab |
| Ctrl+Shift+S | Save all tabs |
| Ctrl+W | Close tab |
| Ctrl+Z / Ctrl+Y | Undo / Redo |
| Ctrl+F | Find |
| Ctrl+H | Find and Replace |
| F5 | Validate |
| Ctrl+Shift+F | Format |
| Ctrl+D | Add to favorites |
| Ctrl+L | Toggle linked files |
| Ctrl+Shift+X | Toggle XPath panel |
| Ctrl+Shift+P | Toggle properties |
| Ctrl+E | XML to Spreadsheet |
| Ctrl+T | Templates |
| Ctrl+G | Generate XSD |

> **Search in XML files (updated in v1.10):** Pressing **Ctrl+F** opens an inline search bar with up/down chevron arrows for Find Previous / Find Next. The search works in both the **Text** and **Graphic** views of an XML file. In the Graphic view it searches element names, attribute names, and values across the whole document, auto-expands collapsed nodes to reach a match, selects the matching row, and scrolls it into view; matches wrap around when you reach the end. Switching between the Text and Graphic sub-tabs while the bar is open re-targets the search to the active view. **Replace** is available in the Text view only. See [XML Editor Features](xml-editor-features.md#search-find) for details.

## Compare & Merge

Both the XML and XSD editors include a **Compare** toolbar button (tooltip *"Compare with file..."*)
for side-by-side diffing and merging.

1. Click **Compare** and pick a file to compare against the current document.
2. A new tab opens titled `Compare: <left> ↔ <right>` with synchronized scrolling and live re-diff.
3. Changed lines are highlighted, with intra-line word-level coloring.

Merge controls let you reconcile the two files:

| Control | Shortcut | Action |
|---------|----------|--------|
| **Prev** / **Next** | Alt+Up / Alt+Down | Jump between changed chunks |
| Per-chunk arrows | - | Apply a single change left→right or right→left |
| **All →** / **All ←** | - | Apply every change in one direction |
| **Re-compute** | - | Recompute the diff manually |
| **Save Left** / **Save Right** | - | Write a pane back to its file |

The diff recomputes automatically about 300 ms after you stop typing.

## Jump to Validation Errors

Validation errors appear in the **Properties / Validation** sidebar (Ctrl+Shift+P). **Double-click**
any error to jump straight to its location:

- In the **Text** view the caret moves to the exact line and column, with the offending text highlighted.
- In the **Graphic** view the matching element is selected, flashed, and scrolled into view.
- In split mode both views navigate at once.

## Properties Inspector

> **Updated in v1.10** - For XML files, property editing works in **all** XML views (Text, Tree,
> and Grid). Previously it was available only in the Grid view.
>
> **Updated June 2026** - For XSD files, property editing now works in the **Text** view too,
> matching the Tree and Graphic views. See [XSD Files](#xsd-files) below.

### XML Files

When an XML file is open, the Properties sidebar (Ctrl+Shift+P) shows the selected node and
lets you edit it from whichever view you are in:

- **Text view** - Move the text caret into an element to select it. The inspector shows the
  element's name, namespace, attributes, and text as editable fields, plus read-only,
  schema-derived hints (type, documentation, valid child elements, and example values) when a
  schema is bound. Edits round-trip into the source as a minimal change that preserves your
  caret and scroll position. If the caret is not inside a well-formed element, the inspector
  falls back to a read-only name/XPath view.
- **Tree view** - Click any node (element, text, comment, CDATA, or processing instruction) to
  edit its properties.
- **Grid view** - Select a row to edit its properties. The Grid view also handles structural
  editing (adding, deleting, and moving nodes) through its right-click context menu.

All three views share one in-memory model per open document, so your edits and Undo/Redo
history are preserved when you switch between Text, Tree, and Grid.

### XSD Files

When an XSD (schema) file is open, the Properties sidebar shows the selected schema node and
lets you edit it from whichever view you are in. XSD files have three views - **Text**, **Tree**,
and **Graphic** (there is no Grid view for XSD):

- **Tree** and **Graphic** views - Select a schema node to edit its name, type,
  cardinality/occurrence, use, form, constraints, documentation, and facets. (Unchanged.)
- **Text** view - Move the text caret into an XSD construct (such as an `xs:element`,
  `xs:complexType`, `xs:simpleType`, `xs:attribute`, a compositor, or a facet) to select the
  matching schema node and edit the same properties you would in the Tree and Graphic views -
  without leaving the source editor. Edits round-trip into the schema text as a minimal change
  that preserves your caret and scroll position. If the caret is not inside a recognizable
  construct (for example inside an `xs:annotation`, a comment, or blank space), the pane falls
  back to a read-only caret/XPath view.

All three XSD views share one in-memory schema model, so your edits and Undo/Redo history are
preserved when you switch between Text, Tree, and Graphic. Structural editing (adding, deleting,
and moving nodes) remains a Tree/Graphic capability through the right-click context menu.

## XPath / XQuery Autocomplete

The XPath/XQuery console (Ctrl+Shift+X) offers context-aware autocomplete in both the XPath and
XQuery input fields. Suggestions appear automatically after trigger characters (`/`, `[`, `@`, `(`,
`$`, `::`) or on demand with **Ctrl+Space**. Depending on context it suggests element names, attribute
names, XPath axes, functions, and (in XQuery) variables. Navigate with the arrow keys and press
**Enter** or **Tab** to insert; **Escape** dismisses the popup. Functions and axes are inserted with
their parentheses / `::` automatically.

### Saving, Loading and Examples

The XPath/XQuery console toolbar provides the same query management as the XML Editor, acting on
whichever tab (XPath or XQuery) is currently active:

- **Saved Queries** - dropdown of previously saved queries of the active type. It also offers
  *Add Current Query to Favorites…* and *Open Queries Folder…*.
- **Save** - store the current expression under a name (kept alongside the XML Editor's queries).
- **Load** - open a saved query file (`*.xpath` / `*.xquery` / `*.xq`) into the active tab.
- **Examples** - insert ready-made sample expressions for the active query type.

Queries are stored in the shared query folder, so anything saved here is also available from the XML
Editor's XPath/XQuery panel and vice versa.

## Drag and Drop

Drag files from your file manager directly into the editor to open them. Multiple files can be dropped at once.

## Where the former tabs went

The Unified Shell consolidates what used to be separate sidebar tabs. The earlier
standalone editors - XSD Editor, XSD Validation, JSON Editor, XSLT Viewer, Schematron,
Schema Generator, Digital Signatures and FOP/PDF - have been **retired** and their
functionality now lives in the shell:

| Former tab | Now in the shell |
|------------|------------------|
| XSD Editor / Tools | Open an `.xsd`: Text/Tree/Graphic views + inspector; **Type Library** activity for type editing, documentation, flatten and schema analysis |
| XSD Validation | **Validation** activity (single + batch, XSD & Schematron) |
| JSON Editor | Open a `.json`: Text + Tree views |
| XSLT Viewer | **Transform** panel (set stylesheet, transform, preview, browser) |
| Schematron | **Validation** activity: check rules, templates, tester, builder, documentation, CSV/JSON export |
| Schema Generator | **Type Library** / Generate XSD from XML |
| Digital Signatures | **Signature** activity (sign, validate, trust validation, certificate creation) |
| FOP / PDF | **FOP** activity (XSL-FO → PDF + preview) |

The **XSLT Developer** (advanced IDE with debugger, profiler and batch processing) and the
**XML Editor** (XML Ultimate, with IntelliSense) remain as dedicated sidebar tools for their
full feature sets.
