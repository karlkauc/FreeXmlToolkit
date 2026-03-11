# XSD Editor V2 - Detailed Technical Reference

## XSD Type Editor: Multi-Tab Type Management

**Key Components:**
- **TypeEditorTabManager** - Manages multiple type editor tabs
- **ComplexTypeEditorTab** - Graphical editor for ComplexTypes using XsdGraphView
- **SimpleTypeEditorTab** - Form-based editor for SimpleTypes (5 panels)
- **SimpleTypesListTab** - Overview of all SimpleTypes with filter/search

```
TypeEditorTabManager
├─ Schema Tab (main schema view)
├─ ComplexType Tabs (graphical editing with XsdGraphView)
├─ SimpleType Tabs (5-panel form editor)
└─ SimpleTypes List Tab (overview with preview)
```

**ComplexType Editor:** Type name as root node, full graphical editing (Add/Delete/Modify), Sequence/Choice/All support, Save/Discard with dirty tracking, isolated XsdEditorContext per tab.

**SimpleType Editor (5 panels):** General (Name, Final), Restriction (Base type + FacetsPanel), List (ItemType), Union (MemberTypes), Annotation (Documentation/AppInfo). Direct model modification, PropertyChangeSupport for change tracking.

**SimpleTypes List:** TableView (Name, Base Type, Facets, Usage Count), filter/search/sort, XSD Preview panel, actions (Edit, Delete, Duplicate, Find Usage).

**Performance:** Target < 1s tab opening time, memory-efficient cleanup.

**Key Files:**
- `controls/v2/editor/TypeEditorTabManager.java`
- `controls/v2/editor/tabs/*.java`
- `controls/v2/editor/views/*.java`

---

## XsdCommand Interface (Full)

```java
public interface XsdCommand {
    boolean execute();
    boolean undo();
    String getDescription();
    boolean canUndo();
    boolean canMergeWith(XsdCommand other);  // Support for merging
    XsdCommand mergeWith(XsdCommand other);  // Merge similar commands
}
```

## CommandManager Details

**Location:** `controls/v2/editor/commands/CommandManager.java`

- Dual stack architecture (undoStack, redoStack)
- Configurable history limit (default 100)
- Command merging for consecutive similar operations
- PropertyChangeSupport for UI binding
- Automatic dirty flag management

**Key Methods:** `executeCommand(XsdCommand)`, `undo()`, `redo()`, `clear()`

## All 31 Concrete Commands

| Category | Commands |
|----------|----------|
| Structure (11) | AddElementCommand, AddContainerElementCommand, AddAttributeCommand, AddSequenceCommand, AddChoiceCommand, AddAllCommand, AddCompositorCommand, DeleteNodeCommand, MoveNodeCommand, DuplicateNodeCommand, PasteNodeCommand |
| Properties (9) | RenameNodeCommand, ChangeCardinalityCommand, ChangeTypeCommand, ChangeFormCommand, ChangeUseCommand, ChangeSubstitutionGroupCommand, ChangeDocumentationCommand, ChangeDocumentationsCommand, ChangeAppinfoCommand |
| Facets (3) | AddFacetCommand, DeleteFacetCommand, EditFacetCommand |
| Constraints (5) | AddPatternCommand, DeletePatternCommand, AddEnumerationCommand, DeleteEnumerationCommand, ChangeConstraintsCommand |
| Assertions (2) | AddAssertionCommand, DeleteAssertionCommand |
| Persistence (1) | SaveCommand |

## Command Pattern Rules

1. NEVER modify XsdNode directly from UI code
2. ALWAYS create a command and execute via CommandManager
3. Store complete state needed for undo in constructor
4. Commands should be atomic and reversible
5. Fire PropertyChangeEvents in execute() and undo()

---

## XsdEditorContext (Central Coordination)

**Location:** `controls/v2/editor/XsdEditorContext.java`

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
1. **Model -> View:** XsdNode fires PropertyChangeEvent -> VisualNode updates UI
2. **View -> Model:** User interaction -> Create XsdCommand -> Execute via CommandManager
3. **Cross-Component:** SelectionModel tracks selection, all components access context

**SelectionModel:** Tracks selected XsdNode(s), fires selection change events, used by property panels, context menus, commands.

---

## XsdNode Hierarchy

**Base Class:** `XsdNode` (abstract)
- **Common Properties:** id (UUID), name, parent, children, minOccurs, maxOccurs, documentation, appinfo
- **Observable:** PropertyChangeSupport for model-view binding
- **Deep Copy:** All nodes support `deepCopy(suffix)` for duplication
- **Parent-Child:** Bidirectional relationships maintained automatically

**Model Factory:** `XsdNodeFactory` parses XSD XML into XsdNode tree, handles all constructs, creates proper parent-child relationships.

---

## Facets Implementation (XSD 1.1)

**Complete Support:** All 44 XSD 1.1 datatypes with appropriate facets.

**Core Classes:**
- **XsdFacetType.java** - Enum of 14 facet types (XSD 1.0: length, minLength, maxLength, pattern, enumeration, whiteSpace, maxInclusive, maxExclusive, minInclusive, minExclusive, totalDigits, fractionDigits; XSD 1.1: assertion, explicitTimezone)
- **XsdDatatypeFacets.java** - Static mapping: `getApplicableFacets()`, `isFacetFixed()`, `getFixedFacetValue()`
- **XsdFacet.java** - Model class with type, value, fixed properties

**FacetsPanel** (`controls/v2/editor/panels/FacetsPanel.java`):
- Datatype-specific facet filtering (only applicable facets shown)
- Fixed facets: Yellow (#fff3cd), read-only
- Inherited facets: Blue (#e7f3ff), italic
- Two modes: `setRestriction(XsdRestriction)` (editable) / `setElement(XsdElement)` (read-only inherited)

---

## Serialization

**XsdSerializer** (`controls/v2/editor/serialization/XsdSerializer.java`):
- Model-First serialization (independent of view)
- Pretty print with configurable indentation
- Automatic timestamped backups
- Complete XSD 1.0/1.1 support
- Constraint synchronization

**Round-Trip:** `XSD XML -> XsdNodeFactory -> XsdNode tree -> XsdSerializer -> XSD XML`
**Testing:** `XsdRoundTripTest.java` verifies integrity

---

## Testing Strategy

**Structure mirrors main source:**
```
src/test/java/.../
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

**Categories:**
- Model Tests (Pure Java): No JavaFX dependencies, test properties, relationships, events
- Command Tests: Verify execute() and undo() behavior
- Serialization Tests: Round-trip XML -> Model -> XML
- UI Tests: Use TestFX for JavaFX interactions

---

## IntelliSense System

**Location:** `controls/intellisense/`

1. Parses XSD schema into internal model
2. Tracks XML cursor position
3. Determines valid elements/attributes based on context
4. Suggests only applicable completions

**Documentation:** `docs/context-sensitive-intellisense.md`

## Favorites System

Universal file management across all editors: Save XML/XSD/Schematron files as favorites, custom categories, cross-editor access, persistent local storage.

**Documentation:** `docs/favorites-system.md`

---

## Development Checklists

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

---

## Package Structure (Full)

```
org.fxt.freexmltoolkit/
├── FxtGui.java                          # Main entry point
├── app/                                 # Application-level utilities
├── controller/                          # MVC Controllers
│   ├── MainController.java             # Main tab navigation
│   ├── XmlUltimateController.java      # XML editor with IntelliSense
│   ├── XsdController.java              # XSD tab orchestration
│   ├── DocumentationTabController.java # XSD documentation export
│   ├── FlattenTabController.java       # XSD schema flattening
│   ├── SchemaAnalysisTabController.java# Schema analysis views
│   ├── SchematronController.java       # Schematron validation
│   └── controls/                       # Sub-controllers
├── controls/                            # Custom JavaFX components
│   ├── commands/                       # Legacy command pattern
│   ├── dialogs/                        # Reusable dialogs
│   ├── editor/                         # Deprecated V1
│   ├── intellisense/                   # IntelliSense engine (deprecated V1)
│   ├── shared/                         # Shared utilities (XmlSyntaxHighlighter)
│   ├── XmlEditor.java                  # XML tab component (uses V2)
│   └── XmlCodeEditor.java              # Deprecated V1 (Schematron only)
├── controls/v2/                        # XSD Editor V2 (Primary)
│   ├── common/utilities/               # Utility helpers (Phase 3)
│   ├── model/                          # XSD domain model (38 classes)
│   ├── view/                           # Visual representation layer
│   ├── editor/                         # Editor orchestration
│   │   ├── XsdEditorContext.java       # Central context
│   │   ├── commands/                   # 31 commands
│   │   ├── panels/                     # Property panels + helpers
│   │   ├── selection/                  # Selection model
│   │   ├── menu/                       # Context menus
│   │   └── serialization/              # Model-to-XML
│   ├── rendering/                      # SVG/visual rendering
│   └── controller/                     # V2 controllers
├── domain/                              # Domain models
├── service/                             # Business logic layer
├── util/                                # Utility classes
└── demo/                               # Demo applications
```

### Resources
- **FXML:** `src/main/resources/pages/` (main.fxml, tab_*.fxml, documentation_tab.fxml, flatten_tab.fxml, schema_analysis_tab.fxml)
- **CSS:** `src/main/resources/css/` (AtlantaFX theme customizations)
- **Images:** `src/main/resources/img/`
- **XSD Templates:** `src/main/resources/xsdDocumentation/`
- **Examples:** `examples/` folder

### Service Layer
- **XmlService:** Parsing, validation, transformation, XPath/XQuery
- **XsdDocumentationService:** Schema analysis, HTML/SVG generation
- **PropertiesService:** Settings and recent files
- **SchematronService:** Business rule validation
- **ConnectionService:** Network/proxy configuration
- **ThreadPoolManager:** Centralized thread pool
