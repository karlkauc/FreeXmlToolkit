# Auto-Completion (IntelliSense)

> **Version:** 2.1.0

The XML Editor includes smart auto-completion that helps you write XML faster and with fewer errors.

---

## Overview

![Auto-Completion in Action](img/intellisense-overview.png)
*The XML Editor with a schema-backed document - typing `<` shows context-valid element suggestions*

When you type `<` in the XML Editor, a popup appears showing only the elements that are valid at your current position. This is based on your XSD schema.

---

## Key Features

### 1. Smart Element Suggestions

- **Context-Aware**: Shows only valid child elements for your current location
- **Position-Aware**: Inside an `xs:sequence` only the elements that may follow the
  siblings already present are offered - an element that has reached its `maxOccurs` (or an
  `xs:choice` whose alternative is already there) disappears from the list. Elements that
  already exist *after* the cursor are respected too, so you cannot insert a child in the
  wrong order. Completion is strict about position: only what the schema allows at exactly
  that spot is listed.
- **Repeatable elements**: When the element you are inside may itself repeat (for example a
  FundsXML `<Fund>` with `maxOccurs="unbounded"`), only its *remaining* children are
  offered - the list does not start over with the first child just because the element can
  occur again. Only a repeatable group (`xs:sequence` / `xs:choice` with `maxOccurs` above
  1) starts over once all of its members are present.
- **Namespace-Aware**: Suggestions are inserted with the prefix your document declares for the
  element's namespace. A child of a type imported from another namespace (e.g. `c:street`
  when the document declares `xmlns:c="…"`) is inserted as `<c:street></c:street>`; elements
  in the default namespace stay unprefixed. Typing the local name (`<st`) still matches
  `c:street`.
- **Based on Schema**: Suggestions come from your loaded XSD schema
- **Reduces Errors**: You can't accidentally add invalid elements

### 2. Path-Specific Values

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

If your XML already references its schema (via `xsi:schemaLocation`), it is picked up
automatically - and if you add or change that reference while editing, the new schema is
picked up on the next validation run and IntelliSense follows automatically. Otherwise,
bind one yourself:

1. Open your XML file in the editor
2. Click the **"No XSD"** indicator in the **status bar** (or the toolbar's **Schema**
   button - **Set XSD Schema…**)
3. Select your XSD file

The indicator changes to **"XSD: name"**, and the binding drives both auto-completion **and**
schema validation.

### Step 2: Use Auto-Completion

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

### Nothing Offered Although the Element Is Empty?

The list also honours the elements that already exist *after* the cursor. If you type `<`
directly in front of the first existing child of a sequence, nothing may legally go there -
move the cursor after the last child that belongs before your new element.

### The First Child Is Offered Again Inside a Repeatable Element?

It no longer is. Earlier versions treated a repeatable element like a repeatable group and
restarted its content model. If you want to add another occurrence of the element itself,
close the current one and type `<` after it.

### Prefix Missing or Unexpected?

Prefixes are taken from the `xmlns` declarations of your document. If the namespace of the
suggested element is not declared at all, the prefix used inside the schema is inserted -
add the matching `xmlns:` declaration to the root element.

---

## Navigation

| Previous                                    | Home             | Next                                        |
|---------------------------------------------|------------------|---------------------------------------------|
| [Digital Signatures](digital-signatures.md) | [Home](index.md) | [Schematron Support](schematron-support.md) |

**All Pages:** [Unified Shell](unified-shell.md) | [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [JSON Editor](json-editor.md) | [XSD Tools](xsd-tools.md) | [Profiled XML Generation](profiled-xml-generation.md) | [XSD Validation](xsd-validation.md) | [XSLT Viewer](xslt-viewer.md) | [XSLT Developer](xslt-developer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [FundsXML Extensions](fundsxml-extensions.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Security](SECURITY.md) | [Licenses](licenses.md)
