# Auto-Completion (IntelliSense)

> **Last Updated:** November 2025 | **Version:** 1.0.0

The XML Editor includes smart auto-completion that helps you write XML faster and with fewer errors.

---

## Overview

![Auto-Completion in Action](img/intellisense-overview.png)
*Screenshot placeholder: Auto-completion popup showing element suggestions*

When you type `<` in the XML Editor, a popup appears showing only the elements that are valid at your current position. This is based on your XSD schema.

---

## Key Features

### 1. Smart Element Suggestions

![Element Suggestions](img/intellisense-elements.png)
*Screenshot placeholder: Context-specific element suggestions*

- **Context-Aware**: Shows only valid child elements for your current location
- **Based on Schema**: Suggestions come from your loaded XSD schema
- **Reduces Errors**: You can't accidentally add invalid elements

### 2. Path-Specific Values

![Enumeration Values](img/intellisense-enums.png)
*Screenshot placeholder: Enumeration value suggestions*

Elements with the same name at different locations show their correct values:

| Location | Suggested Values |
|----------|-----------------|
| `/Document/ControlData/Version` | 4.0.0, 4.0.1, 4.1.0 |
| `/Document/Report/Version` | V3, V3S1, V3S2 |

### 3. Automatic Tag Closing

When you type an opening tag like `<element>`, the editor automatically adds the closing tag `</element>`.

---

## How to Use

### Step 1: Load an XSD Schema

1. Open the XML Editor
2. Click "..." in the XSD Schema section
3. Select your XSD file

### Step 2: Use Auto-Completion

![Using Auto-Completion](img/intellisense-usage.png)
*Screenshot placeholder: Step-by-step auto-completion usage*

1. Position your cursor where you want to add an element
2. Type `<`
3. A popup shows valid elements for this location
4. Use **↑/↓** arrow keys to navigate
5. Press **Enter** to insert the selected element
6. Press **Escape** to close without selecting

### Example

```
1. Inside <document>, type <
   → Shows: header, body, footer

2. Inside <body>, type <
   → Shows: section, article, aside

3. Inside <section>, type <
   → Shows: title, paragraph, list
```

---

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `<` | Open auto-completion |
| `↑` `↓` | Navigate suggestions |
| `Enter` | Insert selected element |
| `Escape` | Close popup |
| `>` | Close the current tag |

---

## Benefits

| Benefit | Description |
|---------|-------------|
| **Faster Writing** | No need to remember element names |
| **Fewer Errors** | Only valid elements are suggested |
| **Schema Compliance** | Your XML always matches the schema |
| **Easy Learning** | Discover available elements as you type |

---

## Troubleshooting

### No Suggestions Appearing?

- Make sure an XSD schema is loaded
- Check that your cursor is inside an element
- Verify the schema file is valid

### Wrong Suggestions?

- Check that the correct schema is loaded
- Verify the XML structure is valid up to your cursor position

---

## Navigation

| Previous                                    | Home             | Next                                        |
|---------------------------------------------|------------------|---------------------------------------------|
| [Digital Signatures](digital-signatures.md) | [Home](index.md) | [Schematron Support](schematron-support.md) |

**All Pages:
** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT](xslt-viewer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Licenses](licenses.md)
