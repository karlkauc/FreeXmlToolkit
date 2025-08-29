package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Node;

/**
 * Command for deleting a node from an XSD schema
 */
public class DeleteNodeCommand implements XsdCommand {

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo nodeToDelete;
    private Node deletedNode;
    private Node parentNode;
    private Node nextSibling;

    public DeleteNodeCommand(XsdDomManipulator domManipulator, XsdNodeInfo nodeToDelete) {
        this.domManipulator = domManipulator;
        this.nodeToDelete = nodeToDelete;
    }

    @Override
    public boolean execute() {
        try {
            // Find the node to delete
            deletedNode = domManipulator.findNodeByPath(nodeToDelete.xpath());
            if (deletedNode == null) {
                return false;
            }

            // Store parent and position for undo
            parentNode = deletedNode.getParentNode();
            nextSibling = deletedNode.getNextSibling();

            // Remove the node
            if (parentNode != null) {
                parentNode.removeChild(deletedNode);
                return true;
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (deletedNode != null && parentNode != null) {
                // Restore the node at its original position
                if (nextSibling != null) {
                    parentNode.insertBefore(deletedNode, nextSibling);
                } else {
                    parentNode.appendChild(deletedNode);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        String nodeName = nodeToDelete.name();
        String nodeType = getNodeTypeDescription();
        return "Delete " + nodeType + " '" + nodeName + "'";
    }

    /**
     * Get human-readable description of node type
     */
    private String getNodeTypeDescription() {
        String type = nodeToDelete.type();
        switch (type) {
            case "element":
                return "element";
            case "attribute":
                return "attribute";
            case "complexType":
                return "complex type";
            case "simpleType":
                return "simple type";
            case "sequence":
                return "sequence";
            case "choice":
                return "choice";
            case "all":
                return "all";
            case "group":
                return "group";
            case "attributeGroup":
                return "attribute group";
            default:
                return "node";
        }
    }
}