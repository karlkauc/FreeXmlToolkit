# Unified Editor

> **New in v1.7** - A multi-file editor that combines XML, XSD, XSLT, Schematron, and JSON editing in one view.

## Overview

The Unified Editor lets you work with multiple related files simultaneously in a single tabbed interface. Instead of switching between different editor pages, you can open XML files alongside their XSD schemas, XSLT stylesheets, and Schematron rules - all in one place.

### Key Features

- **Multi-tab editing** - Open multiple files of different types in one view
- **Automatic file type detection** - Files are recognized by extension (.xml, .xsd, .xsl, .sch, .json)
- **Linked file detection** - Automatically discovers related files (imported schemas, included stylesheets)
- **Unified toolbar** - Common operations (Save, Validate, Format) work across all file types
- **Context-sensitive tools** - Toolbar buttons change based on the active file type
- **Integrated XPath/XQuery** - Query panel works with any XML-based file
- **Search & Replace** - Unified search across all editor types; in XML files it works in both the Text and Graphic views (see note below)
- **Favorites** - Quick access to frequently used files

## Getting Started

1. Click **"Editor"** in the sidebar (or it opens automatically on startup)
2. Click **New** to create a new file, or **Open** to load existing files
3. Files open as tabs - switch between them by clicking the tab headers

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

## XSD Sub-Tabs

When editing an XSD file, the editor provides additional sub-tabs:

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

## Relationship to Other Editor Pages

The Unified Editor provides a consolidated editing experience. The dedicated editor pages (XML Editor, XSD Editor, etc.) remain available in the sidebar for their full feature sets, including advanced workflows like XSD documentation generation, Schematron batch testing, and XSLT batch processing.
