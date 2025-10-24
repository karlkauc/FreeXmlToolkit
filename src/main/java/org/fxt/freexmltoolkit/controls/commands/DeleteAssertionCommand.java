package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for deleting an XSD 1.1 assertion (xs:assert) from a complexType.
 * Stores the deleted element for undo functionality.
 */
public class DeleteAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteAssertionCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo assertionNode;

    private Element deletedAssertion;
    private Node parentNode;
    private Node nextSibling;

    public DeleteAssertionCommand(XsdDomManipulator domManipulator, XsdNodeInfo assertionNode) {
        this.domManipulator = domManipulator;
        this.assertionNode = assertionNode;
    }

    @Override
    public boolean execute() {
        try {
            // Find assertion element in DOM
            Node node = domManipulator.findNodeByPath(assertionNode.xpath());
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Assertion node not found: {}", assertionNode.xpath());
                return false;
            }

            deletedAssertion = (Element) node;
            parentNode = deletedAssertion.getParentNode();
            nextSibling = deletedAssertion.getNextSibling();

            // Remove assertion from parent
            if (parentNode != null) {
                parentNode.removeChild(deletedAssertion);
                logger.info("Deleted assertion: {}", assertionNode.xpathExpression());
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error deleting assertion", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (deletedAssertion != null && parentNode != null) {
                // Re-insert at the same position
                if (nextSibling != null) {
                    parentNode.insertBefore(deletedAssertion, nextSibling);
                } else {
                    parentNode.appendChild(deletedAssertion);
                }

                logger.info("Restored deleted assertion");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing assertion deletion", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String expr = assertionNode.xpathExpression();
        if (expr != null && expr.length() > 30) {
            expr = expr.substring(0, 30) + "...";
        }
        return "Delete assertion: " + (expr != null ? expr : "assertion");
    }
}
