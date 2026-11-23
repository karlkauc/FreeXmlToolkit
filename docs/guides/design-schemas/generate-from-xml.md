# Generate a schema from XML

**Generate XSD from XML** reads an XML document and writes an XSD that describes what it found:
the elements, their nesting, how often they occur, the attributes and a data type for each value.
Use it to get a first draft of a schema for XML that has none, then finish the draft in the schema
editor.

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
well-formed, an error message names the document and no schema is generated.

The samples are merged: the schema describes every file you picked. An element that is missing in
one file becomes optional, a value that is a number in one file and text in another becomes
`xs:string`, and files with different root elements give a schema with several global elements.
All files must be in the same namespace - one schema file describes one namespace.

The more the samples differ, the better the draft: pick files that show the optional parts and the
repeating parts of the format.

## What the schema contains

The generated schema has one global element for the root element of the document, with everything
else declared inline below it ("Russian doll" style). It declares no named types. Every sample is
valid against the schema it was generated from.

| In the XML | In the schema |
|------------|---------------|
| An element with text only | `xs:element` with a built-in type |
| An element with child elements | `xs:element` with an inline `xs:complexType` and an `xs:sequence` of the children, in document order |
| An element with text and attributes | `xs:complexType` with `xs:simpleContent`, extending the type of the text |
| An element with text between its child elements | `xs:complexType mixed="true"` |
| A child that occurs several times in its parent | `maxOccurs="unbounded"` |
| A child that is missing in one occurrence of its parent | `minOccurs="0"` |
| An attribute on every occurrence of its element | `xs:attribute` with `use="required"` |
| An attribute that is missing on one occurrence | `xs:attribute` without `use` (optional) |
| The namespace of the root element | `targetNamespace`, the prefix `tns` and `elementFormDefault="qualified"` |
| A child without a namespace in a document that has one | `form="unqualified"` |
| A child element or an attribute of another namespace | `xs:any` / `xs:anyAttribute` for that namespace with `processContents="lax"` |

All occurrences of an element below the same parent are put together - the three `item` elements
of an order give one declaration that fits all three. `xsi:` attributes and namespace declarations
are not declared.

When the samples list the children of an element in different orders, or a child comes back after
another one (`a`, `b`, `a`), no `xs:sequence` fits; the content then becomes
`<xs:choice minOccurs="0" maxOccurs="unbounded">`, which allows the children in any order and number.

A documentation note at the top names the generator and the number of source documents.

The data type is the narrowest type that fits **every** value seen at that place:

| Value looks like | Type |
|------------------|------|
| `true`, `false` | `xs:boolean` |
| A whole number without leading zeros (`7`, `-42`, also `0` and `1`) | `xs:integer` |
| A number with a decimal point | `xs:decimal` |
| `2026-03-31T10:15:00`, also with a time zone | `xs:dateTime` |
| `2026-03-31` | `xs:date` |
| `10:15:00` | `xs:time` |
| Starts with `http://` or `https://` | `xs:anyURI` |
| Anything else, or empty | `xs:string` |

Whole numbers and decimals at the same place give `xs:decimal`; any other mix gives `xs:string`,
and so does a place that is empty in one occurrence. A value with leading zeros (`01234`) stays
`xs:string`, because a number would lose them, and an impossible date (`2026-02-30`) is text.

## What to check afterwards

The schema is derived from samples, not from a specification - it is as good as the samples are
complete. Go through it before you use it:

- **Cardinality.** An element is optional or repeatable only if a sample showed it that way. Add
  `minOccurs="0"` and `maxOccurs="unbounded"` where the format allows more than the samples show.
- **Attributes.** An attribute that every sample carries is `use="required"`. Remove that for
  optional ones.
- **Data types.** A type fits the values seen. A field that happened to hold only numbers becomes
  `xs:integer` although the format allows letters. Widen the types that are too narrow.
- **A repeatable choice** is looser than the format usually is. Replace it with the real content
  model once you know it.
- **Other namespaces.** Elements of another namespace are only let through. Give them their own
  schema and an `xs:import` if they should be checked.
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
