# Template Management System

The Template Management System in FreeXmlToolkit provides advanced capabilities for creating, managing, and using XML templates, XPath snippets, and reusable code patterns.

## Key Features

### 1. XML Template Engine

- **Template Creation:** Create reusable XML document templates with parameter substitution
- **Parameter System:** Define template parameters with validation rules and default values
- **Template Library:** Organize templates in categories with descriptions and metadata
- **Smart Substitution:** Intelligent parameter replacement with type validation and error handling

### 2. XPath Snippet Repository

- **Pre-built Snippets:** Extensive library of common XPath expressions for frequent operations
- **Custom Snippets:** Create and save your own XPath expressions with parameter support
- **Parameter Validation:** Built-in validation rules for XPath parameters to prevent syntax errors
- **Context-Aware Suggestions:** Snippets are suggested based on your current XML document structure

### 3. Template Manager Interface

![Template Manager Interface](img/templates-manager.png)

- **Visual Template Editor:** WYSIWYG editor for creating and modifying templates
- **Parameter Configuration:** Configure template parameters with type constraints and validation rules
- **Preview Mode:** Real-time preview of template output with sample data
- **Import/Export:** Share templates across different installations and teams

## Template Types

### XML Document Templates

Create complete XML document structures with placeholders for dynamic content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<${rootElement}>
    <header>
        <title>${documentTitle}</title>
        <version>${version}</version>
        <created>${createdDate}</created>
    </header>
    <content>
        ${contentPlaceholder}
    </content>
</${rootElement}>
```

### XPath Expression Snippets

Pre-configured XPath expressions for common operations:

- **Element Selection:** `//element[@attribute='${value}']`
- **Text Content:** `//element/text()[contains(.,'${searchTerm}')]`
- **Attribute Queries:** `//@${attributeName}[.='${attributeValue}']`
- **Positional Selection:** `//element[position()=${position}]`

### Schematron Rule Templates

Ready-to-use Schematron validation patterns:

```xml
<rule context="${contextPath}">
    <assert test="${testExpression}">
        ${errorMessage}
    </assert>
</rule>
```

## Template Parameters

### Parameter Types

- **String:** Text values with optional length constraints
- **Number:** Numeric values with min/max validation
- **Date:** Date values with format validation
- **Boolean:** True/false values with default states
- **Choice:** Predefined list of allowed values
- **XPath:** XPath expressions with syntax validation

### Validation Rules

```java
public class TemplateParameter {
    private String name;
    private ParameterType type;
    private String defaultValue;
    private List<ValidationRule> validationRules;
    private boolean required;
}
```

### Example Parameter Configuration

```xml
<parameter name="rootElement" type="string" required="true">
    <validation>
        <rule type="pattern" value="[a-zA-Z][a-zA-Z0-9_]*" />
        <rule type="length" min="1" max="50" />
    </validation>
    <defaultValue>document</defaultValue>
    <description>The root element name for the XML document</description>
</parameter>
```

## Usage Workflows

### 1. Creating a New Template

1. Open Template Manager from the main toolbar
2. Click "New Template" and select template type
3. Define template structure in the editor
4. Configure parameters with validation rules
5. Test template with sample values
6. Save template with descriptive metadata

### 2. Using Existing Templates

1. Access template library from XML Editor
2. Browse templates by category or search
3. Select desired template
4. Fill in parameter values in the dialog
5. Insert generated content into your document

### 3. Managing Template Library

- **Categorization:** Organize templates in logical categories
- **Search and Filter:** Find templates quickly by name, category, or description
- **Version Control:** Track template modifications with changelog
- **Sharing:** Export/import templates for team collaboration

## Advanced Features

### Template Validation Engine

- **Syntax Validation:** Automatic validation of template syntax and structure
- **Parameter Validation:** Real-time validation of parameter values
- **Preview Generation:** Live preview with error highlighting
- **Dependency Checking:** Validate template dependencies and references

### Integration with XSD Schemas

- **Schema-Aware Templates:** Templates that adapt to loaded XSD schemas
- **Element Completion:** Auto-complete template parameters based on schema definitions
- **Validation Integration:** Validate generated content against XSD schemas

### Performance Optimization

- **Lazy Loading:** Templates loaded on-demand for better performance
- **Caching System:** Compiled templates cached for repeated use
- **Background Processing:** Template operations run in background threads

## Template File Format

Templates are stored in a structured XML format:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<template>
    <metadata>
        <name>Sample Document Template</name>
        <category>General</category>
        <version>1.0</version>
        <author>Template Author</author>
        <description>A sample XML document template</description>
        <created>2024-01-01T00:00:00Z</created>
    </metadata>
    
    <parameters>
        <parameter name="rootElement" type="string" required="true">
            <defaultValue>document</defaultValue>
            <validation>
                <rule type="pattern" value="[a-zA-Z][a-zA-Z0-9_]*" />
            </validation>
        </parameter>
    </parameters>
    
    <content><![CDATA[
        <?xml version="1.0" encoding="UTF-8"?>
        <${rootElement}>
            <header>
                <title>${documentTitle}</title>
            </header>
        </${rootElement}>
    ]]></content>
</template>
```

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+T` | Open Template Manager |
| `Ctrl+Shift+T` | Insert Template |
| `Ctrl+Alt+T` | Create New Template |
| `F5` | Refresh Template Library |

## Best Practices

### Template Design

1. **Keep templates modular** - Create smaller, focused templates that can be combined
2. **Use descriptive parameters** - Clear parameter names improve usability
3. **Provide default values** - Sensible defaults speed up template usage
4. **Add comprehensive validation** - Prevent common errors with proper validation rules

### Organization

1. **Use consistent naming** - Follow naming conventions for templates and parameters
2. **Categorize logically** - Group related templates in meaningful categories
3. **Document thoroughly** - Add descriptions and usage examples to templates
4. **Version control** - Track changes and maintain backward compatibility

---

[Previous: Favorites System](favorites-system.md) | [Home](index.md) | [Next: Context-Sensitive IntelliSense](context-sensitive-intellisense.md)