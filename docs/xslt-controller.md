# XSLT Transformation

> **Last Updated:** November 2025 | **Version:** 1.0.0

This tool lets you transform XML documents into other formats like HTML, text, or different XML structures using XSLT stylesheets.

---

## What is XSLT?

XSLT (Extensible Stylesheet Language Transformations) is a language for transforming XML documents. Think of it like a recipe that tells the computer how to convert your XML data into a different format.

![XSLT Transformation Overview](img/xslt-factsheet.png)
*XSLT transformation showing XML input and HTML output*

---

## How It Works

You need two files:
1. **XML File** - Your source document with the data
2. **XSLT File** - The stylesheet with transformation rules

The tool applies the rules from the XSLT file to your XML and generates the output.

![XSLT File Selection](img/xslt-file-selection.png)
*Screenshot placeholder: File selection panel with XML and XSLT inputs*

---

## Using the XSLT Tool

### Step 1: Select Your Files

1. In the left panel, select your XML source file
2. In the right panel, select your XSLT stylesheet
3. The transformation runs automatically

### Step 2: View the Results

![XSLT Results](img/xslt-results.png)
*Screenshot placeholder: Transformation results panel*

The output appears in the appropriate viewer:
- **HTML output** → Displayed as a rendered web page
- **XML output** → Displayed in a code editor with highlighting
- **Text output** → Displayed as plain text

---

## Features

| Feature | Description |
|---------|-------------|
| **Automatic Transformation** | Results update when you select files |
| **Multiple Output Formats** | HTML, XML, Text, and more |
| **Live Preview** | See results immediately |
| **Open in Browser** | View HTML output in your web browser |
| **Error Messages** | Clear feedback when something goes wrong |

---

## Interface Options

### Collapsible File Panel

Click the arrow to collapse the file selection panel and maximize your view of the results.

### Open in Browser

For HTML output, click "Open in Browser" to view the result in your default web browser.

---

## Tips

- Make sure your XML and XSLT files are valid before transformation
- Check the error messages if the transformation fails
- Use the preview to verify your output before saving

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [XSD Validation](xsd-validation-controller.md) | [Home](index.md) | [PDF Generator (FOP)](fop-controller.md) |

**All Pages:** [XML Editor](xml-controller.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-controller.md) | [XSD Validation](xsd-validation-controller.md) | [XSLT](xslt-controller.md) | [FOP/PDF](fop-controller.md) | [Signatures](signature-controller.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
