# Technology Stack - FreeXmlToolkit

## Core Development
*   **Language:** Java 25 (utilizing latest features and toolchain support)
*   **GUI Framework:** JavaFX 25 (using FXML for layout and CSS for modern styling)
*   **Build & Dependency Management:** Gradle 9.x (Kotlin DSL)

## XML & Data Processing
*   **Transformations & Queries:** Saxon-HE 12.10 (XSLT 3.0, XPath 3.1 and XQuery 3.1; version pinned by XML Calabash 3)
*   **XProc:** XML Calabash 3.0.54 (XProc 3.0 pipelines)
*   **Schema Validation:** Xerces-J 2.12.2 (specially patched for XSD 1.1 and assertions support)
*   **Schematron:** ph-schematron 10.2.0 (ISO Schematron via SchXslt on Saxon, plus XSLT and Pure modes)
*   **PDF Generation:** Apache FOP 2.11 (XML to PDF via XSL-FO)
*   **Digital Signatures:** Apache Santuario (XML Security) 4.0.4 and BouncyCastle 1.86
*   **Office Integration:** Apache POI 5.5.1 (Excel/Spreadsheet conversion)

## User Interface & Experience
*   **Theming:** Project-owned `-fxt-*` design-token CSS theme (`css/design-tokens.css`, `css/unified-shell.css`; Light + Dark)
*   **Advanced Controls:** ControlsFX 11.2.5 and RichTextFX 0.11.7 (for high-performance code editing)
*   **Icons:** Project-owned `IconifyIcon` control backed by bundled Iconify Bootstrap Icons SVG data (`bi-*` names)
*   **Documentation Templates:** Thymeleaf 3.1.5 and Flexmark (Markdown to HTML)

## Infrastructure & Testing
*   **Testing Framework:** JUnit 6 (JUnit Jupiter, BOM 6.1.3)
*   **Mocking:** Mockito 5.24.0
*   **GUI Testing:** TestFX 4.0.18 (with Monocle for headless CI/CD execution)
*   **Logging:** Log4j 2.26.1 (API, Core, and SLF4J implementation)
*   **Serialization:** Google GSON 2.14.0
*   **Utilities:** Apache Commons (Lang3, IO, Text, Validator)
