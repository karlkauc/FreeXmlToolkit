# Template Management

> **Last Updated:** June 2026 | **Version:** 1.10.0

Create and manage reusable XML templates, XPath snippets, and code patterns to speed up your work.

!!! tip "Templates power the New File dialog"
    Templates you create also appear in the guided **New File** dialog (filtered to the matching
    file type), so a brand-new document can start from a template instead of a blank page. You
    manage your own templates in the **Settings → Templates** card. See
    [Creating templates for the New File dialog](#creating-templates-for-the-new-file-dialog).

---

## Overview

![Template Manager Overview](img/templates-overview.png)
*The XML Editor with the Template Development panel and the Templates toolbar action*

Templates let you create reusable document structures with placeholders for content that changes. Instead of typing the same XML structure repeatedly, save it as a template and fill in the blanks each time.

---

## Key Features

### Template Creation

| Feature | Description |
|---------|-------------|
| **Visual Editor** | Create templates without writing code |
| **Parameters** | Define placeholders that get filled in later |
| **Preview** | See what your template will produce |
| **Categories** | Organize templates into groups |

### XPath Snippets

Pre-built XPath expressions for common tasks:
- Find elements with specific attributes
- Search for text content
- Select elements by position

---

## Template Types

### XML Document Templates

Create complete document structures with placeholders:

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

The parts in `${}` are parameters you fill in when using the template.

### Schematron Rule Templates

Ready-to-use validation patterns:

```xml
<rule context="${contextPath}">
    <assert test="${testExpression}">
        ${errorMessage}
    </assert>
</rule>
```

---

## How to Use

### Creating a New Template

1. Open **Template Manager** from the toolbar
2. Click **"New Template"**
3. Choose template type
4. Write your template with `${parameter}` placeholders
5. Define what each parameter means
6. Test with sample values
7. Save with a descriptive name

### Using a Template

1. Open the template library in any editor
2. Browse or search for the template you need
3. Select the template
4. Fill in the parameter values
5. Insert the generated content

### Managing Templates

| Action | Description |
|--------|-------------|
| **Edit** | Modify existing templates |
| **Duplicate** | Copy a template to create a variation |
| **Delete** | Remove templates you no longer need |
| **Export** | Share templates with others |
| **Import** | Add templates from others |

---

## Creating templates for the New File dialog

> **New in June 2026**

Your own templates are managed in **Settings**, in the **TEMPLATES** card, and are then offered
in the [New File dialog](unified-shell.md#new-file-dialog) whenever you create a document of the
matching type.

### Set the templates folder

1. Open **Settings** (gear icon at the bottom of the activity bar).
2. In the **TEMPLATES** card, use **Browse…** to choose the folder where your templates are kept
   (leave it empty for the default location).
3. The change takes effect right away - no restart needed.

### Add or change a template

In the **TEMPLATES** card you will find a list of your own templates with three buttons:

| Button | Action |
|--------|--------|
| **New** | Create a template. Fill in **Name**, **Category**, **Description**, **File type**, and **Content**. |
| **Edit** | Open the selected template to change any of its fields. |
| **Delete** | Remove the selected template. |

Each template is saved as a `.template` file in the templates folder. The **File type** you pick
decides where the template shows up: it appears in the New File dialog only when that same file
type is selected.

### Use it when creating a file

1. Click **New** on the toolbar (Ctrl+N), or **New file** in the Explorer panel.
2. Choose the **File type** that matches your template.
3. Open the **Template** list - your template appears alongside the built-in ones.
4. Select it; if it has parameters, you are prompted to fill them in.
5. Optionally choose a **Save to** location, then confirm to create the document.

---

## Parameter Types

| Type | Description | Example |
|------|-------------|---------|
| **Text** | Any text value | Document title |
| **Number** | Numeric values | Version number |
| **Date** | Date values | Creation date |
| **Yes/No** | True or false | Include header? |
| **Choice** | Pick from a list | Document type |

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+T` | Open Template Manager |
| `Ctrl+Shift+T` | Insert Template |
| `F5` | Refresh Template Library |

---

## Tips

### Template Design

| Tip | Description |
|-----|-------------|
| **Keep it simple** | Smaller templates are more reusable |
| **Clear names** | Use descriptive parameter names |
| **Default values** | Provide sensible defaults to speed up usage |
| **Add descriptions** | Help yourself remember what each template does |

### Organization

| Tip | Description |
|-----|-------------|
| **Use categories** | Group related templates together |
| **Consistent naming** | Follow a naming pattern |
| **Document templates** | Add notes about when to use each one |

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Favorites](favorites-system.md) | [Home](index.md) | [Tech Stack](technology-stack.md) |

**All Pages:** [Unified Shell](unified-shell.md) | [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [JSON Editor](json-editor.md) | [XSD Tools](xsd-tools.md) | [Profiled XML Generation](profiled-xml-generation.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [FundsXML Extensions](fundsxml-extensions.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Security](SECURITY.md) | [Licenses](licenses.md)
