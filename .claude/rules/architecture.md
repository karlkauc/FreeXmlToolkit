# Architecture Quick-Reference

## Application Layers
```
View (FXML/JavaFX) → Controller → Service → Model
```

## Package Structure
- `controller/` - FXML Controllers (MainController, XmlController, XsdController + sub-controllers)
- `util/` - Utility classes (FormattingUtils)
- `service/` - Business logic (XmlService, PropertiesService, ThreadPoolManager)
- `controls/v2/` - XSD Editor V2 (primary architecture)
- `domain/` - Domain models

---

## XSD Editor V2 Architecture (MVVM)

### Data Flow
```
XSD File → XsdNodeFactory → XsdNode Tree → VisualNode → UI
                              ↓
                     PropertyChangeEvents
                              ↓
                    Commands (execute/undo)
```

### Critical Classes

| Class | Purpose | Location |
|-------|---------|----------|
| `XsdEditorContext` | Central coordinator | `controls/v2/editor/` |
| `CommandManager` | Undo/Redo stack (100 history) | `controls/v2/editor/commands/` |
| `SelectionModel` | Tracks selected nodes | `controls/v2/editor/selection/` |
| `XsdSerializer` | Model → XSD XML | `controls/v2/editor/serialization/` |
| `XsdNodeFactory` | XSD XML → Model | `controls/v2/model/` |

### Command Pattern (CRITICAL)
```java
// WRONG - direct modification
element.setName("NewName");

// CORRECT - via command
commandManager.executeCommand(new RenameNodeCommand(element, "NewName"));
```

**31 Concrete Commands:**
- Structure (11): `AddElementCommand`, `AddContainerElementCommand`, `AddAttributeCommand`, `AddSequenceCommand`, `AddChoiceCommand`, `AddAllCommand`, `AddCompositorCommand`, `DeleteNodeCommand`, `MoveNodeCommand`, `DuplicateNodeCommand`, `PasteNodeCommand`
- Properties (9): `RenameNodeCommand`, `ChangeCardinalityCommand`, `ChangeTypeCommand`, `ChangeFormCommand`, `ChangeUseCommand`, `ChangeSubstitutionGroupCommand`, `ChangeDocumentationCommand`, `ChangeDocumentationsCommand`, `ChangeAppinfoCommand`
- Facets (3): `AddFacetCommand`, `DeleteFacetCommand`, `EditFacetCommand`
- Constraints (5): `AddPatternCommand`, `DeletePatternCommand`, `AddEnumerationCommand`, `DeleteEnumerationCommand`, `ChangeConstraintsCommand`
- Assertions (2): `AddAssertionCommand`, `DeleteAssertionCommand`
- Persistence (1): `SaveCommand`

---

## Threading Model

| Context | Pattern |
|---------|---------|
| UI Updates | `Platform.runLater(() -> { ... })` |
| Background Work | `FxtGui.executorService.submit(() -> { ... })` |
| Periodic Tasks | `MainController.scheduler` |
| Thread Pool | `ThreadPoolManager` (centralized) |

---

## Key Controllers (25 total)

| Controller | Tab / Context | Responsibility |
|------------|---------------|----------------|
| `MainController` | - | Navigation, lifecycle, memory monitoring |
| `XmlUltimateController` | XML | Multi-tab XML editing, IntelliSense |
| `XsdController` | XSD | Tab orchestration, graphical/text view, Type Library/Editor |
| `DocumentationTabController` | XSD > Documentation | XSD documentation export (HTML, PDF, Word) |
| `FlattenTabController` | XSD > Flatten | Schema flattening with options |
| `SchemaAnalysisTabController` | XSD > Analysis | Schema statistics, identity constraints |
| `XsdValidationController` | XSD Validation | Schema validation results |
| `XsltController` | XSLT | Transformations |
| `XsltDeveloperController` | XSLT Developer | Full XSLT/XQuery development environment |
| `SchematronController` | Schematron | Business rule validation |
| `FopController` | FOP | PDF generation |
| `SignatureController` | Signature | Digital signatures |
| `JsonController` | JSON | JSON editing and validation |
| `SchemaGeneratorController` | Schema Generator | XSD generation from XML |
| `TemplatesController` | Templates | Template management |
| `UnifiedEditorController` | Unified Editor | Unified editing view |
| `SettingsController` | Settings | Application settings |
| `HelpController` | Help | Help and about information |
| `WelcomeController` | Welcome | Welcome screen |
| `FavoritesParentController` | - | Favorites coordination |
| `SchemaGeneratorPopupController` | Popup | Schema generator popup dialog |
| `TemplateManagerPopupController` | Popup | Template manager popup dialog |
| `XmlSpreadsheetConverterDialogController` | Dialog | XML-to-spreadsheet conversion |
| `XmlEditorSidebarController` | Control | XML editor sidebar panel |
| `FavoritesPanelController` | Control | Favorites panel component |

---

## Observable Model Pattern
```java
// All XsdNode subclasses use PropertyChangeSupport
private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

public void setName(String name) {
    String oldName = this.name;
    this.name = name;
    pcs.firePropertyChange("name", oldName, name);  // Triggers UI update
}
```

---

## Entry Points (24 FXML files)

| File | Purpose |
|------|---------|
| `FxtGui.java` | Application main class |
| `main.fxml` | Root layout |
| `welcome.fxml` | Welcome screen |
| `settings.fxml` | Application settings |
| `tab_xml_ultimate.fxml` | XML editor tab |
| `tab_xsd.fxml` | XSD tools tab |
| `tab_json.fxml` | JSON editor tab |
| `tab_xslt.fxml` | XSLT viewer tab |
| `tab_xslt_developer.fxml` | XSLT developer tab |
| `tab_schematron.fxml` | Schematron tab |
| `tab_fop.fxml` | FOP/PDF tab |
| `tab_signature.fxml` | Digital signatures tab |
| `tab_schema_generator.fxml` | Schema generator tab |
| `tab_templates.fxml` | Templates tab |
| `tab_help.fxml` | Help tab |
| `tab_unified_editor.fxml` | Unified editor tab |
| `tab_validation.fxml` | XSD validation tab |
| `documentation_tab.fxml` | XSD documentation sub-tab |
| `flatten_tab.fxml` | XSD flatten sub-tab |
| `schema_analysis_tab.fxml` | XSD schema analysis sub-tab |
| `popup_schema_generator.fxml` | Schema generator popup |
| `popup_templates.fxml` | Template manager popup |
| `dialogs/XmlSpreadsheetConverterDialog.fxml` | Spreadsheet converter dialog |
| `controls/FavoritesPanel.fxml` | Favorites panel component |
| `controls/XmlEditorSidebar.fxml` | XML editor sidebar component |
