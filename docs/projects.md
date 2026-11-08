# Projects and Transformation Scenarios

A **project** keeps the settings that belong to a set of files in one file next to them: which
schema validates which XML files, which XML catalogs resolve schema references, and named
**transformation scenarios** (stylesheet, input, parameters, output). Commit the project file with
your files and everyone who opens it gets the same bindings and scenarios — in the application and
on the command line.

## Creating and opening a project

**File → Project** holds every project action:

| Menu item | What it does |
|---|---|
| **New Project…** | Asks where to save the project file (`.fxtproj`) and opens the new, empty project |
| **Open Project…** | Opens a project file; the project open before is closed |
| **Close Project** | Closes the project; schema bindings and catalogs no longer apply |
| **Bind Schema in Project…** | Binds a schema to XML files of the project (see below) |
| **Add Catalog to Project…** | Adds an OASIS XML catalog |
| **Edit Project File** | Opens the project file in the editor (it is JSON) |

Only one project is open at a time. It opens again at the next start, as long as its file is still
there.

While a project is open, the **Explorer** shows the project's folder (the folder of the project file)
as its workspace, titled `PROJECT · NAME`. Closing the project keeps the folder open.

## Schema bindings

**Bind Schema in Project…** asks for two things:

- **XML files** — a path or pattern relative to the project folder. `*` matches within a folder,
  `**` across folders: `data/**/*.xml` matches `data/a.xml` and `data/2026/b.xml`. The dialog
  starts with the active document's path.
- **Schema** — the XSD, starting with the schema bound to the active document.

An XML file of the project is validated against the schema of the **first** binding whose pattern
matches, before the schema the file declares (`xsi:schemaLocation`) is looked at. The status bar
shows such a schema as `… (project)`. A binding for the same pattern replaces the old one.

## Catalogs

The project's catalogs are used in addition to the catalogs of the
[Schema Library](schema-library.md), and before them, while the project is open. They are not added
to the Schema Library's own list.

## Transformation scenarios

While a project is open, the [Transform Panel](transform-panel.md) shows a **SCENARIO** section below
OPTIONS & TOOLS:

- **Choose a scenario** to fill the panel with its stylesheet, input file, parameters and output
  method. Run Transform and Debug then work as usual.
- **Run Scenario** runs the chosen scenario from the files on disk and shows the result in the
  OUTPUT panel. When the scenario names an output file, the result is also written there (folders
  are created). Save open stylesheets first: Run Scenario does not read unsaved editor text.
- The **disk button** saves the panel's current settings as a scenario: stylesheet, input (the input
  file, or the active document when it is saved), parameters and output method, plus a name and an
  optional output file. Saving under an existing name replaces that scenario.
- The **trash button** removes the chosen scenario from the project.

A scenario can also name an XQuery (`.xq`, `.xquery`, …) or an XProc pipeline (`.xpl`) instead of
a stylesheet; such scenarios run with **Run Scenario** (and on the command line), since the
Transform panel itself runs XSLT. XQuery and XProc scenarios do not need an input.

## The project file

All paths are relative to the folder of the project file, so the project can be moved or checked
out elsewhere. The file can be edited by hand:

```json
{
  "name": "Orders",
  "folders": ["."],
  "schemaBindings": [
    { "files": "data/**/*.xml", "schema": "schemas/order.xsd" }
  ],
  "catalogs": ["schemas/catalog.xml"],
  "scenarios": [
    {
      "name": "Report",
      "stylesheet": "xsl/report.xsl",
      "input": "data/order.xml",
      "parameters": { "year": "2026" },
      "outputFormat": "HTML",
      "output": "out/report.html"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `name` | Shown in the Explorer and the Transform panel; default: the file name |
| `folders` | The project's folders; the first one is the Explorer workspace (default `.`) |
| `schemaBindings` | `files` (pattern) → `schema`, first match wins |
| `catalogs` | OASIS XML catalogs |
| `scenarios` | `name`, `stylesheet`, optional `input`, `parameters`, `outputFormat` (`XML`, `HTML`, `XHTML`, `TEXT`, `JSON`; default from `xsl:output`), `output` |

After editing the file by hand, open the project again (**File → Project → Open Project…**) to use
the changes.

## On the command line

```bash
fxt-cli scenario orders.fxtproj              # run all scenarios
fxt-cli scenario orders.fxtproj Report       # run one
fxt-cli scenario orders.fxtproj --list       # list them
fxt-cli validate --project orders.fxtproj data/
```

See [`scenario`](command-line.md#scenario) and [`validate --project`](command-line.md#validate).
