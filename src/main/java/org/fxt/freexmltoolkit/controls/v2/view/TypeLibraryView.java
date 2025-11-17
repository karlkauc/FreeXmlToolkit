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
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.kordamp.ikonli.javafx.FontIcon;

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

    private final XsdSchema schema;
    private TableView<TypeInfo> typeTable;
    private TextField searchField;
    private ChoiceBox<String> filterChoice;
    private Label statsLabel;

    public TypeLibraryView(XsdSchema schema) {
        this.schema = schema;
        initializeUI();
        populateTypes();
    }

    private void initializeUI() {
        // Top toolbar
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #dee2e6; -fx-border-width: 0 0 1 0;");

        // Search field
        searchField = new TextField();
        searchField.setPromptText("Search types...");
        searchField.setPrefWidth(250);
        searchField.textProperty().addListener((obs, old, newVal) -> filterTypes());

        // Filter choice
        filterChoice = new ChoiceBox<>();
        filterChoice.setItems(FXCollections.observableArrayList("All Types", "Simple Types", "Complex Types", "Unused Types"));
        filterChoice.setValue("All Types");
        filterChoice.valueProperty().addListener((obs, old, newVal) -> filterTypes());

        // Stats label
        statsLabel = new Label();
        statsLabel.setStyle("-fx-font-weight: bold;");

        HBox.setHgrow(searchField, Priority.NEVER);
        toolbar.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("Filter:"), filterChoice,
            createSpacer(),
            statsLabel
        );

        setTop(toolbar);

        // Create table
        typeTable = createTypeTable();
        setCenter(typeTable);
    }

    private TableView<TypeInfo> createTypeTable() {
        TableView<TypeInfo> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Type column
        TableColumn<TypeInfo, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().kind));
        typeCol.setPrefWidth(80);
        typeCol.setStyle("-fx-alignment: CENTER;");

        // Name column
        TableColumn<TypeInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        nameCol.setPrefWidth(200);

        // Base Type column
        TableColumn<TypeInfo, String> baseCol = new TableColumn<>("Base Type");
        baseCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().baseType));
        baseCol.setPrefWidth(150);

        // Documentation column
        TableColumn<TypeInfo, String> docCol = new TableColumn<>("Documentation");
        docCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().documentation));
        docCol.setPrefWidth(250);

        // Usage Count column
        TableColumn<TypeInfo, String> usageCol = new TableColumn<>("Usage");
        usageCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().usageCount)));
        usageCol.setPrefWidth(60);
        usageCol.setStyle("-fx-alignment: CENTER;");

        // Usage Locations column
        TableColumn<TypeInfo, String> locationsCol = new TableColumn<>("Used In (XPath)");
        locationsCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().usageLocations.isEmpty() ? "Not used" :
            String.join(", ", data.getValue().usageLocations)
        ));
        locationsCol.setPrefWidth(300);

        // Row factory for unused types styling
        table.setRowFactory(tv -> new TableRow<TypeInfo>() {
            @Override
            protected void updateItem(TypeInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.usageCount == 0) {
                    setStyle("-fx-background-color: #fff3cd;"); // Yellow for unused
                } else {
                    setStyle("");
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

        // Scan all elements in the schema to find type usage
        scanForTypeUsage(schema, "", typeMap);
    }

    private void scanForTypeUsage(XsdNode node, String currentPath, Map<String, TypeInfo> typeMap) {
        if (node instanceof XsdElement element) {
            String elementPath = currentPath + "/" + element.getName();
            String typeRef = element.getType();

            if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                // Remove namespace prefix if present
                String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;

                TypeInfo typeInfo = typeMap.get(typeName);
                if (typeInfo != null) {
                    typeInfo.usageCount++;
                    typeInfo.usageLocations.add(elementPath);
                }
            }
        } else if (node instanceof XsdAttribute attribute) {
            String attrPath = currentPath + "/@" + attribute.getName();
            String typeRef = attribute.getType();

            if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;

                TypeInfo typeInfo = typeMap.get(typeName);
                if (typeInfo != null) {
                    typeInfo.usageCount++;
                    typeInfo.usageLocations.add(attrPath);
                }
            }
        }

        // Recursively scan children
        for (XsdNode child : node.getChildren()) {
            String childPath = currentPath;
            if (node instanceof XsdElement) {
                childPath = currentPath + "/" + ((XsdElement) node).getName();
            }
            scanForTypeUsage(child, childPath, typeMap);
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
     * Updates the view with a new schema.
     */
    public void setSchema(XsdSchema newSchema) {
        populateTypes();
    }

    /**
     * Type information holder
     */
    private static class TypeInfo {
        String kind; // "Simple" or "Complex"
        String name;
        String baseType;
        String documentation;
        int usageCount = 0;
        List<String> usageLocations = new ArrayList<>();
        XsdNode node; // Reference to actual node
    }
}
