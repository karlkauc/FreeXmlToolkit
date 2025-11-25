package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.menu.XsdContextMenuFactory;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.XsdPropertiesPanel;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Graphical XSD visualization with rectangular nodes and expand/collapse functionality.
 * Renders schema structure similar to XMLSpy with boxes and connection lines.
 *
 * @since 2.0
 */
public class XsdGraphView extends BorderPane implements PropertyChangeListener {

    private static final Logger logger = LogManager.getLogger(XsdGraphView.class);

    private final XsdSchema xsdSchema;
    private Map<String, org.fxt.freexmltoolkit.controls.v2.model.XsdSchema> importedSchemas = new HashMap<>();
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final XsdNodeRenderer renderer;
    private final SelectionModel selectionModel;
    private XsdEditorContext editorContext;
    private XsdContextMenuFactory contextMenuFactory;
    private XsdPropertiesPanel propertiesPanel;
    private SplitPane mainSplitPane;
    private ToggleButton propertiesToggle;

    private VisualNode rootNode;
    private VisualNode selectedNode;
    private VisualNode hoveredNode;
    private final Map<String, VisualNode> nodeMap = new HashMap<>();

    // Zoom state
    private double zoomLevel = 1.0;
    private static final double ZOOM_MIN = 0.1;
    private static final double ZOOM_MAX = 5.0;
    private static final double ZOOM_STEP = 0.1;
    private Label zoomLabel;

    // Save callback
    private Runnable onSaveCallback;
    private Button saveButton;

    // Type Editor callbacks (stored to re-apply when contextMenuFactory is recreated)
    private java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType> openComplexTypeEditorCallback;
    private java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType> openSimpleTypeEditorCallback;

    /**
     * Constructor using the XsdSchema (XsdNode-based tree structure).
     *
     * @param schema the XSD schema to visualize
     * @since 2.0
     */
    public XsdGraphView(XsdSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Schema cannot be null");
        }

        this.xsdSchema = schema;
        this.renderer = new XsdNodeRenderer();
        this.selectionModel = new SelectionModel();

        // Create canvas for drawing
        this.canvas = new Canvas(2000, 2000);
        this.scrollPane = new ScrollPane(canvas);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: white;");

        // Pass the existing selectionModel to EditorContext so they share the same instance
        this.editorContext = new XsdEditorContext(schema, this.selectionModel);
        this.contextMenuFactory = new XsdContextMenuFactory(editorContext);

        initializeUI();
    }

    /**
     * Initializes the UI components (called from constructors).
     */
    private void initializeUI() {

        // Setup selection listener
        selectionModel.addSelectionListener((oldSelection, newSelection) -> {
            // Update visual state of previously selected nodes
            for (VisualNode node : oldSelection) {
                node.setSelected(false);
                node.setFocused(false);
                node.setInEditMode(false);
            }
            // Update visual state of newly selected nodes
            boolean editMode = editorContext != null && editorContext.isEditMode();
            for (VisualNode node : newSelection) {
                node.setSelected(true);
                node.setInEditMode(editMode);
            }
            // Set focus on primary selection
            VisualNode primarySelection = selectionModel.getPrimarySelection();
            if (primarySelection != null) {
                primarySelection.setFocused(true);
            }
            // Update legacy selectedNode reference
            if (!newSelection.isEmpty()) {
                selectedNode = newSelection.iterator().next();
            } else {
                selectedNode = null;
            }
            // Redraw to show selection changes
            redraw();
        });

        // Setup UI
        setupLayout();

        // Build visual tree
        buildVisualTree();

        // Draw initial state
        redraw();

        // Register for model changes
        xsdSchema.addPropertyChangeListener(this);

        // Setup mouse interaction
        setupMouseHandlers();

        logger.info("XsdGraphView initialized (graphical mode) using XsdSchema");
    }

    /**
     * Sets up the UI layout.
     */
    private void setupLayout() {
        // Toolbar
        ToolBar toolbar = createToolbar();
        setTop(toolbar);

        // Properties panel (initially hidden)
        propertiesPanel = new XsdPropertiesPanel(editorContext);
        propertiesPanel.setPrefWidth(300);
        propertiesPanel.setMinWidth(200);
        propertiesPanel.setMaxWidth(500);

        // Main split pane: horizontal split with canvas and properties panel
        mainSplitPane = new SplitPane(scrollPane, propertiesPanel);
        mainSplitPane.setOrientation(javafx.geometry.Orientation.HORIZONTAL);
        mainSplitPane.setDividerPositions(0.7);

        setCenter(mainSplitPane);
        setStyle("-fx-background-color: #f5f5f5;");
    }

    /**
     * Creates the toolbar.
     */
    private ToolBar createToolbar() {
        ToolBar toolbar = new ToolBar();

        // Save Button
        saveButton = new Button("Save");
        saveButton.setTooltip(new Tooltip("Save XSD file (Ctrl+S)"));
        saveButton.setStyle("-fx-font-weight: bold;");
        saveButton.setOnAction(e -> {
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
        });
        // Initially disabled until callback is set and edit mode is active
        saveButton.setDisable(true);

        Button expandAllBtn = new Button("Expand All");
        expandAllBtn.setOnAction(e -> {
            expandAll(rootNode);
            redraw();
        });

        Button collapseAllBtn = new Button("Collapse All");
        collapseAllBtn.setOnAction(e -> {
            collapseAll(rootNode);
            redraw();
        });

        Button fitBtn = new Button("Fit to View");
        fitBtn.setOnAction(e -> fitToView());

        // Separator
        Separator separator = new Separator();
        separator.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Properties Panel Toggle Button
        propertiesToggle = new ToggleButton("Properties");
        propertiesToggle.setSelected(true); // Initially visible
        propertiesToggle.setTooltip(new Tooltip("Show/Hide Properties Panel (Ctrl+P)"));
        propertiesToggle.setOnAction(e -> {
            if (propertiesToggle.isSelected()) {
                // Show properties panel
                if (!mainSplitPane.getItems().contains(propertiesPanel)) {
                    mainSplitPane.getItems().add(propertiesPanel);
                    mainSplitPane.setDividerPositions(0.7);
                }
            } else {
                // Hide properties panel
                mainSplitPane.getItems().remove(propertiesPanel);
            }
        });

        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Zoom buttons
        Button zoomInBtn = new Button("+");
        zoomInBtn.setStyle("-fx-font-weight: bold; -fx-padding: 5 10;");
        zoomInBtn.setTooltip(new Tooltip("Zoom In (Ctrl +)"));
        zoomInBtn.setOnAction(e -> zoomIn());

        Button zoomOutBtn = new Button("-");
        zoomOutBtn.setStyle("-fx-font-weight: bold; -fx-padding: 5 10;");
        zoomOutBtn.setTooltip(new Tooltip("Zoom Out (Ctrl -)"));
        zoomOutBtn.setOnAction(e -> zoomOut());

        Button zoomResetBtn = new Button("100%");
        zoomResetBtn.setStyle("-fx-padding: 5 10;");
        zoomResetBtn.setTooltip(new Tooltip("Reset Zoom to 100% (Ctrl 0)"));
        zoomResetBtn.setOnAction(e -> zoomReset());

        zoomLabel = new Label("100%");
        zoomLabel.setStyle("-fx-padding: 5; -fx-font-weight: bold;");

        Label infoLabel = new Label("XSD Editor V2 (Beta) - Graphical View");
        infoLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getItems().addAll(
                saveButton,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                expandAllBtn, collapseAllBtn, fitBtn,
                separator,
                propertiesToggle,
                separator2,
                zoomInBtn, zoomOutBtn, zoomResetBtn, zoomLabel,
                spacer, infoLabel
        );

        return toolbar;
    }

    /**
     * Builds the visual tree from the schema.
     * Shows only global elements (not complex/simple types directly).
     * Types are resolved when elements reference them.
     */
    private void buildVisualTree() {
        nodeMap.clear();

        XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
        // Provide rebuild callback for Model → View synchronization
        // For structural changes (add/delete child), we need to rebuild the tree
        rootNode = builder.buildFromSchema(xsdSchema, this::rebuildVisualTree, importedSchemas);
        nodeMap.putAll(builder.getNodeMap());
        logger.debug("Visual tree built from XsdSchema with {} nodes (with auto-rebuild on model changes)", nodeMap.size());
    }

    /**
     * Rebuilds the visual tree while preserving expansion and selection state.
     * This is called when structural changes occur (add/delete child elements).
     * <p>
     * The method:
     * 1. Saves which nodes are currently expanded
     * 2. Saves current selection
     * 3. Rebuilds the entire VisualNode tree from the model
     * 4. Restores expansion state
     * 5. Restores selection
     * 6. Triggers a redraw
     *
     * @since 2.0
     */
    private void rebuildVisualTree() {
        if (rootNode == null) {
            logger.warn("Cannot rebuild visual tree: rootNode is null");
            return;
        }

        // Prevent infinite recursion during rebuild
        // When we call buildVisualTree(), it creates new VisualNodes with this::rebuildVisualTree as callback
        // We need to prevent those new nodes from triggering another rebuild during construction
        if (isRebuilding) {
            logger.trace("Already rebuilding, skipping nested rebuild call");
            return;
        }

        try {
            isRebuilding = true;

            logger.debug("Rebuilding visual tree (structural change detected)");

            // 1. Save expansion state
            Set<String> expandedNodeIds = new HashSet<>();
            collectExpandedNodes(rootNode, expandedNodeIds);
            logger.trace("Saved {} expanded nodes", expandedNodeIds.size());

            // 2. Save selection state
            Set<String> selectedNodeIds = new HashSet<>();
            for (VisualNode selected : selectionModel.getSelectedNodes()) {
                Object modelObj = selected.getModelObject();
                if (modelObj instanceof XsdNode xsdNode) {
                    selectedNodeIds.add(xsdNode.getId());
                }
            }
            logger.trace("Saved {} selected nodes", selectedNodeIds.size());

            // 3. Rebuild tree from schema
            // Temporarily use this::redraw to avoid recursion during rebuild
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            rootNode = builder.buildFromSchema(xsdSchema, this::redraw, importedSchemas);
            nodeMap.clear();
            nodeMap.putAll(builder.getNodeMap());
            logger.debug("Visual tree rebuilt with {} nodes", nodeMap.size());
            logger.debug("Root node has {} children", rootNode.getChildren().size());

            // 4. Restore expansion state
            restoreExpansionState(rootNode, expandedNodeIds);
            logger.trace("Restored expansion state");

            // 5. Restore selection
            selectionModel.clearSelection();
            for (String selectedId : selectedNodeIds) {
                VisualNode nodeToSelect = findNodeById(rootNode, selectedId);
                if (nodeToSelect != null) {
                    selectionModel.addToSelection(nodeToSelect);
                }
            }
            logger.trace("Restored selection state");

            // 6. Now update all VisualNodes to use this::rebuildVisualTree as callback again
            updateCallbacks(rootNode, this::rebuildVisualTree);

        } finally {
            isRebuilding = false;
        }

        // 7. Trigger redraw on JavaFX thread
        logger.debug("Triggering redraw after rebuild");
        javafx.application.Platform.runLater(this::redraw);
    }

    private boolean isRebuilding = false;

    /**
     * Recursively collects IDs of all expanded nodes.
     */
    private void collectExpandedNodes(VisualNode node, Set<String> expandedIds) {
        if (node == null) return;

        if (node.isExpanded()) {
            Object modelObj = node.getModelObject();
            if (modelObj instanceof XsdNode xsdNode) {
                expandedIds.add(xsdNode.getId());
            }
        }

        for (VisualNode child : node.getChildren()) {
            collectExpandedNodes(child, expandedIds);
        }
    }

    /**
     * Recursively restores expansion state based on saved node IDs.
     */
    private void restoreExpansionState(VisualNode node, Set<String> expandedIds) {
        if (node == null) return;

        Object modelObj = node.getModelObject();
        if (modelObj instanceof XsdNode xsdNode) {
            if (expandedIds.contains(xsdNode.getId())) {
                node.setExpanded(true);
            }
        }

        for (VisualNode child : node.getChildren()) {
            restoreExpansionState(child, expandedIds);
        }
    }

    /**
     * Finds a VisualNode by its model's ID.
     */
    private VisualNode findNodeById(VisualNode node, String id) {
        if (node == null) return null;

        Object modelObj = node.getModelObject();
        if (modelObj instanceof XsdNode xsdNode) {
            if (xsdNode.getId().equals(id)) {
                return node;
            }
        }

        for (VisualNode child : node.getChildren()) {
            VisualNode found = findNodeById(child, id);
            if (found != null) {
                return found;
            }
        }

        return null;
    }

    /**
     * Updates the callback for all VisualNodes in the tree.
     * This is needed after rebuilding to ensure all nodes use the correct callback.
     */
    private void updateCallbacks(VisualNode node, Runnable callback) {
        if (node == null) return;

        node.setOnModelChangeCallback(callback);

        for (VisualNode child : node.getChildren()) {
            updateCallbacks(child, callback);
        }
    }

    /**
     * Redraws the entire canvas.
     */
    private void redraw() {
        // logger.debug("redraw() called, rootNode has {} children", rootNode != null ? rootNode.getChildren().size() : "null");
        GraphicsContext gc = canvas.getGraphicsContext2D();

        if (rootNode == null) {
            logger.warn("redraw() aborted: rootNode is null");
            return;
        }

        // Layout nodes
        layoutNode(rootNode, 50, 50);

        // Calculate required canvas size based on node positions
        double[] bounds = calculateCanvasBounds(rootNode);
        double requiredWidth = bounds[0] + 100;  // Add padding
        double requiredHeight = bounds[1] + 100; // Add padding

        // Ensure minimum size
        double canvasWidth = Math.max(requiredWidth, 800);
        double canvasHeight = Math.max(requiredHeight, 600);

        // Resize canvas if needed
        if (canvas.getWidth() != canvasWidth || canvas.getHeight() != canvasHeight) {
            canvas.setWidth(canvasWidth);
            canvas.setHeight(canvasHeight);
            // logger.debug("Canvas resized to {}x{}", canvasWidth, canvasHeight);
        }

        // Clear canvas
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Render tree
        renderTree(gc, rootNode);
    }

    /**
     * Calculates the required canvas bounds based on all visible nodes.
     * Returns [maxX, maxY] coordinates.
     */
    private double[] calculateCanvasBounds(VisualNode node) {
        double maxX = node.getX() + node.getWidth();
        double maxY = node.getY() + node.getHeight();

        if (node.isExpanded() && node.hasChildren()) {
            for (VisualNode child : node.getChildren()) {
                double[] childBounds = calculateCanvasBounds(child);
                maxX = Math.max(maxX, childBounds[0]);
                maxY = Math.max(maxY, childBounds[1]);
            }
        }

        return new double[]{maxX, maxY};
    }

    /**
     * Recursively layouts nodes in a tree structure.
     */
    private double layoutNode(VisualNode node, double x, double y) {
        double nodeHeight = renderer.getNodeHeight();
        double vSpacing = renderer.getVerticalSpacing();
        double hSpacing = renderer.getHorizontalSpacing();

        // Check if this is a compositor node (sequence, choice, all)
        boolean isCompositor = node.getType() == XsdNodeRenderer.NodeWrapperType.SEQUENCE ||
                node.getType() == XsdNodeRenderer.NodeWrapperType.CHOICE ||
                node.getType() == XsdNodeRenderer.NodeWrapperType.ALL;

        // Use actual node dimensions (compositor symbols are smaller)
        double compositorSize = renderer.getCompositorSize();
        double actualWidth = isCompositor ? compositorSize : renderer.calculateNodeWidth(node);
        double actualHeight = isCompositor ? compositorSize : nodeHeight;

        // Position this node and set dimensions
        node.setX(x);
        node.setY(y);
        node.setWidth(actualWidth);
        node.setHeight(actualHeight);

        if (!node.isExpanded() || !node.hasChildren()) {
            return actualHeight;
        }

        // Calculate maximum width among all children for uniform sizing
        double maxChildWidth = 0;
        for (VisualNode child : node.getChildren()) {
            double childWidth = renderer.calculateNodeWidth(child);
            maxChildWidth = Math.max(maxChildWidth, childWidth);
        }

        // Layout children - use actual width for positioning
        double childX = x + actualWidth + hSpacing;
        double childY = y;
        double totalHeight = 0;

        for (VisualNode child : node.getChildren()) {
            // Pre-set uniform width for all siblings before layout
            boolean isChildCompositor = child.getType() == XsdNodeRenderer.NodeWrapperType.SEQUENCE ||
                    child.getType() == XsdNodeRenderer.NodeWrapperType.CHOICE ||
                    child.getType() == XsdNodeRenderer.NodeWrapperType.ALL;

            if (!isChildCompositor) {
                child.setWidth(maxChildWidth);
            }

            double childHeight = layoutNode(child, childX, childY);

            // Use smaller spacing after compositor symbols
            double spacingToUse = isCompositor ? vSpacing / 4 : vSpacing;

            childY += childHeight + spacingToUse;
            totalHeight += childHeight + spacingToUse;
        }

        totalHeight = Math.max(totalHeight - (isCompositor ? vSpacing / 4 : vSpacing), actualHeight);

        // Center parent vertically with children
        double centerOffset = (totalHeight - actualHeight) / 2;
        node.setY(y + centerOffset);

        return totalHeight;
    }

    /**
     * Recursively renders the tree.
     */
    private void renderTree(GraphicsContext gc, VisualNode node) {
        // Render node
        renderer.renderNode(gc, node, node.getX(), node.getY());

        // Render connections and children
        if (node.isExpanded()) {
            for (VisualNode child : node.getChildren()) {
                renderer.renderConnection(gc, node, child);
                renderTree(gc, child);
            }
        }
    }

    /**
     * Sets up mouse event handlers.
     */
    private void setupMouseHandlers() {
        canvas.setOnMouseClicked(this::handleMouseClick);
        canvas.setOnMouseMoved(this::handleMouseMove);

        // Setup keyboard shortcuts for zoom and properties toggle
        this.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case PLUS, EQUALS -> {
                        event.consume();
                        zoomIn();
                    }
                    case MINUS -> {
                        event.consume();
                        zoomOut();
                    }
                    case DIGIT0, NUMPAD0 -> {
                        event.consume();
                        zoomReset();
                    }
                    case P -> {
                        event.consume();
                        togglePropertiesPanel();
                    }
                }
            }
        });

        // Setup mouse wheel zoom
        canvas.setOnScroll(event -> {
            if (event.isControlDown()) {
                event.consume();
                double deltaY = event.getDeltaY();
                if (deltaY > 0) {
                    zoomIn();
                } else if (deltaY < 0) {
                    zoomOut();
                }
            }
        });

        // Make this component focusable for keyboard shortcuts
        this.setFocusTraversable(true);
    }

    /**
     * Handles mouse move events for hover detection.
     */
    private void handleMouseMove(MouseEvent event) {
        // Adjust coordinates for zoom level
        double x = event.getX() / zoomLevel;
        double y = event.getY() / zoomLevel;

        VisualNode nodeAtPosition = findNodeAt(rootNode, x, y);

        // Update hover state
        if (nodeAtPosition != hoveredNode) {
            // Clear previous hover
            if (hoveredNode != null) {
                hoveredNode.setHovered(false);
            }

            // Set new hover
            hoveredNode = nodeAtPosition;
            if (hoveredNode != null) {
                hoveredNode.setHovered(true);
            }

            // Redraw to show hover effect
            redraw();
        }
    }

    /**
     * Handles mouse clicks on the canvas.
     */
    private void handleMouseClick(MouseEvent event) {
        // Handle right-click for context menu
        if (event.getButton() == MouseButton.SECONDARY) {
            handleContextMenu(event);
            return;
        }

        // Handle left-click for selection
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        // Adjust coordinates for zoom level
        double x = event.getX() / zoomLevel;
        double y = event.getY() / zoomLevel;

        VisualNode clickedNode = findNodeAt(rootNode, x, y);

        if (clickedNode != null) {
            // Check if expand button was clicked
            if (clickedNode.expandButtonContainsPoint(x, y)) {
                clickedNode.toggleExpanded();
                redraw();
            } else {
                // Node body clicked - select it via SelectionModel
                if (event.isControlDown()) {
                    // Ctrl+Click: toggle selection
                    selectionModel.toggleSelection(clickedNode);
                } else if (event.isShiftDown() && !selectionModel.isEmpty()) {
                    // Shift+Click: add to selection
                    selectionModel.addToSelection(clickedNode);
                } else {
                    // Normal click: single selection
                    selectionModel.select(clickedNode);
                }
            }
        } else {
            // Clicked on empty space - clear selection
            selectionModel.clearSelection();
        }
    }

    /**
     * Handles right-click context menu.
     */
    private void handleContextMenu(MouseEvent event) {
        if (contextMenuFactory == null) {
            logger.warn("Context menu factory not initialized");
            return;
        }

        // Adjust coordinates for zoom level
        double x = event.getX() / zoomLevel;
        double y = event.getY() / zoomLevel;

        VisualNode clickedNode = findNodeAt(rootNode, x, y);

        // If node is clicked but not selected, select it first
        if (clickedNode != null && !selectionModel.isSelected(clickedNode)) {
            selectionModel.select(clickedNode);
        }

        // Create and show context menu
        ContextMenu contextMenu = contextMenuFactory.createContextMenu(clickedNode);
        contextMenu.show(canvas, event.getScreenX(), event.getScreenY());

        logger.debug("Context menu shown for node: {}", clickedNode != null ? clickedNode.getLabel() : "empty canvas");
    }

    /**
     * Finds the node at the given coordinates.
     */
    private VisualNode findNodeAt(VisualNode node, double x, double y) {
        if (node.containsPoint(x, y)) {
            return node;
        }

        if (node.isExpanded()) {
            for (VisualNode child : node.getChildren()) {
                VisualNode found = findNodeAt(child, x, y);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }


    /**
     * Expands all nodes recursively.
     */
    private void expandAll(VisualNode node) {
        if (node == null) return;
        node.setExpanded(true);
        for (VisualNode child : node.getChildren()) {
            expandAll(child);
        }
    }

    /**
     * Collapses all nodes recursively.
     */
    private void collapseAll(VisualNode node) {
        if (node == null) return;
        // Keep root expanded
        if (node != rootNode) {
            node.setExpanded(false);
        }
        for (VisualNode child : node.getChildren()) {
            collapseAll(child);
        }
    }

    /**
     * Sets the imported schemas for this graph view.
     * This should be called after loading the schema to provide access to imported schema elements.
     *
     * @param importedSchemas map of namespace/location to imported schema
     */
    public void setImportedSchemas(Map<String, org.fxt.freexmltoolkit.controls.v2.model.XsdSchema> importedSchemas) {
        this.importedSchemas = importedSchemas != null ? importedSchemas : new HashMap<>();
        logger.info("Set {} imported schemas", this.importedSchemas.size());
        // Rebuild visual tree to include imported elements
        rebuildVisualTree();
    }

    /**
     * Fits the view to show all content.
     */
    private void fitToView() {
        scrollPane.setHvalue(0);
        scrollPane.setVvalue(0);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        logger.debug("Model change detected: {}", evt.getPropertyName());
        javafx.application.Platform.runLater(() -> {
            buildVisualTree();
            redraw();
        });
    }

    /**
     * Zooms in by one step.
     */
    private void zoomIn() {
        setZoom(zoomLevel + ZOOM_STEP);
    }

    /**
     * Zooms out by one step.
     */
    private void zoomOut() {
        setZoom(zoomLevel - ZOOM_STEP);
    }

    /**
     * Resets zoom to 100%.
     */
    private void zoomReset() {
        setZoom(1.0);
    }

    /**
     * Toggles the visibility of the properties panel.
     * Triggered by Ctrl+P keyboard shortcut.
     */
    private void togglePropertiesPanel() {
        propertiesToggle.setSelected(!propertiesToggle.isSelected());
        propertiesToggle.fire();
        logger.debug("Properties panel toggled: {}", propertiesToggle.isSelected() ? "visible" : "hidden");
    }

    /**
     * Sets the zoom level and updates the canvas.
     */
    private void setZoom(double zoom) {
        zoomLevel = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom));

        // Update zoom label
        if (zoomLabel != null) {
            zoomLabel.setText(String.format("%.0f%%", zoomLevel * 100));
        }

        // Apply zoom to canvas
        canvas.setScaleX(zoomLevel);
        canvas.setScaleY(zoomLevel);

        logger.debug("Zoom level set to: {}%", zoomLevel * 100);
    }

    /**
     * Gets the selection model.
     *
     * @return the selection model
     */
    public SelectionModel getSelectionModel() {
        return selectionModel;
    }

    /**
     * Sets the editor context and registers listeners for edit mode changes.
     *
     * @param editorContext the editor context
     */
    public void setEditorContext(XsdEditorContext editorContext) {
        // Remove old listener if present
        if (this.editorContext != null) {
            this.editorContext.removePropertyChangeListener("editMode", this::handleEditModeChange);
        }

        this.editorContext = editorContext != null ? editorContext : new XsdEditorContext(xsdSchema);

        // Add listener for edit mode changes
        this.editorContext.addPropertyChangeListener("editMode", this::handleEditModeChange);
        // Update visual state immediately
        updateEditModeVisuals();

        // Reinitialize context menu factory with new context
        this.contextMenuFactory = new XsdContextMenuFactory(this.editorContext);

        // Re-apply stored callbacks to the new factory
        if (openComplexTypeEditorCallback != null) {
            logger.debug("Re-applying stored openComplexTypeEditorCallback to new contextMenuFactory");
            contextMenuFactory.setOpenComplexTypeEditorCallback(openComplexTypeEditorCallback);
        }
        if (openSimpleTypeEditorCallback != null) {
            logger.debug("Re-applying stored openSimpleTypeEditorCallback to new contextMenuFactory");
            contextMenuFactory.setOpenSimpleTypeEditorCallback(openSimpleTypeEditorCallback);
        }

        logger.debug("EditorContext set, editMode: {}", this.editorContext.isEditMode());
    }

    /**
     * Handles edit mode changes from EditorContext.
     */
    private void handleEditModeChange(PropertyChangeEvent evt) {
        logger.debug("Edit mode changed: {} -> {}", evt.getOldValue(), evt.getNewValue());
        updateEditModeVisuals();
        updateSaveButtonState();
    }

    /**
     * Updates the visual edit mode indicators for all selected nodes.
     */
    private void updateEditModeVisuals() {
        boolean editMode = editorContext != null && editorContext.isEditMode();

        // Update all selected nodes
        for (VisualNode node : selectionModel.getSelectedNodes()) {
            node.setInEditMode(editMode);
        }

        // Redraw to show changes
        redraw();
    }

    /**
     * Gets the XSD schema.
     *
     * @return the XSD schema
     */
    public XsdSchema getXsdSchema() {
        return xsdSchema;
    }

    /**
     * Gets the editor context.
     *
     * @return the editor context
     */
    public XsdEditorContext getEditorContext() {
        return editorContext;
    }

    /**
     * Sets the callback to be invoked when the Save button is clicked.
     *
     * @param callback the save callback (typically calls XsdController.handleSaveV2Editor())
     */
    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
        updateSaveButtonState();
    }

    /**
     * Sets the callback for opening a ComplexType in the Type Editor.
     * This is invoked when the user selects "Edit Type in Editor" from the context menu.
     *
     * @param callback the callback to open ComplexType editor
     */
    public void setOpenComplexTypeEditorCallback(java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.model.XsdComplexType> callback) {
        // Store callback locally so it survives contextMenuFactory recreation
        this.openComplexTypeEditorCallback = callback;
        logger.debug("setOpenComplexTypeEditorCallback: callback={}, contextMenuFactory={}",
                callback != null ? "set" : "null",
                contextMenuFactory != null ? "exists" : "null");

        if (contextMenuFactory != null) {
            contextMenuFactory.setOpenComplexTypeEditorCallback(callback);
        } else {
            logger.warn("setOpenComplexTypeEditorCallback: contextMenuFactory is null, callback stored for later");
        }
    }

    /**
     * Sets the callback for opening a SimpleType in the Type Editor.
     * This is invoked when the user selects "Edit Type in Editor" from the context menu.
     *
     * @param callback the callback to open SimpleType editor
     */
    public void setOpenSimpleTypeEditorCallback(java.util.function.Consumer<org.fxt.freexmltoolkit.controls.v2.model.XsdSimpleType> callback) {
        // Store callback locally so it survives contextMenuFactory recreation
        this.openSimpleTypeEditorCallback = callback;
        logger.debug("setOpenSimpleTypeEditorCallback: callback={}, contextMenuFactory={}",
                callback != null ? "set" : "null",
                contextMenuFactory != null ? "exists" : "null");

        if (contextMenuFactory != null) {
            contextMenuFactory.setOpenSimpleTypeEditorCallback(callback);
        } else {
            logger.warn("setOpenSimpleTypeEditorCallback: contextMenuFactory is null, callback stored for later");
        }
    }

    /**
     * Updates the enabled/disabled state of the Save button.
     * Button is enabled only when:
     * - A save callback is set
     * - Editor is in edit mode
     * - There is a schema to save
     */
    public void updateSaveButtonState() {
        if (saveButton != null) {
            boolean hasCallback = onSaveCallback != null;
            boolean inEditMode = editorContext != null && editorContext.isEditMode();
            boolean hasSchema = xsdSchema != null;

            saveButton.setDisable(!hasCallback || !inEditMode || !hasSchema);
        }
    }
}
