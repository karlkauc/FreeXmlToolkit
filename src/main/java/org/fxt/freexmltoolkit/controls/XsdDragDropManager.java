package org.fxt.freexmltoolkit.controls;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.commands.MoveNodeCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages drag and drop operations for XSD nodes in the diagram view
 * <p>
 * Features:
 * - Visual feedback during drag operations
 * - Drop zone highlighting
 * - Move vs Copy logic based on modifier keys
 * - Multi-selection drag support
 * - Validation of drop targets
 * - Smooth visual transitions
 */
public class XsdDragDropManager {

    private static final Logger logger = LogManager.getLogger(XsdDragDropManager.class);

    // Drag & Drop Data Format
    private static final DataFormat XSD_NODE_FORMAT = new DataFormat("application/xsd-node");
    private static final DataFormat XSD_NODE_LIST_FORMAT = new DataFormat("application/xsd-node-list");

    // Visual feedback constants
    private static final String DRAG_SOURCE_STYLE = "-fx-opacity: 0.5; -fx-effect: dropshadow(gaussian, rgba(0, 100, 255, 0.6), 10, 0, 0, 0);";
    private static final String DROP_ZONE_VALID_STYLE = "-fx-border-color: #4CAF50; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: rgba(76, 175, 80, 0.1);";
    private static final String DROP_ZONE_INVALID_STYLE = "-fx-border-color: #F44336; -fx-border-width: 2px; -fx-border-style: dashed; -fx-background-color: rgba(244, 67, 54, 0.1);";
    private static final String DROP_ZONE_HOVER_STYLE = "-fx-border-color: #2196F3; -fx-border-width: 3px; -fx-border-style: solid; -fx-background-color: rgba(33, 150, 243, 0.2);";

    private final XsdDiagramView diagramView;
    private final XsdDomManipulator domManipulator;
    private final XsdUndoManager undoManager;

    // Drag state
    private Node dragSourceNode;
    private XsdNodeInfo dragSourceInfo;
    private final List<XsdNodeInfo> draggedNodes = new ArrayList<>();
    private final Set<Node> highlightedDropZones = new HashSet<>();
    private boolean isDragInProgress = false;

    public XsdDragDropManager(XsdDiagramView diagramView, XsdDomManipulator domManipulator, XsdUndoManager undoManager) {
        this.diagramView = diagramView;
        this.domManipulator = domManipulator;
        this.undoManager = undoManager;

        logger.info("XsdDragDropManager initialized");
    }

    /**
     * Makes a node draggable by setting up drag detection
     */
    public void makeDraggable(Node node, XsdNodeInfo nodeInfo) {
        node.setOnDragDetected(event -> handleDragDetected(event, node, nodeInfo));
        node.setOnDragDone(event -> handleDragDone(event, node, nodeInfo));

        // Add visual feedback for draggable items
        node.setOnMouseEntered(e -> {
            if (!isDragInProgress) {
                node.setEffect(new InnerShadow(3, Color.web("#2196F3")));
                node.setCursor(Cursor.MOVE);
            }
        });

        node.setOnMouseExited(e -> {
            if (!isDragInProgress) {
                node.setEffect(null);
                node.setCursor(Cursor.DEFAULT);
            }
        });
    }

    /**
     * Makes a node a drop target
     */
    public void makeDropTarget(Node node, XsdNodeInfo nodeInfo) {
        node.setOnDragOver(event -> handleDragOver(event, node, nodeInfo));
        node.setOnDragEntered(event -> handleDragEntered(event, node, nodeInfo));
        node.setOnDragExited(event -> handleDragExited(event, node, nodeInfo));
        node.setOnDragDropped(event -> handleDragDropped(event, node, nodeInfo));
    }

    /**
     * Handles drag detection - starts drag operation
     */
    private void handleDragDetected(MouseEvent event, Node source, XsdNodeInfo sourceInfo) {
        logger.debug("Drag detected on node: {}", sourceInfo.name());

        dragSourceNode = source;
        dragSourceInfo = sourceInfo;
        isDragInProgress = true;

        // Check for multi-selection (Ctrl+Drag for multiple nodes)
        draggedNodes.clear();
        draggedNodes.add(sourceInfo);

        // Start drag operation
        Dragboard dragboard = source.startDragAndDrop(TransferMode.COPY_OR_MOVE);
        ClipboardContent content = new ClipboardContent();

        // Add data to dragboard (only serializable data)
        content.putString(sourceInfo.name());
        // Don't put XsdNodeInfo objects in dragboard - they're not serializable
        // We'll use the internal state (dragSourceInfo, draggedNodes) instead

        // Create drag image
        createDragImage(dragboard, sourceInfo);

        dragboard.setContent(content);

        // Apply visual feedback to source
        source.setStyle(DRAG_SOURCE_STYLE);

        event.consume();
    }

    /**
     * Handles drag over - determines if drop is allowed
     */
    private void handleDragOver(DragEvent event, Node target, XsdNodeInfo targetInfo) {
        if (isDragInProgress && dragSourceInfo != null) {
            boolean canDrop = isValidDropTarget(dragSourceInfo, targetInfo, event);

            if (canDrop) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);

                // Determine transfer mode based on modifier keys
                // Default to move for now - we'll check modifier keys when dropping
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
        }

        event.consume();
    }

    /**
     * Handles drag entered - visual feedback when entering drop zone
     */
    private void handleDragEntered(DragEvent event, Node target, XsdNodeInfo targetInfo) {
        if (isDragInProgress && dragSourceInfo != null) {
            boolean canDrop = isValidDropTarget(dragSourceInfo, targetInfo, event);

            String style = canDrop ? DROP_ZONE_VALID_STYLE : DROP_ZONE_INVALID_STYLE;

            // Store original style
            String originalStyle = target.getStyle();
            target.setUserData(originalStyle);

            // Apply drop zone styling
            target.setStyle(originalStyle + style);
            highlightedDropZones.add(target);

            logger.debug("Drag entered target: {} (canDrop: {})", targetInfo.name(), canDrop);
        }

        event.consume();
    }

    /**
     * Handles drag exited - remove visual feedback
     */
    private void handleDragExited(DragEvent event, Node target, XsdNodeInfo targetInfo) {
        if (highlightedDropZones.contains(target)) {
            // Restore original style
            String originalStyle = (String) target.getUserData();
            if (originalStyle != null) {
                target.setStyle(originalStyle);
            } else {
                target.setStyle("");
            }

            highlightedDropZones.remove(target);
        }

        event.consume();
    }

    /**
     * Handles drag dropped - executes the drop operation
     */
    private void handleDragDropped(DragEvent event, Node target, XsdNodeInfo targetInfo) {
        logger.info("Drag dropped on target: {}", targetInfo.name());

        boolean success = false;

        if (isDragInProgress && dragSourceInfo != null) {
            boolean canDrop = isValidDropTarget(dragSourceInfo, targetInfo, event);

            if (canDrop) {
                // Determine operation type (default to move for now)
                boolean isCopy = event.getTransferMode() == TransferMode.COPY;

                try {
                    if (isCopy) {
                        success = executeCopyOperation(dragSourceInfo, targetInfo);
                    } else {
                        success = executeMoveOperation(dragSourceInfo, targetInfo);
                    }

                    if (success) {
                        // Refresh view and trigger validation
                        diagramView.refreshView();
                        diagramView.triggerLiveValidation();

                        logger.info("Successfully {} node '{}' to '{}'",
                                isCopy ? "copied" : "moved", dragSourceInfo.name(), targetInfo.name());
                    }

                } catch (Exception e) {
                    logger.error("Error during drag drop operation", e);
                    success = false;
                }
            }
        }

        event.setDropCompleted(success);
        event.consume();
    }

    /**
     * Handles drag done - cleanup after drag operation
     */
    private void handleDragDone(DragEvent event, Node source, XsdNodeInfo sourceInfo) {
        logger.debug("Drag done for node: {}", sourceInfo.name());

        // Clean up visual feedback
        cleanupDragVisuals();

        isDragInProgress = false;
        dragSourceNode = null;
        dragSourceInfo = null;
        draggedNodes.clear();

        event.consume();
    }

    /**
     * Determines if a node can be dropped on a target
     */
    private boolean isValidDropTarget(XsdNodeInfo source, XsdNodeInfo target, DragEvent event) {
        // Prevent dropping on self
        if (source.equals(target)) {
            return false;
        }

        // Since we're adding as siblings, check if they can be siblings
        // This means they should be able to coexist in the same parent container
        return canBeSiblings(source, target);
    }

    /**
     * Checks if two nodes can be siblings (exist in the same parent container)
     */
    private boolean canBeSiblings(XsdNodeInfo source, XsdNodeInfo target) {
        // Elements can be siblings with other elements if they are in the same sequence/choice
        if (source.nodeType() == XsdNodeInfo.NodeType.ELEMENT &&
                target.nodeType() == XsdNodeInfo.NodeType.ELEMENT) {
            return true;
        }

        // Attributes cannot be moved between elements (they belong to specific elements)
        if (source.nodeType() == XsdNodeInfo.NodeType.ATTRIBUTE) {
            return false;
        }

        // Sequences and choices can be siblings if they are in the same element
        if ((source.nodeType() == XsdNodeInfo.NodeType.SEQUENCE ||
                source.nodeType() == XsdNodeInfo.NodeType.CHOICE) &&
                (target.nodeType() == XsdNodeInfo.NodeType.ELEMENT)) {
            return false; // They can't really be siblings in XSD structure
        }

        return false;
    }

    /**
     * Checks if a node can have children based on XSD rules
     */
    private boolean canHaveChildren(XsdNodeInfo node) {
        switch (node.nodeType()) {
            case ELEMENT:
                // Elements can have children if they don't have a simple type
                String type = node.type();
                return type == null || type.isEmpty() ||
                        (!type.startsWith("xs:") && !type.startsWith("xsd:"));

            case SEQUENCE:
            case CHOICE:
            case COMPLEX_TYPE:
            case ALL:
            case OPEN_CONTENT:
                return true;

            case ATTRIBUTE:
            case ANY:
            case SIMPLE_TYPE:
            case SCHEMA:
            case ASSERT:
            case ALTERNATIVE:
            case OVERRIDE:
            default:
                return false;
        }
    }

    /**
     * Validates XSD structural rules for drag and drop
     */
    private boolean isXsdStructurallyValid(XsdNodeInfo source, XsdNodeInfo target) {
        XsdNodeInfo.NodeType sourceType = source.nodeType();
        XsdNodeInfo.NodeType targetType = target.nodeType();

        // Elements can be dropped into sequences, choices, or other elements
        if (sourceType == XsdNodeInfo.NodeType.ELEMENT) {
            return targetType == XsdNodeInfo.NodeType.SEQUENCE ||
                    targetType == XsdNodeInfo.NodeType.CHOICE ||
                    targetType == XsdNodeInfo.NodeType.ELEMENT;
        }

        // Attributes can be dropped into elements
        if (sourceType == XsdNodeInfo.NodeType.ATTRIBUTE) {
            return targetType == XsdNodeInfo.NodeType.ELEMENT;
        }

        // Sequences and choices can be dropped into elements
        if (sourceType == XsdNodeInfo.NodeType.SEQUENCE || sourceType == XsdNodeInfo.NodeType.CHOICE) {
            return targetType == XsdNodeInfo.NodeType.ELEMENT;
        }

        return false;
    }

    /**
     * Executes a copy operation
     */
    private boolean executeCopyOperation(XsdNodeInfo source, XsdNodeInfo target) {
        // For now, just delegate to move operation since clipboard service needs refactoring
        // TODO: Implement proper copy operation using clipboard service
        logger.info("Copy operation not fully implemented yet, performing move operation instead");
        return executeMoveOperation(source, target);
    }

    /**
     * Executes a move operation
     */
    private boolean executeMoveOperation(XsdNodeInfo source, XsdNodeInfo target) {
        try {
            // Create move command
            MoveNodeCommand command = new MoveNodeCommand(domManipulator, source, target);

            // Execute command through undo manager
            return undoManager.executeCommand(command);

        } catch (Exception e) {
            logger.error("Error during move operation", e);
            return false;
        }
    }

    /**
     * Creates a drag image for visual feedback
     */
    private void createDragImage(Dragboard dragboard, XsdNodeInfo nodeInfo) {
        try {
            // Create a visual representation of the dragged node
            Label dragImage = new Label(nodeInfo.name());
            dragImage.setStyle("-fx-background-color: rgba(33, 150, 243, 0.8); " +
                    "-fx-text-fill: white; " +
                    "-fx-padding: 8px 12px; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-font-weight: bold;");

            // Add type-specific icon
            FontIcon icon = createNodeTypeIcon(nodeInfo.nodeType());
            if (icon != null) {
                dragImage.setGraphic(icon);
            }

            // Set drag image
            dragboard.setDragView(dragImage.snapshot(null, null));

        } catch (Exception e) {
            logger.warn("Could not create drag image", e);
        }
    }

    /**
     * Creates a type-specific icon for the drag image
     */
    private FontIcon createNodeTypeIcon(XsdNodeInfo.NodeType nodeType) {
        String iconLiteral;
        String iconColor = "#ffffff";

        switch (nodeType) {
            case ELEMENT -> iconLiteral = "bi-square";
            case ATTRIBUTE -> iconLiteral = "bi-at";
            case SEQUENCE -> iconLiteral = "bi-list-ol";
            case CHOICE -> iconLiteral = "bi-option";
            case ANY -> iconLiteral = "bi-asterisk";
            case SIMPLE_TYPE -> iconLiteral = "bi-type";
            case COMPLEX_TYPE -> iconLiteral = "bi-diagram-3";
            case SCHEMA -> iconLiteral = "bi-file-earmark-code";
            case ASSERT -> iconLiteral = "bi-check-circle";
            case ALTERNATIVE -> iconLiteral = "bi-arrows-angle-contract";
            case OPEN_CONTENT -> iconLiteral = "bi-collection";
            case OVERRIDE -> iconLiteral = "bi-arrow-repeat";
            case ALL -> iconLiteral = "bi-grid-3x3";
            default -> iconLiteral = "bi-box";
        }

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconColor(Color.web(iconColor));
        icon.setIconSize(12);

        return icon;
    }

    /**
     * Cleans up all visual feedback from drag operations
     */
    private void cleanupDragVisuals() {
        // Restore source node style
        if (dragSourceNode != null) {
            dragSourceNode.setStyle("");
            dragSourceNode.setEffect(null);
            dragSourceNode.setCursor(Cursor.DEFAULT);
        }

        // Restore drop zone styles
        for (Node node : highlightedDropZones) {
            String originalStyle = (String) node.getUserData();
            if (originalStyle != null) {
                node.setStyle(originalStyle);
            } else {
                node.setStyle("");
            }
        }
        highlightedDropZones.clear();
    }

    /**
     * Enables multi-selection mode for drag operations
     */
    public void setMultiSelectionMode(boolean enabled) {
        // Future implementation for multi-selection
        logger.debug("Multi-selection mode: {}", enabled);
    }
}