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

The XML Editor now includes IntelliSense features to enhance the editing experience with intelligent code completion and
auto-closing tags.

### Features

- **Tab Completion**: Use Tab key to complete XML element names (LSP integration pending)
- **Auto-closing Tags**: Automatically insert closing tags when opening new XML elements
- **LSP Integration**: Language Server Protocol integration for advanced code completion
- **Self-closing Tag Support**: Proper handling of self-closing tags (br, img, input, etc.)

### Usage

1. **Auto-closing Tags**: When you type `<element>`, the editor automatically adds `</element>` and positions the cursor
   between the tags
2. **Tab Completion**: Press Tab to trigger code completion (currently logs to console, full LSP integration in
   progress)
3. **Self-closing Tags**: Tags like `<br>`, `<img>`, `<input>` are not auto-closed as they are self-closing

### Supported Self-closing Tags

- `br`, `hr`, `img`, `input`, `meta`, `link`
- `area`, `base`, `col`, `embed`, `source`
- `track`, `wbr`, `param`, `keygen`, `command`

### Example Usage

```xml
<!-- Type: <root> -->
<!-- Result: <root></root> (cursor positioned between tags) -->

<!-- Type: <br> -->
<!-- Result: <br> (no auto-closing for self-closing tags) -->
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
├── XmlCodeEditor (IntelliSense)
│   ├── Tab Completion
│   ├── Auto-closing Tags
│   └── LSP Integration
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

- Schematron compilation is currently a placeholder (requires full XSLT transformation pipeline)
- Tab completion currently logs to console (full LSP integration pending)
- Error highlighting in editor not yet implemented

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
