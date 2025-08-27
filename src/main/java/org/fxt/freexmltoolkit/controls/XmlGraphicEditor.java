package org.fxt.freexmltoolkit.controls;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
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
import org.fxt.freexmltoolkit.controller.controls.XmlEditorSidebarController;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

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
    private XmlEditorSidebarController sidebarController;
    private javafx.scene.Node currentSelectedUINode;
    private Node currentSelectedDomNode;

    // Map to store direct UI Node -> DOM Node associations
    private final Map<javafx.scene.Node, Node> uiNodeToDomNodeMap = new LinkedHashMap<>();

    // Drag and drop state tracking
    private Node draggedDomNode;
    private javafx.scene.Node draggedUINode;
    private javafx.scene.Node currentDropTarget;
    private DropPosition currentDropPosition;

    // Drop position indicator
    private javafx.scene.Node dropPositionIndicator;

    /**
     * Enum for drop position relative to target element
     */
    public enum DropPosition {
        BEFORE,     // Insert before the target element (same parent)
        AFTER,      // Insert after the target element (same parent)  
        INSIDE      // Insert as child of target element
    }

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
     * Sets the sidebar controller for integration with XmlEditorSidebar functionality
     */
    public void setSidebarController(XmlEditorSidebarController sidebarController) {
        this.sidebarController = sidebarController;
        logger.info("🔗 XmlGraphicEditor: Sidebar controller set: {}", sidebarController != null ? "SUCCESS" : "NULL");

        // If sidebar controller is now available, setup node selection for all existing nodes
        if (sidebarController != null) {
            setupNodeSelectionForAllNodes();
        }
    }

    /**
     * Sets up node selection for all existing UI nodes - called after sidebar controller is set
     */
    private void setupNodeSelectionForAllNodes() {
        logger.info("🔄 XmlGraphicEditor: Setting up node selection for {} mapped nodes", uiNodeToDomNodeMap.size());

        // Use the direct mapping instead of complex heuristics
        for (Map.Entry<javafx.scene.Node, Node> entry : uiNodeToDomNodeMap.entrySet()) {
            javafx.scene.Node uiNode = entry.getKey();
            Node domNode = entry.getValue();

            logger.debug("🔗 Setting up selection for mapped: {} -> DOM: {}",
                    uiNode.getClass().getSimpleName(), domNode.getNodeName());

            setupNodeSelectionForSpecificNode(uiNode, domNode);
        }
    }


    /**
     * Sets up node selection for a specific UI node - DOM node pair
     */
    private void setupNodeSelectionForSpecificNode(javafx.scene.Node uiNode, Node domNode) {
        if (sidebarController == null) {
            logger.warn("⚠️  XmlGraphicEditor: Cannot setup node selection - sidebarController is still null!");
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
        if (sidebarController == null) {
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

        // Set up drag and drop for text nodes too
        setupDragAndDropForGridPane(gridPane, subNode);

        // Store the mapping between UI element and DOM node
        uiNodeToDomNodeMap.put(gridPane, subNode);
        logger.debug("🗂️ Mapped GridPane -> DOM node: {}", subNode.getNodeName());

        // Set up node selection immediately if sidebar controller is available
        if (sidebarController != null) {
            // Use text cell selection for text nodes to preserve double-click editing
            setupNodeSelectionForTextCell(gridPane, subNode);
            logger.debug("✅ Set up text cell selection for text GridPane: {}", subNode.getNodeName());
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
                        if (sidebarController != null) {
                            nestedEditor.setSidebarController(sidebarController);
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
        if (sidebarController != null) {
            setupNodeSelectionForSpecificNode(elementContainer, subNode);
            logger.debug("✅ Set up node selection for complex element VBox: {}", subNode.getNodeName());
        }

        this.getChildren().add(elementContainer);
    }

    @NotNull
    private EventHandler<MouseEvent> editNodeValueHandler(Label nodeValueLabel, Node domNode) {
        return event -> {
            if (event.getClickCount() != 2) return; // Only edit on double click

            // Enhanced debugging for empty text nodes
            logger.debug("Double-click detected on node: {} (type: {}), label text: '{}'",
                    domNode.getNodeName(), domNode.getNodeType(), nodeValueLabel.getText());

            if (domNode.getNodeType() == Node.ELEMENT_NODE) {
                NodeList childNodes = domNode.getChildNodes();
                logger.debug("Element has {} child nodes", childNodes.getLength());
                for (int i = 0; i < childNodes.getLength(); i++) {
                    Node child = childNodes.item(i);
                    logger.debug("Child {}: type={}, value='{}'", i, child.getNodeType(), child.getNodeValue());
                }
            }

            // Get the parent - could be HBox or other Pane
            Parent labelParent = nodeValueLabel.getParent();
            if (labelParent == null) {
                logger.warn("Cannot edit node value, label has no parent.");
                return;
            }

            // Check if parent is a Pane (including HBox which extends Pane)
            if (!(labelParent instanceof Pane parent)) {
                logger.warn("Cannot edit node value, label's parent is not a Pane but a {}.",
                        labelParent.getClass().getSimpleName());
                return;
            }

            final String originalValue = nodeValueLabel.getText();
            TextField textField = new TextField(originalValue);

            // Log for debugging
            logger.debug("Starting edit for node: {} with value: '{}', parent type: {}",
                    domNode.getNodeName(), originalValue, parent.getClass().getSimpleName());

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

            // Replace the label with the text field in the parent container
            parent.getChildren().setAll(textField);
            textField.requestFocus();
            textField.selectAll();

            logger.debug("TextField activated for editing, parent children count: {}", parent.getChildren().size());
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
            if (sidebarController != null) {
                nestedEditor.setSidebarController(sidebarController);
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

        if (sidebarController != null) {
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
            logger.info("🔄 Drag detected for node: {}", domNode.getNodeName());

            // Store references to dragged elements
            draggedDomNode = domNode;
            draggedUINode = elementContainer;
            
            Dragboard db = elementContainer.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            // Use a unique identifier for the dragged node
            String dragData = domNode.getNodeName() + "||" + System.identityHashCode(domNode) + "||" + buildXPathForNode(domNode);
            content.putString(dragData);
            db.setContent(content);

            // Visual feedback for drag source
            elementContainer.setStyle(elementContainer.getStyle() + "-fx-opacity: 0.5;");

            event.consume();
        });

        // Reset visual feedback when drag ends
        elementContainer.setOnDragDone(event -> {
            logger.info("🏁 Drag done for node: {}", domNode.getNodeName());

            // Reset opacity
            String style = elementContainer.getStyle();
            if (style.contains("-fx-opacity: 0.5;")) {
                elementContainer.setStyle(style.replace("-fx-opacity: 0.5;", ""));
            }

            // Clear drag state
            draggedDomNode = null;
            draggedUINode = null;
            removeAllDropFeedback();
            currentDropTarget = null;
            currentDropPosition = null;
            
            event.consume();
        });

        // Set up drop target with visual feedback and position detection
        elementContainer.setOnDragOver(event -> {
            if (event.getGestureSource() != elementContainer && event.getDragboard().hasString()) {
                if (draggedDomNode != null) {
                    // Determine drop position based on mouse position
                    DropPosition position = determineDropPosition(elementContainer, event.getY(), domNode, draggedDomNode);

                    if (position != null) {
                        event.acceptTransferModes(TransferMode.MOVE);

                        // Update visual feedback if target or position changed
                        if (currentDropTarget != elementContainer || currentDropPosition != position) {
                            // Remove previous feedback
                            removeAllDropFeedback();

                            // Add new feedback based on position
                            addDropPositionFeedback(elementContainer, position);
                            currentDropTarget = elementContainer;
                            currentDropPosition = position;
                        }
                    }
                }
            }
            event.consume();
        });

        // Remove visual feedback when drag exits
        elementContainer.setOnDragExited(event -> {
            if (currentDropTarget == elementContainer) {
                removeAllDropFeedback();
                currentDropTarget = null;
                currentDropPosition = null;
            }
            event.consume();
        });

        elementContainer.setOnDragDropped(event -> {
            logger.info("📥 Drop detected on node: {} at position: {}", domNode.getNodeName(), currentDropPosition);
            
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasString() && draggedDomNode != null && currentDropPosition != null) {
                String[] data = db.getString().split("\\|\\|");
                if (data.length >= 2) {
                    try {
                        // Move the DOM node based on determined position
                        success = moveNodeWithPosition(draggedDomNode, domNode, currentDropPosition);

                        if (success) {
                            logger.info("✅ Successfully moved node {} {} {}",
                                    draggedDomNode.getNodeName(),
                                    currentDropPosition.toString().toLowerCase(),
                                    domNode.getNodeName());
                        }
                    } catch (Exception e) {
                        logger.error("❌ Error moving node", e);
                        showErrorDialog("Move failed", "Error moving node: " + e.getMessage());
                    }
                }
            }

            // Remove visual feedback
            removeAllDropFeedback();
            currentDropTarget = null;
            currentDropPosition = null;
            
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


    /**
     * Moves a DOM node with specific positioning
     *
     * @param nodeToMove The DOM node to be moved
     * @param targetNode The target DOM node (context for positioning)
     * @param position   The desired position (BEFORE, AFTER, or INSIDE)
     * @return true if the move was successful, false otherwise
     */
    private boolean moveNodeWithPosition(Node nodeToMove, Node targetNode, DropPosition position) {
        logger.info("🔄 Moving node '{}' {} '{}'",
                nodeToMove.getNodeName(), position.toString().toLowerCase(), targetNode.getNodeName());

        try {
            // Validate the move
            if (!isValidPositionMove(nodeToMove, targetNode, position)) {
                logger.warn("⚠️ Invalid position move");
                showErrorDialog("Invalid Move", "Cannot move node to this position.");
                return false;
            }

            // Get the current parent
            Node currentParent = nodeToMove.getParentNode();
            if (currentParent == null) {
                logger.warn("⚠️ Cannot move node without parent");
                return false;
            }

            // Remove from current parent
            currentParent.removeChild(nodeToMove);
            logger.debug("✂️ Removed node from current parent: {}", currentParent.getNodeName());

            // Insert based on position
            switch (position) {
                case BEFORE:
                    insertNodeBefore(nodeToMove, targetNode);
                    break;
                case AFTER:
                    insertNodeAfter(nodeToMove, targetNode);
                    break;
                case INSIDE:
                    targetNode.appendChild(nodeToMove);
                    break;
            }

            // Update the text view
            xmlEditor.refreshTextViewFromDom();

            // Refresh the entire graphical view to reflect the changes
            refreshWholeView();

            logger.info("✅ Successfully moved node '{}' {} '{}'",
                    nodeToMove.getNodeName(), position.toString().toLowerCase(), targetNode.getNodeName());
            return true;

        } catch (Exception e) {
            logger.error("❌ Error moving node '{}' {} '{}'",
                    nodeToMove.getNodeName(), position.toString().toLowerCase(), targetNode.getNodeName(), e);
            return false;
        }
    }

    /**
     * Inserts a node before the target node (same parent)
     */
    private void insertNodeBefore(Node nodeToMove, Node targetNode) {
        Node parentNode = targetNode.getParentNode();
        if (parentNode != null) {
            parentNode.insertBefore(nodeToMove, targetNode);
            logger.debug("⬅️ Inserted node before target");
        }
    }

    /**
     * Inserts a node after the target node (same parent)
     */
    private void insertNodeAfter(Node nodeToMove, Node targetNode) {
        Node parentNode = targetNode.getParentNode();
        if (parentNode != null) {
            Node nextSibling = targetNode.getNextSibling();
            if (nextSibling != null) {
                parentNode.insertBefore(nodeToMove, nextSibling);
                logger.debug("➡️ Inserted node after target (before next sibling)");
            } else {
                parentNode.appendChild(nodeToMove);
                logger.debug("➡️ Inserted node after target (as last child)");
            }
        }
    }

    /**
     * Moves a DOM node to a new parent node (legacy method for backward compatibility)
     *
     * @param nodeToMove    The DOM node to be moved
     * @param newParentNode The new parent DOM node
     * @return true if the move was successful, false otherwise
     */
    private boolean moveNodeToNewParent(Node nodeToMove, Node newParentNode) {
        // Use the new positioned move method with INSIDE position
        return moveNodeWithPosition(nodeToMove, newParentNode, DropPosition.INSIDE);
    }

    /**
     * Validates whether a move operation is valid
     */
    private boolean isValidMove(Node nodeToMove, Node newParent) {
        // Cannot move a node to itself
        if (nodeToMove == newParent) {
            return false;
        }

        // Cannot move a node to one of its descendants (would create a cycle)
        Node current = newParent;
        while (current != null) {
            if (current == nodeToMove) {
                return false;
            }
            current = current.getParentNode();
        }

        // Cannot move to a text-only parent (parents that have only text content)
        return !hasTextContent(newParent);
    }

    /**
     * Checks if a node is a valid drop target for the currently dragged node
     */
    private boolean isValidDropTarget(Node potentialParent, Node draggedNode) {
        if (draggedNode == null || potentialParent == null) {
            return false;
        }

        return isValidMove(draggedNode, potentialParent);
    }

    /**
     * Adds visual feedback for valid drop targets
     */
    private void addDropTargetFeedback(javafx.scene.Node uiNode) {
        uiNode.setStyle(uiNode.getStyle() + "-fx-border-color: #4CAF50; -fx-border-width: 2px; -fx-border-style: dashed;");
    }

    /**
     * Removes visual feedback from drop targets
     */
    private void removeDropTargetFeedback(javafx.scene.Node uiNode) {
        String style = uiNode.getStyle();
        style = style.replaceAll("-fx-border-color: #4CAF50;", "");
        style = style.replaceAll("-fx-border-width: 2px;", "");
        style = style.replaceAll("-fx-border-style: dashed;", "");
        uiNode.setStyle(style);
    }

    /**
     * Removes all drop feedback including position indicators
     */
    private void removeAllDropFeedback() {
        if (currentDropTarget != null) {
            removeDropTargetFeedback(currentDropTarget);
        }
        if (dropPositionIndicator != null && dropPositionIndicator.getParent() instanceof Pane parent) {
            parent.getChildren().remove(dropPositionIndicator);
            dropPositionIndicator = null;
        }
    }

    /**
     * Determines the drop position based on mouse coordinates and element bounds
     */
    private DropPosition determineDropPosition(javafx.scene.Node targetUINode, double mouseY, Node targetDomNode, Node draggedNode) {
        if (draggedNode == null || targetDomNode == null) {
            return null;
        }

        // Get the bounds of the target UI element
        var bounds = targetUINode.getBoundsInLocal();
        double elementHeight = bounds.getHeight();
        double relativeY = mouseY;

        // Calculate position zones
        double upperThird = elementHeight * 0.33;
        double lowerThird = elementHeight * 0.67;

        logger.debug("📍 Position detection - mouseY: {}, height: {}, zones: {}/{}",
                relativeY, elementHeight, upperThird, lowerThird);

        DropPosition position;

        if (relativeY < upperThird) {
            // Upper third - insert before
            position = DropPosition.BEFORE;
        } else if (relativeY > lowerThird) {
            // Lower third - insert after
            position = DropPosition.AFTER;
        } else {
            // Middle third - insert inside (only if target can have children)
            position = canHaveChildren(targetDomNode) ? DropPosition.INSIDE : null;
        }

        // Validate the position
        if (position != null && !isValidPositionMove(draggedNode, targetDomNode, position)) {
            return null;
        }

        logger.debug("✅ Determined position: {} for target: {}", position, targetDomNode.getNodeName());
        return position;
    }

    /**
     * Validates if a position-based move is allowed
     */
    private boolean isValidPositionMove(Node draggedNode, Node targetNode, DropPosition position) {
        if (draggedNode == targetNode) {
            return false; // Can't move to itself
        }

        switch (position) {
            case BEFORE:
            case AFTER:
                // For before/after, target node must have a parent (can't be root)
                Node targetParent = targetNode.getParentNode();
                if (targetParent == null) {
                    return false;
                }

                // Can't move to create invalid XML structure
                return !isDescendant(targetNode, draggedNode);

            case INSIDE:
                // For inside, use the existing validation logic
                return isValidMove(draggedNode, targetNode);

            default:
                return false;
        }
    }

    /**
     * Checks if potentialDescendant is a descendant of potentialAncestor
     */
    private boolean isDescendant(Node potentialAncestor, Node potentialDescendant) {
        Node current = potentialDescendant.getParentNode();
        while (current != null) {
            if (current == potentialAncestor) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    /**
     * Adds visual feedback for drop position
     */
    private void addDropPositionFeedback(javafx.scene.Node targetUINode, DropPosition position) {
        // First remove any existing feedback
        removeAllDropFeedback();

        switch (position) {
            case BEFORE:
                addDropTargetFeedback(targetUINode);
                createPositionIndicator(targetUINode, true); // true = before
                break;
            case AFTER:
                addDropTargetFeedback(targetUINode);
                createPositionIndicator(targetUINode, false); // false = after
                break;
            case INSIDE:
                // For inside, just use the regular drop target feedback
                addDropTargetFeedback(targetUINode);
                break;
        }
    }

    /**
     * Creates a visual indicator for insertion position
     */
    private void createPositionIndicator(javafx.scene.Node targetUINode, boolean before) {
        // Create a thin line to show insertion point
        javafx.scene.shape.Line indicator = new javafx.scene.shape.Line();
        indicator.setStroke(javafx.scene.paint.Color.ORANGE);
        indicator.setStrokeWidth(3);
        indicator.getStyleClass().add("drop-position-indicator");

        // Get parent container
        javafx.scene.Parent parent = targetUINode.getParent();
        if (parent instanceof Pane pane) {
            var bounds = targetUINode.getBoundsInParent();

            // Position the indicator line
            double y = before ? bounds.getMinY() - 2 : bounds.getMaxY() + 2;
            indicator.setStartX(bounds.getMinX());
            indicator.setEndX(bounds.getMaxX());
            indicator.setStartY(y);
            indicator.setEndY(y);

            pane.getChildren().add(indicator);
            dropPositionIndicator = indicator;

            logger.debug("📏 Created position indicator {} target at Y: {}", before ? "before" : "after", y);
        }
    }

    /**
     * Sets up drag and drop functionality for GridPane elements (text nodes)
     */
    private void setupDragAndDropForGridPane(GridPane gridPane, Node domNode) {
        // Set up drag source for GridPane (text nodes)
        gridPane.setOnDragDetected(event -> {
            logger.info("🔄 Drag detected for text node: {}", domNode.getNodeName());

            // Store references to dragged elements
            draggedDomNode = domNode;
            draggedUINode = gridPane;

            Dragboard db = gridPane.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            // Use a unique identifier for the dragged node
            String dragData = domNode.getNodeName() + "||" + System.identityHashCode(domNode) + "||" + buildXPathForNode(domNode);
            content.putString(dragData);
            db.setContent(content);

            // Visual feedback for drag source
            gridPane.setStyle(gridPane.getStyle() + "-fx-opacity: 0.5;");

            event.consume();
        });

        // Reset visual feedback when drag ends
        gridPane.setOnDragDone(event -> {
            logger.info("🏁 Drag done for text node: {}", domNode.getNodeName());

            // Reset opacity
            String style = gridPane.getStyle();
            if (style.contains("-fx-opacity: 0.5;")) {
                gridPane.setStyle(style.replace("-fx-opacity: 0.5;", ""));
            }

            // Clear drag state
            draggedDomNode = null;
            draggedUINode = null;
            removeAllDropFeedback();
            currentDropTarget = null;
            currentDropPosition = null;

            event.consume();
        });

        // Set up drop target with visual feedback and position detection
        gridPane.setOnDragOver(event -> {
            if (event.getGestureSource() != gridPane && event.getDragboard().hasString()) {
                if (draggedDomNode != null) {
                    // Determine drop position based on mouse position
                    DropPosition position = determineDropPosition(gridPane, event.getY(), domNode, draggedDomNode);

                    if (position != null) {
                        event.acceptTransferModes(TransferMode.MOVE);

                        // Update visual feedback if target or position changed
                        if (currentDropTarget != gridPane || currentDropPosition != position) {
                            // Remove previous feedback
                            removeAllDropFeedback();

                            // Add new feedback based on position
                            addDropPositionFeedback(gridPane, position);
                            currentDropTarget = gridPane;
                            currentDropPosition = position;
                        }
                    }
                }
            }
            event.consume();
        });

        // Remove visual feedback when drag exits
        gridPane.setOnDragExited(event -> {
            if (currentDropTarget == gridPane) {
                removeAllDropFeedback();
                currentDropTarget = null;
                currentDropPosition = null;
            }
            event.consume();
        });

        gridPane.setOnDragDropped(event -> {
            logger.info("📥 Drop detected on text node: {} at position: {}", domNode.getNodeName(), currentDropPosition);

            Dragboard db = event.getDragboard();
            boolean success = false;

            if (db.hasString() && draggedDomNode != null && currentDropPosition != null) {
                String[] data = db.getString().split("\\|\\|");
                if (data.length >= 2) {
                    try {
                        // Move the DOM node based on determined position
                        success = moveNodeWithPosition(draggedDomNode, domNode, currentDropPosition);

                        if (success) {
                            logger.info("✅ Successfully moved node {} {} text node {}",
                                    draggedDomNode.getNodeName(),
                                    currentDropPosition.toString().toLowerCase(),
                                    domNode.getNodeName());
                        }
                    } catch (Exception e) {
                        logger.error("❌ Error moving node to text node", e);
                        showErrorDialog("Move failed", "Error moving node: " + e.getMessage());
                    }
                }
            }

            // Remove visual feedback
            removeAllDropFeedback();
            currentDropTarget = null;
            currentDropPosition = null;

            event.setDropCompleted(success);
            event.consume();
        });
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
        // Debug: Check the structure of the new element
        NodeList childNodes = newElement.getChildNodes();
        logger.debug("Adding new element '{}' to grid pane, child count: {}", newElement.getNodeName(), childNodes.getLength());

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            logger.debug("Child {}: type={}, value='{}'", i, child.getNodeType(), child.getNodeValue());
        }

        // Check if this element should be treated as a simple text node
        boolean isSimpleTextElement = childNodes.getLength() == 1 &&
                childNodes.item(0).getNodeType() == Node.TEXT_NODE;

        logger.debug("Element '{}' is simple text element: {}", newElement.getNodeName(), isSimpleTextElement);

        if (isSimpleTextElement) {
            // Use the same logic as addTextNode for consistency
            addTextNodeToGridPane(gridPane, newElement);
        } else {
            // Fallback to the original implementation
            addComplexElementToGridPane(gridPane, newElement);
        }
    }

    private void addTextNodeToGridPane(GridPane gridPane, Element element) {
        Node textNode = element.getChildNodes().item(0);
        String textValue = textNode.getNodeValue();
        logger.debug("Adding text element '{}' with value: '{}'", element.getNodeName(), textValue);

        var nodeName = new Label(element.getNodeName());
        var nodeValue = new Label(textValue != null ? textValue : "");

        // XMLSpy-inspired styling (same as addTextNode)
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

        nodeName.setTooltip(new Tooltip(element.getNodeName()));
        nodeValue.setTooltip(new Tooltip(textValue != null && !textValue.isEmpty() ? textValue : "Double-click to edit"));

        // CRUCIAL: Use the same approach as addTextNode - pass the element node
        // The editNodeValueHandler expects an Element node for simple text elements
        nodeValue.setOnMouseClicked(editNodeValueHandler(nodeValue, element));

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
        setupContextMenu(gridPane, element);

        // Store mapping and set up node selection for the new text element
        uiNodeToDomNodeMap.put(gridPane, element);
        logger.debug("🗂️ Mapped new GridPane -> DOM node: {}", element.getNodeName());

        // Set up text cell selection for new text nodes to preserve double-click editing
        if (sidebarController != null) {
            setupNodeSelectionForTextCell(gridPane, element);
            logger.debug("✅ Set up text cell selection for new text element: {}", element.getNodeName());
        }

        logger.debug("Added text element '{}' to UI at row {}", element.getNodeName(), nextRow);
    }

    private void addComplexElementToGridPane(GridPane gridPane, Element element) {
        // Fallback to original implementation for complex elements
        var nodeName = new Label(element.getNodeName());
        var nodeValue = new Label(""); // Complex elements don't have direct text content

        // XMLSpy-inspired styling for complex elements
        nodeName.setStyle(
                "-fx-text-fill: #2c5aa0; " +
                        "-fx-font-weight: bold; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        nodeValue.setStyle(
                "-fx-text-fill: #666666; " +
                        "-fx-font-size: 11px; " +
                        "-fx-font-style: italic; " +
                        "-fx-font-family: 'Segoe UI', Arial, sans-serif;"
        );

        nodeName.setWrapText(false);
        nodeValue.setWrapText(true);

        nodeName.setTooltip(new Tooltip(element.getNodeName()));
        nodeValue.setTooltip(new Tooltip("Complex element - not directly editable"));

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
                "-fx-background-color: #f8f8f8; " +
                        "-fx-padding: 4px 8px;"
        );

        // Füge das neue Element zur nächsten Zeile hinzu
        int nextRow = gridPane.getRowCount();
        gridPane.add(nodeNameBox, 0, nextRow);
        gridPane.add(nodeValueBox, 1, nextRow);

        // Kontextmenü für das neue Element hinzufügen
        setupContextMenu(gridPane, element);

        // Store mapping and set up node selection for the new complex element
        uiNodeToDomNodeMap.put(gridPane, element);
        logger.debug("🗂️ Mapped new complex GridPane -> DOM node: {}", element.getNodeName());

        // Set up normal node selection for complex elements
        if (sidebarController != null) {
            setupNodeSelectionForSpecificNode(gridPane, element);
            logger.debug("✅ Set up node selection for new complex element: {}", element.getNodeName());
        }

        logger.debug("Added complex element '{}' to UI at row {}", element.getNodeName(), nextRow);
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

        if (sidebarController == null) {
            logger.error("❌ XmlGraphicEditor: sidebarController is null! Cannot update sidebar.");
            return;
        }

        try {
            // Build XPath for the selected node
            String xpath = buildXPathForNode(domNode);
            logger.info("📍 XmlGraphicEditor: Built XPath: {}", xpath);

            sidebarController.setXPath(xpath);
            logger.info("✅ XmlGraphicEditor: XPath set in sidebar: {}", xpath);

            // Prefer XSD-backed info if available
            XsdExtendedElement xsdInfo = xmlEditor.findBestMatchingElement(xpath);
            logger.info("🔍 XmlGraphicEditor: XSD info found: {}", xsdInfo != null);

            if (xsdInfo != null) {
                logger.info("📚 XmlGraphicEditor: Using XSD-backed information for element: {}", xsdInfo.getElementName());
                sidebarController.setElementName(xsdInfo.getElementName());
                sidebarController.setElementType(xsdInfo.getElementType() != null ? xsdInfo.getElementType() : "");
                // Documentation and examples
                String documentation = xmlEditor.getDocumentationFromExtendedElement(xsdInfo);
                sidebarController.setDocumentation(documentation != null ? documentation : "");
                sidebarController.setExampleValues(xsdInfo.getExampleValues());

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
                sidebarController.setPossibleChildElements(childElements);

            } else if (domNode.getNodeType() == Node.ATTRIBUTE_NODE) {
                sidebarController.setElementName("@" + domNode.getNodeName());
                sidebarController.setElementType("attribute");
                sidebarController.setDocumentation("Attribute: " + domNode.getNodeName() + " = " + domNode.getNodeValue());
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

                    sidebarController.setElementName(elementName);
                    sidebarController.setElementType(elementType);
                    sidebarController.setDocumentation("Element: " + elementName);
                    sidebarController.setPossibleChildElements(childElements);
                } else {
                    logger.info("   Non-element node: {} (Type: {})", domNode.getNodeName(), domNode.getNodeType());
                    sidebarController.setElementName(domNode.getNodeName());
                    sidebarController.setElementType(getNodeTypeString(domNode.getNodeType()));
                    sidebarController.setDocumentation("Node: " + domNode.getNodeName());
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