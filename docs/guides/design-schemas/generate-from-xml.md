# Generate a schema from XML

**Generate XSD from XML** reads an XML document and writes an XSD that describes what it found:
the elements, their nesting, the attributes and a data type for each value. Use it to get a first
draft of a schema for XML that has none, then finish the draft in the schema editor.

The generator has no options and no dialog. It runs at once and opens the result as a new tab.

## Before you start

- A well-formed XML document. If the document is not well-formed, an error message appears and
  no schema is generated.
- The generator reads the text in the editor, so the document does not have to be saved.

## From the open document

1. Open the XML document and make its tab the active one.
2. Choose **Tools → Generate XSD from XML**, or click the small arrow next to the **Schema**
   button in the editor toolbar and choose **Generate XSD from XML**.

Both entries are available only while an XML document is active.

The schema opens in a new, unsaved tab named **Generated.xsd**. Nothing is written to disk and the
XML document is not changed - it is not bound to the new schema either.

## From several files

Start it in one of these places:

| Where | What it uses |
|-------|--------------|
| **Tools → Generate XSD from XML Files…** or **Schema ▾ → Generate XSD from XML Files…** | Asks you to pick the `.xml` files. Works without an open document. |
| Explorer, **TOOLS** section → **Generate XSD from XML files…** | The `.xml` files selected in the workspace tree. With no XML file selected it asks you to pick the files. |
| Explorer, right-click a selection → **Generate XSD from Selected** | The `.xml` files in the selection. The entry is disabled when the selection holds no `.xml` file. |

Hold **Ctrl** or **Shift** to select several files in the Explorer or in the file dialog. Only
files ending in `.xml` are used.

One schema opens in a new, unsaved tab named **Generated.xsd**. If a file cannot be read or is not
well-formed, an error message appears and no schema is generated.

!!! warning "Several files are not merged yet"
    The result for several files describes a single element taken from the files - not
    necessarily the root element - without a target namespace. The header comment only counts the
    files. For a usable draft, generate from the one file that shows the most complete structure.

## What the schema contains

The generated schema is one global element - the root element of the document - with everything
else declared inline below it ("Russian doll" style). It declares no named types.

| In the XML | In the schema |
|------------|---------------|
| An element with text only | `xs:element` with a built-in type |
| An element with child elements | `xs:element` with an inline `xs:complexType` and an `xs:sequence` of the children |
| An element with text and attributes | `xs:complexType` with `xs:simpleContent`, extending the type of the text |
| An attribute | `xs:attribute` with a built-in type and `use="required"` |
| A default namespace on the document (else the first namespace found) | `targetNamespace`, the prefix `tns` and `elementFormDefault="qualified"` |
| A child element that occurs several times in its parent | `maxOccurs` set to the number counted |

A comment in `xs:annotation` at the top names the generator and the time of generation.

The data type is taken from the value, checked in this order:

| Value looks like | Type |
|------------------|------|
| `true`, `false`, `0`, `1` | `xs:boolean` |
| A whole number | `xs:long` (`xs:integer` if it is too large for that) |
| A number with a decimal point | `xs:decimal` |
| `2026-03-31T10:15:00…` | `xs:dateTime` |
| `2026-03-31` | `xs:date` |
| `10:15:00…` | `xs:time` |
| Starts with `http://` or `https://` | `xs:anyURI` |
| Anything else, or empty | `xs:string` |

## What to check afterwards

The schema is derived from one sample, not from a specification. Go through it before you use it:

- **Repeated elements.** An element that occurs more than once in its parent is declared with an
  empty `xs:complexType`: its children, attributes and text type are missing. Add them by hand,
  for example from a sample that contains the element only once.
- **Cardinality.** `maxOccurs` is the count in the sample (`maxOccurs="3"` for three items), and
  nothing is declared optional. Set `maxOccurs="unbounded"` and `minOccurs="0"` where they belong.
- **Attributes.** Every attribute is `use="required"`. Remove that for optional ones.
- **Data types.** A type fits the one value seen. An `id="1"` becomes `xs:boolean`, a postal code
  becomes `xs:long`, an amount written as `7` becomes `xs:long` instead of `xs:decimal`. Correct
  the types that are too narrow.
- **Order of the children.** The children of an element are not guaranteed to be listed in
  document order. Reorder the `xs:sequence` or the document will not validate.
- **Namespaces.** Only one target namespace is written. Elements from other namespaces need their
  own schema and an `xs:import`.
- **Empty elements.** An element without content, children and attributes gets an empty
  `xs:complexType`.
- **Enumerations, patterns and lengths** are not derived. Add facets where values are restricted.

Then:

1. Save the tab as an `.xsd` file (**Ctrl+S**).
2. Edit it in the [Graphic view](graphic-view.md) or the [Text view](text-view.md) - pull
   repeated structures into named [types](types.md).
3. Bind it to the XML document with **Schema ▾ → Set XSD Schema…** and validate (**F8**). The
   problems that remain show where the schema still differs from the document. See
   [XSD validation](../validate/xsd.md).

## See also

- [Generate sample XML](sample-xml.md) - the other direction: a document from a schema
- [Schema analysis](analysis.md) - quality checks for the finished schema
