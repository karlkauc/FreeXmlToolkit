# Schematron Validation

> **Last Updated:** December 2025 | **Version:** 1.1.0

Schematron lets you create custom validation rules that go beyond what XSD schemas can check. It's perfect for validating business rules and data relationships.

---

## Overview

While XSD schemas check the structure of your XML (which elements can appear where), Schematron checks the content and relationships:

| XSD Checks | Schematron Checks |
|------------|-------------------|
| Element names | Data values |
| Element order | Field relationships |
| Required fields | Business rules |
| Data types | Conditional requirements |

### Example Rules

- "If status is 'approved', an approval date must be present"
- "The total must equal the sum of all line items"
- "Each ID must be unique in the document"

---

## Toolbar

| Button | Shortcut | Description |
|--------|----------|-------------|
| **New** | - | Create new Schematron file |
| **Open** | - | Open existing Schematron file |
| **Save** | Ctrl+S | Save current file |
| **Save As** | Ctrl+Shift+S | Save with new name |
| **Add Rule** | Ctrl+R | Add a new rule template |
| **Add Favorite** | Ctrl+D | Add to favorites |
| **Favorites** | Ctrl+Shift+D | Toggle favorites panel |
| **Help** | F1 | Show help |

---

## Code Editor

The Code tab provides a full-featured Schematron editor:

### Editor Toolbar

| Button | Description |
|--------|-------------|
| **Load Schematron** | Open an existing file |
| **New File** | Create empty Schematron |
| **Save** / **Save As** | Save current file |
| **New Rule** | Insert rule template |
| **New Pattern** | Insert pattern template |
| **Format** | Format/prettify the XML |
| **Validate** | Check Schematron syntax |
| **Test Rules** | Test against XML files |

### Sidebar Panels

The sidebar provides helpful tools:

#### Quick Help

Shows Schematron basics:
- `<pattern>` - Groups related rules together
- `<rule>` - Defines context and conditions
- `<assert>` - Tests a condition (must be true)
- `<report>` - Reports a finding (when condition is true)

Common XPath expressions for Schematron rules.

#### Document Structure

Shows the structure of your current Schematron schema - patterns, rules, and assertions.

#### Rule Templates

Pre-built rule templates you can insert:
- Required field check
- Unique value check
- Conditional requirement
- Value comparison
- Cross-reference validation

#### XPath Tester

Test XPath expressions against sample XML:
1. Enter an XPath expression
2. Click **Test**
3. See the result

---

## Creating Schematron Rules

### Basic Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<schema xmlns="http://purl.oclc.org/dsdl/schematron">
    <title>My Validation Rules</title>

    <pattern name="Invoice Validation">
        <rule context="invoice">
            <assert test="@date">
                Every invoice must have a date attribute.
            </assert>
            <assert test="customer">
                Every invoice must have a customer element.
            </assert>
        </rule>
    </pattern>
</schema>
```

### Common Rule Patterns

#### Required Field

```xml
<rule context="order">
    <assert test="orderNumber">
        Order number is required.
    </assert>
</rule>
```

#### Conditional Requirement

```xml
<rule context="order">
    <assert test="not(@status='shipped') or shipDate">
        Shipped orders must have a ship date.
    </assert>
</rule>
```

#### Value Comparison

```xml
<rule context="order">
    <assert test="endDate >= startDate">
        End date must be after start date.
    </assert>
</rule>
```

#### Sum Validation

```xml
<rule context="invoice">
    <assert test="sum(item/price) = total">
        Total must equal sum of item prices.
    </assert>
</rule>
```

#### Unique Values

```xml
<rule context="items">
    <assert test="count(item) = count(distinct-values(item/@id))">
        All item IDs must be unique.
    </assert>
</rule>
```

#### Cross-Reference

```xml
<rule context="orderLine">
    <assert test="@productId = //product/@id">
        Product ID must reference an existing product.
    </assert>
</rule>
```

---

## Testing Rules

### Test Against XML Files

1. Click **Test Rules** in the toolbar
2. Select one or more XML files
3. View validation results

### Integration with XML Editor

Use Schematron validation directly in the XML Editor:

1. Open an XML file in the XML Editor
2. In the validation panel, select your Schematron file
3. Errors are highlighted in the editor
4. Click errors to jump to the problem location

---

## Favorites Integration

Save frequently used Schematron files for quick access:

- **Add Favorite** (Ctrl+D) - Save current file to favorites
- **Favorites** (Ctrl+Shift+D) - Show/hide the favorites panel

---

## XPath Reference

Common XPath expressions for Schematron rules:

| Expression | Description |
|------------|-------------|
| `.` | Current node |
| `@attribute` | Attribute of current node |
| `child::element` | Child element |
| `parent::element` | Parent element |
| `//element` | Any element in document |
| `count(element)` | Count elements |
| `string-length(.)` | Length of text content |
| `contains(., 'text')` | Text contains substring |
| `starts-with(., 'text')` | Text starts with |
| `normalize-space(.)` | Normalized whitespace |
| `sum(element)` | Sum of numeric values |
| `distinct-values(element)` | Unique values |

---

## Supported Formats

| Format | Support |
|--------|---------|
| ISO Schematron (.sch) | Full support |
| XSLT-based Schematron | Full support |

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+S | Save file |
| Ctrl+Shift+S | Save As |
| Ctrl+R | Add new rule |
| Ctrl+D | Add to favorites |
| Ctrl+Shift+D | Toggle favorites |
| F1 | Help |

---

## Tips

- **Start simple** - Begin with basic rules and add complexity
- **Test both ways** - Test with valid and invalid sample files
- **Clear messages** - Write helpful error messages for users
- **Use patterns** - Group related rules into patterns
- **XPath Tester** - Use the sidebar to test expressions

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Rule not firing | Check the context XPath matches elements |
| False positives | Review your test condition logic |
| Namespace issues | Add namespace declarations to schema |
| Performance slow | Optimize complex XPath expressions |

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [IntelliSense](context-sensitive-intellisense.md) | [Home](index.md) | [Schema Support](schema-support.md) |

**All Pages:** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
