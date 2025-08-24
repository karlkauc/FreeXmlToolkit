package org.fxt.freexmltoolkit.controls;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * A custom VBox implementation for displaying XML nodes in a tree structure.
 * Styles are managed via an external CSS file.
 * This version is refactored for a more modern look and better performance.
 */
public class XmlGraphicEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(XmlGraphicEditor.class);

    private final XmlEditor xmlEditor;
    private final Node currentDomNode;
    private javafx.scene.Node currentSelectedUINode;
    private Node currentSelectedDomNode;

    // Map to store direct UI Node -> DOM Node associations
    private final Map<javafx.scene.Node, Node> uiNodeToDomNodeMap = new LinkedHashMap<>();

    public XmlGraphicEditor(Node node, XmlEditor caller) {
        this.xmlEditor = caller;
        this.currentDomNode = node;
        // Assign a CSS class to the root element for targeted styling.
        this.getStyleClass().add("simple-node-element");

        if (node.hasChildNodes()) {
            addChildNodes(node);
        }

        // NO context menu for the main VBox to avoid duplicate menus
        // Context menus are only created for individual child elements
    }

    /**
     * Sidebar functionality has been moved to Ultimate XML Editor
     */
    public void setSidebarController(Object sidebarController) {
        // Functionality moved to Ultimate XML Editor
    }

    /**
     * Sets up node selection for all existing UI nodes - called after sidebar controller is set
     */
    private void setupNodeSelectionForAllNodes() {
        logger.info("🔄 XmlGraphicEditor: Setting up node selection for {} mapped nodes", uiNodeToDomNodeMap.size());

        // Functionality moved to Ultimate XML Editor
        for (Map.Entry<javafx.scene.Node, Node> entry : uiNodeToDomNodeMap.entrySet()) {
            javafx.scene.Node uiNode = entry.getKey();
            Node domNode = entry.getValue();

            logger.debug("🔗 Setting up selection for mapped: {} -> DOM: {}",
                    uiNode.getClass().getSimpleName(), domNode.getNodeName());

            // Functionality moved to Ultimate XML Editor
        }
    }


    /**
     * Sets up node selection for a specific UI node - DOM node pair
     */
    private void setupNodeSelectionForSpecificNode(javafx.scene.Node uiNode, Node domNode) {
        if (false) { // Functionality moved to Ultimate XML Editor
            logger.warn("⚠️  XmlGraphicEditor: Cannot setup node selection - functionality moved to Ultimate XML Editor!");
            return;
        }

        logger.debug("🖱️  Setting up node selection for: {} -> {}",
                uiNode.getClass().getSimpleName(), domNode.getNodeName());

        uiNode.setOnMouseClicked(event -> {
            logger.info("🖱️ CLICK EVENT: clickCount={}, target={}, domNode={}",
                    event.getClickCount(), event.getTarget().getClass().getSimpleName(), domNode.getNodeName());

            if (event.getClickCount() == 1) {
                // Single click to select for sidebar
                logger.info("👆 XmlGraphicEditor: Processing single click for node - {}", domNode.getNodeName());
                selectNode(uiNode, domNode);
                logger.info("✅ XmlGraphicEditor: Single click processed for - {}", domNode.getNodeName());
                // IMPORTANT: Consume the event to prevent bubbling to parent elements
                event.consume();
                logger.debug("🛑 XmlGraphicEditor: Event consumed to prevent bubbling");
            } else {
                logger.info("🖱️ XmlGraphicEditor: Multi-click ({}) - letting it bubble for - {}",
                        event.getClickCount(), domNode.getNodeName());
            }
            // For double-clicks, don't handle here - let them bubble to child elements (Labels)
        });

        // Add visual selection feedback
        uiNode.setOnMouseEntered(event -> {
            if (currentSelectedUINode != uiNode) {
                uiNode.setStyle(uiNode.getStyle() + "-fx-background-color: rgba(74, 144, 226, 0.1);");
            }
        });

        uiNode.setOnMouseExited(event -> {
            if (currentSelectedUINode != uiNode) {
                // Remove hover effect by resetting style
                String style = uiNode.getStyle();
                if (style.contains("-fx-background-color: rgba(74, 144, 226, 0.1);")) {
                    uiNode.setStyle(style.replace("-fx-background-color: rgba(74, 144, 226, 0.1);", ""));
                }
            }
        });
    }

    /**
     * Special setup for text cells that preserves double-click text editing
     */
    private void setupNodeSelectionForTextCell(javafx.scene.Node cellPane, Node domNode) {
        if (false) { // Functionality moved to Ultimate XML Editor
            return;
        }

        logger.debug("🖱️📝 Setting up text cell selection for: {}", domNode.getNodeName());

        cellPane.setOnMouseClicked(event -> {
            logger.info("🖱️📝 TEXT CELL CLICK: clickCount={}, target={}, domNode={}",
                    event.getClickCount(), event.getTarget().getClass().getSimpleName(), domNode.getNodeName());

            if (event.getClickCount() == 1) {
                // Single click to select for sidebar
                logger.info("👆 XmlGraphicEditor: Processing text cell click - {}", domNode.getNodeName());
                selectNode(cellPane, domNode);
                logger.info("✅ XmlGraphicEditor: Text cell click processed for - {}", domNode.getNodeName());
                // Don't consume - let double-clicks reach the label inside
            } else {
                logger.info("🖱️📝 XmlGraphicEditor: Text cell multi-click ({}) - letting it bubble for - {}",
                        event.getClickCount(), domNode.getNodeName());
            }
            // Let double-clicks bubble to child Label elements
        });

        // Add visual selection feedback (same as normal)
        cellPane.setOnMouseEntered(event -> {
            if (currentSelectedUINode != cellPane) {
                cellPane.setStyle(cellPane.getStyle() + "-fx-background-color: rgba(74, 144, 226, 0.1);");
            }
        });

        cellPane.setOnMouseExited(event -> {
            if (currentSelectedUINode != cellPane) {
                String style = cellPane.getStyle();
                if (style.contains("-fx-background-color: rgba(74, 144, 226, 0.1);")) {
                    cellPane.setStyle(style.replace("-fx-background-color: rgba(74, 144, 226, 0.1);", ""));
                }
            }
        });
    }

    private void addChildNodes(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            var subNode = node.getChildNodes().item(i);

            switch (subNode.getNodeType()) {
                case Node.COMMENT_NODE -> addCommentNode(subNode);
                case Node.ELEMENT_NODE -> addElementNode(subNode);
                case Node.TEXT_NODE -> {
                    // Empty text nodes (often just line breaks) are ignored
                    if (subNode.getNodeValue() != null && !subNode.getNodeValue().trim().isEmpty()) {
                        this.getChildren().add(new Label("TEXT: " + subNode.getNodeValue()));
                    }
                }
                default -> this.getChildren().add(new Label("DEFAULT: " + subNode.getNodeName()));
            }
        }
    }

    private void addCommentNode(Node subNode) {
        Label label = new Label("<!-- " + subNode.getNodeValue().trim() + " -->");
        label.getStyleClass().add("xml-tree-comment");
        // XMLSpy-inspired comment styling
        label.setStyle(
                "-fx-text-fill: #6c757d; " +
                        "-fx-font-style: italic; " +
                        "-fx-font-size: 10px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif; " +
                        "-fx-background-color: #f8f9fa; " +
                        "-fx-padding: 4px 8px; " +
                        "-fx-border-color: #e9ecef; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px;"
        );
        this.getChildren().add(label);
    }

    private void addElementNode(Node subNode) {
        // Check if the node has only a single text child (e.g. <tag>value</tag>)
        boolean isTextNode = subNode.getChildNodes().getLength() == 1 && subNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE;

        if (isTextNode) {
            addTextNode(subNode);
        } else {
            addComplexNode(subNode);
        }
    }

    private void addTextNode(Node subNode) {
        var firstItem = subNode.getChildNodes().item(0);
        var nodeName = new Label(subNode.getNodeName());
        var nodeValue = new Label(firstItem.getNodeValue());

        // XMLSpy-inspired styling for text elements
        nodeName.setStyle(
                "-fx-text-fill: #2c5aa0; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        nodeValue.setStyle(
                "-fx-text-fill: #000000; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        // Disable line wrapping for the node name.
        nodeName.setWrapText(false);
        nodeValue.setWrapText(true); // The value may continue to wrap.

        nodeName.setTooltip(new Tooltip(subNode.getNodeName()));
        nodeValue.setTooltip(new Tooltip(firstItem.getNodeValue()));

        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, subNode));

        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("xml-tree-text");

        // XMLSpy-inspired GridPane styling
        gridPane.setStyle(
                "-fx-background-color: #f0f8ff; " +
                        "-fx-border-color: #4a90e2; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 2, 0, 1, 1); " +
                        "-fx-padding: 6px;"
        );

        // Flexible column layout instead of fixed percentage values.
        // The name column takes as much space as it needs.
        ColumnConstraints nameColumn = new ColumnConstraints();
        nameColumn.setHgrow(Priority.NEVER);

        // The value column fills all remaining space.
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);

        gridPane.getColumnConstraints().addAll(nameColumn, valueColumn);

        addAttributesToGridPane(subNode, gridPane);

        var nodeNameBox = new HBox(nodeName);
        nodeNameBox.getStyleClass().add("node-name-box");
        nodeNameBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #ffffff, #f0f8ff); " +
                        "-fx-padding: 4px 8px; " +
                        "-fx-border-color: #e0e8ff; " +
                        "-fx-border-width: 0 1px 0 0;"
        );

        var nodeValueBox = new HBox(nodeValue);
        nodeValueBox.getStyleClass().add("node-value-box");
        nodeValueBox.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-padding: 4px 8px;"
        );

        final int row = gridPane.getRowCount();
        gridPane.add(nodeNameBox, 0, row);
        gridPane.add(nodeValueBox, 1, row);

        // Add context menu for text nodes - only to the GridPane to avoid duplicate menus
        logger.debug("Setting up context menu for text node: {} (Type: {})", subNode.getNodeName(), subNode.getNodeType());
        setupContextMenu(gridPane, subNode);

        // Store the mapping between UI element and DOM node
        uiNodeToDomNodeMap.put(gridPane, subNode);
        logger.debug("🗂️ Mapped GridPane -> DOM node: {}", subNode.getNodeName());

        // Set up node selection immediately if sidebar controller is available
        if (false) { // Functionality moved to Ultimate XML Editor
            setupNodeSelectionForSpecificNode(gridPane, subNode);
            logger.debug("✅ Set up node selection for text GridPane: {}", subNode.getNodeName());
        }

        this.getChildren().add(gridPane);
    }

    private void addAttributesToGridPane(Node subNode, GridPane gridPane) {
        if (subNode.hasAttributes()) {
            for (int i = 0; i < subNode.getAttributes().getLength(); i++) {
                var attribute = subNode.getAttributes().item(i);

                var attributeNameLabel = new Label(attribute.getNodeName());
                // XMLSpy-inspired attribute styling
                attributeNameLabel.setStyle(
                        "-fx-text-fill: #8b6914; " +
                                "-fx-font-weight: bold; " +
                                "-fx-font-size: 10px; " +
                                "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
                );
                
                var attributeBox = new HBox(attributeNameLabel);
                attributeBox.getStyleClass().add("attribute-box");
                attributeBox.setStyle(
                        "-fx-background-color: linear-gradient(to right, #fffef7, #fff8dc); " +
                                "-fx-padding: 3px 6px; " +
                                "-fx-border-color: #e8dcc0; " +
                                "-fx-border-width: 0 1px 0 0;"
                );

                var attributeValueLabel = new Label(attribute.getNodeValue());
                attributeValueLabel.setStyle(
                        "-fx-text-fill: #6c4100; " +
                                "-fx-font-size: 10px; " +
                                "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
                );
                attributeValueLabel.setOnMouseClicked(editNodeValueHandler(attributeValueLabel, attribute));
                var nodeValueBox = new HBox(attributeValueLabel);
                nodeValueBox.getStyleClass().add("attribute-value-box");
                nodeValueBox.setStyle(
                        "-fx-background-color: #ffffff; " +
                                "-fx-padding: 3px 6px;"
                );

                gridPane.add(attributeBox, 0, i);
                gridPane.add(nodeValueBox, 1, i);
            }
        }
    }

    /**
     * Refactored method to handle complex nodes with a more efficient expand/collapse logic.
     */
    private void addComplexNode(Node subNode) {
        // Container for the entire element (Parent + Children)
        VBox elementContainer = new VBox();
        elementContainer.getStyleClass().add("element-container");
        elementContainer.setAlignment(Pos.TOP_CENTER);
        elementContainer.setStyle("-fx-spacing: 2px;");

        // 1. The header for the expandable area
        HBox headerBox = new HBox(5);
        headerBox.getStyleClass().add("element-box");
        headerBox.setAlignment(Pos.CENTER_LEFT);

        // XMLSpy-inspired header styling
        headerBox.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                        "-fx-border-color: #6c757d; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 1, 1); " +
                        "-fx-padding: 6px 8px;"
        );

        Region icon = new Region();
        icon.getStyleClass().add("icon");

        Button toggleButton = new Button();
        toggleButton.setGraphic(icon);
        toggleButton.getStyleClass().add("tree-toggle-button");
        toggleButton.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-border-color: #4a90e2; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-padding: 2px 6px; " +
                        "-fx-cursor: hand;"
        );
        icon.getStyleClass().add("toggle-expand");
        icon.setStyle(
                "-fx-background-color: #4a90e2; " +
                        "-fx-min-width: 8px; " +
                        "-fx-min-height: 8px;"
        );

        Label label = new Label(subNode.getNodeName());
        label.getStyleClass().add("node-label-complex");
        label.setStyle(
                "-fx-text-fill: #2c5aa0; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 12px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        Label countLabel = new Label("(" + calculateNodeCount(subNode) + ")");
        countLabel.getStyleClass().add("node-count-label");
        countLabel.setStyle(
                "-fx-text-fill: #6c757d; " +
                        "-fx-font-size: 10px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif; " +
                        "-fx-font-style: italic;"
        );

        headerBox.getChildren().addAll(toggleButton, label, countLabel);

        // 2. The container for child elements
        VBox childrenContainer = new VBox();
        childrenContainer.getStyleClass().add("children-container");
        childrenContainer.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-border-width: 1px 1px 1px 3px; " +
                        "-fx-padding: 8px; " +
                        "-fx-spacing: 4px;"
        );

        // --- NEW: The side click bar for collapsing ---
        Region collapseBar = new Region();
        collapseBar.getStyleClass().add("collapse-bar");
        collapseBar.setStyle(
                "-fx-background-color: #4a90e2; " +
                        "-fx-min-width: 4px; " +
                        "-fx-max-width: 4px; " +
                        "-fx-opacity: 0.7;"
        );
        // A click on the bar triggers the toggle button action
        collapseBar.setOnMouseClicked(event -> {
            collapseBar.setStyle(collapseBar.getStyle() + "-fx-opacity: 1.0;");
            toggleButton.fire();
        });

        // --- NEW: An HBox wrapper for bar and content ---
        HBox contentWrapper = new HBox(collapseBar, childrenContainer);
        // The childrenContainer should take up all available horizontal space
        HBox.setHgrow(childrenContainer, Priority.ALWAYS);

        // Initial state: Everything invisible
        contentWrapper.setVisible(false);
        contentWrapper.setManaged(false);

        // 3. The action to toggle visibility
        toggleButton.setOnAction(event -> {
            boolean isExpanded = contentWrapper.isVisible();
            if (isExpanded) {
                // Collapse
                contentWrapper.setVisible(false);
                contentWrapper.setManaged(false);
                icon.getStyleClass().remove("toggle-collapse");
                icon.getStyleClass().add("toggle-expand");
            } else {
                // Expand
                if (childrenContainer.getChildren().isEmpty()) {
                    if (shouldBeTable(subNode)) {
                        childrenContainer.getChildren().add(createTable(subNode));
                    } else {
                        // Create nested XmlGraphicEditor and pass sidebar controller
                        XmlGraphicEditor nestedEditor = new XmlGraphicEditor(subNode, xmlEditor);
                        if (false) { // Functionality moved to Ultimate XML Editor
                            nestedEditor.setSidebarController(null);
                            logger.debug("🔗 Passed sidebar controller to nested editor for: {}", subNode.getNodeName());
                        }
                        childrenContainer.getChildren().add(nestedEditor);
                    }
                }
                contentWrapper.setVisible(true);
                contentWrapper.setManaged(true);
                icon.getStyleClass().remove("toggle-expand");
                icon.getStyleClass().add("toggle-collapse");
            }
        });

        // 4. Add header and the new contentWrapper to main VBox
        elementContainer.getChildren().addAll(headerBox, contentWrapper);

        // 5. Add context menu for the element
        logger.debug("Setting up context menu for complex node: {} (Type: {})", subNode.getNodeName(), subNode.getNodeType());
        setupContextMenu(elementContainer, subNode);

        // 6. Set up drag & drop for the element
        setupDragAndDrop(elementContainer, subNode);

        // Store the mapping between UI element and DOM node
        uiNodeToDomNodeMap.put(elementContainer, subNode);
        logger.debug("🗂️ Mapped VBox -> DOM node: {}", subNode.getNodeName());

        // Set up node selection immediately if sidebar controller is available
        if (false) { // Functionality moved to Ultimate XML Editor
            setupNodeSelectionForSpecificNode(elementContainer, subNode);
            logger.debug("✅ Set up node selection for complex element VBox: {}", subNode.getNodeName());
        }

        this.getChildren().add(elementContainer);
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValueLabel, Node domNode) {
        return event -> {
            if (event.getClickCount() != 2) return; // Only edit on double click

            if (!(nodeValueLabel.getParent() instanceof Pane parent)) {
                logger.warn("Cannot edit node value, label's parent is not a Pane.");
                return;
            }

            final String originalValue = nodeValueLabel.getText();
            TextField textField = new TextField(originalValue);

            // A flag to track whether editing was successfully committed.
            // We use an array so the variable is effectively final in the lambda expression.
            final boolean[] committed = {false};

            // 1. The commit action (ENTER) sets the flag to true.
            textField.setOnAction(e -> {
                handleEditCommit(textField, nodeValueLabel, domNode, parent);
                committed[0] = true;
            });

            // 2. The cancel action (focus loss) is ONLY executed if NOT committed.
            textField.focusedProperty().addListener((obs, oldVal, newVal) -> {
                if (!newVal && !committed[0]) { // Check the flag!
                    handleEditCancel(textField, nodeValueLabel, originalValue, parent);
                }
            });

            parent.getChildren().setAll(textField);
            textField.requestFocus();
            textField.selectAll();
        };
    }

    /**
     * Takes the new value from the text field, updates the UI label and the underlying XML DOM node.
     *
     * @param textField       The text field with the new value.
     * @param label           The UI label to be displayed again.
     * @param domNodeToUpdate The XML node (text or attribute) whose value is updated.
     * @param parent          The UI container in which the label/text field is located.
     */
    private void handleEditCommit(TextField textField, Label label, Node domNodeToUpdate, Pane parent) {
        // 1. Get new value from text field.
        final String newValue = textField.getText() != null ? textField.getText() : "";

        // 2. Update the text of the UI label.
        label.setText(newValue);

        // Log old and new values
        String oldValue = domNodeToUpdate.getNodeType() == Node.ELEMENT_NODE
                ? domNodeToUpdate.getTextContent()
                : domNodeToUpdate.getNodeValue();
        logger.info("Value change - Old: '{}', New: '{}'", oldValue, newValue);

        // 3. Update the value in the XML DOM.
        if (domNodeToUpdate.getNodeType() == Node.ELEMENT_NODE) {
            domNodeToUpdate.setTextContent(newValue);
        } else {
            domNodeToUpdate.setNodeValue(newValue);
        }

        // 4. Replace the text field with the label again.
        parent.getChildren().setAll(label);

        // 5. Update the text view of the editor.
        // CORRECTION: Calls the new method that reads from the DOM, not from the file.
        this.xmlEditor.refreshTextViewFromDom();
    }

    private void handleEditCancel(TextField textField, Label label, String originalValue, Pane parent) {
        label.setText(originalValue);
        parent.getChildren().setAll(label);
    }

    private GridPane createTable(Node subNode) {
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
        // We iterate through all rows just to collect column names.
        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);
            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
                    Node oneNode = oneRow.getChildNodes().item(x);
                    if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                        // Add the column name if it doesn't exist yet,
                        // and assign it the next available index.
                        columns.computeIfAbsent(oneNode.getNodeName(), k -> columns.size());
                    }
                }
            }
        }

        // --- STEP 2: Create header row based on collected columns ---
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            String columnName = entry.getKey();
            int columnIndex = entry.getValue();

            var headerLabel = new Label(columnName);
            headerLabel.setStyle(
                    "-fx-text-fill: #333333; " +
                            "-fx-font-weight: bold; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
            );
            
            var headerPane = new StackPane(headerLabel);
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

        // --- STEP 3: Fill data rows ---
        int row = 1; // Data starts in row 1
        for (int i = 0; i < subNode.getChildNodes().getLength(); i++) {
            Node oneRow = subNode.getChildNodes().item(i);
            if (oneRow.getNodeType() == Node.ELEMENT_NODE) {
                // The helper methods now use the pre-filled 'columns' map.
                addTableRow(gridPane, oneRow, row, columns);
                row++;
            }
        }
        return gridPane;
    }

    private void addTableRow(GridPane gridPane, Node oneRow, int row, Map<String, Integer> columns) {
        for (int x = 0; x < oneRow.getChildNodes().getLength(); x++) {
            Node oneNode = oneRow.getChildNodes().item(x);
            if (oneNode.getNodeType() == Node.ELEMENT_NODE) {
                addTableCell(gridPane, oneNode, row, columns);
            }
        }
    }

    private void addTableCell(GridPane gridPane, Node oneNode, int row, Map<String, Integer> columns) {
        var nodeName = oneNode.getNodeName();

        // The column position is now reliably retrieved from the pre-filled map.
        Integer colPos = columns.get(nodeName);
        if (colPos == null) {
            // This should not happen with the new createTable logic, but it's good safeguarding.
            logger.warn("Column '{}' not found in pre-calculated header map. Skipping cell.", nodeName);
            return;
        }

        StackPane cellPane;
        if (oneNode.getChildNodes().getLength() == 1 && oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            var contentLabel = new Label(oneNode.getTextContent());
            // XMLSpy-inspired text cell styling
            contentLabel.setStyle(
                    "-fx-text-fill: #000000; " +
                            "-fx-font-size: 11px; " +
                            "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
            );
            
            // We pass the ELEMENT node (oneNode), no longer its text child node.
            contentLabel.setOnMouseClicked(editNodeValueHandler(contentLabel, oneNode));
            cellPane = new StackPane(contentLabel);
        } else {
            // Nested complex nodes in a table
            XmlGraphicEditor nestedEditor = new XmlGraphicEditor(oneNode, xmlEditor);
            if (false) { // Functionality moved to Ultimate XML Editor
                nestedEditor.setSidebarController(null);
                logger.debug("🔗 Passed sidebar controller to table nested editor for: {}", oneNode.getNodeName());
            }
            cellPane = new StackPane(nestedEditor);
        }

        // Store the mapping for table cells too
        uiNodeToDomNodeMap.put(cellPane, oneNode);
        logger.debug("🗂️ Mapped table cell -> DOM node: {}", oneNode.getNodeName());

        // Set up node selection immediately if sidebar controller is available
        // But only for complex nodes (not for text nodes which have their own double-click editing)
        boolean isTextNode = (oneNode.getChildNodes().getLength() == 1 &&
                oneNode.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE);

        if (false) { // Functionality moved to Ultimate XML Editor
            if (!isTextNode) {
                setupNodeSelectionForSpecificNode(cellPane, oneNode);
                logger.debug("✅ Set up node selection for complex table cell: {}", oneNode.getNodeName());
            } else {
                // For text nodes in table cells, set up a special handler that doesn't interfere with text editing
                setupNodeSelectionForTextCell(cellPane, oneNode);
                logger.debug("✅ Set up text cell selection for table cell: {}", oneNode.getNodeName());
            }
        }

        // XMLSpy-inspired cell styling with alternating row colors
        boolean isOddRow = (row % 2) == 1;
        String backgroundColor = isOddRow ? "#ffffff" : "#f8f8f8";

        cellPane.setStyle(
                "-fx-background-color: " + backgroundColor + "; " +
                        "-fx-border-color: #e0e0e0; " +
                        "-fx-border-width: 0 1px 1px 0; " +
                        "-fx-padding: 4px 8px;"
        );
        cellPane.setAlignment(Pos.CENTER_LEFT);
        cellPane.getStyleClass().add("xmlspy-table-cell");
        gridPane.add(cellPane, colPos, row);
    }

    private static int calculateNodeCount(Node n) {
        return (int) IntStream.range(0, n.getChildNodes().getLength())
                .filter(i -> n.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE)
                .count();
    }

    private static boolean shouldBeTable(Node n) {
        if (n.getChildNodes().getLength() < 2) return false;

        String firstChildName = null;
        int elementNodeCount = 0;

        for (int i = 0; i < n.getChildNodes().getLength(); i++) {
            Node child = n.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementNodeCount++;
                if (firstChildName == null) {
                    firstChildName = child.getNodeName();
                } else if (!firstChildName.equals(child.getNodeName())) {
                    return false; // Different names, so no table
                }
            }
        }
        return elementNodeCount > 1;
    }

    private void setupContextMenu(javafx.scene.Node uiContainer, Node domNode) {
        logger.debug("Creating context menu for DOM node: {} (Type: {}, Parent: {})",
                domNode.getNodeName(),
                domNode.getNodeType(),
                domNode.getParentNode() != null ? domNode.getParentNode().getNodeName() : "null");

        ContextMenu contextMenu = new ContextMenu();

        // Only show "Add Child" if the node can have child elements
        if (canHaveChildren(domNode)) {
            MenuItem addChildMenuItem = new MenuItem("Add Child to: " + domNode.getNodeName());
            addChildMenuItem.setGraphic(createIcon("ADD_CHILD"));
            addChildMenuItem.setOnAction(e -> addChildNodeToSpecificParent(domNode));
            contextMenu.getItems().add(addChildMenuItem);
        }

        MenuItem addSiblingAfterMenuItem = new MenuItem("Add Sibling After");
        addSiblingAfterMenuItem.setGraphic(createIcon("ADD_AFTER"));
        addSiblingAfterMenuItem.setOnAction(e -> addSiblingNode(domNode, true));

        MenuItem addSiblingBeforeMenuItem = new MenuItem("Add Sibling Before");
        addSiblingBeforeMenuItem.setGraphic(createIcon("ADD_BEFORE"));
        addSiblingBeforeMenuItem.setOnAction(e -> addSiblingNode(domNode, false));

        MenuItem deleteMenuItem = new MenuItem("Delete: " + domNode.getNodeName());
        deleteMenuItem.setGraphic(createIcon("DELETE"));
        deleteMenuItem.setOnAction(e -> deleteNode(domNode));

        contextMenu.getItems().addAll(
                addSiblingAfterMenuItem,
                addSiblingBeforeMenuItem,
                new SeparatorMenuItem(),
                deleteMenuItem
        );

        uiContainer.setOnContextMenuRequested(e -> {
            logger.debug("Context menu requested for UI container. DOM node: {}", domNode.getNodeName());

            // Close all other context menus
            contextMenu.hide();

            // Show our menu
            contextMenu.show(uiContainer, e.getScreenX(), e.getScreenY());

            // Important: Consume event so it doesn't bubble up further
            e.consume();
        });
    }

    /**
     * Checks if a DOM node has text content.
     * A node has text content if it has exactly one text child.
     */
    private boolean hasTextContent(Node node) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }

        // Check if the node has exactly one text child
        return node.getChildNodes().getLength() == 1 &&
                node.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE;
    }

    /**
     * Checks if a DOM node can have child elements.
     * A node can have children if it has no text content.
     */
    private boolean canHaveChildren(Node node) {
        return !hasTextContent(node);
    }

    private void setupDragAndDrop(VBox elementContainer, Node domNode) {
        // Set up drag source - only for VBox containers (complex nodes)
        elementContainer.setOnDragDetected(event -> {
            Dragboard db = elementContainer.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(domNode.getNodeName() + "||" + System.identityHashCode(domNode));
            db.setContent(content);
            event.consume();
        });

        // Set up drop target
        elementContainer.setOnDragOver(event -> {
            if (event.getGestureSource() != elementContainer && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        elementContainer.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String[] data = db.getString().split("\\|\\|");
                if (data.length == 2) {
                    String sourceName = data[0];
                    try {
                        moveNodeToNewParent(domNode, sourceName);
                        success = true;
                    } catch (Exception e) {
                        logger.error("Error moving node", e);
                        showErrorDialog("Move failed", e.getMessage());
                    }
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void addChildNodeToSpecificParent(Node parentNode) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Node");
        dialog.setHeaderText("Create new child element for '" + parentNode.getNodeName() + "'");
        dialog.setContentText("Element Name:");

        dialog.showAndWait().ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                try {
                    Document doc = parentNode.getOwnerDocument();
                    Element newElement = doc.createElement(elementName.trim());

                    // Add an empty text node so the element is immediately editable
                    newElement.appendChild(doc.createTextNode(""));
                    
                    parentNode.appendChild(newElement);

                    // Intelligent UI update instead of complete recreation
                    updateUIAfterNodeAddition(parentNode, newElement);

                    logger.info("New child element '{}' added to '{}' with empty text content", elementName, parentNode.getNodeName());
                } catch (Exception e) {
                    showErrorDialog("Error adding element", e.getMessage());
                }
            }
        });
    }

    private void addSiblingNode(Node siblingNode, boolean after) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add New Node");
        dialog.setHeaderText(after ? "Create element after current" : "Create element before current");
        dialog.setContentText("Element Name:");

        dialog.showAndWait().ifPresent(elementName -> {
            if (!elementName.trim().isEmpty()) {
                try {
                    Node parentNode = siblingNode.getParentNode();
                    if (parentNode != null) {
                        Document doc = siblingNode.getOwnerDocument();
                        Element newElement = doc.createElement(elementName.trim());

                        // Add an empty text node so the element is immediately editable
                        newElement.appendChild(doc.createTextNode(""));

                        if (after) {
                            Node nextSibling = siblingNode.getNextSibling();
                            if (nextSibling != null) {
                                parentNode.insertBefore(newElement, nextSibling);
                            } else {
                                parentNode.appendChild(newElement);
                            }
                        } else {
                            parentNode.insertBefore(newElement, siblingNode);
                        }

                        // Intelligent UI update instead of complete recreation
                        updateUIAfterNodeAddition(parentNode, newElement);

                        logger.info("New sibling element '{}' added {} '{}' with empty text content",
                                elementName, after ? "after" : "before", siblingNode.getNodeName());
                    }
                } catch (Exception e) {
                    showErrorDialog("Error adding element", e.getMessage());
                }
            }
        });
    }

    private void deleteNode(Node nodeToDelete) {
        logger.info("Deleting node: Name='{}', Type={}, HasParent={}",
                nodeToDelete.getNodeName(),
                nodeToDelete.getNodeType(),
                nodeToDelete.getParentNode() != null);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Node");
        alert.setHeaderText("Delete node and all child nodes?");
        alert.setContentText("Element '" + nodeToDelete.getNodeName() + "' will be permanently deleted.\n" +
                "Node type: " + getNodeTypeString(nodeToDelete.getNodeType()) + "\n" +
                "Parent: " + (nodeToDelete.getParentNode() != null ? nodeToDelete.getParentNode().getNodeName() : "null"));

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    Node parentNode = nodeToDelete.getParentNode();
                    if (parentNode != null) {
                        parentNode.removeChild(nodeToDelete);

                        // UI aktualisieren
                        refreshWholeView();

                        logger.info("Element '{}' successfully deleted", nodeToDelete.getNodeName());
                    }
                } catch (Exception e) {
                    showErrorDialog("Error deleting element", e.getMessage());
                }
            }
        });
    }

    private void refreshWholeView() {
        // Rebuild the entire view - we need to reload from root
        // since the DOM structure has changed
        this.xmlEditor.refreshTextViewFromDom();

        // Reinitialize the graphical editor
        // For this we search for the parent XmlGraphicEditor instance
        findRootEditorAndRefresh();
    }

    private void findRootEditorAndRefresh() {
        // This method would in a real implementation
        // find the root editor and reload it
        // For now we just log that an update is needed
        logger.info("DOM structure changed - full UI refresh required");

        // Simple solution: Reload the current instance
        this.getChildren().clear();
        if (currentDomNode.hasChildNodes()) {
            addChildNodes(currentDomNode);
        }
    }


    private void moveNodeToNewParent(Node newParentNode, String sourceNodeName) {
        // Simplified implementation - in a real application one would
        // find and move the node to be moved by ID
        logger.info("Node '{}' would be moved to '{}'", sourceNodeName, newParentNode.getNodeName());

        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Move Node");
        info.setHeaderText(null);
        info.setContentText("Node '" + sourceNodeName + "' would be moved to '" + newParentNode.getNodeName() + "'.\n" +
                "(Full implementation requires node reference tracking)");
        info.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String getNodeTypeString(short nodeType) {
        return switch (nodeType) {
            case Node.ELEMENT_NODE -> "ELEMENT";
            case Node.TEXT_NODE -> "TEXT";
            case Node.COMMENT_NODE -> "COMMENT";
            case Node.ATTRIBUTE_NODE -> "ATTRIBUTE";
            case Node.DOCUMENT_NODE -> "DOCUMENT";
            default -> "OTHER(" + nodeType + ")";
        };
    }

    private javafx.scene.Node createIcon(String iconType) {
        return switch (iconType) {
            case "ADD_CHILD" -> createAddChildIcon();
            case "ADD_AFTER" -> createAddAfterIcon();
            case "ADD_BEFORE" -> createAddBeforeIcon();
            case "DELETE" -> createDeleteIcon();
            default -> createDefaultIcon();
        };
    }

    private javafx.scene.Node createAddChildIcon() {
        // Plus icon with downward arrow
        Group group = new Group();

        // Plus sign
        Rectangle hLine = new Rectangle(10, 2);
        hLine.setFill(Color.DARKGREEN);
        hLine.setX(3);
        hLine.setY(7);

        Rectangle vLine = new Rectangle(2, 10);
        vLine.setFill(Color.DARKGREEN);
        vLine.setX(7);
        vLine.setY(3);

        // Small arrow pointing down
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(8.0, 14.0,  // top point
                6.0, 16.0,  // left point
                10.0, 16.0  // right point
        );
        arrow.setFill(Color.DARKGREEN);

        group.getChildren().addAll(hLine, vLine, arrow);
        return group;
    }

    private javafx.scene.Node createAddAfterIcon() {
        // Plus with right arrow
        Group group = new Group();

        Rectangle hLine = new Rectangle(8, 2);
        hLine.setFill(Color.DARKBLUE);
        hLine.setX(2);
        hLine.setY(7);

        Rectangle vLine = new Rectangle(2, 8);
        vLine.setFill(Color.DARKBLUE);
        vLine.setX(5);
        vLine.setY(4);

        // Arrow pointing right
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(11.0, 8.0,  // left point
                14.0, 6.0,  // top point
                14.0, 10.0  // bottom point
        );
        arrow.setFill(Color.DARKBLUE);

        group.getChildren().addAll(hLine, vLine, arrow);
        return group;
    }

    private javafx.scene.Node createAddBeforeIcon() {
        // Plus with left arrow
        Group group = new Group();

        Rectangle hLine = new Rectangle(8, 2);
        hLine.setFill(Color.DARKBLUE);
        hLine.setX(6);
        hLine.setY(7);

        Rectangle vLine = new Rectangle(2, 8);
        vLine.setFill(Color.DARKBLUE);
        vLine.setX(9);
        vLine.setY(4);

        // Arrow pointing left
        Polygon arrow = new Polygon();
        arrow.getPoints().addAll(5.0, 8.0,   // right point
                2.0, 6.0,   // top point
                2.0, 10.0   // bottom point
        );
        arrow.setFill(Color.DARKBLUE);

        group.getChildren().addAll(hLine, vLine, arrow);
        return group;
    }

    private javafx.scene.Node createDeleteIcon() {
        // X icon
        Group group = new Group();

        // First diagonal line (top-left to bottom-right)
        Line line1 = new Line(3, 3, 13, 13);
        line1.setStroke(Color.DARKRED);
        line1.setStrokeWidth(2);

        // Second diagonal line (top-right to bottom-left)
        Line line2 = new Line(13, 3, 3, 13);
        line2.setStroke(Color.DARKRED);
        line2.setStrokeWidth(2);

        group.getChildren().addAll(line1, line2);
        return group;
    }

    private javafx.scene.Node createDefaultIcon() {
        // Simple circle
        Circle circle = new Circle(8, 8, 6);
        circle.setFill(Color.LIGHTGRAY);
        circle.setStroke(Color.GRAY);
        return circle;
    }

    /**
     * Intelligently updates the UI after adding a new node,
     * instead of rebuilding the entire UI.
     */
    private void updateUIAfterNodeAddition(Node parentNode, Element newElement) {
        // Aktualisiere die Textansicht des Editors
        this.xmlEditor.refreshTextViewFromDom();

        // Finde das UI-Element für den Parent-Knoten und füge das neue Element hinzu
        addNewNodeToUI(parentNode, newElement);
    }

    /**
     * Fügt einen neuen Knoten zur UI hinzu, ohne die gesamte UI neu aufzubauen.
     */
    private void addNewNodeToUI(Node parentNode, Element newElement) {
        // Durchlaufe alle UI-Elemente und finde das entsprechende Parent-Element
        for (javafx.scene.Node child : this.getChildren()) {
            if (child instanceof GridPane gridPane) {
                // Prüfe, ob dieses GridPane zum Parent-Knoten gehört
                if (isGridPaneForNode(gridPane, parentNode)) {
                    // Füge das neue Element zur UI hinzu
                    addNewElementToGridPane(gridPane, newElement);
                    return;
                }
            } else if (child instanceof VBox vbox) {
                // Rekursiv durch komplexe Knoten gehen
                if (addNewNodeToVBox(vbox, parentNode, newElement)) {
                    return;
                }
            }
        }

        // Falls wir das Parent-Element nicht finden, machen wir eine vollständige Neuerstellung
        logger.warn("Could not find parent UI element for node: {}. Performing full refresh.", parentNode.getNodeName());
        refreshWholeView();
    }

    private boolean isGridPaneForNode(GridPane gridPane, Node node) {
        // Diese Methode prüft, ob ein GridPane zu einem bestimmten DOM-Knoten gehört
        // Das ist komplex, da wir keine direkte Referenz haben
        // Für jetzt verwenden wir eine einfache Heuristik

        // Prüfe, ob das GridPane ein Label mit dem Knotennamen enthält
        for (javafx.scene.Node gridChild : gridPane.getChildren()) {
            if (gridChild instanceof HBox hbox) {
                for (javafx.scene.Node hboxChild : hbox.getChildren()) {
                    if (hboxChild instanceof Label label) {
                        if (hbox.getStyleClass().contains("node-name-box") &&
                                label.getText().equals(node.getNodeName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void addNewElementToGridPane(GridPane gridPane, Element newElement) {
        // Erstelle ein neues UI-Element für den neuen Knoten
        var nodeName = new Label(newElement.getNodeName());
        var nodeValue = new Label(""); // Leerer Text für neue Elemente

        // XMLSpy-inspired styling for new elements
        nodeName.setStyle(
                "-fx-text-fill: #2c5aa0; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        nodeValue.setStyle(
                "-fx-text-fill: #000000; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        nodeName.setWrapText(false);
        nodeValue.setWrapText(true);

        nodeName.setTooltip(new Tooltip(newElement.getNodeName()));
        nodeValue.setTooltip(new Tooltip(""));

        // WICHTIG: Event-Handler für das neue Label setzen
        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, newElement));

        var nodeNameBox = new HBox(nodeName);
        nodeNameBox.getStyleClass().add("node-name-box");
        nodeNameBox.setStyle(
                "-fx-background-color: linear-gradient(to right, #ffffff, #f0f8ff); " +
                        "-fx-padding: 4px 8px; " +
                        "-fx-border-color: #e0e8ff; " +
                        "-fx-border-width: 0 1px 0 0;"
        );

        var nodeValueBox = new HBox(nodeValue);
        nodeValueBox.getStyleClass().add("node-value-box");
        nodeValueBox.setStyle(
                "-fx-background-color: #ffffff; " +
                        "-fx-padding: 4px 8px;"
        );

        // Füge das neue Element zur nächsten Zeile hinzu
        int nextRow = gridPane.getRowCount();
        gridPane.add(nodeNameBox, 0, nextRow);
        gridPane.add(nodeValueBox, 1, nextRow);

        // Kontextmenü für das neue Element hinzufügen
        setupContextMenu(gridPane, newElement);

        logger.debug("Added new element '{}' to UI at row {}", newElement.getNodeName(), nextRow);
    }

    private boolean addNewNodeToVBox(VBox vbox, Node parentNode, Element newElement) {
        // Durchlaufe alle Kinder der VBox
        for (javafx.scene.Node vboxChild : vbox.getChildren()) {
            if (vboxChild instanceof GridPane gridPane) {
                if (isGridPaneForNode(gridPane, parentNode)) {
                    addNewElementToGridPane(gridPane, newElement);
                    return true;
                }
            } else if (vboxChild instanceof VBox childVbox) {
                if (addNewNodeToVBox(childVbox, parentNode, newElement)) {
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * Selects a node and updates sidebar information
     */
    private void selectNode(javafx.scene.Node uiNode, Node domNode) {
        logger.info("🎯 XmlGraphicEditor: Selecting node - {}", domNode.getNodeName());

        // Remove selection from previous node
        if (currentSelectedUINode != null) {
            String style = currentSelectedUINode.getStyle();
            if (style.contains("-fx-background-color: rgba(74, 144, 226, 0.2);")) {
                currentSelectedUINode.setStyle(style.replace("-fx-background-color: rgba(74, 144, 226, 0.2);", ""));
            }
        }

        // Set new selection
        currentSelectedUINode = uiNode;
        currentSelectedDomNode = domNode;

        // Add selection visual feedback
        uiNode.setStyle(uiNode.getStyle() + "-fx-background-color: rgba(74, 144, 226, 0.2);");

        // Update sidebar with node information
        logger.info("📊 XmlGraphicEditor: About to update sidebar for node: {}", domNode.getNodeName());
        updateSidebarInformation(domNode);

        logger.debug("✅ XmlGraphicEditor: Selected node - {} (Type: {})", domNode.getNodeName(), domNode.getNodeType());
    }

    /**
     * Updates the sidebar with information about the selected DOM node
     */
    private void updateSidebarInformation(Node domNode) {
        logger.info("🔄 XmlGraphicEditor: updateSidebarInformation called for: {}", domNode.getNodeName());

        if (true) { // Functionality moved to Ultimate XML Editor
            logger.error("❌ XmlGraphicEditor: Sidebar functionality moved to Ultimate XML Editor.");
            return;
        }

        try {
            // Build XPath for the selected node
            String xpath = buildXPathForNode(domNode);
            logger.info("📍 XmlGraphicEditor: Built XPath: {}", xpath);

            // Functionality moved to Ultimate XML Editor
            logger.info("✅ XmlGraphicEditor: XPath set in sidebar: {}", xpath);

            // Prefer XSD-backed info if available
            XsdExtendedElement xsdInfo = xmlEditor.findBestMatchingElement(xpath);
            logger.info("🔍 XmlGraphicEditor: XSD info found: {}", xsdInfo != null);

            if (xsdInfo != null) {
                logger.info("📚 XmlGraphicEditor: Using XSD-backed information for element: {}", xsdInfo.getElementName());
                // Functionality moved to Ultimate XML Editor
                // Documentation and examples
                String documentation = xmlEditor.getDocumentationFromExtendedElement(xsdInfo);
                // Functionality moved to Ultimate XML Editor

                // Possible child elements from XSD map
                java.util.List<String> childElements = new java.util.ArrayList<>();
                if (xsdInfo.getChildren() != null) {
                    for (String childXpath : xsdInfo.getChildren()) {
                        XsdExtendedElement child = xmlEditor.getXsdDocumentationData() != null
                                ? xmlEditor.getXsdDocumentationData().getExtendedXsdElementMap().get(childXpath)
                                : null;
                        if (child != null && child.getElementName() != null) {
                            if (!childElements.contains(child.getElementName())) {
                                childElements.add(child.getElementName());
                            }
                        }
                    }
                }
                if (childElements.isEmpty()) {
                    childElements = getChildElementNames(domNode);
                }
                // Functionality moved to Ultimate XML Editor

            } else if (domNode.getNodeType() == Node.ATTRIBUTE_NODE) {
                // Functionality moved to Ultimate XML Editor
            } else {
                // Fallback: DOM-derived info
                logger.info("📝 XmlGraphicEditor: Using DOM-based fallback information");
                if (domNode.getNodeType() == Node.ELEMENT_NODE) {
                    String elementName = domNode.getNodeName();
                    String elementType = getElementType(domNode);
                    java.util.List<String> childElements = getChildElementNames(domNode);

                    logger.info("   Element Name: {}", elementName);
                    logger.info("   Element Type: {}", elementType);
                    logger.info("   Child Elements: {}", childElements);

                    // Functionality moved to Ultimate XML Editor
                } else {
                    logger.info("   Non-element node: {} (Type: {})", domNode.getNodeName(), domNode.getNodeType());
                    // Functionality moved to Ultimate XML Editor
                }
            }

        } catch (Exception e) {
            logger.error("Error updating sidebar information for node: {}", domNode.getNodeName(), e);
        }
    }

    /**
     * Builds an XPath string for the given DOM node
     */
    private String buildXPathForNode(Node node) {
        if (node == null) {
            logger.warn("⚠️  XmlGraphicEditor: buildXPathForNode called with null node");
            return "";
        }

        logger.debug("🔨 XmlGraphicEditor: Building XPath for node: {}", node.getNodeName());

        java.util.List<String> pathElements = new java.util.ArrayList<>();
        Node current = node;

        while (current != null && current.getNodeType() != Node.DOCUMENT_NODE) {
            if (current.getNodeType() == Node.ELEMENT_NODE) {
                logger.debug("  📂 Adding to path: {}", current.getNodeName());
                pathElements.add(0, current.getNodeName());
            }
            current = current.getParentNode();
        }

        String xpath = "/" + String.join("/", pathElements);
        logger.info("🗺️  XmlGraphicEditor: Built XPath: {}", xpath);
        return xpath;
    }

    /**
     * Determines the element type based on DOM node content
     */
    private String getElementType(Node node) {
        if (node.getChildNodes().getLength() == 0) {
            return "empty";
        } else if (node.getChildNodes().getLength() == 1 &&
                node.getChildNodes().item(0).getNodeType() == Node.TEXT_NODE) {
            return "text";
        } else {
            return "complex";
        }
    }

    /**
     * Gets the names of child elements for the given node
     */
    private java.util.List<String> getChildElementNames(Node node) {
        java.util.List<String> childElements = new java.util.ArrayList<>();

        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node child = node.getChildNodes().item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String childName = child.getNodeName();
                if (!childElements.contains(childName)) {
                    childElements.add(childName);
                }
            }
        }

        if (childElements.isEmpty()) {
            childElements.add("No child elements");
        }

        return childElements;
    }

    /**
     * Navigates to a specific node in the graphic view based on XPath or line number
     * This can be used for validation error navigation
     */
    public boolean navigateToNode(String xpath, int lineNumber) {
        try {
            // First try to find node by XPath if available
            if (xpath != null && !xpath.isEmpty() && !xpath.equals("Unknown")) {
                Node targetNode = findNodeByXPath(xpath);
                if (targetNode != null) {
                    javafx.scene.Node uiNode = findUINodeForDomNode(targetNode);
                    if (uiNode != null) {
                        selectNode(uiNode, targetNode);
                        scrollToNode(uiNode);
                        return true;
                    }
                }
            }

            // Fallback: try to find node by approximate line number
            // This is less accurate but better than nothing
            if (lineNumber > 0) {
                Node approximateNode = findNodeByApproximatePosition(lineNumber);
                if (approximateNode != null) {
                    javafx.scene.Node uiNode = findUINodeForDomNode(approximateNode);
                    if (uiNode != null) {
                        selectNode(uiNode, approximateNode);
                        scrollToNode(uiNode);
                        return true;
                    }
                }
            }

            logger.debug("Could not navigate to node with xpath: {} and line: {}", xpath, lineNumber);
            return false;
        } catch (Exception e) {
            logger.error("Error navigating to node: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Finds a DOM node by XPath (simplified implementation)
     */
    private Node findNodeByXPath(String xpath) {
        // This is a simplified XPath implementation
        // For a complete solution, you would use XPath evaluation
        String[] pathParts = xpath.split("/");
        if (pathParts.length == 0) return null;

        Node current = currentDomNode;

        // Skip empty first element from leading slash
        for (int i = (pathParts[0].isEmpty() ? 1 : 0); i < pathParts.length; i++) {
            String elementName = pathParts[i];
            if (elementName.isEmpty()) continue;

            Node found = null;
            for (int j = 0; j < current.getChildNodes().getLength(); j++) {
                Node child = current.getChildNodes().item(j);
                if (child.getNodeType() == Node.ELEMENT_NODE &&
                        child.getNodeName().equals(elementName)) {
                    found = child;
                    break;
                }
            }

            if (found == null) return null;
            current = found;
        }

        return current;
    }

    /**
     * Finds a UI node that corresponds to a DOM node
     */
    private javafx.scene.Node findUINodeForDomNode(Node targetDomNode) {
        // This is a simplified implementation
        // In a complete solution, you would maintain a mapping between DOM and UI nodes
        return findUINodeRecursively(this, targetDomNode);
    }

    /**
     * Recursively searches for UI node corresponding to DOM node
     */
    private javafx.scene.Node findUINodeRecursively(javafx.scene.Parent parent, Node targetDomNode) {
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            // Check if this child corresponds to our target DOM node
            // This is a heuristic approach - in a complete implementation you'd have proper mapping
            if (child instanceof GridPane || child instanceof VBox) {
                if (nodeCorrespondsToDOM(child, targetDomNode)) {
                    return child;
                }
            }

            // Recursively search in children
            if (child instanceof javafx.scene.Parent childParent) {
                javafx.scene.Node result = findUINodeRecursively(childParent, targetDomNode);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * Checks if a UI node corresponds to a DOM node (heuristic)
     */
    private boolean nodeCorrespondsToDOM(javafx.scene.Node uiNode, Node domNode) {
        // This is a heuristic approach
        // Look for labels in the UI node that match the DOM node name
        if (uiNode instanceof GridPane gridPane) {
            for (javafx.scene.Node child : gridPane.getChildren()) {
                if (child instanceof HBox hbox) {
                    for (javafx.scene.Node hboxChild : hbox.getChildren()) {
                        if (hboxChild instanceof Label label) {
                            if (label.getText().equals(domNode.getNodeName())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Finds a node by approximate position (line number based)
     */
    private Node findNodeByApproximatePosition(int lineNumber) {
        // This is a very approximate method
        // Count nodes and estimate position
        java.util.List<Node> allElements = new java.util.ArrayList<>();
        collectAllElements(currentDomNode, allElements);

        if (!allElements.isEmpty()) {
            // Use line number as rough approximation of element position
            int approximateIndex = Math.min(lineNumber - 1, allElements.size() - 1);
            approximateIndex = Math.max(0, approximateIndex);
            return allElements.get(approximateIndex);
        }

        return null;
    }

    /**
     * Collects all element nodes recursively
     */
    private void collectAllElements(Node node, java.util.List<Node> elements) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            elements.add(node);
        }

        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            collectAllElements(node.getChildNodes().item(i), elements);
        }
    }

    /**
     * Scrolls to make the specified UI node visible
     */
    private void scrollToNode(javafx.scene.Node uiNode) {
        // Request focus and try to scroll to the node
        uiNode.requestFocus();

        // If this XmlGraphicEditor is in a ScrollPane, scroll to the node
        javafx.scene.Parent parent = this.getParent();
        while (parent != null) {
            if (parent instanceof ScrollPane scrollPane) {
                // Calculate the relative position of the node
                double nodeY = uiNode.getBoundsInParent().getMinY();
                double scrollPaneHeight = scrollPane.getHeight();
                double contentHeight = scrollPane.getContent().getBoundsInLocal().getHeight();

                if (contentHeight > scrollPaneHeight) {
                    double scrollPosition = nodeY / (contentHeight - scrollPaneHeight);
                    scrollPosition = Math.max(0, Math.min(1, scrollPosition));
                    scrollPane.setVvalue(scrollPosition);
                }
                break;
            }
            parent = parent.getParent();
        }
    }
}