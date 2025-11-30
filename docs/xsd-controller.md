# XSD Tools

> **Last Updated:** November 2025 | **Version:** 1.0.0

This part of the application provides tools for working with XML Schemas (XSD). These tools help you understand, document, and use XSD files effectively.

---

## 1. XSD Viewer

The XSD Viewer lets you explore and edit your schemas. It has two main views:

### Graphical View

![XSD Graphical View](img/xsd-editor-graphic.png)
*The XSD schema displayed as an interactive tree*

- **Visual Tree**: See your XSD as an interactive, hierarchical tree
- **Easy Navigation**: Click on elements to explore their structure
- **Edit Documentation**: Add or edit documentation for schema elements
- **Add Examples**: Include example values for elements

### Text View

![XSD Text View](img/xsd-editor-text.png)
*Screenshot placeholder: XSD code editor with syntax highlighting*

- **Full Code Editor**: View and edit the raw XSD source code
- **Syntax Highlighting**: Color-coded code for easy reading
- **Search and Replace**: Find and change text quickly
- **Save as Favorite**: Quick access to frequently used schemas

---

## 2. Documentation Generator

![Documentation Generator](img/xsd-documentation.png)
*Generated HTML documentation from an XSD schema*

Create professional HTML documentation from your XSD file automatically.

### How to Generate Documentation

1. Load your XSD file in the XSD Viewer
2. Click on the "Documentation" tab
3. Choose your options (image format, etc.)
4. Click "Generate"
5. Preview the documentation in the built-in viewer

### Options

| Option | Description |
|--------|-------------|
| **Image Format** | Choose PNG or SVG for diagrams |
| **Markdown Support** | Render Markdown formatting in documentation |
| **Live Preview** | View the documentation immediately |
| **Open Folder** | Access the generated files directly |

### Adding Technical Notes to Your Schema

You can add structured technical information directly in your XSD files. This information appears in a separate section of the generated documentation.

**Supported tags:**
- `@since` - When a feature was introduced
- `@see` - References to other elements
- `@deprecated` - Mark elements as deprecated
- `{@link /path/to/element}` - Create clickable links

**Example in your XSD:**

```xml
<xs:element name="Transaction">
  <xs:annotation>
    <!-- User-friendly documentation -->
    <xs:documentation>
      Represents a single financial transaction.
    </xs:documentation>

    <!-- Technical notes for developers -->
    <xs:appinfo source="@since 4.0.0"/>
    <xs:appinfo source="@see {@link /FundsXML4/ControlData}"/>
    <xs:appinfo source="@deprecated Use NewTransaction instead."/>
  </xs:annotation>
</xs:element>
```

---

## 3. Sample XML Generator

![Sample XML Generator](img/xsd-sample-generator.png)
*Screenshot placeholder: Sample XML generator panel*

Create sample XML files based on your XSD schema. This is useful for testing or as a template.

### How to Use

1. Load your XSD file
2. Go to the "Sample Data" section
3. Choose your options:
   - **Mandatory Only**: Include only required elements
   - **Max Occurrences**: Limit repeating elements
4. Click "Generate"
5. Save or copy the generated XML

---

## 4. XSD Flattener

![XSD Flattener](img/xsd-flattener.png)
*Screenshot placeholder: Flattener tool with before/after view*

Combine multiple XSD files into a single file. Useful when your schema imports other schemas.

### How to Use

1. Select your main XSD file
2. Choose where to save the new file
3. Click "Flatten"
4. The tool merges all imported schemas into one file

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [XML Editor Features](xml-editor-features.md) | [Home](index.md) | [XSD Validation](xsd-validation-controller.md) |

**All Pages:** [XML Editor](xml-controller.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-controller.md) | [XSD Validation](xsd-validation-controller.md) | [XSLT](xslt-controller.md) | [FOP/PDF](fop-controller.md) | [Signatures](signature-controller.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
