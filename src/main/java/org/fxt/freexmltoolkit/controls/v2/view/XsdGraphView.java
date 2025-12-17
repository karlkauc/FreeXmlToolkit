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
import org.fxt.freexmltoolkit.controls.v2.model.IncludeSourceInfo;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.DeleteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.MoveNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.PasteNodeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.menu.XsdContextMenuFactory;
import org.fxt.freexmltoolkit.controls.v2.editor.panels.XsdPropertiesPanel;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.NodeWrapperType;
import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

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

    // Source file indicator for include support
    private Label currentSourceFileLabel;

    // Drag & Drop state
    private boolean isDragging = false;
    private VisualNode draggedNode = null;
    private VisualNode dropTarget = null;
    private double dragStartX = 0;
    private double dragStartY = 0;
    private static final double DRAG_THRESHOLD = 5.0; // Pixels before drag starts

    // Debounce for model change events (prevents multiple rebuilds from rapid events)
    private PauseTransition rebuildDebounce;
    private static final double DEBOUNCE_DELAY_MS = 50.0; // 50ms debounce delay

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
            // Update source file indicator
            updateSourceFileIndicator();
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

        // Note: Save button removed - use the Save button in the XSD tab toolbar instead

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

        // Source file indicator (shows current source file of selected node)
        currentSourceFileLabel = new Label("");
        currentSourceFileLabel.setStyle("-fx-padding: 5; -fx-text-fill: #0369a1; -fx-font-style: italic;");
        currentSourceFileLabel.setVisible(false);

        Label infoLabel = new Label("XSD Editor V2 - Graphical View");
        infoLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getItems().addAll(
                expandAllBtn, collapseAllBtn, fitBtn,
                separator,
                zoomInBtn, zoomOutBtn, zoomResetBtn, zoomLabel,
                currentSourceFileLabel,
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
        // Set flag to prevent PropertyChangeEvents from triggering rebuilds during initial build
        isRebuilding = true;
        try {
            nodeMap.clear();

            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            // Provide redraw callback for Model → View synchronization
            // IMPORTANT: Use this::redraw instead of this::rebuildVisualTree for performance!
            // VisualNode.updateFromModel() already updates the visual properties in-place.
            // We only need to redraw to show the changes.
            // Structural changes (add/delete) are handled separately by XsdGraphView.propertyChange()
            rootNode = builder.buildFromSchema(xsdSchema, this::redraw, importedSchemas);
            nodeMap.putAll(builder.getNodeMap());
            logger.debug("Visual tree built from XsdSchema with {} nodes (with redraw callback for property changes)", nodeMap.size());
        } finally {
            isRebuilding = false;
        }
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

            // 1. Save collapsed state (inverse logic - track what's collapsed, not expanded)
            // This ensures new nodes are expanded by default
            Set<String> collapsedNodeIds = new HashSet<>();
            collectCollapsedNodes(rootNode, collapsedNodeIds);
            logger.trace("Saved {} collapsed nodes", collapsedNodeIds.size());

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
            // Use this::redraw as callback - structural changes are handled by XsdGraphView.propertyChange()
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            rootNode = builder.buildFromSchema(xsdSchema, this::redraw, importedSchemas);
            nodeMap.clear();
            nodeMap.putAll(builder.getNodeMap());
            logger.debug("Visual tree rebuilt with {} nodes", nodeMap.size());
            logger.debug("Root node has {} children", rootNode.getChildren().size());

            // 4. Restore expansion state in single pass (optimized from double traversal)
            restoreExpansionState(rootNode, collapsedNodeIds);
            logger.trace("Restored expansion state (single pass O(n))");

            // 5. Restore selection using nodeMap for O(1) lookup instead of O(n) findNodeById
            selectionModel.clearSelection();
            for (String selectedId : selectedNodeIds) {
                // Use nodeMap for O(1) lookup instead of recursive findNodeById O(n)
                VisualNode nodeToSelect = nodeMap.get(selectedId);
                if (nodeToSelect != null) {
                    selectionModel.addToSelection(nodeToSelect);
                }
            }
            logger.trace("Restored selection state (using HashMap O(1) lookup)");

            // Note: Callback update removed - we now consistently use this::redraw
            // Structural changes are handled by XsdGraphView.propertyChange() which calls scheduleFullRebuild()

        } finally {
            isRebuilding = false;
        }

        // 7. Trigger redraw on JavaFX thread
        logger.debug("Triggering redraw after rebuild");
        javafx.application.Platform.runLater(this::redraw);
    }

    private boolean isRebuilding = false;

    /**
     * Recursively collects IDs of all COLLAPSED nodes (nodes that have children but are not expanded).
     * This inverse logic ensures that new nodes are expanded by default.
     */
    private void collectCollapsedNodes(VisualNode node, Set<String> collapsedIds) {
        if (node == null) return;

        // Only track nodes that have children but are collapsed
        if (node.hasChildren() && !node.isExpanded()) {
            Object modelObj = node.getModelObject();
            if (modelObj instanceof XsdNode xsdNode) {
                collapsedIds.add(xsdNode.getId());
            }
        }

        for (VisualNode child : node.getChildren()) {
            collectCollapsedNodes(child, collapsedIds);
        }
    }

    /**
     * Recursively restores expansion state in a single pass.
     * Expands nodes that were NOT in the collapsed set, collapses those that were.
     * This is an optimization over the previous two-pass approach (expandAll + applyCollapsed).
     *
     * @param node         the node to process
     * @param collapsedIds set of node IDs that should be collapsed
     */
    private void restoreExpansionState(VisualNode node, Set<String> collapsedIds) {
        if (node == null) return;

        // Determine expansion state: expand unless it was in the collapsed set
        Object modelObj = node.getModelObject();
        if (modelObj instanceof XsdNode xsdNode) {
            boolean shouldBeCollapsed = collapsedIds.contains(xsdNode.getId());
            node.setExpanded(!shouldBeCollapsed);
        } else {
            // Non-model nodes default to expanded
            node.setExpanded(true);
        }

        // Recursively process children
        for (VisualNode child : node.getChildren()) {
            restoreExpansionState(child, collapsedIds);
        }
    }

    /**
     * Recursively expands all nodes in the tree.
     * @deprecated Use restoreExpansionState() for better performance
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void expandAllNodes(VisualNode node) {
        if (node == null) return;
        node.setExpanded(true);
        for (VisualNode child : node.getChildren()) {
            expandAllNodes(child);
        }
    }

    /**
     * Recursively applies collapsed state based on saved node IDs.
     * Only collapses nodes that were previously collapsed.
     * @deprecated Use restoreExpansionState() for better performance
     */
    @Deprecated
    @SuppressWarnings("unused")
    private void applyCollapsedState(VisualNode node, Set<String> collapsedIds) {
        if (node == null) return;

        Object modelObj = node.getModelObject();
        if (modelObj instanceof XsdNode xsdNode) {
            if (collapsedIds.contains(xsdNode.getId())) {
                node.setExpanded(false);
            }
        }

        for (VisualNode child : node.getChildren()) {
            applyCollapsedState(child, collapsedIds);
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

        // Limit maximum canvas size to prevent JavaFX texture allocation errors
        // 8192 is a safe limit for most GPU configurations
        final double MAX_CANVAS_SIZE = 8192;
        if (canvasWidth > MAX_CANVAS_SIZE || canvasHeight > MAX_CANVAS_SIZE) {
            logger.warn("Canvas size {}x{} exceeds maximum. Limiting to {}x{}",
                    canvasWidth, canvasHeight, MAX_CANVAS_SIZE, MAX_CANVAS_SIZE);
            canvasWidth = Math.min(canvasWidth, MAX_CANVAS_SIZE);
            canvasHeight = Math.min(canvasHeight, MAX_CANVAS_SIZE);
        }

        // Resize canvas if needed - wrap in try-catch to handle GPU texture allocation failures
        try {
            if (canvas.getWidth() != canvasWidth || canvas.getHeight() != canvasHeight) {
                canvas.setWidth(canvasWidth);
                canvas.setHeight(canvasHeight);
                // logger.debug("Canvas resized to {}x{}", canvasWidth, canvasHeight);
            }

            // Clear canvas
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        } catch (Exception e) {
            logger.error("Failed to resize/clear canvas to {}x{}: {}", canvasWidth, canvasHeight, e.getMessage());
            // Try with smaller size as fallback
            try {
                canvas.setWidth(2000);
                canvas.setHeight(2000);
                gc.setFill(Color.WHITE);
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                logger.info("Fallback to smaller canvas size 2000x2000");
            } catch (Exception e2) {
                logger.error("Fallback canvas resize also failed: {}", e2.getMessage());
                return; // Cannot render
            }
        }

        // Render tree - wrap in try-catch to handle rendering failures gracefully
        try {
            renderTree(gc, rootNode);
        } catch (Exception e) {
            logger.error("Failed to render tree: {}. Schema may be too large for graphical display.", e.getMessage());
            // Show error message on canvas
            gc.setFill(Color.RED);
            gc.fillText("Error: Schema too large for graphical display. Try collapsing some nodes.", 50, 50);
            gc.fillText("Details: " + e.getMessage(), 50, 70);
        }
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

        // Setup drag & drop handlers
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);

        // Setup keyboard shortcuts for zoom, properties toggle, and node operations
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
                    case UP -> {
                        event.consume();
                        moveSelectedNodeUp();
                    }
                    case DOWN -> {
                        event.consume();
                        moveSelectedNodeDown();
                    }
                    case C -> {
                        event.consume();
                        copySelectedNode();
                    }
                    case X -> {
                        event.consume();
                        cutSelectedNode();
                    }
                    case V -> {
                        event.consume();
                        pasteToSelectedNode();
                    }
                }
            } else {
                // Non-Ctrl shortcuts
                switch (event.getCode()) {
                    case DELETE -> {
                        event.consume();
                        deleteSelectedNode();
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

        // Invalidate the main schema's cache to ensure imported elements are included in re-indexing
        // This is necessary because the first build (without imports) may have cached only the main schema elements
        if (!this.importedSchemas.isEmpty()) {
            XsdVisualTreeBuilder.invalidateCacheFor(xsdSchema);
            logger.debug("Invalidated cache for main schema to include imported schema elements");
        }

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
        // Skip events during rebuild to prevent infinite loops
        if (isRebuilding) {
            logger.trace("Ignoring property change during rebuild: {}", evt.getPropertyName());
            return;
        }

        String propertyName = evt.getPropertyName();
        logger.debug("Model change detected: {} (source: {})", propertyName,
                evt.getSource() instanceof XsdNode xsdNode ? xsdNode.getName() : evt.getSource().getClass().getSimpleName());

        // Distinguish between structural and property changes for performance optimization
        if (isStructuralChange(propertyName)) {
            // Structural changes (children, descendantChanged) require full tree rebuild
            scheduleFullRebuild();
        } else {
            // Property changes (name, type, documentation, appinfo, etc.) only need node update
            scheduleIncrementalUpdate(evt);
        }
    }

    /**
     * Checks if the property change is a structural change requiring full rebuild.
     * Structural changes: children added/removed, descendant structure changed
     * Non-structural: name, type, documentation, appinfo, minOccurs, maxOccurs, etc.
     */
    private boolean isStructuralChange(String propertyName) {
        return "children".equals(propertyName) || "descendantChanged".equals(propertyName);
    }

    /**
     * Schedules a full tree rebuild with debouncing.
     * Used for structural changes (add/delete children).
     */
    private void scheduleFullRebuild() {
        javafx.application.Platform.runLater(() -> {
            if (isRebuilding) {
                return;
            }
            if (rebuildDebounce == null) {
                rebuildDebounce = new PauseTransition(Duration.millis(DEBOUNCE_DELAY_MS));
                rebuildDebounce.setOnFinished(e -> {
                    logger.debug("Debounced full rebuild triggered");
                    // Invalidate cache for this schema since structure changed
                    XsdVisualTreeBuilder.invalidateCacheFor(xsdSchema);
                    rebuildVisualTree();
                    redraw();
                });
            }
            rebuildDebounce.playFromStart();
        });
    }

    /**
     * Schedules an incremental update for property changes.
     * Since VisualNode already updates itself via its PropertyChangeListener,
     * we just need to trigger a redraw to show the changes.
     * This is a significant performance optimization - no full tree rebuild needed!
     */
    private void scheduleIncrementalUpdate(PropertyChangeEvent evt) {
        javafx.application.Platform.runLater(() -> {
            if (isRebuilding) {
                return;
            }

            // VisualNode.updateFromModel() is already called by VisualNode's own listener
            // We just need to redraw to show the changes
            logger.debug("Incremental update (redraw only) for property '{}' on '{}'",
                    evt.getPropertyName(),
                    evt.getSource() instanceof XsdNode xsdNode ? xsdNode.getName() : "unknown");
            redraw();
        });
    }

    // Note: updateVisualNodeFromModel, buildNodeLabel, buildCardinalityString removed
    // VisualNode already has updateFromModel() which is called automatically
    // when the model fires PropertyChangeEvents. This is handled via the
    // PropertyChangeListener registered in VisualNode constructor.

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
     * Moves the selected node up (to a lower index among siblings).
     * Triggered by Ctrl+Up keyboard shortcut.
     */
    private void moveSelectedNodeUp() {
        if (editorContext == null || !editorContext.isEditMode()) {
            logger.debug("Cannot move node: not in edit mode");
            return;
        }

        VisualNode selected = selectionModel.getPrimarySelection();
        if (selected == null) {
            logger.debug("Cannot move node: no node selected");
            return;
        }

        Object modelObject = selected.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            XsdNode parent = xsdNode.getParent();
            if (parent != null) {
                int index = parent.getChildren().indexOf(xsdNode);
                if (index > 0) {
                    MoveNodeCommand command = new MoveNodeCommand(xsdNode, parent, index - 1);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Moved node '{}' up via Ctrl+Up", xsdNode.getName());
                } else {
                    logger.debug("Cannot move up: node is already at the top");
                }
            }
        }
    }

    /**
     * Moves the selected node down (to a higher index among siblings).
     * Triggered by Ctrl+Down keyboard shortcut.
     */
    private void moveSelectedNodeDown() {
        if (editorContext == null || !editorContext.isEditMode()) {
            logger.debug("Cannot move node: not in edit mode");
            return;
        }

        VisualNode selected = selectionModel.getPrimarySelection();
        if (selected == null) {
            logger.debug("Cannot move node: no node selected");
            return;
        }

        Object modelObject = selected.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            XsdNode parent = xsdNode.getParent();
            if (parent != null) {
                int index = parent.getChildren().indexOf(xsdNode);
                if (index >= 0 && index < parent.getChildren().size() - 1) {
                    MoveNodeCommand command = new MoveNodeCommand(xsdNode, parent, index + 1);
                    editorContext.getCommandManager().executeCommand(command);
                    logger.info("Moved node '{}' down via Ctrl+Down", xsdNode.getName());
                } else {
                    logger.debug("Cannot move down: node is already at the bottom");
                }
            }
        }
    }

    /**
     * Copies the selected node to the clipboard.
     * Triggered by Ctrl+C keyboard shortcut.
     */
    private void copySelectedNode() {
        VisualNode selected = selectionModel.getPrimarySelection();
        if (selected == null) {
            logger.debug("Cannot copy: no node selected");
            return;
        }

        Object modelObject = selected.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            editorContext.getClipboard().copy(xsdNode);
            logger.info("Copied node '{}' to clipboard via Ctrl+C", xsdNode.getName());
        }
    }

    /**
     * Cuts the selected node to the clipboard.
     * Triggered by Ctrl+X keyboard shortcut.
     */
    private void cutSelectedNode() {
        if (editorContext == null || !editorContext.isEditMode()) {
            logger.debug("Cannot cut node: not in edit mode");
            return;
        }

        VisualNode selected = selectionModel.getPrimarySelection();
        if (selected == null) {
            logger.debug("Cannot cut: no node selected");
            return;
        }

        Object modelObject = selected.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            editorContext.getClipboard().cut(xsdNode);
            logger.info("Cut node '{}' to clipboard via Ctrl+X", xsdNode.getName());
        }
    }

    /**
     * Pastes from clipboard to the selected node.
     * Triggered by Ctrl+V keyboard shortcut.
     */
    private void pasteToSelectedNode() {
        if (editorContext == null || !editorContext.isEditMode()) {
            logger.debug("Cannot paste: not in edit mode");
            return;
        }

        if (!editorContext.getClipboard().hasContent()) {
            logger.debug("Cannot paste: clipboard is empty");
            return;
        }

        VisualNode selected = selectionModel.getPrimarySelection();
        if (selected == null) {
            logger.debug("Cannot paste: no target node selected");
            return;
        }

        Object modelObject = selected.getModelObject();
        if (modelObject instanceof XsdNode targetParent) {
            PasteNodeCommand command = new PasteNodeCommand(editorContext.getClipboard(), targetParent);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Pasted node to '{}' via Ctrl+V", targetParent.getName());
        }
    }

    /**
     * Deletes the selected node.
     * Triggered by Delete key.
     */
    private void deleteSelectedNode() {
        if (editorContext == null || !editorContext.isEditMode()) {
            logger.debug("Cannot delete node: not in edit mode");
            return;
        }

        VisualNode selected = selectionModel.getPrimarySelection();
        if (selected == null) {
            logger.debug("Cannot delete: no node selected");
            return;
        }

        Object modelObject = selected.getModelObject();
        if (modelObject instanceof XsdNode xsdNode) {
            DeleteNodeCommand command = new DeleteNodeCommand(xsdNode);
            editorContext.getCommandManager().executeCommand(command);
            logger.info("Deleted node '{}' via Delete key", xsdNode.getName());
        }
    }

    // ==================== Drag & Drop Implementation ====================

    /**
     * Handles mouse press for initiating drag operations.
     * Stores the start position and the node under the mouse.
     */
    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        // Adjust coordinates for zoom level
        double x = event.getX() / zoomLevel;
        double y = event.getY() / zoomLevel;

        // Store drag start position
        dragStartX = x;
        dragStartY = y;

        // Find node at click position
        VisualNode nodeAtPosition = findNodeAt(rootNode, x, y);
        if (nodeAtPosition != null) {
            // Don't allow dragging if clicking on expand button
            if (!nodeAtPosition.expandButtonContainsPoint(x, y)) {
                draggedNode = nodeAtPosition;
            }
        }

        // Reset drag state
        isDragging = false;
        dropTarget = null;
    }

    /**
     * Handles mouse drag for drag operations.
     * Starts dragging when threshold is exceeded and highlights valid drop targets.
     */
    private void handleMouseDragged(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || draggedNode == null) {
            return;
        }

        // Check if we're in edit mode
        if (editorContext == null || !editorContext.isEditMode()) {
            return;
        }

        // Adjust coordinates for zoom level
        double x = event.getX() / zoomLevel;
        double y = event.getY() / zoomLevel;

        // Check if drag threshold has been exceeded
        double deltaX = Math.abs(x - dragStartX);
        double deltaY = Math.abs(y - dragStartY);

        if (!isDragging && (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD)) {
            // Start dragging
            isDragging = true;
            logger.debug("Started dragging node '{}'", draggedNode.getLabel());

            // Mark the dragged node visually
            draggedNode.setDragging(true);
        }

        if (isDragging) {
            // Find potential drop target
            VisualNode newDropTarget = findDropTargetAt(rootNode, x, y);

            // Update drop target highlighting
            if (newDropTarget != dropTarget) {
                // Clear old drop target highlight
                if (dropTarget != null) {
                    dropTarget.setDropTarget(false);
                }

                // Set new drop target highlight
                dropTarget = newDropTarget;
                if (dropTarget != null) {
                    dropTarget.setDropTarget(true);
                }

                redraw();
            }
        }
    }

    /**
     * Handles mouse release for completing drag operations.
     * Executes MoveNodeCommand if a valid drop target is selected.
     */
    private void handleMouseReleased(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }

        try {
            if (isDragging && draggedNode != null && dropTarget != null) {
                // Execute move operation
                Object draggedModelObj = draggedNode.getModelObject();
                Object dropTargetModelObj = dropTarget.getModelObject();

                if (draggedModelObj instanceof XsdNode draggedXsdNode &&
                        dropTargetModelObj instanceof XsdNode dropTargetXsdNode) {

                    // Determine the new parent - could be the drop target itself or its parent
                    XsdNode newParent = determineDropParent(dropTargetXsdNode);
                    int newIndex = determineDropIndex(draggedXsdNode, dropTargetXsdNode, newParent);

                    if (newParent != null && canDrop(draggedXsdNode, newParent)) {
                        MoveNodeCommand command = new MoveNodeCommand(draggedXsdNode, newParent, newIndex);
                        editorContext.getCommandManager().executeCommand(command);
                        logger.info("Dropped node '{}' onto '{}' at index {}",
                                draggedXsdNode.getName(), newParent.getName(), newIndex);
                    } else {
                        logger.debug("Cannot drop '{}' onto '{}' - invalid target",
                                draggedXsdNode.getName(), dropTargetXsdNode.getName());
                    }
                }
            }
        } finally {
            // Reset drag state
            if (draggedNode != null) {
                draggedNode.setDragging(false);
            }
            if (dropTarget != null) {
                dropTarget.setDropTarget(false);
            }

            isDragging = false;
            draggedNode = null;
            dropTarget = null;

            redraw();
        }
    }

    /**
     * Finds a valid drop target at the given coordinates.
     * A valid drop target is any node except:
     * - The dragged node itself
     * - Descendants of the dragged node
     */
    private VisualNode findDropTargetAt(VisualNode node, double x, double y) {
        if (node == null || node == draggedNode) {
            return null;
        }

        // Don't allow dropping onto descendants
        if (isDescendantOf(node, draggedNode)) {
            return null;
        }

        // Check if point is within this node
        if (node.containsPoint(x, y)) {
            return node;
        }

        // Search children if expanded
        if (node.isExpanded()) {
            for (VisualNode child : node.getChildren()) {
                VisualNode found = findDropTargetAt(child, x, y);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Checks if potentialDescendant is a descendant of potentialAncestor.
     */
    private boolean isDescendantOf(VisualNode potentialDescendant, VisualNode potentialAncestor) {
        if (potentialAncestor == null || potentialDescendant == null) {
            return false;
        }

        for (VisualNode child : potentialAncestor.getChildren()) {
            if (child == potentialDescendant) {
                return true;
            }
            if (isDescendantOf(potentialDescendant, child)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines the parent node for a drop operation.
     * If dropping onto a compositor (sequence, choice, all), returns the compositor.
     * If dropping onto an element with compositor children, returns the compositor.
     * Otherwise returns the node's parent for sibling insertion.
     */
    private XsdNode determineDropParent(XsdNode dropTargetXsdNode) {
        // If drop target is a compositor, drop into it
        if (dropTargetXsdNode instanceof XsdSequence ||
                dropTargetXsdNode instanceof XsdChoice ||
                dropTargetXsdNode instanceof XsdAll) {
            return dropTargetXsdNode;
        }

        // If drop target is a complexType, find its compositor child
        if (dropTargetXsdNode instanceof XsdComplexType complexType) {
            for (XsdNode child : complexType.getChildren()) {
                if (child instanceof XsdSequence || child instanceof XsdChoice || child instanceof XsdAll) {
                    return child;
                }
            }
            // No compositor found, drop into the complexType itself
            return dropTargetXsdNode;
        }

        // If drop target is an element with inline complexType, find its compositor
        if (dropTargetXsdNode instanceof XsdElement) {
            for (XsdNode child : dropTargetXsdNode.getChildren()) {
                if (child instanceof XsdComplexType) {
                    for (XsdNode complexChild : child.getChildren()) {
                        if (complexChild instanceof XsdSequence ||
                                complexChild instanceof XsdChoice ||
                                complexChild instanceof XsdAll) {
                            return complexChild;
                        }
                    }
                }
            }
        }

        // Default: make sibling by using drop target's parent
        return dropTargetXsdNode.getParent();
    }

    /**
     * Determines the index for inserting the dragged node.
     */
    private int determineDropIndex(XsdNode draggedNode, XsdNode dropTargetXsdNode, XsdNode newParent) {
        if (newParent == null) {
            return 0;
        }

        // If dropping into the drop target (not as sibling), append at end
        if (newParent == dropTargetXsdNode ||
                (dropTargetXsdNode instanceof XsdComplexType && newParent.getParent() == dropTargetXsdNode)) {
            return newParent.getChildren().size();
        }

        // Find index of drop target in its parent
        int targetIndex = newParent.getChildren().indexOf(dropTargetXsdNode);
        if (targetIndex < 0) {
            // Drop target not directly in parent, append at end
            return newParent.getChildren().size();
        }

        // Insert after the drop target
        return targetIndex + 1;
    }

    /**
     * Checks if the node can be dropped onto the given parent.
     */
    private boolean canDrop(XsdNode draggedXsdNode, XsdNode newParent) {
        // Cannot drop onto itself
        if (draggedXsdNode == newParent) {
            return false;
        }

        // Cannot drop onto descendants
        if (isModelDescendant(newParent, draggedXsdNode)) {
            return false;
        }

        // Check if parent can accept this type of child
        // Elements can be dropped into compositors (sequence, choice, all)
        if (draggedXsdNode instanceof XsdElement) {
            return newParent instanceof XsdSequence ||
                    newParent instanceof XsdChoice ||
                    newParent instanceof XsdAll ||
                    newParent instanceof XsdGroup;
        }

        // Attributes can be dropped into element, complexType, or attributeGroup
        if (draggedXsdNode instanceof XsdAttribute) {
            return newParent instanceof XsdElement ||
                    newParent instanceof XsdComplexType ||
                    newParent instanceof XsdAttributeGroup;
        }

        // Allow other node types to be moved to their parent type
        return true;
    }

    /**
     * Checks if potentialDescendant is a model descendant of potentialAncestor.
     */
    private boolean isModelDescendant(XsdNode potentialDescendant, XsdNode potentialAncestor) {
        if (potentialAncestor == null || potentialDescendant == null) {
            return false;
        }

        for (XsdNode child : potentialAncestor.getChildren()) {
            if (child == potentialDescendant) {
                return true;
            }
            if (isModelDescendant(potentialDescendant, child)) {
                return true;
            }
        }

        return false;
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
     * Updates the source file indicator label in the toolbar based on the currently selected node.
     * Shows the source file name if the node is from an included file.
     */
    private void updateSourceFileIndicator() {
        if (currentSourceFileLabel == null) {
            return;
        }

        VisualNode primarySelection = selectionModel.getPrimarySelection();
        if (primarySelection == null) {
            currentSourceFileLabel.setVisible(false);
            currentSourceFileLabel.setText("");
            return;
        }

        Object modelObject = primarySelection.getModelObject();
        logger.debug("updateSourceFileIndicator: modelObject={}, class={}",
                modelObject, modelObject != null ? modelObject.getClass().getSimpleName() : "null");

        if (modelObject instanceof XsdNode xsdNode) {
            IncludeSourceInfo sourceInfo = xsdNode.getSourceInfo();
            logger.debug("updateSourceFileIndicator: sourceInfo={}, isFromInclude={}",
                    sourceInfo, sourceInfo != null ? sourceInfo.isFromInclude() : "null");

            if (sourceInfo != null && sourceInfo.isFromInclude()) {
                String fileName = sourceInfo.getFileName();
                currentSourceFileLabel.setText("From: " + fileName);
                currentSourceFileLabel.setVisible(true);
                currentSourceFileLabel.setTooltip(new Tooltip(
                        "This element is defined in: " + sourceInfo.getSchemaLocation()));
                logger.info("Node '{}' is from included file: {}", xsdNode.getName(), fileName);
            } else {
                currentSourceFileLabel.setText("");
                currentSourceFileLabel.setVisible(false);
                logger.debug("Node '{}' is from main schema (sourceInfo={})",
                        xsdNode.getName(), sourceInfo);
            }
        } else {
            currentSourceFileLabel.setText("");
            currentSourceFileLabel.setVisible(false);
        }
    }
}
