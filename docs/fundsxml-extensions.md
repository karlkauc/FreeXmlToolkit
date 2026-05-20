# FundsXML Extensions

> **Last Updated:** May 2026 | **Version:** 1.0.0

Optional integration with the [FundsXML](https://fundsxml.org/) standard for the fund management industry. When enabled, FreeXmlToolkit can download and manage the official FundsXML schemas, sample documents, Schematron rules, and XPath/XQuery snippets - and use them for quick validation of your own files.

This feature is **off by default**. Enable it only if you work with FundsXML documents.

---

## Overview

FundsXML is an open XML standard used to exchange fund data between asset managers, fund administrators, depositaries, and regulators. The FundsXML community publishes:

- **Schemas** (XSD) - the official `FundsXML4.xsd` and its include files
- **Examples** - real-world sample XML documents
- **Schematron rules** - additional business-rule checks beyond what XSD covers
- **Query snippets** - useful XPath and XQuery expressions for common tasks

The FundsXML Extensions feature downloads all of this from the official GitHub repositories and integrates the content into FreeXmlToolkit so you can use it directly from the menus, sidebar, and Favorites.

> **Note:** No FundsXML content is bundled with FreeXmlToolkit. Everything is fetched at runtime from the official GitHub repositories ([fundsxml/schema](https://github.com/fundsxml/schema), MIT License, and [fundsxml/examples](https://github.com/fundsxml/examples), Apache-2.0 License).

---

## Enabling the Feature

The feature is opt-in. Until you enable it, nothing in the application changes.

1. Open **Settings** from the sidebar.
2. Switch to the **FundsXML** tab.
3. Check **Enable FundsXML Extensions**.
4. Save your settings.

Once enabled, three things appear in the application:

| Where | What appears |
|-------|--------------|
| **Menu bar** | A new top-level **FundsXML** menu |
| **XML Editor sidebar** | A new **FundsXML** tab |
| **Welcome screen** | A new **FundsXML Extensions** tile |

You can turn the feature off again at any time from the same Settings tab. Disabling it hides the menu, sidebar tab, and welcome tile, but leaves the downloaded content on disk intact.

---

## Downloading FundsXML Content

After enabling the feature, you need to fetch the content from GitHub once.

1. Open **Settings -> FundsXML**.
2. Click **Download / Update FundsXML Content**.
3. Wait for the download to finish. A progress indicator shows what is being fetched.

The application will:

- Download the latest **schema release** from `fundsxml/schema` (the official `FundsXML4.xsd` and its included files).
- Download **example documents**, **Schematron rule files**, and **XPath/XQuery snippets** from `fundsxml/examples`.
- Register the example XML files as Favorites under the category **FundsXML Examples**.
- Register the Schematron files as Favorites under the category **FundsXML Schematron**.
- Seed the XPath/XQuery snippets into your **Saved Queries** (visible in the XPath/XQuery tab of the XSLT Developer), tagged `fundsxml`.
- Select the newly downloaded schema version as the **active** version.

You can repeat this step whenever you want to update to the latest published content. Existing versions are kept on disk - downloads are additive, not replacements.

---

## Choosing an Active Schema Version

If you have downloaded more than one version of the FundsXML schema, you can switch between them.

There are two equivalent ways to pick the active version:

- **Settings -> FundsXML -> Active Schema Version** (drop-down)
- **FundsXML menu -> Active Schema Version** (submenu listing all installed versions)

The active version is the schema used by the **Quick-validate** action (see below). Switching does not delete any files - you can change versions back and forth freely.

---

## Quick-Validate Your XML

The fastest way to check an XML file against the FundsXML schema:

1. Open your XML document in the XML editor.
2. Press **Ctrl+Shift+F**, or choose **FundsXML -> Validate Current XML Against FundsXML**, or click the **Validate** button on the FundsXML sidebar tab.
3. If the document is valid, you get a confirmation message.
4. If there are errors, they are listed line-by-line in an alert dialog so you can locate and fix each one.

The validation uses whichever FundsXML schema version is currently marked as active.

> **Tip:** Use the Schematron Favorites under **FundsXML Schematron** to run additional business-rule checks. Open a Schematron file from Favorites, then validate your XML against it as you would with any other Schematron file.

---

## Browsing Downloaded Content

The **FundsXML** top-level menu provides shortcuts to open the cache folders in your system's file manager:

| Menu item | What it opens |
|-----------|---------------|
| **Open Schema Folder** | The folder containing the active schema and its include files |
| **Open Examples Folder** | The folder with downloaded sample XML documents |
| **Open Schematron Folder** | The folder with downloaded `.sch` rule files |
| **Online Documentation** | Opens [https://fundsxml.org/](https://fundsxml.org/) in your default browser |

You can also reach the example files directly from the Favorites menu (category **FundsXML Examples**) in any editor.

---

## Where the Files Are Stored

All FundsXML content is stored under your user home directory:

```
~/.freeXmlToolkit/fundsxml/
├── schema/
│   └── <version>/
│       ├── FundsXML4.xsd
│       └── include_files/
├── examples/        (downloaded XML sample documents)
├── schematron/      (downloaded .sch rule files)
├── queries/         (downloaded XPath/XQuery snippets)
└── metadata.json    (tracks installed versions and timestamps)
```

You can have multiple schema versions installed side by side; each lives in its own `schema/<version>/` subfolder.

On Windows the path is typically `C:\Users\<you>\.freeXmlToolkit\fundsxml\`. On macOS and Linux it is `/Users/<you>/.freeXmlToolkit/fundsxml/` or `/home/<you>/.freeXmlToolkit/fundsxml/`.

---

## Automatic Update Checks

By default, FreeXmlToolkit checks once per day whether a newer FundsXML schema release is available on GitHub. If a new release is found, you get a notification - but nothing is downloaded automatically. You decide whether to fetch the update from **Settings -> FundsXML -> Download / Update FundsXML Content**.

The check is throttled to at most once every 24 hours and runs quietly in the background. You can disable it in **Settings -> FundsXML -> Check for updates daily**.

---

## Contributing Snippets Upstream

The XPath/XQuery snippets in the `queries/` folder follow a simple convention so the community can grow them.

If a `queries/index.json` manifest is present, it tells FreeXmlToolkit how to label and tag each snippet:

```json
{
  "snippets": [
    {
      "file": "list-portfolios.xq",
      "name": "List all Portfolio IDs",
      "type": "xquery",
      "description": "Returns the unique identifier of every Portfolio in the document.",
      "tags": ["fundsxml", "portfolio"]
    },
    {
      "file": "total-nav.xpath",
      "name": "Total Net Asset Value",
      "type": "xpath",
      "description": "Sum of all NAV values across funds.",
      "tags": ["fundsxml", "nav"]
    }
  ]
}
```

Field reference:

| Field | Description |
|-------|-------------|
| `file` | Path of the snippet file relative to the `queries/` folder |
| `name` | Short title shown in the Saved Queries list |
| `type` | Either `xpath` or `xquery` |
| `description` | Human-readable explanation (optional but recommended) |
| `tags` | List of tags used for filtering; include `fundsxml` for FundsXML snippets |

If no `index.json` is present, snippets are still loaded - file names are used as titles and the snippet type is inferred from the file extension.

To propose a snippet for everyone, submit a pull request to the [fundsxml/examples](https://github.com/fundsxml/examples) repository.

---

## Settings Reference

The FundsXML tab in Settings exposes these options:

| Setting | Description | Default |
|---------|-------------|---------|
| **Enable FundsXML Extensions** | Master switch for the entire feature | Off |
| **Active Schema Version** | Which downloaded schema version is used for Quick-validate | Latest installed |
| **Check for updates daily** | Run a once-per-day background check for new releases | On |
| **Download / Update FundsXML Content** | Fetch the latest content from GitHub | - |

These preferences are stored in your `FreeXmlToolkit.properties` file in the user home directory.

---

## Troubleshooting

### "Download / Update" fails

- Check your internet connection.
- If you are behind a corporate proxy, configure proxy settings under **Settings -> Connection**.
- Verify that github.com is reachable from your network.

### Quick-validate reports "No active schema"

You have enabled the feature but have not downloaded a schema yet. Click **Download / Update FundsXML Content** in Settings -> FundsXML.

### The FundsXML menu disappeared

The feature was disabled. Re-enable it under **Settings -> FundsXML -> Enable FundsXML Extensions**. Your downloaded content is still on disk and will reappear immediately.

### I want to remove everything

1. Disable the feature in Settings.
2. Delete the folder `~/.freeXmlToolkit/fundsxml/`.
3. Optionally remove the Favorites entries under **FundsXML Examples** and **FundsXML Schematron**.

---

## Licensing

The FundsXML content downloaded by this feature is published by the FundsXML community:

- **Schemas** (`fundsxml/schema`): MIT License
- **Examples, Schematron, Queries** (`fundsxml/examples`): Apache License 2.0

FreeXmlToolkit itself does not bundle any of this content - it is fetched from the public GitHub repositories only when you choose to download it.

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Schematron Support](schematron-support.md) | [Home](index.md) | [Security Features](SECURITY.md) |

**All Pages:** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [JSON Editor](json-editor.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [FundsXML Extensions](fundsxml-extensions.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
