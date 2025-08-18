# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Commands

### Building and Running
- **Run the application**: `./gradlew run`
- **Build JAR**: `./gradlew jar`
- **Run tests**: `./gradlew test`
- **Run single test**: `./gradlew test --tests "ClassName.methodName"`
- **Clean build**: `./gradlew clean build`

### Packaging and Distribution
- **Create all executables**: `./gradlew createAllExecutables`
- **Windows packages**: `./gradlew createWindowsPackages`
- **macOS packages**: `./gradlew createMacOSPackages`  
- **Linux packages**: `./gradlew createLinuxPackages`
- **Package distribution**: `./gradlew packageDistribution`

### Development Tools
- **Check for dependency updates**: `./gradlew dependencyUpdates`
- **Build with detailed logging**: `./gradlew build --info`

## Project Architecture

### Technology Stack
- **Language**: Java 21+ with preview features enabled
- **UI Framework**: JavaFX 21.0.8 with FXML
- **Build System**: Gradle with Kotlin DSL
- **Dependency Management**: Multiple Maven repositories including Eclipse repositories
- **Testing**: JUnit 5 with TestFX for JavaFX testing

### Core Architecture Pattern
- **MVC Pattern**: Model-View-Controller with Service Layer
- **Main Entry Point**: `org.fxt.freexmltoolkit.FxtGui` (JavaFX Application)
- **Controllers**: Tab-based UI with specialized controllers for different XML tools

### Key Controller Responsibilities
- **MainController**: Application lifecycle, navigation, memory monitoring, ExecutorService coordination
- **XmlController**: Multi-tab XML editing, validation, XPath/XQuery execution, LSP integration,
  IntelliSense/Auto-completion
- **XsdController**: XSD visualization, documentation generation, schema flattening, sample data generation
- **XsltController**: XSLT transformations, multi-format output rendering
- **SignatureController**: XML digital signature creation and validation
- **FopController**: PDF generation from XML/XSL-FO using Apache FOP

### Service Layer Architecture
- **XmlService**: Core XML operations (parsing, validation, transformation, XPath/XQuery)
- **XsdDocumentationService**: Schema analysis, HTML/SVG documentation generation
- **PropertiesService**: Application settings and recent files management
- **SchematronService**: Business rule validation beyond XSD
- **ConnectionService**: Network connectivity and proxy configuration

### Key Libraries and Integrations
- **Saxon HE 12.8**: XSLT 3.0 and XQuery processing
- **Eclipse LemMinX**: XML Language Server for IntelliSense and validation
- **Apache FOP 2.11**: PDF generation from XSL-FO
- **BouncyCastle**: Cryptographic operations for digital signatures
- **AtlantaFX**: Modern JavaFX theme framework
- **RichTextFX**: Advanced code editor with syntax highlighting

### Package Structure
```
org.fxt.freexmltoolkit/
├── controller/           # MVC Controllers
│   ├── controls/        # Sub-controllers for reusable components
├── service/             # Business logic and external library integration
├── domain/              # Domain models and DTOs
└── controls/            # Custom JavaFX UI components and editors
```

### Resource Organization
- **FXML files**: `src/main/resources/pages/` (main.fxml, tab_*.fxml)
- **CSS stylesheets**: `src/main/resources/css/` (AtlantaFX theme customizations)
- **Static assets**: `src/main/resources/img/` (icons and logos)
- **XSD Documentation templates**: `src/main/resources/xsdDocumentation/`

### Testing Strategy
- **Unit Tests**: Service layer testing with JUnit 5 and Mockito
- **JavaFX Testing**: UI testing with TestFX framework
- **Test Resources**: `src/test/resources/` contains sample XML, XSD, XSLT files
- **Test Configuration**: Separate log4j2.xml for testing

### Key Development Considerations
- **JavaFX Threading**: Heavy operations run on background ExecutorService to prevent UI blocking
- **Memory Management**: Built-in memory monitoring with configurable thresholds
- **File Encoding**: Comprehensive BOM and encoding detection/handling
- **Error Handling**: Extensive validation with user-friendly error reporting
- **LSP Integration**: Real-time XML validation and IntelliSense via Language Server Protocol

### Build Configuration Notes
- **Java Toolchain**: Configured for Java 21 with preview features enabled
- **Native Packaging**: Complex jpackage setup for Windows/macOS/Linux distribution
- **JavaFX Modules**: Separate JMOD files for cross-platform native packaging
- **Runtime Images**: Custom JLink runtime images for each platform
- **Dependency Exclusions**: Security-related files (*.RSA, *.DSA, *.SF) excluded from JAR