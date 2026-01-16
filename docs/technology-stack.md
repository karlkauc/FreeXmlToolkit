# Technology Stack

FreeXmlToolkit is built using modern Java technologies and libraries to provide a robust, cross-platform XML and JSON processing environment.

> **Last Updated:** January 2026 | **Version:** 1.2.3

## Core Technologies

### Java Platform
- **Java Vers[template-management.md](template-management.md)ion:** Java 25 with preview features enabled
- **Build System:** Gradle 8.x with Kotlin DSL
- **Application Framework:** JavaFX 24.0.1 (included in Liberica Full JDK)

### UI Framework
- **JavaFX:** 24.0.1 for cross-platform desktop GUI (bundled with Liberica Full JDK)
- **FXML:** Declarative UI layout with MVC architecture
- **AtlantaFX:** 2.1.0 - Modern theme library for enhanced styling
- **RichTextFX:** 0.11.7 - Advanced text editing component with syntax highlighting
- **ControlsFX:** 11.2.3 - Extended JavaFX controls

## Key Libraries

### XML Processing
- **Saxon HE:** 12.9 - XSLT 3.0, XPath 3.1 and XQuery processing engine
- **Jakarta XML Bind API:** 4.0.4 - XML binding and marshalling
- **Apache Xerces:** 2.12.2 (exist-db fork) - XSD 1.1 validation with assertions support

### JSON Processing
- **Gson:** 2.13.2 - JSON parsing and serialization
- **JSONPath:** 2.10.0 (Jayway) - JSONPath query language support
- **JSON Schema Validator:** 1.5.3 (NetworkNT) - JSON Schema validation (Draft 4-2020-12)

### PDF Generation
- **Apache FOP:** 2.11 - XSL-FO to PDF transformation
- **Apache PDFBox:** 3.0.6 - PDF manipulation
- **Apache Batik:** 1.19 - SVG processing and rendering

### Digital Signatures
- **Apache Santuario:** 4.0.4 - XML Security (XML-DSig)
- **BouncyCastle:** 1.83 - Cryptographic operations for XML digital signatures
- **Java Cryptography Architecture (JCA):** Native Java crypto support

### Office Integration
- **Apache POI:** 5.4.1 - Excel export for validation results

### Utilities
- **Apache Commons Lang3:** 3.20.0 - String manipulation and utilities
- **Apache Commons IO:** 2.21.0 - File and IO utilities
- **Apache Commons Text:** 1.15.0 - Text algorithms
- **Gson:** 2.13.2 - JSON processing

### Testing Framework
- **JUnit 5:** Unit and integration testing
- **TestFX:** 4.0.18 - JavaFX application testing framework
- **Mockito:** Mocking framework for unit tests

### Logging
- **Log4j2:** 2.24.1 - Comprehensive logging framework
- **SLF4J:** Logging facade for library compatibility

### UI Enhancements
- **Ikonli:** 12.4.0 - Icon packs (Bootstrap, FontAwesome, Feather, Win10)
- **CSSFX:** 11.5.1 - CSS hot-reloading for development
- **Thymeleaf:** 3.1.3 - Template engine for documentation generation

## Architecture Components

### Model-View-Controller (MVC)
- **Controllers:** JavaFX controllers handling UI logic and user interactions
- **Services:** Business logic layer for XML operations
- **Domain Models:** Data transfer objects and entity representations
- **FXML Views:** Declarative UI definitions

### XSD Editor V2 Architecture (MVVM Variant)
The XSD Editor V2 uses a sophisticated MVVM variant:
- **Model Layer:** Pure XsdNode tree with PropertyChangeSupport
- **Command Pattern:** All editing operations for undo/redo (24 commands)
- **Observable Properties:** Reactive UI updates without tight coupling
- **Incremental Rendering:** Only changed nodes re-render

### Core Services

#### XmlService
- XML parsing, validation, and transformation
- XPath and XQuery execution with context-aware autocomplete
- Schema validation (XSD 1.0 and 1.1)
- Document manipulation operations

#### XsdDocumentationService
- Schema analysis and documentation generation
- HTML/SVG documentation output
- Technical annotation processing (XsdDocInfo)
- Schema flattening and sample data generation

#### SchematronService
- Schematron rule compilation and validation
- Business rule processing
- Integration with Saxon XSLT engine

#### SignatureService
- XML digital signature creation and validation
- Certificate management and generation
- Cryptographic operations using BouncyCastle

### IntelliSense System
```
org.fxt.freexmltoolkit.controls.intellisense/
├── XmlIntelliSenseEngine     # Main orchestrator
├── XsdIntegrationAdapter     # XSD-based suggestions
├── CompletionCache           # Performance caching
├── CompletionContext         # Context-aware completion
└── AttributeValueHelper      # Type-aware attribute editing

org.fxt.freexmltoolkit.controls.v2.editor.intellisense/
├── IntelliSenseEngine        # V2 main engine
├── ContextAnalyzer           # XPath context detection
├── XsdCompletionProvider     # XSD-based completions
└── IntelliSensePopup         # Completion UI
```

Key Features:
- **XPath-aware enumeration lookup:** Correctly identifies enumeration values based on full XPath context
- **Context-sensitive suggestions:** Different suggestions for elements, attributes, and text content
- **XSD constraint awareness:** Shows only valid options based on schema

### Template Engine
```
org.fxt.freexmltoolkit.service/
├── TemplateEngine            # Template processing
├── SchemaGenerationEngine    # Schema-based generation
├── XPathSnippetRepository    # XPath expression library
└── TransformationProfile     # XSLT transformation profiles
```

### Thread Pool Architecture
Centralized thread management with 5 specialized pools:
- **CPU Pool:** CPU-intensive operations (XSLT, validation)
- **IO Pool:** File operations, network requests
- **UI Pool:** JavaFX Application Thread operations
- **Scheduled Pool:** Periodic tasks (memory monitoring)
- **Single Pool:** Serial task execution

## Build Configuration

### Gradle Features
- **Java Toolchain:** Configured for Java 25
- **Native Packaging:** Cross-platform executable generation (jpackage)
- **Dependency Updates:** Automated dependency update checking
- **Test Heap:** 16GB max configured for large schema tests

### Repository Configuration
```kotlin
repositories {
    mavenCentral()
    gradlePluginPortal()
}
```

### Key Dependencies
```kotlin
dependencies {
    // XML Processing
    implementation("net.sf.saxon:Saxon-HE:12.9")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.4")
    implementation("org.exist-db.thirdparty.xerces:xercesImpl:2.12.2")

    // PDF Generation
    implementation("org.apache.xmlgraphics:fop:2.11")

    // UI Components
    implementation("org.fxmisc.richtext:richtextfx:0.11.7")
    implementation("io.github.mkpaz:atlantafx-base:2.1.0")
    implementation("org.controlsfx:controlsfx:11.2.3")

    // Security
    implementation("org.apache.santuario:xmlsec:4.0.4")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.x")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
}
```

## Performance Optimizations

### Memory Management
- **ExecutorService:** Centralized thread pool for heavy operations
- **Memory Monitoring:** Built-in memory usage tracking with configurable thresholds
- **Caching Systems:** Intelligent caching for schemas, templates, and completion data
- **Lazy Loading:** On-demand loading of resources and components

### Processing Optimizations
- **Saxon HE Configuration:** Optimized XSLT 3.0 and XQuery processing
- **Document Streaming:** Memory-efficient XML processing for large files
- **Background Processing:** Non-blocking operations to maintain UI responsiveness
- **Incremental Updates:** Efficient handling of document changes

## Cross-Platform Support

### Native Packaging
- **jpackage:** Java native packaging tool for platform-specific installers
- **JLink:** Custom runtime images for reduced distribution size
- **Platform-Specific Builds:** Windows (.exe), macOS (.dmg), and Linux (AppImage)

### JavaFX Runtime
JavaFX 24.0.1 is bundled with Liberica Full JDK, providing:
- Cross-platform GUI consistency
- Hardware-accelerated graphics
- Web content rendering (WebView)
- Swing interoperability

## Security Features

For detailed information about security protections, see the [Security Features](SECURITY.md) documentation.

### Digital Signatures
- **XML-DSig:** W3C XML Digital Signature support
- **Certificate Validation:** X.509 certificate chain validation
- **Cryptographic Algorithms:** Support for RSA, DSA, and ECDSA signatures
- **Key Generation:** RSA 2048/4096-bit key pair generation

### Input Validation & Attack Prevention
- **XXE Protection:** Secure XML parser configuration prevents external entity attacks
- **SSRF Protection:** URL validation blocks access to internal networks and metadata endpoints
- **XSLT Extension Security:** Java extensions disabled by default to prevent code execution
- **Path Traversal Prevention:** File access restricted to prevent directory escape
- **XPath Injection Prevention:** Proper parameter escaping in XPath queries
- **Schema Validation:** Strict validation against XSD 1.0/1.1 schemas

## Development Environment

### Recommended IDE Setup
- **IntelliJ IDEA:** Primary development environment
- **JavaFX Scene Builder:** Visual FXML design tool
- **Git:** Version control system

### Code Quality
- **Static Analysis:** Built-in code analysis and inspection
- **Unit Testing:** Comprehensive test coverage with JUnit 5
- **UI Testing:** TestFX for JavaFX component testing
- **Documentation:** Javadoc generation for API documentation

---

## Navigation

| Previous | Home | Next |
|----------|------|------|
| [Templates](template-management.md) | [Home](index.md) | [Security](SECURITY.md) |

**All Pages:** [XML Editor](xml-editor.md) | [XML Features](xml-editor-features.md) | [XSD Tools](xsd-tools.md) | [XSD Validation](xsd-validation.md) | [XSLT](xslt-viewer.md) | [FOP/PDF](pdf-generator.md) | [Signatures](digital-signatures.md) | [IntelliSense](context-sensitive-intellisense.md) | [Schematron](schematron-support.md) | [Favorites](favorites-system.md) | [Templates](template-management.md) | [Tech Stack](technology-stack.md) | [Security](SECURITY.md) | [Licenses](licenses.md)
