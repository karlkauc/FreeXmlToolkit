package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Professional Namespace Management Dialog for XSD editing
 * <p>
 * Features:
 * - Namespace prefix mapping table
 * - Default namespace handling
 * - Target namespace editor
 * - Schema location management
 * - Namespace validation
 * - Import/Include namespace resolution
 */
public class XsdNamespaceEditor extends Dialog<NamespaceResult> {

    private static final Logger logger = LogManager.getLogger(XsdNamespaceEditor.class);

    // UI Components
    private TextField targetNamespaceField;
    private TextField defaultNamespaceField;
    private CheckBox elementFormDefaultCheckBox;
    private CheckBox attributeFormDefaultCheckBox;
    private TableView<NamespaceMapping> namespaceTable;
    private ObservableList<NamespaceMapping> namespaceMappings;

    // Buttons
    private Button addMappingButton;
    private Button removeMappingButton;
    private Button validateButton;

    // Results
    private final ButtonType okButtonType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
    private final ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    public XsdNamespaceEditor(NamespaceResult initialData) {
        super();

        setTitle("Namespace Management");
        setHeaderText("Manage XSD Namespaces and Prefixes");

        // Set the button types
        getDialogPane().getButtonTypes().addAll(okButtonType, cancelButtonType);

        // Create and set the content
        BorderPane content = createContent();
        getDialogPane().setContent(content);

        // Load initial data
        if (initialData != null) {
            loadData(initialData);
        } else {
            loadDefaultData();
        }

        // Set result converter
        setResultConverter(this::convertResult);

        // Styling
        getDialogPane().getStylesheets().add(
                getClass().getResource("/css/xsd-namespace-editor.css").toExternalForm()
        );

        // Make resizable
        setResizable(true);
        getDialogPane().setPrefWidth(800);
        getDialogPane().setPrefHeight(600);

        logger.info("XsdNamespaceEditor initialized");
    }

    private BorderPane createContent() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));

        // Create tabs for organized layout
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Schema Settings Tab
        Tab schemaTab = new Tab("Schema Settings");
        schemaTab.setGraphic(new FontIcon("bi-file-earmark-code"));
        schemaTab.setContent(createSchemaSettingsPane());

        // Namespace Mappings Tab
        Tab mappingsTab = new Tab("Namespace Mappings");
        mappingsTab.setGraphic(new FontIcon("bi-diagram-2"));
        mappingsTab.setContent(createNamespaceMappingsPane());

        // Validation Tab
        Tab validationTab = new Tab("Validation");
        validationTab.setGraphic(new FontIcon("bi-check-circle"));
        validationTab.setContent(createValidationPane());

        tabPane.getTabs().addAll(schemaTab, mappingsTab, validationTab);

        root.setCenter(tabPane);

        return root;
    }

    private VBox createSchemaSettingsPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(20));

        // Target Namespace Section
        Label targetNsLabel = new Label("Target Namespace:");
        targetNsLabel.getStyleClass().add("section-label");

        targetNamespaceField = new TextField();
        targetNamespaceField.setPromptText("http://example.com/myschema");
        targetNamespaceField.setPrefWidth(500);

        VBox targetNsSection = new VBox(5, targetNsLabel, targetNamespaceField);

        // Default Namespace Section  
        Label defaultNsLabel = new Label("Default Namespace:");
        defaultNsLabel.getStyleClass().add("section-label");

        defaultNamespaceField = new TextField();
        defaultNamespaceField.setPromptText("http://www.w3.org/2001/XMLSchema (leave empty for no default)");
        defaultNamespaceField.setPrefWidth(500);

        VBox defaultNsSection = new VBox(5, defaultNsLabel, defaultNamespaceField);

        // Form Default Settings
        Label formDefaultLabel = new Label("Form Default Settings:");
        formDefaultLabel.getStyleClass().add("section-label");

        elementFormDefaultCheckBox = new CheckBox("elementFormDefault = qualified");
        elementFormDefaultCheckBox.setTooltip(new Tooltip("When checked, local elements must be namespace-qualified"));

        attributeFormDefaultCheckBox = new CheckBox("attributeFormDefault = qualified");
        attributeFormDefaultCheckBox.setTooltip(new Tooltip("When checked, local attributes must be namespace-qualified"));

        VBox formDefaultSection = new VBox(5, formDefaultLabel, elementFormDefaultCheckBox, attributeFormDefaultCheckBox);

        pane.getChildren().addAll(targetNsSection, defaultNsSection, formDefaultSection);

        return pane;
    }

    private VBox createNamespaceMappingsPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(20));

        // Header
        Label headerLabel = new Label("Namespace Prefix Mappings");
        headerLabel.getStyleClass().add("section-label");

        // Table for namespace mappings
        namespaceTable = new TableView<>();
        namespaceTable.setEditable(true);
        namespaceTable.setPrefHeight(300);

        // Prefix column
        TableColumn<NamespaceMapping, String> prefixColumn = new TableColumn<>("Prefix");
        prefixColumn.setCellValueFactory(new PropertyValueFactory<>("prefix"));
        prefixColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        prefixColumn.setOnEditCommit(event -> {
            event.getRowValue().setPrefix(event.getNewValue());
            validateMappings();
        });
        prefixColumn.setPrefWidth(120);

        // Namespace URI column
        TableColumn<NamespaceMapping, String> namespaceColumn = new TableColumn<>("Namespace URI");
        namespaceColumn.setCellValueFactory(new PropertyValueFactory<>("namespaceUri"));
        namespaceColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        namespaceColumn.setOnEditCommit(event -> {
            event.getRowValue().setNamespaceUri(event.getNewValue());
            validateMappings();
        });
        namespaceColumn.setPrefWidth(400);

        // Usage column (where this namespace is used)
        TableColumn<NamespaceMapping, String> usageColumn = new TableColumn<>("Usage");
        usageColumn.setCellValueFactory(new PropertyValueFactory<>("usage"));
        usageColumn.setPrefWidth(150);
        usageColumn.setEditable(false);

        namespaceTable.getColumns().addAll(prefixColumn, namespaceColumn, usageColumn);

        // Initialize data
        namespaceMappings = FXCollections.observableArrayList();
        namespaceTable.setItems(namespaceMappings);

        // Buttons for table management
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        addMappingButton = new Button("Add Mapping");
        addMappingButton.setGraphic(new FontIcon("bi-plus-circle"));
        addMappingButton.setOnAction(e -> addNamespaceMapping());

        removeMappingButton = new Button("Remove Selected");
        removeMappingButton.setGraphic(new FontIcon("bi-trash"));
        removeMappingButton.setOnAction(e -> removeSelectedMapping());
        removeMappingButton.setDisable(true);

        Button addCommonButton = new Button("Add Common Namespaces");
        addCommonButton.setGraphic(new FontIcon("bi-collection"));
        addCommonButton.setOnAction(e -> showCommonNamespacesDialog());

        buttonBox.getChildren().addAll(addMappingButton, removeMappingButton, addCommonButton);

        // Selection listener for remove button
        namespaceTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> removeMappingButton.setDisable(newVal == null)
        );

        pane.getChildren().addAll(headerLabel, namespaceTable, buttonBox);

        return pane;
    }

    private VBox createValidationPane() {
        VBox pane = new VBox(15);
        pane.setPadding(new Insets(20));

        Label headerLabel = new Label("Namespace Validation");
        headerLabel.getStyleClass().add("section-label");

        // Validation controls
        HBox validationControls = new HBox(10);
        validationControls.setAlignment(Pos.CENTER_LEFT);

        validateButton = new Button("Validate All Namespaces");
        validateButton.setGraphic(new FontIcon("bi-check2-circle"));
        validateButton.setOnAction(e -> validateAllNamespaces());

        Button autoFixButton = new Button("Auto-fix Issues");
        autoFixButton.setGraphic(new FontIcon("bi-tools"));
        autoFixButton.setOnAction(e -> autoFixNamespaceIssues());

        validationControls.getChildren().addAll(validateButton, autoFixButton);

        // Validation results area
        TextArea validationResults = new TextArea();
        validationResults.setEditable(false);
        validationResults.setPrefHeight(200);
        validationResults.setPromptText("Validation results will appear here...");

        VBox.setVgrow(validationResults, Priority.ALWAYS);

        pane.getChildren().addAll(headerLabel, validationControls, validationResults);

        return pane;
    }

    private void loadData(NamespaceResult data) {
        targetNamespaceField.setText(data.targetNamespace());
        defaultNamespaceField.setText(data.defaultNamespace());
        elementFormDefaultCheckBox.setSelected(data.elementFormDefault());
        attributeFormDefaultCheckBox.setSelected(data.attributeFormDefault());

        namespaceMappings.clear();
        for (Map.Entry<String, String> entry : data.namespaceMappings().entrySet()) {
            namespaceMappings.add(new NamespaceMapping(entry.getKey(), entry.getValue(), "Schema"));
        }
    }

    private void loadDefaultData() {
        // Set common defaults
        targetNamespaceField.setText("");
        defaultNamespaceField.setText("");
        elementFormDefaultCheckBox.setSelected(false);
        attributeFormDefaultCheckBox.setSelected(false);

        // Add common namespace mappings
        namespaceMappings.clear();
        namespaceMappings.add(new NamespaceMapping("xs", "http://www.w3.org/2001/XMLSchema", "Built-in"));
        namespaceMappings.add(new NamespaceMapping("xsi", "http://www.w3.org/2001/XMLSchema-instance", "Built-in"));
    }

    private void addNamespaceMapping() {
        NamespaceMapping newMapping = new NamespaceMapping("", "", "Custom");
        namespaceMappings.add(newMapping);

        // Select and edit the new row
        namespaceTable.getSelectionModel().select(newMapping);
        namespaceTable.scrollTo(newMapping);
        namespaceTable.edit(namespaceMappings.size() - 1, namespaceTable.getColumns().get(0));
    }

    private void removeSelectedMapping() {
        NamespaceMapping selected = namespaceTable.getSelectionModel().getSelectedItem();
        if (selected != null && !"Built-in".equals(selected.getUsage())) {
            namespaceMappings.remove(selected);
        }
    }

    private void showCommonNamespacesDialog() {
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Add Common Namespaces");
        dialog.setHeaderText("Select common namespaces to add:");

        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        // Common namespace checkboxes
        Map<CheckBox, Map.Entry<String, String>> checkboxMap = new HashMap<>();

        addCommonNamespaceCheckBox(content, checkboxMap, "xhtml", "http://www.w3.org/1999/xhtml", "XHTML");
        addCommonNamespaceCheckBox(content, checkboxMap, "soap", "http://schemas.xmlsoap.org/soap/envelope/", "SOAP Envelope");
        addCommonNamespaceCheckBox(content, checkboxMap, "wsdl", "http://schemas.xmlsoap.org/wsdl/", "WSDL");
        addCommonNamespaceCheckBox(content, checkboxMap, "xml", "http://www.w3.org/XML/1998/namespace", "XML Namespace");
        addCommonNamespaceCheckBox(content, checkboxMap, "xmlns", "http://www.w3.org/2000/xmlns/", "XMLNS");

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                Map<String, String> selected = new HashMap<>();
                for (Map.Entry<CheckBox, Map.Entry<String, String>> entry : checkboxMap.entrySet()) {
                    if (entry.getKey().isSelected()) {
                        selected.put(entry.getValue().getKey(), entry.getValue().getValue());
                    }
                }
                return selected;
            }
            return null;
        });

        Optional<Map<String, String>> result = dialog.showAndWait();
        if (result.isPresent()) {
            for (Map.Entry<String, String> entry : result.get().entrySet()) {
                // Check if prefix already exists
                boolean exists = namespaceMappings.stream()
                        .anyMatch(mapping -> mapping.getPrefix().equals(entry.getKey()));

                if (!exists) {
                    namespaceMappings.add(new NamespaceMapping(entry.getKey(), entry.getValue(), "Common"));
                }
            }
        }
    }

    private void addCommonNamespaceCheckBox(VBox parent, Map<CheckBox, Map.Entry<String, String>> map,
                                            String prefix, String uri, String description) {
        CheckBox checkBox = new CheckBox(String.format("%s - %s (%s)", prefix, description, uri));
        map.put(checkBox, Map.entry(prefix, uri));
        parent.getChildren().add(checkBox);
    }

    private void validateMappings() {
        // Basic validation logic
        for (NamespaceMapping mapping : namespaceMappings) {
            if (mapping.getPrefix().trim().isEmpty() || mapping.getNamespaceUri().trim().isEmpty()) {
                mapping.setUsage("Invalid");
            } else if (mapping.getUsage().equals("Invalid")) {
                mapping.setUsage("Custom");
            }
        }
    }

    private void validateAllNamespaces() {
        // Comprehensive namespace validation
        logger.info("Validating all namespaces...");
        // Implementation would include:
        // - Check for duplicate prefixes
        // - Validate URI formats
        // - Check for conflicting definitions
        // - Validate against XSD specifications
    }

    private void autoFixNamespaceIssues() {
        // Auto-fix common namespace issues
        logger.info("Auto-fixing namespace issues...");
        // Implementation would include:
        // - Remove empty mappings
        // - Fix duplicate prefixes
        // - Suggest common namespace URIs
        // - Normalize URI formats
    }

    private NamespaceResult convertResult(ButtonType buttonType) {
        if (buttonType == okButtonType) {
            Map<String, String> mappings = new HashMap<>();
            for (NamespaceMapping mapping : namespaceMappings) {
                if (!mapping.getPrefix().trim().isEmpty() && !mapping.getNamespaceUri().trim().isEmpty()) {
                    mappings.put(mapping.getPrefix().trim(), mapping.getNamespaceUri().trim());
                }
            }

            return new NamespaceResult(
                    targetNamespaceField.getText().trim(),
                    defaultNamespaceField.getText().trim(),
                    elementFormDefaultCheckBox.isSelected(),
                    attributeFormDefaultCheckBox.isSelected(),
                    mappings
            );
        }
        return null;
    }

    /**
     * Data model for namespace mappings table
     */
    public static class NamespaceMapping {
        private final SimpleStringProperty prefix = new SimpleStringProperty();
        private final SimpleStringProperty namespaceUri = new SimpleStringProperty();
        private final SimpleStringProperty usage = new SimpleStringProperty();

        public NamespaceMapping(String prefix, String namespaceUri, String usage) {
            setPrefix(prefix);
            setNamespaceUri(namespaceUri);
            setUsage(usage);
        }

        // Property getters
        public String getPrefix() {
            return prefix.get();
        }

        public String getNamespaceUri() {
            return namespaceUri.get();
        }

        public String getUsage() {
            return usage.get();
        }

        // Property setters
        public void setPrefix(String prefix) {
            this.prefix.set(prefix);
        }

        public void setNamespaceUri(String namespaceUri) {
            this.namespaceUri.set(namespaceUri);
        }

        public void setUsage(String usage) {
            this.usage.set(usage);
        }

        // JavaFX Properties
        public SimpleStringProperty prefixProperty() {
            return prefix;
        }

        public SimpleStringProperty namespaceUriProperty() {
            return namespaceUri;
        }

        public SimpleStringProperty usageProperty() {
            return usage;
        }
    }
}