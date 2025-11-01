package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for deleting an XSD 1.1 assertion (xs:assert) from a simpleType restriction.
 * Stores the deleted element for undo functionality.
 */
public class DeleteSimpleTypeAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(DeleteSimpleTypeAssertionCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo assertionNode;

    private Element deletedAssertion;
    private Node parentNode;
    private Node nextSibling;

    public DeleteSimpleTypeAssertionCommand(XsdDomManipulator domManipulator, XsdNodeInfo assertionNode) {
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

            // Remove assertion from parent (restriction)
            if (parentNode != null) {
                parentNode.removeChild(deletedAssertion);
                logger.info("Deleted simpleType assertion: {}", assertionNode.xpathExpression());
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error deleting simpleType assertion", e);
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

                logger.info("Restored deleted simpleType assertion");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing simpleType assertion deletion", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        String expr = assertionNode.xpathExpression();
        if (expr != null && expr.length() > 30) {
            expr = expr.substring(0, 30) + "...";
        }
        return "Delete simpleType assertion: " + (expr != null ? expr : "assertion");
    }
}
