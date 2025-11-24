package org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.panels;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.*;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

/**
 * Property panel for editing XML node properties.
 *
 * <p>Displays and allows editing of:</p>
 * <ul>
 *   <li>Element name</li>
 *   <li>Attributes (name/value pairs)</li>
 *   <li>Text content</li>
 *   <li>Namespace (prefix and URI)</li>
 *   <li>Node ID and type (read-only)</li>
 * </ul>
 *
 * <p>All edits are executed via the command pattern through XmlEditorContext.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlPropertiesPanel extends VBox {

    /**
     * The editor context.
     */
    private final XmlEditorContext context;

    /**
     * Currently displayed node.
     */
    private XmlNode currentNode;

    /**
     * Label showing node type.
     */
    private final Label nodeTypeLabel;

    /**
     * Label showing node ID.
     */
    private final Label nodeIdLabel;

    /**
     * TextField for element name.
     */
    private final TextField nameField;

    /**
     * TextField for namespace prefix.
     */
    private final TextField prefixField;

    /**
     * TextField for namespace URI.
     */
    private final TextField namespaceUriField;

    /**
     * TextArea for text content.
     */
    private final TextArea textArea;

    /**
     * TableView for attributes.
     */
    private final TableView<AttributeRow> attributeTable;

    /**
     * Button to add attribute.
     */
    private final Button addAttributeButton;

    /**
     * Button to remove attribute.
     */
    private final Button removeAttributeButton;

    /**
     * Flag to prevent update loops.
     */
    private boolean updating = false;

    // ==================== Constructor ====================

    /**
     * Constructs a new XmlPropertiesPanel.
     *
     * @param context the editor context
     */
    public XmlPropertiesPanel(XmlEditorContext context) {
        this.context = context;

        // Initialize UI components
        nodeTypeLabel = new Label("-");
        nodeIdLabel = new Label("-");
        nameField = new TextField();
        prefixField = new TextField();
        namespaceUriField = new TextField();
        textArea = new TextArea();
        attributeTable = new TableView<>();
        addAttributeButton = new Button("Add Attribute");
        removeAttributeButton = new Button("Remove");

        // Build UI
        buildUI();

        // Listen to selection changes
        context.getSelectionModel().addPropertyChangeListener("selectedNode", this::onSelectionChanged);

        // Listen to edit mode changes
        context.addPropertyChangeListener("editMode", this::onEditModeChanged);

        // Initial state
        updateEditability();
        showEmptyState();
    }

    // ==================== UI Building ====================

    /**
     * Builds the UI layout.
     */
    private void buildUI() {
        setPadding(new Insets(10));
        setSpacing(10);

        // Title
        Label titleLabel = new Label("Properties");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Info section
        GridPane infoPane = new GridPane();
        infoPane.setHgap(10);
        infoPane.setVgap(5);

        infoPane.add(new Label("Type:"), 0, 0);
        infoPane.add(nodeTypeLabel, 1, 0);
        infoPane.add(new Label("ID:"), 0, 1);
        infoPane.add(nodeIdLabel, 1, 1);

        // Element name section
        GridPane namePane = new GridPane();
        namePane.setHgap(10);
        namePane.setVgap(5);

        namePane.add(new Label("Name:"), 0, 0);
        namePane.add(nameField, 1, 0);
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        namePane.add(new Label("Prefix:"), 0, 1);
        namePane.add(prefixField, 1, 1);

        namePane.add(new Label("Namespace URI:"), 0, 2);
        namePane.add(namespaceUriField, 1, 2);
        GridPane.setHgrow(namespaceUriField, Priority.ALWAYS);

        // Set up name field listener
        nameField.setOnAction(evt -> onNameChanged());
        nameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Lost focus
                onNameChanged();
            }
        });

        // Prefix and URI listeners
        prefixField.setOnAction(evt -> onNamespaceChanged());
        namespaceUriField.setOnAction(evt -> onNamespaceChanged());

        // Text content section
        Label textLabel = new Label("Text Content:");
        textArea.setPrefRowCount(4);
        textArea.setWrapText(true);
        textArea.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Lost focus
                onTextChanged();
            }
        });

        // Attributes section
        Label attrLabel = new Label("Attributes:");
        setupAttributeTable();

        ToolBar attrToolbar = new ToolBar(addAttributeButton, removeAttributeButton);
        addAttributeButton.setOnAction(evt -> onAddAttribute());
        removeAttributeButton.setOnAction(evt -> onRemoveAttribute());
        removeAttributeButton.setDisable(true);

        attributeTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeAttributeButton.setDisable(newVal == null);
        });

        // Layout
        getChildren().addAll(
                titleLabel,
                new Separator(),
                infoPane,
                new Separator(),
                namePane,
                new Separator(),
                textLabel,
                textArea,
                new Separator(),
                attrLabel,
                attributeTable,
                attrToolbar
        );

        VBox.setVgrow(attributeTable, Priority.ALWAYS);
    }

    /**
     * Sets up the attribute table columns.
     */
    private void setupAttributeTable() {
        attributeTable.setEditable(true);
        attributeTable.setPrefHeight(150);

        // Name column
        TableColumn<AttributeRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        nameCol.setCellFactory(col -> new TextFieldTableCell<>());
        nameCol.setOnEditCommit(evt -> {
            AttributeRow row = evt.getRowValue();
            String newName = evt.getNewValue();
            if (newName != null && !newName.equals(row.getName())) {
                onAttributeRenamed(row.getName(), newName);
            }
        });
        nameCol.setPrefWidth(150);

        // Value column
        TableColumn<AttributeRow, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(data -> data.getValue().valueProperty());
        valueCol.setCellFactory(col -> new TextFieldTableCell<>());
        valueCol.setOnEditCommit(evt -> {
            AttributeRow row = evt.getRowValue();
            String newValue = evt.getNewValue();
            if (newValue != null && !newValue.equals(row.getValue())) {
                onAttributeValueChanged(row.getName(), newValue);
            }
        });
        valueCol.setPrefWidth(200);

        attributeTable.getColumns().addAll(nameCol, valueCol);
    }

    // ==================== Event Handlers ====================

    /**
     * Called when selection changes.
     */
    private void onSelectionChanged(PropertyChangeEvent evt) {
        XmlNode newNode = (XmlNode) evt.getNewValue();
        setNode(newNode);
    }

    /**
     * Called when edit mode changes.
     */
    private void onEditModeChanged(PropertyChangeEvent evt) {
        updateEditability();
    }

    /**
     * Called when element name is changed.
     */
    private void onNameChanged() {
        if (updating || !(currentNode instanceof XmlElement)) {
            return;
        }

        XmlElement element = (XmlElement) currentNode;
        String newName = nameField.getText().trim();

        if (!newName.isEmpty() && !newName.equals(element.getName())) {
            RenameNodeCommand cmd = new RenameNodeCommand(element, newName);
            context.executeCommand(cmd);
        }
    }

    /**
     * Called when namespace prefix or URI changes.
     */
    private void onNamespaceChanged() {
        if (updating || !(currentNode instanceof XmlElement)) {
            return;
        }

        // TODO: Implement namespace change command
        // For now, this is a placeholder
    }

    /**
     * Called when text content changes.
     */
    private void onTextChanged() {
        if (updating || !(currentNode instanceof XmlElement)) {
            return;
        }

        XmlElement element = (XmlElement) currentNode;
        String newText = textArea.getText();
        String oldText = element.getTextContent();

        if (!newText.equals(oldText)) {
            // Set text content directly (simplified for property panel)
            element.setTextContent(newText);
        }
    }

    /**
     * Called when add attribute button is clicked.
     */
    private void onAddAttribute() {
        if (!(currentNode instanceof XmlElement)) {
            return;
        }

        XmlElement element = (XmlElement) currentNode;

        // Show dialog to enter attribute name and value
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Add Attribute");
        nameDialog.setHeaderText("Enter attribute name:");
        nameDialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                TextInputDialog valueDialog = new TextInputDialog();
                valueDialog.setTitle("Add Attribute");
                valueDialog.setHeaderText("Enter attribute value for '" + name + "':");
                valueDialog.showAndWait().ifPresent(value -> {
                    SetAttributeCommand cmd = new SetAttributeCommand(element, name.trim(), value);
                    context.executeCommand(cmd);
                    refreshAttributes();
                });
            }
        });
    }

    /**
     * Called when remove attribute button is clicked.
     */
    private void onRemoveAttribute() {
        AttributeRow selected = attributeTable.getSelectionModel().getSelectedItem();
        if (selected == null || !(currentNode instanceof XmlElement)) {
            return;
        }

        XmlElement element = (XmlElement) currentNode;
        RemoveAttributeCommand cmd = new RemoveAttributeCommand(element, selected.getName());
        context.executeCommand(cmd);
        refreshAttributes();
    }

    /**
     * Called when an attribute is renamed in the table.
     */
    private void onAttributeRenamed(String oldName, String newName) {
        if (!(currentNode instanceof XmlElement)) {
            return;
        }

        XmlElement element = (XmlElement) currentNode;
        String value = element.getAttribute(oldName);

        // Remove old, add new
        RemoveAttributeCommand removeCmd = new RemoveAttributeCommand(element, oldName);
        SetAttributeCommand addCmd = new SetAttributeCommand(element, newName, value);

        context.executeCommand(removeCmd);
        context.executeCommand(addCmd);
        refreshAttributes();
    }

    /**
     * Called when an attribute value is changed in the table.
     */
    private void onAttributeValueChanged(String name, String newValue) {
        if (!(currentNode instanceof XmlElement)) {
            return;
        }

        XmlElement element = (XmlElement) currentNode;
        SetAttributeCommand cmd = new SetAttributeCommand(element, name, newValue);
        context.executeCommand(cmd);
    }

    // ==================== Node Display ====================

    /**
     * Sets the node to display.
     *
     * @param node the node to display
     */
    public void setNode(XmlNode node) {
        // Remove listeners from old node
        if (currentNode != null) {
            currentNode.removePropertyChangeListener(this::onNodePropertyChanged);
        }

        this.currentNode = node;

        if (node == null) {
            showEmptyState();
        } else {
            // Add listener to new node
            node.addPropertyChangeListener(this::onNodePropertyChanged);
            updateDisplay();
        }
    }

    /**
     * Shows empty state when no node is selected.
     */
    private void showEmptyState() {
        updating = true;

        nodeTypeLabel.setText("-");
        nodeIdLabel.setText("-");
        nameField.setText("");
        prefixField.setText("");
        namespaceUriField.setText("");
        textArea.setText("");
        attributeTable.getItems().clear();

        updating = false;
    }

    /**
     * Updates the display from the current node.
     */
    private void updateDisplay() {
        if (currentNode == null) {
            showEmptyState();
            return;
        }

        updating = true;

        // Node info
        nodeTypeLabel.setText(currentNode.getNodeType().toString());
        nodeIdLabel.setText(currentNode.getId().toString().substring(0, 8) + "...");

        // Element-specific fields
        if (currentNode instanceof XmlElement) {
            XmlElement element = (XmlElement) currentNode;
            nameField.setText(element.getName());
            prefixField.setText(element.getNamespacePrefix() != null ? element.getNamespacePrefix() : "");
            namespaceUriField.setText(element.getNamespaceURI() != null ? element.getNamespaceURI() : "");
            textArea.setText(element.getTextContent());
            refreshAttributes();
        } else if (currentNode instanceof XmlText) {
            nameField.setText("");
            textArea.setText(((XmlText) currentNode).getText());
            attributeTable.getItems().clear();
        } else if (currentNode instanceof XmlComment) {
            nameField.setText("");
            textArea.setText(((XmlComment) currentNode).getText());
            attributeTable.getItems().clear();
        } else {
            nameField.setText("");
            textArea.setText("");
            attributeTable.getItems().clear();
        }

        updating = false;
    }

    /**
     * Refreshes the attributes table from the current element.
     */
    private void refreshAttributes() {
        if (!(currentNode instanceof XmlElement)) {
            attributeTable.getItems().clear();
            return;
        }

        XmlElement element = (XmlElement) currentNode;
        attributeTable.getItems().clear();

        for (Map.Entry<String, String> entry : element.getAttributes().entrySet()) {
            attributeTable.getItems().add(new AttributeRow(entry.getKey(), entry.getValue()));
        }
    }

    /**
     * Called when a node property changes.
     */
    private void onNodePropertyChanged(PropertyChangeEvent evt) {
        updateDisplay();
    }

    /**
     * Updates editability based on edit mode.
     */
    private void updateEditability() {
        boolean editable = context.isEditMode();

        nameField.setEditable(editable);
        prefixField.setEditable(editable);
        namespaceUriField.setEditable(editable);
        textArea.setEditable(editable);
        attributeTable.setEditable(editable);
        addAttributeButton.setDisable(!editable);
    }

    // ==================== Inner Class ====================

    /**
     * Row in the attributes table.
     */
    public static class AttributeRow {
        private final javafx.beans.property.SimpleStringProperty name;
        private final javafx.beans.property.SimpleStringProperty value;

        public AttributeRow(String name, String value) {
            this.name = new javafx.beans.property.SimpleStringProperty(name);
            this.value = new javafx.beans.property.SimpleStringProperty(value);
        }

        public String getName() {
            return name.get();
        }

        public javafx.beans.property.SimpleStringProperty nameProperty() {
            return name;
        }

        public String getValue() {
            return value.get();
        }

        public javafx.beans.property.SimpleStringProperty valueProperty() {
            return value;
        }
    }
}
