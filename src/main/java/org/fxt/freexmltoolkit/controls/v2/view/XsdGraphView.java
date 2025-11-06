package org.fxt.freexmltoolkit.controls.v2.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    private final XsdSchemaModel model;
    private final Canvas canvas;
    private final ScrollPane scrollPane;
    private final XsdNodeRenderer renderer;
    private final TextArea documentationArea;

    private VisualNode rootNode;
    private VisualNode selectedNode;
    private final Map<String, VisualNode> nodeMap = new HashMap<>();

    public XsdGraphView(XsdSchemaModel model) {
        this.model = model;
        this.renderer = new XsdNodeRenderer();

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

        // Setup UI
        setupLayout();

        // Build visual tree
        buildVisualTree();

        // Draw initial state
        redraw();

        // Register for model changes
        model.addPropertyChangeListener(this);

        // Setup mouse interaction
        setupMouseHandlers();

        logger.info("XsdGraphView initialized (graphical mode)");
    }

    /**
     * Sets up the UI layout.
     */
    private void setupLayout() {
        // Toolbar
        ToolBar toolbar = createToolbar();
        setTop(toolbar);

        // Documentation panel
        TitledPane docPane = new TitledPane("Documentation", documentationArea);
        docPane.setCollapsible(true);
        docPane.setExpanded(false);

        // Split pane
        SplitPane splitPane = new SplitPane(scrollPane, docPane);
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setDividerPositions(0.7);

        setCenter(splitPane);
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

        Label infoLabel = new Label("XSD Editor V2 (Beta) - Graphical View");
        infoLabel.setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getItems().addAll(expandAllBtn, collapseAllBtn, fitBtn, spacer, infoLabel);

        return toolbar;
    }

    /**
     * Builds the visual tree from the model.
     * Shows only global elements (not complex/simple types directly).
     * Types are resolved when elements reference them.
     */
    private void buildVisualTree() {
        nodeMap.clear();

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
     * Redraws the entire canvas.
     */
    private void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        if (rootNode == null) {
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
    }

    /**
     * Handles mouse clicks on the canvas.
     */
    private void handleMouseClick(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();

        VisualNode clickedNode = findNodeAt(rootNode, x, y);

        if (clickedNode != null) {
            // Check if expand button was clicked
            if (clickedNode.expandButtonContainsPoint(x, y)) {
                clickedNode.toggleExpanded();
                redraw();
            } else {
                // Node body clicked - select it
                selectedNode = clickedNode;
                showDocumentation(clickedNode);
            }
        }
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
}
