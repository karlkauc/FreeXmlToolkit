# Schematron Validation

> **Last Updated:** August 2026 | **Version:** 2.0.1

> **Note (Phase 10c):** The standalone *Schematron* editor tab has been retired.
> Schematron editing and validation — rule check, templates, tester, visual
> builder, documentation, and CSV/JSON export of results — now live in the
> **Unified Shell's Validation activity panel**. Open a `.sch` file and use the
> Validation panel's Schematron tools. The capabilities below are unchanged; they
> are reached through the shell rather than a dedicated sidebar tab.

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

> **Updated in August 2026** - Schematron files use the shell's single-row editor toolbar.
> Related actions sit in **split buttons** with a visible **▾** arrow menu; see
> [Unified Shell - Toolbar](unified-shell.md#toolbar) for the full reference.

With a `.sch` file active, the relevant actions are:

| Button | Shortcut | Description |
|--------|----------|-------------|
| **New** | Ctrl+N | Open the guided New File dialog (includes Schematron) |
| **Open** | Ctrl+O | Open an existing file |
| **Save ▾** | Ctrl+S | Save the current file; the arrow menu holds **Save As…** (Ctrl+Shift+S) and **Save All** |
| **Undo** / **Redo** | Ctrl+Z / Ctrl+Y | Undo / redo the last change (icon buttons) |
| **Format ▾** | Ctrl+Shift+F | Pretty-print the file |
| **Validate** | F8 | Check the Schematron syntax |

Rule templates, the tester, and the visual builder are reached through the **Validation
activity panel** (see the note at the top of this page).

---

## Code Editor

The Code tab provides a full-featured Schematron editor:

### Editor Toolbar

| Button | Description |
|--------|-------------|
| **Load Schematron** | Open an existing file |
| **New File** | Create empty Schematron |
| **Save ▾** | Save the current file; **Save As…** and **Save All** sit in the arrow menu |
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

## Schematron Tools in the Unified Shell

> **Updated June 2026** - Added **Check Rules** and **Documentation** to the Schematron tools.
> **Updated July 2026** - Added the detailed **Validation Report**.

When you work with a Schematron file in the [Unified Shell](unified-shell.md), the
**Validation** panel offers a set of Schematron tools:

| Tool | What It Does |
|------|--------------|
| **Rule Templates** | Insert ready-made Schematron rule patterns |
| **Tester** | Run the rules against an XML file |
| **Rule Builder** | Build rules visually |
| **Check Rules** | Inspect the Schematron file itself for problems |
| **Documentation** | Generate documentation for the Schematron file |
| **Validation Report** | Open a detailed report of the last Schematron validation run |

### Check Rules

**Check Rules** runs an error detector over your Schematron file and shows the issues it finds
in a categorised table, so you can fix mistakes in the rules before relying on them. Issues are
grouped into categories:

| Category | Examples |
|----------|----------|
| **XML syntax** | Malformed XML, unclosed tags |
| **Structural** | Missing or misplaced patterns, rules, or assertions |
| **XPath** | Invalid XPath expressions in contexts or tests |
| **Semantic** | Rules that can never match, or contradictory conditions |
| **Best practice** | Style and maintainability suggestions |

### Documentation

**Documentation** opens the Schematron documentation generator, which produces readable
documentation describing the patterns, rules, and assertions in your Schematron file.

### Validation Report

> **New in July 2026**

After validating an XML document that has a Schematron file bound, **Validation Report**
(in the ⋮ menu under *Schematron Tools*, or via the report button in the Validation panel's
PROBLEMS header) opens a **Schematron Report** tool tab. It shows the document name, the
Schematron file, an error/warning summary, and a table with one row per finding - severity,
line, message, the failed rule/test expression, and the failing node's XPath context.
Clicking a row jumps to that line in the editor.

The report can be exported with **Save Report (HTML)** (a self-contained HTML file) or
**Save SVRL (XML)** (the raw SVRL result of the run). See
[Detailed Schematron Report](unified-shell.md#detailed-schematron-report) in the Unified
Shell guide.

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

In the PROBLEMS panel below the editor, Schematron findings carry a **Schematron**
source badge (so they are easy to tell apart from XSD errors), and hovering a row shows
the full message together with the failed test expression and the failing node's XPath
*(new in July 2026)*. For a complete overview of a run, open the
[Validation Report](#validation-report).

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

**All Pages:** [Unified Shell](unified-shell.md) | [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [JSON Editor](json-editor.md) | [XSD Tools](xsd-tools.md) | [Profiled XML Generation](profiled-xml-generation.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [FundsXML Extensions](fundsxml-extensions.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Security](SECURITY.md) | [Licenses](licenses.md)
