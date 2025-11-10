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

    private final XsdSchemaModel model;  // Old model (may be null for XsdSchema-based views)
    private final XsdSchema xsdSchema;   // New XsdNode-based model (may be null for XsdSchemaModel-based views)
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final XsdNodeRenderer renderer;
    private final TextArea documentationArea;
    private final SelectionModel selectionModel;
    private XsdEditorContext editorContext;
    private XsdContextMenuFactory contextMenuFactory;
    private XsdPropertiesPanel propertiesPanel;
    private SplitPane mainSplitPane;
    private SplitPane rightPanel;
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

    /**
     * Constructor using the old XsdSchemaModel (flat structure).
     *
     * @deprecated Use XsdGraphView(XsdSchema) instead for better integration with XsdNode model
     */
    @Deprecated
    public XsdGraphView(XsdSchemaModel model) {
        this.model = model;
        this.xsdSchema = null;
        this.renderer = new XsdNodeRenderer();
        this.selectionModel = new SelectionModel();

        // Create canvas for drawing
        this.canvas = new Canvas(2000, 2000);
        this.scrollPane = new ScrollPane(canvas);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: white;");

        // Documentation panel
        this.documentationArea = new TextArea();
        documentationArea.setEditable(false);
        documentationArea.setWrapText(true);
        documentationArea.setPrefHeight(150);
        documentationArea.setPromptText("Select a node to view documentation...");

        // Initialize with default EditorContext (view mode)
        this.editorContext = new XsdEditorContext(model);
        this.contextMenuFactory = new XsdContextMenuFactory(editorContext);

        initializeUI();
    }

    /**
     * Constructor using the new XsdSchema (XsdNode-based tree structure).
     * This is the preferred constructor for direct XsdNode model integration.
     *
     * @param schema the XSD schema to visualize
     * @since 2.0
     */
    public XsdGraphView(XsdSchema schema) {
        this.xsdSchema = schema;
        this.model = null;
        this.renderer = new XsdNodeRenderer();
        this.selectionModel = new SelectionModel();

        // Create canvas for drawing
        this.canvas = new Canvas(2000, 2000);
        this.scrollPane = new ScrollPane(canvas);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: white;");

        // Documentation panel
        this.documentationArea = new TextArea();
        documentationArea.setEditable(false);
        documentationArea.setWrapText(true);
        documentationArea.setPrefHeight(150);
        documentationArea.setPromptText("Select a node to view documentation...");

        // Create a temporary model for EditorContext compatibility
        // TODO: Refactor EditorContext to work with XsdSchema directly
        XsdSchemaModel tempModel = new XsdSchemaModel();
        if (schema.getTargetNamespace() != null) {
            tempModel.setTargetNamespace(schema.getTargetNamespace());
        }
        this.editorContext = new XsdEditorContext(tempModel);
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
            // Update legacy selectedNode reference for documentation
            if (!newSelection.isEmpty()) {
                selectedNode = newSelection.iterator().next();
                showDocumentation(selectedNode);
            } else {
                selectedNode = null;
                documentationArea.clear();
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
        if (model != null) {
            model.addPropertyChangeListener(this);
        } else if (xsdSchema != null) {
            xsdSchema.addPropertyChangeListener(this);
        }

        // Setup mouse interaction
        setupMouseHandlers();

        logger.info("XsdGraphView initialized (graphical mode) using {}", model != null ? "XsdSchemaModel" : "XsdSchema");
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

        // Documentation panel
        TitledPane docPane = new TitledPane("Documentation", documentationArea);
        docPane.setCollapsible(true);
        docPane.setExpanded(false);

        // Right side: vertical split with properties and documentation
        rightPanel = new SplitPane(propertiesPanel, docPane);
        rightPanel.setOrientation(javafx.geometry.Orientation.VERTICAL);
        rightPanel.setDividerPositions(0.6);

        // Main split pane: horizontal split with canvas and right panel
        mainSplitPane = new SplitPane(scrollPane, rightPanel);
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
                // Show right panel
                if (!mainSplitPane.getItems().contains(rightPanel)) {
                    mainSplitPane.getItems().add(rightPanel);
                    mainSplitPane.setDividerPositions(0.7);
                }
            } else {
                // Hide right panel
                mainSplitPane.getItems().remove(rightPanel);
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
     * Builds the visual tree from the model.
     * Shows only global elements (not complex/simple types directly).
     * Types are resolved when elements reference them.
     */
    private void buildVisualTree() {
        nodeMap.clear();

        // Use new XsdSchema-based builder if available
        if (xsdSchema != null) {
            XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
            // Provide rebuild callback for Model → View synchronization
            // For structural changes (add/delete child), we need to rebuild the tree
            rootNode = builder.buildFromSchema(xsdSchema, this::rebuildVisualTree);
            nodeMap.putAll(builder.getNodeMap());
            logger.debug("Visual tree built from XsdSchema with {} nodes (with auto-rebuild on model changes)", nodeMap.size());
            return;
        }

        // Fallback to old XsdSchemaModel-based builder
        if (model.getGlobalElements().isEmpty()) {
            // No global elements - create empty schema node
            rootNode = new VisualNode(
                    "Schema: " + (model.getTargetNamespace() != null ? model.getTargetNamespace() : "empty"),
                    "No elements",
                    NodeWrapperType.SCHEMA,
                    model,
                    null
            );
            nodeMap.put(model.getId(), rootNode);
            logger.debug("Visual tree built with no elements");
            return;
        }

        if (model.getGlobalElements().size() == 1) {
            // Single root element - use it directly as root node
            XsdElementModel rootElement = model.getGlobalElements().get(0);
            rootNode = createElementNode(rootElement, null, new HashSet<>());
            rootNode.setExpanded(true);
            logger.debug("Visual tree built with single root element: {}", rootElement.getName());
        } else {
            // Multiple global elements - create schema node as root
            rootNode = new VisualNode(
                    "Schema: " + (model.getTargetNamespace() != null ? model.getTargetNamespace() : "default"),
                    model.getGlobalElements().size() + " elements",
                    NodeWrapperType.SCHEMA,
                    model,
                    null
            );
            rootNode.setExpanded(true);
            nodeMap.put(model.getId(), rootNode);

            // Add global elements only (types will be resolved on demand)
            for (XsdElementModel element : model.getGlobalElements()) {
                VisualNode elementNode = createElementNode(element, rootNode, new HashSet<>());
                rootNode.addChild(elementNode);
            }

            logger.debug("Visual tree built with {} global elements", rootNode.getChildren().size());
        }
    }

    /**
     * Creates a visual node for an element.
     * Resolves type references to show the structure of referenced types.
     *
     * @param element      The element model to create a node for
     * @param parent       The parent visual node
     * @param visitedTypes Set of type names already visited in the current path (to prevent infinite recursion)
     * @return The created visual node
     */
    private VisualNode createElementNode(XsdElementModel element, VisualNode parent, Set<String> visitedTypes) {
        String label = element.getName();
        String detail = "";

        if (element.getType() != null) {
            detail = element.getType();
        }
        if (element.getMinOccurs() != 1 || element.getMaxOccurs() != 1) {
            detail += " [" + element.getMinOccurs() + ".." +
                    (element.getMaxOccurs() == Integer.MAX_VALUE ? "*" : element.getMaxOccurs()) + "]";
        }

        VisualNode node = new VisualNode(label, detail, NodeWrapperType.ELEMENT, element, parent,
                element.getMinOccurs(), element.getMaxOccurs());
        nodeMap.put(element.getId(), node);

        // First priority: Compositors (sequence, choice, all)
        boolean hasCompositors = !element.getCompositors().isEmpty();
        if (hasCompositors) {
            for (XsdCompositorModel compositor : element.getCompositors()) {
                node.addChild(createCompositorNode(compositor, node, visitedTypes));
            }
        }

        // Second priority: inline child elements (without compositors)
        boolean hasInlineChildren = !element.getChildren().isEmpty();
        if (hasInlineChildren && !hasCompositors) {
            for (XsdElementModel child : element.getChildren()) {
                node.addChild(createElementNode(child, node, visitedTypes));
            }
        }

        // Add inline attributes
        for (XsdAttributeModel attr : element.getAttributes()) {
            node.addChild(createAttributeNode(attr, node));
        }

        // If no inline content and element has a type reference, resolve the type
        if (!hasInlineChildren && !hasCompositors && element.getType() != null) {
            resolveTypeReference(element.getType(), node, visitedTypes);
        }

        return node;
    }

    /**
     * Resolves a type reference and adds its structure to the parent node.
     * Prevents infinite recursion by tracking visited types on the current path.
     *
     * @param typeRef      the type reference (e.g. "ControlDataType" or "xs:string")
     * @param parentNode   the parent node to add children to
     * @param visitedTypes set of type names already visited in the current path
     */
    private void resolveTypeReference(String typeRef, VisualNode parentNode, Set<String> visitedTypes) {
        if (typeRef == null || typeRef.isEmpty()) {
            return;
        }

        // Skip built-in XML Schema types
        if (typeRef.startsWith("xs:") || typeRef.startsWith("xsd:")) {
            return;
        }

        // Remove namespace prefix if present
        String typeName = typeRef;
        if (typeName.contains(":")) {
            typeName = typeName.substring(typeName.indexOf(":") + 1);
        }

        // Check if this type is already being processed (circular reference)
        if (visitedTypes.contains(typeName)) {
            logger.warn("Recursion detected: type '{}' is already being processed. Aborting to prevent infinite loop.", typeName);
            return;
        }

        // Add type to visited set
        visitedTypes.add(typeName);

        try {
            // Try to resolve as complex type
            XsdComplexTypeModel complexType = model.getGlobalComplexTypes().get(typeName);
            if (complexType != null) {
                // First, add compositors from complex type
                for (XsdCompositorModel compositor : complexType.getCompositors()) {
                    parentNode.addChild(createCompositorNode(compositor, parentNode, visitedTypes));
                }

                // Add elements from complex type (if no compositors)
                if (complexType.getCompositors().isEmpty()) {
                    for (XsdElementModel child : complexType.getElements()) {
                        parentNode.addChild(createElementNode(child, parentNode, visitedTypes));
                    }
                }

                // Add attributes from complex type
                for (XsdAttributeModel attr : complexType.getAttributes()) {
                    parentNode.addChild(createAttributeNode(attr, parentNode));
                }
                logger.debug("Resolved complex type '{}' for element '{}'", typeName,
                        ((XsdElementModel) parentNode.getModelObject()).getName());
                return;
            }

            // Try to resolve as simple type (simple types don't have children, so nothing to add)
            XsdSimpleTypeModel simpleType = model.getGlobalSimpleTypes().get(typeName);
            if (simpleType != null) {
                logger.debug("Element '{}' has simple type '{}' (no children)",
                        ((XsdElementModel) parentNode.getModelObject()).getName(), typeName);
            }
        } finally {
            // Remove type from visited set when done (backtrack)
            visitedTypes.remove(typeName);
        }
    }

    /**
     * Creates a visual node for an attribute.
     */
    private VisualNode createAttributeNode(XsdAttributeModel attribute, VisualNode parent) {
        String label = "@" + attribute.getName();
        String detail = attribute.getType() != null ? attribute.getType() : "";
        if (attribute.isRequired()) {
            detail += " (required)";
        }

        VisualNode node = new VisualNode(label, detail, NodeWrapperType.ATTRIBUTE, attribute, parent);
        nodeMap.put(attribute.getId(), node);

        return node;
    }

    /**
     * Creates a visual node for a compositor (sequence, choice, all).
     *
     * @param compositor   The compositor model
     * @param parent       The parent visual node
     * @param visitedTypes Set of type names already visited in the current path (to prevent infinite recursion)
     * @return The created visual node
     */
    private VisualNode createCompositorNode(XsdCompositorModel compositor, VisualNode parent, Set<String> visitedTypes) {
        // Determine node type based on compositor type
        NodeWrapperType nodeType = switch (compositor.getType()) {
            case SEQUENCE -> NodeWrapperType.SEQUENCE;
            case CHOICE -> NodeWrapperType.CHOICE;
            case ALL -> NodeWrapperType.ALL;
        };

        String label = compositor.getLabel();
        String detail = "";

        // Add cardinality if not default (1..1)
        if (compositor.getMinOccurs() != 1 || compositor.getMaxOccurs() != 1) {
            detail = "[" + compositor.getMinOccurs() + ".." +
                    (compositor.getMaxOccurs() == Integer.MAX_VALUE ? "*" : compositor.getMaxOccurs()) + "]";
        }

        VisualNode node = new VisualNode(label, detail, nodeType, compositor, parent,
                compositor.getMinOccurs(), compositor.getMaxOccurs());
        nodeMap.put(compositor.getId(), node);

        // Compositor nodes are always expanded to show their children directly
        node.setExpanded(true);

        // Add all children (elements and compositors) in document order
        for (Object child : compositor.getChildrenInOrder()) {
            if (child instanceof XsdElementModel element) {
                node.addChild(createElementNode(element, node, visitedTypes));
            } else if (child instanceof XsdCompositorModel nestedCompositor) {
                node.addChild(createCompositorNode(nestedCompositor, node, visitedTypes));
            }
        }

        logger.debug("Created compositor node '{}' with {} children in document order (auto-expanded)",
                compositor.getLabel(), compositor.getChildrenInOrder().size());

        return node;
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

            // 3. Rebuild tree from model
            // Temporarily use this::redraw to avoid recursion during rebuild
            if (xsdSchema != null) {
                XsdVisualTreeBuilder builder = new XsdVisualTreeBuilder();
                rootNode = builder.buildFromSchema(xsdSchema, this::redraw);
                nodeMap.clear();
                nodeMap.putAll(builder.getNodeMap());
                logger.debug("Visual tree rebuilt with {} nodes", nodeMap.size());
                logger.debug("Root node has {} children", rootNode.getChildren().size());
            } else if (model != null) {
                buildVisualTree();
            }

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
        logger.debug("redraw() called, rootNode has {} children", rootNode != null ? rootNode.getChildren().size() : "null");
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
            logger.debug("Canvas resized to {}x{}", canvasWidth, canvasHeight);
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
     * Shows documentation for the selected node.
     */
    private void showDocumentation(VisualNode node) {
        if (node == null || node.getModelObject() == null) {
            documentationArea.clear();
            return;
        }

        StringBuilder doc = new StringBuilder();
        Object modelObj = node.getModelObject();

        if (modelObj instanceof XsdElementModel element) {
            doc.append("Element: ").append(element.getName()).append("\n");
            doc.append("Type: ").append(element.getType() != null ? element.getType() : "inline").append("\n");
            doc.append("Cardinality: ").append(element.getMinOccurs()).append("..")
                    .append(element.getMaxOccurs() == Integer.MAX_VALUE ? "unbounded" : element.getMaxOccurs()).append("\n");
            if (element.getDocumentation() != null) {
                doc.append("\n").append(element.getDocumentation());
            }
        } else if (modelObj instanceof XsdAttributeModel attr) {
            doc.append("Attribute: ").append(attr.getName()).append("\n");
            doc.append("Type: ").append(attr.getType() != null ? attr.getType() : "string").append("\n");
            doc.append("Use: ").append(attr.isRequired() ? "required" : "optional").append("\n");
            if (attr.getDocumentation() != null) {
                doc.append("\n").append(attr.getDocumentation());
            }
        } else if (modelObj instanceof XsdComplexTypeModel ct) {
            doc.append("Complex Type: ").append(ct.getName()).append("\n");
            doc.append("Elements: ").append(ct.getElements().size()).append("\n");
            doc.append("Attributes: ").append(ct.getAttributes().size()).append("\n");
            if (ct.getDocumentation() != null) {
                doc.append("\n").append(ct.getDocumentation());
            }
        } else if (modelObj instanceof XsdSimpleTypeModel st) {
            doc.append("Simple Type: ").append(st.getName()).append("\n");
            doc.append("Base Type: ").append(st.getBaseType() != null ? st.getBaseType() : "none").append("\n");
            if (st.getDocumentation() != null) {
                doc.append("\n").append(st.getDocumentation());
            }
        }

        documentationArea.setText(doc.toString());
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

        this.editorContext = editorContext != null ? editorContext : new XsdEditorContext(model);

        // Add listener for edit mode changes
        this.editorContext.addPropertyChangeListener("editMode", this::handleEditModeChange);
        // Update visual state immediately
        updateEditModeVisuals();

        // Reinitialize context menu factory with new context
        this.contextMenuFactory = new XsdContextMenuFactory(this.editorContext);

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
}
