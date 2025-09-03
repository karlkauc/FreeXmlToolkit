# FreeXmlToolkit Documentation

## Introduction

Welcome to the official documentation for the Free XML Toolkit (Version 1.0.0).

FreeXmlToolkit is a powerful, cross-platform desktop application built with JavaFX 21, designed to streamline common XML-related tasks. It provides an integrated environment for editing, validating, transforming, and securing XML documents with advanced features like IntelliSense, template management, and Schematron validation. Whether you are a developer working with complex schemas, a data analyst querying XML sources, or a document specialist generating PDFs, this toolkit offers a comprehensive set of features to boost your productivity.

This guide provides a comprehensive overview of the application's features to help you get the most out of them.

![Screenshot of the main application window](img/app.png)

## Installation

FreeXmlToolkit is designed to be easy to install and use:

- **Windows**: No administrator rights required for installation - standard installation process
- **macOS**: Standard installation process, no special permissions needed
- **Linux**: Standard installation process, no system-level privileges required

The application can be installed in any directory on your system.

## Features

Click on any feature below to learn more about it:

-   **[XML Editor](xml-controller.md)**: A powerful editor for viewing, editing, and formatting XML files with advanced syntax highlighting, real-time validation, IntelliSense auto-completion, and dual-mode editing (text/grid).

-   **[XSD Tools](xsd-controller.md)**: A comprehensive suite of tools for working with XML Schemas (XSD), including a graphical viewer, documentation generator with technical annotations support, sample XML generator, and schema flattening capabilities.

-   **[XSD Validation](xsd-validation-controller.md)**: A dedicated tool for validating XML files against XSD schemas with detailed error reporting and continuous validation feedback.

-   **[XSLT Transformation](xslt-controller.md)**: Perform XSLT 3.0 transformations using Saxon HE 12.8 to convert your XML documents into multiple output formats with advanced template support.

-   **[PDF Generator (FOP)](fop-controller.md)**: Create professional-quality PDF documents from your XML data using XSL-FO stylesheets with Apache FOP 2.11.

- **[XML Digital Signature](signature-controller.md)**: A complete workflow for creating and validating XML digital signatures with BouncyCastle cryptographic support, featuring both Basic Mode for common use cases and Expert Mode for advanced XML-DSig operations with full W3C compliance.

- **[Context-Sensitive IntelliSense](context-sensitive-intellisense.md)**: Advanced auto-completion that shows only relevant child elements based on your current XML context and loaded XSD schema.

- **[Schematron Support](schematron-support.md)**: Comprehensive Schematron validation with visual rule builder, testing capabilities, and integration with XML editor for business rule validation.

- **[Schema Support](schema-support.md)**: Comprehensive support for XSD schema analysis, validation, and documentation generation with advanced features like namespace handling and type resolution.

- **[Favorites System](favorites-system.md)**: Save and organize frequently used files across all editors with smart categorization, custom descriptions, and quick access features.

- **[Template Management](template-management.md)**: Advanced template system for XML snippets, XPath expressions, and reusable code patterns with parameter substitution and validation rules.

## Technical Documentation

- **[Technology Stack](technology-stack.md)**: Comprehensive overview of Java 24, JavaFX 21, Saxon HE 12.8, and all libraries used in the application architecture.

---

[Next: XML Editor](xml-controller.md) | [Favorites System](favorites-system.md) | [Technology Stack](technology-stack.md) | [Licenses](licenses.md)
