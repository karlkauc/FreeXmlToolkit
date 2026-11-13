# What's New

The highlights of each release. The complete notes and all downloads are on the
[GitHub Releases page](https://github.com/karlkauc/FreeXmlToolkit/releases); the version you are
running is shown under **Help → About FreeXmlToolkit**.

## 3.2.0 (2026-11-13)

**Editing**

- **"Edited with FreeXmlToolkit" comment:** saved XML, XSD, XSLT, Schematron and XProc files carry a comment after
  the XML declaration with the version and the user from Settings → User Info, as other XML editors do. It is
  updated on every save, not repeated, and can be switched off (Settings → Editor → Mark saved XML files). See
  [Settings](settings.md).
- **Filter from a value:** in the Graphic view, **Filter…** on a simple cell or leaf row opens with a matching
  expression already filled in — `Country` = `AT` in a `Position` row gives `Position[Country='AT']`; Enter applies
  it. See [Grid](grid.md).

**Querying**

- **XPath results as a table:** the Query Console's **Table** button now works for XPath too, not only for XQuery —
  `/order/item` shows one row per item with its child elements as columns. See
  [Query Console](query-console.md).

**Standards**

- **Older language versions (expert mode):** XPath 1.0 / 2.0 / 3.0, XSLT 1.0 / 2.0, XQuery 1.0 / 3.0, XSD 1.0 and
  XML 1.0 can be chosen, and the Schematron query binding of schemas without one. XPath 1.0 and XSLT 1.0 run on the
  JDK's real 1.0 processors; newer syntax, functions and instructions are reported as errors, so code written in
  FreeXmlToolkit runs on older processors too. Version pickers in the Query Console, XPath search, Transform and
  Validation panels, per project binding and scenario, `--lang-version` / `--xsd-version` / `--xml-version` /
  `--schematron-binding` on the command line and version arguments for the MCP tools. Off by default. See
  [Language Versions](language-versions.md).

**AI**

- **Refine AI results step by step:** Ask AI in the Query Console refines the query already in the console, Generate
  stylesheet can revise the Transform panel's stylesheet (and apply the result to its tab as one edit), and the AI
  panel has a follow-up field for every action — explain, fix, query and stylesheet — that continues the
  conversation and sends your own edits of an inserted result along. See
  [AI Assistant → Follow-ups](ai-assistant.md#follow-ups).

**Appearance**

- **oXygen and XMLSpy-like syntax colours:** two more schemes that recreate the text-view colours of those
  editors, in a light and a dark variant. See [Settings](settings.md#syntax-colours).

**Fixes**

- **Undo after repeated edits:** two text edits or renames in a row on the same node are one undo step again —
  Ctrl+Z did nothing for them before.
- **Query Console `doc()`:** an XQuery on a saved document resolves relative `doc()` and `unparsed-text()` next to
  the document.
- **Folding large elements:** folding a large element in the text view — e.g. `<Funds>` of a FundsXML file with
  24,000 lines — froze the application. It now takes well under a second, and so do Collapse all and Expand all;
  the caret jumps over a folded block. The fold toggles in the gutter are chevrons in the theme colours; a folded
  block shows as a highlighted chip. Elements whose start tag spans several lines (attributes on their own lines,
  such as the FundsXML root) can be folded too; tags inside comments and CDATA no longer count.
- **Update check behind corporate networks:** when GitHub's API refuses (rate limit on a shared address), the check
  reads the release page instead. The downloaded update is then verified against the release's `SHA256SUMS.txt`
  instead of being installed without a checksum check.
- **Clearer messages:** Open from URL names the HTTP status; Copy View as Image copies the visible area when the
  whole view is too large for the clipboard; file associations blocked by an administrator policy say so; an
  unreachable remote schema is not fetched again on every open for ten minutes; stopping the debugger is no
  longer treated as a failure.
- **Grid tables after sorting:** sorting a table column no longer collapses the table, and the selected row stays
  selected.

## 3.1.0 (2026-11-11)

**AI**

- **MCP server for AI agents:** `fxt-cli mcp` lets Claude Code and other MCP clients validate documents, run and
  test XSLT/XQuery (with XSpec), explore schemas, outline large documents and compare results — read-only and
  sandboxed, restricted to the folders you name. Eleven tools; administrators can switch it off with
  `ai.mcp.allowed=false`. See [MCP Server](mcp-server.md).
- **AI assistant** (off by default): fix or explain a validation problem, turn a question into XPath / XQuery, and
  generate a stylesheet from a target sample — with a model on your computer (Ollama, LM Studio) or a provider you
  choose (OpenAI-compatible, Anthropic). Every answer is checked by the application (compiled, run, validated) and
  repaired up to three times before you see it; nothing is applied without you. Data levels and a request preview
  control what leaves your computer. See [AI Assistant](ai-assistant.md).

**Appearance**

- **System theme and accent colour:** Settings → Appearance → Theme offers **System**, which follows the light or
  dark setting of your operating system while the application runs. The **accent colour** (eight presets or your
  own) colours buttons, selection, focus and links, with readable contrast kept automatically. See
  [Settings](settings.md#accent-colour).
- **Editor font:** font, size and line spacing of every code editor, and a zoom (Ctrl+mouse wheel) that can apply
  to all editors and be remembered. The grid uses the editor font too. See [Settings](settings.md#editor-font).
- **Text size, font and density of the interface:** Small to Extra Large text, your own interface font, and a
  Compact, Normal or Comfortable density. See [Settings](settings.md#text-size-and-font).
- **Syntax colours:** colour schemes for XML, JSON and XPath (Monokai and others, each with a light and a dark
  variant) and your own colour per role. See [Settings](settings.md#syntax-colours).
- **High contrast:** stronger texts, borders and focus rings on top of the light or the dark theme. See
  [Settings](settings.md#high-contrast).

**Editing and output**

- **Export views:** the XSD diagram, the XML / JSON grid and the profiler's flame graph can be saved as PNG, SVG or
  PDF, or copied to the clipboard as an image (File → Export View…, Edit → Copy View as Image, context menu). See
  [Exporting a Graphical View](unified-shell.md#export-view).
- **Switching views keeps your place:** going from Text to Tree, Graphic or Overview selects the node at the caret;
  going back to Text puts the caret on the node you had selected and tints its lines. See
  [View Modes](unified-shell.md#view-modes).
- **Transform from open tabs:** STYLESHEET → Change and INPUT → Change list every open XSLT / XML tab, saved
  or not, and a **Use current tab** button takes the tab you are editing — a generated stylesheet opened in a new
  tab runs straight away, with its editor text. See
  [Transform Panel](transform-panel.md#stylesheet).
- **External XSL-FO processors:** render PDFs with Antenna House Formatter, RenderX XEP, another Apache FOP or a
  script of your own; the XSLT stage, the intermediate FO and the debugger stay the same. Set them up under
  Settings → Extensions → XSL-FO Processors, choose one in the PDF/FOP panel, in a PDF scenario or with
  `fxt-cli fop --processor`. See [PDF Generator](pdf-generator.md#external-xsl-fo-processors).
- **One look for every Excel export:** validation problems, the Schema Analysis report, the Data Dictionary and the
  XML → Excel conversion share one house style — dark header, striped rows, no gridlines, an info sheet, frozen
  header and auto-filter, printed one page wide. See [XSD Validation](xsd-validation.md).

**Plugins and FundsXML**

- **A page per industry plugin:** ISO 20022, ebInterface, FinDatEx and XBRL each have their own guide; the
  [Industry Plugins](industry-plugins.md) page compares them.
- **Recognised documents are marked:** while the FundsXML extension is on, a FundsXML document shows a
  **FundsXML** mark in the status bar; a click opens the FundsXML panel. Plugins can add such marks too. See
  [FundsXML Extensions](fundsxml-extensions.md#recognised-documents-the-fundsxml-mark) and
  [Writing Plugins](plugin-development.md#document-badges).
- **FundsXML data-quality checks** compare position weights, net asset values and portfolio dates with the fund,
  subfund, segment or share class the portfolio belongs to, so share-class portfolios no longer add up to 300 %.
  Positions in country-specific data are no longer taken for portfolio positions, and assets used only by an
  earning no longer count as unused. See
  [FundsXML Extensions](fundsxml-extensions.md#data-quality-checks).

**Documentation**

- **Supported standards:** one table lists the XML-related standards and versions FreeXmlToolkit reads, validates,
  runs or writes, and the engine behind each. See [Supported Standards](index.md#supported-standards).

## 3.0.0 (2026-11-09)

**Platform and enterprise**

- **German user interface:** Settings → Appearance → Language (System default, English, Deutsch);
  all layouts and the shell's messages are translated. See [Settings](settings.md).
- **Command line `fxt-cli`:** validate (XSD, DTD, RELAX NG, Schematron, JSON Schema; text, JSON,
  JUnit and SARIF reports), transform (XSLT, XQuery, XProc, with profiling), xsd-doc, xspec,
  scenario, compare, merge, convert, fop. See [Command Line](command-line.md).
- **Administrator policies:** machine-wide `policy.properties` (locked) and `defaults.properties`,
  telemetry and updates can be switched off centrally, silent installation. See
  [Administration](administration.md).
- **Plugins:** an API for activities with side panels, menu items, validators, startup tasks,
  document listeners, stylesheets and schemas; jars in `~/.freeXmlToolkit/plugins`, managed in
  Settings → Extensions → PLUGINS. FundsXML is now a plugin. See [Plugins](plugins.md) and
  [Writing Plugins](plugin-development.md).
- **Industry plugins** (off by default): ISO 20022 payments / SEPA, ebInterface, FinDatEx EMT/EET/EPT/TPT (V7 and V8),
  XBRL with Arelle. See [Industry Plugins](industry-plugins.md).
- **Projects and transformation scenarios:** `.fxtproj` files bind a schema (XSD, DTD, RELAX NG,
  JSON Schema), a Schematron and a transformation scenario to files by pattern, add catalogs and
  store named scenarios (XSLT, XQuery, XProc, PDF through FOP).
  - **Project Settings** edits all of it without touching the JSON.
  - **Validate Project** checks every bound file.
  - Scenarios run for the chosen files (`${file}`, `${name}`), from the Explorer and with
    `fxt-cli scenario --input`.
  - Recent projects are on the menu and the Welcome page.
  - The project reloads when its file changes.

  See [Projects](projects.md).
- **Accessibility:** screen-reader names for every button and field, WCAG AA contrast in both
  themes, documented keyboard alternatives. See [Accessibility](accessibility.md).
- **Security:** no external entities in Schematron validation, HTML previews without JavaScript or
  remote content, XProc pipelines run no programs and reach no network, schemas are never fetched
  from internal addresses, Saxon's parser hardened, SBOM with every release. DTDs and RELAX NG
  schemas cannot send local files to a server (a parse never mixes local files and downloads), the
  output of project scenarios stays inside the project folder, YAML alias bombs are stopped, and
  Jackson, jsoup and AssertJ are past their known vulnerabilities. See [Threat Model](threat-model.md).

**XML work**

- **DTD and RELAX NG** (XML and compact syntax): validation against the declared or a bound
  grammar, with IntelliSense. See [Schema Support](schema-support.md#dtd-and-relax-ng).
- **Compare and merge:** XML compared by structure (namespaces, attribute order, whitespace,
  element order), folder comparison, three-way merge. 
- **Conversion:** XML ↔ JSON ↔ YAML (Tools menu, `fxt-cli convert`).
- **Large File Viewer:** files of any size (1 GB in seconds, a few MB of memory) with search,
  structure tree, record table and streaming validation. See [Large Files](large-files.md).
- **XSLT profiler:** hot spots, call tree, flame graph and line times (Transform panel → Profile run).
- **XSpec:** run XSpec tests for XSLT, XQuery and Schematron, with HTML and JUnit reports.
- **FundsXML:** 17 data-quality checks with a report and as Query Console snippets, analysis queries,
  and grid formulas and filters for funds, positions, master data and transactions. See
  [FundsXML Extensions](fundsxml-extensions.md#data-quality-checks).
- **Grid:** filters inside opened table cells, saved filters (off when opened), formulas first under
  their element, German number format; much faster with very large tables (680,000 records).

**Signatures and encryption**

- **XML Encryption 1.1:** the Signature page is now **Sign & Encrypt** with **Encrypt**, **Decrypt**
  and **Inspect**. Encrypt the whole document, the element at the caret or an XPath selection for
  any number of recipients (certificate, keystore entry, shared key file, password); decrypt with
  your private key, a shared key or the password. The XML editor's context menu offers **Encrypt
  Element… / Decrypt…**. See [XML Encryption](xml-encryption.md).
- **Signature algorithms:** RSA, RSA-PSS and ECDSA, a selectable digest and canonicalization;
  new certificates with RSA or EC keys. See [Digital Signatures](digital-signatures.md).
- **`fxt-cli sign`, `verify`, `encrypt`, `decrypt`** (also `--inspect`). See
  [Command Line](command-line.md#sign).

**More**

- **`fxt-cli query`** runs XPath, XQuery and JSONPath; the **XQuery profiler** shows where a query
  spends its time (Run ▾ → Profile Query, `fxt-cli transform --profile`).
- **Regression checks for scenarios:** compare a scenario's output with an expected result
  (Check, Differences, Accept; `fxt-cli scenario --check`). See [Projects](projects.md).
- **XML compare:** ignore XPaths, XMLUnit placeholders and a numeric tolerance.
- **XSpec templates** in File → New.
- **Problems, Query Console hits and FundsXML data-quality findings** open in the document's
  current view (Tree or Graphic) instead of switching to the Text view.
- **Grid:** a click elsewhere keeps the value you are editing (like Enter) and closes an open
  context menu; the XSD diagram's context menu closes the same way.
- **One look for every dialog**, light and dark; banner and dialog images in the Windows installers.

## 2.10.0 (2026-11-05)

- **XSD Overview view:** a new view mode for schemas lists all top-level
  components - imports, includes, annotations, elements, types, groups, attribute groups,
  attributes, notations - in one table. Insert, append, move (also by drag and drop), sort by
  type or name, rename with the references, edit annotations in place, add imports and includes.
  Tabs below edit the selected component's attributes (Default/Fixed offer the enumeration
  values), identity constraints, XSD 1.1 assertions and the schema's namespace prefixes. See
  [XSD Tools](xsd-tools.md#overview-view).
- **XSD saving keeps your order:** the top-level components are no longer re-sorted on every
  save; schema-level `xs:annotation`, `xs:notation` and `xs:defaultOpenContent` are no longer
  lost when the schema is edited.
- **Named types in frames (Graphic view):** the content an element gets from a
  named complex type is drawn in a light-yellow, dashed frame titled with the type's name; the
  base of an `xs:extension` gets a nested frame. The content is read-only there - double-click
  the frame title or choose **Edit Type '…'** to open the type in its Type Editor. A type used
  inside a Type Editor now opens in a further Type Editor tab, so nested types can be followed
  level by level. See [XSD Tools](xsd-tools.md#named-types-in-frames).
- **Constraints on their target nodes (optional, off by default):** a key, unique or keyref can
  also show on the node its field points to (e.g. `BenchmarkID` lists the key `Fund` declares).
  In the Graphic view, switch it in the new **DISPLAY** section at the top of the Properties pane
  or under **View → Show Constraints on Target Nodes**. In the XSD documentation, use **Show
  constraints on their target elements**: it adds a row to the diagram box and a **Referenced by
  Identity Constraints** section to the element's page. See
  [XSD Tools](xsd-tools.md#reading-a-card).
- **Flatten Schema:** the dialog asks for the output file, preset to `<name>_flattened.xsd` next to
  the original schema; the result is written there and opened. See
  [XSD Tools](xsd-tools.md#8-xsd-flattener).
- **Downloaded imports are saved next to your schema:** a missing `xs:import` / `xs:include`
  (e.g. `xmldsig-core-schema.xsd` of FundsXML4) is saved under its original name in the folder
  of the schema that references it and loaded from there - also in validation, Schema Analysis,
  flattening and documentation - instead of an md5-named cache file. Existing files are never
  overwritten. See [XSD Tools](xsd-tools.md#automatic-resolution-of-imported-schemas).
- **XML → Excel:** the workbook now has the same look as the Data Dictionary export. With a
  bound XSD, numbers, booleans and dates go into number, boolean and date cells instead of text.
  Importing the workbook gives back the original values. The CSV export adds a **Data Type**
  column and quotes only text. **Excel → XML** and **CSV → XML** also read the values with the XSD
  (from the new **Schema (XSD)** field, or the one the file declares). Dates, booleans and
  numbers that were entered by hand or reformatted by a spreadsheet application (`31.01.2024`,
  `1.234,50`, `TRUE`) come out valid. Values that contain the delimiter, quotes or line breaks now
  import back correctly. See [XML Editor](xml-editor.md#xmlexcelcsv-converter).
- **Favorites from everywhere:** a **star in the header bar** lists every favorite on every
  activity and page (also Settings, Schema Library and Signature); the Explorer has a star and
  **FAVORITES | RECENT** tabs at the bottom; and the menu bar has **File → Open Favorite**. See
  [Favorites](favorites-system.md#open-favorites-from-anywhere).
- **Explorer TOOLS:** besides **Open file…** and **Clear recent**, the list offers **Open from
  URL…**, **Compare files…**, **Spreadsheet Converter…** and **Generate XSD from XML files…**.
  Compare and Generate XSD use the files selected in the workspace tree (two selected files are
  compared with each other); the tree's context menu adds **Compare Selected Files** and
  **Generate XSD from Selected**. See [Unified Shell](unified-shell.md#explorer-panel).
- **Query Console:** a close button (**✕**) at the top right, and the **XPath | XQuery** mode is
  a clear switch; the active mode is also named on the **Run** button (*Run XPath* / *Run
  XQuery*) and in the hint below the input. See [Query Console](query-console.md#query-console).
- **Side panels** are collapsed and re-opened only with the editor toolbar's toggle buttons
  (or Ctrl+L / Ctrl+Shift+P), and collapsing one no longer changes the width of the other. See
  [Unified Shell](unified-shell.md#collapsing-the-side-panels).
- **Fixes:** the grid's scroll bars can be dragged when the content is less than twice the
  height of the view; in the XSD documentation, nested compositor symbols are centred over
  their children.

## 2.9.0 (2026-11-04)

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

- **Editable grid** for XML: block layout with docked tables and inline previews, table cell
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
