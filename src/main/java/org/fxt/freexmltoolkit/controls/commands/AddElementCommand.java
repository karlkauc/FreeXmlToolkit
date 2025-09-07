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
            // Use the XsdDomManipulator's createElement method which has proper logic
            // for handling different parent types (element, sequence, choice, etc.)
            addedElement = domManipulator.createElement(
                    parentNode.xpath(),
                    elementName,
                    elementType,
                    minOccurs,
                    maxOccurs
            );

            if (addedElement != null) {
                // Store reference to parent for undo operation
                parentDomNode = addedElement.getParentNode();
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
}