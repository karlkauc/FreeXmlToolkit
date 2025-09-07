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
            // Use the XsdDomManipulator's createAttribute method which has proper logic
            // for handling different parent types and attribute positioning
            addedAttribute = domManipulator.createAttribute(
                    parentNode.xpath(),
                    attributeName,
                    attributeType,
                    use,
                    null // defaultValue
            );

            if (addedAttribute != null) {
                // Store reference to parent for undo operation
                parentDomNode = addedAttribute.getParentNode();
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
}