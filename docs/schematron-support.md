# Schematron Validation

> **Version:** 2.1.0

> **Note:** The standalone *Schematron* editor tab has been retired.
> `.sch` files open as ordinary editor tabs in the [Unified Shell](unified-shell.md),
> and validation runs in the **Validation activity panel**. The Schematron tools —
> rule check, templates, tester, visual builder, documentation, and the validation
> report — sit in the Validation panel's **⋮ menu → Schematron Tools**. The
> capabilities below are unchanged; they are reached through the shell rather than a
> dedicated sidebar tab.

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

> Schematron files use the shell's single-row editor toolbar.
> Related actions sit in **split buttons** with a visible **▾** arrow menu; see
> [Unified Shell - Toolbar](unified-shell.md#toolbar) for the full reference.

With a `.sch` file active, the relevant actions are:

| Button | Shortcut | Description |
|--------|----------|-------------|
| **New** | Ctrl+N | Open the guided New File dialog (includes Schematron) |
| **Open** | Ctrl+O | Open an existing file |
| **Save ▾** | Ctrl+S | Save the current file; the arrow menu holds **Save As…** (Ctrl+Shift+S) and **Save All** |
| **Undo** / **Redo** | Ctrl+Z / Ctrl+Y | Undo / redo the last change (icon buttons) |
| **Format ▾** | Shift+Alt+F | Pretty-print the file. (Note: Ctrl+Shift+F opens [Find in Files](unified-shell.md#search-panel), not document search.) |
| **Validate** | F8 | Check that the Schematron file is well-formed XML (the result appears in the Validation panel's PROBLEMS list). Use **Check Rules** for a deeper rule-level inspection |
| **Query Console** | Ctrl+Shift+X | Toggle the bottom XPath/XQuery console — the place to try out rule expressions |

Rule templates, the tester, the visual builder, and **Check Rules** are reached through the
**Validation activity panel** (see [Schematron Tools in the Unified Shell](#schematron-tools-in-the-unified-shell)).

---

## Editing Schematron Files

A `.sch` (or `.schematron`) file opens as a normal file tab in the shell's editor — there is
no separate Schematron editor. The document is detected as Schematron by its namespace
(`http://purl.oclc.org/dsdl/schematron`), which enables:

- **Syntax highlighting** and **IntelliSense** for Schematron elements (`pattern`, `rule`,
  `assert`, `report`, …) in the **Text** view
- A **Tree** view of the file's patterns, rules, and assertions (switch views in the editor
  toolbar)
- Well-formedness checking with **Validate** (F8); rule-level problems are found with
  **Check Rules** (see below)

To create a new Schematron file, use **New** (Ctrl+N) and pick the Schematron template in
the New File dialog. Ready-made rule snippets are inserted from **Rule Templates**.

### Testing XPath Expressions

To try out a rule's `context` or `test` expression, open the XML document you want to
check and press **Ctrl+Shift+X** to show the **Query Console**. It evaluates XPath (and
XQuery) against the active document and lists the matching nodes, so you can refine an
expression before putting it into a rule. See
[Query Console](unified-shell.md#query-console) in the Unified Shell guide.

---

## Schematron Tools in the Unified Shell

The **Validation** activity panel offers a set of Schematron tools in its **⋮ menu**
under *Schematron Tools*. Each one opens as a closable tool tab in the editor area:

| Tool | What It Does |
|------|--------------|
| **Rule Templates** | Insert ready-made Schematron rule patterns into the active editor |
| **Tester** | Run the rules against an XML file (pre-loads the bound Schematron, if any) |
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

1. Open the **Validation** activity and choose **⋮ → Schematron Tools → Tester**
2. Pick the Schematron file with **Browse…** (the one bound to the active document is
   pre-loaded) and add the XML file(s) to test with **Add XML Files**
3. Click **Run Tests** and review the results in the Tester tab

For a quick check of many files at once, the Explorer's
[Schematron bar](unified-shell.md#schematron-bar-one-click-validation-from-the-explorer)
validates the selected XML files against a sticky Schematron in one click.

### Integration with XML Editor

Use Schematron validation directly in the XML Editor:

1. Open an XML file in the editor
2. In the **Validation** panel's SOURCES section, click the **Schematron** row to select
   your Schematron file (or pick one from its favorites menu) - or simply **drop** a
   `.sch` / `.schematron` file from your file manager onto that row or onto the
   Explorer's Schematron picker
3. Press **Validate** (F8) or click **Run Validation** in the panel; with **Validate while
   typing** on, the document is re-checked as you edit
4. Errors are highlighted in the editor; click a problem to jump to its location

In the PROBLEMS panel below the editor, Schematron findings carry a **Schematron**
source badge (so they are easy to tell apart from XSD errors), and hovering a row shows
the full message together with the failed test expression and the failing node's
XPath. For a complete overview of a run, open the
[Validation Report](#validation-report).

If a rule defines **Schematron Quick Fixes** (SQF), the
matching problems can be corrected automatically — from a right-click **Quick Fix**
menu on the problem rows, from a lightbulb in the editor gutter, or from the
Validation Report's **Fix** column. See
[Schematron Quick Fixes](schematron-quick-fixes.md) for the full guide, including
how to author fixes.

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

> Schematron validation runs on the **SchXslt**
> compiler (executed by Saxon), which fully resolves `sch:include`,
> `sch:extends`, and abstract patterns. Schematrons that declare
> `queryBinding="xslt2"` or `"xslt3"` use XPath 2.0/3.0; files without a
> `queryBinding` attribute are compiled as `xslt2`, so existing rule files keep
> working unchanged.

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+N | New file (Schematron template available) |
| Ctrl+S | Save file |
| Ctrl+Shift+S | Save As |
| F8 | Validate the active document |
| Ctrl+Shift+X | Toggle the Query Console (XPath/XQuery) |
| Alt+Enter / Ctrl+. | Open the Quick Fix chooser on the current line |
| Ctrl+D | Add to favorites |
| Ctrl+Shift+D | Toggle favorites |

---

## Tips

- **Start simple** - Begin with basic rules and add complexity
- **Test both ways** - Test with valid and invalid sample files
- **Clear messages** - Write helpful error messages for users
- **Use patterns** - Group related rules into patterns
- **Query Console** - Press Ctrl+Shift+X to test XPath expressions against the open document before using them in a rule

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
| [IntelliSense](context-sensitive-intellisense.md) | [Home](index.md) | [Schematron Quick Fixes](schematron-quick-fixes.md) |

**All Pages:** [Unified Shell](unified-shell.md) | [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [JSON Editor](json-editor.md) | [XSD Tools](xsd-tools.md) | [Profiled XML Generation](profiled-xml-generation.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [FundsXML Extensions](fundsxml-extensions.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Security](SECURITY.md) | [Licenses](licenses.md)
