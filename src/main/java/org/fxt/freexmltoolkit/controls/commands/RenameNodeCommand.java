package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for renaming a node in an XSD schema
 */
public class RenameNodeCommand implements XsdCommand {

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo nodeToRename;
    private final String newName;
    private String oldName;
    private Node targetNode;

    public RenameNodeCommand(XsdDomManipulator domManipulator, XsdNodeInfo nodeToRename, String newName) {
        this.domManipulator = domManipulator;
        this.nodeToRename = nodeToRename;
        this.newName = newName;
        this.oldName = nodeToRename.name();
    }

    @Override
    public boolean execute() {
        try {
            // Find the node to rename
            targetNode = domManipulator.findNodeByPath(nodeToRename.xpath());
            if (targetNode == null || targetNode.getNodeType() != Node.ELEMENT_NODE) {
                return false;
            }

            Element element = (Element) targetNode;

            // Store old name for undo
            oldName = element.getAttribute("name");

            // Set new name
            element.setAttribute("name", newName);

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (targetNode != null && targetNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) targetNode;
                element.setAttribute("name", oldName);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Rename '" + oldName + "' to '" + newName + "'";
    }
}