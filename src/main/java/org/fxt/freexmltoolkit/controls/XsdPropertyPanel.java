package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Property panel for editing XSD element and attribute properties
 * Provides detailed editing capabilities with validation and two-way binding
 */
public class XsdPropertyPanel extends VBox {

    private static final Logger logger = LogManager.getLogger(XsdPropertyPanel.class);

    // Built-in XSD types
    private static final List<String> BUILTIN_TYPES = Arrays.asList(
            "xs:string", "xs:int", "xs:integer", "xs:long", "xs:short", "xs:byte",
            "xs:decimal", "xs:float", "xs:double", "xs:boolean", "xs:date", "xs:dateTime",
            "xs:time", "xs:duration", "xs:base64Binary", "xs:hexBinary", "xs:anyURI",
            "xs:QName", "xs:NOTATION", "xs:normalizedString", "xs:token", "xs:language",
            "xs:Name", "xs:NCName", "xs:ID", "xs:IDREF", "xs:IDREFS", "xs:ENTITY",
            "xs:ENTITIES", "xs:NMTOKEN", "xs:NMTOKENS", "xs:positiveInteger",
            "xs:nonNegativeInteger", "xs:negativeInteger", "xs:nonPositiveInteger",
            "xs:unsignedLong", "xs:unsignedInt", "xs:unsignedShort", "xs:unsignedByte"
    );

    private XsdNodeInfo currentNode;
    private XsdDomManipulator domManipulator;
    private Consumer<String> onPropertyChanged;

    // UI Components
    private Label nodeTypeLabel;
    private TextField nameField;
    private ComboBox<String> typeComboBox;
    private TextField minOccursField;
    private TextField maxOccursField;
    private ComboBox<String> useComboBox; // for attributes
    private TextField defaultValueField;
    private TextField fixedValueField;
    private TextArea documentationTextArea;
    private CheckBox nilableCheckBox;
    private CheckBox abstractCheckBox;

    // Property bindings
    private final StringProperty nameProperty = new SimpleStringProperty();
    private final StringProperty typeProperty = new SimpleStringProperty();
    private final StringProperty minOccursProperty = new SimpleStringProperty();
    private final StringProperty maxOccursProperty = new SimpleStringProperty();

    public XsdPropertyPanel() {
        initializeUI();
        setupBindings();
        setDisable(true); // Disabled until a node is selected
    }

    /**
     * Sets the DOM manipulator for making changes
     */
    public void setDomManipulator(XsdDomManipulator manipulator) {
        this.domManipulator = manipulator;
    }

    /**
     * Sets the callback for when properties change
     */
    public void setOnPropertyChanged(Consumer<String> callback) {
        this.onPropertyChanged = callback;
    }

    /**
     * Updates the panel to show properties of the selected node
     */
    public void setSelectedNode(XsdNodeInfo node) {
        this.currentNode = node;

        if (node == null) {
            setDisable(true);
            clearFields();
            return;
        }

        setDisable(false);
        updateFields();
    }

    private void initializeUI() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 1px;");

        // Header
        Label headerLabel = new Label("Element Properties");
        headerLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        getChildren().add(headerLabel);

        // Node type indicator
        nodeTypeLabel = new Label();
        nodeTypeLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 12px;");
        getChildren().add(nodeTypeLabel);

        getChildren().add(new Separator());

        // Properties grid
        GridPane propertiesGrid = new GridPane();
        propertiesGrid.setHgap(10);
        propertiesGrid.setVgap(8);
        propertiesGrid.setPadding(new Insets(5));

        int row = 0;

        // Name field
        propertiesGrid.add(new Label("Name:"), 0, row);
        nameField = new TextField();
        nameField.setPromptText("Element/Attribute name");
        propertiesGrid.add(nameField, 1, row++);

        // Type field
        propertiesGrid.add(new Label("Type:"), 0, row);
        typeComboBox = new ComboBox<>(FXCollections.observableList(BUILTIN_TYPES));
        typeComboBox.setEditable(true);
        typeComboBox.setPromptText("Select or enter type");
        HBox.setHgrow(typeComboBox, Priority.ALWAYS);
        propertiesGrid.add(typeComboBox, 1, row++);

        // Cardinality section
        Label cardinalityLabel = new Label("Cardinality:");
        cardinalityLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");
        propertiesGrid.add(cardinalityLabel, 0, row, 2, 1);
        row++;

        // MinOccurs
        propertiesGrid.add(new Label("Min Occurs:"), 0, row);
        minOccursField = new TextField();
        minOccursField.setPromptText("0, 1, ...");
        minOccursField.setPrefWidth(80);
        propertiesGrid.add(minOccursField, 1, row++);

        // MaxOccurs
        propertiesGrid.add(new Label("Max Occurs:"), 0, row);
        maxOccursField = new TextField();
        maxOccursField.setPromptText("1, unbounded, ...");
        maxOccursField.setPrefWidth(80);
        propertiesGrid.add(maxOccursField, 1, row++);

        // Use field (for attributes)
        propertiesGrid.add(new Label("Use:"), 0, row);
        useComboBox = new ComboBox<>(FXCollections.observableArrayList("optional", "required", "prohibited"));
        useComboBox.setPromptText("Attribute use");
        propertiesGrid.add(useComboBox, 1, row++);

        // Default value
        propertiesGrid.add(new Label("Default:"), 0, row);
        defaultValueField = new TextField();
        defaultValueField.setPromptText("Default value");
        propertiesGrid.add(defaultValueField, 1, row++);

        // Fixed value
        propertiesGrid.add(new Label("Fixed:"), 0, row);
        fixedValueField = new TextField();
        fixedValueField.setPromptText("Fixed value");
        propertiesGrid.add(fixedValueField, 1, row++);

        // Options section
        Label optionsLabel = new Label("Options:");
        optionsLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");
        propertiesGrid.add(optionsLabel, 0, row, 2, 1);
        row++;

        // Nillable checkbox
        nilableCheckBox = new CheckBox("Nillable");
        nilableCheckBox.setTooltip(new Tooltip("Element can have null value"));
        propertiesGrid.add(nilableCheckBox, 0, row, 2, 1);
        row++;

        // Abstract checkbox
        abstractCheckBox = new CheckBox("Abstract");
        abstractCheckBox.setTooltip(new Tooltip("Element is abstract (cannot be used directly)"));
        propertiesGrid.add(abstractCheckBox, 0, row, 2, 1);
        row++;

        getChildren().add(propertiesGrid);

        // Documentation section
        Label docLabel = new Label("Documentation:");
        docLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #495057;");
        getChildren().add(docLabel);

        documentationTextArea = new TextArea();
        documentationTextArea.setPromptText("Element documentation...");
        documentationTextArea.setPrefRowCount(3);
        documentationTextArea.setWrapText(true);
        getChildren().add(documentationTextArea);

        // Action buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        Button applyButton = new Button("Apply Changes");
        applyButton.setGraphic(new FontIcon("bi-check-circle"));
        applyButton.setStyle("-fx-background-color: #28a745; -fx-text-fill: white;");
        applyButton.setOnAction(e -> applyChanges());

        Button resetButton = new Button("Reset");
        resetButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        resetButton.setOnAction(e -> updateFields());

        buttonBox.getChildren().addAll(applyButton, resetButton);
        getChildren().add(buttonBox);
    }

    private void setupBindings() {
        // Setup two-way bindings
        nameField.textProperty().bindBidirectional(nameProperty);
        typeComboBox.valueProperty().bindBidirectional(typeProperty);
        minOccursField.textProperty().bindBidirectional(minOccursProperty);
        maxOccursField.textProperty().bindBidirectional(maxOccursProperty);

        // Add validation listeners
        setupValidation();
    }

    private void setupValidation() {
        // Name validation
        nameField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // Lost focus
                validateName();
            }
        });

        // MinOccurs validation
        minOccursField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                validateCardinality();
            }
        });

        // MaxOccurs validation
        maxOccursField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                validateCardinality();
            }
        });
    }

    private boolean validateName() {
        String name = nameField.getText();
        if (name == null || name.trim().isEmpty()) {
            showFieldError(nameField, "Name cannot be empty");
            return false;
        }

        // Basic XSD name validation
        if (!name.matches("[a-zA-Z_][a-zA-Z0-9_.-]*")) {
            showFieldError(nameField, "Invalid XSD name format");
            return false;
        }

        clearFieldError(nameField);
        return true;
    }

    private boolean validateCardinality() {
        String minOccurs = minOccursField.getText();
        String maxOccurs = maxOccursField.getText();

        // Validate minOccurs
        if (minOccurs != null && !minOccurs.trim().isEmpty()) {
            try {
                int min = Integer.parseInt(minOccurs);
                if (min < 0) {
                    showFieldError(minOccursField, "MinOccurs must be >= 0");
                    return false;
                }
            } catch (NumberFormatException e) {
                showFieldError(minOccursField, "MinOccurs must be a number");
                return false;
            }
        }

        // Validate maxOccurs
        if (maxOccurs != null && !maxOccurs.trim().isEmpty()) {
            if (!"unbounded".equals(maxOccurs)) {
                try {
                    int max = Integer.parseInt(maxOccurs);
                    if (max < 1) {
                        showFieldError(maxOccursField, "MaxOccurs must be >= 1 or 'unbounded'");
                        return false;
                    }

                    // Check min <= max
                    if (minOccurs != null && !minOccurs.trim().isEmpty()) {
                        int min = Integer.parseInt(minOccurs);
                        if (min > max) {
                            showFieldError(maxOccursField, "MaxOccurs must be >= MinOccurs");
                            return false;
                        }
                    }
                } catch (NumberFormatException e) {
                    showFieldError(maxOccursField, "MaxOccurs must be a number or 'unbounded'");
                    return false;
                }
            }
        }

        clearFieldError(minOccursField);
        clearFieldError(maxOccursField);
        return true;
    }

    private void showFieldError(Control field, String message) {
        field.setStyle("-fx-border-color: #dc3545; -fx-border-width: 2px;");
        Tooltip.install(field, new Tooltip(message));
    }

    private void clearFieldError(Control field) {
        field.setStyle("");
        Tooltip.uninstall(field, field.getTooltip());
    }

    private void updateFields() {
        if (currentNode == null) {
            return;
        }

        // Update node type label
        nodeTypeLabel.setText(currentNode.nodeType().toString());
        nodeTypeLabel.setGraphic(getNodeTypeIcon());

        // Update fields with current values
        nameProperty.set(currentNode.name());
        typeProperty.set(currentNode.type() != null ? currentNode.type() : "");
        minOccursProperty.set(currentNode.minOccurs() != null ? currentNode.minOccurs() : "");
        maxOccursProperty.set(currentNode.maxOccurs() != null ? currentNode.maxOccurs() : "");

        // Update attribute-specific fields
        boolean isAttribute = currentNode.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE;
        useComboBox.setVisible(isAttribute);
        useComboBox.setManaged(isAttribute);

        if (isAttribute) {
            // For attributes, use field represents the 'use' attribute
            String use = currentNode.minOccurs();
            if ("1".equals(use)) {
                useComboBox.setValue("required");
            } else {
                useComboBox.setValue("optional");
            }
        }

        // Update documentation
        documentationTextArea.setText(currentNode.documentation() != null ? currentNode.documentation() : "");

        // Hide cardinality fields for attributes (they use 'use' instead)
        minOccursField.setVisible(!isAttribute);
        minOccursField.setManaged(!isAttribute);
        maxOccursField.setVisible(!isAttribute);
        maxOccursField.setManaged(!isAttribute);
    }

    private FontIcon getNodeTypeIcon() {
        return switch (currentNode.nodeType()) {
            case ELEMENT -> new FontIcon("bi-box");
            case ATTRIBUTE -> new FontIcon("bi-at");
            case SEQUENCE -> new FontIcon("bi-list-ol");
            case CHOICE -> new FontIcon("bi-option");
            case ANY -> new FontIcon("bi-asterisk");
            case SIMPLE_TYPE -> new FontIcon("bi-type");
            case COMPLEX_TYPE -> new FontIcon("bi-diagram-3");
            case SCHEMA -> new FontIcon("bi-file-earmark-code");
        };
    }

    private void clearFields() {
        nameProperty.set("");
        typeProperty.set("");
        minOccursProperty.set("");
        maxOccursProperty.set("");
        useComboBox.setValue(null);
        defaultValueField.setText("");
        fixedValueField.setText("");
        documentationTextArea.setText("");
        nilableCheckBox.setSelected(false);
        abstractCheckBox.setSelected(false);
        nodeTypeLabel.setText("");
        nodeTypeLabel.setGraphic(null);
    }

    private void applyChanges() {
        if (currentNode == null || domManipulator == null) {
            return;
        }

        // Validate all fields
        boolean isValid = validateName() && validateCardinality();
        if (!isValid) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation Error");
            alert.setContentText("Please fix the validation errors before applying changes.");
            alert.showAndWait();
            return;
        }

        try {
            String xpath = currentNode.xpath();

            // Update name if changed
            if (!nameField.getText().equals(currentNode.name())) {
                domManipulator.renameElement(xpath, nameField.getText());
            }

            // Update type if changed
            if (!typeComboBox.getValue().equals(currentNode.type())) {
                domManipulator.updateElementProperties(xpath, "type", typeComboBox.getValue());
            }

            // Update cardinality for elements
            if (currentNode.nodeType() != XsdNodeInfo.NodeType.ATTRIBUTE) {
                if (!minOccursField.getText().equals(currentNode.minOccurs())) {
                    domManipulator.updateElementProperties(xpath, "minOccurs", minOccursField.getText());
                }
                if (!maxOccursField.getText().equals(currentNode.maxOccurs())) {
                    domManipulator.updateElementProperties(xpath, "maxOccurs", maxOccursField.getText());
                }
            } else {
                // For attributes, update 'use' instead
                String currentUse = "required".equals(useComboBox.getValue()) ? "required" : "optional";
                domManipulator.updateElementProperties(xpath, "use", currentUse);
            }

            // Update other properties
            domManipulator.updateElementProperties(xpath, "default", defaultValueField.getText());
            domManipulator.updateElementProperties(xpath, "fixed", fixedValueField.getText());

            // Notify that changes were made
            if (onPropertyChanged != null) {
                onPropertyChanged.accept("Properties updated for " + currentNode.name());
            }

            logger.info("Applied property changes for node: {}", currentNode.name());

        } catch (Exception e) {
            logger.error("Failed to apply property changes", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Update Failed");
            alert.setContentText("Failed to update properties: " + e.getMessage());
            alert.showAndWait();
        }
    }
}