package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for adding an element to an XSD schema
 */
public class AddElementCommand implements XsdCommand {

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final String elementName;
    private final String elementType;
    private final String minOccurs;
    private final String maxOccurs;
    private Element addedElement;
    private Node parentDomNode;
    private Node nextSibling;

    public AddElementCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                             String elementName, String elementType, String minOccurs, String maxOccurs) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.elementName = elementName;
        this.elementType = elementType;
        this.minOccurs = minOccurs;
        this.maxOccurs = maxOccurs;
    }

    @Override
    public boolean execute() {
        try {
            // Store parent node and position for undo
            parentDomNode = domManipulator.findNodeByPath(parentNode.xpath());
            if (parentDomNode == null) {
                return false;
            }

            // Create new element
            addedElement = domManipulator.getDocument().createElement("xs:element");
            addedElement.setAttribute("name", elementName);

            if (elementType != null && !elementType.trim().isEmpty()) {
                addedElement.setAttribute("type", elementType);
            }

            if (minOccurs != null && !minOccurs.trim().isEmpty() && !"1".equals(minOccurs)) {
                addedElement.setAttribute("minOccurs", minOccurs);
            }

            if (maxOccurs != null && !maxOccurs.trim().isEmpty() && !"1".equals(maxOccurs)) {
                addedElement.setAttribute("maxOccurs", maxOccurs);
            }

            // Find appropriate insertion point
            nextSibling = findInsertionPoint(parentDomNode);

            // Insert element
            if (nextSibling != null) {
                parentDomNode.insertBefore(addedElement, nextSibling);
            } else {
                parentDomNode.appendChild(addedElement);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedElement != null && parentDomNode != null) {
                parentDomNode.removeChild(addedElement);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Add element '" + elementName + "'";
    }

    /**
     * Find appropriate insertion point for the new element
     */
    private Node findInsertionPoint(Node parent) {
        // Insert after other elements but before other types of nodes
        Node child = parent.getFirstChild();
        Node lastElement = null;

        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                if ("xs:element".equals(elem.getTagName())) {
                    lastElement = child;
                } else if (lastElement != null) {
                    // Found non-element after elements, insert before this
                    return child;
                }
            }
            child = child.getNextSibling();
        }

        // Insert at end if no specific position found
        return null;
    }
}