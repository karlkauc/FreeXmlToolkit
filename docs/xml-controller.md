# XML Editor

The XML Editor is the core feature of the Free XML Toolkit. It provides a powerful and user-friendly interface for working with XML files.

## Key Features

![Screenshot of XML Controller](img/xml-editor-text.png)

![Screenshot of XML Controller](img/xml-editor-graphic.png)

### File Management
- **Open & Save:** Easily open existing XML files from your computer or save your work. The application remembers the last directory you used, making it convenient to access your files.
- **New Files:** Create new, empty XML documents from scratch.
- **Drag & Drop:** Simply drag and drop XML files from your file explorer directly into the editor to open them.

### Editing Experience
- **Syntax Highlighting:** The editor automatically colors different parts of your XML, making it easier to read and understand the structure.
- **Line Numbers:** Each line is numbered, which is helpful for navigating large files and identifying specific locations.
- **Font Size Control:** You can increase or decrease the font size for better readability.

### Formatting Tools
- **Pretty Print:** With a single click, you can format your XML into a clean, indented structure. This is useful for making messy or unformatted XML readable.
- **Minify:** This tool compresses your XML into a single line by removing all whitespace. This is useful for reducing file size for transmission or storage.

### Validation
- **Well-Formedness Check:** The editor automatically checks if your XML is "well-formed," meaning it follows the basic syntax rules of XML.
- **Schema Validation (XSD):** You can validate your XML against an XSD schema to ensure it conforms to a specific structure and data types.
  - **Automatic Schema Detection:** The tool can often find the schema reference within the XML file.
  - **Manual Schema Selection:** You can manually select an XSD file from your computer to validate against.
  - **Continuous Validation:** Get real-time feedback on your XML's validity as you type. The validation status is always visible.

### Querying
- **XPath & XQuery:** The editor includes dedicated tabs for running XPath and XQuery expressions. This allows you to search, filter, and extract specific data from your XML documents.

### Advanced Features
- **Language Server Protocol (LSP):** The editor uses modern technology to provide advanced features like real-time error checking (diagnostics) and the ability to collapse or expand sections of your XML (code folding).
- **Recent Files:** The application keeps a list of your recently opened files for quick access.

---

[Home](index.md) | [Next: XSD Tools](xsd-controller.md)
