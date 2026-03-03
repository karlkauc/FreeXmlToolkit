# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Detailed technical references are in `.claude/rules/`:**
> - `architecture.md` - Application layers, V2 data flow, controllers, threading, observable pattern
> - `domain.md` - XSD 1.0/1.1 differences, 38 node types, facets by datatype, XML libraries
> - `quick-reference.md` - Build commands, command template, icons, FXML rules, testing, gotchas
> - `xsd-editor-v2-details.md` - Type Editor, CommandManager, XsdEditorContext, facets, serialization, package structure

## Build Commands

```bash
./gradlew run                    # Run the application
./gradlew test                   # Run all tests
./gradlew clean build            # Clean and build
./gradlew test --tests "ClassName.methodName"  # Single test
./gradlew createAllExecutables   # All native packages
./gradlew dependencyUpdates      # Check for dependency updates
```

**Build Details:** Java 25 with preview features, JavaFX 24.0.1 (Liberica Full JDK), Gradle 8.x with Kotlin DSL, test heap 16GB max.

## Release Checklist

Update version in these locations:

1. **build.gradle.kts** (line ~26): `version = "X.Y.Z"`
2. **UpdateCheckServiceImpl.java**: `DEFAULT_VERSION` constant
   - Location: `src/main/java/org/fxt/freexmltoolkit/service/UpdateCheckServiceImpl.java`
   - Prevents false update notifications during IDE development

## Technology Stack

**Core Technologies:**
- **Java 25** with preview features
- **JavaFX 24.0.1** for UI (Liberica Full JDK includes JavaFX)
- **Saxon HE 12.9** for XSLT 3.0/XPath 3.1/XQuery
- **Xerces 2.12.2** with XSD 1.1 support (exist-db fork)
- **Apache FOP 2.11** for PDF generation
- **RichTextFX 0.11.6** for code editor with syntax highlighting
- **Log4j2 2.24.1** for logging

**Key Dependencies:** Apache Santuario (XML signatures), ControlsFX 11.2.2, AtlantaFX Base 2.1.0 (theme), TestFX (UI tests)

## Application Entry Point

**Main Class:** `org.fxt.freexmltoolkit.FxtGui` — extends `javafx.application.Application`, loads FXML from `/pages/main.fxml`, initializes maximized window with logging, fonts, CSS hot-reloading, platform icons.

**Main Controller:** `org.fxt.freexmltoolkit.controller.MainController` — manages tab-based navigation, coordinates specialized controllers, handles lifecycle and executor services.

## Architecture Overview

**Pattern:** MVC with Service Layer. The XSD Editor V2 (`controls/v2/`) uses MVVM with Command Pattern.

See `.claude/rules/architecture.md` for layer diagrams, controller table, threading model, and observable pattern. See `.claude/rules/xsd-editor-v2-details.md` for V2 internals.

**Key Principle:** All model modifications MUST go through commands (never modify XsdNode directly from UI code). See `.claude/rules/quick-reference.md` for the wrong/right example.

## Critical Architecture Principles

1. **Model-View Separation:** XsdNode has zero UI dependencies
2. **Command Pattern for Editing:** All changes through commands
3. **Observable Properties:** PropertyChangeSupport for reactive updates
4. **Immutable IDs:** UUID-based node identification
5. **Deep Copy Support:** All nodes support duplication
6. **Test-Driven:** Comprehensive test coverage
7. **Incremental Rendering:** Only changed nodes refresh

## UI Guidelines

**IMPORTANT:** When modifying or creating UI components, ALWAYS consult **[STYLE_GUIDE.jsonc](STYLE_GUIDE.jsonc)** first!

The style guide covers: color palette, typography, icons, components, CSS classes, and usage guidelines.

### Core Principles

- Use `xmlspy-style-config.json` for general styling
- Modern and colorful styled UI (AtlantaFX theme)
- Menus and context menus should always have icons and text
- User-friendly error dialogs and alerts
- Graceful degradation on errors

### Icon Usage (Critical)

1. **Only use icons from Ikonli Bootstrap Icons** (bi-* prefix)
2. **Always verify icon existence** at https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html
3. **NEVER use `bi-database`** - it does not exist!
4. **Standard sizes:** Menu items: `iconSize="16"`, Toolbar: `iconSize="20"`, Empty states: `iconSize="48"` or `iconSize="64"`
5. **Semantic colors:** Success `#28a745`, Danger `#dc3545`, Info `#17a2b8`, Warning `#ffc107`, Primary `#007bff`

### Example FXML Icon Usage

```xml
<Button onAction="#handleSave">
    <graphic>
        <FontIcon iconLiteral="bi-save" iconSize="20"
                  iconColor="#17a2b8" styleClass="toolbar-icon-info"/>
    </graphic>
</Button>

<MenuItem text="Delete" onAction="#handleDelete">
    <graphic>
        <FontIcon iconLiteral="bi-trash" iconSize="16" iconColor="#dc3545"/>
    </graphic>
</MenuItem>
```

### CSS Style Classes

- **Buttons:** `.btn-primary`, `.btn-success`, `.btn-danger`, `.toolbar-button`
- **Cards:** `.card`, `.action-card`, `.statistics-card`
- **Tabs:** `.primary-tab`, `.output-tab`, `.utility-tab`
- **Toolbars:** `.xsd-toolbar`, `.toolbar-icon-success`, `.toolbar-icon-danger`
- **Dialogs:** `.dialog-header-primary`, `.dialog-info-box`

## Important Development Guidelines

- **Always create unit tests** for new features
- **Always check** no existing implementation is broken
- **Comments and JavaDoc** in English
- **Refactor automatically:** Split large classes, delete unused code, avoid reflections
- **Error handling:** Comprehensive management, user-friendly alerts, graceful degradation

## Common Mistakes to Avoid

- **Invalid Icons:** Never use `bi-database` (does not exist). Always validate against the cheat sheet.
- **XSD Infinite Loops:** Child nodes can reference parent types. Always check circular references.
- **FXML Controller Methods:** All `@FXML` methods MUST be `public` (module system requirement for jpackage).
- **Icon References:** Validate all `bi-*` icons before committing.

## Project-Specific Patterns

- **XSLT/XQuery:** Saxon HE 12.9 for XSLT 3.0, XPath 3.1, XQuery 3.1
- **XML Validation:** Xerces 2.12.2 (exist-db fork) for XSD 1.1
- **UI Framework:** JavaFX 24.0.1 with AtlantaFX theme
- **Icons:** Ikonli Bootstrap Icons - always verify existence
- **Async Operations:** `Platform.runLater()` for UI updates from background threads
- **Background Tasks:** `FxtGui.executorService` or `ThreadPoolManager`

## Known Limitations

**Schema Support:** XSD 1.0/1.1 (full), Schematron (full). DTD and RelaxNG not supported.

**XSD Editor V2:** One level of SimpleType resolution for inherited facets. No Union/List facet support yet. Imported/included schemas not resolved.

## Performance Requirements

- **Background Loading:** All loadable data MUST be loaded asynchronously. Never block the UI thread.
- **Lazy Loading:** Load data on-demand where possible.

## Usability Requirements

- **Preserve View State:** Expanded/collapsed nodes must remain after edits.
- **Workflow Continuity:** Multiple sequential actions without interruption.
- **All User-Facing Text in English.**

## Documentation

**Architecture:** `docs/XSD_EDITOR_V2_README.md`, `docs/XSD_EDITOR_V2_PLAN.md`, `docs/XSD_EDITOR_V2_ROADMAP.md`, `docs/ThreadPoolArchitecture.md`

**Features:** `docs/context-sensitive-intellisense.md`, `docs/favorites-system.md`, `docs/type-definition-inclusion-feature.md`, `docs/xml-editor-features.md`

**Facets:** `FACETS_IMPLEMENTATION_SUMMARY.md`, `INHERITED_FACETS_FEATURE.md`, `XSD-1.1-DATATYPES-FACETS.md`

**Build:** `BUILD_INSTRUCTIONS.md`, `README.md`

**UI/Styling:** `STYLE_GUIDE.jsonc`
