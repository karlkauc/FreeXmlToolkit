# Architecture Quick-Reference

## Application Layers
```
View (FXML/JavaFX) → Controller → Service → Model
```

## Package Structure
- `controller/` - FXML Controllers (MainController, XmlController, XsdController)
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

**24 Available Commands:**
- Structure: `AddElementCommand`, `DeleteNodeCommand`, `MoveNodeCommand`, `DuplicateNodeCommand`
- Properties: `RenameNodeCommand`, `ChangeCardinalityCommand`, `ChangeTypeCommand`
- Facets: `AddFacetCommand`, `DeleteFacetCommand`, `EditFacetCommand`

---

## Threading Model

| Context | Pattern |
|---------|---------|
| UI Updates | `Platform.runLater(() -> { ... })` |
| Background Work | `FxtGui.executorService.submit(() -> { ... })` |
| Periodic Tasks | `MainController.scheduler` |
| Thread Pool | `ThreadPoolManager` (centralized) |

---

## Key Controllers

| Controller | Tab | Responsibility |
|------------|-----|----------------|
| `MainController` | - | Navigation, lifecycle, memory monitoring |
| `XmlUltimateController` | XML | Multi-tab XML editing, IntelliSense |
| `XsdController` | XSD | Visualization, documentation, validation |
| `XsltController` | XSLT | Transformations |
| `SchematronController` | Schematron | Business rule validation |
| `FopController` | FOP | PDF generation |
| `SignatureController` | Signature | Digital signatures |

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

## Entry Points

| File | Purpose |
|------|---------|
| `FxtGui.java` | Application main class |
| `main.fxml` | Root layout |
| `tab_*.fxml` | Individual tab layouts |
