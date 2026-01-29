package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageFinder;
import org.fxt.freexmltoolkit.controls.v2.editor.usage.TypeUsageLocation;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Type Library View - Shows all types in the schema with their usage information.
 *
 * Features:
 * - List of all SimpleTypes and ComplexTypes
 * - Documentation for each type
 * - XPath locations where each type is used
 * - Highlights unused types
 * - Filter by type kind (Simple/Complex/All)
 * - Search by type name
 *
 * @since 2.0
 */
public class TypeLibraryView extends BorderPane {

    private static final Logger logger = LoggerFactory.getLogger(TypeLibraryView.class);

    private final XsdSchema schema;
    private TableView<TypeInfo> typeTable;
    private TextField searchField;
    private ChoiceBox<String> filterChoice;
    private Label statsLabel;
    private String schemaName = "Unknown Schema";
    private TypeEditorTabManager typeEditorTabManager;
    private Consumer<XsdComplexType> onOpenComplexType;
    private Consumer<XsdSimpleType> onOpenSimpleType;

    /**
     * Creates a new TypeLibraryView.
     *
     * @param schema The schema to display types for
     */
    public TypeLibraryView(XsdSchema schema) {
        this.schema = schema;
        if (schema != null && schema.getTargetNamespace() != null) {
            this.schemaName = schema.getTargetNamespace();
        }
        initializeUI();
        populateTypes();
    }

    /**
     * Set the schema name for export file naming.
     *
     * @param schemaName The name of the schema
     */
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    /**
     * Set the TypeEditorTabManager for creating new types.
     * Required for the "Create New Type" buttons to work.
     *
     * @param typeEditorTabManager The manager for type editor tabs
     */
    public void setTypeEditorTabManager(TypeEditorTabManager typeEditorTabManager) {
        this.typeEditorTabManager = typeEditorTabManager;
    }

    /**
     * Set the callback for opening a ComplexType in the editor.
     * This callback should also switch to the Type Editor tab.
     *
     * @param callback The callback that receives the ComplexType to open
     */
    public void setOnOpenComplexType(Consumer<XsdComplexType> callback) {
        this.onOpenComplexType = callback;
    }

    /**
     * Set the callback for opening a SimpleType in the editor.
     * This callback should also switch to the Type Editor tab.
     *
     * @param callback The callback that receives the SimpleType to open
     */
    public void setOnOpenSimpleType(Consumer<XsdSimpleType> callback) {
        this.onOpenSimpleType = callback;
    }

    private void initializeUI() {
        // Top toolbar - XMLSpy style
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(8));
        toolbar.setStyle(
            "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef);" +
            "-fx-border-color: #dee2e6;" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 1, 1);"
        );

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search types...");
        searchField.setPrefWidth(250);
        searchField.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-border-color: #c0c0c0;" +
            "-fx-border-radius: 3px;" +
            "-fx-background-radius: 3px;"
        );
        searchField.textProperty().addListener((obs, old, newVal) -> filterTypes());

        // Filter choice
        filterChoice = new ChoiceBox<>();
        filterChoice.setItems(FXCollections.observableArrayList("All Types", "Simple Types", "Complex Types", "Unused Types"));
        filterChoice.setValue("All Types");
        filterChoice.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        filterChoice.valueProperty().addListener((obs, old, newVal) -> filterTypes());

        // Labels with XMLSpy styling
        Label searchLabel = new Label("Search:");
        searchLabel.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #333333;"
        );

        Label filterLabel = new Label("Filter:");
        filterLabel.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #333333;"
        );

        // Stats label
        statsLabel = new Label();
        statsLabel.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: #2c5aa0;"
        );

        // Create buttons for new types
        Button createComplexTypeBtn = new Button("New ComplexType");
        createComplexTypeBtn.setGraphic(new FontIcon("bi-file-earmark-plus"));
        ((FontIcon) createComplexTypeBtn.getGraphic()).setIconColor(javafx.scene.paint.Color.web("#28a745"));
        ((FontIcon) createComplexTypeBtn.getGraphic()).setIconSize(16);
        createComplexTypeBtn.setStyle("-fx-font-size: 11px;");
        createComplexTypeBtn.setOnAction(e -> {
            if (typeEditorTabManager != null) {
                typeEditorTabManager.createNewComplexType();
            }
        });

        Button createSimpleTypeBtn = new Button("New SimpleType");
        createSimpleTypeBtn.setGraphic(new FontIcon("bi-plus-circle"));
        ((FontIcon) createSimpleTypeBtn.getGraphic()).setIconColor(javafx.scene.paint.Color.web("#17a2b8"));
        ((FontIcon) createSimpleTypeBtn.getGraphic()).setIconSize(16);
        createSimpleTypeBtn.setStyle("-fx-font-size: 11px;");
        createSimpleTypeBtn.setOnAction(e -> {
            if (typeEditorTabManager != null) {
                typeEditorTabManager.createNewSimpleType();
            }
        });

        // Export button with menu
        MenuButton exportButton = createExportButton();

        Separator separator = new Separator();
        separator.setStyle("-fx-padding: 0 5 0 5;");

        HBox.setHgrow(searchField, Priority.NEVER);
        toolbar.getChildren().addAll(
            createComplexTypeBtn, createSimpleTypeBtn,
            separator,
            searchLabel, searchField,
            filterLabel, filterChoice,
            createSpacer(),
            statsLabel,
            exportButton
        );

        setTop(toolbar);

        // Create table
        typeTable = createTypeTable();
        setCenter(typeTable);
    }

    private TableView<TypeInfo> createTypeTable() {
        TableView<TypeInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Professional table styling with subtle shadow
        table.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #d0d0d0;" +
            "-fx-border-width: 1px;" +
            "-fx-border-radius: 4px;" +
            "-fx-background-radius: 4px;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);" +
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );

        // Type column with colored badges
        TableColumn<TypeInfo, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().kind));
        typeCol.setPrefWidth(100);
        typeCol.setCellFactory(col -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Color-coded badges for type kinds
                    if ("Simple".equals(item)) {
                        setStyle(
                            "-fx-background-color: #e3f2fd;" +
                            "-fx-text-fill: #1976d2;" +
                            "-fx-font-weight: bold;" +
                            "-fx-alignment: CENTER;" +
                            "-fx-padding: 4px 8px;" +
                            "-fx-background-radius: 3px;"
                        );
                    } else if ("Complex".equals(item)) {
                        setStyle(
                            "-fx-background-color: #f3e5f5;" +
                            "-fx-text-fill: #7b1fa2;" +
                            "-fx-font-weight: bold;" +
                            "-fx-alignment: CENTER;" +
                            "-fx-padding: 4px 8px;" +
                            "-fx-background-radius: 3px;"
                        );
                    }
                }
            }
        });

        // Name column with bold styling
        TableColumn<TypeInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        nameCol.setPrefWidth(220);
        nameCol.setCellFactory(col -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: #2c3e50;" +
                        "-fx-font-size: 12px;"
                    );
                }
            }
        });

        // Base Type column
        TableColumn<TypeInfo, String> baseCol = new TableColumn<>("Base Type");
        baseCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().baseType));
        baseCol.setPrefWidth(150);
        baseCol.setCellFactory(col -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle(
                        "-fx-text-fill: #546e7a;" +
                        "-fx-font-style: italic;"
                    );
                }
            }
        });

        // Documentation column
        TableColumn<TypeInfo, String> docCol = new TableColumn<>("Documentation");
        docCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().documentation));
        docCol.setPrefWidth(280);
        docCol.setCellFactory(col -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText("—");
                    setStyle("-fx-text-fill: #bdbdbd;");
                } else {
                    setText(item);
                    setStyle(
                        "-fx-text-fill: #607d8b;" +
                        "-fx-font-size: 10px;"
                    );
                }
            }
        });

        // Usage Count column with color indicators
        TableColumn<TypeInfo, String> usageCol = new TableColumn<>("Usage");
        usageCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().usageCount)));
        usageCol.setPrefWidth(80);
        usageCol.setCellFactory(col -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    int count = Integer.parseInt(item);
                    setText(item);

                    // Traffic light color system
                    if (count == 0) {
                        // Red - Unused
                        setStyle(
                            "-fx-background-color: #ffebee;" +
                            "-fx-text-fill: #c62828;" +
                            "-fx-font-weight: bold;" +
                            "-fx-alignment: CENTER;" +
                            "-fx-padding: 4px;" +
                            "-fx-background-radius: 3px;"
                        );
                    } else if (count <= 3) {
                        // Orange - Low usage
                        setStyle(
                            "-fx-background-color: #fff3e0;" +
                            "-fx-text-fill: #e65100;" +
                            "-fx-font-weight: bold;" +
                            "-fx-alignment: CENTER;" +
                            "-fx-padding: 4px;" +
                            "-fx-background-radius: 3px;"
                        );
                    } else {
                        // Green - Good usage
                        setStyle(
                            "-fx-background-color: #e8f5e9;" +
                            "-fx-text-fill: #2e7d32;" +
                            "-fx-font-weight: bold;" +
                            "-fx-alignment: CENTER;" +
                            "-fx-padding: 4px;" +
                            "-fx-background-radius: 3px;"
                        );
                    }
                }
            }
        });

        // Usage Locations column with monospace font
        TableColumn<TypeInfo, String> locationsCol = new TableColumn<>("Used In (XPath)");
        locationsCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().usageLocations.isEmpty() ? "—" :
            String.join("\n", data.getValue().usageLocations)
        ));
        locationsCol.setPrefWidth(400);
        locationsCol.setCellFactory(col -> new TableCell<TypeInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("—".equals(item)) {
                        setStyle(
                            "-fx-text-fill: #bdbdbd;" +
                            "-fx-font-style: italic;"
                        );
                    } else {
                        setStyle(
                            "-fx-text-fill: #37474f;" +
                            "-fx-font-family: 'Courier New', monospace;" +
                            "-fx-font-size: 10px;" +
                            "-fx-wrap-text: true;"
                        );
                    }
                }
            }
        });

        // Professional row factory with hover effects and context menu
        table.setRowFactory(tv -> new TableRow<TypeInfo>() {
            @Override
            protected void updateItem(TypeInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    setContextMenu(null);
                } else {
                    // Yellow background for imported types, alternating for others
                    String baseColor;
                    if (item.isFromInclude) {
                        baseColor = "#fff8e1"; // Light yellow for imported types
                    } else {
                        baseColor = (getIndex() % 2 == 0) ? "#ffffff" : "#fafafa";
                    }

                    setStyle(
                        "-fx-background-color: " + baseColor + ";" +
                        "-fx-border-color: #eeeeee;" +
                        "-fx-border-width: 0 0 1 0;" +
                        "-fx-padding: 8px 4px;"
                    );

                    // Add context menu
                    setContextMenu(createContextMenu(item));

                    // Hover effect
                    final String hoverBaseColor = baseColor;
                    setOnMouseEntered(e -> {
                        if (!isEmpty()) {
                            setStyle(
                                "-fx-background-color: #e3f2fd;" +
                                "-fx-border-color: #90caf9;" +
                                "-fx-border-width: 1px 0;" +
                                "-fx-padding: 7px 4px;" +
                                "-fx-cursor: hand;"
                            );
                        }
                    });

                    setOnMouseExited(e -> {
                        if (!isEmpty()) {
                            setStyle(
                                "-fx-background-color: " + hoverBaseColor + ";" +
                                "-fx-border-color: #eeeeee;" +
                                "-fx-border-width: 0 0 1 0;" +
                                "-fx-padding: 8px 4px;"
                            );
                        }
                    });
                }
            }
        });

        table.getColumns().addAll(typeCol, nameCol, baseCol, docCol, usageCol, locationsCol);

        // Double-click to edit type
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TypeInfo selected = table.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    handleEditType(selected);
                }
            }
        });

        return table;
    }

    private void populateTypes() {
        if (schema == null) {
            statsLabel.setText("No schema loaded");
            return;
        }

        List<TypeInfo> types = new ArrayList<>();

        // Collect all types
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                types.add(createTypeInfo(simpleType));
            } else if (child instanceof XsdComplexType complexType) {
                types.add(createTypeInfo(complexType));
            }
        }

        // Calculate usage for each type
        calculateUsage(types);

        // Update table
        typeTable.setItems(FXCollections.observableArrayList(types));

        // Update stats
        long simpleCount = types.stream().filter(t -> "Simple".equals(t.kind)).count();
        long complexCount = types.stream().filter(t -> "Complex".equals(t.kind)).count();
        long unusedCount = types.stream().filter(t -> t.usageCount == 0).count();
        statsLabel.setText(String.format("Total: %d | Simple: %d | Complex: %d | Unused: %d",
            types.size(), simpleCount, complexCount, unusedCount));
    }

    private TypeInfo createTypeInfo(XsdSimpleType simpleType) {
        TypeInfo info = new TypeInfo();
        info.kind = "Simple";
        info.name = simpleType.getName() != null ? simpleType.getName() : "(anonymous)";

        // Get base type - check both direct base and restriction/list/union children
        String baseType = simpleType.getBase();
        if (baseType == null || baseType.isEmpty()) {
            // Check for restriction child
            for (XsdNode child : simpleType.getChildren()) {
                if (child instanceof XsdRestriction restriction) {
                    baseType = restriction.getBase();
                    break;
                } else if (child instanceof XsdList list) {
                    baseType = "list of " + (list.getItemType() != null ? list.getItemType() : "?");
                    break;
                } else if (child instanceof XsdUnion union) {
                    baseType = "union";
                    break;
                }
            }
        }
        info.baseType = baseType != null ? baseType : "";

        info.documentation = extractDocumentation(simpleType);
        info.node = simpleType;

        // Track include/import source
        info.isFromInclude = simpleType.isFromInclude();
        IncludeSourceInfo sourceInfo = simpleType.getSourceInfo();
        info.sourceFileName = sourceInfo != null ? sourceInfo.getFileName() : null;

        return info;
    }

    private TypeInfo createTypeInfo(XsdComplexType complexType) {
        TypeInfo info = new TypeInfo();
        info.kind = "Complex";
        info.name = complexType.getName() != null ? complexType.getName() : "(anonymous)";
        info.baseType = ""; // Complex types don't always have a simple base
        info.documentation = extractDocumentation(complexType);
        info.node = complexType;

        // Track include/import source
        info.isFromInclude = complexType.isFromInclude();
        IncludeSourceInfo sourceInfo = complexType.getSourceInfo();
        info.sourceFileName = sourceInfo != null ? sourceInfo.getFileName() : null;

        return info;
    }

    /**
     * Extracts documentation from an XsdNode, checking both the new documentations list
     * and the legacy documentation field.
     *
     * @param node the node to extract documentation from
     * @return the documentation text, or empty string if not found
     */
    private String extractDocumentation(XsdNode node) {
        // Check new documentations list first
        List<XsdDocumentation> docs = node.getDocumentations();
        if (docs != null && !docs.isEmpty()) {
            // Prefer English documentation
            for (XsdDocumentation doc : docs) {
                if ("en".equals(doc.getLang()) && doc.getText() != null) {
                    return doc.getText().trim();
                }
            }
            // Fallback to first entry with text
            for (XsdDocumentation doc : docs) {
                String text = doc.getText();
                if (text != null && !text.isEmpty()) {
                    return text.trim();
                }
            }
        }
        // Fallback to legacy field
        String legacy = node.getDocumentation();
        return legacy != null ? legacy.trim() : "";
    }

    /**
     * Creates a context menu for a type row.
     *
     * @param typeInfo the type info for the row
     * @return the context menu
     */
    private ContextMenu createContextMenu(TypeInfo typeInfo) {
        ContextMenu menu = new ContextMenu();

        // Edit Type
        MenuItem editItem = new MenuItem("Edit Type in Editor");
        editItem.setGraphic(createMenuIcon("bi-pencil-square", "#17a2b8"));
        editItem.setOnAction(e -> handleEditType(typeInfo));

        // Find Usages
        MenuItem findUsagesItem = new MenuItem("Find Usages");
        findUsagesItem.setGraphic(createMenuIcon("bi-search", "#6c757d"));
        findUsagesItem.setOnAction(e -> handleFindUsages(typeInfo));

        // Separator
        SeparatorMenuItem sep = new SeparatorMenuItem();

        // Delete
        MenuItem deleteItem = new MenuItem("Delete Type");
        deleteItem.setGraphic(createMenuIcon("bi-trash", "#dc3545"));
        deleteItem.setOnAction(e -> handleDeleteType(typeInfo));

        // Disable delete for imported types
        if (typeInfo.isFromInclude) {
            deleteItem.setDisable(true);
            deleteItem.setText("Delete Type (from external schema)");
        }

        menu.getItems().addAll(editItem, findUsagesItem, sep, deleteItem);
        return menu;
    }

    /**
     * Handles opening a type in the editor.
     * Uses callbacks if set (which also switch to the Type Editor tab),
     * otherwise falls back to direct TypeEditorTabManager calls.
     *
     * @param typeInfo the type to edit
     */
    private void handleEditType(TypeInfo typeInfo) {
        XsdNode node = typeInfo.node;

        // Info message for imported types
        if (node.isFromInclude()) {
            showImportedTypeWarning(node, "edit");
        }

        if (node instanceof XsdComplexType complexType) {
            // Use callback if available (switches to Type Editor tab)
            if (onOpenComplexType != null) {
                onOpenComplexType.accept(complexType);
            } else if (typeEditorTabManager != null) {
                typeEditorTabManager.openComplexTypeTab(complexType);
            } else {
                logger.warn("Cannot open ComplexType - no callback or TypeEditorTabManager set");
            }
        } else if (node instanceof XsdSimpleType simpleType) {
            // Use callback if available (switches to Type Editor tab)
            if (onOpenSimpleType != null) {
                onOpenSimpleType.accept(simpleType);
            } else if (typeEditorTabManager != null) {
                typeEditorTabManager.openSimpleTypeTab(simpleType);
            } else {
                logger.warn("Cannot open SimpleType - no callback or TypeEditorTabManager set");
            }
        }
    }

    /**
     * Handles finding usages of a type.
     *
     * @param typeInfo the type to find usages for
     */
    private void handleFindUsages(TypeInfo typeInfo) {
        if (typeInfo.usageCount == 0) {
            showAlert(Alert.AlertType.INFORMATION, "No Usages Found",
                "Type '" + typeInfo.name + "' is not used anywhere in the schema.");
            return;
        }

        // Show dialog with all usage locations
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Type Usages");
        alert.setHeaderText("Type '" + typeInfo.name + "' is used in " + typeInfo.usageCount + " location(s)");

        // Create a TextArea with the locations
        TextArea textArea = new TextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setText(String.join("\n", typeInfo.usageLocations));
        textArea.setPrefHeight(200);
        textArea.setPrefWidth(500);
        textArea.setStyle("-fx-font-family: 'Courier New', monospace;");

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    /**
     * Handles deleting a type.
     *
     * @param typeInfo the type to delete
     */
    private void handleDeleteType(TypeInfo typeInfo) {
        XsdNode node = typeInfo.node;

        // Imported types cannot be deleted
        if (node.isFromInclude()) {
            showImportedTypeWarning(node, "delete");
            return;
        }

        // Check usage
        if (typeInfo.usageCount > 0) {
            // Warning: Type is in use
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Type In Use");
            alert.setHeaderText("Cannot delete type '" + typeInfo.name + "'");

            String locationsList = typeInfo.usageLocations.stream()
                .limit(5)
                .collect(Collectors.joining("\n"));
            if (typeInfo.usageLocations.size() > 5) {
                locationsList += "\n... and " + (typeInfo.usageLocations.size() - 5) + " more";
            }

            alert.setContentText("This type is used in " + typeInfo.usageCount + " location(s).\n\n" +
                "Locations:\n" + locationsList);
            alert.showAndWait();
            return;
        }

        // Confirmation dialog
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Type");
        confirm.setHeaderText("Delete type '" + typeInfo.name + "'?");
        confirm.setContentText("This type is not used and can be safely deleted.");

        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                schema.removeChild(node);
                populateTypes();
                logger.info("Deleted type: {}", typeInfo.name);
            }
        });
    }

    /**
     * Shows an info dialog for imported/included types.
     *
     * @param node   the node from an external schema
     * @param action the action being attempted ("edit" or "delete")
     */
    private void showImportedTypeWarning(XsdNode node, String action) {
        IncludeSourceInfo sourceInfo = node.getSourceInfo();
        String fileName = sourceInfo != null ? sourceInfo.getFileName() : "external file";

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("External Schema Type");

        if ("delete".equals(action)) {
            alert.setHeaderText("Cannot delete type from external schema");
            alert.setContentText(
                "This type is defined in '" + fileName + "'.\n\n" +
                "To delete this type, modify the source file directly.");
        } else {
            alert.setHeaderText("Editing type from included schema");
            alert.setContentText(
                "This type is defined in '" + fileName + "'.\n\n" +
                "Changes will be saved directly to that file when you save.");
        }

        alert.showAndWait();
    }

    private void calculateUsage(List<TypeInfo> types) {
        // Use TypeUsageFinder for comprehensive and accurate usage detection
        TypeUsageFinder usageFinder = new TypeUsageFinder(schema);

        // For each type, find all usages
        for (TypeInfo typeInfo : types) {
            if (typeInfo.name == null || typeInfo.name.isEmpty() || typeInfo.name.equals("(anonymous)")) {
                continue; // Skip anonymous types
            }

            List<TypeUsageLocation> usageLocations = usageFinder.findUsages(typeInfo.name);
            typeInfo.usageCount = usageLocations.size();

            // Convert TypeUsageLocation to description strings
            for (TypeUsageLocation location : usageLocations) {
                typeInfo.usageLocations.add(location.getDescription());
            }
        }
    }

    private void filterTypes() {
        String search = searchField.getText().toLowerCase();
        String filter = filterChoice.getValue();

        ObservableList<TypeInfo> allTypes = FXCollections.observableArrayList();

        // Re-collect types (could be optimized with caching)
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdSimpleType simpleType) {
                allTypes.add(createTypeInfo(simpleType));
            } else if (child instanceof XsdComplexType complexType) {
                allTypes.add(createTypeInfo(complexType));
            }
        }

        calculateUsage(allTypes);

        // Apply filters
        ObservableList<TypeInfo> filtered = allTypes.stream()
            .filter(t -> {
                // Search filter
                if (!search.isEmpty() && !t.name.toLowerCase().contains(search)) {
                    return false;
                }

                // Type filter
                return switch (filter) {
                    case "Simple Types" -> "Simple".equals(t.kind);
                    case "Complex Types" -> "Complex".equals(t.kind);
                    case "Unused Types" -> t.usageCount == 0;
                    default -> true; // "All Types"
                };
            })
            .collect(Collectors.toCollection(FXCollections::observableArrayList));

        typeTable.setItems(filtered);
    }

    private HBox createSpacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /**
     * Create Export MenuButton with all export format options
     */
    private MenuButton createExportButton() {
        MenuButton exportButton = new MenuButton("Export");

        // Style the button - XMLSpy blue styling
        exportButton.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;" +
            "-fx-background-color: #4a90e2;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 3px;" +
            "-fx-padding: 5px 10px;" +
            "-fx-cursor: hand;"
        );

        // CSV MenuItem
        MenuItem csvItem = new MenuItem("CSV - Comma Separated Values");
        csvItem.setGraphic(createMenuIcon("bi-file-text", "#28a745"));
        csvItem.setOnAction(e -> exportTo("CSV"));

        // Excel MenuItem
        MenuItem excelItem = new MenuItem("Excel - XLSX (with multiple sheets)");
        excelItem.setGraphic(createMenuIcon("bi-file-earmark-spreadsheet", "#217346"));
        excelItem.setOnAction(e -> exportTo("XLSX"));

        // HTML MenuItem
        MenuItem htmlItem = new MenuItem("HTML - Web Page");
        htmlItem.setGraphic(createMenuIcon("bi-code", "#e44d26"));
        htmlItem.setOnAction(e -> exportTo("HTML"));

        // JSON MenuItem
        MenuItem jsonItem = new MenuItem("JSON - JavaScript Object Notation");
        jsonItem.setGraphic(createMenuIcon("bi-braces", "#f7df1e"));
        jsonItem.setOnAction(e -> exportTo("JSON"));

        // XML MenuItem
        MenuItem xmlItem = new MenuItem("XML - Extensible Markup Language");
        xmlItem.setGraphic(createMenuIcon("bi-file-earmark-code", "#007bff"));
        xmlItem.setOnAction(e -> exportTo("XML"));

        // Markdown MenuItem
        MenuItem markdownItem = new MenuItem("Markdown - GitHub/GitLab");
        markdownItem.setGraphic(createMenuIcon("bi-markdown", "#083fa1"));
        markdownItem.setOnAction(e -> exportTo("MARKDOWN"));

        exportButton.getItems().addAll(
            csvItem, excelItem, htmlItem, jsonItem, xmlItem, markdownItem
        );

        return exportButton;
    }

    /**
     * Export type library data to specified format
     */
    private void exportTo(String format) {
        // Get all types from the table
        ObservableList<TypeInfo> types = typeTable.getItems();
        if (types.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data", "There are no types to export.");
            return;
        }

        // Create FileChooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Type Library - " + format);
        fileChooser.setInitialFileName("type-library." + getFileExtension(format));

        // Set file extension filter
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(
            format + " Files (*." + getFileExtension(format) + ")",
            "*." + getFileExtension(format)
        );
        fileChooser.getExtensionFilters().add(extFilter);

        // Show save dialog
        File file = fileChooser.showSaveDialog(this.getScene().getWindow());
        if (file == null) {
            return; // User cancelled
        }

        // Convert TypeInfo to TypeLibraryExporter.TypeInfo
        List<TypeLibraryExporter.TypeInfo> exportTypes = new ArrayList<>();
        for (TypeInfo type : types) {
            TypeLibraryExporter.TypeInfo exportType = new TypeLibraryExporter.TypeInfo();
            exportType.kind = type.kind;
            exportType.name = type.name;
            exportType.baseType = type.baseType;
            exportType.documentation = type.documentation;
            exportType.usageCount = type.usageCount;
            exportType.usageLocations = new ArrayList<>(type.usageLocations);
            exportTypes.add(exportType);
        }

        // Export
        try {
            switch (format) {
                case "CSV" -> TypeLibraryExporter.exportToCSV(exportTypes, file);
                case "XLSX" -> TypeLibraryExporter.exportToExcel(exportTypes, file);
                case "HTML" -> TypeLibraryExporter.exportToHTML(exportTypes, file, schemaName);
                case "JSON" -> TypeLibraryExporter.exportToJSON(exportTypes, file, schemaName);
                case "XML" -> TypeLibraryExporter.exportToXML(exportTypes, file, schemaName);
                case "MARKDOWN" -> TypeLibraryExporter.exportToMarkdown(exportTypes, file, schemaName);
            }

            logger.info("Exported type library to {} format: {}", format, file.getAbsolutePath());
            showAlert(Alert.AlertType.INFORMATION, "Export Successful",
                "Type library exported successfully to:\n" + file.getAbsolutePath());

        } catch (Exception ex) {
            logger.error("Error exporting type library to {}: {}", format, ex.getMessage(), ex);
            showAlert(Alert.AlertType.ERROR, "Export Failed",
                "Failed to export type library:\n" + ex.getMessage());
        }
    }

    private String getFileExtension(String format) {
        return switch (format) {
            case "CSV" -> "csv";
            case "XLSX" -> "xlsx";
            case "HTML" -> "html";
            case "JSON" -> "json";
            case "XML" -> "xml";
            case "MARKDOWN" -> "md";
            default -> "txt";
        };
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Updates the view with a new schema.
     *
     * @param newSchema The new schema to display
     */
    public void setSchema(XsdSchema newSchema) {
        populateTypes();
    }

    /**
     * Creates a colored FontIcon for menu items.
     *
     * @param iconLiteral The icon literal (e.g., "bi-file-earmark-code")
     * @param color       The color in hex format (e.g., "#007bff")
     * @return FontIcon with specified color
     */
    private FontIcon createMenuIcon(String iconLiteral, String color) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(javafx.scene.paint.Color.web(color));
        icon.setIconSize(16);
        return icon;
    }

    /**
     * Type information holder.
     */
    private static class TypeInfo {
        /** The kind of type (Simple/Complex). */
        String kind;
        /** The name of the type. */
        String name;
        /** The base type name. */
        String baseType;
        /** The documentation for the type. */
        String documentation;
        /** The number of times the type is used. */
        int usageCount = 0;
        /** List of XPaths where the type is used. */
        List<String> usageLocations = new ArrayList<>();
        /** Reference to actual node. */
        XsdNode node;
        /** Whether this type is from an imported/included schema. */
        boolean isFromInclude;
        /** The source file name if from include. */
        String sourceFileName;

        /**
         * Creates a new TypeInfo instance.
         */
        public TypeInfo() {
        }
    }
}

