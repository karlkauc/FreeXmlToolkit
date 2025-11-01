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
 * Command for adding an XSD 1.1 assertion (xs:assert) to a complexType.
 * Supports XPath 2.0 test expressions with optional namespace and documentation.
 */
public class AddAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddAssertionCommand.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final String testExpression;
    private final String xpathDefaultNamespace;
    private final String documentation;

    private Element addedAssertion;
    private Node parentDomNode;

    public AddAssertionCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                               String testExpression, String xpathDefaultNamespace, String documentation) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.testExpression = testExpression;
        this.xpathDefaultNamespace = xpathDefaultNamespace;
        this.documentation = documentation;
    }

    @Override
    public boolean execute() {
        try {
            // Find parent complexType node in DOM
            parentDomNode = domManipulator.findNodeByPath(parentNode.xpath());
            if (parentDomNode == null || parentDomNode.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Parent node not found or not an element: {}", parentNode.xpath());
                return false;
            }

            Element parentElement = (Element) parentDomNode;
            String localName = parentElement.getLocalName();

            // Find the complexType element where we'll add the assertion
            Element complexTypeElement;
            if ("complexType".equals(localName)) {
                // Parent is already a complexType (global or inline)
                logger.debug("Adding assertion to complexType: {}", parentElement.getAttribute("name"));
                complexTypeElement = parentElement;
            } else if ("element".equals(localName)) {
                // Parent is an element, find its inline complexType child
                complexTypeElement = findComplexTypeInElement(parentElement);
                if (complexTypeElement == null) {
                    String elementName = parentElement.getAttribute("name");
                    String typeRef = parentElement.getAttribute("type");

                    if (typeRef != null && !typeRef.isEmpty()) {
                        logger.error("Element '{}' uses a type reference '{}'. Assertions can only be added to elements with inline complexType definitions or to global complexTypes directly.",
                                elementName, typeRef);
                    } else {
                        logger.error("Element '{}' does not have an inline complexType definition", elementName);
                    }
                    return false;
                }
                logger.debug("Adding assertion to inline complexType of element: {}", parentElement.getAttribute("name"));
            } else {
                logger.error("Assertions can only be added to complexType or element nodes, not: {}", localName);
                return false;
            }

            // Create xs:assert element
            Document doc = domManipulator.getDocument();
            addedAssertion = doc.createElementNS(XSD_NS, "xs:assert");
            addedAssertion.setAttribute("test", testExpression);

            // Add optional xpath-default-namespace
            if (xpathDefaultNamespace != null && !xpathDefaultNamespace.isEmpty()) {
                addedAssertion.setAttribute("xpath-default-namespace", xpathDefaultNamespace);
            }

            // Add optional documentation
            if (documentation != null && !documentation.isEmpty()) {
                Element annotation = doc.createElementNS(XSD_NS, "xs:annotation");
                Element docElement = doc.createElementNS(XSD_NS, "xs:documentation");
                docElement.setTextContent(documentation);
                annotation.appendChild(docElement);
                addedAssertion.appendChild(annotation);
            }

            // Insert assertion at the end of complexType (after all content)
            // According to XSD 1.1 spec, assertions come after the content model
            complexTypeElement.appendChild(addedAssertion);

            logger.info("Added assertion with test: {}", testExpression);
            return true;

        } catch (Exception e) {
            logger.error("Error adding assertion", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedAssertion != null && parentDomNode != null) {
                parentDomNode.removeChild(addedAssertion);
                logger.info("Removed assertion");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing add assertion", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Add assertion: " + (testExpression.length() > 30
                ? testExpression.substring(0, 30) + "..."
                : testExpression);
    }

    /**
     * Finds the complexType child element within an xs:element
     */
    private Element findComplexTypeInElement(Element element) {
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("complexType".equals(childElement.getLocalName()) &&
                        XSD_NS.equals(childElement.getNamespaceURI())) {
                    return childElement;
                }
            }
        }
        return null;
    }
}
