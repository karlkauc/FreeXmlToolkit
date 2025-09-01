package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Grid/table view of XSD content.
 * Displays schema elements in a tabular format with columns for
 * name, type, cardinality, and other properties.
 */
public class XsdGridView extends BorderPane {
    private static final Logger logger = LogManager.getLogger(XsdGridView.class);

    private XsdDomManipulator domManipulator;
    private final TableView<XsdGridRow> gridTable;
    private final ObservableList<XsdGridRow> gridData;
    private final ObservableList<XsdGridRow> allGridData; // Stores all data including collapsed items
    private final ObjectProperty<XsdNodeInfo> selectedNodeProperty = new SimpleObjectProperty<>();

    public XsdGridView(XsdDomManipulator domManipulator) {
        this.domManipulator = domManipulator;
        this.gridData = FXCollections.observableArrayList();
        this.allGridData = FXCollections.observableArrayList();
        this.gridTable = new TableView<>(gridData);

        initializeComponents();
        refreshView();
    }

    private void initializeComponents() {
        // Create toolbar
        ToolBar toolbar = new ToolBar();

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshButton.setOnAction(e -> refreshView());

        Button expandAllButton = new Button("Expand All");
        expandAllButton.setGraphic(new FontIcon("bi-arrows-expand"));
        expandAllButton.setOnAction(e -> expandAllRows());

        Button collapseAllButton = new Button("Collapse All");
        collapseAllButton.setGraphic(new FontIcon("bi-arrows-collapse"));
        collapseAllButton.setOnAction(e -> collapseAllRows());

        toolbar.getItems().addAll(refreshButton, new Separator(), expandAllButton, collapseAllButton);

        // Configure table
        setupTableColumns();
        setupTableBehavior();

        // Apply styling to ensure text is readable
        gridTable.setStyle("-fx-text-fill: black; -fx-background-color: white;");

        // Layout
        setTop(toolbar);
        setCenter(gridTable);
    }

    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        // Name column
        TableColumn<XsdGridRow, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().name()));
        nameColumn.setPrefWidth(200);
        nameColumn.setCellFactory(column -> createTextCell());

        // Type column
        TableColumn<XsdGridRow, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().type()));
        typeColumn.setPrefWidth(150);
        typeColumn.setCellFactory(column -> createTextCell());

        // Node Type column
        TableColumn<XsdGridRow, String> nodeTypeColumn = new TableColumn<>("Kind");
        nodeTypeColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().nodeType()));
        nodeTypeColumn.setPrefWidth(100);
        nodeTypeColumn.setCellFactory(column -> createTextCell());

        // Min Occurs column
        TableColumn<XsdGridRow, String> minOccursColumn = new TableColumn<>("Min");
        minOccursColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().minOccurs()));
        minOccursColumn.setPrefWidth(60);
        minOccursColumn.setCellFactory(column -> createTextCell());

        // Max Occurs column
        TableColumn<XsdGridRow, String> maxOccursColumn = new TableColumn<>("Max");
        maxOccursColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().maxOccurs()));
        maxOccursColumn.setPrefWidth(60);
        maxOccursColumn.setCellFactory(column -> createTextCell());

        // XPath column
        TableColumn<XsdGridRow, String> xpathColumn = new TableColumn<>("XPath");
        xpathColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().xpath()));
        xpathColumn.setPrefWidth(300);
        xpathColumn.setCellFactory(column -> createTextCell());

        // Documentation column
        TableColumn<XsdGridRow, String> docColumn = new TableColumn<>("Documentation");
        docColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().documentation()));
        docColumn.setPrefWidth(200);
        docColumn.setCellFactory(column -> createTextCell());

        gridTable.getColumns().addAll(nameColumn, typeColumn, nodeTypeColumn,
                minOccursColumn, maxOccursColumn, xpathColumn, docColumn);
    }

    /**
     * Create a table cell with black text for better readability
     */
    private TableCell<XsdGridRow, String> createTextCell() {
        return new TableCell<XsdGridRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    // Force black text color for readability
                    setStyle("-fx-text-fill: black; -fx-font-family: monospace;");
                }
            }
        };
    }

    private void setupTableBehavior() {
        // Selection handling
        gridTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
            if (newRow != null) {
                // Create XsdNodeInfo from grid row
                XsdNodeInfo nodeInfo = convertToNodeInfo(newRow);
                selectedNodeProperty.set(nodeInfo);
            }
        });

        // Double-click to focus on element
        gridTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                XsdGridRow selectedRow = gridTable.getSelectionModel().getSelectedItem();
                if (selectedRow != null) {
                    focusOnElement(selectedRow);
                }
            }
        });

        // Context menu
        gridTable.setContextMenu(createContextMenu());
    }

    private ContextMenu createContextMenu() {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem refreshItem = new MenuItem("Refresh View");
        refreshItem.setGraphic(new FontIcon("bi-arrow-clockwise"));
        refreshItem.setOnAction(e -> refreshView());

        MenuItem exportItem = new MenuItem("Export to CSV");
        exportItem.setGraphic(new FontIcon("bi-file-earmark-spreadsheet"));
        exportItem.setOnAction(e -> exportToCsv());

        contextMenu.getItems().addAll(refreshItem, new SeparatorMenuItem(), exportItem);
        return contextMenu;
    }

    /**
     * Refresh the grid view with current XSD content
     */
    public void refreshView() {
        logger.info("Refreshing XSD grid view");

        gridData.clear();

        if (domManipulator != null && domManipulator.getDocument() != null) {
            logger.info("DOM manipulator has document, proceeding to populate grid");
            populateGridFromDocument();
        } else {
            if (domManipulator == null) {
                logger.warn("DOM manipulator is null - cannot refresh grid view");
            } else if (domManipulator.getDocument() == null) {
                logger.warn("DOM manipulator document is null - cannot refresh grid view");
            } else {
                logger.warn("Unknown issue with DOM manipulator - cannot refresh grid view");
            }

            // Show a message to the user that no data is available
            XsdGridRow noDataRow = new XsdGridRow(
                    "No XSD data loaded",
                    "",
                    "info",
                    "",
                    "",
                    "",
                    "Please load an XSD file or enter XSD content to see data in the grid view.",
                    0
            );
            gridData.add(noDataRow);
        }

        logger.info("Grid view refreshed with {} rows", gridData.size());
    }

    private void populateGridFromDocument() {
        Element root = domManipulator.getDocument().getDocumentElement();
        if (root != null) {
            logger.info("Starting to populate grid from document. Root element: {}", root.getNodeName());

            // For XSD files, we should specifically look for schema elements
            if ("schema".equals(root.getLocalName()) || root.getNodeName().endsWith(":schema")) {
                parseXsdSchema(root);
            } else {
                logger.warn("Root element is not a schema: {}", root.getNodeName());
                traverseElement(root, "", 0);
            }
        } else {
            logger.warn("Document root is null");
        }
    }

    /**
     * Parse XSD schema elements specifically
     */
    private void parseXsdSchema(Element schemaRoot) {
        logger.info("Parsing XSD schema with namespace: {}", schemaRoot.getNamespaceURI());

        String nsUri = "http://www.w3.org/2001/XMLSchema";

        // Parse global elements
        NodeList globalElements = schemaRoot.getElementsByTagNameNS(nsUri, "element");
        logger.info("Found {} global elements", globalElements.getLength());
        for (int i = 0; i < globalElements.getLength(); i++) {
            Element element = (Element) globalElements.item(i);
            if (element.getParentNode() == schemaRoot) { // Only direct children (global elements)
                parseXsdElement(element, "", 0);
            }
        }

        // Parse global complex types
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS(nsUri, "complexType");
        logger.info("Found {} complex types", complexTypes.getLength());
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == schemaRoot) { // Only global types
                parseXsdComplexType(complexType, "", 0);
            }
        }

        // Parse global simple types
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS(nsUri, "simpleType");
        logger.info("Found {} simple types", simpleTypes.getLength());
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (simpleType.getParentNode() == schemaRoot) { // Only global types
                parseXsdSimpleType(simpleType, "", 0);
            }
        }
    }

    private void parseXsdElement(Element element, String parentPath, int level) {
        String name = element.getAttribute("name");
        String type = element.getAttribute("type");
        String minOccurs = element.getAttribute("minOccurs");
        String maxOccurs = element.getAttribute("maxOccurs");

        if (name.isEmpty() && element.hasAttribute("ref")) {
            name = element.getAttribute("ref");
            type = "reference";
        }

        String currentPath = parentPath.isEmpty() ? name : parentPath + "/" + name;

        XsdGridRow row = new XsdGridRow(
                "  ".repeat(level) + name,
                type.isEmpty() ? "xs:string" : type,
                "element",
                minOccurs.isEmpty() ? "1" : minOccurs,
                maxOccurs.isEmpty() ? "1" : maxOccurs,
                currentPath,
                extractDocumentation(element),
                level
        );

        gridData.add(row);
        logger.debug("Added element: {} (type: {})", name, type);

        // Parse nested complex type if present
        NodeList complexTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (isDirectChild(complexType, element)) {
                parseComplexTypeContent(complexType, currentPath, level + 1);
            }
        }
    }

    private void parseXsdComplexType(Element complexType, String parentPath, int level) {
        String name = complexType.getAttribute("name");
        String currentPath = parentPath.isEmpty() ? name : parentPath + "/" + name;

        XsdGridRow row = new XsdGridRow(
                "  ".repeat(level) + name,
                "complexType",
                "complexType",
                "",
                "",
                currentPath,
                extractDocumentation(complexType),
                level
        );

        gridData.add(row);
        logger.debug("Added complex type: {}", name);

        parseComplexTypeContent(complexType, currentPath, level + 1);
    }

    private void parseXsdSimpleType(Element simpleType, String parentPath, int level) {
        String name = simpleType.getAttribute("name");
        String currentPath = parentPath.isEmpty() ? name : parentPath + "/" + name;

        XsdGridRow row = new XsdGridRow(
                "  ".repeat(level) + name,
                "simpleType",
                "simpleType",
                "",
                "",
                currentPath,
                extractDocumentation(simpleType),
                level
        );

        gridData.add(row);
        logger.debug("Added simple type: {}", name);
    }

    private void parseComplexTypeContent(Element complexType, String parentPath, int level) {
        String nsUri = "http://www.w3.org/2001/XMLSchema";

        // Parse sequence elements
        NodeList sequences = complexType.getElementsByTagNameNS(nsUri, "sequence");
        for (int i = 0; i < sequences.getLength(); i++) {
            Element sequence = (Element) sequences.item(i);
            if (isDirectChild(sequence, complexType)) {
                parseSequenceElements(sequence, parentPath, level);
            }
        }

        // Parse choice elements
        NodeList choices = complexType.getElementsByTagNameNS(nsUri, "choice");
        for (int i = 0; i < choices.getLength(); i++) {
            Element choice = (Element) choices.item(i);
            if (isDirectChild(choice, complexType)) {
                parseChoiceElements(choice, parentPath, level);
            }
        }

        // Parse attributes
        NodeList attributes = complexType.getElementsByTagNameNS(nsUri, "attribute");
        for (int i = 0; i < attributes.getLength(); i++) {
            Element attribute = (Element) attributes.item(i);
            parseXsdAttribute(attribute, parentPath, level);
        }
    }

    private void parseSequenceElements(Element sequence, String parentPath, int level) {
        NodeList elements = sequence.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (isDirectChild(element, sequence)) {
                parseXsdElement(element, parentPath, level);
            }
        }
    }

    private void parseChoiceElements(Element choice, String parentPath, int level) {
        NodeList elements = choice.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (isDirectChild(element, choice)) {
                parseXsdElement(element, parentPath, level);
            }
        }
    }

    private void parseXsdAttribute(Element attribute, String parentPath, int level) {
        String name = attribute.getAttribute("name");
        String type = attribute.getAttribute("type");
        String use = attribute.getAttribute("use");

        String currentPath = parentPath + "/@" + name;

        XsdGridRow row = new XsdGridRow(
                "  ".repeat(level) + "@" + name,
                type.isEmpty() ? "xs:string" : type,
                "attribute",
                "required".equals(use) ? "1" : "0",
                "1",
                currentPath,
                extractDocumentation(attribute),
                level
        );

        gridData.add(row);
        logger.debug("Added attribute: {} (type: {})", name, type);
    }

    private boolean isDirectChild(Element child, Element parent) {
        return child.getParentNode() == parent;
    }

    private void traverseElement(Element element, String parentPath, int level) {
        String elementName = element.getLocalName();
        if (elementName == null) elementName = element.getNodeName();

        String currentPath = parentPath.isEmpty() ? elementName : parentPath + "/" + elementName;

        // Create grid row for this element
        XsdGridRow row = new XsdGridRow(
                "  ".repeat(level) + (element.getAttribute("name").isEmpty() ? elementName : element.getAttribute("name")),
                element.getAttribute("type"),
                elementName,
                element.getAttribute("minOccurs"),
                element.getAttribute("maxOccurs"),
                currentPath,
                extractDocumentation(element),
                level
        );

        gridData.add(row);

        // Traverse child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                traverseElement(child, currentPath, level + 1);
            }
        }
    }

    private String extractDocumentation(Element element) {
        NodeList annotations = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation");
        if (annotations.getLength() > 0) {
            Element annotation = (Element) annotations.item(0);
            NodeList docs = annotation.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "documentation");
            if (docs.getLength() > 0) {
                return docs.item(0).getTextContent();
            }
        }
        return "";
    }

    private XsdNodeInfo convertToNodeInfo(XsdGridRow row) {
        // This is a simplified conversion - in a full implementation,
        // we would need to properly map back to the DOM structure
        // Map node type string to enum
        XsdNodeInfo.NodeType nodeType;
        try {
            nodeType = XsdNodeInfo.NodeType.valueOf(row.nodeType().toUpperCase());
        } catch (IllegalArgumentException e) {
            nodeType = XsdNodeInfo.NodeType.ELEMENT; // Default fallback
        }

        return new XsdNodeInfo(
                row.name().trim(),           // name
                row.type(),                  // type
                row.xpath(),                 // xpath
                row.documentation(),         // documentation
                java.util.List.of(),           // children would need to be properly resolved
                java.util.List.of(),           // exampleValues
                row.minOccurs(),            // minOccurs
                row.maxOccurs(),            // maxOccurs
                nodeType                       // nodeType
        );
    }

    private void focusOnElement(XsdGridRow row) {
        logger.info("Focusing on element: {}", row.name());

        try {
            // Scroll to the selected row in the table
            int index = gridData.indexOf(row);
            if (index >= 0) {
                gridTable.getSelectionModel().select(index);
                gridTable.scrollTo(index);
                gridTable.requestFocus();

                logger.debug("Focused on row {} (index: {})", row.name(), index);
            } else {
                logger.warn("Row not found in current data: {}", row.name());
            }

            // Update selected node property for external listeners
            XsdNodeInfo nodeInfo = convertToNodeInfo(row);
            selectedNodeProperty.set(nodeInfo);

            // Show details in a popup for complex elements
            if ("complexType".equals(row.nodeType()) || "element".equals(row.nodeType())) {
                showElementDetails(row);
            }

        } catch (Exception e) {
            logger.error("Failed to focus on element: {}", row.name(), e);
        }
    }

    /**
     * Show detailed information about an element in a popup
     */
    private void showElementDetails(XsdGridRow row) {
        try {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Element Details");
            alert.setHeaderText("Details for: " + row.name());

            StringBuilder details = new StringBuilder();
            details.append("Name: ").append(row.name()).append("\n");
            details.append("Type: ").append(row.type()).append("\n");
            details.append("Kind: ").append(row.nodeType()).append("\n");
            details.append("XPath: ").append(row.xpath()).append("\n");
            details.append("Level: ").append(row.level()).append("\n");

            if (!row.minOccurs().isEmpty() || !row.maxOccurs().isEmpty()) {
                details.append("Cardinality: ").append(row.minOccurs()).append(" - ").append(row.maxOccurs()).append("\n");
            }

            if (!row.documentation().isEmpty()) {
                details.append("\nDocumentation:\n").append(row.documentation());
            }

            alert.setContentText(details.toString());
            alert.setResizable(true);
            alert.getDialogPane().setPrefWidth(500);
            alert.getDialogPane().setPrefHeight(300);

            alert.showAndWait();

        } catch (Exception e) {
            logger.error("Failed to show element details for: {}", row.name(), e);
        }
    }

    private void expandAllRows() {
        logger.info("Expanding all rows");

        // First, collect all items and mark them as expanded
        allGridData.clear();
        allGridData.addAll(gridData);

        // For now, we simulate expand by showing items that would be hidden
        // In a full implementation, this would maintain hierarchical state
        for (int i = 0; i < allGridData.size(); i++) {
            XsdGridRow row = allGridData.get(i);
            if (!row.expanded()) {
                // Create expanded version
                XsdGridRow expandedRow = new XsdGridRow(
                        row.name(), row.type(), row.nodeType(),
                        row.minOccurs(), row.maxOccurs(),
                        row.xpath(), row.documentation(),
                        row.level(), true
                );
                allGridData.set(i, expandedRow);
            }
        }

        // Update the displayed data
        gridData.clear();
        gridData.addAll(allGridData);

        logger.info("Expanded view now shows {} rows", gridData.size());
    }

    private void collapseAllRows() {
        logger.info("Collapsing all rows");

        // Collapse by showing only top-level items (level 0 and 1)
        ObservableList<XsdGridRow> collapsedData = FXCollections.observableArrayList();

        for (XsdGridRow row : allGridData.isEmpty() ? gridData : allGridData) {
            if (row.level() <= 1) { // Show only root and first level
                // Create collapsed version  
                XsdGridRow collapsedRow = new XsdGridRow(
                        row.name(), row.type(), row.nodeType(),
                        row.minOccurs(), row.maxOccurs(),
                        row.xpath(), row.documentation(),
                        row.level(), false
                );
                collapsedData.add(collapsedRow);
            }
        }

        // Store all data for later expansion
        if (allGridData.isEmpty()) {
            allGridData.addAll(gridData);
        }

        // Update displayed data
        gridData.clear();
        gridData.addAll(collapsedData);

        logger.info("Collapsed view now shows {} rows (from {} total)", gridData.size(), allGridData.size());
    }

    private void exportToCsv() {
        logger.info("Exporting grid to CSV");

        try {
            // Create file chooser
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export XSD Grid to CSV");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("CSV Files", "*.csv")
            );
            fileChooser.setInitialFileName("xsd-grid-export.csv");

            // Get the window from the scene
            Window window = getScene() != null ? getScene().getWindow() : null;
            java.io.File selectedFile = fileChooser.showSaveDialog(window);

            if (selectedFile != null) {
                exportToCsvFile(selectedFile);
                logger.info("Successfully exported {} rows to CSV file: {}", gridData.size(), selectedFile.getAbsolutePath());

                // Show success message
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("CSV Export Completed");
                alert.setContentText("Successfully exported " + gridData.size() + " rows to:\n" + selectedFile.getAbsolutePath());
                alert.showAndWait();
            } else {
                logger.info("CSV export cancelled by user");
            }
        } catch (Exception e) {
            logger.error("Failed to export CSV", e);

            // Show error message
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setHeaderText("CSV Export Error");
            alert.setContentText("Failed to export CSV file:\n" + e.getMessage());
            alert.showAndWait();
        }
    }

    private void exportToCsvFile(java.io.File file) throws java.io.IOException {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(
                new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {

            // Write CSV header
            writer.println("Name,Type,Kind,Min,Max,XPath,Documentation,Level");

            // Write data rows
            for (XsdGridRow row : gridData) {
                writer.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d%n",
                        escapeCsv(row.name()),
                        escapeCsv(row.type()),
                        escapeCsv(row.nodeType()),
                        escapeCsv(row.minOccurs()),
                        escapeCsv(row.maxOccurs()),
                        escapeCsv(row.xpath()),
                        escapeCsv(row.documentation()),
                        row.level()
                );
            }
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        // Escape quotes by doubling them
        return value.replace("\"", "\"\"");
    }

    /**
     * Update the DOM manipulator and refresh the view
     * This method can be called when XSD content is loaded
     */
    public void updateDomManipulator(XsdDomManipulator newDomManipulator) {
        logger.info("Updating DOM manipulator in XsdGridView");
        this.domManipulator = newDomManipulator;
        refreshView();
    }

    // Property accessor
    public ObjectProperty<XsdNodeInfo> selectedNodeProperty() {
        return selectedNodeProperty;
    }

    /**
     * Data class for grid rows
     */
    public record XsdGridRow(String name, String type, String nodeType, String minOccurs, String maxOccurs,
                             String xpath, String documentation, int level, boolean expanded) {

        // Constructor without expanded flag (defaults to true)
        public XsdGridRow(String name, String type, String nodeType, String minOccurs, String maxOccurs,
                          String xpath, String documentation, int level) {
            this(name, type, nodeType, minOccurs, maxOccurs, xpath, documentation, level, true);
        }
    }
}