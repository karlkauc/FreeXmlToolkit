# Schema Support in FreeXmlToolkit

FreeXmlToolkit provides comprehensive XML schema validation and editing support through multiple schema technologies.

## Supported Schema Formats

### ✅ XSD (XML Schema Definition)

**Full Support** - Complete W3C XML Schema implementation with advanced features:

- **Validation**: Real-time validation against XSD schemas
- **IntelliSense**: Schema-aware code completion in the XML editor
- **Auto-Detection**: Automatic schema detection from XML files via `xsi:schemaLocation` and
  `xsi:noNamespaceSchemaLocation`
- **Documentation Generation**: Generate beautiful HTML documentation from XSD files
- **Sample Data Generation**: Create realistic test XML data based on XSD constraints
- **Visual Representation**: Interactive diagrams and tree views of schema structure

**Features Include:**

- Complex types and simple types
- Element and attribute declarations
- Namespaces and namespace prefixes
- Restrictions, extensions, and substitution groups
- Documentation annotations
- Import and include directives

### ✅ Schematron

**Business Rules Validation** - Support for constraint validation beyond structural validation:

- **Rule-Based Validation**: Validate XML against custom business rules
- **XPath Expressions**: Use powerful XPath expressions for complex validation logic
- **Real-time Checking**: Continuous validation as you edit XML files
- **Custom Error Messages**: Define meaningful error messages for validation failures
- **Context-Sensitive Rules**: Apply different rules based on document context

**Supported Schematron Features:**

- Pure Schematron (.sch files)
- XSLT-based Schematron transformations
- Pattern and rule definitions
- Assert and report statements
- Phase-based validation

## ❌ Unsupported Schema Formats

### DTD (Document Type Definition)

**Not Supported** - While DTD is a classic XML schema format, it is not currently supported in FreeXmlToolkit.

**Reasons for Non-Support:**

- Limited expressiveness compared to XSD
- Legacy technology with declining usage
- No namespace support
- Focus on modern schema technologies

**Alternative:** Convert DTD to XSD format for use in FreeXmlToolkit

### RelaxNG

**Not Supported** - RelaxNG is an alternative schema language that is not currently supported.

**Reasons for Non-Support:**

- Limited market adoption compared to XSD
- Focus on W3C standards (XSD)
- Resource allocation priorities

**Alternative:** Use XSD for structural validation or Schematron for business rules

## Schema Integration Features

### IntelliSense and Auto-Completion

When working with XML files that have associated XSD schemas:

1. **Context-Aware Suggestions**: Type `<` to see only valid child elements for the current position
2. **Schema-Driven Completion**: Suggestions are filtered based on XSD schema rules
3. **Real-time Validation**: Immediate feedback when elements don't conform to schema
4. **Automatic Schema Loading**: Schemas referenced in XML files are automatically loaded

### Validation Workflow

1. **Open XML File**: Load an XML document in the editor
2. **Associate Schema**: Either automatically detected or manually selected
3. **Real-time Feedback**: See validation results as you type
4. **Error Reporting**: Detailed error messages with line and column information
5. **Continuous Validation**: Optional always-on validation mode

### Documentation Generation

For XSD schemas specifically:

- **HTML Documentation**: Professional-looking web documentation
- **Interactive Diagrams**: Clickable SVG diagrams showing schema structure
- **Search Functionality**: Built-in search within generated documentation
- **Cross-References**: Linked references between schema components
- **Export Options**: Save documentation for sharing or publication

## Best Practices

### Choosing a Schema Format

- **Use XSD for**: Structural validation, data type constraints, namespace management
- **Use Schematron for**: Business rules, cross-field validation, conditional constraints
- **Combine Both**: Use XSD for structure and Schematron for business logic

### Schema Organization

- **Modular Design**: Split large schemas into smaller, focused modules
- **Clear Documentation**: Use annotations within schemas to document constraints
- **Version Management**: Maintain schema versions for backward compatibility
- **Namespace Planning**: Design namespace strategy early in development

## Migration Strategies

### From DTD to XSD

1. Use automated DTD-to-XSD conversion tools
2. Manual refinement of converted schema
3. Add data type constraints not possible in DTD
4. Implement namespace support
5. Test thoroughly with existing XML documents

### From RelaxNG to XSD

1. Analyze RelaxNG patterns and constraints
2. Map patterns to equivalent XSD constructs
3. Handle any RelaxNG features not available in XSD
4. Add Schematron rules for complex constraints
5. Validate conversion with test documents

## Technical Implementation

### XSD Support

- **Parser**: Using standard Java XML Schema validation
- **Cache System**: Efficient caching of parsed schemas
- **Error Handling**: Comprehensive error reporting and recovery
- **Performance**: Optimized for large schema files

### Schematron Support

- **Processing**: XSLT-based Schematron rule processing
- **Integration**: Seamless integration with XSD validation
- **Custom Rules**: Support for user-defined validation patterns
- **Reporting**: Detailed validation reports with context

## Future Roadmap

### Planned Enhancements

- **Enhanced XSD Features**: Additional XSD 1.1 features
- **Improved Performance**: Faster validation for large documents
- **Better Error Messages**: More helpful and specific error reporting
- **Schema Debugging**: Tools for debugging complex schema rules

### Under Consideration

- **NVDL Support**: Namespace-based validation dispatch language
- **Additional Schema Formats**: Based on user demand
- **Cloud Schema Storage**: Integration with schema repositories
- **Collaborative Editing**: Multi-user schema development

---

*For more information about specific schema features, see the individual controller documentation.*

[Home](index.md) | [XML Editor Features](xml-editor-features.md) | [XSD Tools](xsd-controller.md)