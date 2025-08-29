package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for adding an attribute to an XSD schema
 */
public class AddAttributeCommand implements XsdCommand {

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final String attributeName;
    private final String attributeType;
    private final String use;
    private Element addedAttribute;
    private Node parentDomNode;

    public AddAttributeCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                               String attributeName, String attributeType, String use) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.attributeName = attributeName;
        this.attributeType = attributeType;
        this.use = use;
    }

    @Override
    public boolean execute() {
        try {
            // Store parent node for undo
            parentDomNode = domManipulator.findNodeByPath(parentNode.xpath());
            if (parentDomNode == null) {
                return false;
            }

            // Create new attribute element
            addedAttribute = domManipulator.getDocument().createElement("xs:attribute");
            addedAttribute.setAttribute("name", attributeName);

            if (attributeType != null && !attributeType.trim().isEmpty()) {
                addedAttribute.setAttribute("type", attributeType);
            }

            if (use != null && !use.trim().isEmpty() && !"optional".equals(use)) {
                addedAttribute.setAttribute("use", use);
            }

            // Find appropriate insertion point (after elements, before other content)
            Node insertionPoint = findAttributeInsertionPoint(parentDomNode);

            // Insert attribute
            if (insertionPoint != null) {
                parentDomNode.insertBefore(addedAttribute, insertionPoint);
            } else {
                parentDomNode.appendChild(addedAttribute);
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedAttribute != null && parentDomNode != null) {
                parentDomNode.removeChild(addedAttribute);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Add attribute '" + attributeName + "'";
    }

    /**
     * Find appropriate insertion point for attributes
     * Attributes should come after elements but before other content
     */
    private Node findAttributeInsertionPoint(Node parent) {
        Node child = parent.getFirstChild();

        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String tagName = elem.getTagName();

                // Insert before complexType, simpleType, or other structural elements
                if ("xs:complexType".equals(tagName) || "xs:simpleType".equals(tagName) ||
                        "xs:restriction".equals(tagName) || "xs:extension".equals(tagName)) {
                    return child;
                }
            }
            child = child.getNextSibling();
        }

        return null; // Insert at end
    }
}