# XML Editor Features

The XML Editor in FreeXMLToolkit now includes advanced features for enhanced XML editing and validation.

## Schematron Integration

### Overview

The XML Editor now supports Schematron validation, allowing you to validate XML documents against business rules and
constraints defined in Schematron files.

### Features

- **Schematron File Selection**: Choose a Schematron (.sch) file to define validation rules
- **Real-time Validation**: Validate XML content against Schematron rules in real-time
- **Continuous Validation**: Enable continuous validation to automatically check XML as you type
- **Error Reporting**: Detailed error messages with line and column information

### Usage

1. Open an XML file in the XML Editor
2. In the sidebar, locate the "Schematron Rules" section
3. Click the "..." button to select a Schematron file
4. The validation status will be displayed in the sidebar
5. Enable "Continuous Schematron validation" for real-time checking

### Schematron Favorites

The XML Editor supports saving frequently used Schematron files as favorites:

- Use the dedicated [Schematron Editor](favorites-system.md) to save .sch files with the "★ Add" button
- Access saved Schematron rules from the "Favorites" dropdown in any editor
- Organize Schematron files in custom categories like "Business Rules", "Validation Sets", etc.

### Supported Schematron Formats

- Pure Schematron (.sch files)
- XSLT-based Schematron (.xslt, .xsl files)

### Example Schematron Rules

```xml
<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://purl.oclc.org/dsdl/schematron">
    <title>Example Validation Schema</title>
    <pattern id="basic-validation">
        <title>Basic Structure Validation</title>
        <rule context="root">
            <assert test="element">Root element must contain at least one 'element' child</assert>
            <assert test="count(element) &lt;= 5">Root element can contain at most 5 'element' children</assert>
        </rule>
    </pattern>
</schema>
```

## IntelliSense Code Completion

### Overview

The XML Editor includes advanced IntelliSense features that provide schema-aware code completion and intelligent editing
assistance based on loaded XSD schemas.

### Features

- **Schema-Aware Completion**: Smart code completion that suggests only valid XML elements based on your XSD schema
  context
- **Context-Sensitive Suggestions**: Element suggestions are filtered based on the current XML position and parent
  elements
- **Auto-closing Tags**: Automatically insert closing tags when opening new XML elements
- **Self-closing Tag Support**: Proper handling of self-closing tags (br, img, input, etc.)
- **Real-time Schema Validation**: Integration with loaded XSD schemas for accurate completion suggestions

### Usage

1. **Schema-Aware Completion**: When you type `<` in the XML editor, a completion popup appears showing only the valid
   child elements for the current context
2. **Auto-closing Tags**: When you type `<element>`, the editor automatically adds `</element>` and positions the cursor
   between the tags
3. **Context Navigation**: Use arrow keys to navigate through completion suggestions, Enter to select, or Escape to
   dismiss

### Example Usage

```xml
<!-- Type: <root> -->
<!-- Result: <root></root> (cursor positioned between tags) -->

<!-- Type: <br> -->
<!-- Result: <br> (no auto-closing for self-closing tags) -->

<!-- Type: < inside <FundsXML4><ControlData> -->
<!-- Result: Shows only valid child elements like UniqueDocumentID, DocumentGenerated, etc. -->
```

## Grid Editor Mode

### Overview

The XML Editor features a dual-mode interface that allows you to edit XML files both as text and as structured data in a
grid format.

### Features

- **Table-like Interface**: Edit XML data in a spreadsheet-like grid for easier data management
- **Structured Data Editing**: Perfect for XML files with repetitive data structures
- **Visual Data Manipulation**: Add, edit, and delete XML elements through an intuitive grid interface
- **Seamless Mode Switching**: Switch between text and grid modes without losing data

### Usage

1. Open an XML file in the XML Editor
2. Switch to the "Grid" tab to view the structured data interface
3. Edit data directly in the grid cells
4. Switch back to "XML" tab to see the updated XML text
5. Changes in either mode are reflected in both views

## Code Folding

### Overview

The XML Editor includes code folding functionality that allows you to collapse and expand sections of XML for better
navigation in large files.

### Features

- **Hierarchical Folding**: Collapse XML elements and their children
- **Visual Indicators**: Clear visual markers show foldable regions
- **Keyboard Support**: Use keyboard shortcuts to fold/unfold sections
- **Language Server Integration**: Advanced folding ranges provided by the XML Language Server

### Usage

1. **Visual Folding**: Click the fold/unfold icons in the editor gutter next to XML elements
2. **Keyboard Shortcuts**: Use standard folding shortcuts (implementation may vary by platform)
3. **Nested Folding**: Fold parent elements to hide all child content, or fold individual child elements
4. **Persistent State**: Folding state is maintained while editing the document

## Graphical Tree View

### Overview

The XML Editor provides a visual tree representation of XML structure alongside the text editor for enhanced editing
capabilities.

### Features

- **Interactive Tree Navigation**: Browse XML structure as an expandable tree
- **Dual-Pane Editing**: Text editor and tree view work together seamlessly
- **Visual Structure Understanding**: Quickly understand complex XML hierarchies
- **Point-and-Click Editing**: Select and edit XML nodes directly from the tree view

### Usage

1. Open an XML file in the XML Editor
2. The tree view appears alongside the text editor
3. Click on tree nodes to navigate to corresponding XML elements in the text
4. Expand and collapse tree nodes to explore XML structure
5. Use the tree view to understand document hierarchy at a glance

```

## Technical Implementation

### Schematron Service

- **Interface**: `SchematronService`
- **Implementation**: `SchematronServiceImpl`
- **Dependencies**: Saxon for XSLT processing
- **Error Handling**: Comprehensive error reporting with detailed messages

### IntelliSense Implementation

- **Language Server**: LSP4J integration for code completion
- **Event Handling**: JavaFX event filters for keyboard input
- **Auto-completion**: Pattern matching for tag detection and auto-closing

### Architecture

```
XmlEditor
├── XmlCodeEditor (Text Editing)
│ ├── Schema-Aware IntelliSense
│ ├── Code Folding
│   ├── Auto-closing Tags
│ └── Syntax Highlighting
├── XmlGraphicEditor (Grid Mode)
│ ├── Table-like Interface
│ ├── Structured Data Editing
│ └── Data Manipulation
├── Tree View (Graphical)
│ ├── Interactive Navigation
│ ├── Visual Structure
│ └── Click-to-Edit
├── SchematronService (Validation)
│   ├── File Validation
│   ├── Error Processing
│   └── Real-time Checking
└── Sidebar UI
    ├── XSD Schema Section
    ├── Schematron Rules Section
    └── Validation Status
```

## Testing

### Test Coverage

- **SchematronServiceTest**: Tests for Schematron validation functionality
- **XmlCodeEditorIntelliSenseTest**: Tests for IntelliSense features
- **XmlEditorFeaturesTest**: Comprehensive integration tests

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test classes
./gradlew test --tests "org.fxt.freexmltoolkit.service.SchematronServiceTest"
./gradlew test --tests "org.fxt.freexmltoolkit.controls.XmlCodeEditorIntelliSenseTest"
./gradlew test --tests "org.fxt.freexmltoolkit.XmlEditorFeaturesTest"
```

## Future Enhancements

### Planned Features

1. **Full LSP Integration**: Complete Language Server Protocol implementation for advanced code completion
2. **Schematron Compilation**: Full Schematron to XSLT compilation for validation
3. **Completion Popup**: Visual completion suggestions with popup interface
4. **Error Highlighting**: Visual highlighting of validation errors in the editor
5. **Quick Fixes**: Automatic suggestions for fixing validation errors

### Known Limitations

#### Schema Support

- **DTD (Document Type Definition)**: Not supported - only XSD and Schematron schemas are supported
- **RelaxNG**: Not supported - only XSD and Schematron schemas are supported

#### Current Implementation Status
- Schematron compilation is currently a placeholder (requires full XSLT transformation pipeline)
- Error highlighting in editor not yet implemented for all validation types

#### Supported Schema Formats

- ✅ **XSD (XML Schema Definition)**: Full support with IntelliSense integration
- ✅ **Schematron**: Business rules validation support
- ❌ **DTD**: Not supported
- ❌ **RelaxNG**: Not supported

## Examples

### Example Files

- `examples/schematron/simple-validation.sch`: Sample Schematron validation rules
- `examples/schematron/test-data.xml`: Sample XML data for testing

### Sample Validation Rules

The included example Schematron file demonstrates:

- Basic structure validation
- Element content validation
- Attribute validation
- Nested structure validation

## Contributing

When contributing to these features:

1. Follow Java coding standards
2. Write comprehensive tests
3. Use native JavaFX components
4. Document new functionality
5. Ensure backward compatibility
