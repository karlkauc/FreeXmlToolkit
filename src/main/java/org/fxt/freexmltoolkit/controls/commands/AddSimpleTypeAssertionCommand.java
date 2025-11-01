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
 * Command for adding an XSD 1.1 assertion (xs:assert) to a simpleType restriction.
 * Supports XPath 2.0 test expressions with optional namespace and documentation.
 * <p>
 * In simpleType assertions, the XPath context is limited to the value being validated,
 * accessed via the $value variable (XSD 1.1 spec).
 */
public class AddSimpleTypeAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddSimpleTypeAssertionCommand.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final String testExpression;
    private final String xpathDefaultNamespace;
    private final String documentation;

    private Element addedAssertion;
    private Node restrictionNode;
    private Element createdSimpleType;  // Track if we created inline simpleType for undo
    private String originalTypeAttribute;  // Store original type attribute for undo
    private Element elementWithTypeRef;  // Track element that had type reference

    public AddSimpleTypeAssertionCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
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
            // Find parent simpleType node in DOM
            Node parentDomNode = domManipulator.findNodeByPath(parentNode.xpath());
            if (parentDomNode == null || parentDomNode.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Parent node not found or not an element: {}", parentNode.xpath());
                return false;
            }

            Element parentElement = (Element) parentDomNode;
            String localName = parentElement.getLocalName();

            // Find the restriction element where we'll add the assertion
            Element restrictionElement = null;
            if ("simpleType".equals(localName)) {
                // Parent is a simpleType, find its restriction child
                restrictionElement = findRestrictionInSimpleType(parentElement);
                if (restrictionElement == null) {
                    logger.error("SimpleType does not have a restriction definition");
                    return false;
                }
            } else if ("element".equals(localName)) {
                // Parent is an element, find its simpleType/restriction
                Element simpleTypeElement = findSimpleTypeInElement(parentElement);

                // If no inline simpleType exists, check for type reference
                if (simpleTypeElement == null) {
                    String typeAttr = parentElement.getAttribute("type");
                    if (typeAttr != null && !typeAttr.isEmpty()) {
                        // Element has a type reference - convert to inline simpleType with restriction
                        logger.info("Converting element with type reference '{}' to inline simpleType", typeAttr);
                        simpleTypeElement = convertTypeReferenceToInlineSimpleType(parentElement, typeAttr);
                        if (simpleTypeElement == null) {
                            logger.error("Failed to convert type reference '{}' to inline simpleType", typeAttr);
                            return false;
                        }
                    }
                }

                if (simpleTypeElement != null) {
                    restrictionElement = findRestrictionInSimpleType(simpleTypeElement);
                }
                if (restrictionElement == null) {
                    logger.error("Element does not have a simpleType/restriction definition");
                    return false;
                }
            } else {
                logger.error("Assertions can only be added to simpleType or element nodes, not: {}", localName);
                return false;
            }

            restrictionNode = restrictionElement;

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

            // Insert assertion at the end of restriction (after all facets)
            // According to XSD 1.1 spec, assertions come after facets in restrictions
            restrictionElement.appendChild(addedAssertion);

            logger.info("Added assertion to simpleType restriction with test: {}", testExpression);
            return true;

        } catch (Exception e) {
            logger.error("Error adding assertion to simpleType", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedAssertion != null && restrictionNode != null) {
                restrictionNode.removeChild(addedAssertion);
                logger.info("Removed assertion from simpleType restriction");

                // If we created an inline simpleType, restore the original type reference
                if (createdSimpleType != null && elementWithTypeRef != null && originalTypeAttribute != null) {
                    elementWithTypeRef.removeChild(createdSimpleType);
                    elementWithTypeRef.setAttribute("type", originalTypeAttribute);
                    logger.info("Restored original type reference: {}", originalTypeAttribute);
                }

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
        return "Add simpleType assertion: " + (testExpression.length() > 30
                ? testExpression.substring(0, 30) + "..."
                : testExpression);
    }

    /**
     * Finds the xs:restriction child element within an xs:simpleType
     */
    private Element findRestrictionInSimpleType(Element simpleTypeElement) {
        org.w3c.dom.NodeList children = simpleTypeElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("restriction".equals(childElement.getLocalName()) &&
                        XSD_NS.equals(childElement.getNamespaceURI())) {
                    return childElement;
                }
            }
        }
        return null;
    }

    /**
     * Finds the xs:simpleType child element within an xs:element
     */
    private Element findSimpleTypeInElement(Element element) {
        org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if ("simpleType".equals(childElement.getLocalName()) &&
                        XSD_NS.equals(childElement.getNamespaceURI())) {
                    return childElement;
                }
            }
        }
        return null;
    }

    /**
     * Converts an element with a type reference to an inline simpleType with restriction.
     * This allows assertions to be added to elements that originally had type attributes.
     *
     * @param element       The element with a type attribute
     * @param typeReference The value of the type attribute (e.g., "xs:integer")
     * @return The created simpleType element, or null if conversion fails
     */
    private Element convertTypeReferenceToInlineSimpleType(Element element, String typeReference) {
        try {
            Document doc = element.getOwnerDocument();

            // Store original values for undo
            this.elementWithTypeRef = element;
            this.originalTypeAttribute = typeReference;

            // Create xs:simpleType element
            Element simpleType = doc.createElementNS(XSD_NS, "xs:simpleType");

            // Create xs:restriction element with base attribute
            Element restriction = doc.createElementNS(XSD_NS, "xs:restriction");
            restriction.setAttribute("base", typeReference);

            // Add restriction to simpleType
            simpleType.appendChild(restriction);

            // Remove type attribute from element
            element.removeAttribute("type");

            // Insert simpleType as first child of element (before any other children)
            Node firstChild = element.getFirstChild();
            if (firstChild != null) {
                element.insertBefore(simpleType, firstChild);
            } else {
                element.appendChild(simpleType);
            }

            // Track created simpleType for undo
            this.createdSimpleType = simpleType;

            logger.info("Converted type reference '{}' to inline simpleType with restriction", typeReference);
            return simpleType;

        } catch (Exception e) {
            logger.error("Error converting type reference to inline simpleType", e);
            return null;
        }
    }
}
