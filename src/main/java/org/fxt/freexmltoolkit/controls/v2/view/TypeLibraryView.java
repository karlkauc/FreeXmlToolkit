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

    public TypeLibraryView(XsdSchema schema) {
        this.schema = schema;
        if (schema != null && schema.getTargetNamespace() != null) {
            this.schemaName = schema.getTargetNamespace();
        }
        initializeUI();
        populateTypes();
    }

    /**
     * Set the schema name for export file naming
     */
    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
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

        // Export button with menu
        MenuButton exportButton = createExportButton();

        HBox.setHgrow(searchField, Priority.NEVER);
        toolbar.getChildren().addAll(
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
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Apply XMLSpy table styling
        table.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #c0c0c0;" +
            "-fx-border-width: 1px;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 1, 1);" +
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );

        // Type column
        TableColumn<TypeInfo, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().kind));
        typeCol.setPrefWidth(80);
        typeCol.setStyle(
            "-fx-alignment: CENTER;" +
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        applyHeaderStyle(typeCol);

        // Name column
        TableColumn<TypeInfo, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name));
        nameCol.setPrefWidth(200);
        nameCol.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        applyHeaderStyle(nameCol);

        // Base Type column
        TableColumn<TypeInfo, String> baseCol = new TableColumn<>("Base Type");
        baseCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().baseType));
        baseCol.setPrefWidth(150);
        baseCol.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        applyHeaderStyle(baseCol);

        // Documentation column
        TableColumn<TypeInfo, String> docCol = new TableColumn<>("Documentation");
        docCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().documentation));
        docCol.setPrefWidth(250);
        docCol.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        applyHeaderStyle(docCol);

        // Usage Count column
        TableColumn<TypeInfo, String> usageCol = new TableColumn<>("Usage");
        usageCol.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().usageCount)));
        usageCol.setPrefWidth(60);
        usageCol.setStyle(
            "-fx-alignment: CENTER;" +
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        applyHeaderStyle(usageCol);

        // Usage Locations column
        TableColumn<TypeInfo, String> locationsCol = new TableColumn<>("Used In (XPath)");
        locationsCol.setCellValueFactory(data -> new SimpleStringProperty(
            data.getValue().usageLocations.isEmpty() ? "Not used" :
            String.join(", ", data.getValue().usageLocations)
        ));
        locationsCol.setPrefWidth(300);
        locationsCol.setStyle(
            "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
            "-fx-font-size: 11px;"
        );
        applyHeaderStyle(locationsCol);

        // Row factory for alternating colors and unused types styling
        table.setRowFactory(tv -> new TableRow<TypeInfo>() {
            @Override
            protected void updateItem(TypeInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                } else if (item.usageCount == 0) {
                    // Yellow for unused types - XMLSpy warning style
                    setStyle(
                        "-fx-background-color: #fff3cd;" +
                        "-fx-border-color: #e0e0e0;" +
                        "-fx-border-width: 0 0 1 0;" +
                        "-fx-padding: 4px 8px;" +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                        "-fx-font-size: 11px;"
                    );
                } else {
                    // Alternating row colors - XMLSpy style
                    String bgColor = (getIndex() % 2 == 0) ? "#ffffff" : "#f8f8f8";
                    setStyle(
                        "-fx-background-color: " + bgColor + ";" +
                        "-fx-border-color: #e0e0e0;" +
                        "-fx-border-width: 0 0 1 0;" +
                        "-fx-padding: 4px 8px;" +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;" +
                        "-fx-font-size: 11px;"
                    );
                }
            }
        });

        table.getColumns().addAll(typeCol, nameCol, baseCol, docCol, usageCol, locationsCol);
        return table;
    }

    /**
     * Apply XMLSpy-style header styling to a table column
     */
    private void applyHeaderStyle(TableColumn<?, ?> column) {
        column.setStyle(
            column.getStyle() +
            "-fx-background-color: linear-gradient(to bottom, #f5f5f5, #e8e8e8);" +
            "-fx-text-fill: #333333;" +
            "-fx-font-weight: bold;" +
            "-fx-border-color: #c0c0c0;" +
            "-fx-border-width: 0 1px 1px 0;" +
            "-fx-padding: 4px 8px;" +
            "-fx-alignment: CENTER_LEFT;"
        );
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

        // Scan all global elements in the schema to find type usage
        for (XsdNode child : schema.getChildren()) {
            if (child instanceof XsdElement globalElement) {
                // Start with absolute XPath for global elements
                String rootPath = "/xs:schema/xs:element[@name='" + globalElement.getName() + "']";
                scanForTypeUsage(globalElement, rootPath, typeMap, true);
            }
        }
    }

    private void scanForTypeUsage(XsdNode node, String currentPath, Map<String, TypeInfo> typeMap, boolean isGlobalElement) {
        if (node instanceof XsdElement element) {
            String typeRef = element.getType();

            // Check if this element uses a custom type
            if (typeRef != null && !typeRef.isEmpty() && !typeRef.startsWith("xs:") && !typeRef.startsWith("xsd:")) {
                // Remove namespace prefix if present
                String typeName = typeRef.contains(":") ? typeRef.substring(typeRef.indexOf(":") + 1) : typeRef;

                TypeInfo typeInfo = typeMap.get(typeName);
                if (typeInfo != null) {
                    typeInfo.usageCount++;
                    typeInfo.usageLocations.add(currentPath);
                }
            }

            // Scan children of this element
            scanElementChildren(element, currentPath, typeMap);
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

        // Style the button
        FontIcon exportIcon = new FontIcon("mdi2f-file-export");
        exportIcon.setIconSize(16);
        exportButton.setGraphic(exportIcon);
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
        MenuItem csvItem = new MenuItem("Export to CSV");
        csvItem.setGraphic(new FontIcon("mdi2f-file-delimited"));
        csvItem.setOnAction(e -> exportTo("CSV"));

        // Excel MenuItem
        MenuItem excelItem = new MenuItem("Export to Excel (XLSX)");
        excelItem.setGraphic(new FontIcon("mdi2f-file-excel"));
        excelItem.setOnAction(e -> exportTo("XLSX"));

        // HTML MenuItem
        MenuItem htmlItem = new MenuItem("Export to HTML");
        htmlItem.setGraphic(new FontIcon("mdi2f-file-code"));
        htmlItem.setOnAction(e -> exportTo("HTML"));

        // JSON MenuItem
        MenuItem jsonItem = new MenuItem("Export to JSON");
        jsonItem.setGraphic(new FontIcon("mdi2f-code-json"));
        jsonItem.setOnAction(e -> exportTo("JSON"));

        // XML MenuItem
        MenuItem xmlItem = new MenuItem("Export to XML");
        xmlItem.setGraphic(new FontIcon("mdi2f-file-xml"));
        xmlItem.setOnAction(e -> exportTo("XML"));

        // Markdown MenuItem
        MenuItem markdownItem = new MenuItem("Export to Markdown");
        markdownItem.setGraphic(new FontIcon("mdi2f-file-document"));
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
