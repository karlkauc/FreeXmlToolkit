# XSD Documentation Generator

## Overview

The XSD Documentation Generator is a powerful tool within the FreeXmlToolkit designed to transform complex, technical XML Schema Definition (XSD) files into clean, human-readable HTML documentation. This feature is essential for developers, architects, and business analysts who need to understand or explain the structure of an XML vocabulary without digging through raw XML code.

With a single click, you can create a fully navigable, self-contained website that clearly outlines every element, attribute, and type defined in your schema.

## Key Features

-   **Instant HTML Generation:** Convert any valid XSD file into a multi-page HTML documentation site.
-   **Clear and Navigable Structure:** The generated output is logically organized with a main index and separate pages for each component, making it easy to browse.
-   **Comprehensive Details:** The documentation automatically extracts and displays crucial information for each component, including:
    -   Data types (e.g., `xs:string`, `xs:int`)
    -   Occurrence constraints (`minOccurs`, `maxOccurs`)
    -   Default and fixed values
    -   Embedded annotations and developer comments (`xs:annotation`/`xs:documentation`)
-   **Javadoc-Style Annotations:** Enrich your schema with metadata using familiar tags like `@since`, `@deprecated`, and `@see` directly within the XSD for versioning, deprecation notices, and cross-references.
-   **Self-Contained and Portable:** The output is a standard set of HTML and CSS files. It has no external dependencies and can be viewed offline in any web browser, shared in a ZIP archive, or hosted on any web server.
-   **Graphical Schema Visualization:** The generator creates interactive SVG diagrams that visually represent the relationships between schema components, further enhancing clarity.

## How to Use

Generating documentation is a straightforward, three-step process:

1.  **Open Your Schema:** In FreeXmlToolkit, open the XSD file you wish to document via `File > Open...`.
2.  **Launch the Generator:** Navigate to the menu `Tools > XSD Documentation Generator`. This will open a new tab dedicated to the generation process.
3.  **Generate and View:** The tool automatically references the currently active XSD file. Click the **"Generate Documentation"** button. The toolkit will process the file and save the HTML output in a new subfolder in the same location as your XSD. A confirmation dialog will appear, allowing you to directly open the generated `index.html` in your default browser.

## Advanced Documentation with Javadoc-Style Tags

Beyond standard descriptions, the XSD Documentation Generator allows you to embed rich, structured metadata directly into your schema using a syntax inspired by Java's Javadoc. This allows you to capture versioning information, deprecation notices, and create navigable cross-references between different parts of your schema.

This metadata is added within the standard `xs:annotation` tag, using `xs:appinfo` elements.

### Supported Tags

#### @since
Use the `@since` tag to specify when an element, attribute, or type was introduced. This is invaluable for tracking the evolution of your schema.

**HTML Output:** This will be rendered in a distinct "Since" section on the element's detail page.

---

#### @deprecated
Use the `@deprecated` tag to mark components that should no longer be used. You can provide a detailed explanation, including migration advice.


**HTML Output:** The component will be clearly marked as deprecated, and the provided text will be displayed in a prominent warning box.

---

#### @see
Use the `@see` tag to create a cross-reference to another component in the schema. This helps users discover related elements and understand their context.


**HTML Output:** This creates a link in the "See Also" section, pointing to the detail page of the specified component.

---

#### {@link XPath}
The `{@link}` tag is an inline tag used *within* the content of other tags (like `@deprecated` or `@see`) to create a clickable hyperlink to another element. The target of the link must be a valid XPath to an element within the processed schema.

**HTML Output:** The `{@link /order/customer/id}` part will be transformed into a clickable link: `Use <a href="...">/order/customer/id</a> instead.`

### Complete Example

Here is how you can combine these tags on a single element:

```xml
<xs:annotation>
    <xs:appinfo source="@since 4.0.0"/>
    <xs:appinfo source="@see {@link /FundsXML4/ControlData/RelatedDocumentIDs/RelatedDocumentID}"/>
    <xs:appinfo source="@see Attention. See also other values."/>
    <xs:appinfo source="@deprecated do not use any more. use {@link /FundsXML4/AssetMasterData} and {@link /FundsXML4/AssetMasterData/AssetDetails} instead."/>
    <xs:documentation>**INITIAL**: This refers to the first time data is delivered. It includes all the necessary information to set up or populate a system or database.
        **AMEND**: This term is used when there are updates or changes to the existing data. An amendment might include corrections, additions, or modifications to the previously delivered data. It ensures that the data remains accurate and up-to-date.
        **DELETE**: This involves the removal of specific data from the system. It could be due to data being outdated, incorrect, or no longer needed. Deleting data helps in maintaining the relevance and efficiency of the database. DELETE requires a RelatedDocumentIDs that referes to the data delivery that should be deleted.
    </xs:documentation>
</xs:annotation>
```

## Example Workflow

Imagine you have an XSD file named `bookstore.xsd`.

1.  You open `bookstore.xsd` in the toolkit.
2.  You launch the XSD Documentation Generator.
3.  After clicking "Generate", a new folder named `bookstore-docs` is created.
4.  Inside this folder, you'll find an `index.html` file. Opening it reveals a professional-looking page listing all global elements like `book` and `author`. Clicking on `book` takes you to a detailed page describing its child elements (`title`, `publicationYear`), their data types, and any rules associated with them.

## Technical Details

The generator leverages a sophisticated, built-in XSLT 3.0 stylesheet. It treats the source XSD file as an XML document and applies this transformation to produce the final, styled HTML output. This standards-based approach ensures a reliable and accurate conversion for any valid schema.