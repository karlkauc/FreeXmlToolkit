# Schematron Support

FreeXmlToolkit provides comprehensive Schematron support for advanced XML validation beyond basic XSD schema validation. Schematron allows you to define business rules and custom validation constraints using XPath expressions.

## What is Schematron?

Schematron is a rule-based validation language that expresses constraints and validation rules for XML documents using XPath expressions. Unlike XSD, which focuses on structure and data types, Schematron excels at expressing business logic and complex validation scenarios.

## Key Features

### 1. Visual Schematron Rule Builder

- **Drag-and-Drop Interface:** Create Schematron rules using a visual editor
- **Rule Templates:** Pre-built templates for common validation patterns
- **Interactive Testing:** Test rules against sample XML documents in real-time
- **XPath Helper:** Assistance with XPath expression creation and validation

### 2. Schematron Code Editor

- **Syntax Highlighting:** Full syntax highlighting for Schematron documents
- **Auto-Completion:** IntelliSense for Schematron elements and attributes
- **Error Detection:** Real-time error checking and validation
- **Documentation Generator:** Generate HTML documentation from Schematron rules

### 3. Integration with XML Editor

- **Seamless Validation:** Validate XML documents against Schematron rules directly from the XML editor
- **Error Reporting:** Detailed error messages with line numbers and descriptions
- **Rule Context:** Visual indication of which rules are applied to specific XML elements
- **Multi-Schema Support:** Use both XSD and Schematron validation simultaneously

## Schematron Components

### Rules and Patterns

```xml
<sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
    <sch:pattern id="business-rules">
        <sch:title>Business Validation Rules</sch:title>
        
        <sch:rule context="invoice">
            <sch:assert test="@date">
                Invoice must have a date attribute
            </sch:assert>
            
            <sch:assert test="sum(item/price) = total">
                Total must equal sum of item prices
            </sch:assert>
        </sch:rule>
        
        <sch:rule context="item">
            <sch:assert test="price > 0">
                Item price must be greater than zero
            </sch:assert>
        </sch:rule>
    </sch:pattern>
</sch:schema>
```

### Validation Types

- **Assert:** Specify conditions that must be true
- **Report:** Generate informational messages when conditions are met
- **Let:** Define variables for use in rules
- **Phase:** Group rules into validation phases

## Visual Rule Builder

### Rule Creation Workflow

1. **Define Context:** Select XML elements to validate
2. **Create Conditions:** Build XPath expressions for validation logic  
3. **Set Messages:** Define error and warning messages
4. **Test Rules:** Validate against sample XML documents
5. **Export Schema:** Generate complete Schematron schema

### Template Library

The visual builder includes templates for common scenarios:

- **Required Elements:** Ensure mandatory elements are present
- **Value Ranges:** Validate numeric values within specified ranges
- **Cross-References:** Validate relationships between elements
- **Business Logic:** Implement complex business rules
- **Data Consistency:** Ensure consistent data across document

## Advanced Features

### XPath Expression Builder

- **Expression Wizard:** Step-by-step XPath creation
- **Function Library:** Access to XPath functions and operators  
- **Variable Support:** Define and use variables in expressions
- **Namespace Handling:** Full XML namespace support

### Testing and Debugging

- **Live Validation:** Real-time testing against XML documents
- **Rule Debugging:** Step through rule execution
- **Performance Analysis:** Identify slow-performing rules
- **Coverage Reports:** See which rules are triggered

### Documentation Generation

Generate comprehensive HTML documentation from Schematron schemas:

```xml
<sch:rule context="invoice">
    <sch:title>Invoice Validation</sch:title>
    <sch:p>This rule validates invoice business logic including dates, amounts, and required fields.</sch:p>
    
    <sch:assert test="@date">
        <sch:title>Date Required</sch:title>
        All invoices must have a date attribute in YYYY-MM-DD format.
    </sch:assert>
</sch:rule>
```

## Integration Examples

### XML Editor Integration

```java
// Schematron validation in XML Editor
public class SchematronXmlIntegrationService {
    public ValidationResult validateXmlWithSchematron(
            Document xmlDoc, 
            File schematronFile) {
        // Load and compile Schematron schema
        // Validate XML document
        // Return detailed validation results
    }
}
```

### XSD + Schematron Validation

Combine both validation approaches for comprehensive XML validation:

1. **Structure Validation:** XSD validates XML structure and data types
2. **Business Rules:** Schematron validates business logic and constraints
3. **Combined Results:** Unified error reporting from both validation types

## Schematron File Management

### File Operations

- **Create New:** Start with blank schema or template
- **Open Existing:** Load and edit existing Schematron files
- **Import/Export:** Share schemas between projects and teams
- **Version Control:** Track changes and maintain schema versions

### Schema Organization

- **Pattern Groups:** Organize rules into logical patterns
- **Rule Libraries:** Reusable rule components
- **Include Support:** Reference external Schematron files
- **Namespace Management:** Handle multiple XML namespaces

## Performance Optimization

### Efficient Rule Design

- **Optimize XPath:** Use efficient XPath expressions
- **Context Selection:** Choose specific contexts to minimize processing
- **Variable Usage:** Define variables for repeated calculations
- **Rule Ordering:** Order rules for optimal performance

### Caching and Processing

- **Schema Compilation:** Compiled schemas cached for repeated use
- **Incremental Validation:** Only validate changed document parts
- **Background Processing:** Non-blocking validation execution
- **Memory Management:** Efficient memory usage for large documents

## Common Use Cases

### Financial Documents

```xml
<sch:pattern id="financial-validation">
    <sch:rule context="transaction">
        <sch:assert test="amount > 0">
            Transaction amount must be positive
        </sch:assert>
        
        <sch:assert test="currency = /document/baseCurrency">
            All transactions must use base currency
        </sch:assert>
    </sch:rule>
</sch:pattern>
```

### Data Consistency

```xml
<sch:pattern id="data-consistency">
    <sch:rule context="person">
        <sch:assert test="age >= 18 or guardian">
            Persons under 18 must have a guardian
        </sch:assert>
        
        <sch:assert test="count(//person[@id=current()/@id]) = 1">
            Person ID must be unique
        </sch:assert>
    </sch:rule>
</sch:pattern>
```

## Best Practices

### Rule Design

1. **Clear Messages:** Write descriptive error messages
2. **Specific Context:** Use precise context selection  
3. **Performance Aware:** Design rules for efficiency
4. **Maintainable:** Keep rules simple and well-documented

### Schema Organization

1. **Modular Design:** Group related rules in patterns
2. **Reusable Components:** Create reusable rule libraries
3. **Version Control:** Track schema changes over time
4. **Documentation:** Document rule purpose and usage

## Troubleshooting

### Common Issues

- **XPath Errors:** Invalid XPath expressions in rules
- **Context Problems:** Incorrect context selection
- **Namespace Issues:** Missing or incorrect namespace declarations
- **Performance:** Slow rule execution

### Debugging Tools

- **XPath Tester:** Test XPath expressions independently
- **Rule Debugger:** Step through rule execution
- **Validation Log:** Detailed execution logging
- **Performance Profiler:** Identify bottlenecks

---

[Previous: Context-Sensitive IntelliSense](context-sensitive-intellisense.md) | [Home](index.md) | [Next: Schema Support](schema-support.md)