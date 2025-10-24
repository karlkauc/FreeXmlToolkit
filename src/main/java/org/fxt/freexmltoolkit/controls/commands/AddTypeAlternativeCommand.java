package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for adding an XSD 1.1 type alternative (xs:alternative) to an element.
 * Supports conditional type assignment based on XPath 2.0 test expressions.
 */
public class AddTypeAlternativeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddTypeAlternativeCommand.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final String testExpression;  // Can be null for default alternative
    private final String typeName;
    private final String documentation;

    private Element addedAlternative;
    private Node parentDomNode;

    public AddTypeAlternativeCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                                     String testExpression, String typeName, String documentation) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.testExpression = testExpression;
        this.typeName = typeName;
        this.documentation = documentation;
    }

    @Override
    public boolean execute() {
        try {
            // Find parent element node in DOM
            parentDomNode = domManipulator.findNodeByPath(parentNode.xpath());
            if (parentDomNode == null || parentDomNode.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Parent node not found or not an element: {}", parentNode.xpath());
                return false;
            }

            Element parentElement = (Element) parentDomNode;
            String localName = parentElement.getLocalName();

            // Validate that parent is an element
            if (!"element".equals(localName)) {
                logger.error("Type alternatives can only be added to element definitions");
                return false;
            }

            // Create xs:alternative element
            Document doc = domManipulator.getDocument();
            addedAlternative = doc.createElementNS(XSD_NS, "xs:alternative");

            // Add test expression (optional - if null, it's the default alternative)
            if (testExpression != null && !testExpression.isEmpty()) {
                addedAlternative.setAttribute("test", testExpression);
            }

            // Add type attribute
            if (typeName != null && !typeName.isEmpty()) {
                addedAlternative.setAttribute("type", typeName);
            }

            // Add optional documentation
            if (documentation != null && !documentation.isEmpty()) {
                Element annotation = doc.createElementNS(XSD_NS, "xs:annotation");
                Element docElement = doc.createElementNS(XSD_NS, "xs:documentation");
                docElement.setTextContent(documentation);
                annotation.appendChild(docElement);
                addedAlternative.appendChild(annotation);
            }

            // Insert alternative as child of element
            // Alternatives should come after simpleType/complexType but before unique/key/keyref
            parentElement.appendChild(addedAlternative);

            logger.info("Added type alternative: {}", typeName);
            return true;

        } catch (Exception e) {
            logger.error("Error adding type alternative", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedAlternative != null && parentDomNode != null) {
                parentDomNode.removeChild(addedAlternative);
                logger.info("Removed type alternative");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing add type alternative", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        if (testExpression != null && !testExpression.isEmpty()) {
            String expr = testExpression.length() > 20
                    ? testExpression.substring(0, 20) + "..."
                    : testExpression;
            return "Add alternative: " + typeName + " when " + expr;
        } else {
            return "Add default alternative: " + typeName;
        }
    }
}
