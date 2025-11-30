# Schematron Support

> **Last Updated:** November 2025 | **Version:** 1.0.0

Schematron lets you create custom validation rules that go beyond what XSD schemas can check. It's perfect for validating business rules and data relationships.

---

## What is Schematron?

![Schematron Overview](img/schematron-overview.png)
*Screenshot placeholder: Schematron validation interface*

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

## Key Features

### 1. Visual Rule Builder

![Rule Builder](img/schematron-rule-builder.png)
*Screenshot placeholder: Visual rule builder interface*

Create validation rules without writing code:
- Drag-and-drop interface
- Pre-built templates for common rules
- Test rules against sample files

### 2. Real-time Validation

![Validation Results](img/schematron-validation.png)
*Screenshot placeholder: Validation results panel*

- Errors appear as you edit
- Clear error messages tell you what's wrong
- Click errors to jump to the problem location

### 3. Integration with XML Editor

Use Schematron validation directly in the XML Editor:
1. Open an XML file
2. Select a Schematron file in the sidebar
3. Enable continuous validation
4. Errors are highlighted automatically

---

## How to Use Schematron

### Creating Rules

![Creating Rules](img/schematron-create.png)
*Screenshot placeholder: Rule creation interface*

1. Open the Schematron tab
2. Create a new Schematron file
3. Define your rules using the visual builder
4. Save the file

### Validating Documents

1. Open your XML file
2. Load the Schematron rules file
3. View validation results
4. Fix any errors shown

---

## Example Schematron Rule

Here's what a simple Schematron rule looks like:

```xml
<schema xmlns="http://purl.oclc.org/dsdl/schematron">
    <pattern>
        <rule context="invoice">
            <assert test="@date">
                Every invoice must have a date
            </assert>
            <assert test="sum(item/price) = total">
                Total must equal sum of item prices
            </assert>
        </rule>
    </pattern>
</schema>
```

---

## Supported Formats

| Format | Support |
|--------|---------|
| Pure Schematron (.sch) | Full support |
| XSLT-based Schematron | Full support |

---

## Tips

- Start with simple rules and build up complexity
- Test rules with both valid and invalid sample files
- Use clear error messages to help users understand problems
- Organize rules into logical groups (patterns)

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Auto-Completion](context-sensitive-intellisense.md) | [Home](index.md) | [Schema Support](schema-support.md) |

**All Pages:** [XML Editor](xml-controller.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-controller.md) | [XSD Validation](xsd-validation-controller.md) | [XSLT](xslt-controller.md) | [FOP/PDF](fop-controller.md) | [Signatures](signature-controller.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
