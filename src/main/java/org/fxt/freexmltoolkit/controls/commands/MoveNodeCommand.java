package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for moving XSD nodes in the schema structure
 */
public class MoveNodeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(MoveNodeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo sourceNode;
    private final XsdNodeInfo targetNode;

    // Backup for undo
    private Element originalParent;
    private Node originalNextSibling;
    private Element movedElement;

    public MoveNodeCommand(XsdDomManipulator domManipulator, XsdNodeInfo sourceNode, XsdNodeInfo targetNode) {
        this.domManipulator = domManipulator;
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Moving node '{}' to '{}'", sourceNode.name(), targetNode.name());

            // Find source element in DOM
            movedElement = domManipulator.findElementByXPath(sourceNode.xpath());
            if (movedElement == null) {
                logger.error("Source element not found: {}", sourceNode.xpath());
                return false;
            }

            // Find target element in DOM
            Element targetElement = domManipulator.findElementByXPath(targetNode.xpath());
            if (targetElement == null) {
                logger.error("Target element not found: {}", targetNode.xpath());
                return false;
            }

            // Store undo information
            originalParent = (Element) movedElement.getParentNode();
            originalNextSibling = movedElement.getNextSibling();

            if (originalParent == null) {
                logger.error("Source element has no parent");
                return false;
            }

            // Validate move operation
            if (!isValidMoveOperation(movedElement, targetElement)) {
                logger.warn("Invalid move operation: cannot move '{}' to '{}'", sourceNode.name(), targetNode.name());
                return false;
            }

            // Remove from current parent
            originalParent.removeChild(movedElement);

            // Add as sibling to target element (not as child!)
            Node targetParent = targetElement.getParentNode();
            if (targetParent != null) {
                // Insert after the target element
                Node nextSibling = targetElement.getNextSibling();
                if (nextSibling != null) {
                    targetParent.insertBefore(movedElement, nextSibling);
                } else {
                    targetParent.appendChild(movedElement);
                }
            } else {
                // Fallback: add as child if target has no parent
                targetElement.appendChild(movedElement);
            }

            logger.info("Successfully moved node '{}' to '{}'", sourceNode.name(), targetNode.name());
            return true;

        } catch (Exception e) {
            logger.error("Error moving node", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (originalParent == null || movedElement == null) {
                logger.warn("Cannot undo move operation - missing backup data");
                return false;
            }

            // Remove from current parent
            Node currentParent = movedElement.getParentNode();
            if (currentParent != null) {
                currentParent.removeChild(movedElement);
            }

            // Restore to original position
            if (originalNextSibling != null) {
                originalParent.insertBefore(movedElement, originalNextSibling);
            } else {
                originalParent.appendChild(movedElement);
            }

            logger.info("Successfully undid move operation for node '{}'", sourceNode.name());
            return true;

        } catch (Exception e) {
            logger.error("Error undoing move operation", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Move '" + sourceNode.name() + "' to '" + targetNode.name() + "'";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    /**
     * Validates if the move operation is structurally valid
     */
    private boolean isValidMoveOperation(Element sourceElement, Element targetElement) {
        // Prevent moving to itself
        if (sourceElement.equals(targetElement)) {
            return false;
        }

        // Prevent moving to a descendant (would create circular reference)
        Node current = targetElement;
        while (current != null) {
            if (current.equals(sourceElement)) {
                logger.warn("Cannot move element to its own descendant");
                return false;
            }
            current = current.getParentNode();
        }

        // Check XSD structural rules
        String sourceType = sourceElement.getLocalName();
        String targetType = targetElement.getLocalName();

        return isXsdStructurallyValid(sourceType, targetType);
    }

    /**
     * Validates XSD structural rules for move operations
     */
    private boolean isXsdStructurallyValid(String sourceType, String targetType) {
        // Elements can be moved into sequences, choices, or other elements
        if ("element".equals(sourceType)) {
            return "sequence".equals(targetType) ||
                    "choice".equals(targetType) ||
                    "element".equals(targetType);
        }

        // Attributes can be moved into elements
        if ("attribute".equals(sourceType)) {
            return "element".equals(targetType);
        }

        // Sequences and choices can be moved into elements
        if ("sequence".equals(sourceType) || "choice".equals(sourceType)) {
            return "element".equals(targetType);
        }

        return false;
    }
}