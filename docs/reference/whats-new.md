---
description: Release notes of FreeXmlToolkit: what is new in the current versions.
---

# What's New

The highlights of each release. The complete notes and all downloads are on the
[GitHub Releases page](https://github.com/karlkauc/FreeXmlToolkit/releases); the version you are
running is shown under **Help → About FreeXmlToolkit**.

## Unreleased

- **Minified JSON stays responsive.** A JSON or YAML document with a very long line (minified, several MB) no
  longer freezes the window: lines over 20,000 characters are shown without colours, and the hover tooltip is off
  while a line is longer than 10,000 characters.

## 3.6.0 (2026-11-27)

- **FundsXML documents.** **Documents (Linked & Embedded)** in the FundsXML panel lists the prospectuses, KIDs and
  reports a FundsXML file links to or embeds, opens links, saves embedded documents, embeds a file (as a new document
  or in place of a link) and replaces an embedded one by a link - each change one undo step.
  [More](../industry/fundsxml.md#documents-linked-and-embedded)
- **FundsXML schema errors in the Problems panel.** **Validate active document** lists the schema errors in the
  Problems panel instead of only counting them, and a FundsXML document without a schema reference that resolves is
  bound to the active FundsXML schema, so F8, live validation and auto-completion use it too.
  [More](../industry/fundsxml.md#quick-validate-your-xml)
- **Fewer false FundsXML data-quality warnings.** Assets used only by look-through positions count as used, an
  ESTIMATED and an OFFICIAL net asset value of the same day are no longer added together, and a matured instrument is
  compared with the date of the portfolio that holds it, not with the file's content date.
- **Behind a company proxy.** HTTPS works behind a proxy that inspects traffic with a company root certificate:
  the certificates of the Windows certificate store or the macOS keychain are trusted (**Settings → Network &
  Security → Trust the certificates of the operating system**, on by default). The proxy sign-in with the Windows
  account uses Kerberos where the proxy offers it, and a refused sign-in is explained in plain words. ESMA FIRDS
  lookups are retried after a gateway error. [More](../administer/updates-and-network.md#network)
- **Large HTML results do not block the window.** An HTML result over 1 MB opens as text; the Preview offers **Show
  anyway** and **Open in browser**. Query Console results over one million characters are shown shortened - Copy
  and Save still take the whole result. [More](../guides/transform-query/run-xslt.md)
- **Scenario checks with HTML results.** HTML output that is not well-formed XML is compared line by line, so such a
  check can pass, and **Differences** shows them. [More](../guides/projects.md#regression-checks)
- **Your file stays as you wrote it.** A grid, Tree or Properties edit keeps the `<!DOCTYPE …>` declaration and
  attributes on lines of their own; saving a schema no longer adds `elementFormDefault="qualified"` /
  `attributeFormDefault="unqualified"` when it did not declare them.
- **Fixes in the editors.** Step Over on a local `xsl:variable` in the XSLT debugger stays in the loop body;
  Ctrl+Z / Ctrl+Y work in the complex type editor; previewing the same PDF again refreshes its tab instead of opening
  another; the **Change** buttons of the input rows in the Transform, Validation and PDF/FOP panels and on the Sign &
  Encrypt page work again; when transform runs overlap, only the latest one shows its result; switching on *Display
  as Table* for tens of thousands of records takes a second instead of minutes; the selected row of a table (for
  example in the FundsXML Documents dialog) is highlighted again.
- **Schema documentation.** The PDF shows the schema overview diagram again, the child table shows a child's own
  example values, the HTML documentation's search and language switch work when it is opened from disk, and very long
  texts no longer stop the Excel data dictionary. [More](../guides/design-schemas/documentation.md)
- **More German texts** in the editor, panels, dialogs, diagram, grid, debugger, XSD quality checks and comparison.
- **Website and contact** - [freexmltoolkit.org](https://freexmltoolkit.org) and office@freexmltoolkit.org - in the
  About dialog and the installation packages.
- **Generate XSD from XML gives a usable schema.** All occurrences of an element are put together, so repeated
  elements keep their children, attributes and type. What is missing somewhere becomes optional, what repeats becomes
  `maxOccurs="unbounded"`, a type fits every value seen (`id="1"` is a number, no boolean), children stay in document
  order, and several files are merged into one schema. Every sample is valid against the schema generated from it.
  [Generate a schema from XML](../guides/design-schemas/generate-from-xml.md)
- **Settings open without a pause.** On Windows the window could freeze for several seconds when the Settings page
  was opened for the first time: the installed fonts were measured for the editor font list at the same moment, and
  JavaFX handles fonts one at a time. The fonts are now looked at only when you open that list, and the result is kept
  for the next start.
- **Templates stay out of your document folders.** When Windows opened an XML file with FreeXmlToolkit, the
  application created a folder `release/examples/templates` next to the file and put its templates there. Templates
  now always live in `.freeXmlToolkit/templates` in your home folder (or the folder chosen under **Settings →
  Editor → Templates**); templates saved by earlier versions are taken over once. A `release` folder that appeared
  next to your files can be deleted - check it for templates of your own first.
  [Templates](../guides/edit-xml/templates.md#set-the-templates-folder)
- **"Open With" works on macOS.** Opening an XML or JSON file from Finder (double-click, **Open With**, dropping it
  on the Dock icon) now opens it in the editor, and **Make Default** no longer fails. The log is written to
  `.freeXmlToolkit/logs` in your home folder on every system, whatever folder the application was started from.
- **The documentation moved** to [freexmltoolkit.org/docs](https://freexmltoolkit.org/docs/); old links are
  forwarded. Pages that had no pictures got screenshots.

## 3.5.0 (2026-11-22)

- **A new logo.** Two tag brackets, blue and orange, form the X of XML. The application, its window and taskbar
  icons, the splash screen, the welcome page, the About dialog and the installers show it. Windows gets its icon in
  nine sizes from 16 to 256 px (the taskbar used to scale one large image down), macOS an icon on Apple's icon grid,
  Linux icons from 16 to 512 px. [Brand guidelines](https://github.com/karlkauc/FreeXmlToolkit/tree/main/release/brand)
- **New icons show up right away.** After an update, and after registering or removing file types, Windows is asked
  to refresh its icon cache, so the taskbar pin, the Start menu and associated files show the new icon; on macOS the
  updated application is registered again, so Finder, Dock and Launchpad drop the old one. This works from the update
  *after* this release on: when updating from 3.4.0, restart Explorer (or sign out and in) on Windows, or run
  `killall Dock` on macOS, if the old icon stays.
- **One colour scheme throughout.** The last controls of the old look - the blue buttons in the schema overview, the
  folder comparison and the large-file window, and the hover frame of the status bar fields - now follow the accent
  colour and the dark theme. The splash screen is navy like the rest of the window chrome.
- **Schema documentation in the new colours.** The generated HTML, PDF and Word documentation and its diagrams use
  the colours of the application. On the diagram page the background covers the whole diagram, and zooming with the
  mouse wheel, dragging and *Fit to screen* work again.
- **New documentation.** The documentation is ordered by task and reader: Get started (with a 15-minute
  [first steps](../get-started/first-steps.md) tutorial and a page for people
  [coming from another editor](../get-started/coming-from-another-editor.md)), Guides, Industry standards, Automate
  (with [CI recipes](../automate/ci-recipes.md)), Administer, Reference (with a [glossary](glossary.md)) and Extend.
  Large pages are split into one page per task. Old links keep working, and the help links in the application open
  the new pages.

- **Saxon HE 13.1 next to the bundled Saxon.** *Settings → XSLT Processors → Add Saxon HE 13.1* downloads the
  newest Saxon HE (XSLT 4.0 preview features) and adds it as a processor for the Transform panel, scenarios, batch
  runs, the PDF/FOP panel and `fxt-cli transform --processor`. It runs in its own process with the application's
  Java; everything else stays on the bundled Saxon HE 12.10, which XProc needs.
  [More](../guides/transform-query/external-processors.md#saxon-he-13)
- **Bookmarks.** Mark lines in any text editor (Ctrl+F2 or a click left of the line number), give them a name,
  number up to nine of them for Ctrl+1 … Ctrl+9, and move between them with F2 / Shift+F2 - across files. They
  follow your edits, survive a restart, find their line again when the file was changed outside the editor, and
  show as marks over the scroll bar. The new **Bookmarks** activity lists them for all files.
  [More](../guides/edit-xml/bookmarks.md)
- **Stylesheet errors you can act on.** A stylesheet that does not compile lists every error with its position, the
  source line with a `^` under the failing part (the function inside a `select`, a wrong attribute) and a hint;
  "Did you mean" works inside XPath expressions too. XSLT documents are checked while you type: errors are
  underlined and listed in PROBLEMS, an error in an included module names the module. Compile warnings are no
  longer lost.
- **Parameters from the stylesheet.** The Transform panel offers a row for each `xsl:param` the stylesheet
  declares, with its type; required ones must be filled in. Values are converted to the declared type, so `21`
  reaches an `xs:integer` parameter. The Query Console gets fields for an XQuery's external variables, and a `.xq`
  file asks for them once.
- **Start where you want.** A transformation can start at `xsl:initial-template`, a named template, a mode or a
  public function - templates and functions need no input document. On the command line: `fxt-cli transform
  --initial-template` / `--initial-mode`.
- **`trace()` output is shown.** It is listed under the result (Transform panel) or in the Query Console's Trace
  menu, together with `xsl:message` output, instead of going to a console nobody sees.
- **The stylesheet's `xsl:output` is respected.** For its own method, the stylesheet's indent, doctype and HTML
  version win; new **Indent** and **Omit XML declaration** options apply where it says nothing.
- **Show Execution Plan.** Saxon's plan of a query or stylesheet after optimization, in a tab.
- **Complete function suggestions.** Completion now offers all of Saxon's 200+ functions with their signatures,
  only those of the chosen XPath / XQuery version.

- **The document's namespaces need no declaration in the Query Console.** Its prefixes are declared for every query
  (`//fx:Fund` just works), and on a document with a default namespace a **Default namespace** chip makes `//Fund`
  find the elements - the most frequent reason for an empty result. Completion proposes names that match.
- **Hits while typing.** As soon as an expression checks without errors, the line under the input says what it would
  find - *"12 hits"*, *"Result: 42"* or *"No hit"* - before you run it.
- **Cancel and a time limit.** Run turns into Cancel while a query runs; an XQuery stops at once, and a run stops after
  60 seconds (Settings → Queries). A run now evaluates the query once for its text, table and hits (it used to run up to
  three times).
- **XPath 1.0 and older XQuery versions report errors like the rest.** XPath 1.0 errors carry codes and positions and
  say when a function exists only in a later version; the check while typing uses the chosen XQuery version.
- **Query files find their modules.** In a `.xq` file, `import module … at "lib.xq"` resolves next to the query file,
  and an error inside a module names the module and line.

- **A broken document is named as such.** When the queried XML is not well-formed, the Query Console no longer
  shows a confusing query error (XQuery used to say *"the context item is absent"*): it reports an *error in the
  document* with its line and column, and a click opens the document there.
- **`doc()` finds files next to the document.** XPath in the Query Console, in `.xpath` files and in
  `fxt-cli query` now resolves relative `doc()` and `unparsed-text()` paths next to the saved document (it used to
  look in the program's folder), like XQuery already did. When a file cannot be read, a hint explains relative and
  `file:` paths.

- **JSON with long strings.** A string of a few thousand characters in a `.json` or `.jsonl` file no longer stops the
  syntax colouring with an error. In documents over 1 MB the hover no longer reads the whole text at every pause of
  the mouse (it held the window for seconds on very large files); there it shows the problems at the position only.
- **No pause when Settings is the first thing you open.** Settings asked the system for its list of fonts before
  showing the syntax colour preview, which froze the window for seconds on computers with many fonts. The bundled
  editor font needs no list now, and the list is read in the background at start.
- **An update is offered only once its package can be downloaded.** Right after a release is published its packages
  are still being uploaded; an update check in those minutes offered the new version and the download failed.
- **macOS: the updated application starts again on its own.** When the new version came with a newer Java runtime,
  the update was installed but the application could not start it and reported the update as failed. Takes effect
  for updates made from this version on.
- **No second window after a double launch.** Starting FreeXmlToolkit twice in quick succession after it had not
  been closed properly (for example a shutdown with the application open) could open two windows. The second launch
  now always hands over to the first.
- **Windows updates are more patient with virus scanners.** The update helper now waits up to about three minutes for
  a program file a scanner still holds, instead of about one minute; before, it could give up and start the old
  version again.

## 3.4.0 (2026-11-18)

- **Query errors you can act on.** A failed XPath or XQuery shows its error code (linked to the W3C explanation), the
  kind of error and its position; the query line with a `^` under the error; a hint for the usual cause
  (`XPTY0019`, `XPTY0020`, `XPTY0004`, `FORG0001`); and "Did you mean" corrections for unknown functions, variables
  and prefixes, applied with one click. The console checks the expression while you type and underlines errors
  and warnings before you run it, explains an empty result on a document in a namespace, and offers *Ask AI to fix*
  when the assistant is on. In `.xq` / `.xpath` files a failed run is underlined and listed in Problems;
  `fxt-cli query` prints the same and adds `code`, `line`, `column` and `hint` to its JSON. See
  [Query Console](../guides/transform-query/query-console.md#when-a-query-fails).
- **New elements in the Graphic view go where the schema wants them.** Typing or pasting a value into an empty
  table cell (for example `TotalPercentage` of a FundsXML position that has none) used to append the new element
  after all others, which made the document invalid. It is now inserted at its schema position - `TotalPercentage`
  right after `TotalValue`; without a schema the column order decides. *Add Child Element*, *Paste as Child* and the
  entry helpers' Children list do the same. See [Grid](../guides/edit-xml/grid.md).
- **Delete removes exactly what you selected.** In the Tree view of XML and JSON documents **Delete** now removes the
  selected element, property or array item completely (before, it did nothing); an XML element's attributes are rows
  of their own there, and Delete on one removes only that attribute. In the Graphic view, Delete on table **cells**
  removes only those cells' elements, attributes or properties - no empty element is left and the row stays - while
  rows selected on their row numbers go as whole rows; Delete on an attribute row removes only the attribute. In the
  XML, JSON and XSD Tree the next node is selected afterwards, so Delete can be pressed again; one Undo restores
  everything. See [Grid](../guides/edit-xml/grid.md) and [XML Editor](../guides/edit-xml/index.md#tree-view).
- **Typed values in the Graphic view follow the schema exactly.** Decimal values such as FundsXML's
  `TotalPercentage` keep their fraction again under a German (or any other) language - typing `3,5` writes `3.5`.
  Number spinners no longer round, clamp or turn an empty value into 0; date and time pickers keep timezones and
  fractional seconds, always write seconds and never insert today's date. Types derived in several steps, simple
  content (amounts with a currency), lists, unions, durations and the Gregorian and binary types are recognised; every
  editor checks the value against all facets with the validation's engine. A value the schema does not allow is
  written with a notification naming the reason instead of silently. See [Grid](../guides/edit-xml/grid.md#typed-values).
- **Set the position weights of a FundsXML document.** *Set Position Weights (TotalPercentage)* in the FundsXML
  panel computes every portfolio position's share of the net asset value - its value in the currency of the fund,
  share class or segment, two decimals - inserts `TotalPercentage` where it is missing and corrects wrong values.
  A dialog shows what changes first; the document keeps its formatting and one Undo takes everything back. See
  [FundsXML Extensions](../industry/fundsxml.md#position-weights-totalpercentage).
- **Tree and Graphic views follow changes to the text.** When an action changes the document while the Tree or the
  Graphic view is shown - setting the position weights, a Schematron quick fix, *Fix ISIN … in Every Place*, Undo -
  the view shows the result at once instead of after the next switch; the grid keeps its expanded rows, selection
  and scroll position.
- **37 more FundsXML data-quality checks.** *Run Data Quality Checks* now runs 54 checks: units × price and nominal ×
  price against the market value, position weights against their value, exchange rates, FX forwards, duplicate
  assets, transactions and prices, positions whose ISIN or currency differs from the master data, NAV dates after
  the content date or before inception, share-class ratios and dates, country codes and BICs. Every check has a
  severity - error, warning or **hint** (a fund without LEI, an OTC derivative without counterparty LEI): hints do not
  fail a document. The report shows the severity, the Problems panel files each finding under it, and the
  documentation lists an XPath for every check to run on its own in the Query Console. A new analysis snippet shows
  the UCITS 5/10/40 issuer concentration. See [FundsXML Extensions](../industry/fundsxml.md#data-quality-checks).
- **Back to the right line in large documents.** Switching from the Graphic or Tree view to the Text view now puts
  the caret on the selected node in documents of any size; before, documents larger than 8 MB stayed where they were.
- **Problem exports describe themselves.** The Excel workbook exported from the Problems or Validation panel carries
  document properties - title, subject, description, keywords - that name the checked document and sum up its
  counts and filter.
- **Clearer input fields.** Text fields and drop-downs in the side panels and on the Settings cards have a visible
  border that gets stronger on hover and takes the accent colour when focused, so they stand out from the background.
- **No freeze when choosing the editor font.** The font list in *Settings → Editor → Editor font* is measured in the background;
  on Windows with many installed fonts opening it could freeze the window for several seconds.
- **File sizes on the Welcome page.** The recent files show their size next to when they were last changed; a long
  name is shortened instead of pushing the date out of view.
- **Table headers stay in view.** In the Graphic view of XML and JSON documents, the header row of a long table stays
  at the top while you scroll through it (inside opened cells too, stacked below the outer table's header), so you
  always see which column is which - and can still sort, select, move or resize columns there. See [Grid](../guides/edit-xml/grid.md).
- **Know where you are in the Tree view.** While you scroll through the Tree view of an XML, JSON or XSD
  document, a bar at the top shows the parents of the topmost row (`root › section › body`); click one to select
  it and scroll back to it. See [XML Editor](../guides/edit-xml/index.md#tree-view).
- **Validate where you work.** **Validate** (toolbar, F8, Tools menu, *Validate Active Report* in PK-Meldewesen …)
  no longer switches to the Validation page: the problems open in the Problems panel below the editor, the status
  bar shows the result and a short note says *✓ Valid* or how many errors, warnings and hints were found. Project
  and batch validation still use the Validation page, where their per-file results are.
- **Open what you exported.** After every export - Excel, CSV, reports, view images, statistics, submission ZIPs,
  folders of generated files - a dialog says where it was saved and offers **Open** (in Excel, the browser …),
  **Show in Folder** and **OK**. *Don't show this again* switches to a short note with an Open link. See
  [After an Export](workspace.md#after-an-export).
- **Work off large lists of problems.** The Problems panel gets a third counter for **hints** (findings that usually
  need no change: not traded in the EU, registration pending), a **category** filter (document, LEI, ISIN, FMA rule
  groups, extension rules), a **search** field and **grouping** by security / object, rule or category: a security
  that occurs a hundred times is one group. Where ESMA FIRDS knows better, **Fix ISIN … in Every Place** corrects the
  CFI code and issuer LEI in every position at once (one undo step). **Export** writes the shown or all problems to
  Excel with sheets per problem, per object and per rule. See
  [Large lists](../guides/validate/problems.md#large-lists-categories-search-grouping-export).
- **Large HTML reports in the preview.** Scrolling through a very large check report (a 150 MB QMV report gives a
  2.7 MB page) could stop the window from painting with hardware rendering on Windows. The application no longer
  caches a graphics texture for every shape, a report of 1 MB and more offers **Open in Browser** above the preview,
  and an error of the graphics card is noticed: reported anonymously, said once, and with rendering on **Auto** the
  next start uses software rendering. See [Settings](settings.md).
- **See what a long check is doing - and stop it.** The side panels of every extension (ISO 20022, ebInterface,
  FinDatEx, XBRL, KAG-Meldewesen, ESMA Reporting, PK-Meldewesen, FundsXML) show a progress block while a check,
  report or tool runs: a bar, what runs right now (*GLEIF: request 5 of 16 · ESMA FIRDS: request 23 of 87*,
  *Rules: record 12400 · line 310000 of 1800000*, *Step 3 of 4: checking the FMA rules…*), the time so far and
  **Cancel** where the work can stop. **Check Identifiers Online** asks GLEIF and the ISIN services at the same time,
  keeps every answer as it arrives (a cancelled or refused check continues where it stopped), estimates the time left
  for OpenFIGI and suggests an API key when it will be slow. Also in the Validation panel's online stage, the Tools
  menu (a small window) and `fxt-cli lei / isin / validate --gleif --isin` (one line on stderr). See
  [Online Identifier Lookup](../industry/identifier-lookup.md#progress-cancelling-and-continuing).
- **Large reports use far less memory while validating.** "Validate while typing" no longer validates an unchanged
  document again when only the caret moves or you switch tabs, and a document is never validated twice at the same
  time. The FMA rules of PK-Meldewesen (validation and HTML check report) run one after the other. With a 150 MB
  QMV report each of these runs needs about 4 GB, so several of them in parallel could exhaust the memory and freeze
  the computer.
- **Example identifiers are not looked up online.** **Check Identifiers Online**, the online stage of a validation,
  the hover and `fxt-cli` skip the synthetic `XMPL` LEIs and ISINs of the FundsXML sample files instead of reporting
  each one as "not registered at GLEIF" or "not in ESMA FIRDS"; the result says how many were skipped. See
  [Online Identifier Lookup](../industry/identifier-lookup.md#check-every-identifier-of-a-document).
- **Wrong LEI check digits in the online check.** In XML, **Check Identifiers Online** now also reports a value with
  wrong check digits in an LEI element (`<LEI>`, `IssuerLEI`, …) as an error. Before, such a value was not recognised
  as an LEI and silently skipped. See [Online Identifier Lookup](../industry/identifier-lookup.md#check-every-identifier-of-a-document).
- **Only real FundsXML examples.** The FundsXML download keeps FundsXML documents, XSLT stylesheets, Schematron
  files and XPath/XQuery snippets; Maven `pom.xml` files and other code of the examples repository no longer show up
  as examples, and an existing cache is cleaned at startup. See [FundsXML Extensions](../industry/fundsxml.md).
- **Filter the Problems panel by severity.** Click the error or warning counter in the PROBLEMS header to list only
  errors or only warnings; click it again to list all problems.
- **Open the entry of an online finding.** Findings of **Check Identifiers Online** in the Problems panel link to the
  identifier's entry at GLEIF, in the ESMA FIRDS register or at OpenFIGI (row link and **Open at …** in the row menu).
  See [Online Identifier Lookup](../industry/identifier-lookup.md#check-every-identifier-of-a-document).

## Earlier releases

- [3.0 to 3.3](whats-new-3.md)
- [2.x](whats-new-2.md)
