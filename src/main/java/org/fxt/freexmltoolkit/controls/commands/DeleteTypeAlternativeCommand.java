package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for deleting an XSD 1.1 type alternative (xs:alternative) from an element.
 * Stores the deleted element for undo functionality.
 */
public class DeleteTypeAlternativeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteTypeAlternativeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo alternativeNode;

    private Element deletedAlternative;
    private Node parentNode;
    private Node nextSibling;

    public DeleteTypeAlternativeCommand(XsdDomManipulator domManipulator, XsdNodeInfo alternativeNode) {
        this.domManipulator = domManipulator;
        this.alternativeNode = alternativeNode;
    }

    @Override
    public boolean execute() {
        try {
            // Find type alternative element in DOM
            Node node = domManipulator.findNodeByPath(alternativeNode.xpath());
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Type alternative node not found: {}", alternativeNode.xpath());
                return false;
            }

            deletedAlternative = (Element) node;
            parentNode = deletedAlternative.getParentNode();
            nextSibling = deletedAlternative.getNextSibling();

            // Remove type alternative from parent element
            if (parentNode != null) {
                parentNode.removeChild(deletedAlternative);
                String type = deletedAlternative.getAttribute("type");
                logger.info("Deleted type alternative: {}", type != null && !type.isEmpty() ? type : "alternative");
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error deleting type alternative", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (deletedAlternative != null && parentNode != null) {
                // Re-insert at the same position
                if (nextSibling != null) {
                    parentNode.insertBefore(deletedAlternative, nextSibling);
                } else {
                    parentNode.appendChild(deletedAlternative);
                }

                logger.info("Restored deleted type alternative");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing type alternative deletion", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String type = alternativeNode.name();
        if (type != null && type.length() > 30) {
            type = type.substring(0, 30) + "...";
        }
        return "Delete type alternative: " + (type != null ? type : "alternative");
    }
}
