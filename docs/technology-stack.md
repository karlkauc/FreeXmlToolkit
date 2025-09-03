# Technology Stack

FreeXmlToolkit is built using modern Java technologies and libraries to provide a robust, cross-platform XML processing environment.

## Core Technologies

### Java Platform
- **Java Version:** Java 24 with preview features enabled
- **Build System:** Gradle 8.x with Kotlin DSL
- **Application Framework:** JavaFX 21.0.8

### UI Framework
- **JavaFX:** 21.0.8 for cross-platform desktop GUI
- **FXML:** Declarative UI layout with MVC architecture
- **AtlantaFX:** Modern theme library for enhanced styling
- **RichTextFX:** Advanced text editing component with syntax highlighting

## Key Libraries

### XML Processing
- **Saxon HE:** 12.8 - XSLT 3.0 and XQuery processing engine
- **Jakarta XML Bind API:** 4.0.2 - XML binding and marshalling
- **Apache Xerces:** XML parsing and validation (transitively via other libraries)

### PDF Generation
- **Apache FOP:** 2.11 - XSL-FO to PDF transformation
- **Apache Batik:** SVG processing and rendering (FOP dependency)

### Digital Signatures
- **BouncyCastle:** Cryptographic operations for XML digital signatures
- **Java Cryptography Architecture (JCA):** Native Java crypto support

### Testing Framework
- **JUnit 5:** Unit and integration testing
- **TestFX:** JavaFX application testing framework
- **Mockito:** Mocking framework for unit tests

### Logging and Monitoring
- **Log4j2:** Comprehensive logging framework
- **SLF4J:** Logging facade for library compatibility

## Architecture Components

### Model-View-Controller (MVC)
- **Controllers:** JavaFX controllers handling UI logic and user interactions
- **Services:** Business logic layer for XML operations
- **Domain Models:** Data transfer objects and entity representations
- **FXML Views:** Declarative UI definitions

### Core Services

#### XmlService
- XML parsing, validation, and transformation
- XPath and XQuery execution
- Schema validation (XSD)
- Document manipulation operations

#### XsdDocumentationService
- Schema analysis and documentation generation
- HTML/SVG documentation output
- Technical annotation processing (XsdDocInfo)
- Schema flattening and sample generation

#### SchematronService
- Schematron rule compilation and validation
- Business rule processing
- Integration with Saxon XSLT engine

#### SignatureService
- XML digital signature creation and validation
- Certificate management
- Cryptographic operations using BouncyCastle

### Advanced Components

#### IntelliSense System
```java
org.fxt.freexmltoolkit.controls.intellisense/
├── CompletionCache           # Caching for performance
├── CompletionContext         # Context-aware completion
├── EnhancedCompletionPopup   # UI for code completion
├── XsdAutoComplete           # XSD-based auto-completion
└── NamespaceResolver         # XML namespace handling
```

#### Template Engine
```java
org.fxt.freexmltoolkit.service/
├── TemplateEngine           # Template processing
├── SchemaGenerationEngine   # Schema-based generation
├── XPathSnippetRepository   # XPath expression library
└── TransformationProfile    # XSLT transformation profiles
```

## Development Tools Integration

### Language Server Protocol (LSP)
- XML Language Server integration for advanced editing features
- Real-time syntax validation and error detection
- Code completion and IntelliSense support

### Schema Processing
- XSD schema parsing and analysis
- Element and attribute extraction
- Type hierarchy resolution
- Namespace-aware processing

## Build Configuration

### Gradle Features
- **Java Toolchain:** Configured for Java 24
- **JavaFX Plugin:** 0.1.0 for JavaFX module management
- **Dependency Updates:** Automated dependency update checking
- **Native Packaging:** Cross-platform executable generation

### Repository Configuration
```kotlin
repositories {
    mavenCentral()
    gradlePluginPortal()
    maven { url = uri("https://repo.eclipse.org/content/groups/releases/") }
    maven { url = uri("https://repo.eclipse.org/content/repositories/lemminx-releases/") }
    maven { url = uri("https://maven.bestsolution.at/efxclipse-releases/") }
}
```

### Key Dependencies
```kotlin
dependencies {
    // XML Processing
    implementation("net.sf.saxon:Saxon-HE:12.8")
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    
    // PDF Generation
    implementation("org.apache.xmlgraphics:fop:2.11")
    
    // UI Components
    implementation("org.fxmisc.richtext:richtextfx:0.11.2")
    implementation("io.github.mkpaz:atlantafx-base:2.0.1")
    
    // Security
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
}
```

## Performance Optimizations

### Memory Management
- **ExecutorService:** Background thread pool for heavy operations
- **Memory Monitoring:** Built-in memory usage tracking with configurable thresholds
- **Caching Systems:** Intelligent caching for schemas, templates, and completion data
- **Lazy Loading:** On-demand loading of resources and components

### Processing Optimizations
- **Saxon HE Configuration:** Optimized XSLT and XQuery processing
- **Document Streaming:** Memory-efficient XML processing for large files
- **Background Processing:** Non-blocking operations to maintain UI responsiveness
- **Incremental Updates:** Efficient handling of document changes

## Cross-Platform Support

### Native Packaging
- **jpackage:** Java native packaging tool for platform-specific installers
- **JLink:** Custom runtime images for reduced distribution size
- **Platform-Specific Builds:** Windows, macOS, and Linux support

### JavaFX Module Configuration
```kotlin
javafx {
    version = "21.0.8"
    modules("javafx.controls", "javafx.fxml", "javafx.web", "javafx.swing")
}
```

## Security Features

### Digital Signatures
- **XML-DSig:** W3C XML Digital Signature support
- **Certificate Validation:** X.509 certificate chain validation
- **Cryptographic Algorithms:** Support for RSA, DSA, and ECDSA signatures
- **Secure Random:** Cryptographically secure random number generation

### Input Validation
- **Schema Validation:** Strict validation against XSD schemas
- **XPath Injection Prevention:** Safe XPath expression execution
- **File System Security:** Controlled file access with validation

## Development Environment

### Recommended IDE Setup
- **IntelliJ IDEA:** Primary development environment
- **JavaFX Scene Builder:** Visual FXML design tool
- **Git:** Version control system

### Code Quality Tools
- **Static Analysis:** Built-in code analysis and inspection
- **Unit Testing:** Comprehensive test coverage with JUnit 5
- **Integration Testing:** TestFX for UI testing
- **Documentation:** Javadoc generation for API documentation

## Future Technology Roadmap

### Planned Upgrades
- **Java 25 LTS:** Migration to next Long Term Support version
- **JavaFX 25:** Latest JavaFX runtime with performance improvements
- **Saxon 13.x:** Updated XSLT/XQuery engine
- **GraalVM Native Image:** Native compilation for improved startup and memory usage

### New Integrations
- **Language Server Protocol:** Full LSP implementation for advanced editing
- **WebAssembly Support:** Browser-based XML processing capabilities
- **Cloud Integration:** Remote schema and template repositories
- **Machine Learning:** AI-powered code completion and validation suggestions

---

[Previous: Schematron Support](schematron-support.md) | [Home](index.md) | [Next: Licenses](licenses.md)