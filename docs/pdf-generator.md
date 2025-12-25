# PDF Generator (FOP)

> **Last Updated:** December 2025 | **Version:** 1.1.0

Create professional PDF documents from your XML files using Apache FOP (Formatting Objects Processor).

---

## Overview

The PDF Generator combines your XML data with an XSL-FO stylesheet to create formatted PDF documents.

![PDF Generator Overview](img/fop-pdf.png)
*The PDF Generator interface with file selection and preview*

### How It Works

```
XML Data + XSL-FO Stylesheet = PDF Document
```

1. **XML File** - Your data (the content you want in the PDF)
2. **XSL-FO Stylesheet** - A template defining layout and formatting
3. **PDF Output** - The generated document

---

## Toolbar

| Button | Shortcut | Description |
|--------|----------|-------------|
| **Open XML** | Ctrl+1 | Load XML source file |
| **Open XSL** | Ctrl+2 | Load XSL-FO stylesheet |
| **PDF Out** | Ctrl+3 | Select output PDF location |
| **Generate** | F5 | Create the PDF |
| **Add Favorite** | Ctrl+D | Add current file to favorites |
| **Favorites** | Ctrl+Shift+D | Toggle favorites panel |
| **Help** | F1 | Show help |

---

## Step-by-Step Guide

### Step 1: Select Your Files

1. Click **Open XML** to select your XML data file
2. Click **Open XSL** to select your XSL-FO stylesheet
3. Click **PDF Out** to choose where to save the PDF

You can also drag and drop files directly into the application.

### Step 2: Set PDF Metadata (Optional)

Add metadata to your PDF document:

| Field | Description |
|-------|-------------|
| **Author** | Your name or organization |
| **Title** | Document title |
| **Keywords** | Search terms for the document |
| **Producer** | Application that created the PDF |
| **Creation Date** | Automatically set |

### Step 3: Configure Options (Optional)

| Option | Description |
|--------|-------------|
| **FOP Config File** | Custom FOP configuration for fonts, renderers, etc. |

### Step 4: Generate the PDF

Click **Generate** or press **F5** to create the PDF.

### Step 5: Preview

The generated PDF appears in the built-in viewer on the right side. You can:
- Scroll through pages
- Review the layout
- Check formatting

---

## Favorites Integration

Save frequently used XML and XSL-FO files for quick access:

- **Add Favorite** (Ctrl+D) - Save current file to favorites
- **Favorites** (Ctrl+Shift+D) - Show/hide the favorites panel

The favorites panel appears on the right side and provides quick access to your saved files.

---

## Features

| Feature | Description |
|---------|-------------|
| **Drag & Drop** | Drop files directly into the application |
| **Built-in Viewer** | Preview PDFs without leaving the app |
| **PDF Metadata** | Add author, title, and keywords |
| **Progress Indicator** | Shows generation progress |
| **FOP Configuration** | Custom fonts and rendering options |
| **Favorites** | Quick access to frequently used files |

---

## XSL-FO Basics

XSL-FO (Extensible Stylesheet Language Formatting Objects) defines how your XML content should be formatted in the PDF.

### Simple Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format">

  <xsl:template match="/">
    <fo:root>
      <fo:layout-master-set>
        <fo:simple-page-master master-name="A4"
            page-height="29.7cm" page-width="21cm"
            margin="2cm">
          <fo:region-body/>
        </fo:simple-page-master>
      </fo:layout-master-set>

      <fo:page-sequence master-reference="A4">
        <fo:flow flow-name="xsl-region-body">
          <fo:block font-size="24pt" font-weight="bold">
            <xsl:value-of select="/document/title"/>
          </fo:block>
          <fo:block font-size="12pt">
            <xsl:value-of select="/document/content"/>
          </fo:block>
        </fo:flow>
      </fo:page-sequence>
    </fo:root>
  </xsl:template>

</xsl:stylesheet>
```

### Common FO Elements

| Element | Description |
|---------|-------------|
| `fo:block` | Paragraph or block of text |
| `fo:inline` | Inline text formatting |
| `fo:table` | Tables with rows and cells |
| `fo:external-graphic` | Images |
| `fo:page-number` | Current page number |
| `fo:leader` | Dots, lines, or space (for TOC) |

---

## Tips

- **Validate first** - Ensure your XML and XSL-FO files are valid before generating
- **Check error messages** - If generation fails, read the error details
- **Use the preview** - Review the PDF in the built-in viewer
- **Save to favorites** - Quick access to frequently used files
- **FOP configuration** - Use a custom config file for special fonts

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+1 | Open XML file |
| Ctrl+2 | Open XSL-FO file |
| Ctrl+3 | Select output PDF |
| F5 | Generate PDF |
| Ctrl+D | Add to favorites |
| Ctrl+Shift+D | Toggle favorites |
| F1 | Help |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| No output | Check that all three files are selected |
| Font issues | Use a custom FOP config with embedded fonts |
| Image not showing | Check image path is correct and accessible |
| Generation fails | Check the error message for details |

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [XSLT Developer](xslt-developer.md) | [Home](index.md) | [Digital Signatures](digital-signatures.md) |

**All Pages:** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
