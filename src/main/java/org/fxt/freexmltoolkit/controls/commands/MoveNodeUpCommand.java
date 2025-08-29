package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for moving a node up in the order within its parent
 */
public class MoveNodeUpCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(MoveNodeUpCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo nodeInfo;

    // Backup for undo
    private Element movedElement;
    private Node previousSibling;
    private Node parentNode;
    private boolean wasFirst;

    public MoveNodeUpCommand(XsdDomManipulator domManipulator, XsdNodeInfo nodeInfo) {
        this.domManipulator = domManipulator;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Moving node up: {}", nodeInfo.name());

            // Find the element to move
            movedElement = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (movedElement == null) {
                logger.error("Element not found: {}", nodeInfo.xpath());
                return false;
            }

            parentNode = movedElement.getParentNode();
            if (parentNode == null) {
                logger.warn("Cannot move root element up");
                return false;
            }

            // Find the previous sibling element (skip text nodes)
            Node currentPrevious = movedElement.getPreviousSibling();
            while (currentPrevious != null && currentPrevious.getNodeType() != Node.ELEMENT_NODE) {
                currentPrevious = currentPrevious.getPreviousSibling();
            }

            if (currentPrevious == null) {
                logger.info("Node is already at the top");
                return false; // Already first
            }

            // Store state for undo
            previousSibling = currentPrevious;
            wasFirst = (previousSibling.getPreviousSibling() == null ||
                    getPreviousElementSibling(previousSibling) == null);

            // Move the element up by inserting it before its previous sibling
            parentNode.removeChild(movedElement);
            parentNode.insertBefore(movedElement, previousSibling);

            logger.info("Successfully moved node '{}' up in order", nodeInfo.name());
            return true;

        } catch (Exception e) {
            logger.error("Error moving node up", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (movedElement == null || parentNode == null) {
                logger.warn("No move operation to undo");
                return false;
            }

            // Remove the element from its current position
            parentNode.removeChild(movedElement);

            // Restore original position
            if (wasFirst) {
                // Insert as first child
                Node firstChild = parentNode.getFirstChild();
                if (firstChild != null) {
                    parentNode.insertBefore(movedElement, firstChild);
                } else {
                    parentNode.appendChild(movedElement);
                }
            } else {
                // Insert after the previous sibling
                Node nextSibling = previousSibling.getNextSibling();
                if (nextSibling != null) {
                    parentNode.insertBefore(movedElement, nextSibling);
                } else {
                    parentNode.appendChild(movedElement);
                }
            }

            logger.info("Successfully undone move up operation for node: {}", nodeInfo.name());
            return true;

        } catch (Exception e) {
            logger.error("Error undoing move up operation", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Move '" + nodeInfo.name() + "' up";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    private Node getPreviousElementSibling(Node node) {
        Node previous = node.getPreviousSibling();
        while (previous != null && previous.getNodeType() != Node.ELEMENT_NODE) {
            previous = previous.getPreviousSibling();
        }
        return previous;
    }

    /**
     * Checks if the node can be moved up (i.e., it's not the first element)
     */
    public static boolean canMoveUp(XsdDomManipulator domManipulator, XsdNodeInfo nodeInfo) {
        try {
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) return false;

            // Check if there's a previous element sibling
            Node previous = element.getPreviousSibling();
            while (previous != null && previous.getNodeType() != Node.ELEMENT_NODE) {
                previous = previous.getPreviousSibling();
            }

            return previous != null;
        } catch (Exception e) {
            return false;
        }
    }
}