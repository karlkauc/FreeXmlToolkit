# XML Editor

The XML Editor is the core feature of the Free XML Toolkit. It provides a powerful and user-friendly interface for working with XML files.

## Key Features

![Screenshot of XML Controller](img/xml-editor-text.png)

![Screenshot of XML Controller](img/xml-editor-graphic.png)

### File Management
- **Open & Save:** Easily open existing XML files from your computer or save your work. The application remembers the last directory you used, making it convenient to access your files.
- **New Files:** Create new, empty XML documents from scratch.
- **Drag & Drop:** Simply drag and drop XML files from your file explorer directly into the editor to open them.
- **Favorites System:** Save frequently used files as favorites with custom categories and descriptions for quick
  access. See the [Favorites System](favorites-system.md) documentation for detailed information.

### Editing Experience

- **Dual-Mode Editing:** Switch between text editor and grid editor modes for different editing styles
    - **Text Mode:** Traditional code editor with advanced syntax highlighting and IntelliSense
    - **Grid Mode:** Table-like interface for structured data editing with direct cell manipulation
- **Graphical Tree View:** Interactive visual representation of XML structure with drag-and-drop editing capabilities
- **Advanced Syntax Highlighting:** Enhanced syntax highlighting with RichTextFX integration, supporting XML namespaces, attributes, and nested structures
- **Code Folding:** Intelligent collapse and expand functionality for XML sections to navigate large documents efficiently
- **Context-Sensitive IntelliSense:** Smart auto-completion that shows only relevant child elements based on your current XML context and loaded XSD schema, with ENTER key selection support
- **XSD Auto-Completion:** Automatic schema detection and element suggestions with full namespace support
- **Line Numbers:** Each line is numbered with advanced navigation support for large files
- **Font Size Control:** Dynamic font scaling with zoom controls for better readability
- **Search and Replace:** Powerful find-and-replace functionality with regex support and multi-file operations
- **Template Integration:** Access to XML templates, snippets, and XPath expressions with parameter substitution

### Formatting Tools
- **Pretty Print:** With a single click, you can format your XML into a clean, indented structure. This is useful for making messy or unformatted XML readable.
- **Minify:** This tool compresses your XML into a single line by removing all whitespace. This is useful for reducing file size for transmission or storage.

### Validation
- **Well-Formedness Check:** Automatic real-time validation ensuring your XML follows basic syntax rules with immediate error highlighting
- **Schema Validation:** Support for multiple validation methods:
    - **XSD (XML Schema Definition):** Advanced validation against W3C XML Schema files with detailed error reporting and line-by-line feedback
    - **Schematron:** Business rule validation with custom constraints and advanced pattern matching
- **Schema Features:**
    - **Automatic Schema Detection:** Intelligent detection of schema references within XML files (xsi:schemaLocation, xsi:noNamespaceSchemaLocation)
    - **Manual Schema Selection:** Browse and select schema files with support for multiple schemas and namespaces
    - **Continuous Validation:** Real-time validation feedback as you type with error markers and status indicators
    - **Validation Panel:** Dedicated validation results panel with clickable error navigation
- **Advanced Validation Features:**
    - **Multi-Schema Support:** Validate against multiple XSD files simultaneously
    - **Namespace-Aware Validation:** Full XML namespace support with prefix resolution
    - **Schematron Integration:** Visual Schematron rule builder with template library and testing capabilities
- **Supported Schema Formats:**
    - ✅ XSD (XML Schema Definition) - Full W3C compliance
    - ✅ Schematron - Complete implementation with visual rule builder
    - ❌ DTD (Document Type Definition) - not supported
    - ❌ RelaxNG - not supported

### Querying and Analysis
- **XPath & XQuery:** Advanced query capabilities with dedicated tabs for running XPath and XQuery expressions using Saxon HE 12.8 engine
- **XPath Execution Engine:** Powerful XPath processing with result highlighting and multi-format output support
- **Query Results:** Interactive result panels with syntax highlighting and export capabilities
- **XPath Snippets:** Pre-built XPath expressions library with parameter substitution and validation
- **Performance Profiling:** Built-in performance analysis for complex queries with execution time metrics

### Advanced Features
- **Memory Management:** Built-in memory monitoring with configurable thresholds and automatic cleanup
- **File Encoding Detection:** Comprehensive BOM and encoding detection with automatic handling of different character sets
- **Multi-Tab Support:** Work with multiple XML files simultaneously with session persistence
- **Background Processing:** Heavy operations run on separate threads to prevent UI blocking
- **Error Recovery:** Advanced error recovery mechanisms with intelligent suggestions for common XML mistakes

---

[Home](index.md) | [Next: XSD Tools](xsd-controller.md)
