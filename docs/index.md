# FreeXmlToolkit

**A Universal Desktop Toolkit for XML Professionals and Developers**

---

## Overview

FreeXmlToolkit is a powerful, cross-platform desktop application built with JavaFX, designed to streamline common
XML-related tasks. It provides an integrated environment for editing, validating, transforming, and securing XML
documents. Whether you are a developer working with complex schemas, a data analyst querying XML sources, or a document
specialist generating PDFs, this toolkit offers a comprehensive set of features to boost your productivity.

The application leverages the Language Server Protocol (LSP) with the LemMinX XML server to provide advanced editor
features like real-time diagnostics, code completion, and hover information.

## Key Features

The toolkit is organized into several modules, each targeting a specific XML technology:

### 📝 **Powerful XML Editor**

- **Syntax Highlighting:** Rich, customizable highlighting for XML syntax.
- **Real-time Validation & Diagnostics:** Instant feedback, error checking, and warnings powered by an integrated LSP
  server.
- **Code Formatting:** "Pretty Print" your XML documents with a single click to improve readability.
- **Minify XML:** Condense XML into a single line for compact storage or transmission.
- **Large File Support:** Efficiently handles large XML files.

### 🧪 **XPath & XQuery Tester**

- Execute XPath 2.0 expressions and XQuery 1.0 queries directly against your open XML documents.
- View the results instantly within the editor, perfect for testing and development.

### ✅ **XSD Validator**

- Validate your XML documents against one or more XSD schemas.
- The application can automatically detect `xsi:schemaLocation` attributes or allow you to specify schemas manually.
- Get detailed error reports for any validation issues.

### ✒️ **XML Digital Signature**

A complete workflow for securing your XML documents:

1. **Create Certificates:** Generate self-signed X.509 certificates and a secure Java Keystore (`.jks`) to store them.
2. **Sign Documents:** Apply an enveloped XML Digital Signature (XMLDSig) to your documents using the created
   certificate.
3. **Validate Signatures:** Verify the integrity and authenticity of signed XML files to ensure they have not been
   tampered with.

### 🔄 **XSLT Processor**

- Apply XSLT 1.0, 2.0, or 3.0 stylesheets to your XML documents.
- Instantly see the transformation result in a new editor tab.

### 📄 **Apache FOP Processor**

- Generate professional-quality PDF documents from XML data.
- Apply XSL-FO (XSL Formatting Objects) stylesheets to your XML files to define the PDF layout and styling.

### 📖 **XSD Documentation Generator**

- Automatically create user-friendly HTML documentation from your XSD schema files.
- (Future Feature) Includes graphical diagrams of the schema structure for better visualization.

## Technology Stack

- **Platform:** Java 17+ & JavaFX
- **Build System:** Gradle
- **XML Editor:** RichTextFX
- **Language Server:** Eclipse LemMinX for XML LSP features
- **Cryptography:** Bouncy Castle for certificate generation and signature handling
- **PDF Generation:** Apache FOP
- **Icons:** Ikonli

## Getting Started

To run the application, you can build a runnable JAR file using Maven:

## License

This project is licensed under the Apache License 2.0. Please see the LICENSE file for more details.