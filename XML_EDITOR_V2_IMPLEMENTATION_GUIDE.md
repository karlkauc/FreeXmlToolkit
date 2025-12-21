# XML EDITOR V2 - IMPLEMENTATION GUIDE

**Version:** 1.0
**Date:** 2025-11-22
**For:** Complete implementation reference combining Architecture, Grid View, XSD Integration, and Roadmap

This document consolidates technical implementation details from multiple specifications.

**See also:**
- `XML_EDITOR_V2_REQUIREMENTS.md` - Complete functional requirements
- `XML_EDITOR_V2_FEATURES.md` - All 234 features documented

---

## TABLE OF CONTENTS

1. [Architecture Overview](#1-architecture-overview)
2. [Package Structure](#2-package-structure)
3. [Model Layer Implementation](#3-model-layer-implementation)
4. [Command Pattern Implementation](#4-command-pattern-implementation)
5. [Grid View Implementation](#5-grid-view-implementation)
6. [XSD Integration Implementation](#6-xsd-integration-implementation)
7. [Implementation Roadmap](#7-implementation-roadmap)

---

## 1. ARCHITECTURE OVERVIEW

### 1.1 Architecture Pattern

**Model-View-Command (MVC variant)**

```
┌─────────────────────────────────────────────────────────┐
│ View Layer (JavaFX UI)                                  │
│ - XmlTextView (RichTextFX)                             │
│ - XmlTreeView (TreeView)                               │
│ - XmlGridView (TableView)                              │
└─────────────────────────────────────────────────────────┘
                      ↕ PropertyChangeEvents
┌─────────────────────────────────────────────────────────┐
│ Editor/Controller Layer                                 │
│ - XmlEditorContext (Central coordination)              │
│ - SelectionModel, CommandManager                       │
│ - Validation, IntelliSense, Schema Managers            │
└─────────────────────────────────────────────────────────┘
                      ↕ Commands
┌─────────────────────────────────────────────────────────┐
│ Command Layer (25+ Commands)                            │
│ - All editing operations                                │
│ - Undo/Redo support                                     │
└─────────────────────────────────────────────────────────┘
                      ↕ Direct model manipulation
┌─────────────────────────────────────────────────────────┐
│ Model Layer (Pure Java - NO UI dependencies)            │
│ - XmlDocument, XmlElement, XmlText, XmlAttribute, etc. │
│ - PropertyChangeSupport for observability              │
│ - UUID-based immutable IDs                             │
└─────────────────────────────────────────────────────────┘
                      ↕ Serialization
┌─────────────────────────────────────────────────────────┐
│ Serialization Layer                                     │
│ - XmlSerializer (Model → XML String)                   │
│ - XmlParser (XML String → Model)                       │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Key Design Principles

1. **Model-View Separation** - Zero UI dependencies in model
2. **Observable Properties** - PropertyChangeSupport throughout
3. **Command Pattern** - All modifications through commands
4. **Immutable IDs** - UUID-based node identification
5. **Deep Copy Support** - All nodes support duplication
6. **Incremental Rendering** - Only changed nodes update

---

## 2. PACKAGE STRUCTURE

```
org.fxt.freexmltoolkit.controls.v2.xmleditor/
│
├── model/                          # Model Layer (Pure Java)
│   ├── XmlNode.java               # Base class (abstract)
│   ├── XmlDocument.java           # Root document node
│   ├── XmlElement.java            # Element nodes
│   ├── XmlText.java               # Text content nodes
│   ├── XmlAttribute.java          # Attributes (name/value pairs)
│   ├── XmlComment.java            # Comment nodes
│   ├── XmlCData.java              # CDATA sections
│   ├── XmlProcessingInstruction.java  # PI nodes
│   └── XmlNamespace.java          # Namespace handling
│
├── commands/                       # Command Pattern (25+ Commands)
│   ├── XmlCommand.java            # Interface
│   ├── CommandManager.java        # Undo/redo stack
│   ├── AddElementCommand.java
│   ├── DeleteNodeCommand.java
│   ├── MoveNodeCommand.java
│   ├── RenameNodeCommand.java
│   ├── EditTextCommand.java
│   ├── AddAttributeCommand.java
│   ├── EditAttributeCommand.java
│   ├── DeleteAttributeCommand.java
│   ├── DuplicateNodeCommand.java
│   ├── PasteCommand.java
│   ├── CutCommand.java
│   ├── FormatDocumentCommand.java
│   ├── IndentCommand.java
│   ├── InsertXmlCommand.java
│   ├── WrapInElementCommand.java
│   ├── UnwrapElementCommand.java
│   ├── CommentNodeCommand.java
│   ├── UncommentNodeCommand.java
│   ├── AddCDataCommand.java
│   ├── AddProcessingInstructionCommand.java
│   ├── ChangeNamespaceCommand.java
│   ├── SortChildrenCommand.java
│   ├── MergeElementsCommand.java
│   ├── SplitElementCommand.java
│   └── CompositeCommand.java      # Multiple commands as one
│
├── view/                           # View Layer (JavaFX)
│   ├── XmlTextView.java           # RichTextFX CodeArea wrapper
│   ├── XmlTreeView.java           # TreeView wrapper
│   ├── XmlGridView.java           # TableView wrapper
│   ├── VisualXmlNode.java         # Visual representation
│   └── cells/                      # Cell renderers/editors
│       ├── XmlTreeCell.java       # Tree cell renderer
│       ├── XmlGridCell.java       # Grid cell editor
│       ├── TextCell.java          # TextField editor
│       ├── DropdownCell.java      # ComboBox editor
│       ├── DateCell.java          # DatePicker editor
│       ├── ImageCell.java         # Image display cell
│       └── AttributeCell.java     # Attribute editor
│
├── editor/                         # Editor/Controller Layer
│   ├── XmlEditorContext.java      # Central coordination
│   ├── selection/
│   │   ├── SelectionModel.java    # Selection tracking
│   │   └── MultiSelectionModel.java
│   ├── panels/
│   │   ├── AttributesPanel.java   # Attribute editing
│   │   ├── ValidationPanel.java   # Error display
│   │   ├── PropertiesPanel.java   # Node properties
│   │   └── GridSettingsPanel.java # Grid settings dialog
│   └── menu/
│       ├── XmlContextMenu.java    # Context menus
│       └── XmlMenuFactory.java    # Menu builders
│
├── serialization/                  # Serialization Layer
│   ├── XmlSerializer.java         # Model → XML String
│   ├── XmlParser.java             # XML String → Model
│   ├── IncrementalXmlParser.java  # Large file parsing
│   └── PrettyPrinter.java         # Formatting
│
├── validation/                     # Validation Layer
│   ├── XmlValidationManager.java  # Validation coordinator
│   ├── XsdValidator.java          # Schema validation
│   ├── SchematronValidator.java   # Business rules
│   ├── ValidationError.java       # Error representation
│   └── QuickFix.java              # Suggested corrections
│
├── intellisense/                   # IntelliSense Layer
│   ├── XmlIntelliSenseManager.java
│   ├── XmlCompletionEngine.java
│   ├── XsdIntegration.java        # Schema integration
│   ├── CompletionItem.java
│   └── CompletionProvider.java
│
├── schema/                         # XSD Integration
│   ├── SchemaCache.java           # Schema caching
│   ├── SchemaAnalyzer.java        # Schema analysis
│   ├── ElementSuggester.java      # Element suggestions
│   ├── AttributeSuggester.java    # Attribute suggestions
│   ├── TypeMapper.java            # XSD Type → UI Editor
│   ├── TemplateGenerator.java     # Schema templates
│   └── EntryHelperFactory.java    # Type-aware editor factory
│
└── performance/                    # Performance Layer
    ├── VirtualizationManager.java
    ├── LazyLoader.java
    ├── PerformanceMonitor.java
    └── CacheManager.java
```

**Total Classes:** ~60 classes
**Total LOC:** 15,000-20,000 lines

---

## 3. MODEL LAYER IMPLEMENTATION

### 3.1 XmlNode Base Class

```java
package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.UUID;

/**
 * Base class for all XML node types.
 * NO UI DEPENDENCIES - Pure Java model.
 */
public abstract class XmlNode {
    private final UUID id;  // Immutable unique identifier
    private XmlNode parent;
    private final PropertyChangeSupport pcs;

    protected XmlNode() {
        this.id = UUID.randomUUID();
        this.pcs = new PropertyChangeSupport(this);
    }

    // Observable properties
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        pcs.firePropertyChange(propertyName, oldValue, newValue);
    }

    // Node operations (must be implemented by subclasses)
    public abstract XmlNode deepCopy(String suffix);
    public abstract String serialize(int indent);
    public abstract <T> T accept(XmlNodeVisitor<T> visitor);

    // Parent-child management
    protected void setParent(XmlNode parent) {
        XmlNode oldParent = this.parent;
        this.parent = parent;
        firePropertyChange("parent", oldParent, parent);
    }

    // Getters
    public UUID getId() { return id; }
    public XmlNode getParent() { return parent; }
    public abstract String getName();  // Element name, "#text", "#comment", etc.
}
```

### 3.2 XmlElement Implementation

```java
public class XmlElement extends XmlNode {
    private String name;
    private List<XmlNode> children = new ArrayList<>();
    private Map<String, String> attributes = new LinkedHashMap<>();
    private String namespace;

    public XmlElement(String name) {
        super();
        this.name = name;
    }

    // Observable setters
    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        firePropertyChange("name", oldName, name);
    }

    // Child management
    public void addChild(XmlNode child) {
        addChild(children.size(), child);
    }

    public void addChild(int index, XmlNode child) {
        children.add(index, child);
        child.setParent(this);
        firePropertyChange("children", null, children);
    }

    public void removeChild(XmlNode child) {
        children.remove(child);
        child.setParent(null);
        firePropertyChange("children", null, children);
    }

    public void moveChild(int fromIndex, int toIndex) {
        XmlNode child = children.remove(fromIndex);
        children.add(toIndex, child);
        firePropertyChange("children", null, children);
    }

    // Attribute management
    public void setAttribute(String name, String value) {
        String oldValue = attributes.put(name, value);
        firePropertyChange("attribute:" + name, oldValue, value);
        firePropertyChange("attributes", null, attributes);
    }

    public void removeAttribute(String name) {
        String oldValue = attributes.remove(name);
        firePropertyChange("attribute:" + name, oldValue, null);
        firePropertyChange("attributes", null, attributes);
    }

    // Deep copy
    @Override
    public XmlElement deepCopy(String suffix) {
        XmlElement copy = new XmlElement(name + suffix);
        copy.namespace = namespace;
        copy.attributes.putAll(attributes);
        for (XmlNode child : children) {
            copy.addChild(child.deepCopy(suffix));
        }
        return copy;
    }

    // Serialization
    @Override
    public String serialize(int indent) {
        StringBuilder sb = new StringBuilder();
        String indentStr = "  ".repeat(indent);

        sb.append(indentStr).append("<").append(name);
        attributes.forEach((k, v) ->
            sb.append(" ").append(k).append("=\"").append(v).append("\""));

        if (children.isEmpty()) {
            sb.append("/>");
        } else {
            sb.append(">");
            for (XmlNode child : children) {
                sb.append("\n").append(child.serialize(indent + 1));
            }
            sb.append("\n").append(indentStr).append("</").append(name).append(">");
        }

        return sb.toString();
    }

    // Getters
    @Override
    public String getName() { return name; }
    public List<XmlNode> getChildren() { return new ArrayList<>(children); }
    public Map<String, String> getAttributes() { return new LinkedHashMap<>(attributes); }
    public String getAttributeValue(String name) { return attributes.get(name); }
}
```

### 3.3 Other Node Types (Brief)

```java
public class XmlText extends XmlNode {
    private String text;

    public void setText(String text) {
        String oldText = this.text;
        this.text = text;
        firePropertyChange("text", oldText, text);
    }

    @Override
    public String getName() { return "#text"; }
}

public class XmlComment extends XmlNode {
    private String comment;
    @Override
    public String getName() { return "#comment"; }
}

public class XmlCData extends XmlNode {
    private String data;
    @Override
    public String getName() { return "#cdata"; }
}

public class XmlProcessingInstruction extends XmlNode {
    private String target;
    private String data;
    @Override
    public String getName() { return "#pi"; }
}

public class XmlDocument extends XmlNode {
    private XmlElement rootElement;
    private String version = "1.0";
    private String encoding = "UTF-8";
    private boolean standalone;
    @Override
    public String getName() { return "#document"; }
}
```

---

## 4. COMMAND PATTERN IMPLEMENTATION

### 4.1 XmlCommand Interface

```java
package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

public interface XmlCommand {
    boolean execute();
    boolean undo();
    String getDescription();
    boolean canUndo();

    default boolean canMergeWith(XmlCommand other) {
        return false;
    }

    default XmlCommand mergeWith(XmlCommand other) {
        return this;
    }
}
```

### 4.2 CommandManager

```java
public class CommandManager {
    private final Deque<XmlCommand> undoStack = new ArrayDeque<>(100);
    private final Deque<XmlCommand> redoStack = new ArrayDeque<>(100);
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final int historyLimit = 100;

    public void executeCommand(XmlCommand command) {
        if (command.execute()) {
            // Try to merge with last command
            if (!undoStack.isEmpty()) {
                XmlCommand last = undoStack.peek();
                if (last.canMergeWith(command)) {
                    undoStack.pop();
                    XmlCommand merged = last.mergeWith(command);
                    undoStack.push(merged);
                    fireCommandExecuted(merged);
                    return;
                }
            }

            undoStack.push(command);
            redoStack.clear();

            // Limit history size
            if (undoStack.size() > historyLimit) {
                ((ArrayDeque<XmlCommand>) undoStack).removeLast();
            }

            fireCommandExecuted(command);
        }
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            XmlCommand command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            fireCanUndoChanged();
            fireCanRedoChanged();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            XmlCommand command = redoStack.pop();
            command.execute();
            undoStack.push(command);
            fireCanUndoChanged();
            fireCanRedoChanged();
        }
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        fireCanUndoChanged();
        fireCanRedoChanged();
    }

    // PropertyChangeSupport integration
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    private void fireCommandExecuted(XmlCommand command) {
        pcs.firePropertyChange("commandExecuted", null, command);
        fireCanUndoChanged();
        fireCanRedoChanged();
    }

    private void fireCanUndoChanged() {
        pcs.firePropertyChange("canUndo", null, canUndo());
    }

    private void fireCanRedoChanged() {
        pcs.firePropertyChange("canRedo", null, canRedo());
    }
}
```

### 4.3 Example Command: AddElementCommand

```java
public class AddElementCommand implements XmlCommand {
    private final XmlElement parent;
    private final XmlElement newChild;
    private final int index;

    public AddElementCommand(XmlElement parent, XmlElement newChild) {
        this(parent, newChild, parent.getChildren().size());
    }

    public AddElementCommand(XmlElement parent, XmlElement newChild, int index) {
        this.parent = parent;
        this.newChild = newChild;
        this.index = index;
    }

    @Override
    public boolean execute() {
        parent.addChild(index, newChild);
        return true;
    }

    @Override
    public boolean undo() {
        parent.removeChild(newChild);
        return true;
    }

    @Override
    public String getDescription() {
        return "Add element '" + newChild.getName() + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }
}
```

---

## 5. GRID VIEW IMPLEMENTATION

### 5.1 XmlGridView Architecture

```java
public class XmlGridView extends VBox {
    private final TableView<XmlElement> tableView;
    private final XmlElement parentElement;
    private final String repeatingElementName;
    private final CommandManager commandManager;
    private final SchemaCache schemaCache;

    public XmlGridView(XmlElement parentElement, String repeatingElementName,
                       CommandManager commandManager, SchemaCache schemaCache) {
        this.parentElement = parentElement;
        this.repeatingElementName = repeatingElementName;
        this.commandManager = commandManager;
        this.schemaCache = schemaCache;
        this.tableView = new TableView<>();

        // Enable virtualization
        tableView.setFixedCellSize(25.0);

        // Create columns
        createColumns();

        // Populate data
        refreshData();

        // Listen to model changes
        parentElement.addPropertyChangeListener(this::onModelChanged);

        // Build UI
        buildUI();
    }

    private void createColumns() {
        // Row # column (read-only)
        TableColumn<XmlElement, Integer> rowNumCol = new TableColumn<>("#");
        rowNumCol.setCellValueFactory(param ->
            new ReadOnlyObjectWrapper<>(
                tableView.getItems().indexOf(param.getValue()) + 1));
        rowNumCol.setSortable(false);
        rowNumCol.setResizable(false);
        rowNumCol.setPrefWidth(50);
        tableView.getColumns().add(rowNumCol);

        // Collect all unique attributes
        Set<String> allAttributes = getRepeatingElements().stream()
            .flatMap(e -> e.getAttributes().keySet().stream())
            .collect(Collectors.toSet());

        // Create attribute columns
        for (String attrName : allAttributes) {
            tableView.getColumns().add(createAttributeColumn(attrName));
        }

        // Collect all simple text children
        Map<String, Boolean> childrenTypes = analyzeChildrenTypes();

        // Create text child columns
        for (Map.Entry<String, Boolean> entry : childrenTypes.entrySet()) {
            if (entry.getValue()) {  // Simple text child
                tableView.getColumns().add(createTextChildColumn(entry.getKey()));
            } else {  // Complex child
                tableView.getColumns().add(createComplexChildColumn(entry.getKey()));
            }
        }
    }

    private TableColumn<XmlElement, String> createAttributeColumn(String attrName) {
        TableColumn<XmlElement, String> col = new TableColumn<>("@" + attrName);
        col.setCellValueFactory(param ->
            new SimpleStringProperty(param.getValue().getAttributeValue(attrName)));

        // Get XSD type if available
        XsdType type = getAttributeType(attrName);
        col.setCellFactory(param -> createCellEditor(type));

        col.setOnEditCommit(event -> {
            XmlElement element = event.getRowValue();
            String newValue = event.getNewValue();
            EditAttributeCommand cmd = new EditAttributeCommand(element, attrName, newValue);
            commandManager.executeCommand(cmd);
        });

        return col;
    }

    private TableCell<XmlElement, String> createCellEditor(XsdType type) {
        switch (type) {
            case XS_INT:
            case XS_DECIMAL:
                return new TextFieldTableCell<>(new NumberStringConverter());
            case XS_BOOLEAN:
                return new CheckBoxTableCell<>();
            case XS_DATE:
                return new DatePickerTableCell<>();
            case ENUMERATION:
                return new ComboBoxTableCell<>(getEnumerationValues());
            case BASE64_BINARY:
                return new ImageTableCell();
            default:
                return new TextFieldTableCell<>();
        }
    }

    private void buildUI() {
        // Toolbar
        HBox toolbar = new HBox(5);
        Button addRowBtn = new Button("Add Row", new FontIcon(BootstrapIcons.PLUS));
        Button deleteRowBtn = new Button("Delete Row", new FontIcon(BootstrapIcons.TRASH));
        Button duplicateRowBtn = new Button("Duplicate", new FontIcon(BootstrapIcons.FILES));
        Button settingsBtn = new Button("Settings", new FontIcon(BootstrapIcons.GEAR));

        addRowBtn.setOnAction(e -> addRow());
        deleteRowBtn.setOnAction(e -> deleteSelectedRows());
        duplicateRowBtn.setOnAction(e -> duplicateSelectedRow());
        settingsBtn.setOnAction(e -> showGridSettings());

        toolbar.getChildren().addAll(addRowBtn, deleteRowBtn, duplicateRowBtn,
            new Separator(Orientation.VERTICAL), settingsBtn);

        // Filter field
        TextField filterField = new TextField();
        filterField.setPromptText("Filter...");
        filterField.textProperty().addListener((obs, old, newVal) -> filterRows(newVal));

        // Layout
        getChildren().addAll(toolbar, filterField, tableView);
        VBox.setVgrow(tableView, Priority.ALWAYS);
    }
}
```

### 5.2 Image Display Cell

```java
public class ImageTableCell extends TableCell<XmlElement, String> {
    private final ImageView imageView = new ImageView();
    private final Button importBtn = new Button("Import...");

    public ImageTableCell() {
        imageView.setFitWidth(64);
        imageView.setFitHeight(64);
        imageView.setPreserveRatio(true);

        importBtn.setOnAction(e -> importImage());

        setGraphic(new VBox(5, imageView, importBtn));
    }

    @Override
    protected void updateItem(String base64, boolean empty) {
        super.updateItem(base64, empty);

        if (empty || base64 == null || base64.isEmpty()) {
            imageView.setImage(null);
        } else {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64);
                Image image = new Image(new ByteArrayInputStream(bytes));
                imageView.setImage(image);
            } catch (Exception e) {
                // Show error icon
                imageView.setImage(getErrorIcon());
            }
        }
    }

    private void importImage() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.gif", "*.bmp"));
        File file = fc.showOpenDialog(getScene().getWindow());

        if (file != null) {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                commitEdit(base64);
            } catch (IOException e) {
                showError("Failed to import image: " + e.getMessage());
            }
        }
    }
}
```

---

## 6. XSD INTEGRATION IMPLEMENTATION

### 6.1 EntryHelperFactory

```java
public class EntryHelperFactory {
    public static Node createEditor(XsdType type, String currentValue) {
        switch (type) {
            case XS_STRING:
                return new TextField(currentValue);

            case XS_INT:
            case XS_DECIMAL:
                Spinner<Number> spinner = new Spinner<>();
                return spinner;

            case XS_BOOLEAN:
                CheckBox checkbox = new CheckBox();
                checkbox.setSelected("true".equals(currentValue));
                return checkbox;

            case XS_DATE:
                DatePicker datePicker = new DatePicker();
                if (currentValue != null) {
                    datePicker.setValue(LocalDate.parse(currentValue));
                }
                return datePicker;

            case XS_ANYURI:
                HBox box = new HBox(5);
                TextField urlField = new TextField(currentValue);
                Button browseBtn = new Button("Browse...");
                browseBtn.setOnAction(e -> browseForFile(urlField));
                box.getChildren().addAll(urlField, browseBtn);
                return box;

            case XS_IDREF:
                ComboBox<String> idrefBox = new ComboBox<>();
                idrefBox.getItems().addAll(getAllIdsInDocument());
                idrefBox.setValue(currentValue);
                return idrefBox;

            case XS_BASE64_BINARY:
                VBox imageBox = new VBox(5);
                TextArea textArea = new TextArea(currentValue);
                textArea.setPrefRowCount(3);
                Button importBtn = new Button("Import File...");
                importBtn.setOnAction(e -> importFileAsBase64(textArea));
                imageBox.getChildren().addAll(textArea, importBtn);
                return imageBox;

            case XS_QNAME:
                HBox qnameBox = new HBox(5);
                ComboBox<String> prefixBox = new ComboBox<>();
                prefixBox.getItems().addAll(getNamespacePrefixes());
                TextField localNameField = new TextField();
                qnameBox.getChildren().addAll(prefixBox, new Label(":"), localNameField);
                return qnameBox;

            case ENUMERATION:
                ComboBox<String> enumBox = new ComboBox<>();
                enumBox.getItems().addAll(getEnumerationValues());
                enumBox.setValue(currentValue);
                return enumBox;

            case PATTERN_EMAIL:
                TextField emailField = new TextField(currentValue);
                Label hintLabel = new Label("Format: user@example.com");
                hintLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
                VBox emailBox = new VBox(3, emailField, hintLabel);
                return emailBox;

            default:
                return new TextField(currentValue);
        }
    }
}
```

---

## 7. IMPLEMENTATION ROADMAP

### 16-Week Timeline

**Phase 1: Model Layer (Week 1-2)**
- Implement XmlNode base class with PropertyChangeSupport
- Create all node types: XmlDocument, XmlElement, XmlText, XmlAttribute, XmlComment, XmlCData, XmlProcessingInstruction
- Add UUID-based IDs
- Implement deepCopy() for all types
- Write comprehensive model tests (100% coverage target)

**Phase 2: Command Pattern (Week 3-4)**
- Implement XmlCommand interface
- Create CommandManager with dual-stack undo/redo
- Implement all 25+ commands
- Add command merging support
- Write command tests (execute/undo verification)

**Phase 3: Serialization (Week 5)**
- Implement XmlSerializer (Model → XML)
- Implement XmlParser (XML → Model)
- Add IncrementalXmlParser for large files
- Add PrettyPrinter
- Write round-trip tests

**Phase 4: Editor Context (Week 6)**
- Implement XmlEditorContext
- Create SelectionModel
- Integrate CommandManager, ValidationManager, IntelliSenseManager
- Add dirty flag management

**Phase 5: Text View (Week 7)**
- Implement XmlTextView (RichTextFX wrapper)
- Add syntax highlighting (async, debounced)
- Add code folding
- Add PropertyChangeListener bindings
- Implement View → Model sync (text edits → commands)

**Phase 6: Tree View (Week 8)**
- Implement XmlTreeView (TreeView wrapper)
- Create VisualXmlNode renderers
- Add context menus
- Add drag & drop
- Add PropertyChangeListener bindings

**Phase 7: Grid View (Week 9)**
- Implement XmlGridView (TableView wrapper)
- Add auto-column detection
- Implement inline editing with type-aware cells
- Add row operations (Add, Delete, Duplicate, Move)
- Add sort & filter
- Add column management

**Phase 8: XSD Integration (Week 10)**
- Implement SchemaCache
- Create SchemaAnalyzer
- Build ElementSuggester and AttributeSuggester
- Implement TypeMapper
- Create TemplateGenerator
- Integrate with IntelliSense

**Phase 9: Validation & IntelliSense (Week 11)**
- Implement XmlValidationManager
- Create XsdValidator and SchematronValidator
- Add ValidationError and QuickFix
- Implement XmlIntelliSenseManager
- Add XmlCompletionEngine
- Integrate with views (error highlighting, completion popup)

**Phase 10: Enhanced Features (Week 12)**
- Implement EntryHelperFactory (15+ type-aware editors)
- Add ImageTableCell (Base64 display)
- Create GridSettingsDialog
- Implement Tree + Grid split view layout
- Add all entry helpers (IDREF, base64Binary, anyURI, QName, etc.)

**Phase 11: Performance Optimization (Week 13)**
- Implement VirtualizationManager
- Add LazyLoader for large files
- Create PerformanceMonitor
- Add CacheManager
- Run performance benchmarks
- Optimize hot paths

**Phase 12: Testing (Week 14)**
- Write comprehensive test suite
- Add integration tests
- Add UI tests (TestFX)
- Add performance tests
- Fix bugs from testing

**Phase 13: Documentation (Week 15)**
- Complete JavaDoc for all public APIs
- Update CLAUDE.md
- Write user guide
- Create migration guide (V1 → V2)

**Phase 14: Release (Week 16)**
- Final testing
- Feature flag: xml.editor.use.v2=true (default)
- Create release notes
- Deploy V2 as default

---

## APPENDIX: Code Examples

### Example: Using the Editor

```java
// Create XML document
XmlDocument doc = new XmlDocument();
XmlElement root = new XmlElement("persons");
doc.setRootElement(root);

// Create editor context
XmlEditorContext context = new XmlEditorContext(doc);

// Add element via command
XmlElement person = new XmlElement("person");
person.setAttribute("id", "p001");
AddElementCommand cmd = new AddElementCommand(root, person);
context.getCommandManager().executeCommand(cmd);

// Create views
XmlTextView textView = new XmlTextView(context);
XmlTreeView treeView = new XmlTreeView(context);
XmlGridView gridView = new XmlGridView(root, "person",
    context.getCommandManager(), context.getSchemaCache());

// Views auto-sync via PropertyChangeEvents
```

### Example: Undo/Redo

```java
// User edits
commandManager.executeCommand(new AddElementCommand(...));
commandManager.executeCommand(new EditTextCommand(...));
commandManager.executeCommand(new RenameNodeCommand(...));

// Undo all
commandManager.undo();  // Undo rename
commandManager.undo();  // Undo edit
commandManager.undo();  // Undo add

// Redo
commandManager.redo();  // Redo add
commandManager.redo();  // Redo edit
```

---

**END OF IMPLEMENTATION GUIDE**

This guide provides the essential technical details for implementing XML Editor V2.

**For complete requirements:** See `XML_EDITOR_V2_REQUIREMENTS.md`
**For complete feature list:** See `XML_EDITOR_V2_FEATURES.md`
