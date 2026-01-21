package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.v2.editor.TypeEditorTabManager;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
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

        // Professional row factory with hover effects
        table.setRowFactory(tv -> new TableRow<TypeInfo>() {
            @Override
            protected void updateItem(TypeInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else {
                    // Subtle alternating rows with hover effect
                    String baseColor = (getIndex() % 2 == 0) ? "#ffffff" : "#fafafa";

                    setStyle(
                        "-fx-background-color: " + baseColor + ";" +
                        "-fx-border-color: #eeeeee;" +
                        "-fx-border-width: 0 0 1 0;" +
                        "-fx-padding: 8px 4px;"
                    );

                    // Hover effect
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
                                "-fx-background-color: " + baseColor + ";" +
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

        info.documentation = simpleType.getDocumentation() != null ? simpleType.getDocumentation() : "";
        info.node = simpleType;
        return info;
    }

    private TypeInfo createTypeInfo(XsdComplexType complexType) {
        TypeInfo info = new TypeInfo();
        info.kind = "Complex";
        info.name = complexType.getName() != null ? complexType.getName() : "(anonymous)";
        info.baseType = ""; // Complex types don't always have a simple base
        info.documentation = complexType.getDocumentation() != null ? complexType.getDocumentation() : "";
        info.node = complexType;
        return info;
    }

    private void calculateUsage(List<TypeInfo> types) {
        // Create a map for quick lookup
        Map<String, TypeInfo> typeMap = types.stream()
            .collect(Collectors.toMap(t -> t.name, t -> t));

        // Scan all schema children to find type usage
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdElement globalElement) {
                // Global element - scan for type references
                String rootPath = "/xs:schema/xs:element[@name='" + globalElement.getName() + "']";

                // Check if global element references a type
                String typeRef = globalElement.getType();
                if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                    String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;
                    TypeInfo typeInfo = typeMap.get(typeName);
                    if (typeInfo != null) {
                        typeInfo.usageCount++;
                        typeInfo.usageLocations.add(rootPath);
                    }
                }

                // Scan element's inline content
                scanElementChildren(globalElement, rootPath, typeMap);

            } else if (child instanceof XsdComplexType complexType) {
                // Global complex type - scan its content for type references
                String typePath = "/xs:schema/xs:complexType[@name='" + complexType.getName() + "']";
                scanComplexTypeContent(complexType, typePath, typeMap);

            } else if (child instanceof XsdSimpleType simpleType) {
                // Global simple type - check for base type references
                String typePath = "/xs:schema/xs:simpleType[@name='" + simpleType.getName() + "']";
                scanSimpleTypeForBaseType(simpleType, typePath, typeMap);
            }
        }
    }

    /**
     * Scan a SimpleType for base type references (in restrictions, lists, unions)
     */
    private void scanSimpleTypeForBaseType(XsdSimpleType simpleType, String typePath, Map<String, TypeInfo> typeMap) {
        // Check direct base
        String baseType = simpleType.getBase();
        if (baseType != null && !baseType.isEmpty() && !baseType.startsWith("xs:") && !baseType.startsWith("xsd:")) {
            String typeName = baseType.contains(":") ? baseType.substring(baseType.indexOf(":") + 1) : baseType;
            TypeInfo typeInfo = typeMap.get(typeName);
            if (typeInfo != null) {
                typeInfo.usageCount++;
                typeInfo.usageLocations.add(typePath);
            }
        }

        // Check children (restriction, list, union)
        for (XsdNode child : simpleType.getChildren()) {
            if (child instanceof XsdRestriction restriction) {
                String restrictionBase = restriction.getBase();
                if (restrictionBase != null && !restrictionBase.isEmpty() &&
                    !restrictionBase.startsWith("xs:") && !restrictionBase.startsWith("xsd:")) {
                    String typeName = restrictionBase.contains(":") ?
                        restrictionBase.substring(restrictionBase.indexOf(":") + 1) : restrictionBase;
                    TypeInfo typeInfo = typeMap.get(typeName);
                    if (typeInfo != null) {
                        typeInfo.usageCount++;
                        typeInfo.usageLocations.add(typePath + "/xs:restriction");
                    }
                }
            } else if (child instanceof XsdList list) {
                String itemType = list.getItemType();
                if (itemType != null && !itemType.isEmpty() &&
                    !itemType.startsWith("xs:") && !itemType.startsWith("xsd:")) {
                    String typeName = itemType.contains(":") ?
                        itemType.substring(itemType.indexOf(":") + 1) : itemType;
                    TypeInfo typeInfo = typeMap.get(typeName);
                    if (typeInfo != null) {
                        typeInfo.usageCount++;
                        typeInfo.usageLocations.add(typePath + "/xs:list");
                    }
                }
            } else if (child instanceof XsdUnion union) {
                List<String> memberTypes = union.getMemberTypes();
                if (memberTypes != null && !memberTypes.isEmpty()) {
                    // Union can have multiple member types
                    for (String memberType : memberTypes) {
                        if (!memberType.startsWith("xs:") && !memberType.startsWith("xsd:")) {
                            String typeName = memberType.contains(":") ?
                                memberType.substring(memberType.indexOf(":") + 1) : memberType;
                            TypeInfo typeInfo = typeMap.get(typeName);
                            if (typeInfo != null) {
                                typeInfo.usageCount++;
                                typeInfo.usageLocations.add(typePath + "/xs:union");
                            }
                        }
                    }
                }
            }
        }
    }

    private void scanElementChildren(XsdElement element, String elementPath, Map<String, TypeInfo> typeMap) {
        for (XsdNode child : element.getChildren()) {
            if (child instanceof XsdComplexType complexType) {
                // Inline complex type - scan its content
                scanComplexTypeContent(complexType, elementPath, typeMap);
            } else if (child instanceof XsdSimpleType simpleType) {
                // Inline simple type - nothing more to scan
            }
        }
    }

    private void scanComplexTypeContent(XsdComplexType complexType, String parentPath, Map<String, TypeInfo> typeMap) {
        for (XsdNode child : complexType.getChildren()) {
            if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                // Compositor - scan its children
                scanCompositor(child, parentPath, typeMap);
            } else if (child instanceof XsdAttribute attribute) {
                // Attribute with type reference
                String attrPath = parentPath + "/@" + attribute.getName();
                String typeRef = attribute.getType();

                if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                    String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;

                    TypeInfo typeInfo = typeMap.get(typeName);
                    if (typeInfo != null) {
                        typeInfo.usageCount++;
                        typeInfo.usageLocations.add(attrPath);
                    }
                }
            } else if (child instanceof XsdComplexContent || child instanceof XsdSimpleContent) {
                // Extension or restriction - scan children recursively
                scanNodeForAttributes(child, parentPath, typeMap);
            }
        }
    }

    private void scanCompositor(XsdNode compositor, String parentPath, Map<String, TypeInfo> typeMap) {
        for (XsdNode child : compositor.getChildren()) {
            if (child instanceof XsdElement element) {
                // Build absolute XPath for this element
                String elementPath = parentPath + "/xs:element[@name='" + element.getName() + "']";

                String typeRef = element.getType();
                if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                    String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;

                    TypeInfo typeInfo = typeMap.get(typeName);
                    if (typeInfo != null) {
                        typeInfo.usageCount++;
                        typeInfo.usageLocations.add(elementPath);
                    }
                }

                // Recursively scan this element's children
                scanElementChildren(element, elementPath, typeMap);
            } else if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                // Nested compositor
                scanCompositor(child, parentPath, typeMap);
            }
        }
    }

    private void scanNodeForAttributes(XsdNode node, String parentPath, Map<String, TypeInfo> typeMap) {
        for (XsdNode child : node.getChildren()) {
            if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                scanCompositor(child, parentPath, typeMap);
            } else if (child instanceof XsdAttribute attribute) {
                String attrPath = parentPath + "/@" + attribute.getName();
                String typeRef = attribute.getType();

                if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                    String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;

                    TypeInfo typeInfo = typeMap.get(typeName);
                    if (typeInfo != null) {
                        typeInfo.usageCount++;
                        typeInfo.usageLocations.add(attrPath);
                    }
                }
            } else if (child instanceof XsdExtension || child instanceof XsdRestriction) {
                // Recursively scan extension/restriction children
                scanNodeForAttributes(child, parentPath, typeMap);
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

        /**
         * Creates a new TypeInfo instance.
         */
        public TypeInfo() {
        }
    }
}

