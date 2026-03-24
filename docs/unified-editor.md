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
- **Search & Replace** - Unified search across all editor types
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
- **Save** (Ctrl+S) / **Save All** (Ctrl+Shift+S)
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
- **Properties** (Ctrl+Shift+P) - Show/hide properties and validation sidebar
- **Favorites** (Ctrl+Shift+B) - Show/hide favorites panel

## XSD Sub-Tabs

When editing an XSD file, the editor provides additional sub-tabs:

- **Text** - Source code editing with syntax highlighting
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
| Ctrl+S | Save |
| Ctrl+Shift+S | Save all |
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

## Drag and Drop

Drag files from your file manager directly into the editor to open them. Multiple files can be dropped at once.

## Relationship to Other Editor Pages

The Unified Editor provides a consolidated editing experience. The dedicated editor pages (XML Editor, XSD Editor, etc.) remain available in the sidebar for their full feature sets, including advanced workflows like XSD documentation generation, Schematron batch testing, and XSLT batch processing.
