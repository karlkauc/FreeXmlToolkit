package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for moving a node down in the order within its parent
 */
public class MoveNodeDownCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(MoveNodeDownCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo nodeInfo;

    // Backup for undo
    private Element movedElement;
    private Node nextSibling;
    private Node parentNode;
    private boolean wasLast;

    public MoveNodeDownCommand(XsdDomManipulator domManipulator, XsdNodeInfo nodeInfo) {
        this.domManipulator = domManipulator;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Moving node down: {}", nodeInfo.name());

            // Find the element to move
            movedElement = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (movedElement == null) {
                logger.error("Element not found: {}", nodeInfo.xpath());
                return false;
            }

            parentNode = movedElement.getParentNode();
            if (parentNode == null) {
                logger.warn("Cannot move root element down");
                return false;
            }

            // Find the next sibling element (skip text nodes)
            Node currentNext = movedElement.getNextSibling();
            while (currentNext != null && currentNext.getNodeType() != Node.ELEMENT_NODE) {
                currentNext = currentNext.getNextSibling();
            }

            if (currentNext == null) {
                logger.info("Node is already at the bottom");
                return false; // Already last
            }

            // Store state for undo
            nextSibling = currentNext;
            wasLast = (getNextElementSibling(nextSibling) == null);

            // Move the element down by inserting it after its next sibling
            parentNode.removeChild(movedElement);
            Node afterNext = nextSibling.getNextSibling();
            if (afterNext != null) {
                parentNode.insertBefore(movedElement, afterNext);
            } else {
                parentNode.appendChild(movedElement);
            }

            logger.info("Successfully moved node '{}' down in order", nodeInfo.name());
            return true;

        } catch (Exception e) {
            logger.error("Error moving node down", e);
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
            if (wasLast) {
                // Insert as last child before the next sibling
                parentNode.insertBefore(movedElement, nextSibling);
            } else {
                // Insert before the next sibling
                parentNode.insertBefore(movedElement, nextSibling);
            }

            logger.info("Successfully undone move down operation for node: {}", nodeInfo.name());
            return true;

        } catch (Exception e) {
            logger.error("Error undoing move down operation", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Move '" + nodeInfo.name() + "' down";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    private Node getNextElementSibling(Node node) {
        Node next = node.getNextSibling();
        while (next != null && next.getNodeType() != Node.ELEMENT_NODE) {
            next = next.getNextSibling();
        }
        return next;
    }

    /**
     * Checks if the node can be moved down (i.e., it's not the last element)
     */
    public static boolean canMoveDown(XsdDomManipulator domManipulator, XsdNodeInfo nodeInfo) {
        try {
            Element element = domManipulator.findElementByXPath(nodeInfo.xpath());
            if (element == null) return false;

            // Check if there's a next element sibling
            Node next = element.getNextSibling();
            while (next != null && next.getNodeType() != Node.ELEMENT_NODE) {
                next = next.getNextSibling();
            }

            return next != null;
        } catch (Exception e) {
            return false;
        }
    }
}