package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for modifying properties of XSD nodes
 */
public class ModifyPropertyCommand implements XsdCommand {

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo nodeInfo;
    private final String propertyName;
    private final String newValue;
    private String oldValue;
    private Node targetNode;

    public ModifyPropertyCommand(XsdDomManipulator domManipulator, XsdNodeInfo nodeInfo,
                                 String propertyName, String newValue) {
        this.domManipulator = domManipulator;
        this.nodeInfo = nodeInfo;
        this.propertyName = propertyName;
        this.newValue = newValue;
    }

    @Override
    public boolean execute() {
        try {
            // Find the target node
            targetNode = domManipulator.findNodeByPath(nodeInfo.xpath());
            if (targetNode == null || targetNode.getNodeType() != Node.ELEMENT_NODE) {
                return false;
            }

            Element element = (Element) targetNode;

            // Store old value for undo
            oldValue = element.getAttribute(propertyName);

            // Set new value
            if (newValue == null || newValue.trim().isEmpty()) {
                // Remove attribute if value is empty
                element.removeAttribute(propertyName);
            } else {
                element.setAttribute(propertyName, newValue);
            }

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

                if (oldValue == null || oldValue.trim().isEmpty()) {
                    // Remove attribute if old value was empty
                    element.removeAttribute(propertyName);
                } else {
                    element.setAttribute(propertyName, oldValue);
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
        String nodeName = nodeInfo.name();
        if (newValue == null || newValue.trim().isEmpty()) {
            return "Remove " + propertyName + " from '" + nodeName + "'";
        } else if (oldValue == null || oldValue.trim().isEmpty()) {
            return "Set " + propertyName + " of '" + nodeName + "' to '" + newValue + "'";
        } else {
            return "Change " + propertyName + " of '" + nodeName + "' from '" + oldValue + "' to '" + newValue + "'";
        }
    }
}