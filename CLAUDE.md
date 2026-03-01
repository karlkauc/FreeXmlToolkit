# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

**Quick Start:**
```bash
./gradlew run                    # Run the application
./gradlew test                   # Run all tests
./gradlew clean build            # Clean and build
```

**Single Test Execution:**
```bash
./gradlew test --tests "ClassName.methodName"
./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.model.XsdFacetTypeTest"
```

**Native Executables:**
```bash
./gradlew createAllExecutables         # All platforms
./gradlew createWindowsExecutable      # Windows EXE
./gradlew createMacOSExecutable        # macOS DMG
./gradlew createLinuxExecutable        # Linux AppImage
```

**Development Tools:**
```bash
./gradlew dependencyUpdates      # Check for dependency updates
./gradlew build --info           # Build with detailed logging
```

**Build Details:**
- Java 25 with preview features enabled
- JavaFX 24.0.1 (included in Liberica Full JDK)
- Gradle 8.x with Kotlin DSL (build.gradle.kts)
- Test heap: 16GB max configured

## Release Checklist

When creating a new release, update the version in these locations:

1. **build.gradle.kts** (line ~26): `version = "X.Y.Z"`
2. **UpdateCheckServiceImpl.java**: Update `DEFAULT_VERSION` constant
   - Location: `src/main/java/org/fxt/freexmltoolkit/service/UpdateCheckServiceImpl.java`
   - This ensures IDE development doesn't show false update notifications

## Technology Stack

**Core Technologies:**
- **Java 25** with preview features
- **JavaFX 24.0.1** for UI (Liberica Full JDK includes JavaFX)
- **Saxon HE 12.9** for XSLT 3.0/XPath 3.1/XQuery
- **Xerces 2.12.2** with XSD 1.1 support (exist-db fork)
- **Apache FOP 2.11** for PDF generation
- **RichTextFX 0.11.6** for code editor with syntax highlighting
- **Log4j2 2.24.1** for logging

**Key Dependencies:**
- Apache Santuario (XML digital signatures)
- ControlsFX 11.2.2 (extended JavaFX controls)
- AtlantaFX Base 2.1.0 (modern theme)
- TestFX for JavaFX UI tests

## Application Entry Point

**Main Class:** `org.fxt.freexmltoolkit.FxtGui`
- Location: `src/main/java/org/fxt/freexmltoolkit/FxtGui.java`
- Extends `javafx.application.Application`
- Initializes with maximized window
- Loads FXML from `/pages/main.fxml`
- Sets up logging, fonts, CSS hot-reloading, platform icons

**Main Controller:** `org.fxt.freexmltoolkit.controller.MainController`
- Manages tab-based navigation
- Coordinates specialized controllers (XML, XSD, Schematron, XSLT, etc.)
- Handles application lifecycle and executor services

## Architecture Overview

### Overall Pattern: MVC with Service Layer

**Layers:**
1. **View Layer** - FXML-based JavaFX UI components
2. **Controller Layer** - FXML controllers managing UI logic
3. **Service Layer** - Business logic and external library integration
4. **Model/Domain Layer** - Data models and DTOs

### XSD Editor V2: Advanced MVVM Architecture

**Special Note:** The XSD Editor V2 (`controls/v2/`) uses a sophisticated MVVM variant distinct from the rest of the application.

**Key Characteristics:**
- **Pure Model Layer** - No UI dependencies in model classes
- **Observable Model** - PropertyChangeSupport for reactive updates
- **Command Pattern** - All editing operations for undo/redo
- **Incremental Updates** - Only changed nodes re-render
- **Complete XSD 1.0/1.1 Support** - 38 XsdNode types, all facets

**Architecture Layers:**

```
┌─────────────────────────────────────────────────────────┐
│ View Layer (controls/v2/view/)                          │
│ - VisualNode: Visual representation of XsdNode         │
│ - SVG/Canvas rendering                                  │
└─────────────────────────────────────────────────────────┘
                      ↕ PropertyChangeEvents
┌─────────────────────────────────────────────────────────┐
│ Editor/Controller Layer (controls/v2/editor/)           │
│ - XsdEditorContext: Central coordination               │
│ - SelectionModel: Tracks selection                     │
│ - CommandManager: Undo/redo stack                      │
│ - Property Panels: FacetsPanel, XsdPropertiesPanel     │
└─────────────────────────────────────────────────────────┘
                      ↕ Commands
┌─────────────────────────────────────────────────────────┐
│ Command Layer (controls/v2/editor/commands/)            │
│ - 24 Commands: Add, Delete, Edit, Move, etc.           │
│ - All implement XsdCommand interface                   │
│ - Support undo, redo, merging                          │
└─────────────────────────────────────────────────────────┘
                      ↕ Direct model manipulation
┌─────────────────────────────────────────────────────────┐
│ Model Layer (controls/v2/model/)                        │
│ - XsdNode tree: 38 node types                          │
│ - PropertyChangeSupport: Observable properties         │
│ - Deep copy support for duplication                    │
│ - UUID-based immutable IDs                             │
└─────────────────────────────────────────────────────────┘
                      ↕ Serialization
┌─────────────────────────────────────────────────────────┐
│ XsdSerializer (controls/v2/editor/serialization/)       │
│ - Model → XSD XML conversion                           │
│ - Pretty printing, backup support                      │
└─────────────────────────────────────────────────────────┘
```

**Documentation:** See `docs/XSD_EDITOR_V2_README.md` for detailed V2 architecture

### XSD Type Editor: Multi-Tab Type Management

**Special Feature:** Dedicated tab-based editor for XSD ComplexTypes and SimpleTypes.

**Key Components:**
- **TypeEditorTabManager** - Manages multiple type editor tabs
- **ComplexTypeEditorTab** - Graphical editor for ComplexTypes using XsdGraphView
- **SimpleTypeEditorTab** - Form-based editor for SimpleTypes (5 panels)
- **SimpleTypesListTab** - Overview of all SimpleTypes with filter/search

**Architecture:**

```
TypeEditorTabManager
├─ Schema Tab (main schema view)
├─ ComplexType Tabs (multiple, graphical editing with XsdGraphView)
├─ SimpleType Tabs (multiple, 5-panel form editor)
└─ SimpleTypes List Tab (overview with preview)
```

**ComplexType Editor Features:**
- Type name appears as root node
- Full graphical editing (Add/Delete/Modify elements)
- Reuses XsdGraphView for visualization
- Sequence/Choice/All support
- Save/Discard with dirty tracking
- Isolated XsdEditorContext per tab

**SimpleType Editor Features:**
- 5 specialized panels:
  - General (Name, Final attribute)
  - Restriction (Base type + FacetsPanel)
  - List (ItemType selection)
  - Union (MemberTypes management)
  - Annotation (Documentation/AppInfo)
- Direct model modification (no virtual schema)
- PropertyChangeSupport for change tracking
- Complete XSD 1.0/1.1 support

**SimpleTypes List Features:**
- TableView with columns: Name, Base Type, Facets, Usage Count
- Filter/Search functionality
- Sort by multiple criteria
- XSD Preview panel (uses XsdSerializer)
- Actions: Edit, Delete, Duplicate, Find Usage

**Performance:**
- Tab opening tracked with performance logging
- Target: < 1s tab opening time
- Memory-efficient with proper cleanup

**Key Files:**
- `controls/v2/editor/TypeEditorTabManager.java` - Tab management
- `controls/v2/editor/tabs/*.java` - Tab implementations
- `controls/v2/editor/views/*.java` - View implementations

**Documentation:** See `TYPE_EDITOR_STATUS.md` for implementation status

### Key Controller Responsibilities

**MainController:** Application lifecycle, navigation, memory monitoring, ExecutorService coordination
**XmlController:** Multi-tab XML editing, validation, XPath/XQuery execution, IntelliSense/Auto-completion
**XsdController:** XSD tab orchestration, graphical/text view, Type Library, Type Editor, toolbar commands
**DocumentationTabController:** XSD documentation generation (HTML, PDF, Word export)
**FlattenTabController:** XSD schema flattening with options (includes, imports, comments, sort order)
**SchemaAnalysisTabController:** Schema statistics and identity constraint analysis (lazy-loaded)
**XsltController:** XSLT transformations, multi-format output rendering
**SignatureController:** XML digital signature creation and validation
**FopController:** PDF generation from XML/XSL-FO using Apache FOP
**SchematronController:** Schematron business rule validation

### Service Layer Architecture

**XmlService:** Core XML operations (parsing, validation, transformation, XPath/XQuery)
**XsdDocumentationService:** Schema analysis, HTML/SVG documentation generation
**PropertiesService:** Application settings and recent files management
**SchematronService:** Business rule validation beyond XSD
**ConnectionService:** Network connectivity and proxy configuration
**ThreadPoolManager:** Centralized thread pool management

### Package Structure

```
org.fxt.freexmltoolkit/
├── FxtGui.java                          # Main application entry point
├── app/                                 # Application-level utilities
├── controller/                          # MVC Controllers
│   ├── MainController.java             # Main tab navigation
│   ├── XmlUltimateController.java      # XML editor with IntelliSense
│   ├── XsdController.java              # XSD tab orchestration (663 lines)
│   ├── DocumentationTabController.java # XSD documentation export
│   ├── FlattenTabController.java       # XSD schema flattening
│   ├── SchemaAnalysisTabController.java# Schema analysis views
│   ├── SchematronController.java       # Schematron validation
│   └── controls/                       # Sub-controllers for reusable components
├── controls/                            # Custom JavaFX components
│   ├── commands/                       # Legacy command pattern
│   ├── dialogs/                        # Reusable dialogs
│   ├── editor/                         # XML/XSD editor components (deprecated V1)
│   ├── intellisense/                   # IntelliSense/autocomplete engine (deprecated V1)
│   ├── shared/                         # Shared utilities (XmlSyntaxHighlighter)
│   ├── XmlEditor.java                  # XML tab component (uses V2 internally)
│   └── XmlCodeEditor.java              # Deprecated V1 - only used by Schematron
├── controls/v2/                        # XML/XSD Editor V2 (Primary Architecture)
│   ├── common/
│   │   └── utilities/                  # Common utility helpers (Phase 3)
│   │       ├── XmlEditorUIHelper       # Text formatting, HTML stripping
│   │       ├── XPathAnalyzer           # XPath parsing and analysis
│   │       ├── XmlValidationHelper     # Error conversion and validation
│   │       ├── XmlCanvasRenderingHelper # Canvas rendering utilities
│   │       ├── XmlCanvasLayoutHelper   # Layout calculations
│   │       └── XmlCanvasEventHelper    # Event type detection
│   ├── model/                          # XSD domain model (38 classes)
│   │   ├── XsdNode.java                # Base class for all XSD nodes
│   │   ├── XsdElement.java             # Element nodes
│   │   ├── XsdComplexType.java         # Complex type definitions
│   │   ├── XsdSimpleType.java          # Simple type definitions
│   │   ├── XsdRestriction.java         # Type restrictions
│   │   ├── XsdFacet.java               # Facets (pattern, enumeration, etc.)
│   │   ├── XsdNodeFactory.java         # Factory for creating nodes from XML
│   │   ├── XsdTypeParsingHelper        # Type classification and parsing (Phase 3)
│   │   ├── XsdElementParsingHelper     # Element property extraction (Phase 3)
│   │   ├── XsdStructureParsingHelper   # Compositor detection (Phase 3)
│   │   ├── XsdConstraintParsingHelper  # Constraint identification (Phase 3)
│   │   ├── XsdSchemaReferenceHelper    # Import/Include handling (Phase 3)
│   │   └── [30+ other XSD constructs]  # Complete XSD 1.0/1.1 support
│   ├── view/                           # Visual representation layer
│   │   ├── XsdGraphViewEventHandler    # Mouse event classification (Phase 3)
│   │   ├── XsdGraphViewRenderingHelper # Zoom and rendering utilities (Phase 3)
│   │   ├── XsdGraphViewTreeManager     # Tree operations (Phase 3)
│   │   └── XsdGraphViewOperationHelper # Clipboard and node operations (Phase 3)
│   ├── editor/                         # Editor orchestration
│   │   ├── XsdEditorContext.java       # Central context
│   │   ├── commands/                   # Command pattern (24 commands)
│   │   ├── panels/                     # Property panels
│   │   │   ├── XsdPropertiesPanelFacetsHelper       # Facet extraction (Phase 3)
│   │   │   ├── XsdPropertiesPanelDocumentationHelper # Documentation (Phase 3)
│   │   │   ├── XsdPropertiesPanelTypeHelper         # Type utilities (Phase 3)
│   │   │   └── XsdPropertiesPanelConstraintHelper   # Constraint operations (Phase 3)
│   │   ├── selection/                  # Selection model
│   │   ├── menu/                       # Context menus
│   │   └── serialization/              # Model-to-XML serialization
│   ├── rendering/                      # SVG/visual rendering
│   └── controller/                     # V2 controllers
├── domain/                              # Domain models
│   └── command/                        # Domain-level commands
├── service/                             # Business logic layer
│   ├── PropertiesService.java          # App settings
│   ├── ThreadPoolManager.java          # Centralized thread pool
│   └── [Various XML services]
├── util/                                # Utility classes
│   └── FormattingUtils.java            # Formatting helpers (time, file size, CSV, JSON)
└── demo/                               # Demo applications
```

### Resource Organization

- **FXML files**: `src/main/resources/pages/` (main.fxml, tab_*.fxml, documentation_tab.fxml, flatten_tab.fxml, schema_analysis_tab.fxml)
- **CSS stylesheets**: `src/main/resources/css/` (AtlantaFX theme customizations)
- **Static assets**: `src/main/resources/img/` (icons and logos)
- **XSD Documentation templates**: `src/main/resources/xsdDocumentation/`
- **Examples**: `examples/` folder contains sample XML, XSD, XSLT files

## XSD Model Layer (controls/v2/model/)

### XsdNode Hierarchy

**Base Class:** `XsdNode` (abstract)
- **Common Properties:** id (UUID), name, parent, children, minOccurs, maxOccurs, documentation, appinfo
- **Observable:** Uses `PropertyChangeSupport` for model-view binding
- **Deep Copy:** All nodes support `deepCopy(suffix)` for duplication
- **Parent-Child:** Bidirectional relationships maintained automatically

**38 Node Types:**
- `XsdSchema` - Root schema node
- `XsdElement` - Element declarations
- `XsdComplexType`, `XsdSimpleType` - Type definitions
- `XsdRestriction` - Type restrictions with facets
- `XsdFacet` - Constraint facets (pattern, enumeration, length, etc.)
- `XsdSequence`, `XsdChoice`, `XsdAll` - Compositors
- `XsdAttribute` - Attribute declarations
- `XsdGroup`, `XsdAttributeGroup` - Reusable groups
- `XsdKey`, `XsdKeyRef`, `XsdUnique` - Identity constraints
- `XsdImport`, `XsdInclude`, `XsdRedefine`, `XsdOverride` - Schema references
- `XsdAssert`, `XsdAlternative`, `XsdOpenContent` - XSD 1.1 features
- [20+ additional XSD constructs]

**Key Model Features:**
- UUID-based immutable IDs for tracking
- PropertyChangeSupport for reactive updates
- Deep copy support for node duplication
- Complete XSD 1.0/1.1 compliance

**Model Factory:**
- `XsdNodeFactory` - Parses XSD XML into XsdNode tree
- Handles all XSD constructs
- Creates proper parent-child relationships

## Command Pattern (Undo/Redo)

**Critical:** All model modifications MUST go through commands.

### XsdCommand Interface

```java
public interface XsdCommand {
    boolean execute();              // Execute the command
    boolean undo();                 // Undo the command
    String getDescription();        // Human-readable description
    boolean canUndo();              // Check if undoable
    boolean canMergeWith(XsdCommand other);  // Support for merging
    XsdCommand mergeWith(XsdCommand other);  // Merge similar commands
}
```

### CommandManager

**Location:** `controls/v2/editor/commands/CommandManager.java`

**Features:**
- Dual stack architecture (undoStack, redoStack)
- Configurable history limit (default 100)
- Command merging for consecutive similar operations
- PropertyChangeSupport for UI binding
- Automatic dirty flag management

**Key Methods:**
- `executeCommand(XsdCommand)` - Execute and push to undo stack
- `undo()` - Pop from undo stack, execute undo, push to redo stack
- `redo()` - Pop from redo stack, execute again
- `clear()` - Clear all history

### Available Commands (24 total)

**Structure:** AddElementCommand, DeleteNodeCommand, MoveNodeCommand, DuplicateNodeCommand
**Properties:** RenameNodeCommand, ChangeCardinalityCommand, ChangeTypeCommand, ChangeDocumentationCommand
**Facets:** AddFacetCommand, DeleteFacetCommand, EditFacetCommand
**Constraints:** AddPatternCommand, DeletePatternCommand, AddEnumerationCommand, DeleteEnumerationCommand
**Assertions:** AddAssertionCommand, DeleteAssertionCommand
**Persistence:** SaveCommand

### Command Pattern Rules

**When modifying the model:**
1. NEVER modify XsdNode directly from UI code
2. ALWAYS create a command and execute via CommandManager
3. Store complete state needed for undo in constructor
4. Commands should be atomic and reversible
5. Fire PropertyChangeEvents in execute() and undo()

**Example:**
```java
// ❌ WRONG: Direct model modification
element.setName("NewName");

// ✅ CORRECT: Via command
RenameNodeCommand cmd = new RenameNodeCommand(element, "NewName");
editorContext.getCommandManager().executeCommand(cmd);
```

## XsdEditorContext (Central Coordination)

**Location:** `controls/v2/editor/XsdEditorContext.java`

**Core Components:**
```java
public class XsdEditorContext {
    private final XsdSchema schema;              // The model root
    private final CommandManager commandManager; // Undo/redo
    private final SelectionModel selectionModel; // Current selection
    private boolean editMode;                    // Edit vs. read-only
    private boolean dirty;                       // Unsaved changes flag
}
```

**Communication Flow:**

1. **Model → View:** XsdNode fires PropertyChangeEvent → VisualNode updates UI
2. **View → Model:** User interaction → Create XsdCommand → Execute via CommandManager
3. **Cross-Component:** SelectionModel tracks selection, all components access context

**SelectionModel:**
- Tracks currently selected XsdNode(s)
- Fires selection change events
- Used by property panels, context menus, commands

## Facets Implementation (XSD 1.1)

**Complete Support:** All 44 XSD 1.1 datatypes with appropriate facets.

### Core Classes

**XsdFacetType.java** - Enum of 14 facet types:
- XSD 1.0: length, minLength, maxLength, pattern, enumeration, whiteSpace, maxInclusive, maxExclusive, minInclusive, minExclusive, totalDigits, fractionDigits
- XSD 1.1: assertion, explicitTimezone

**XsdDatatypeFacets.java** - Static mapping utility:
- `getApplicableFacets(String datatype)` - Returns applicable facets
- `isFacetFixed(String datatype, XsdFacetType)` - Checks if facet has fixed value
- `getFixedFacetValue(String datatype, XsdFacetType)` - Returns fixed value

**XsdFacet.java** - Model class with type, value, fixed properties

### FacetsPanel UI Features

**Location:** `controls/v2/editor/panels/FacetsPanel.java`

**Key Features:**
1. **Datatype-specific facet filtering** - Only shows applicable facets
2. **Fixed facets as read-only** - Yellow background, disabled editing
3. **Inherited facets display** - Blue background for facets from referenced types
4. **Visual distinction:**
   - Normal: White background
   - Fixed facets: Yellow (#fff3cd) with brown text
   - Inherited facets: Blue (#e7f3ff) with dark blue text, italic

**Two Modes:**
- `setRestriction(XsdRestriction)` - Editable facets
- `setElement(XsdElement)` - Read-only inherited facets from referenced SimpleType

**Documentation:** See `FACETS_IMPLEMENTATION_SUMMARY.md` and `INHERITED_FACETS_FEATURE.md`

## Serialization

**XsdSerializer** - Model to XSD XML conversion

**Location:** `controls/v2/editor/serialization/XsdSerializer.java`

**Features:**
- Model-First serialization (independent of view)
- Pretty print with configurable indentation
- Automatic timestamped backups
- Complete XSD 1.0/1.1 support
- Constraint synchronization

**Round-Trip:**
```
XSD XML → XsdNodeFactory → XsdNode tree → XsdSerializer → XSD XML
```

**Testing:** `XsdRoundTripTest.java` verifies serialization integrity

## Testing Strategy

### Test Structure (mirrors main source)

```
src/test/java/org/fxt/freexmltoolkit/
├── controller/          # Controller tests
├── controls/
│   ├── commands/       # Command tests
│   ├── intellisense/   # IntelliSense tests
│   └── v2/
│       ├── model/      # 31 model test classes
│       ├── editor/     # Editor tests
│       └── view/       # View tests
└── service/            # Service tests
```

### Test Categories

**Model Tests (Pure Java):** No JavaFX dependencies, test properties, relationships, events
**Command Tests:** Verify execute() and undo() behavior
**Serialization Tests:** Round-trip XML→Model→XML
**UI Tests:** Use TestFX for JavaFX interactions

### Test Heap Configuration

Tests run with 16GB max heap (configured in build.gradle.kts)

## Common Development Tasks

### Adding a New Command

1. Create class in `controls/v2/editor/commands/`
2. Implement `XsdCommand` interface
3. Store old state in constructor
4. Implement `execute()` to modify model
5. Implement `undo()` to restore state
6. Fire PropertyChangeEvents
7. Add unit test

**Template:**
```java
public class MyCommand implements XsdCommand {
    private final XsdNode node;
    private final String oldValue;
    private final String newValue;

    public MyCommand(XsdNode node, String newValue) {
        this.node = node;
        this.oldValue = node.getSomeProperty();
        this.newValue = newValue;
    }

    @Override
    public boolean execute() {
        node.setSomeProperty(newValue);
        return true;
    }

    @Override
    public boolean undo() {
        node.setSomeProperty(oldValue);
        return true;
    }

    @Override
    public String getDescription() {
        return "Change property to " + newValue;
    }

    @Override
    public boolean canUndo() { return true; }
}
```

### Adding a New XSD Node Type

1. Create class extending `XsdNode` in `controls/v2/model/`
2. Add to `XsdNodeType` enum
3. Override `getNodeType()` and `deepCopy(suffix)`
4. Add PropertyChangeSupport for observable properties
5. Update `XsdNodeFactory.createXsdNode()`
6. Update `XsdSerializer.serializeXsdNode()`
7. Create comprehensive unit tests

### Modifying the Model

1. Update XsdNode with new properties
2. Fire PropertyChangeEvents for observables
3. Update `deepCopy()` if needed
4. Update serializer/factory if XML changes
5. Update affected commands
6. Add tests

## PropertyChangeSupport Pattern

**All XsdNode subclasses use this:**
```java
private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

public void addPropertyChangeListener(PropertyChangeListener listener) {
    pcs.addPropertyChangeListener(listener);
}

public void setName(String name) {
    String oldName = this.name;
    this.name = name;
    pcs.firePropertyChange("name", oldName, name);
}
```

**Why:** Enables reactive UI updates without tight coupling.

## Threading and Concurrency

**JavaFX UI Thread Safety:**
```java
// UI updates MUST be on JavaFX Application Thread
Platform.runLater(() -> {
    // Update UI here
});
```

**Background Operations:**
- Use `FxtGui.executorService` for background tasks
- Use `MainController.scheduler` for periodic tasks
- See `ThreadPoolManager` for centralized thread pool

**Documentation:** `docs/ThreadPoolArchitecture.md`

## IntelliSense System

**Location:** `controls/intellisense/`

**How it works:**
1. Parses XSD schema into internal model
2. Tracks XML cursor position
3. Determines valid elements/attributes based on context
4. Suggests only applicable completions

**Documentation:** `docs/context-sensitive-intellisense.md`

## Favorites System

**Universal file management across all editors**

**Features:**
- Save XML, XSD, Schematron files as favorites
- Custom categories
- Cross-editor access
- Persistent local storage

**Documentation:** `docs/favorites-system.md`

## Documentation

**Architecture:**
- `docs/XSD_EDITOR_V2_README.md` - V2 editor overview
- `docs/XSD_EDITOR_V2_PLAN.md` - Detailed plan (45KB)
- `docs/XSD_EDITOR_V2_ROADMAP.md` - Implementation roadmap
- `docs/ThreadPoolArchitecture.md` - Threading

**Features:**
- `docs/context-sensitive-intellisense.md`
- `docs/favorites-system.md`
- `docs/type-definition-inclusion-feature.md`
- `docs/xml-editor-features.md`

**Facets:**
- `FACETS_IMPLEMENTATION_SUMMARY.md` - Complete implementation
- `INHERITED_FACETS_FEATURE.md` - Inherited facets guide
- `XSD-1.1-DATATYPES-FACETS.md` - Datatype-facet mapping
- `test-comprehensive-facets-demo-README.md` - Demo guide

**Build:**
- `BUILD_INSTRUCTIONS.md` - Native executable creation
- `README.md` - User-facing overview

**UI/Styling:**
- `STYLE_GUIDE.jsonc` - **Complete UI style guide** (colors, icons, components, CSS classes)

## Critical Architecture Principles

1. **Model-View Separation:** XsdNode has zero UI dependencies
2. **Command Pattern for Editing:** All changes through commands
3. **Observable Properties:** PropertyChangeSupport for reactive updates
4. **Immutable IDs:** UUID-based node identification
5. **Deep Copy Support:** All nodes support duplication
6. **Test-Driven:** Comprehensive test coverage
7. **Incremental Rendering:** Only changed nodes refresh

## UI Guidelines

**IMPORTANT:** When modifying or creating UI components, ALWAYS consult the **[STYLE_GUIDE.jsonc](STYLE_GUIDE.jsonc)** first!

### Style Guide Reference

The `STYLE_GUIDE.jsonc` file contains comprehensive documentation of:
- **Color Palette:** Semantic colors, theme colors, tab category colors
- **Typography:** Font families, sizes, weights, headings
- **Icons:** Complete list of available Ikonli Bootstrap icons with usage patterns
- **Components:** Button variants, cards, dialogs, toolbars, context menus
- **CSS Classes:** All available style classes organized by category
- **Usage Guidelines:** Best practices for consistent UI development

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
4. **Standard sizes:**
   - Menu items: `iconSize="16"`
   - Toolbar buttons: `iconSize="20"`
   - Empty states: `iconSize="48"` or `iconSize="64"`
5. **Use semantic colors:**
   - Success actions: `#28a745` (green)
   - Danger/Delete: `#dc3545` (red)
   - Info/Save: `#17a2b8` (cyan)
   - Warning: `#ffc107` (yellow)
   - Primary: `#007bff` (blue)

### Example FXML Icon Usage

```xml
<!-- Toolbar button with semantic color -->
<Button onAction="#handleSave">
    <graphic>
        <FontIcon iconLiteral="bi-save" iconSize="20"
                  iconColor="#17a2b8" styleClass="toolbar-icon-info"/>
    </graphic>
</Button>

<!-- Menu item with icon -->
<MenuItem text="Delete" onAction="#handleDelete">
    <graphic>
        <FontIcon iconLiteral="bi-trash" iconSize="16" iconColor="#dc3545"/>
    </graphic>
</MenuItem>
```

### CSS Style Classes

Use the predefined style classes from the style guide:
- **Buttons:** `.btn-primary`, `.btn-success`, `.btn-danger`, `.toolbar-button`
- **Cards:** `.card`, `.action-card`, `.statistics-card`
- **Tabs:** `.primary-tab`, `.output-tab`, `.utility-tab`
- **Toolbars:** `.xsd-toolbar`, `.toolbar-icon-success`, `.toolbar-icon-danger`
- **Dialogs:** `.dialog-header-primary`, `.dialog-info-box`

## Important Development Guidelines

- **Always create unit tests** for new features
- **Always check** no existing implementation is broken
- **Comments and JavaDoc** in English
- **Refactor automatically:**
  - Split large classes into smaller ones
  - Delete unused code (ensure no production code breaks)
  - Avoid reflections if possible
- **Error handling:**
  - Comprehensive error management
  - User-friendly alerts
  - Graceful degradation

## Common Mistakes to Avoid

- **Invalid Icons:** Never use `bi-database` icon (does not exist). Always validate icons against https://kordamp.org/ikonli/cheat-sheet-bootstrapicons.html before using them.
- **XSD Infinite Loops:** In XSD schemas, child nodes can have the parent node's type. Always check for circular references when processing XSD to prevent infinite loops.
- **FXML Controller Methods:** All `@FXML` annotated methods in controllers MUST be `public`. The module system prevents access to non-public methods in jlink/jpackage releases.
- **Icon References:** Before committing code with `bi-*` icons or `FontIcon` references, validate that all icons exist.

## Project-Specific Patterns

- **XSLT/XQuery Engine:** Use Saxon HE 12.9 for all XSLT 3.0, XPath 3.1, and XQuery 3.1 processing
- **XML Validation:** Use Xerces 2.12.2 (exist-db fork) for XSD 1.1 validation support
- **UI Framework:** JavaFX 24.0.1 with AtlantaFX theme for modern styling
- **Icons:** Ikonli Bootstrap Icons - always verify icon existence before use
- **Async Operations:** Use `Platform.runLater()` for UI updates from background threads
- **Background Tasks:** Use `FxtGui.executorService` or `ThreadPoolManager` for background operations

## Known Limitations

**Schema Support:**
- ✅ XSD (XML Schema Definition) - Full XSD 1.0/1.1
- ✅ Schematron - Business rules validation
- ❌ DTD (Document Type Definition) - Not supported
- ❌ RelaxNG - Not supported

**XSD Editor V2:**
- One level of SimpleType resolution for inherited facets
- No Union/List facet support yet
- Imported/included schemas not resolved

## Quick Reference

**Run:** `./gradlew run`
**Test:** `./gradlew test`
**Single Test:** `./gradlew test --tests "ClassName.methodName"`
**Native:** `./gradlew createAllExecutables`
**Main:** `org.fxt.freexmltoolkit.FxtGui`
**XSD Controller:** `controller/XsdController.java` (tab orchestration)
**XSD Sub-tabs:** `controller/DocumentationTabController.java`, `FlattenTabController.java`, `SchemaAnalysisTabController.java`
**Formatting Utils:** `util/FormattingUtils.java`
**XSD Model:** `controls/v2/model/XsdNode.java`
**Commands:** `controls/v2/editor/commands/`
**Context:** `controls/v2/editor/XsdEditorContext.java`
**Serializer:** `controls/v2/editor/serialization/XsdSerializer.java`
**Factory:** `controls/v2/model/XsdNodeFactory.java`
**Facets:** `controls/v2/editor/panels/FacetsPanel.java`
**Style Guide:** `STYLE_GUIDE.jsonc` (colors, icons, components)

## Performance Requirements

- **Background Loading:** All data that can be loaded in the background MUST be loaded asynchronously. Never block the UI thread.
- **Lazy Loading:** Load data on-demand where possible to improve startup time.

## Usability Requirements

- **Preserve View State:** When editing elements graphically, the view state MUST be preserved after changes. All expanded/collapsed nodes should remain in their previous state.
- **Workflow Continuity:** Users must be able to perform multiple actions in sequence without interruption (e.g., no need to re-expand nodes to return to previous location).
- **All User-Facing Text in English:** Error messages, dialogs, labels, and all UI text must be in English.
