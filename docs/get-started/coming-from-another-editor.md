# Coming from another editor

If you know Altova XMLSpy or Oxygen XML Editor, most of what you use has a counterpart here
under another name. This page lists the names side by side and says what is different.

The terms of the other products are taken from their own manuals (XMLSpy 2027 Enterprise
Edition, Oxygen XML Editor 28.1; read in November 2026). "—" means the manual pages read did not
name such a feature; it is not a statement that the product lacks it. For a feature-by-feature
table see [Comparison with other editors](../reference/comparison.md).

## What is different first

- **One window, no perspectives.** Tools are *activities* in the bar at the left edge. Selecting
  one shows its side panel next to the open document; the editor stays where it is.
- **Three views of one document.** **Text**, **Tree** and **Graphic** share the document and its
  undo history. For XML the Graphic view is a grid, for a schema it is the diagram.
- **Results stay below the editor.** Validation findings go to the **PROBLEMS** panel, the result
  of a transformation to the **OUTPUT** panel; neither opens a new window.
- **No visual authoring mode.** There is no counterpart to XMLSpy's Authentic View or Oxygen's
  Author mode, and no WSDL view.
- **The command line is included.** `fxt-cli` is installed with the application and needs no
  separate licence.

## Editing

| XMLSpy | Oxygen | FreeXmlToolkit |
|---|---|---|
| Text View | Text mode | **Text** view - [Edit XML](../guides/edit-xml/index.md) |
| Grid View | Grid mode | **Graphic** view (grid) - [Grid view](../guides/edit-xml/grid.md) |
| — | Outline view | **Tree** view |
| Authentic View, Browser View | Author mode | No counterpart; HTML files have a **Preview** view |
| Entry Helpers (Elements, Attributes, Entities) | Content Completion Assistant; Elements, Attributes and Entities views | [Auto-completion](../guides/edit-xml/auto-completion.md) (**Ctrl+Space**); in the grid the entry helpers panel |
| Info Window | Attributes view, Model view | **Properties** panel - [Edit the properties of a node](../guides/edit-xml/properties.md) |
| Find in Files | Find/Replace in Files | **Search** activity (**Ctrl+Shift+F**) - [Search and replace in files](../guides/edit-xml/search-in-files.md) |
| Project, Project Window (`.spp`) | Project view, XML project (`.xpr`) | Project (`.fxtproj`) and the **Explorer** - [Projects & scenarios](../guides/projects.md) |

## Schemas

| XMLSpy | Oxygen | FreeXmlToolkit |
|---|---|---|
| Schema View | Design mode (Schema Diagram Editor) | **Graphic** view of an XSD - [Graphic view](../guides/design-schemas/graphic-view.md); all top-level components in the [Overview view](../guides/design-schemas/overview-view.md) |
| Details, Components and Facets entry helpers | Palette view, Facets view | **Properties** panel and the **Schema** activity - [The Schema panel](../guides/design-schemas/schema-panel.md) |
| Assign Schema | Associate Schema | The schema indicator in the status bar, or **Tools → Set Schema…** - [Edit XML](../guides/edit-xml/index.md#binding-an-xsd-schema); per file pattern in a [project](../guides/projects.md) |
| Catalogs | XML Catalog | **Schema Library**, Catalogs tab - [Schema Library](../guides/design-schemas/schema-library.md) |
| Generate Sample XML/JSON/YAML File | Generate Sample XML Files | [Generate sample XML](../guides/design-schemas/sample-xml.md) |
| Schema Design → Generate Documentation | Generate Documentation → XML Schema Documentation | [Generate schema documentation](../guides/design-schemas/documentation.md) |

## Validation

| XMLSpy | Oxygen | FreeXmlToolkit |
|---|---|---|
| XML → Validate (**F8**) | Validate | **F8**, or the **Validation** activity - [Validate](../guides/validate/index.md) |
| Validate on Edit | Automatic Validation | **Validate while typing**, in the menu of the Validation panel - [Validate](../guides/validate/index.md) |
| Messages window | Results view | **PROBLEMS** panel - [Working with problems](../guides/validate/problems.md) |
| — | Validation Scenarios | Bindings of a project: schema and Schematron per file pattern - [Projects & scenarios](../guides/projects.md) |
| — | Schematron validation | [Schematron](../guides/validate/schematron.md) |
| — | Schematron Quick Fixes (SQF) | [Schematron Quick Fixes](../guides/validate/schematron-quick-fixes.md) |

## Transformation and queries

| XMLSpy | Oxygen | FreeXmlToolkit |
|---|---|---|
| XSL/XQuery → XSL Transformation (**F10**) | Apply Transformation Scenario(s) | **Run Transform** in the **Transform** activity, or **Tools → Transform with XSLT…** - [Run a transformation](../guides/transform-query/run-xslt.md) |
| Assign XSL; XSL Parameters / XQuery Variables | Transformation scenario (XSLT tab, Output tab) | The panel's STYLESHEET, INPUT and PARAMETERS sections; kept by name as a *scenario* in a [project](../guides/projects.md) |
| Output in a new window | Output as set in the scenario | **OUTPUT** panel below the editor |
| XPath/XQuery Window | XPath toolbar, XPath/XQuery Builder view | [Query console](../guides/transform-query/query-console.md) (**Ctrl+Shift+X**) |
| XSLT and XQuery Debugger | XSLT Debugger and XQuery Debugger perspectives | [Debugger](../guides/transform-query/debug.md) (**Shift+F5**), in a dock below the stylesheet; also for Schematron |
| XSLT and XQuery Profiler | XSLT/XQuery profiler | [Profile a transformation](../guides/transform-query/profile.md) |
| XSL-FO Transformation | FO Processor tab of a scenario | **PDF / FOP** activity - [PDF with XSL-FO](../guides/publish-pdf.md) |

## Comparing and converting

| XMLSpy | Oxygen | FreeXmlToolkit |
|---|---|---|
| Compare Open File With | Compare Files Tool | **Tools → Compare with File…** - [Compare and merge](../guides/compare-merge.md) |
| Compare Directories | Compare Directories Tool | Folder comparison - [Compare and merge](../guides/compare-merge.md#comparing-folders) |
| — | Three-Way Comparison | Three-way merge - [Compare and merge](../guides/compare-merge.md#merging-three-files) |
| Convert XML Instance to/from JSON/YAML | XML to JSON Converter, JSON to XML Converter | [Convert formats](../guides/convert.md) |
| Convert → Import Text File, Export to Text Files | Import/Convert → MS Excel file to XML, Text File to XML | **Tools → Spreadsheet Converter…** (**Ctrl+E**), both directions - [Convert formats](../guides/convert.md#xmlexcelcsv-converter) |

## Automation

| XMLSpy | Oxygen | FreeXmlToolkit |
|---|---|---|
| RaptorXML Server (separate product) | Scripting Oxygen (command-line scripts, separate licence) | `fxt-cli`, installed with the application - [Command line](../automate/command-line.md), [CI recipes](../automate/ci-recipes.md) |

## Next steps

- [First steps](first-steps.md) - try the workflow on the bundled example
- [Tour of the workspace](workspace-tour.md)
- [Keyboard shortcuts](../reference/keyboard-shortcuts.md)

Altova, XMLSpy and RaptorXML are trademarks of Altova GmbH; Oxygen XML Editor is a trademark of
Syncro Soft SRL. This page is not affiliated with either company.
