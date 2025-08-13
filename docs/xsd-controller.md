# XSD Tools

This part of the application provides a suite of tools for working with XML Schemas (XSD). These tools help you understand, document, and use XSD files effectively.

![Screenshot of XSD Controller](img/xsd-controller.png)

## 1. XSD Viewer

The XSD Viewer is the central feature for exploring and editing your schemas. It has two main views:

### Graphical View
- **Visual Tree:** Displays your XSD file as an interactive, hierarchical tree. This makes it easy to see the structure of your schema, including elements, types, and their relationships.
- **Documentation Editor:** You can directly add or edit the main documentation and technical Javadoc-style comments for the entire schema right from the graphical view. Your changes can be saved back to the XSD file.
- **Example Value Editor:** For specific elements in the tree, you can add and save example values. This is useful for documenting how the data should look.
- **Metadata Display:** Key information like the schema's target namespace and version is clearly displayed.

### Text View
- **Full Code Editor:** Provides a complete text editor for viewing and editing the raw source code of the XSD file.
- **Syntax Highlighting:** The code is colored to make it easy to read and identify different parts of the schema syntax.
- **Search and Replace:** A powerful find-and-replace feature is built-in, allowing you to quickly locate and change text within the XSD file.

## 2. Documentation Generator

This powerful feature automatically creates user-friendly HTML documentation from your XSD file.

- **Generate HTML Docs:** With a few clicks, you can produce a full website that documents every element and attribute in your schema.
- **Customization Options:**
    - Choose between different image formats (PNG or SVG) for the diagrams in the documentation.
    - Optionally render documentation written in Markdown for richer formatting.
- **Live Preview:** After generating the documentation, you can launch a live preview in a new tab directly within the application. This starts a local web server to display the HTML files.
- **Easy Access:** A button allows you to directly open the folder containing the generated documentation files.

### Adding Technical Documentation (XsdDocInfo)

In addition to the standard `<xs:documentation>` tag for user-friendly descriptions, you can embed structured, Javadoc-style technical information directly into your XSDs. This is done by adding multiple `<xs:appinfo>` tags within an `<xs:annotation>`. The Documentation Generator recognizes this information and displays it in a distinct, technical section of the generated HTML, separate from the main documentation.

This allows you to keep technical notes for developers alongside the general-purpose documentation.

**Supported Annotations**

The following annotations are supported inside the `source` attribute of an `<xs:appinfo>` tag:

-   `@since`: Specifies the version in which the element or feature was introduced.
-   `@see`: Provides a cross-reference to another element, type, or an external resource. Can be plain text or a structured link.
-   `@deprecated`: Marks an element as deprecated, optionally including a message about its replacement or removal timeline.
-   `{@link XPath}`: A special tag used within `@see` or `@deprecated` to create a clickable hyperlink to another element in the generated documentation. The XPath should be the absolute path from the root of the XML.

**Example**

Here is the correct way to structure your XSD to include this technical information. Each piece of metadata is placed in its own `<xs:appinfo>` tag using the `source` attribute.

```xml
<xs:element name="Transaction">
  <xs:annotation>
    <!-- This is the standard, user-facing documentation -->
    <xs:documentation>
      Represents a single financial transaction.
    </xs:documentation>

    <!-- This is the technical, developer-facing documentation -->
    <xs:appinfo source="@since 4.0.0"/>
    <xs:appinfo source="@see {@link /FundsXML4/ControlData/RelatedDocumentIDs/RelatedDocumentID}"/>
    <xs:appinfo source="@see Attention. See also other values."/>
    <xs:appinfo source="@deprecated do not use any more. use {@link /FundsXML4/AssetMasterData} and {@link /FundsXML4/AssetMasterData/AssetDetails} instead."/>

  </xs:annotation>
  <xs:complexType>
    <!-- ... element definition ... -->
  </xs:complexType>
</xs:element>
```

When the HTML documentation is generated, it will clearly display the "since," "see," and "deprecated" information in a formatted block. Any `{@link}` tags will be converted into clickable hyperlinks, providing valuable, interactive context for developers using the schema.

## 3. Sample XML Generator

This tool creates a sample XML file based on your XSD schema. This is very useful for testing or for providing an example of what a valid XML document should look like.

- **Generate Sample Data:** Creates an XML document that conforms to the rules of your selected XSD.
- **Control the Output:**
    - **Mandatory Only:** You can choose to generate an XML file that includes only the elements and attributes that are required by the schema.
    - **Max Occurrences:** You can set a limit on how many times a repeating element should be generated.
- **Instant Preview:** The generated XML is displayed in a text area with syntax highlighting.
- **Save the File:** You can save the generated sample XML to a file on your computer.

## 4. XSD Flattener

If you have a complex XSD that imports other XSD files, the flattener tool can combine them all into a single, self-contained XSD file.

- **Merge Schemas:** It resolves all `xs:include` and `xs:import` statements and merges the content into one file.
- **Simple Process:** You select your main XSD file, choose a destination for the new flattened file, and the tool handles the rest.
- **View the Result:** The content of the new, flattened XSD is displayed in a text editor after the process is complete.

---

[Previous: XML Editor](xml-controller.md) | [Home](index.md) | [Next: XSD Validation](xsd-validation-controller.md)
