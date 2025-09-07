package org.fxt.freexmltoolkit.controls;

import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controller.controls.XmlEditorSidebarController;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.javafx.FontIcon;
import org.w3c.dom.*;

import java.util.*;
import java.util.stream.IntStream;

/**
 * A custom VBox implementation for displaying XML nodes in a tree structure.
 * Styles are managed via an external CSS file.
 * This version is refactored for a more modern look and better performance.
 *
 * SEARCH FUNCTIONALITY:
 * - Comprehensive search through all XML content: node names, attributes, text content, and comments
 * - Automatic expansion of collapsed nodes to reveal search results
 * - Visual highlighting of found elements with navigation between results
 * - Keyboard shortcuts: Ctrl+F (open search), Enter/Shift+Enter (navigate), Escape (close)
 *
 * Usage:
 * 1. Call integrateSearchWithContainer(parentContainer) to add search bar to UI
 * 2. Users can press Ctrl+F to open search and type their search terms
 * 3. Navigation through results with Enter (next) and Shift+Enter (previous)
 * 4. Found nodes are automatically expanded and visually highlighted
 *
 * Search covers:
 * - Element names (e.g., searching "Asset" finds all <Asset> elements)
 * - Attribute names and values (e.g., searching "type" finds type="..." attributes)
 * - Text content within elements (e.g., searching "John" finds <name>John</name>)
 * - XML comments (e.g., searching "TODO" finds <!-- TODO: ... --> comments)
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

    // Search functionality state tracking
    private String currentSearchTerm = "";
    private List<SearchResult> searchResults = new ArrayList<>();
    private int currentSearchIndex = -1;
    private final Set<javafx.scene.Node> highlightedNodes = new HashSet<>();
    private final Map<Node, Button> nodeToggleButtonMap = new LinkedHashMap<>();
    private HBox searchBar;

    /**
     * Enum for drop position relative to target element
     */
    public enum DropPosition {
        BEFORE,     // Insert before the target element (same parent)
        AFTER,      // Insert after the target element (same parent)  
        INSIDE      // Insert as child of target element
    }

    /**
         * Represents a search result with all necessary information for highlighting and navigation
         */
        private record SearchResult(Node domNode, javafx.scene.Node uiNode, SearchType type, String matchedText,
                                    List<Node> pathToRoot, String fullText) {
            private SearchResult(Node domNode, javafx.scene.Node uiNode, SearchType type, String matchedText, List<Node> pathToRoot, String fullText) {
                this.domNode = domNode;
                this.uiNode = uiNode;
                this.type = type;
                this.matchedText = matchedText;
                this.pathToRoot = pathToRoot != null ? new ArrayList<>(pathToRoot) : new ArrayList<>();
                this.fullText = fullText;
            }

            @Override
            public String toString() {
                return String.format("%s: %s (in %s)", type.getDisplayName(), matchedText,
                        domNode != null ? domNode.getNodeName() : "unknown");
            }
        }

    /**
     * Types of search matches
     */
    public enum SearchType {
        NODE_NAME("Element Name"),
        ATTRIBUTE_NAME("Attribute Name"),
        ATTRIBUTE_VALUE("Attribute Value"),
        TEXT_CONTENT("Text Content"),
        COMMENT("Comment");

        private final String displayName;

        SearchType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Information about search results
     */
    public static class SearchResultInfo {
        private final int totalResults;
        private final int currentIndex;
        private final String searchTerm;
        private final boolean hasResults;

        public SearchResultInfo(int totalResults, int currentIndex, String searchTerm) {
            this.totalResults = totalResults;
            this.currentIndex = currentIndex;
            this.searchTerm = searchTerm;
            this.hasResults = totalResults > 0;
        }

        public int getTotalResults() {
            return totalResults;
        }

        public int getCurrentIndex() {
            return currentIndex;
        }

        public String getSearchTerm() {
            return searchTerm;
        }

        public boolean hasResults() {
            return hasResults;
        }

        @Override
        public String toString() {
            return hasResults ? String.format("%d of %d", currentIndex + 1, totalResults) : "No results";
        }
    }

    public XmlGraphicEditor(Node node, XmlEditor caller) {
        this.xmlEditor = caller;
        this.currentDomNode = node;
        // Assign a CSS class to the root element for targeted styling.
        this.getStyleClass().add("simple-node-element");

        if (node.hasChildNodes()) {
            addChildNodes(node);
        }

        // Set up keyboard shortcuts for search functionality
        setupSearchKeyboardShortcuts();

        // Make this node focusable so it can receive key events
        this.setFocusTraversable(true);

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

        // Store toggle button reference for search functionality
        nodeToggleButtonMap.put(subNode, toggleButton);
        logger.debug("🔘 Stored toggle button reference for node: {}", subNode.getNodeName());

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

        // Navigation actions (only for elements)
        if (domNode.getNodeType() == Node.ELEMENT_NODE) {
            MenuItem goToDefinitionMenuItem = new MenuItem("Go to XSD Definition");
            goToDefinitionMenuItem.setGraphic(new FontIcon("bi-box-arrow-up-right"));
            goToDefinitionMenuItem.setOnAction(e -> navigateToXsdDefinitionFromGraphicEditor(domNode));
            contextMenu.getItems().add(goToDefinitionMenuItem);
            contextMenu.getItems().add(new SeparatorMenuItem());
        }

        // Only show "Add Child" if the node can have child elements
        if (canHaveChildren(domNode)) {
            MenuItem addChildMenuItem = new MenuItem("Add Child to: " + domNode.getNodeName());
            addChildMenuItem.setGraphic(new FontIcon("bi-plus-circle"));
            addChildMenuItem.setOnAction(e -> addChildNodeToSpecificParent(domNode));
            contextMenu.getItems().add(addChildMenuItem);
        }

        MenuItem addSiblingAfterMenuItem = new MenuItem("Add Sibling After");
        addSiblingAfterMenuItem.setGraphic(new FontIcon("bi-arrow-right-circle"));
        addSiblingAfterMenuItem.setOnAction(e -> addSiblingNode(domNode, true));

        MenuItem addSiblingBeforeMenuItem = new MenuItem("Add Sibling Before");
        addSiblingBeforeMenuItem.setGraphic(new FontIcon("bi-arrow-left-circle"));
        addSiblingBeforeMenuItem.setOnAction(e -> addSiblingNode(domNode, false));

        MenuItem moveUpMenuItem = new MenuItem("Move Up");
        moveUpMenuItem.setGraphic(new FontIcon("bi-arrow-up-circle"));
        moveUpMenuItem.setOnAction(e -> moveNodeUp(domNode));

        MenuItem moveDownMenuItem = new MenuItem("Move Down");
        moveDownMenuItem.setGraphic(new FontIcon("bi-arrow-down-circle"));
        moveDownMenuItem.setOnAction(e -> moveNodeDown(domNode));

        MenuItem deleteMenuItem = new MenuItem("Delete: " + domNode.getNodeName());
        deleteMenuItem.setGraphic(new FontIcon("bi-trash"));
        deleteMenuItem.setOnAction(e -> deleteNode(domNode));

        // Attribute management menu items (only for elements)
        if (domNode.getNodeType() == Node.ELEMENT_NODE) {
            MenuItem addAttributeMenuItem = new MenuItem("Add Attribute");
            addAttributeMenuItem.setGraphic(new FontIcon("bi-at"));
            addAttributeMenuItem.setOnAction(e -> addAttributeToElement(domNode));

            MenuItem editAttributesMenuItem = new MenuItem("Edit Attributes");
            editAttributesMenuItem.setGraphic(new FontIcon("bi-pencil"));
            editAttributesMenuItem.setOnAction(e -> editElementAttributes(domNode));

            contextMenu.getItems().addAll(
                    addSiblingAfterMenuItem,
                    addSiblingBeforeMenuItem,
                    new SeparatorMenuItem(),
                    moveUpMenuItem,
                    moveDownMenuItem,
                    new SeparatorMenuItem(),
                    addAttributeMenuItem,
                    editAttributesMenuItem,
                    new SeparatorMenuItem(),
                    deleteMenuItem
            );
        } else {
            contextMenu.getItems().addAll(
                    addSiblingAfterMenuItem,
                    addSiblingBeforeMenuItem,
                    new SeparatorMenuItem(),
                    moveUpMenuItem,
                    moveDownMenuItem,
                    new SeparatorMenuItem(),
                    deleteMenuItem
            );
        }

        // Apply uniform font styling to context menu
        contextMenu.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

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


    private void moveNodeUp(Node domNode) {
        Node parent = domNode.getParentNode();
        if (parent == null) {
            logger.debug("Cannot move root node up");
            return;
        }

        Node previousSibling = domNode.getPreviousSibling();
        if (previousSibling == null) {
            logger.debug("Node is already at the top");
            return;
        }

        // Skip text nodes (whitespace)
        while (previousSibling != null && previousSibling.getNodeType() == Node.TEXT_NODE) {
            previousSibling = previousSibling.getPreviousSibling();
        }

        if (previousSibling == null) {
            logger.debug("No valid previous sibling found");
            return;
        }

        // Move the node up by inserting it before the previous sibling
        parent.removeChild(domNode);
        parent.insertBefore(domNode, previousSibling);

        // Refresh the UI
        refreshWholeView();
        logger.debug("Moved node '{}' up", domNode.getNodeName());
    }

    private void moveNodeDown(Node domNode) {
        Node parent = domNode.getParentNode();
        if (parent == null) {
            logger.debug("Cannot move root node down");
            return;
        }

        Node nextSibling = domNode.getNextSibling();
        if (nextSibling == null) {
            logger.debug("Node is already at the bottom");
            return;
        }

        // Skip text nodes (whitespace)
        while (nextSibling != null && nextSibling.getNodeType() == Node.TEXT_NODE) {
            nextSibling = nextSibling.getNextSibling();
        }

        if (nextSibling == null) {
            logger.debug("No valid next sibling found");
            return;
        }

        // Get the node after the next sibling
        Node nodeAfterNext = nextSibling.getNextSibling();

        // Move the node down by inserting it after the next sibling
        parent.removeChild(domNode);
        if (nodeAfterNext != null) {
            parent.insertBefore(domNode, nodeAfterNext);
        } else {
            parent.appendChild(domNode);
        }

        // Refresh the UI
        refreshWholeView();
        logger.debug("Moved node '{}' down", domNode.getNodeName());
    }


    private void addAttributeToElement(Node elementNode) {
        if (elementNode.getNodeType() != Node.ELEMENT_NODE) {
            logger.warn("Cannot add attribute to non-element node: {}", elementNode.getNodeName());
            return;
        }

        // Create dialog for attribute input
        TextInputDialog nameDialog = new TextInputDialog();
        nameDialog.setTitle("Add Attribute");
        nameDialog.setHeaderText("Add new attribute to element: " + elementNode.getNodeName());
        nameDialog.setContentText("Attribute name:");

        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isPresent() && !nameResult.get().trim().isEmpty()) {
            String attributeName = nameResult.get().trim();

            // Check if attribute already exists
            if (((Element) elementNode).hasAttribute(attributeName)) {
                showAttributeExistsWarning(attributeName);
                return;
            }

            TextInputDialog valueDialog = new TextInputDialog();
            valueDialog.setTitle("Add Attribute");
            valueDialog.setHeaderText("Set value for attribute: " + attributeName);
            valueDialog.setContentText("Attribute value:");

            Optional<String> valueResult = valueDialog.showAndWait();
            if (valueResult.isPresent()) {
                String attributeValue = valueResult.get();

                // Add the attribute
                ((Element) elementNode).setAttribute(attributeName, attributeValue);

                // Refresh the UI
                refreshWholeView();
                logger.debug("Added attribute '{}={}' to element '{}'", attributeName, attributeValue, elementNode.getNodeName());
            }
        }
    }

    private void editElementAttributes(Node elementNode) {
        if (elementNode.getNodeType() != Node.ELEMENT_NODE) {
            logger.warn("Cannot edit attributes of non-element node: {}", elementNode.getNodeName());
            return;
        }

        Element element = (Element) elementNode;
        NamedNodeMap attributes = element.getAttributes();

        if (attributes.getLength() == 0) {
            showNoAttributesInfo();
            return;
        }

        // Create custom dialog for attribute editing
        createAttributeEditDialog(element).showAndWait();
    }

    private void showAttributeExistsWarning(String attributeName) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Attribute Exists");
        alert.setHeaderText("Attribute already exists");
        alert.setContentText("The attribute '" + attributeName + "' already exists on this element.");
        alert.showAndWait();
    }

    private void showNoAttributesInfo() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("No Attributes");
        alert.setHeaderText("No attributes found");
        alert.setContentText("This element has no attributes to edit.");
        alert.showAndWait();
    }

    private Dialog<Void> createAttributeEditDialog(Element element) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Edit Attributes");
        dialog.setHeaderText("Edit attributes for element: " + element.getNodeName());

        // Create content
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));

        NamedNodeMap attributes = element.getAttributes();
        List<HBox> attributeRows = new ArrayList<>();

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(attr.getNodeName() + ":");
            nameLabel.setMinWidth(100);

            TextField valueField = new TextField(attr.getNodeValue());
            valueField.setMinWidth(200);

            Button deleteBtn = new Button("Delete");
            deleteBtn.setOnAction(e -> {
                element.removeAttribute(attr.getNodeName());
                content.getChildren().remove(row);
                refreshWholeView();
            });

            row.getChildren().addAll(nameLabel, valueField, deleteBtn);
            attributeRows.add(row);
            content.getChildren().add(row);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Handle OK button
        dialog.setResultConverter(buttonType -> {
            if (buttonType == ButtonType.OK) {
                // Update attribute values
                for (int i = 0; i < attributeRows.size() && i < attributes.getLength(); i++) {
                    HBox row = attributeRows.get(i);
                    TextField valueField = (TextField) row.getChildren().get(1);
                    String newValue = valueField.getText();

                    Node attr = attributes.item(i);
                    attr.setNodeValue(newValue);
                }

                refreshWholeView();
                logger.debug("Updated attributes for element: {}", element.getNodeName());
            }
            return null;
        });

        return dialog;
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
        // First try to find in our direct mapping
        for (Map.Entry<javafx.scene.Node, Node> entry : uiNodeToDomNodeMap.entrySet()) {
            if (entry.getValue() == targetDomNode) {
                return entry.getKey();
            }
        }

        // Fallback to recursive search
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

    /**
     * Navigates to the XSD definition of the specified DOM element.
     * This method is called from the context menu in the graphic editor.
     */
    private void navigateToXsdDefinitionFromGraphicEditor(Node domNode) {
        if (domNode.getNodeType() != Node.ELEMENT_NODE) {
            logger.debug("Go-to-Definition only available for element nodes, got: {}", domNode.getNodeType());
            return;
        }

        String elementName = domNode.getNodeName();
        logger.info("Go-to-Definition triggered from graphic editor for element: {}", elementName);

        try {
            var xsdDocumentationData = xmlEditor.getXsdDocumentationData();
            if (xsdDocumentationData == null) {
                logger.debug("XSD documentation data not yet loaded - showing user notification");
                javafx.application.Platform.runLater(() -> {
                    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Go-to-Definition");
                    alert.setHeaderText("XSD Schema Loading");
                    alert.setContentText("XSD schema is still being processed. Please wait a moment and try again.");
                    alert.showAndWait();
                });
                return;
            }

            // Look for element in the extended element map
            var extendedElementMap = xsdDocumentationData.getExtendedXsdElementMap();
            org.fxt.freexmltoolkit.domain.XsdExtendedElement targetElement = null;

            // Search for element by name
            for (var entry : extendedElementMap.entrySet()) {
                var element = entry.getValue();
                if (elementName.equals(element.getElementName())) {
                    targetElement = element;
                    break;
                }
            }

            if (targetElement == null) {
                // Also check global elements
                for (org.w3c.dom.Node globalElement : xsdDocumentationData.getGlobalElements()) {
                    String name = getElementNameFromNode(globalElement);
                    if (elementName.equals(name)) {
                        // Found in global elements - navigate to XSD
                        navigateToXsdFile(globalElement, elementName);
                        return;
                    }
                }

                logger.debug("Element '{}' not found in XSD documentation", elementName);
                showElementNotFoundMessage(elementName);
                return;
            }

            logger.debug("Found element '{}' in XSD documentation, showing definition info", elementName);
            showXsdDefinitionInfo(targetElement, elementName);

        } catch (Exception e) {
            logger.error("Error in Go-to-Definition from graphic editor: {}", e.getMessage(), e);
        }
    }

    /**
     * Navigates to the XSD file and highlights the element definition.
     */
    private void navigateToXsdFile(org.w3c.dom.Node targetNode, String elementName) {
        try {
            var mainController = xmlEditor.getMainController();

            // Try to get MainController through sidebar controller if direct access fails
            if (mainController == null && sidebarController != null) {
                try {
                    var sidebarClass = sidebarController.getClass();
                    var getMainControllerMethod = sidebarClass.getMethod("getMainController");
                    mainController = (org.fxt.freexmltoolkit.controller.MainController) getMainControllerMethod.invoke(sidebarController);
                    logger.debug("Retrieved MainController through sidebar controller");
                } catch (Exception e) {
                    logger.debug("Could not get MainController through sidebar: {}", e.getMessage());
                }
            }

            if (mainController == null) {
                logger.error("MainController not available for navigation - tried both xmlEditor and sidebarController");
                javafx.application.Platform.runLater(() -> {
                    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                    alert.setTitle("Go-to-Definition");
                    alert.setHeaderText("Navigation Error");
                    alert.setContentText("Unable to navigate to XSD definition. Please ensure both XML and XSD files are loaded.");
                    alert.showAndWait();
                });
                return;
            }

            // Use reflection to access XSD controller and navigate
            var mainControllerClass = mainController.getClass();
            var getXsdControllerMethod = mainControllerClass.getMethod("getXsdUltimateController");
            var xsdController = getXsdControllerMethod.invoke(mainController);

            if (xsdController != null) {
                logger.debug("Navigating to XSD tab for element: {}", elementName);

                // Switch to XSD tab
                var showXsdTabMethod = mainControllerClass.getMethod("showXsdTab");
                showXsdTabMethod.invoke(mainController);

                logger.debug("Successfully navigated to XSD definition for element: {}", elementName);
            } else {
                logger.debug("XSD controller not available");
            }
        } catch (Exception e) {
            logger.error("Error navigating to XSD file: {}", e.getMessage(), e);
        }
    }

    /**
     * Extracts element name from a DOM node.
     */
    private String getElementNameFromNode(org.w3c.dom.Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            return node.getNodeName();
        }
        return "";
    }

    /**
     * Shows XSD definition information for the element in a dialog.
     */
    private void showXsdDefinitionInfo(org.fxt.freexmltoolkit.domain.XsdExtendedElement element, String elementName) {
        javafx.application.Platform.runLater(() -> {
            var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("XSD Definition - " + elementName);
            alert.setHeaderText("Element: " + elementName);

            StringBuilder content = new StringBuilder();
            content.append("Type: ").append(element.getElementType() != null ? element.getElementType() : "Not specified").append("\n");
            content.append("XPath: ").append(element.getCurrentXpath() != null ? element.getCurrentXpath() : "Unknown").append("\n");
            content.append("Mandatory: ").append(element.isMandatory() ? "Yes" : "No").append("\n");

            if (element.getDocumentationAsHtml() != null && !element.getDocumentationAsHtml().trim().isEmpty()) {
                content.append("\nDocumentation:\n").append(element.getDocumentationAsHtml().replaceAll("<[^>]*>", ""));
            }

            if (element.getXsdRestrictionString() != null && !element.getXsdRestrictionString().trim().isEmpty()) {
                content.append("\nRestrictions:\n").append(element.getXsdRestrictionString().replaceAll("<[^>]*>", ""));
            }

            alert.setContentText(content.toString());
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();

            // Also try to navigate to XSD tab if possible
            navigateToXsdFile(element.getCurrentNode(), elementName);
        });
    }

    /**
     * Shows a message when an element definition is not found.
     */
    private void showElementNotFoundMessage(String elementName) {
        javafx.application.Platform.runLater(() -> {
            logger.debug("Element '{}' definition not found in associated XSD", elementName);
            var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Go-to-Definition");
            alert.setHeaderText("Element Not Found");
            alert.setContentText("Definition for element '" + elementName + "' was not found in the associated XSD schema.");
            alert.showAndWait();
        });
    }

    // ========== SEARCH FUNCTIONALITY ==========

    /**
     * Performs a comprehensive search through the XML document
     *
     * @param searchTerm    the term to search for
     * @param caseSensitive whether the search should be case sensitive
     * @return information about the search results
     */
    public SearchResultInfo performSearch(String searchTerm, boolean caseSensitive) {
        logger.info("🔍 Starting search for term: '{}' (case sensitive: {})", searchTerm, caseSensitive);

        // Clear previous search results
        clearSearch();

        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            logger.debug("Empty search term, returning no results");
            return new SearchResultInfo(0, -1, "");
        }

        currentSearchTerm = caseSensitive ? searchTerm.trim() : searchTerm.trim().toLowerCase();
        searchResults = new ArrayList<>();

        // Start recursive search from the current DOM node
        List<Node> pathToRoot = new ArrayList<>();
        searchInNode(currentDomNode, pathToRoot, caseSensitive);

        logger.info("🎯 Search completed. Found {} results for term: '{}'", searchResults.size(), searchTerm);

        if (!searchResults.isEmpty()) {
            currentSearchIndex = 0;
            navigateToSearchResult(0);
            return new SearchResultInfo(searchResults.size(), 0, searchTerm);
        } else {
            return new SearchResultInfo(0, -1, searchTerm);
        }
    }

    /**
     * Recursively searches within a DOM node and its children
     */
    private void searchInNode(Node node, List<Node> pathToRoot, boolean caseSensitive) {
        if (node == null) return;

        // Add current node to path
        pathToRoot.add(node);

        try {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    searchElementNode(node, pathToRoot, caseSensitive);
                    break;
                case Node.TEXT_NODE:
                    searchTextNode(node, pathToRoot, caseSensitive);
                    break;
                case Node.COMMENT_NODE:
                    searchCommentNode(node, pathToRoot, caseSensitive);
                    break;
            }

            // Recursively search children
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                searchInNode(children.item(i), new ArrayList<>(pathToRoot), caseSensitive);
            }

        } finally {
            // Remove current node from path when backtracking
            if (!pathToRoot.isEmpty()) {
                pathToRoot.remove(pathToRoot.size() - 1);
            }
        }
    }

    /**
     * Searches within an element node (name and attributes)
     */
    private void searchElementNode(Node node, List<Node> pathToRoot, boolean caseSensitive) {
        String nodeName = node.getNodeName();

        // Search in element name
        if (containsSearchTerm(nodeName, caseSensitive)) {
            javafx.scene.Node uiNode = findUINodeForDomNode(node);
            if (uiNode != null) {
                SearchResult result = new SearchResult(node, uiNode, SearchType.NODE_NAME,
                        nodeName, pathToRoot, nodeName);
                searchResults.add(result);
                logger.debug("Found element name match: {} in node: {}", currentSearchTerm, nodeName);
            }
        }

        // Search in attributes
        if (node.hasAttributes()) {
            NamedNodeMap attributes = node.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                String attrName = attribute.getNodeName();
                String attrValue = attribute.getNodeValue();

                // Search in attribute name
                if (containsSearchTerm(attrName, caseSensitive)) {
                    javafx.scene.Node uiNode = findUINodeForDomNode(node);
                    if (uiNode != null) {
                        SearchResult result = new SearchResult(node, uiNode, SearchType.ATTRIBUTE_NAME,
                                attrName, pathToRoot, attrName + "=" + attrValue);
                        searchResults.add(result);
                        logger.debug("Found attribute name match: {} in attribute: {}", currentSearchTerm, attrName);
                    }
                }

                // Search in attribute value
                if (containsSearchTerm(attrValue, caseSensitive)) {
                    javafx.scene.Node uiNode = findUINodeForDomNode(node);
                    if (uiNode != null) {
                        SearchResult result = new SearchResult(node, uiNode, SearchType.ATTRIBUTE_VALUE,
                                attrValue, pathToRoot, attrName + "=" + attrValue);
                        searchResults.add(result);
                        logger.debug("Found attribute value match: {} in attribute: {}={}",
                                currentSearchTerm, attrName, attrValue);
                    }
                }
            }
        }
    }

    /**
     * Searches within a text node
     */
    private void searchTextNode(Node node, List<Node> pathToRoot, boolean caseSensitive) {
        String textContent = node.getNodeValue();
        if (textContent != null && !textContent.trim().isEmpty()) {
            if (containsSearchTerm(textContent, caseSensitive)) {
                // For text nodes, we want to find the parent element's UI node
                Node parentNode = node.getParentNode();
                if (parentNode != null) {
                    javafx.scene.Node uiNode = findUINodeForDomNode(parentNode);
                    if (uiNode != null) {
                        SearchResult result = new SearchResult(parentNode, uiNode, SearchType.TEXT_CONTENT,
                                textContent.trim(), pathToRoot, textContent.trim());
                        searchResults.add(result);
                        logger.debug("Found text content match: {} in text: {}", currentSearchTerm, textContent.trim());
                    }
                }
            }
        }
    }

    /**
     * Searches within a comment node
     */
    private void searchCommentNode(Node node, List<Node> pathToRoot, boolean caseSensitive) {
        String commentContent = node.getNodeValue();
        if (containsSearchTerm(commentContent, caseSensitive)) {
            javafx.scene.Node uiNode = findUINodeForDomNode(node);
            if (uiNode != null) {
                SearchResult result = new SearchResult(node, uiNode, SearchType.COMMENT,
                        commentContent.trim(), pathToRoot, commentContent.trim());
                searchResults.add(result);
                logger.debug("Found comment match: {} in comment: {}", currentSearchTerm, commentContent.trim());
            }
        }
    }

    /**
     * Checks if the given text contains the search term
     */
    private boolean containsSearchTerm(String text, boolean caseSensitive) {
        if (text == null || currentSearchTerm.isEmpty()) {
            return false;
        }

        String textToSearch = caseSensitive ? text : text.toLowerCase();
        return textToSearch.contains(currentSearchTerm);
    }

    /**
     * Navigates to the next search result
     */
    public SearchResultInfo navigateToNextSearchResult() {
        if (searchResults.isEmpty()) {
            logger.debug("No search results available for navigation");
            return new SearchResultInfo(0, -1, currentSearchTerm);
        }

        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size();
        navigateToSearchResult(currentSearchIndex);

        logger.debug("Navigated to next search result: {} of {}", currentSearchIndex + 1, searchResults.size());
        return new SearchResultInfo(searchResults.size(), currentSearchIndex, currentSearchTerm);
    }

    /**
     * Navigates to the previous search result
     */
    public SearchResultInfo navigateToPreviousSearchResult() {
        if (searchResults.isEmpty()) {
            logger.debug("No search results available for navigation");
            return new SearchResultInfo(0, -1, currentSearchTerm);
        }

        currentSearchIndex = currentSearchIndex <= 0 ? searchResults.size() - 1 : currentSearchIndex - 1;
        navigateToSearchResult(currentSearchIndex);

        logger.debug("Navigated to previous search result: {} of {}", currentSearchIndex + 1, searchResults.size());
        return new SearchResultInfo(searchResults.size(), currentSearchIndex, currentSearchTerm);
    }

    /**
     * Navigates to a specific search result by index
     */
    private void navigateToSearchResult(int index) {
        if (index < 0 || index >= searchResults.size()) {
            logger.warn("Invalid search result index: {} (available: 0-{})", index, searchResults.size() - 1);
            return;
        }

        SearchResult result = searchResults.get(index);
        logger.debug("Navigating to search result {}: {} - {}", index + 1, result.type, result.matchedText);

        // Clear previous highlights
        clearHighlights();

        // Expand path to the found node
        expandPathToNode(result);

        // Highlight and scroll to the result
        highlightSearchResult(result);
        scrollToNode(result.uiNode);

        // Select the node
        selectNode(result.uiNode, result.domNode);
    }

    /**
     * Expands all nodes in the path to make the search result visible
     */
    private void expandPathToNode(SearchResult result) {
        logger.debug("Expanding path to node: {}", result.domNode.getNodeName());

        for (Node pathNode : result.pathToRoot) {
            Button toggleButton = nodeToggleButtonMap.get(pathNode);
            if (toggleButton != null) {
                // Check if the node is currently collapsed (button should be expanded)
                // We need to find the associated content wrapper to check visibility
                javafx.scene.Node parent = toggleButton.getParent();
                if (parent instanceof HBox headerBox) {
                    javafx.scene.Node elementContainer = headerBox.getParent();
                    if (elementContainer instanceof VBox vbox) {
                        // Look for the content wrapper (should be the second child after header)
                        if (vbox.getChildren().size() > 1) {
                            javafx.scene.Node contentWrapper = vbox.getChildren().get(1);
                            if (contentWrapper instanceof HBox && !contentWrapper.isVisible()) {
                                // Node is collapsed, expand it
                                logger.debug("Expanding collapsed node: {}", pathNode.getNodeName());
                                toggleButton.fire();
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Highlights a search result visually
     */
    private void highlightSearchResult(SearchResult result) {
        highlightedNodes.add(result.uiNode);

        // Add highlight style to the UI node
        String currentStyle = result.uiNode.getStyle();
        String highlightStyle = "-fx-background-color: #ffff99; -fx-border-color: #ff6600; -fx-border-width: 2px;";
        result.uiNode.setStyle(currentStyle + " " + highlightStyle);

        logger.debug("Highlighted search result: {} - {}", result.type, result.matchedText);
    }

    /**
     * Clears all visual highlights from previous search results
     */
    private void clearHighlights() {
        for (javafx.scene.Node node : highlightedNodes) {
            String style = node.getStyle();
            // Remove highlight-related styles
            style = style.replaceAll("-fx-background-color: #ffff99;", "");
            style = style.replaceAll("-fx-border-color: #ff6600;", "");
            style = style.replaceAll("-fx-border-width: 2px;", "");
            style = style.trim();
            node.setStyle(style);
        }
        highlightedNodes.clear();
    }

    /**
     * Clears all search-related state and visual indicators
     */
    public void clearSearch() {
        logger.debug("Clearing search state");
        clearHighlights();
        searchResults.clear();
        currentSearchIndex = -1;
        currentSearchTerm = "";
    }

    /**
     * Gets the current search results information
     */
    public SearchResultInfo getCurrentSearchInfo() {
        if (searchResults.isEmpty()) {
            return new SearchResultInfo(0, -1, currentSearchTerm);
        }
        return new SearchResultInfo(searchResults.size(), currentSearchIndex, currentSearchTerm);
    }

    /**
     * Checks if there are any search results
     */
    public boolean hasSearchResults() {
        return !searchResults.isEmpty();
    }

    /**
     * Creates and returns a search bar UI component
     */
    public HBox createSearchBar() {
        if (searchBar != null) {
            return searchBar;
        }

        searchBar = new HBox(5);
        searchBar.getStyleClass().add("xml-search-bar");
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setPadding(new Insets(8, 12, 8, 12));
        searchBar.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f8f9fa, #e9ecef); " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-border-width: 0 0 1px 0; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 2, 0, 0, 1);"
        );

        // Search icon
        Label searchIcon = new Label("🔍");
        searchIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: #6c757d;");

        // Search text field
        TextField searchField = new TextField();
        searchField.setPromptText("Search nodes, attributes, text, comments...");
        searchField.setPrefColumnCount(25);
        searchField.setStyle(
                "-fx-background-color: white; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 4px; " +
                        "-fx-background-radius: 4px; " +
                        "-fx-padding: 4px 8px;"
        );

        // Case sensitive checkbox
        CheckBox caseSensitiveCheckBox = new CheckBox("Case sensitive");
        caseSensitiveCheckBox.setStyle(
                "-fx-text-fill: #495057; " +
                        "-fx-font-size: 11px;"
        );

        // Results label
        Label resultsLabel = new Label("Ready");
        resultsLabel.setStyle(
                "-fx-text-fill: #6c757d; " +
                        "-fx-font-size: 11px; " +
                        "-fx-min-width: 80px;"
        );

        // Previous button
        Button prevButton = new Button("◀");
        prevButton.setTooltip(new Tooltip("Previous result (Shift+Enter)"));
        prevButton.setDisable(true);
        prevButton.setStyle(
                "-fx-background-color: #f8f9fa; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-padding: 2px 8px; " +
                        "-fx-font-size: 12px;"
        );

        // Next button
        Button nextButton = new Button("▶");
        nextButton.setTooltip(new Tooltip("Next result (Enter)"));
        nextButton.setDisable(true);
        nextButton.setStyle(
                "-fx-background-color: #f8f9fa; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-padding: 2px 8px; " +
                        "-fx-font-size: 12px;"
        );

        // Clear/Close button
        Button clearButton = new Button("✕");
        clearButton.setTooltip(new Tooltip("Close search (Escape)"));
        clearButton.setStyle(
                "-fx-background-color: #f8f9fa; " +
                        "-fx-border-color: #ced4da; " +
                        "-fx-border-width: 1px; " +
                        "-fx-border-radius: 3px; " +
                        "-fx-background-radius: 3px; " +
                        "-fx-padding: 2px 8px; " +
                        "-fx-font-size: 12px;"
        );

        // Search functionality
        Runnable performSearch = () -> {
            String searchTerm = searchField.getText();
            boolean caseSensitive = caseSensitiveCheckBox.isSelected();

            if (searchTerm.trim().isEmpty()) {
                clearSearch();
                resultsLabel.setText("Ready");
                prevButton.setDisable(true);
                nextButton.setDisable(true);
                return;
            }

            SearchResultInfo info = performSearch(searchTerm, caseSensitive);

            if (info.hasResults()) {
                resultsLabel.setText(info.toString());
                prevButton.setDisable(false);
                nextButton.setDisable(false);
            } else {
                resultsLabel.setText("No results");
                prevButton.setDisable(true);
                nextButton.setDisable(true);
            }
        };

        // Event handlers
        searchField.textProperty().addListener((obs, oldText, newText) -> {
            // Perform search on text change with a small delay to avoid excessive searches
            if (newText.trim().length() > 0) {
                javafx.application.Platform.runLater(performSearch);
            } else {
                clearSearch();
                resultsLabel.setText("Ready");
                prevButton.setDisable(true);
                nextButton.setDisable(true);
            }
        });

        caseSensitiveCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!searchField.getText().trim().isEmpty()) {
                javafx.application.Platform.runLater(performSearch);
            }
        });

        searchField.setOnAction(e -> {
            if (hasSearchResults()) {
                SearchResultInfo info = navigateToNextSearchResult();
                resultsLabel.setText(info.toString());
            }
        });

        prevButton.setOnAction(e -> {
            if (hasSearchResults()) {
                SearchResultInfo info = navigateToPreviousSearchResult();
                resultsLabel.setText(info.toString());
            }
        });

        nextButton.setOnAction(e -> {
            if (hasSearchResults()) {
                SearchResultInfo info = navigateToNextSearchResult();
                resultsLabel.setText(info.toString());
            }
        });

        clearButton.setOnAction(e -> {
            hideSearchBar();
        });

        // Keyboard shortcuts
        searchField.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER:
                    if (e.isShiftDown()) {
                        if (hasSearchResults()) {
                            SearchResultInfo info = navigateToPreviousSearchResult();
                            resultsLabel.setText(info.toString());
                        }
                    } else {
                        if (hasSearchResults()) {
                            SearchResultInfo info = navigateToNextSearchResult();
                            resultsLabel.setText(info.toString());
                        }
                    }
                    e.consume();
                    break;
                case ESCAPE:
                    hideSearchBar();
                    e.consume();
                    break;
            }
        });

        // Add components to search bar
        searchBar.getChildren().addAll(
                searchIcon,
                searchField,
                caseSensitiveCheckBox,
                new Separator(Orientation.VERTICAL),
                resultsLabel,
                prevButton,
                nextButton,
                clearButton
        );

        // Initially hide the search bar
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        return searchBar;
    }

    /**
     * Shows the search bar and focuses the search field
     */
    public void showSearchBar() {
        if (searchBar == null) {
            createSearchBar();
        }

        searchBar.setVisible(true);
        searchBar.setManaged(true);

        // Focus the search field
        TextField searchField = (TextField) searchBar.getChildren().get(1);
        javafx.application.Platform.runLater(() -> {
            searchField.requestFocus();
            searchField.selectAll();
        });

        logger.debug("Search bar shown and focused");
    }

    /**
     * Hides the search bar and clears search results
     */
    public void hideSearchBar() {
        if (searchBar != null) {
            searchBar.setVisible(false);
            searchBar.setManaged(false);

            // Clear search results
            clearSearch();

            // Reset search field
            TextField searchField = (TextField) searchBar.getChildren().get(1);
            searchField.clear();

            // Reset results label
            Label resultsLabel = (Label) searchBar.getChildren().get(4);
            resultsLabel.setText("Ready");

            // Disable buttons
            Button prevButton = (Button) searchBar.getChildren().get(6);
            Button nextButton = (Button) searchBar.getChildren().get(7);
            prevButton.setDisable(true);
            nextButton.setDisable(true);

            logger.debug("Search bar hidden and state cleared");
        }
    }

    /**
     * Returns whether the search bar is currently visible
     */
    public boolean isSearchBarVisible() {
        return searchBar != null && searchBar.isVisible();
    }

    /**
     * Sets up global keyboard shortcuts for search functionality
     */
    private void setupSearchKeyboardShortcuts() {
        this.setOnKeyPressed(e -> {
            if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
                showSearchBar();
                e.consume();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && isSearchBarVisible()) {
                hideSearchBar();
                e.consume();
            }
        });
    }

    /**
     * Integrates the search functionality with a parent container.
     * This method should be called by the parent to set up the search bar in the UI.
     *
     * @param parentContainer the parent container (typically a VBox) where the search bar should be added
     */
    public void integrateSearchWithContainer(VBox parentContainer) {
        HBox searchBar = createSearchBar();

        // Insert search bar at the top of the parent container
        if (!parentContainer.getChildren().contains(searchBar)) {
            parentContainer.getChildren().add(0, searchBar);
            logger.debug("Search bar integrated with parent container");
        }
    }

    /**
     * Alternative method for integrating search with any Pane-based container
     */
    public void integrateSearchWithContainer(Pane parentContainer) {
        HBox searchBar = createSearchBar();

        if (!parentContainer.getChildren().contains(searchBar)) {
            parentContainer.getChildren().add(searchBar);
            logger.debug("Search bar integrated with pane container");
        }
    }

    /**
     * Gets the search bar component for manual integration
     */
    public HBox getSearchBar() {
        return createSearchBar();
    }
}