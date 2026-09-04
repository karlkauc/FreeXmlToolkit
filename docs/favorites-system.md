# Favorites System

> **Version:** 2.1.0

Save your frequently used files for quick access. The favorites system works across all editors in FreeXmlToolkit.

---

## Overview

![Favorites Overview](img/favorites-overview.png)
*The Favorites panel with its **Add current** and **Manage…** buttons, the search field, and favorites grouped by file type*

The favorites system helps you quickly access the files you use most often. Save any file as a favorite and open it with one click from any editor.

---

## Key Features

### Quick Access

| Feature | Description |
|---------|-------------|
| **Works Everywhere** | Access favorites from any editor |
| **File Recognition** | Automatically detects file types (XML, XSD, etc.) |
| **One-Click Loading** | Open files instantly |

### Organization

| Feature | Description |
|---------|-------------|
| **Folders** | Move favorites into folders like "Project Files", "Templates", "Schemas" (context menu **Move to folder**) |
| **Easy Management** | Rename or remove favorites from the panel; the **Manage…** view offers smart collections and clean-up |
| **Mixed Files** | Store different file types in the same folder |

### File Information

| Feature | Description |
|---------|-------------|
| **Custom Names** | Give favorites easy-to-remember names |
| **Descriptions** | Add notes about what each file is for |
| **File Icons** | Quickly see file types at a glance |

### Grouped by Folder or File Type (Unified Shell)

The **Favorites panel** in the Unified Shell (star icon in the activity bar, or
**Ctrl+Shift+D**) groups your favorites **by folder** as soon as at least one favorite has
been moved into a folder; favorites without a folder are listed under **Uncategorized** at
the end. While no folder is in use, the panel groups **by file type** instead - for example
**XML Document**, **JSON Document**, **XSD Schema**, **Schematron Rules**, and **XSLT
Stylesheet**. Each entry shows a **colored type icon**, so you can tell the file kinds apart
at a glance. Click an entry to open it as an editor tab; the search field filters by name or path.

Keystore and trust store files (`.jks`, `.p12`, `.pfx`, `.keystore`)
are recognized as their own **Keystore** type with a lock icon - they form their own group in the Favorites panel and feed the keystore
star menus in the Signature panel (see below).

### Star Menus in the Side Panels

Wherever a side panel asks you to pick a file, a **star button** next to the row's
**Change** link opens a quick-select menu of your matching favorites - pick one to use
it in a single click, without browsing the file system:

| Panel | Row | Favorites Listed |
|-------|-----|------------------|
| **Validation** | XSD schema | XSD favorites |
| **Validation** | Schematron rules | Schematron favorites |
| **Validation** | JSON Schema (shown for JSON documents) | JSON favorites |
| **Transform** | STYLESHEET / INPUT | XSLT / XML favorites |
| **PDF / FOP** | XML input / XSL-FO stylesheet | XML / XSLT favorites |
| **Signature** | Keystore (KEYSTORE section) | Keystore favorites |
| **Signature** | Trust store (Expert Mode) | Keystore favorites |

The menu is rebuilt each time you open it, so newly added favorites appear immediately.
When you use more than one favorites folder, the entries are grouped by folder as
submenus. While you have no matching favorites, the star button is grayed out.

---

## How to Use

### Adding Files to Favorites

1. **Open a file** (it must be saved to disk - unsaved buffers cannot be added)
2. Press **Ctrl+D** or click **Add current** in the Favorites panel - the file is added
   immediately under its file name, without a dialog
3. **Optionally**, right-click the new entry to give it a different name (**Rename…**) or to
   put it into a folder (**Move to folder**)

### Opening Favorites

1. **Open the "Favorites" activity** from the activity bar on the left (or press **Ctrl+Shift+D**)
2. **Browse the folder or file-type groups**, or type in the search field
3. **Click any file** to open it immediately

### Managing Favorites

Right-click an entry in the Favorites panel for its context menu:
- **Open** - Open the file as an editor tab
- **Rename…** - Change the display name
- **Move to folder** - Put the favorite into an existing folder or a new one
- **Remove** - Delete the favorite (the file itself is untouched)

Click **Manage…** for the full-size **Favorites Manager** view: it adds smart collections
(recently used, most used) and a **Clean up** button that removes favorites whose files no
longer exist.

---

## Tips

### Organization Ideas

| Strategy | Description |
|----------|-------------|
| **By Project** | Create a category for each project |
| **By Type** | Organize by file type ("Schemas", "Templates") |
| **By Frequency** | "Daily Use", "Archive" |

### Best Practices

- Use descriptive names that make sense to you
- Add descriptions for complex files
- Clean up favorites regularly when files are moved or deleted

---

## Supported File Types

| Type | Extension |
|------|-----------|
| XML documents | .xml |
| JSON documents (incl. JSON Schemas) | .json (`.jsonc`/`.json5` files are grouped here too, but open as plain text) |
| XSD schemas | .xsd |
| Schematron rules | .sch |
| XSLT stylesheets | .xsl, .xslt |
| Keystores / trust stores | .jks, .p12, .pfx, .keystore |

---

## Auto-Populated Categories

Some optional features add their own files to your Favorites automatically:

| Category | Source |
|----------|--------|
| **FundsXML Examples** | Sample XML documents (and the examples folder) downloaded by the [FundsXML Extensions](fundsxml-extensions.md) feature |
| **FundsXML Schema** | The active FundsXML XSD schema |
| **FundsXML Schematron** | Schematron rule files downloaded by the FundsXML Extensions feature |
| **FundsXML XSLT** | XSLT stylesheets shipped with the FundsXML examples |

These categories are created **automatically**: right after you enable
the FundsXML feature (the content downloads in the background - no manual download step
needed) and again on every application start, when they are re-registered from the on-disk
cache.

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Schema Support](schema-support.md) | [Home](index.md) | [Templates](template-management.md) |

**All Pages:** [Unified Shell](unified-shell.md) | [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [JSON Editor](json-editor.md) | [XSD Tools](xsd-tools.md) | [Profiled XML Generation](profiled-xml-generation.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [FundsXML Extensions](fundsxml-extensions.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Security](SECURITY.md) | [Licenses](licenses.md)
