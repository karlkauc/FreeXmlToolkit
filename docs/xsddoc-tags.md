# XsdDoc Tags

JavaDoc-style annotations in `xs:appinfo` - how FreeXmlToolkit reads, renders and edits them, in
the [HTML documentation generator](xsd-tools.md#how-to-generate-documentation) and in the XSD
editor's [Properties Inspector](inspector.md).

## Where the Metadata Lives

Everything structured hangs off `xs:annotation` with its two children:

- **`xs:documentation`** - human-readable prose, multi-language via `xml:lang`.
- **`xs:appinfo`** - machine-readable metadata. Its `source` attribute carries the JavaDoc-style
  tag, the element's text content its value:
  `<xs:appinfo source="@since">4.0.0</xs:appinfo>`.

!!! note "Legacy encoding"
    Older schemas (FundsXML among them) packed tag and value into the attribute alone:
    `<xs:appinfo source="@since 4.0.0"/>`. That form is still read - as is the duplicated
    `<xs:appinfo source="@since 4.0.0">@since 4.0.0</xs:appinfo>` some older versions of the
    editor produced - and is rewritten into the canonical form the next time the schema is saved
    from the toolkit. The generated documentation is identical either way.

### Namespaces

| Prefix | Namespace URI | Used for |
|--------|---------------|----------|
| `fxt` | `http://freexmltoolkit.org/xml-schema-extensions` | `fxt:exampleValues` - the toolkit's example values |
| `fxt` | `http://freexmltoolkit.org/schema/flattening` | `fxt:sourceFile` - origin tracking when flattening ([details](#sourcefile)) |
| `altova` | `http://www.altova.com/xml-schema-extensions` | `altova:exampleValues` - read for compatibility, never written |

Both extension blocks declare their prefix on the `xs:appinfo` child itself rather than on
`xs:schema`, so a schema edited by the toolkit does not collect namespace declarations it does
not otherwise need.

## The Supported Tags

| Tag | Meaning | HTML documentation | XSD editor (Inspector) |
|-----|---------|--------------------|------------------------|
| `@since` | Schema version that introduced the component | "Since" field in the JavaDoc block | Text field `@since` |
| `@see` | Cross reference; may occur several times | "See Also" list, `{@link}` resolved to hyperlinks | List with Add/Remove + `{@link}` autocomplete |
| `@deprecated` | Deprecation marker plus reason | Red warning box at the top of the detail page | "Deprecated" checkbox + note field |
| `@version` | Version detail of the component | Only under "Additional Information" | Text field `@version` |
| `@author` | Author | Only under "Additional Information" | No field - but preserved unchanged on edit |
| `{@link XPath\|Name}` | Inline link inside `@see` / `@deprecated` | Link to the target's detail page | Autocomplete popup over the schema's element paths |
| `@markdown` | `true`/`false` - whether this node's documentation is rendered as Markdown ([details](#markdown)) | Honored in every *Markdown rendering* mode except *Off* - a node can opt in or out whatever the mode says | "Markdown" choice (*Not set* / *Markdown* / *Plain text*) |
| `fxt:exampleValues`<br>`altova:exampleValues` | Example values. Written as `fxt:`; the Altova form (the FundsXML convention) is still read | "Sample Data" on the detail page | "Example values" list; also feeds sample-XML generation and IntelliSense |
| `fxt:sourceFile` | Records which included file a component came from when a multi-file schema is flattened ([details](#sourcefile)) | - | Not shown; passed through untouched |
| *anything else* | Arbitrary `source` string or text content | "Additional Information" section (HTML-escaped) | Preserved untouched |

On top of that, `xs:documentation/@xml:lang` gets special treatment: it drives the language
switcher on the generated pages, the **Scan languages** filter of the documentation generator,
and one row per language in the Inspector.

## Example

```xml
<xs:element name="Transaction">
    <xs:annotation>
        <xs:documentation xml:lang="en">A single financial transaction.</xs:documentation>
        <xs:documentation xml:lang="de">Eine einzelne Transaktion.</xs:documentation>

        <xs:appinfo source="@since">4.0.0</xs:appinfo>
        <xs:appinfo source="@version">1.2</xs:appinfo>
        <xs:appinfo source="@see">{@link /FundsXML4/ControlData}</xs:appinfo>
        <xs:appinfo source="@see">{@link TransactionType}</xs:appinfo>
        <xs:appinfo source="@deprecated">Use {@link /FundsXML4/NewTransaction} instead.</xs:appinfo>
        <xs:appinfo source="@markdown">true</xs:appinfo>
        <xs:appinfo>
            <fxt:exampleValues xmlns:fxt="http://freexmltoolkit.org/xml-schema-extensions">
                <fxt:example value="TRX-0815"/>
                <fxt:example value="TRX-0816"/>
            </fxt:exampleValues>
        </xs:appinfo>
    </xs:annotation>
</xs:element>
```

!!! note "`{@link}` resolution"
    The target may be an absolute XPath (`/FundsXML4/ControlData` - synthetic `CHOICE_n` /
    `SEQUENCE_n` path segments are stripped before matching) or a plain element name (first match
    wins). A target that cannot be resolved degrades to plain code text instead of failing the
    documentation run.

## `@markdown` - Per-Node Markdown Rendering {#markdown}

`xs:documentation` content can be rendered as Markdown. The documentation generator offers three
choices under **Markdown rendering**:

| Choice | Effect |
|--------|--------|
| **All documentation** *(default)* | Every node's documentation goes through the Markdown renderer, unless that node itself says `@markdown = false`. |
| **Per node (`@markdown`)** | Only nodes whose annotation says `@markdown = true` are rendered; every other node - including nodes that say nothing - stays plain text. |
| **Off** | Nothing is rendered, whatever the nodes say. |

```xml
<xs:element name="Transaction">
    <xs:annotation>
        <xs:documentation>A **single** financial transaction.</xs:documentation>
        <xs:appinfo source="@markdown">true</xs:appinfo>
    </xs:annotation>
</xs:element>
```

A node's own `@markdown` always beats the chosen mode; only **Off** is absolute. The same rule
applies to the documentation of named types, attributes and enumerations on the generated type
pages, each of which decides from its own annotation.

In the Inspector the tag is a three-state **Markdown** choice: *Not set* (no tag is written),
*Markdown* (`true`) and *Plain text* (`false`). Like the other tags it is read from the element's
own annotation and from the annotation of its resolved type - a type-level `@markdown` therefore
applies to every element using that type unless the element states its own.

- Documentation that is *not* rendered as Markdown is emitted literally: `**bold**` stays visible
  as typed instead of being formatted. Write Markdown (or turn the node's Markdown on) if the text
  is meant to be formatted.
- Unrendered text is HTML-escaped wherever it is written into the generated HTML, so markup
  written straight into `xs:documentation` shows as the tags themselves rather than being applied.
- PDF, Word and the Excel data dictionary are plain-text formats: rendered documentation is
  flattened for them, with paragraphs, list items and headings kept on separate lines and HTML
  entities decoded. Only the HTML output shows the formatting itself.
- `@markdown` is a rendering hint, not documentation: it never appears in the "Additional
  Information" section of a generated page.

## `fxt:sourceFile` - Origin Tracking When Flattening {#sourcefile}

This is the toolkit's own `xs:appinfo` extension, in the namespace
`http://freexmltoolkit.org/schema/flattening` (prefix `fxt`). It answers a question that
flattening otherwise destroys: *which of the included files did this component come from?*

```xml
<xs:complexType name="PersonType">
    <xs:annotation>
        <xs:appinfo>
            <fxt:sourceFile xmlns:fxt="http://freexmltoolkit.org/schema/flattening">types.xsd</fxt:sourceFile>
        </xs:appinfo>
    </xs:annotation>
    ...
</xs:complexType>
```

- **Written** by **Flatten Schema…** (Schema activity → TOOLS) when **Track source files
  (xs:appinfo)** is ticked (off by default). Only global components get the marker - elements,
  complex and simple types, groups, attribute groups and attributes. The markers are added after
  the other reductions, so they survive *Remove annotations*, and a marker from an earlier flatten
  is replaced rather than duplicated. The schema must be saved: unsaved content resolves no
  includes and is therefore never marked.
- **Read and kept** everywhere else: the appinfo statistics of the
  [Schema Analysis](xsd-tools.md) count it as an `@sourceFile` tag, and the Inspector never shows
  it but carries it over untouched, so editing `@since` on a flattened component does not lose its
  origin marker. Saving writes it back as a clean `fxt:sourceFile` element.

!!! tip "Origin of nodes in an unflattened schema"
    The per-file node counts in the Schema Analysis statistics and the file column of its quality
    issues work on any multi-file schema, flattened or not - they do not need this marker.
    `fxt:sourceFile` is the form of origin information that survives writing a flattened schema to
    disk and opening it again.
