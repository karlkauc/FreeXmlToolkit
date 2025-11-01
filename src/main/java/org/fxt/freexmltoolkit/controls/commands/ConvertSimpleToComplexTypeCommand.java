package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command for converting an element with simpleType/simpleContent to complexType.
 * This allows adding assertions to elements that previously only had simple content.
 */
public class ConvertSimpleToComplexTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ConvertSimpleToComplexTypeCommand.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo elementNode;

    // Store old structure for undo
    private Element oldTypeElement;
    private Node parentNode;
    private Node nextSibling;

    public ConvertSimpleToComplexTypeCommand(XsdDomManipulator domManipulator, XsdNodeInfo elementNode) {
        this.domManipulator = domManipulator;
        this.elementNode = elementNode;
    }

    @Override
    public boolean execute() {
        try {
            // Find element in DOM
            Node node = domManipulator.findNodeByPath(elementNode.xpath());
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Element node not found: {}", elementNode.xpath());
                return false;
            }

            Element element = (Element) node;
            Document doc = element.getOwnerDocument();

            // Check if element has a type attribute (reference to simpleType)
            String typeAttr = element.getAttribute("type");
            if (typeAttr != null && !typeAttr.isEmpty()) {
                // Element references a type - convert to inline complexType with simpleContent
                return convertTypeAttributeToComplexType(element, typeAttr, doc);
            }

            // Check if element has inline simpleType
            NodeList simpleTypes = element.getElementsByTagNameNS(XSD_NS, "simpleType");
            if (simpleTypes.getLength() > 0) {
                // Element has inline simpleType - convert to complexType with simpleContent
                return convertInlineSimpleTypeToComplexType(element, (Element) simpleTypes.item(0), doc);
            }

            // Check if element already has complexType
            NodeList complexTypes = element.getElementsByTagNameNS(XSD_NS, "complexType");
            if (complexTypes.getLength() > 0) {
                logger.info("Element already has a complexType");
                return true; // Already complex, nothing to do
            }

            logger.error("Element has no type definition to convert");
            return false;

        } catch (Exception e) {
            logger.error("Error converting simpleType to complexType", e);
            return false;
        }
    }

    private boolean convertTypeAttributeToComplexType(Element element, String typeAttr, Document doc) {
        // Store old state for undo
        parentNode = element;
        oldTypeElement = null; // No child element, just an attribute

        // Remove type attribute
        element.removeAttribute("type");

        // Create complexType with simpleContent extension
        Element complexType = doc.createElementNS(XSD_NS, "xs:complexType");
        Element simpleContent = doc.createElementNS(XSD_NS, "xs:simpleContent");
        Element extension = doc.createElementNS(XSD_NS, "xs:extension");
        extension.setAttribute("base", typeAttr);

        simpleContent.appendChild(extension);
        complexType.appendChild(simpleContent);

        // Insert complexType as first child of element
        Node firstChild = element.getFirstChild();
        if (firstChild != null) {
            element.insertBefore(complexType, firstChild);
        } else {
            element.appendChild(complexType);
        }

        logger.info("Converted type attribute to complexType with simpleContent");
        return true;
    }

    private boolean convertInlineSimpleTypeToComplexType(Element element, Element simpleType, Document doc) {
        // Store old state for undo
        parentNode = element;
        oldTypeElement = simpleType;
        nextSibling = simpleType.getNextSibling();

        // Remove the simpleType
        element.removeChild(simpleType);

        // Create complexType with simpleContent restriction
        Element complexType = doc.createElementNS(XSD_NS, "xs:complexType");
        Element simpleContent = doc.createElementNS(XSD_NS, "xs:simpleContent");
        Element restriction = doc.createElementNS(XSD_NS, "xs:restriction");

        // Get the base type from the simpleType's restriction
        NodeList restrictions = simpleType.getElementsByTagNameNS(XSD_NS, "restriction");
        if (restrictions.getLength() > 0) {
            Element oldRestriction = (Element) restrictions.item(0);
            String base = oldRestriction.getAttribute("base");
            if (base != null && !base.isEmpty()) {
                restriction.setAttribute("base", base);
            } else {
                restriction.setAttribute("base", "xs:string"); // Default
            }

            // Copy all facets from old restriction to new restriction
            NodeList children = oldRestriction.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    restriction.appendChild(child.cloneNode(true));
                }
            }
        } else {
            restriction.setAttribute("base", "xs:string"); // Default
        }

        simpleContent.appendChild(restriction);
        complexType.appendChild(simpleContent);

        // Insert complexType where simpleType was
        if (nextSibling != null) {
            element.insertBefore(complexType, nextSibling);
        } else {
            element.appendChild(complexType);
        }

        logger.info("Converted inline simpleType to complexType with simpleContent");
        return true;
    }

    @Override
    public boolean undo() {
        try {
            if (parentNode != null) {
                Element element = (Element) parentNode;

                // Remove the complexType we created
                NodeList complexTypes = element.getElementsByTagNameNS(XSD_NS, "complexType");
                if (complexTypes.getLength() > 0) {
                    Element complexType = (Element) complexTypes.item(0);
                    element.removeChild(complexType);

                    // Restore old state
                    if (oldTypeElement != null) {
                        // Restore inline simpleType
                        if (nextSibling != null) {
                            element.insertBefore(oldTypeElement, nextSibling);
                        } else {
                            element.appendChild(oldTypeElement);
                        }
                    } else {
                        // Restore type attribute
                        // Need to extract type from the simpleContent extension
                        NodeList simpleContents = complexType.getElementsByTagNameNS(XSD_NS, "simpleContent");
                        if (simpleContents.getLength() > 0) {
                            Element simpleContent = (Element) simpleContents.item(0);
                            NodeList extensions = simpleContent.getElementsByTagNameNS(XSD_NS, "extension");
                            if (extensions.getLength() > 0) {
                                Element extension = (Element) extensions.item(0);
                                String base = extension.getAttribute("base");
                                if (base != null && !base.isEmpty()) {
                                    element.setAttribute("type", base);
                                }
                            }
                        }
                    }
                }

                logger.info("Reverted complexType conversion");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing simpleType to complexType conversion", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Convert to complex type: " + (elementNode.name() != null ? elementNode.name() : "element");
    }
}
