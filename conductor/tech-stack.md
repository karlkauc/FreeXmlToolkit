# Technology Stack - FreeXmlToolkit

## Core Development
*   **Language:** Java 25 (utilizing latest features and toolchain support)
*   **GUI Framework:** JavaFX 24 (using FXML for layout and CSS for modern styling)
*   **Build & Dependency Management:** Gradle (Kotlin DSL)

## XML & Data Processing
*   **Transformations & Queries:** Saxon-HE 12.9 (XSLT 3.0 and XPath 3.1 support)
*   **Schema Validation:** Xerces-J 2.12.2 (specially patched for XSD 1.1 and assertions support)
*   **Schematron:** PH-Schematron (ISO Schematron support via XSLT and Pure modes)
*   **PDF Generation:** Apache FOP 2.11 (XML to PDF via XSL-FO)
*   **Digital Signatures:** Apache Santuario (XML Security) and BouncyCastle
*   **Office Integration:** Apache POI 5.4.1 (Excel/Spreadsheet conversion)

## User Interface & Experience
*   **Theming:** AtlantaFX 2.1.0 (modern CSS themes)
*   **Advanced Controls:** ControlsFX 11.2.2 and RichTextFX 0.11.6 (for high-performance code editing)
*   **Icons:** Ikonli (Bootstrap, FontAwesome, etc.)
*   **Documentation Templates:** Thymeleaf 3.1.3 and Flexmark (Markdown to HTML)

## Infrastructure & Testing
*   **Testing Framework:** JUnit 5 (JUnit Jupiter)
*   **Mocking:** Mockito 5.20.0
*   **GUI Testing:** TestFX 4.0.18 (with Monocle for headless CI/CD execution)
*   **Logging:** Log4j 2 (API, Core, and SLF4J implementation)
*   **Serialization:** Google GSON 2.13.2
*   **Utilities:** Apache Commons (Lang3, IO, Text, Validator)
