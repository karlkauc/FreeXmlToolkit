package org.fxt.freexmltoolkit.controls;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
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

    private final XsdDomManipulator domManipulator;
    private final TableView<XsdGridRow> gridTable;
    private final ObservableList<XsdGridRow> gridData;
    private final ObjectProperty<XsdNodeInfo> selectedNodeProperty = new SimpleObjectProperty<>();

    public XsdGridView(XsdDomManipulator domManipulator) {
        this.domManipulator = domManipulator;
        this.gridData = FXCollections.observableArrayList();
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

        // Layout
        setTop(toolbar);
        setCenter(gridTable);
    }

    @SuppressWarnings("unchecked")
    private void setupTableColumns() {
        // Name column
        TableColumn<XsdGridRow, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(200);

        // Type column
        TableColumn<XsdGridRow, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setPrefWidth(150);

        // Node Type column
        TableColumn<XsdGridRow, String> nodeTypeColumn = new TableColumn<>("Kind");
        nodeTypeColumn.setCellValueFactory(new PropertyValueFactory<>("nodeType"));
        nodeTypeColumn.setPrefWidth(100);

        // Min Occurs column
        TableColumn<XsdGridRow, String> minOccursColumn = new TableColumn<>("Min");
        minOccursColumn.setCellValueFactory(new PropertyValueFactory<>("minOccurs"));
        minOccursColumn.setPrefWidth(60);

        // Max Occurs column
        TableColumn<XsdGridRow, String> maxOccursColumn = new TableColumn<>("Max");
        maxOccursColumn.setCellValueFactory(new PropertyValueFactory<>("maxOccurs"));
        maxOccursColumn.setPrefWidth(60);

        // XPath column
        TableColumn<XsdGridRow, String> xpathColumn = new TableColumn<>("XPath");
        xpathColumn.setCellValueFactory(new PropertyValueFactory<>("xpath"));
        xpathColumn.setPrefWidth(300);

        // Documentation column
        TableColumn<XsdGridRow, String> docColumn = new TableColumn<>("Documentation");
        docColumn.setCellValueFactory(new PropertyValueFactory<>("documentation"));
        docColumn.setPrefWidth(200);

        gridTable.getColumns().addAll(nameColumn, typeColumn, nodeTypeColumn,
                minOccursColumn, maxOccursColumn, xpathColumn, docColumn);
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
            populateGridFromDocument();
        }

        logger.info("Grid view refreshed with {} rows", gridData.size());
    }

    private void populateGridFromDocument() {
        Element root = domManipulator.getDocument().getDocumentElement();
        if (root != null) {
            traverseElement(root, "", 0);
        }
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
        // Placeholder for focus functionality
        logger.info("Focusing on element: {}", row.name());
    }

    private void expandAllRows() {
        // Placeholder for expand functionality
        logger.info("Expanding all rows");
    }

    private void collapseAllRows() {
        // Placeholder for collapse functionality
        logger.info("Collapsing all rows");
    }

    private void exportToCsv() {
        // Placeholder for CSV export functionality
        logger.info("Exporting grid to CSV");
    }

    // Property accessor
    public ObjectProperty<XsdNodeInfo> selectedNodeProperty() {
        return selectedNodeProperty;
    }

    /**
         * Data class for grid rows
         */
        public record XsdGridRow(String name, String type, String nodeType, String minOccurs, String maxOccurs,
                                 String xpath, String documentation, int level) {
    }
}