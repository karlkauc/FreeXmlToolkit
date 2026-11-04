# What's New

The highlights of each release. The complete notes and all downloads are on the
[GitHub Releases page](https://github.com/karlkauc/FreeXmlToolkit/releases); the version you are
running is shown under **Help → About FreeXmlToolkit**.

## 2.9.0 (upcoming)

- **XML → Excel:** the workbook now has the same look as the Data Dictionary export. With a
  bound XSD, numbers, booleans and dates go into number, boolean and date cells instead of text.
  Importing the workbook gives back the original values. The CSV export adds a **Data Type**
  column and quotes only text. **Excel → XML** and **CSV → XML** also read the values with the XSD
  (from the new **Schema (XSD)** field, or the one the file declares). Dates, booleans and
  numbers that were entered by hand or reformatted by a spreadsheet application (`31.01.2024`,
  `1.234,50`, `TRUE`) come out valid. Values that contain the delimiter, quotes or line breaks now
  import back correctly. See [XML Editor](xml-editor.md#xmlexcelcsv-converter).
- **XSD Graphic view, new card layout:** every node is one card with a symbol, name and type
  line; cardinality and attribute use are pills, optional cards are dashed and repeating ones have
  a second outline. A **title chip** (top left) names the selected card, a **zoom control** sits
  at the bottom right, and the light theme has more contrast. Element and attribute cards list
  their **constraints and assertions** (**View → Show Constraints in Diagram**). The ComplexType
  editor tab shows the same cards. See [XSD Tools](xsd-tools.md#reading-a-card).
- **XSD Graphic view:** cards open in large schemas too; inherited `xs:extension` content, group
  and attribute group references, wildcards, inherited attributes and placeholder cards are shown;
  a bar names includes and imports that could not be loaded; relative references inside schemas
  loaded over HTTP now resolve; `xs:redefine` and `xs:override` are resolved, and every file of a
  namespace split over several `xs:import`s is loaded; changes to included and imported files on
  disk are noticed, and open cards stay open when the schema is rebuilt. See
  [XSD Tools](xsd-tools.md#what-the-diagram-shows).
- **Schema files on disk:** a changed date alone no longer asks to reload, and an XML document
  follows the schema bound to it even without a tab for that schema. The check pauses on a slow
  drive.
- **`xs:redefine` and `xs:override`** also work inside included files, and they now count in the
  Type Library, statistics, quality checks and the Inspector of an XML document.
- **Facets:** a restriction of a named type offers the facets of what that type comes down to,
  and restrictions of list and union types offer only the facets XML Schema allows there.
- **XSD documentation (PDF and Word):** an **Element Reference** with a section per element and
  attribute (diagram, properties, documentation, restrictions, children, constraints) and a
  section per complex and simple type, all linked from the data dictionary, the index and the
  tables, with bookmarks; a namespace overview that names each namespace, the option **XSD
  source in each section**; in the PDF vector (SVG) diagrams and
  an overview diagram of the whole schema; in Word real headings, so the table of contents and
  the navigation pane work. Large schemas no longer run out of memory.
  Fixed: wrong root in the schema diagram, HTML in restrictions, cut-off text, usage counts.
  See [XSD Tools](xsd-tools.md#generation-options).
- **XSD documentation diagrams:** element boxes list constraints and assertions (option **Show
  constraints in diagrams**, with **Also in the page's own element box**); constraint rows link
  their selector, fields and key; the choice symbol is drawn as a switch. **Show documentation in
  diagrams** is now off by default.
- **Go to Definition** from an XML document (Graphic, Tree and Text view, Ctrl+Click in text)
  opens the bound XSD in the Graphic view, expanded along the element's path with its card
  selected. See [XML Editor](xml-editor.md#go-to-definition).
- **Grid:** the header adapts to a narrow column (icon-only buttons, then the tools move into
  the **⋯** menu); the scroll range follows tables whose cells are opened.
- **Side panels** list their tools first; the **Tools** menu has **Generate XSD from XML** and
  **Generate XSD from XML Files…** (batch, always available).
- **Fixes:** schemas whose `DOCTYPE` names a missing DTD (such as `XMLSchema.xsd`) load
  everywhere; absolute schema locations in `xsi:schemaLocation` bind; right-clicking in the
  editor no longer stalls on a large clipboard; user errors are no longer reported as app errors.

## 2.8.0 (2026-11-01)

- **Grid:** opened table cells are full grids at any depth (select, edit, navigate, search,
  resize and drag & drop inside them); select whole table rows/columns and open nested cells
  with **+** / **−**; the **entry helpers** panel (Siblings / Children / Values) beside the XML
  grid. JSON grid: change a value's type via its type icon, drag & drop, Table submenu. See
  [Grid View](grid.md).
- **Network:** complete proxy settings (system / manual / none, exceptions, authentication incl.
  NTLM/Kerberos, timeouts, Test Connection); every HTTP connection uses them. See
  [Settings](settings.md).
- **Settings:** old log files are deleted automatically (**LOG FILES** card); the clear buttons
  show the space in use.
- **XSD documentation:** type definitions, SVG overview and metadata are on by default; the
  Data Dictionary Excel export has the look of the HTML documentation.

## 2.7.0 (2026-10-29)

- **XMLSpy-style grid** for XML: block layout with docked tables and inline previews, table cell
  ranges and TSV copy/paste with spreadsheets, flip rows/columns, sortable and movable columns,
  multi-selection, split view, sticky scroll header, sibling groups for huge containers,
  validation markers, XQuery **filters** (Filter to Focus) and **formulas**, drag & drop,
  **Edit as Raw Text** (Ctrl+Alt+R), Wrap in Element, configurable cell colours. See
  [Grid View](grid.md).
- **XSD editor:** only structural edits that keep the schema valid are allowed; create global
  complex and simple types; set type, cardinality and more when adding a node; drag & drop in
  the Graphic view; a **type picker** with explanations. See [XSD Tools](xsd-tools.md).
- **IntelliSense:** attribute and value completion for XSLT and XSD-bound XML. See
  [Auto-Completion](context-sensitive-intellisense.md).
- **Editor tabs** can be reordered by drag and have a context menu (Reload, Close Others, Copy
  Path, Reveal…); problem rows can be copied.

## 2.6.0 (2026-10-25)

- **Interactive debugger** for XSLT, **XQuery** and **Schematron** (at the `.sch` lines):
  DEBUG dock below the editor, conditional breakpoints, hit counts, logpoints, template /
  function / mode and input-node breakpoints, break on errors and `xsl:message`, Pause (F6),
  line stepping, Smart Step Into (Shift+F7), Run to Cursor (Ctrl+F10), debug configurations and
  Re-run Last Debug, Evaluate (Alt+F8), structured variables, watches, inline and hover values,
  changed-value markers, Explain Match, coverage, output so far and output-to-source mapping.
  See [Debugger](debugger.md).
- **Memory:** the heap limit follows the installed RAM and is adjustable in Settings; large
  files are guarded. See [Settings → Memory](settings.md#memory).
- **Files:** every opened file goes into Recent (configurable limit); files picked, dropped or
  chosen from favorites open in the editor; "Open with" works on all platforms; quitting asks
  about unsaved documents.

## 2.5.1 (2026-10-20)

- **Streamlined workflows:** Settings, Schema Library and Signature open as pages in the main
  area; Help is a menu (header **?** and F1); the Welcome page has a task list and
  **Open an Example**.
- **Properties inspector redesign**, and editing the **bound XSD from an XML document** (type,
  cardinality, facets, documentation) with **Open in schema**. See
  [Properties Inspector](inspector.md).
- Large documents and schemas stay responsive: typing, highlighting, folding, view switches,
  saving and formatting no longer block the window.

## 2.5.0 (2026-10-15)

- Installation via package managers: updates of Scoop / Homebrew / winget / Flatpak installs
  show the matching upgrade command. See [Installation](installation.md).
- `SHA256SUMS.txt` with every release; Linux desktop entry, AppStream metadata and icons.
- [Code signing policy](code-signing-policy.md).

## 2.4.0 (2026-10-14)

- **macOS:** packages signed and notarized by Apple; native system menu bar.
- **Menu bar** (File, Edit, View, Tools, Help) with every action by name; Ctrl/Cmd+W closes the
  tab, Ctrl/Cmd+, opens the settings.
- **Files changed outside the app** are detected (also on network drives) and can be reloaded.

## 2.3.0 / 2.3.1 (2026-09-28 / 2026-10-08)

- **Schema Analysis** tool with types, statistics, quality checks and report export (CSV, JSON,
  HTML, PDF, XLSX). See [XSD Tools](xsd-tools.md).
- **Workflow colours** for every activity; labelled action rows in all side panels.
- Grid: wrapped values, measured column widths, zoom, resizable columns.
- Settings are stored in `~/.freeXmlToolkit/FreeXmlToolkit.properties`.
- 2.3.1: no more UI freezes on large diffs, minified files and web-imported schemas; log files
  roll over automatically.

## 2.2.0 (2026-09-24)

- **Anonymous usage statistics and error reports** (opt-out). See
  [Privacy & Telemetry](telemetry.md).
- **JSON grid view**; `.jsonc` / `.json5` open as JSON. See [JSON Editor](json-editor.md).
- A much more reliable sample XML generator and the Schema Analysis tool tab.

## Where the former tabs went

The Unified Shell consolidates what used to be separate sidebar tabs. The earlier
standalone editors - XSD Editor, XSD Validation, JSON Editor, XSLT Viewer, Schematron,
Schema Generator, Digital Signatures and FOP/PDF - have been **retired** and their
functionality now lives in the shell:

| Former tab | Now in the shell |
|------------|------------------|
| XSD Editor / Tools | Open an `.xsd`: Text/Tree/Graphic views + inspector; **Schema** activity (Type Library panel) for type editing, documentation, flatten and schema analysis |
| XSD Validation | **Validation** activity (single + batch, XSD & Schematron) |
| JSON Editor | Open a `.json` / `.jsonc` / `.json5`: Text / Tree / Graphic (grid) views + inspector; JSONPath via the Query Console; JSON Schema validation in the **Validation** activity |
| XSLT Viewer | **Transform** panel (set stylesheet, transform, preview, browser) |
| Schematron | **Validation** activity: check rules, templates, tester, builder, documentation, CSV/JSON export |
| Schema Generator | Editor toolbar **Schema ▾** → Generate XSD from XML |
| Digital Signatures | **Signature** activity (sign, validate, trust validation, certificate creation) |
| FOP / PDF | **FOP** activity (XSL-FO → PDF + preview) |
| XSLT Developer | **Transform** panel + editor: run/live transform, parameters, and the OPTIONS & TOOLS section (batch, profile, trace, debugger) — see [XSLT Developer](xslt-developer.md) |
| XML Editor | The shell's editor itself: every XML document opens with IntelliSense, Text/Tree/Graphic views and the Properties inspector |
