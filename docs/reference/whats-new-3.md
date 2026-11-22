# What's new: 3.0 to 3.3

Release highlights of versions 3.0 to 3.3. The current release is on [What's new](whats-new.md).

## 3.3.0 (2026-11-16)

**Online identifier lookup (GLEIF, ESMA FIRDS, OpenFIGI)**

- **Is the LEI registered, does the ISIN exist?** The identifier checker of the industry, FundsXML and PK-Meldewesen
  panels looks an LEI up at GLEIF (name, address, registration and entity status, next renewal) and an ISIN at ESMA
  FIRDS (name, CFI code, issuer LEI, EU trading) or, when FIRDS does not know it, at OpenFIGI. **Check Identifiers
  Online** (panels, Tools menu) checks every LEI and ISIN of a document and reports unknown, lapsed, retired or annulled
  LEIs, unknown or no longer traded ISINs and reported CFI codes and issuer LEIs that differ from ESMA FIRDS in the Problems panel. Optionally with every validation and as a hover in
  the editor (Settings → Network & Security, off by default); on the command line `fxt-cli lei`, `fxt-cli isin` and
  `fxt-cli validate --gleif --isin`. See [Online Identifier Lookup](../industry/identifier-lookup.md).

**External XSLT processors**

- **Run stylesheets on another XSLT processor** - Saxon PE/EE, xsltproc, Altova RaptorXML or a script: set them up in
  *Settings → XSLT Processors* (presets, parameter syntax, a Test run) and choose one in the Transform panel, in a
  scenario (`xsltProcessor`), for the XSLT stage of the PDF/FOP panel, in batch runs and on the command line
  (`fxt-cli transform --processor`, `fxt-cli fop --xslt-processor`). Unsaved editor text is handed over next to the
  file, so includes resolve; errors show their line as with Saxon HE. Debugging, profiling, XSpec and the live
  preview stay on Saxon HE. See [External XSLT Processors](../guides/transform-query/external-processors.md).

**YAML**

- **YAML editor:** `.yaml` / `.yml` files get syntax colours, folding by indentation and a syntax check while you
  type, with the error at its line and column.
- **JSON Schema for YAML:** validation and completion from the schema a YAML file declares
  (`# yaml-language-server: $schema=…` or `$schema:`), a project binding or **Set Schema…**; problems are underlined
  on their key. `fxt-cli validate` checks YAML files too.
- **Format** re-indents YAML (and leaves documents with comments or anchors alone), **JSONPath** queries run on
  YAML, **Generate Sample** writes YAML. Application tags such as CloudFormation's `!Ref` no longer stop a
  conversion. See [YAML](../guides/json-yaml.md#yaml).
- **Tree and Grid for YAML:** YAML documents get the JSON Tree and Grid views. Edits are written into the YAML text
  at their place, so comments, anchors, tags and formatting stay; a change that cannot be placed asks before the
  document is written again. See [Tree and Grid for YAML](../guides/json-yaml.md#tree-and-grid-for-yaml).
- **Schemas by file name:** a Schema Library mapping can name the JSON and YAML files it is for (`docker-compose*.yml`,
  `.github/workflows/*.yml`), and the optional [SchemaStore catalog](../guides/design-schemas/schema-library.md#4-schemastore) finds the schema of
  hundreds of known file types by their name - for files that declare no schema themselves.
- **Command line and MCP:** `fxt-cli validate` also uses the `$schema` of JSON files and the file-name patterns,
  `--schemastore` adds the SchemaStore catalog (`fxt-cli validate --schemastore .github/workflows`), and folders
  include YAML by default. `fxt-cli query` runs JSONPath on YAML files. The MCP tool `validate` checks JSON and YAML
  (new arguments `format`, `json_schema_path`). See [Command Line](../automate/cli-validate.md#validate).
- **Compare JSON and YAML by their data:** the Compare view's new **Data** mode and `fxt-cli compare` ignore member
  order, formatting, comments and anchors, compare a JSON file against a YAML file, match array items by key members
  and report JSONPath locations. **Compare Folders** and `fxt-cli compare` on two folders also compare JSON and YAML
  files by their data. See [YAML](../guides/json-yaml.md#yaml).
- **New YAML files** start with `---`; templates for a schema-bound YAML document, a Docker Compose file and a GitHub
  Actions workflow.

**Large files**

- **Edit large files:** the Large File Viewer's new **Graphic** tab shows a part of the file — an element, or a page
  of up to 1,000 children of a huge element — in the grid, where it can be edited with undo/redo. **Apply to File**
  writes only that part back into the file in one streaming pass (the rest stays byte for byte as it was, the
  original is untouched if writing fails; Settings can keep the previous version as `.bak`). See [Large File Viewer](../guides/edit-xml/large-files.md#graphic-view-and-edit-a-part-of-the-file-xml).
- **Jump anywhere:** **Go to** an XPath such as `/orders/order[1500000]` (Ctrl+L) or a child number, **Previous
  page…** in the tree, and **Show in Structure** from a text row or a search match.
- **Statistics:** one pass over the file lists every element path with counts, occurrences per parent, attributes
  and a sample value.
- **Export all records:** all children of an element — not only the loaded 1,000 — to CSV or Excel.
- The viewer notices when the file changes on disk and offers to reload it; character references such as `&#233;`
  are decoded in the tree and table.

**ESMA Reporting**

- **EMIR Refit trade reports** (auth.030): a new extension binds them to ESMA's schema (also in the BizData
  envelope) and checks ESMA's table of mandatory and not applicable fields per action type and level, the record
  count, UTI, LEI and ISIN; every element shows its EMIR field number. See [ESMA Reporting](../industry/esma-reporting.md).
- **SFTR reports** (auth.052) are checked the same way, per action type, level and type of SFT.
- **Conditional EMIR and SFTR fields:** ESMA's rules for the fields marked *C* - reported only when other fields
  say so, or never then - are checked too (174 rules for EMIR, 104 for SFTR; SFTR collateral per component).
- **Dates in EMIR and SFTR reports** are checked in the order ESMA requires - execution, valuation, confirmation,
  clearing, event, expiration, maturity and termination against each other and against the reporting timestamp.
- **MiFIR transaction reports** (auth.016) are checked with ESMA's content rules: venue and trading capacity, LEIs,
  ISINs, CONCAT codes, currencies and the instrument fields the CFI code requires or forbids.

**KAG-Meldewesen (FMA)**

- **AIFMD reporting:** a new extension for Austrian management companies binds the AIFM and AIF files to ESMA's
  schema v1.2 and checks them with the FMA's rules (reporting period, content blocks, conditional fields, percentages,
  rankings, LEI/ISIN) - each finding on its line, each element with its ESMA field number, guidance and FMA rule in the
  Inspector. See [KAG-Meldewesen](../industry/kag-meldewesen.md).
- **Money market fund reports** (ESMA auth.093) are validated in their BizData envelope and checked with ESMA's
  application rules (periods, share classes, liquidity profile, stress tests, assets, codes).
- **Industry extension panels:** **Check Active Document** validates like **F8** - the problems go to the PROBLEMS
  panel with a click to the line and are underlined in the text; the panel shows the counts. A switched-off
  extension that recognises an opened document is offered once in a bar above the editor, and the field labels of
  ESMA and KAG reports appear when the mouse rests on a tag name. See [Industry Extensions](../industry/index.md).
- **KAG-Meldewesen rules:** money market fund reports are checked with the content rules of ESMA's validation
  rules v07 (periods, LEIs, master/feeder, yields, stress tests, CFI code per asset type, credit assessments,
  investor shares, arrangements, monthly figures, LVNAV data) - 211 of the table's 254 rules, the rest need ESMA's
  registers. AIF files get the FMA rules on individual exposures, currency exposures and private equity fields.
  See [KAG-Meldewesen](../industry/kag-meldewesen.md#money-market-fund-report).
- **KAG-Meldewesen:** **Check Report (HTML)** with the findings by rule and, for DeRiMV, a table of every fund's
  use of its limits; **Import DeRiMV from CSV/Excel** (the risk management's CSV layout) and **Export to
  Excel/CSV**; grid formulas for DeRiMV reports and AIF files; options for the AIFMD sum tolerance and the DeRiMV
  limits; templates, analysis queries and schema favorites. See [KAG-Meldewesen](../industry/kag-meldewesen.md).
- **Derivative risk report (4. DeRiMV):** the UCITS report `Derivatemeldung` is bound to the FMA's schema and checked
  against the limits of the global exposure (commitment 100 %, absolute VaR 20 %, relative VaR 200 %), the fund's own
  limit, the period maximum and ISIN check digits. **New Derivative Risk Report (Example)** opens a fictitious report.

**PK-Meldewesen (FMA)**

- **New extension for Austrian pension fund reports:** the quarterly report (PK-QMV), the annual report (PK-FJMV)
  and the ECB report are recognised by their root element and bound to the shipped FMA taxonomy 2.9.0 Hotfix2 - also
  when the schema location in the file points to an unreachable network path. A status bar badge
  (**PK-QMV Q1/2026**) and a side panel show the report, its schema and the FMA documentation. See
  [PK-Meldewesen](../industry/pk-meldewesen.md).
- **FMA rules:** the 180 rules of the FMA validation workbook (plus optional additional plausibility checks) run
  on quarterly reports with **F8** and in `fxt-cli validate`; every finding names its FMA code, the VRG and the
  object and jumps to its line. **Rule Report (HTML)** shows a dashboard per VRG, all results and the rule catalogue;
  tolerances and look-through limits are set in the side panel.
- **FMA labels:** row, column and template codes show their FMA name, explanation and code-list values in the
  Inspector, as the grid tooltip and when hovering a code in the text. **Grid formulas and filters** for the quarterly
  report can be added to a copy and removed again before submission.
- **Prepare Submission:** cleans the report (no grid formulas, UTF-8), checks it against the schema and the FMA
  rules, proposes the next running number and writes the ZIP under the FMA's file name
  (`34.pfq.20190331.00001.test.zip`).
- **Newer taxonomies** are imported from the FMA's ZIP and chosen in the side panel (no restart); an example report,
  the schema documentation, six Query Console snippets, new-file templates and favorites come with the extension.

**Editing**

- **Spreadsheet Converter for everyone:** XML becomes a **table** – one row per record (every `<Order>`), one
  column per value – instead of only the XPath list. Edit it in Excel and convert it back; any other CSV file or
  sheet becomes XML too, one element per row. The dialog explains each choice, has tooltips and a **?** to the
  documentation, and previews the table or the XML before anything is written. With a CSV document open it
  starts with Spreadsheet → XML; a malformed document's message has **Go to Error**. See
  [XML Editor](../guides/convert.md#xmlexcelcsv-converter).
- **Validation results stay with their document:** after Validate / F8 or a run of the Validation panel,
  switching to another tab and back shows the result and the problems again (until the text changes). A run that
  finishes after you switched tabs goes to the document it checked.
- **Show problems:** the Validation panel's "N problems — show below the editor" now also shows the first problem in
  the editor, on every click.
- **Save confirmation:** after a save the status bar shows **Saved orders.xml (12:55:18)** for a few seconds, also
  when the file had no changes. See [Status Bar](workspace.md#status-bar).

**Fixes**

- **First start after installing on Windows:** the virus scanner checked every program file when the application
  first opened it, so the first start took 13 seconds or more. The installer now opens every file once as its last
  step, so this check happens during the installation.
- **Very large documents in the text editor:** resting the mouse on a document over 2 MB no longer freezes the
  application (the hover context is skipped there), and files from 256 KB are prepared off the UI thread when opened.

**Opening files**

- **XML in files with other extensions:** a file whose extension the app does not know (`web.config`, `.xaml`, `.gpx`,
  `.svg`, a file without extension, …) is opened by its content - as XML, HTML or JSON with all their features
  instead of plain text.

**JSON**

- **Format detection fixed:** a plain JSON file with a URL or a time such as `10:30` in a string was taken for
  JSON5 (and warned about lost comments in the grid); the flavor is now read from the tokens, not guessed by
  patterns. JSON5 strings with apostrophes, hex numbers and `.5` / `5.` numbers are read correctly.
- **Format** uses the JSON indentation from Settings; **Minify** keeps `null` members.
- Parse errors carry the column; JSONC and JSON5 files are validated against their JSON Schema (the
  violations point at the original lines).
- **Schema help while typing:** with a JSON Schema bound, Ctrl+Space (or typing `"` / `:`) proposes the missing
  properties - required ones first - and the allowed values; hovering shows the schema's description, type and
  limits, and the inspector has a **JSON Schema** section for the selected value. `$schema` is also found while the
  document is incomplete.
- **Problems in place:** validation problems are underlined in the text (with a gutter badge and the message on
  hover) and marked in the grid.
- **Format keeps comments:** formatting changes only whitespace - comments, quote style, JSON5 numbers and trailing
  commas stay.
- **Sort Keys** (top level or recursive) in the text editor's and the grid's context menu; comments move with their
  members.
- **Folding** of objects, arrays and block comments in the text editor; the context menu's Format / Minify /
  Validate entries work again.
- Ctrl+Z right after opening a file no longer empties the editor.
- **Quick fixes for schema violations:** add a missing required property, replace a value with an allowed one,
  remove a property the schema does not allow - one undo step, formatting kept.
- **Generate a sample JSON** from a JSON Schema (the open schema or the bound one), required properties only or
  all.
- **Grid:** JSONPath **filters**, validation markers, the **entry helpers** panel with the schema's properties and
  values, and pasting tab-separated cells into a table as one undo step.

## 3.2.0 (2026-11-13)

**Editing**

- **"Edited with FreeXmlToolkit" comment:** saved XML, XSD, XSLT, Schematron and XProc files carry a comment after
  the XML declaration with the version and the user from Settings → User Info, as other XML editors do. It is
  updated on every save, not repeated, and can be switched off (Settings → Editor → Mark saved XML files). See
  [Settings](settings.md).
- **Filter from a value:** in the Graphic view, **Filter…** on a simple cell or leaf row opens with a matching
  expression already filled in — `Country` = `AT` in a `Position` row gives `Position[Country='AT']`; Enter applies
  it. See [Grid](../guides/edit-xml/grid.md).

**Querying**

- **XPath results as a table:** the Query Console's **Table** button now works for XPath too, not only for XQuery —
  `/order/item` shows one row per item with its child elements as columns. See
  [Query Console](../guides/transform-query/query-console.md).

**Standards**

- **Older language versions (expert mode):** XPath 1.0 / 2.0 / 3.0, XSLT 1.0 / 2.0, XQuery 1.0 / 3.0, XSD 1.0 and
  XML 1.0 can be chosen, and the Schematron query binding of schemas without one. XPath 1.0 and XSLT 1.0 run on the
  JDK's real 1.0 processors; newer syntax, functions and instructions are reported as errors, so code written in
  FreeXmlToolkit runs on older processors too. Version pickers in the Query Console, XPath search, Transform and
  Validation panels, per project binding and scenario, `--lang-version` / `--xsd-version` / `--xml-version` /
  `--schematron-binding` on the command line and version arguments for the MCP tools. Off by default. See
  [Language Versions](standards-versions.md).

**AI**

- **Refine AI results step by step:** Ask AI in the Query Console refines the query already in the console, Generate
  stylesheet can revise the Transform panel's stylesheet (and apply the result to its tab as one edit), and the AI
  panel has a follow-up field for every action — explain, fix, query and stylesheet — that continues the
  conversation and sends your own edits of an inserted result along. See
  [AI Assistant → Follow-ups](../guides/ai-assistant.md#follow-ups).

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
  `ai.mcp.allowed=false`. See [MCP Server](../automate/mcp-server.md).
- **AI assistant** (off by default): fix or explain a validation problem, turn a question into XPath / XQuery, and
  generate a stylesheet from a target sample — with a model on your computer (Ollama, LM Studio) or a provider you
  choose (OpenAI-compatible, Anthropic). Every answer is checked by the application (compiled, run, validated) and
  repaired up to three times before you see it; nothing is applied without you. Data levels and a request preview
  control what leaves your computer. See [AI Assistant](../guides/ai-assistant.md).

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
  [Exporting a Graphical View](../get-started/workspace-tour.md#export-view).
- **Switching views keeps your place:** going from Text to Tree, Graphic or Overview selects the node at the caret;
  going back to Text puts the caret on the node you had selected and tints its lines. See
  [View Modes](../get-started/workspace-tour.md#view-modes).
- **Transform from open tabs:** STYLESHEET → Change and INPUT → Change list every open XSLT / XML tab, saved
  or not, and a **Use current tab** button takes the tab you are editing — a generated stylesheet opened in a new
  tab runs straight away, with its editor text. See
  [Transform Panel](transform-panel.md#stylesheet).
- **External XSL-FO processors:** render PDFs with Antenna House Formatter, RenderX XEP, another Apache FOP or a
  script of your own; the XSLT stage, the intermediate FO and the debugger stay the same. Set them up under
  Settings → Extensions → XSL-FO Processors, choose one in the PDF/FOP panel, in a PDF scenario or with
  `fxt-cli fop --processor`. See [PDF Generator](../guides/publish-pdf.md#external-xsl-fo-processors).
- **One look for every Excel export:** validation problems, the Schema Analysis report, the Data Dictionary and the
  XML → Excel conversion share one house style — dark header, striped rows, no gridlines, an info sheet, frozen
  header and auto-filter, printed one page wide. See [XSD Validation](../guides/validate/xsd.md).

**Plugins and FundsXML**

- **A page per industry plugin:** ISO 20022, ebInterface, FinDatEx and XBRL each have their own guide; the
  [Industry Plugins](../industry/index.md) page compares them.
- **Recognised documents are marked:** while the FundsXML extension is on, a FundsXML document shows a
  **FundsXML** mark in the status bar; a click opens the FundsXML panel. Plugins can add such marks too. See
  [FundsXML Extensions](../industry/fundsxml.md#recognised-documents-the-fundsxml-mark) and
  [Writing Plugins](../extend/writing-plugins.md#document-badges).
- **FundsXML data-quality checks** compare position weights, net asset values and portfolio dates with the fund,
  subfund, segment or share class the portfolio belongs to, so share-class portfolios no longer add up to 300 %.
  Positions in country-specific data are no longer taken for portfolio positions, and assets used only by an
  earning no longer count as unused. See
  [FundsXML Extensions](../industry/fundsxml.md#data-quality-checks).

**Documentation**

- **Supported standards:** one table lists the XML-related standards and versions FreeXmlToolkit reads, validates,
  runs or writes, and the engine behind each. See [Supported Standards](../get-started/features.md#supported-standards).

## 3.0.0 (2026-11-09)

**Platform and enterprise**

- **German user interface:** Settings → Appearance → Language (System default, English, Deutsch);
  all layouts and the shell's messages are translated. See [Settings](settings.md).
- **Command line `fxt-cli`:** validate (XSD, DTD, RELAX NG, Schematron, JSON Schema; text, JSON,
  JUnit and SARIF reports), transform (XSLT, XQuery, XProc, with profiling), xsd-doc, xspec,
  scenario, compare, merge, convert, fop. See [Command Line](../automate/command-line.md).
- **Administrator policies:** machine-wide `policy.properties` (locked) and `defaults.properties`,
  telemetry and updates can be switched off centrally, silent installation. See
  [Administration](../administer/policies.md).
- **Plugins:** an API for activities with side panels, menu items, validators, startup tasks,
  document listeners, stylesheets and schemas; jars in `~/.freeXmlToolkit/plugins`, managed in
  Settings → Extensions → PLUGINS. FundsXML is now a plugin. See [Plugins](../industry/manage-extensions.md) and
  [Writing Plugins](../extend/writing-plugins.md).
- **Industry plugins** (off by default): ISO 20022 payments / SEPA, ebInterface, FinDatEx EMT/EET/EPT/TPT (V7 and V8),
  XBRL with Arelle. See [Industry Plugins](../industry/index.md).
- **Projects and transformation scenarios:** `.fxtproj` files bind a schema (XSD, DTD, RELAX NG,
  JSON Schema), a Schematron and a transformation scenario to files by pattern, add catalogs and
  store named scenarios (XSLT, XQuery, XProc, PDF through FOP).
  - **Project Settings** edits all of it without touching the JSON.
  - **Validate Project** checks every bound file.
  - Scenarios run for the chosen files (`${file}`, `${name}`), from the Explorer and with
    `fxt-cli scenario --input`.
  - Recent projects are on the menu and the Welcome page.
  - The project reloads when its file changes.

  See [Projects](../guides/projects.md).
- **Accessibility:** screen-reader names for every button and field, WCAG AA contrast in both
  themes, documented keyboard alternatives. See [Accessibility](accessibility.md).
- **Security:** no external entities in Schematron validation, HTML previews without JavaScript or
  remote content, XProc pipelines run no programs and reach no network, schemas are never fetched
  from internal addresses, Saxon's parser hardened, SBOM with every release. DTDs and RELAX NG
  schemas cannot send local files to a server (a parse never mixes local files and downloads), the
  output of project scenarios stays inside the project folder, YAML alias bombs are stopped, and
  Jackson, jsoup and AssertJ are past their known vulnerabilities. See [Threat Model](../administer/threat-model.md).

**XML work**

- **DTD and RELAX NG** (XML and compact syntax): validation against the declared or a bound
  grammar, with IntelliSense. See [Schema Support](schema-support.md#dtd-and-relax-ng).
- **Compare and merge:** XML compared by structure (namespaces, attribute order, whitespace,
  element order), folder comparison, three-way merge. 
- **Conversion:** XML ↔ JSON ↔ YAML (Tools menu, `fxt-cli convert`).
- **Large File Viewer:** files of any size (1 GB in seconds, a few MB of memory) with search,
  structure tree, record table and streaming validation. See [Large Files](../guides/edit-xml/large-files.md).
- **XSLT profiler:** hot spots, call tree, flame graph and line times (Transform panel → Profile run).
- **XSpec:** run XSpec tests for XSLT, XQuery and Schematron, with HTML and JUnit reports.
- **FundsXML:** 17 data-quality checks with a report and as Query Console snippets, analysis queries,
  and grid formulas and filters for funds, positions, master data and transactions. See
  [FundsXML Extensions](../industry/fundsxml.md#data-quality-checks).
- **Grid:** filters inside opened table cells, saved filters (off when opened), formulas first under
  their element, German number format; much faster with very large tables (680,000 records).

**Signatures and encryption**

- **XML Encryption 1.1:** the Signature page is now **Sign & Encrypt** with **Encrypt**, **Decrypt**
  and **Inspect**. Encrypt the whole document, the element at the caret or an XPath selection for
  any number of recipients (certificate, keystore entry, shared key file, password); decrypt with
  your private key, a shared key or the password. The XML editor's context menu offers **Encrypt
  Element… / Decrypt…**. See [XML Encryption](../guides/sign-encrypt/encryption.md).
- **Signature algorithms:** RSA, RSA-PSS and ECDSA, a selectable digest and canonicalization;
  new certificates with RSA or EC keys. See [Digital Signatures](../guides/sign-encrypt/signatures.md).
- **`fxt-cli sign`, `verify`, `encrypt`, `decrypt`** (also `--inspect`). See
  [Command Line](../automate/cli-sign.md#sign).

**More**

- **`fxt-cli query`** runs XPath, XQuery and JSONPath; the **XQuery profiler** shows where a query
  spends its time (Run ▾ → Profile Query, `fxt-cli transform --profile`).
- **Regression checks for scenarios:** compare a scenario's output with an expected result
  (Check, Differences, Accept; `fxt-cli scenario --check`). See [Projects](../guides/projects.md).
- **XML compare:** ignore XPaths, XMLUnit placeholders and a numeric tolerance.
- **XSpec templates** in File → New.
- **Problems, Query Console hits and FundsXML data-quality findings** open in the document's
  current view (Tree or Graphic) instead of switching to the Text view.
- **Grid:** a click elsewhere keeps the value you are editing (like Enter) and closes an open
  context menu; the XSD diagram's context menu closes the same way.
- **One look for every dialog**, light and dark; banner and dialog images in the Windows installers.
