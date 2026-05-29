# XML Editor Features

> **Last Updated:** May 2026 | **Version:** 1.10.0

This page describes the advanced features available in the XML Editor.

---

## Search (Find)

> **Updated in v1.10** - Search now works in the **Graphic** view too, not just the text view.

### Finding Text in Your Document

Press **Ctrl+F** to open the Find feature and type the text you are looking for. Use the up and down arrows (**Find Previous** / **Find Next**) to move between matches. When you reach the last match, the search wraps around and continues from the beginning.

### Search Works in Both Views

You can search in either the **Text** view or the **Graphic** view of an XML document:

- **Text view** - Matches are highlighted in the source code. **Replace** is available here.
- **Graphic view** - The search looks through element names, attribute names, and values across the whole document. When you press the up/down arrows, the editor jumps directly to the matching node: if the match is inside a collapsed node, its parent nodes are expanded automatically, the matching row is selected, and it is scrolled into view.

Searching is **case-insensitive** in both views.

### Switching Views While Searching

If you switch between the **Text** and **Graphic** sub-tabs while the search bar is open, the search automatically re-targets the view you switched to, so you can keep navigating without retyping your search term.

### Where Search Is Available

| Editor | How Search Appears |
|--------|--------------------|
| **XML** tab | A Find / Replace dialog (opens with Ctrl+F) |
| **Unified Editor** | An inline search bar with up/down chevron arrows |

> **Note:** In the **Graphic** view, search is for finding and navigating only - **Replace** is not available there. To replace text, use the **Text** view.

---

## Schematron Integration

![Schematron Validation](img/xml-editor-schematron.png)
*Schematron validation panel*

### What is Schematron?

Schematron lets you create custom validation rules for your XML documents. While XSD schemas validate the structure, Schematron validates business rules - for example, "if field A contains X, then field B must not be empty."

### How to Use Schematron

1. Open an XML file in the XML Editor
2. In the sidebar, find the "Schematron Rules" section
3. Click the "..." button to select a Schematron file (.sch)
4. The validation results appear in the sidebar
5. Enable "Continuous Schematron validation" to check as you type

### Saving Schematron Files as Favorites

Save frequently used Schematron files for quick access:
- Click the star icon to add to favorites
- Organize files in custom categories like "Business Rules"
- Access saved rules from any editor via the Favorites dropdown

Learn more: [Schematron Support](schematron-support.md) | [Favorites System](favorites-system.md)

---

## Auto-Completion (IntelliSense)

![IntelliSense Popup](img/xml-editor-intellisense-popup.png)
*Auto-completion suggestions popup*

### Smart Element Suggestions

When you type `<` in the editor, a popup shows only the elements that are valid at your current position based on your XSD schema.

### How It Works

1. **Type `<`** - A popup appears with valid element options
2. **Use arrow keys** - Navigate through the suggestions
3. **Press Enter** - Insert the selected element
4. **Press Escape** - Close the popup

### Auto-Closing Tags

When you type an opening tag like `<element>`, the editor automatically adds the closing tag `</element>` and places your cursor between them.

Learn more: [Auto-Completion Guide](context-sensitive-intellisense.md)

---

## Grid Editor Mode

![Grid Editor](img/xml-editor-grid-detail.png)
*Grid editor with data cells*

### Edit XML Like a Spreadsheet

The grid editor displays your XML data in a table format, making it easy to edit structured data.

### How to Use

1. Open an XML file in the XML Editor
2. Switch to the "Grid" tab
3. Click cells to edit values directly
4. Switch back to "XML" tab to see the updated code

Changes made in either mode are synchronized automatically.

---

## Code Folding

![Code Folding](img/xml-editor-folding.png)
*Code folding - collapse XML sections to focus on the parts that matter*

### Hide Sections for Better Navigation

For large XML files, you can collapse sections to focus on what you're working on:

- **Collapse**: Click the minus (-) icon next to an element
- **Expand**: Click the plus (+) icon to show the content again
- **Nested Folding**: Collapse parent elements to hide all children

---

## Tree View

![Tree View Panel](img/xml-editor-tree.png)
*Tree view sidebar*

### Visual Document Structure

The tree view shows your XML document as a hierarchical structure:

- **Navigate**: Click on tree nodes to jump to that location in the text
- **Understand Structure**: Quickly see how your document is organized
- **Expand/Collapse**: Click arrows to show or hide child elements

---

## Supported Schema Formats

| Format | Support |
|--------|---------|
| XSD (XML Schema) | Full support with IntelliSense |
| Schematron | Business rules validation |
| DTD | Not supported |
| RelaxNG | Not supported |

---

## Navigation

| Previous                    | Home             | Next                      |
|-----------------------------|------------------|---------------------------|
| [XML Editor](xml-editor.md) | [Home](index.md) | [XSD Tools](xsd-tools.md) |

**All Pages:
** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT](xslt-viewer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
