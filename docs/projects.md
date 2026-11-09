# Projects and Transformation Scenarios

A **project** keeps the settings that belong to a set of files in one file next to them:

- which schema, which Schematron and which transformation scenario belong to which files;
- which XML catalogs resolve schema references;
- named **transformation scenarios**: stylesheet, query or pipeline, input, parameters, output.

Commit the project file with your files. Everyone who opens it gets the same bindings and scenarios,
in the application and on the command line.

## Creating and opening a project

**File → Project** holds every project action:

| Menu item | What it does |
|---|---|
| **New Project…** | Asks where to save the project file (`.fxtproj`) and opens the new, empty project |
| **Open Project…** | Opens a project file; the project open before is closed |
| **Open Recent Project** | The last eight projects; **Clear List** empties it |
| **Close Project** | Closes the project; its bindings and catalogs no longer apply |
| **Project Settings…** | Edits name, folders, bindings, scenarios and catalogs (see below) |
| **Validate Project** | Validates every file of the project that has a schema or Schematron bound |
| **Bind in Project…** | Binds a schema, Schematron or scenario to files of the project |
| **Add Catalog to Project…** | Adds an OASIS XML catalog |
| **Edit Project File** | Opens the project file in the editor (it is JSON) |

Other ways to open a project:

- **Welcome page:** it lists the recent projects under **Projects**; a click opens one.
- **Explorer:** it shows project files with a briefcase icon. A double click opens the project; on
  the open project's own file, a double click opens the file in the editor.

Only one project is open at a time. It opens again at the next start, as long as its file is still
there.

While a project is open, the **Explorer** shows the project's folders as its workspace, titled
`PROJECT · NAME`. A project with several folders shows each one at the top level. Closing the
project keeps the first folder open.

The project follows changes to its file within a few seconds: saving it in the editor, a
`git pull`, or another program. If the changed file is no valid project file, the project keeps its
previous settings.

## Bindings

A binding assigns things to the files whose path matches a pattern:

- **Files:** a path or pattern relative to the project folder. `*` matches within a folder, `**`
  across folders: `data/**/*.xml` matches `data/a.xml` and `data/2026/b.xml`.
- **Schema:** an XSD, DTD or RELAX NG schema (`.rng`, `.rnc`). JSON files get a JSON Schema here.
- **Schematron:** checked in addition to the schema.
- **Scenario:** the transformation scenario of these files (see below).

Each of the three is optional, but at least one is needed. The dialog shows how many files of the
project the pattern matches.

**Which binding applies.** For each file, schema, Schematron and scenario are taken separately,
each from the first binding that matches the file and names one. A broad binding at the end can
therefore add a Schematron to every file, while narrower ones before it choose the schemas:

| Files | Schema | Schematron | Scenario |
|---|---|---|---|
| `data/special.xml` | `schemas/special.xsd` | | |
| `data/**/*.xml` | `schemas/order.xsd` | | `Report` |
| `**/*.xml` | | `rules/common.sch` | |

Here `data/special.xml` gets `special.xsd`, `common.sch` and the `Report` scenario.

**What a binding does:**

- **Schema:** it comes before the schema a file declares (`xsi:schemaLocation`). The status bar
  shows it as `… (project)`.
- **Schematron:** Validate (F8), the Validation panel and Quick Fixes use it, unless you chose
  another Schematron for the document.
- **Scenario:** the [Transform panel](transform-panel.md) chooses it when the file becomes the
  active document, and the Explorer offers it first under **Run Scenario**.

**Where to bind:**

- **File → Project → Bind in Project…** starts with the active document: its binding, or its path
  and schema.
- **In the Explorer:** select files, then right-click → **Bind in Project…**. The pattern starts as
  the file's path; several files of one folder become `folder/*.xml`.
- **Project Settings → Bindings** lists all bindings. There you can edit them, remove them and
  change their order.

The Explorer's tooltip on a file says what the project assigns to it.

## Project Settings

**File → Project → Project Settings…** (also in the Explorer's context menu) edits the project
without touching the JSON:

| Tab | What you can do |
|---|---|
| **General** | The project's name and its folders. The first folder is the Explorer workspace. |
| **Bindings** | The table of bindings: add, edit (double click), remove, move up or down. Order matters, see above. |
| **Scenarios** | Every scenario with all its fields: add, edit, duplicate, remove, reorder. Renaming a scenario renames it in the bindings too. Removing one removes it from the bindings. |
| **Catalogs** | The project's XML catalogs. |

Nothing is saved until you press **OK**.

## Transformation scenarios

A scenario names a stylesheet (XSLT), an XQuery (`.xq`, `.xquery`, …) or an XProc pipeline
(`.xpl`), together with:

- **Input:** a fixed file, or `${file}`, the file the scenario is run for. An empty input of an
  XSLT scenario means the same as `${file}`. XQuery and XProc scenarios may have no input.
- **Parameters**, one `name=value` per line. `${file}` in a value is the file the scenario runs for.
- **Output method:** XML, HTML, XHTML, TEXT, JSON or **PDF**, or Auto (from `xsl:output`). For
  PDF, the stylesheet produces XSL-FO and FOP renders it to the output file.
- **Output file:** optional, except for PDF. `${name}` is the input file's name without
  extension, `${dir}` its folder: `out/${dir}/${name}.html`. The output must stay inside the
  project folder.

### In the Transform panel

While a project is open, the [Transform Panel](transform-panel.md) shows a **SCENARIO** section
below OPTIONS & TOOLS.

**Choosing and running a scenario:**

- **Choose a scenario** to fill the panel with its stylesheet, input file, parameters and output
  method. Run Transform and Debug then work as usual.
- When the active document has a bound scenario, the panel chooses it by itself. A scenario you
  chose yourself stays chosen.
- **Run Scenario** runs the chosen scenario from the files on disk and shows the result in the
  OUTPUT panel. A `${file}` scenario runs for the active (saved) document.
  - When the scenario names an output file, the result is also written there (folders are created).
  - A PDF opens in the PDF preview.
  - Save open stylesheets first: Run Scenario does not read unsaved editor text.

**Saving and removing scenarios:**

- The **disk button** saves the panel's current settings as a scenario: stylesheet, input,
  parameters and output method, plus a name and an optional output file. Tick **Runs for the file
  it is started for** to store `${file}` instead of the current input. Saving under an existing
  name replaces that scenario.
- The **trash button** removes the chosen scenario from the project.

### For several files

Select files in the Explorer and choose **Run Scenario ▸** in the context menu. The scenario bound
to the first file is listed first. The scenario runs once per file:

- One file without an output file: the result opens in a new tab.
- Otherwise, a summary lists what was written and what failed.
- A second file that would overwrite the output of the first fails. Use `${name}` in the output
  so that each file gets its own.

## Validate Project

**File → Project → Validate Project** (also in the Explorer's context menu) collects every file of
the project's folders that a binding gives a schema or Schematron. It skips hidden folders such as
`.git`. It then validates each file in the background with its own schema and Schematron.

Files without a bound schema use the schema they declare. JSON files are checked against their
bound JSON Schema.

The result appears in the [Validation panel](validation-panel.md)'s batch view. There is one row per
file (the tooltip shows its path), plus a text report. At most 10,000 files are checked.

## Catalogs

The project's catalogs are used in addition to the catalogs of the
[Schema Library](schema-library.md), and before them, while the project is open. They are not added
to the Schema Library's own list.

## The project file

All paths are relative to the folder of the project file, so the project can be moved or checked
out elsewhere. The file can be edited by hand; the open project picks up the change when the file
is saved.

```json
{
  "name": "Orders",
  "folders": ["."],
  "schemaBindings": [
    { "files": "data/**/*.xml", "schema": "schemas/order.xsd", "scenario": "Report" },
    { "files": "**/*.xml", "schematron": "rules/common.sch" },
    { "files": "**/*.json", "schema": "schemas/order.schema.json" }
  ],
  "catalogs": ["schemas/catalog.xml"],
  "scenarios": [
    {
      "name": "Report",
      "stylesheet": "xsl/report.xsl",
      "input": "${file}",
      "parameters": { "year": "2026" },
      "outputFormat": "HTML",
      "output": "out/${name}.html"
    },
    {
      "name": "Invoice PDF",
      "stylesheet": "xsl/invoice-fo.xsl",
      "input": "${file}",
      "outputFormat": "PDF",
      "output": "pdf/${name}.pdf"
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `name` | Shown in the Explorer and the Transform panel; default: the file name |
| `folders` | The project's folders; the first one is the Explorer workspace (default `.`) |
| `schemaBindings` | `files` (pattern) → `schema`, `schematron`, `scenario` (each optional); first match per part |
| `catalogs` | OASIS XML catalogs |
| `scenarios` | `name`, `stylesheet`, optional `input` (`${file}` = the file it runs for), `parameters`, `outputFormat` (`XML`, `HTML`, `XHTML`, `TEXT`, `JSON`, `PDF`; default from `xsl:output`), `output` (`${name}`, `${dir}`) |

Project files from before these additions keep working unchanged.

## On the command line

```bash
fxt-cli scenario orders.fxtproj                      # run all scenarios
fxt-cli scenario orders.fxtproj Report               # run one
fxt-cli scenario orders.fxtproj Report -i data/      # run it for every XML file in data/
fxt-cli scenario orders.fxtproj -i data/a.xml        # run the scenario bound to the file
fxt-cli scenario orders.fxtproj --list               # list them
fxt-cli validate --project orders.fxtproj data/      # bound schema, Schematron, JSON Schema
```

See [`scenario`](command-line.md#scenario) and [`validate --project`](command-line.md#validate).
