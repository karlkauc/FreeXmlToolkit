# Comparison with Other XML Editors

This page compares the features of FreeXmlToolkit with three other XML editors, so you can
check whether it covers what you need. It lists facts only and shows where the other editors
offer more.

**As of November 2026.** Compared versions:

| Product | Version | Publisher |
|---------|---------|-----------|
| FreeXmlToolkit | 2.10 | Karl Kauc (open source) |
| Oxygen XML Editor | 28.1 (March 2026) | Syncro Soft SRL |
| Altova XMLSpy | 2027 (October 2026) | Altova GmbH |
| XMLBlueprint | 22 (June 2026) | Monkfish XML Software |

The information about the other products comes from their publishers' websites and manuals
(see [Sources](#sources)). Products change. If you find an error or an outdated entry,
please [open an issue](https://github.com/karlkauc/FreeXmlToolkit/issues) and it will be corrected.

**Legend:**

- **✓** supported
- **◐** partly supported (see the note in the cell)
- **✗** not supported
- **–** not found in the publisher's documentation at the time of writing (this does not mean
  the feature is missing)

## License and Platforms

| | FreeXmlToolkit | Oxygen XML Editor | XMLSpy | XMLBlueprint |
|---|---|---|---|---|
| License | Free, open source (Apache 2.0) | Commercial: perpetual or subscription ([prices](https://www.oxygenxml.com/buy.html)) | Commercial ([prices](https://shop.altova.com/XMLSpy)) | Commercial, free academic licence ([prices](https://www.xmlblueprint.com/buy.htm)) |
| Windows | ✓ | ✓ | ✓ | ✓ |
| macOS | ✓ | ✓ | ◐ via Parallels, according to the publisher | – |
| Linux | ✓ | ✓ | – | – |

## XML Editing

| | FreeXmlToolkit | Oxygen XML Editor | XMLSpy | XMLBlueprint |
|---|---|---|---|---|
| Schema-aware auto-completion | ✓ | ✓ | ✓ | ✓ |
| Grid / table view | ✓ | ✓ | ✓ | – (outline view) |
| Large files | ◐ read-only viewer for files of any size; editing limited by memory | ✓ special loading modes for large and huge files | ✓ optimized for very large files | ✓ "up to 1 GB and larger" |

## Schemas and Validation

| | FreeXmlToolkit | Oxygen XML Editor | XMLSpy | XMLBlueprint |
|---|---|---|---|---|
| Graphical XSD editor | ✓ | ✓ | ✓ | – (schema viewer) |
| XSD 1.1 | ✓ | ✓ | ✓ | – |
| DTD validation | ✓ | ✓ | ✓ | ✓ |
| RELAX NG validation | ✓ | ✓ | – | ✓ |
| Schematron validation | ✓ | ✓ | – | ✓ |
| Schematron Quick Fixes (SQF) | ✓ | ✓ | – | – |
| Sample XML from a schema | ✓ | ✓ | ✓ | ✓ |
| Schema documentation | ✓ HTML, Word, PDF | ✓ | ✓ | – |

## JSON and YAML

| | FreeXmlToolkit | Oxygen XML Editor | XMLSpy | XMLBlueprint |
|---|---|---|---|---|
| JSON editor | ✓ | ✓ | ✓ | ◐ format and validate |
| JSON Schema | ✓ | ✓ | ✓ | – |
| YAML | ◐ conversion | ✓ validation | ✓ editor, validation | – |
| XML ↔ JSON conversion | ✓ | ✓ | ✓ | – |

## Transformation and Debugging

| | FreeXmlToolkit | Oxygen XML Editor | XMLSpy | XMLBlueprint |
|---|---|---|---|---|
| XSLT 3.0 | ✓ Saxon HE | ✓ Saxon HE / PE / EE | ✓ | ✓ Saxon HE |
| XQuery | ✓ 3.1 | ✓ | ✓ 3.1 | – |
| XSLT debugger | ✓ | ✓ | ✓ | – |
| XQuery debugger | ✓ | ✓ | ✓ | – |
| Profiler | ✓ XSLT, XQuery | ✓ XSLT, XQuery | ✓ XSLT, XQuery | – |
| XProc 3 | ✓ run pipelines | ✓ | – | ◐ editing and validation |
| XSpec tests | ✓ | ✓ | – | – |
| XSL-FO to PDF | ✓ Apache FOP | ✓ | ✓ | – |

## Other Tools

| | FreeXmlToolkit | Oxygen XML Editor | XMLSpy | XMLBlueprint |
|---|---|---|---|---|
| Compare files (XML-aware) | ✓ | ✓ | ✓ | – |
| Compare folders | ✓ | ✓ | ✓ | – |
| Three-way merge | ✓ | ✓ | ✓ | – |
| XML digital signatures | ✓ sign and verify | ✓ sign | ✓ sign and verify | – |
| XML encryption (W3C 1.1) | ✓ encrypt, decrypt, inspect | – | – | – |
| Projects / transformation scenarios | ✓ | ✓ | ✓ | ◐ |
| Command line | ✓ included (`fxt-cli`) | ◐ separate product (Oxygen Scripting) | ◐ separate product (RaptorXML Server) for automation | ✓ |
| Plugin / extension API | ✓ Java plugins | ✓ | ◐ scripting, OLE / ActiveX | – |

## Where the Others Offer More

FreeXmlToolkit focuses on XML, XSD, XSLT and XQuery development. It edits DTD and RELAX NG
schemas as text only (with validation and auto-completion for documents that use them), and it
does not edit files larger than its memory limit (the [Large File Viewer](large-files.md) is
read-only). The other products cover areas that FreeXmlToolkit does not:

- **Oxygen XML Editor:**
    - authoring and publishing for DITA, DocBook and TEI;
    - a visual (CSS-based) author mode with change tracking;
    - connections to XML databases, content management systems and WebDAV;
    - an Eclipse plugin and a web-based editor (separate product);
    - an AI assistant (paid add-on).
- **XMLSpy:**
    - database connections;
    - code generation for Java, C# and C++;
    - a WSDL editor and a SOAP client;
    - an XBRL taxonomy editor;
    - Authentic view;
    - charts from XML data;
    - Visual Studio and Eclipse integration;
    - an AI chat (separate subscription).
- **XMLBlueprint:**
    - refactoring (rename or split elements, rename namespace prefixes);
    - editing files on FTPS, SFTP and WebDAV servers;
    - CSV and Excel to XML conversion;
    - a user interface in 21 languages.

## Sources

Publishers' pages, read in November 2026:

- **Oxygen XML Editor:**
    - [Product overview](https://www.oxygenxml.com/xml_editor.html)
    - [Feature matrix](https://www.oxygenxml.com/xml_editor/feature_matrix.html)
    - [What's new](https://www.oxygenxml.com/xml_editor/whats_new.html)
    - [Download and system requirements](https://www.oxygenxml.com/xml_editor/download_oxygenxml_editor.html)
    - [User guide](https://www.oxygenxml.com/doc/ug-editor/), including the pages on [debugging](https://www.oxygenxml.com/doc/ug-editor/topics/debugging.html), [profiling](https://www.oxygenxml.com/doc/ug-editor/topics/performance-profiling.html), [XSpec](https://www.oxygenxml.com/doc/ug-editor/topics/schematron-unit-test-xspec.html), [SQF](https://www.oxygenxml.com/doc/ug-editor/topics/editing-schematron-quick-fixes.html) and [large documents](https://www.oxygenxml.com/doc/ug-editor/topics/editing-large-documents.html)
    - Feature pages: [XSD diagram](https://www.oxygenxml.com/xml_editor/xml_schema_diagram_editor.html), [XSD 1.1](https://www.oxygenxml.com/xml_editor/xml_schema_1_1_support.html), [YAML](https://www.oxygenxml.com/xml_editor/yaml_validation.html), [JSON converter](https://www.oxygenxml.com/xml_editor/json_converter.html), [signatures](https://www.oxygenxml.com/xml_editor/xml_signature.html), [change tracking](https://www.oxygenxml.com/xml_editor/wysiwyg_xml_editor_review_change_tracking.html)
    - [Shop](https://www.oxygenxml.com/buy.html)
- **XMLSpy:**
    - [Product overview](https://www.altova.com/xmlspy-xml-editor)
    - [Edition comparison](https://www.altova.com/xmlspy-xml-editor/editions)
    - [What's new](https://www.altova.com/xmlspy-xml-editor/whatsnew)
    - [Download](https://www.altova.com/xmlspy-xml-editor/download)
    - [XSLT debugger](https://www.altova.com/xmlspy-xml-editor/xslt-debugger)
    - [Manual](https://www.altova.com/manual/XMLSpy/spyenterprise/)
- **XMLBlueprint:**
    - [Home page](https://www.xmlblueprint.com/)
    - [What's new](https://www.xmlblueprint.com/what-is-new.htm)
    - [Download and system requirements](https://www.xmlblueprint.com/download-xml-editor.htm)
    - [User manual](https://www.xmlblueprint.com/user-manual.htm)
    - [XProc editor](https://www.xmlblueprint.com/xproc-editor.htm)
    - [Schematron editor](https://www.xmlblueprint.com/schematron-editor.htm)

FreeXmlToolkit: this documentation and the
[source code](https://github.com/karlkauc/FreeXmlToolkit).

## Trademarks

Oxygen and Oxygen XML Editor are trademarks of Syncro Soft SRL. Altova and XMLSpy are trademarks of
Altova GmbH. XMLBlueprint is a product of Monkfish XML Software. All other product names are
trademarks or registered trademarks of their respective owners. They are used here only to
identify the products. FreeXmlToolkit is not affiliated with, endorsed by or sponsored by any of
these companies.
