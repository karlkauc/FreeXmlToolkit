package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.*;

import java.beans.PropertyChangeEvent;
import java.util.*;

/**
 * Grid view component for displaying repeating XML elements using GridPane.
 *
 * <p>This implementation follows the XMLSpy-style table display pattern
 * from XmlGraphicEditor, using GridPane instead of TableView.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>XMLSpy-style grid for repeating elements</li>
 *   <li>GridPane-based table layout (NOT TableView)</li>
 *   <li>Automatic column header detection</li>
 *   <li>Row numbers and alternating row colors</li>
 *   <li>Follows shouldBeTable logic from XmlGraphicEditor</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlGridView extends BorderPane {

    private final XmlEditorContext context;
    private final ScrollPane scrollPane;
    private XmlNode parentNode;
    private String repeatingElementName;

    // ==================== Constructor ====================

    public XmlGridView(XmlEditorContext context) {
        this.context = context;
        this.scrollPane = new ScrollPane();

        setupScrollPane();
        setupListeners();

        setCenter(scrollPane);
    }

    // ==================== Setup Methods ====================

    private void setupScrollPane() {
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: white;");
    }

    private void setupListeners() {
        // Listen to document changes
        context.addPropertyChangeListener("document", this::onDocumentChanged);

        // Listen to selection changes to update grid
        context.getSelectionModel().addPropertyChangeListener("selectedNode", this::onSelectionChanged);
    }

    // ==================== Grid Building ====================

    /**
     * Loads grid data for a parent node containing repeating elements.
     *
     * @param parent      the parent node
     * @param elementName the name of repeating child elements
     */
    public void loadRepeatingElements(XmlNode parent, String elementName) {
        this.parentNode = parent;
        this.repeatingElementName = elementName;
        buildGrid();
    }

    /**
     * Checks if a node should be displayed as a table/grid.
     * Ported from XmlGraphicEditor.shouldBeTable(Node n).
     *
     * @param node the node to check
     * @return true if node has 2+ child elements with the same name
     */
    public static boolean shouldBeTable(XmlNode node) {
        List<XmlNode> children = getChildrenList(node);

        if (children.size() < 2) {
            return false;
        }

        String firstChildName = null;
        int elementNodeCount = 0;

        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                elementNodeCount++;
                if (firstChildName == null) {
                    firstChildName = ((XmlElement) child).getName();
                } else if (!firstChildName.equals(((XmlElement) child).getName())) {
                    return false; // Different names, so no table
                }
            }
        }

        return elementNodeCount > 1;
    }

    private void buildGrid() {
        if (parentNode == null || repeatingElementName == null) {
            clearGrid();
            return;
        }

        // Find all repeating elements
        List<XmlElement> elements = new ArrayList<>();
        for (XmlNode child : getChildrenList(parentNode)) {
            if (child instanceof XmlElement) {
                XmlElement elem = (XmlElement) child;
                if (elem.getName().equals(repeatingElementName)) {
                    elements.add(elem);
                }
            }
        }

        if (elements.isEmpty()) {
            clearGrid();
            return;
        }

        // Create table using GridPane (following XmlGraphicEditor pattern)
        GridPane gridPane = createTable(elements);
        scrollPane.setContent(gridPane);
    }

    /**
     * Creates a GridPane-based table for repeating elements.
     * Ported from XmlGraphicEditor.createTable(Node subNode).
     *
     * @param elements the list of repeating elements
     * @return GridPane containing the table
     */
    private GridPane createTable(List<XmlElement> elements) {
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("xmlspy-table-grid");

        // XMLSpy-inspired styling
        gridPane.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #c0c0c0; " +
                        "-fx-border-width: 1px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 3, 0, 1, 1);"
        );

        // Set grid lines and gaps for XMLSpy look
        gridPane.setGridLinesVisible(true);
        gridPane.setHgap(0);
        gridPane.setVgap(0);

        // Map to store column names and their indices.
        // LinkedHashMap maintains insertion order, ensuring consistent column order.
        Map<String, Integer> columns = new LinkedHashMap<>();

        // --- STEP 1: Determine all column headers in advance ---
        // Start with index 1 to leave space for row number column at index 0
        for (XmlElement element : elements) {
            for (XmlNode child : element.getChildren()) {
                if (child instanceof XmlElement) {
                    String childName = ((XmlElement) child).getName();
                    // Add the column name if it doesn't exist yet
                    columns.computeIfAbsent(childName, k -> columns.size() + 1);
                }
            }
        }

        // --- STEP 2: Create row number header column (column 0) ---
        Label rowNumberHeaderLabel = new Label("#");
        rowNumberHeaderLabel.setStyle(
                "-fx-text-fill: #333333; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        StackPane rowNumberHeaderPane = new StackPane(rowNumberHeaderLabel);
        rowNumberHeaderPane.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #e8e8e8, #d0d0d0); " +
                        "-fx-border-color: #c0c0c0; " +
                        "-fx-border-width: 0 1px 1px 0; " +
                        "-fx-padding: 4px 8px; " +
                        "-fx-min-width: 40px; " +
                        "-fx-max-width: 40px;"
        );
        rowNumberHeaderPane.setAlignment(Pos.CENTER);
        rowNumberHeaderPane.getStyleClass().add("xmlspy-table-header-rownumber");
        gridPane.add(rowNumberHeaderPane, 0, 0); // Row number header in column 0, row 0

        // --- STEP 3: Create data column headers (starting from column 1) ---
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            String columnName = entry.getKey();
            int columnIndex = entry.getValue();

            Label headerLabel = new Label(columnName);
            headerLabel.setStyle(
                    "-fx-text-fill: #333333; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
            );

            StackPane headerPane = new StackPane(headerLabel);
            headerPane.setStyle(
                    "-fx-background-color: linear-gradient(to bottom, #f5f5f5, #e8e8e8); " +
                            "-fx-border-color: #c0c0c0; " +
                            "-fx-border-width: 0 1px 1px 0; " +
                            "-fx-padding: 4px 8px;"
            );
            headerPane.setAlignment(Pos.CENTER_LEFT);
            headerPane.getStyleClass().add("xmlspy-table-header");
            gridPane.add(headerPane, columnIndex, 0); // Header always in row 0
        }

        // --- STEP 4: Fill data rows ---
        int row = 1; // Data starts in row 1
        int rowNumber = 1; // Row number counter
        for (XmlElement element : elements) {
            // Add row number cell first (column 0)
            addRowNumberCell(gridPane, rowNumber, row);

            // Add table row cells
            addTableRow(gridPane, element, row, columns);

            row++;
            rowNumber++;
        }

        return gridPane;
    }

    /**
     * Adds a row number cell to the grid.
     *
     * @param gridPane  the grid pane
     * @param rowNumber the display row number (1-based)
     * @param row       the grid row position
     */
    private void addRowNumberCell(GridPane gridPane, int rowNumber, int row) {
        Label rowNumberLabel = new Label(String.valueOf(rowNumber));
        rowNumberLabel.setStyle(
                "-fx-text-fill: #666666; " +
                        "-fx-font-size: 10px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif; " +
                        "-fx-font-weight: bold;"
        );

        StackPane rowNumberPane = new StackPane(rowNumberLabel);

        // XMLSpy-inspired row number cell styling with alternating row colors
        boolean isOddRow = (row % 2) == 1;
        String backgroundColor = isOddRow ? "#f8f8f8" : "#f0f0f0";

        rowNumberPane.setStyle(
                "-fx-background-color: " + backgroundColor + "; " +
                        "-fx-border-color: #c0c0c0; " +
                        "-fx-border-width: 0 1px 1px 0; " +
                        "-fx-padding: 4px 8px; " +
                        "-fx-min-width: 40px; " +
                        "-fx-max-width: 40px;"
        );
        rowNumberPane.setAlignment(Pos.CENTER);
        rowNumberPane.getStyleClass().add("xmlspy-table-rownumber");

        gridPane.add(rowNumberPane, 0, row);
    }

    /**
     * Adds all cells for a table row.
     *
     * @param gridPane the grid pane
     * @param element  the element representing this row
     * @param row      the grid row position
     * @param columns  map of column names to column indices
     */
    private void addTableRow(GridPane gridPane, XmlElement element, int row, Map<String, Integer> columns) {
        for (XmlNode child : element.getChildren()) {
            if (child instanceof XmlElement) {
                addTableCell(gridPane, (XmlElement) child, row, columns);
            }
        }
    }

    /**
     * Adds a single cell to the table.
     *
     * @param gridPane     the grid pane
     * @param childElement the child element
     * @param row          the grid row position
     * @param columns      map of column names to column indices
     */
    private void addTableCell(GridPane gridPane, XmlElement childElement, int row, Map<String, Integer> columns) {
        String nodeName = childElement.getName();

        // Get column position from pre-filled map
        Integer colPos = columns.get(nodeName);
        if (colPos == null) {
            System.err.println("Column '" + nodeName + "' not found in pre-calculated header map. Skipping cell.");
            return;
        }

        StackPane cellPane;

        // Check if element has simple text content (no child elements)
        boolean hasOnlyTextContent = childElement.getChildren().stream()
                .noneMatch(child -> child instanceof XmlElement);

        if (hasOnlyTextContent) {
            // Simple text cell
            String textContent = childElement.getTextContent();
            Label contentLabel = new Label(textContent != null ? textContent : "");

            // XMLSpy-inspired text cell styling
            contentLabel.setStyle(
                    "-fx-text-fill: #000000; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
            );

            cellPane = new StackPane(contentLabel);
        } else {
            // Complex nested elements - display as nested structure
            Label nestedLabel = new Label("[Complex]");
            nestedLabel.setStyle(
                    "-fx-text-fill: #666666; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-style: italic;"
            );
            cellPane = new StackPane(nestedLabel);
        }

        // Cell styling with alternating row colors
        boolean isOddRow = (row % 2) == 1;
        String backgroundColor = isOddRow ? "#ffffff" : "#f9f9f9";

        cellPane.setStyle(
                "-fx-background-color: " + backgroundColor + "; " +
                        "-fx-border-color: #c0c0c0; " +
                        "-fx-border-width: 0 1px 1px 0; " +
                        "-fx-padding: 4px 8px;"
        );
        cellPane.setAlignment(Pos.CENTER_LEFT);
        cellPane.getStyleClass().add("xmlspy-table-cell");

        gridPane.add(cellPane, colPos, row);
    }

    private void clearGrid() {
        scrollPane.setContent(new Label("No repeating elements to display.\nSelect a parent with repeating child elements."));
        parentNode = null;
        repeatingElementName = null;
    }

    public void refresh() {
        buildGrid();
    }

    // ==================== Event Handlers ====================

    private void onDocumentChanged(PropertyChangeEvent evt) {
        clearGrid();
    }

    private void onSelectionChanged(PropertyChangeEvent evt) {
        XmlNode selected = (XmlNode) evt.getNewValue();
        if (selected == null) {
            return;
        }

        // Try to detect repeating elements in selected node
        if (selected instanceof XmlElement || selected instanceof XmlDocument) {
            detectAndLoadRepeatingElements(selected);
        }
    }

    private void detectAndLoadRepeatingElements(XmlNode node) {
        // Use the shouldBeTable logic to detect repeating elements
        if (!shouldBeTable(node)) {
            clearGrid();
            return;
        }

        // Find the repeating element name
        List<XmlNode> children = getChildrenList(node);
        String firstElementName = null;

        for (XmlNode child : children) {
            if (child instanceof XmlElement) {
                firstElementName = ((XmlElement) child).getName();
                break;
            }
        }

        if (firstElementName != null) {
            loadRepeatingElements(node, firstElementName);
        } else {
            clearGrid();
        }
    }

    // ==================== Utility Methods ====================

    /**
     * Helper method to get children from XmlNode (handles both XmlElement and XmlDocument).
     */
    private static List<XmlNode> getChildrenList(XmlNode node) {
        if (node instanceof XmlElement) {
            return ((XmlElement) node).getChildren();
        } else if (node instanceof XmlDocument) {
            return ((XmlDocument) node).getChildren();
        }
        return Collections.emptyList();
    }

    // ==================== Public API ====================

    public XmlNode getParentNode() {
        return parentNode;
    }

    public String getRepeatingElementName() {
        return repeatingElementName;
    }
}
