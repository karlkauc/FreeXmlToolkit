# DTD and RELAX NG

An XML document can be validated against a **DTD** or a **RELAX NG** schema — XML syntax (`.rng`)
or compact syntax (`.rnc`) — the same way as against an XSD: **F8**, the Validation panel, a batch
run or the command line. This page shows how the schema is found and what you get. The complete
rules are in [Schema support](../../reference/schema-support.md#dtd-and-relax-ng).

## Before you start

- An XML document that names its DTD or RELAX NG schema, or a `.dtd`, `.rng` or `.rnc` file to
  bind to it.

## A document that names its schema

Nothing needs to be set up when the document says which schema it follows. Save these two files
into one folder.

`note.dtd`:

```dtd
<!ELEMENT note (to, body)>
<!ATTLIST note priority (low | high) "low">
<!ELEMENT to (#PCDATA)>
<!ELEMENT body (#PCDATA)>
```

`note.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE note SYSTEM "note.dtd">
<note priority="urgent">
  <to>Ada</to>
</note>
```

Open `note.xml`. The schema indicator in the status bar reads **DTD: note.dtd**. Press **F8**: the
Problems panel lists two errors with the source **DTD** — `urgent` is not one of the allowed
values, and the content of `note` is incomplete.

The same with RELAX NG. `note.rnc`:

```rnc
element note {
  attribute priority { "low" | "high" }?,
  element to { text },
  element body { text }
}
```

and, in the document, an `xml-model` processing instruction instead of the DOCTYPE:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<?xml-model href="note.rnc" type="application/relax-ng-compact-syntax"?>
<note priority="urgent">
  <to>Ada</to>
</note>
```

The status bar reads **RELAX NG: note.rnc**, and the problems carry the source **RELAX NG**.

### What is recognised

The declaration must stand before the root element.

| The document has | It is validated against |
|---|---|
| `<!DOCTYPE note SYSTEM "note.dtd">` or `PUBLIC "…" "…"` | that DTD, and the internal subset if there is one |
| `<!DOCTYPE note [ … ]>` (only an internal subset) | the internal subset |
| `<?xml-model href="note.rng" schematypens="http://relaxng.org/ns/structure/1.0"?>` | that RELAX NG schema |
| `<?xml-model href="note.rnc" type="application/relax-ng-compact-syntax"?>` | that compact-syntax schema |

An `xml-model` instruction whose `href` ends in `.rng`, `.rnc` or `.dtd` is recognised by the
extension alone. A relative address is resolved against the document's folder.

An XSD wins: a document with `xsi:schemaLocation` or `xsi:noNamespaceSchemaLocation` is validated
against that XSD, and the DOCTYPE or `xml-model` is used only when there is none.

## Bind a schema yourself

For a document that names no schema, bind the `.dtd`, `.rng` or `.rnc` file like an XSD:

- click the **schema indicator** in the status bar (**No XSD**) and choose the file, or
- open the **Validation** panel and click **Change** in the XSD row, or drop the file onto that
  row.

The file chooser lists `.xsd`, `.dtd`, `.rng` and `.rnc` files. The status bar then reads
**DTD: name** or **RELAX NG: name**.

A document's own DOCTYPE wins over a bound DTD, because it may carry an internal subset.

In a [project](../projects.md), a schema binding can name a DTD or a RELAX NG schema for a file
pattern in the same way as an XSD.

## What you get

- **Validation** is exact: Xerces checks DTDs, Jing checks RELAX NG. Errors appear in the
  [Problems panel](problems.md) with line and column, like XSD errors.
- **Auto-completion**, the grid's entry helpers and the Properties inspector work too. They use an
  XML Schema that is translated from the DTD or RELAX NG schema in the background (with Trang).
  Where RELAX NG says more than an XSD can express, the suggestions are an approximation;
  validation is not affected.
- **Batch validation** and **Validate Project** use the declared or bound schema for every file.

## What is not there

- No graphical editor, diagram, Overview or type editor for DTDs and RELAX NG schemas — those are
  for XSD.
- No documentation generator for a DTD or RELAX NG schema.
- The translated XML Schema is read-only help for editing: the Properties inspector shows the
  declarations, but cannot change them.

## Where DTDs come from

A DTD is looked up in this order:

1. the XML catalogs and the [Schema Library](../design-schemas/schema-library.md), by public and
   system identifier — the way to use DocBook, XHTML or other standard DTDs without internet access;
2. local files, relative to the document;
3. for `http` and `https` addresses only: the schema cache, which downloads the DTD once, through
   the proxy settings.

A DTD or RELAX NG schema that cannot be loaded does not make the document look invalid. A warning
says that it is not available, and the document is checked for well-formedness only.

## Security

- External entities (`<!ENTITY x SYSTEM "…">`) are never read, and entity expansion is limited.
- DTDs are loaded only from the places listed above.
- **Settings → Network & Security → Validate documents against their DTD** switches DTD
  validation off. A DOCTYPE is then only checked for well-formedness and no DTD is loaded.
  RELAX NG is not affected.
- Administrators can lock this with `security.dtd.allowed=false`, see
  [Policies](../../administer/policies.md).

## On the command line

```bash
fxt-cli validate note.xml                    # uses the DOCTYPE or xml-model of each file
fxt-cli validate -x note.rnc notes/*.xml     # one DTD or RELAX NG schema for all files
```

For `note.xml` from above:

```text
note.xml:3:25: error: [DTD] Attribute "priority" with value "urgent" must have a value from the list "low high ".
note.xml:5:8: error: [DTD] The content of element type "note" is incomplete, it must match "(to,body)".
1 file, 1 invalid, 2 error(s), 0 warning(s)
```

`-x` (also `--xsd`, `--schema`) takes a `.xsd`, `.dtd`, `.rng` or `.rnc` file. See
[fxt-cli validate](../../automate/cli-validate.md#validate).

## Next steps

- [Working with problems](problems.md)
- [Schema Library](../design-schemas/schema-library.md) - map public identifiers to local DTDs
- [Schema support](../../reference/schema-support.md#dtd-and-relax-ng) - the reference
