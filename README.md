# FreeXmlToolkit

---


Toolkit for working with XML/XSD files.

* XML Texteditor
* XML Grid Editor
* XML/XSD Schema validation
* XSLT Transformation
* FOP(PDF) Transformation
* working with Signatures

Screenshot:
![app.png](docs%2Fapp.png)

# ✨ Key Features

## 📝 Powerful XML Editor

The heart of the toolkit is a modern, responsive editor designed for maximum productivity.

* Intelligent Syntax Highlighting: Clear and distinct coloring for XML, XSD, and XSL files to improve readability and structural recognition.
* Real-time Validation: Get instant feedback as you type. An integrated Language Server Protocol (LSP) server highlights errors and provides descriptive tooltips, helping you write valid XML from the start.
* Automatic Formatting: Clean up and beautify messy XML documents with a single click, applying consistent indentation and line breaks.


## 📚 Advanced XSD Documentation Generator

Transform complex XSD schemas into beautiful, human-readable, and interactive HTML documentation.

* Modern & Responsive Design: The documentation is styled with Tailwind CSS, ensuring a clean look that works on any device.
* Interactive SVG Diagrams: Automatically generates clear, visual diagrams for each element, showing its structure, child elements, and relationships with intuitive symbols for <sequence> and <choice>.
* Detailed Information: Each element includes its data type, cardinality (0..*, 1..1, etc.), annotations, and the full XPath.
* One-Click XPath Copy: A convenient button to copy the XPath to the clipboard is available for every element, saving you time and effort.
* High-Performance Generation: Leverages Java's Virtual Threads to generate documentation for large schemas in parallel, significantly reducing wait times.

## ✅ Robust XSD Validator

Precisely validate your XML documents against one or more XSD schemas.

* Detailed Error Reporting: Quickly identify, locate, and understand validation errors with clear messages that include line and column numbers.
* Multi-Schema Support: Effortlessly handle complex scenarios where an XML file must conform to multiple schemas simultaneously.

 
## 🔍 Interactive XPath & XQuery Tester

Debug, test, and refine your queries in a live environment.

* Instant Results: Run XPath expressions or XQuery queries directly on your loaded XML file and see the results immediately.
* Highlighted Output: The matching nodes or query results are clearly highlighted in the editor for easy identification.

 
## 🎨 Live XSLT Viewer & Processor

Apply XSLT transformations and see the results without ever leaving the application.

* Instant Preview: Select an XML file and an XSLT stylesheet to instantly view the transformed output, whether it's HTML, another XML document, or plain text.
* Parameter Support: Easily pass parameters to your XSLT stylesheets to dynamically control the transformation logic.

## 📄 PDF Generation with Apache FOP™

Create high-quality, print-ready PDF documents directly from your XML data.

* XSL-FO Processing: Seamlessly transforms XML data into professional-looking PDFs using XSL-FO (Formatting Objects) stylesheets.
* Integrated Engine: Leverages the full power of the industry-standard Apache FOP™ engine.
 
## ✍️ XML Signature Tools

Ensure the integrity and authenticity of your XML data with built-in digital signature features.

* Sign Documents: Apply W3C standard XML Digital Signatures (XMLDSig) to your documents using a PFX/P12 certificate.
* Verify Signatures: Validate the signature of a received XML file to confirm it has not been tampered with and comes from a trusted source.

## 🎲 Intelligent Sample XML Generator

Quickly create valid and realistic sample XML files from any XSD schema.

* XMLSchema-Compliant Generation: The generated XML is guaranteed to be valid according to the source XSD.
* Realistic Data: Automatically populates elements with plausible, random data (dates, numbers, strings) that respects the schema's data types, patterns, and restrictions (e.g., minLength, maxLength).
* Perfect for Testing: Instantly create a variety of test cases for your applications without manual effort.

## Download

---

[Releases](https://github.com/karlkauc/FreeXmlToolkit/releases)

## System Requirements

---

* Windows (all current supported version)
* Mac (all current supported version)
* Linux (all current supported version)

## Running

--- 

Just download the latest release and unzip the achive.
For Windows Platform run "FreeXmlToolkit.exe" to start the application.

## Documentation

---

* [Wiki](https://github.com/karlkauc/FreeXmlToolkit/wiki)
* [Bugs / Features](https://github.com/karlkauc/FreeXmlToolkit/issues)

## Build from source

---

```
git clone https://github.com/karlkauc/FreeXmlToolkit.git FreeXmlToolkit  
cd FreeXmlToolkit  
./gradlew[.bat|.sh] run  
```

## 🙌 Contributing

Contributions are welcome! This project thrives on community input. If you have an idea, find a bug, or want to contribute code, please use the GitHub issue tracker.

* Report a Bug or Request a Feature: Check for existing issues first, then feel free to create a new one.
* Submit a Pull Request: For significant changes, please open an issue first to discuss what you would like to change.

## Documentation
For more detailed information, please visit the project Wiki.