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
 * Command for editing an existing XSD 1.1 type alternative (xs:alternative).
 * Stores old values for undo functionality.
 */
public class EditTypeAlternativeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(EditTypeAlternativeCommand.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo alternativeNode;
    private final String newTestExpression;  // null for default alternative
    private final String newTypeName;
    private final String newXpathDefaultNamespace;
    private final String newDocumentation;

    // Store old values for undo
    private String oldTestExpression;
    private String oldTypeName;
    private String oldXpathDefaultNamespace;
    private String oldDocumentation;
    private Element alternativeElement;

    public EditTypeAlternativeCommand(XsdDomManipulator domManipulator, XsdNodeInfo alternativeNode,
                                      String newTestExpression, String newTypeName,
                                      String newXpathDefaultNamespace, String newDocumentation) {
        this.domManipulator = domManipulator;
        this.alternativeNode = alternativeNode;
        this.newTestExpression = newTestExpression;
        this.newTypeName = newTypeName;
        this.newXpathDefaultNamespace = newXpathDefaultNamespace;
        this.newDocumentation = newDocumentation;
    }

    @Override
    public boolean execute() {
        try {
            // Find alternative element in DOM
            Node node = domManipulator.findNodeByPath(alternativeNode.xpath());
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Type alternative node not found: {}", alternativeNode.xpath());
                return false;
            }

            alternativeElement = (Element) node;

            // Store old values for undo
            oldTestExpression = alternativeElement.getAttribute("test");
            oldTypeName = alternativeElement.getAttribute("type");
            oldXpathDefaultNamespace = alternativeElement.getAttribute("xpath-default-namespace");
            oldDocumentation = extractDocumentation(alternativeElement);

            // Update test expression (or remove for default alternative)
            if (newTestExpression != null && !newTestExpression.isEmpty()) {
                alternativeElement.setAttribute("test", newTestExpression);
            } else {
                alternativeElement.removeAttribute("test");
            }

            // Update type attribute
            if (newTypeName != null && !newTypeName.isEmpty()) {
                alternativeElement.setAttribute("type", newTypeName);
            } else {
                alternativeElement.removeAttribute("type");
            }

            // Update xpath-default-namespace
            if (newXpathDefaultNamespace != null && !newXpathDefaultNamespace.isEmpty()) {
                alternativeElement.setAttribute("xpath-default-namespace", newXpathDefaultNamespace);
            } else {
                alternativeElement.removeAttribute("xpath-default-namespace");
            }

            // Update documentation
            updateDocumentation(alternativeElement, newDocumentation);

            logger.info("Edited type alternative: {} -> {}", oldTypeName, newTypeName);
            return true;

        } catch (Exception e) {
            logger.error("Error editing type alternative", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (alternativeElement != null) {
                // Restore old test expression
                if (oldTestExpression != null && !oldTestExpression.isEmpty()) {
                    alternativeElement.setAttribute("test", oldTestExpression);
                } else {
                    alternativeElement.removeAttribute("test");
                }

                // Restore old type
                if (oldTypeName != null && !oldTypeName.isEmpty()) {
                    alternativeElement.setAttribute("type", oldTypeName);
                } else {
                    alternativeElement.removeAttribute("type");
                }

                // Restore old xpath-default-namespace
                if (oldXpathDefaultNamespace != null && !oldXpathDefaultNamespace.isEmpty()) {
                    alternativeElement.setAttribute("xpath-default-namespace", oldXpathDefaultNamespace);
                } else {
                    alternativeElement.removeAttribute("xpath-default-namespace");
                }

                // Restore old documentation
                updateDocumentation(alternativeElement, oldDocumentation);

                logger.info("Undone type alternative edit");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing type alternative edit", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Edit type alternative: " + (newTypeName != null && newTypeName.length() > 30
                ? newTypeName.substring(0, 30) + "..."
                : newTypeName);
    }

    /**
     * Extracts documentation text from xs:annotation/xs:documentation
     */
    private String extractDocumentation(Element element) {
        NodeList annotations = element.getElementsByTagNameNS(XSD_NS, "annotation");
        if (annotations.getLength() > 0) {
            Element annotation = (Element) annotations.item(0);
            NodeList docs = annotation.getElementsByTagNameNS(XSD_NS, "documentation");
            if (docs.getLength() > 0) {
                return docs.item(0).getTextContent();
            }
        }
        return "";
    }

    /**
     * Updates or creates documentation annotation
     */
    private void updateDocumentation(Element element, String documentation) {
        Document doc = element.getOwnerDocument();

        // Remove existing annotation if present
        NodeList annotations = element.getElementsByTagNameNS(XSD_NS, "annotation");
        for (int i = 0; i < annotations.getLength(); i++) {
            element.removeChild(annotations.item(i));
        }

        // Add new annotation if documentation is not empty
        if (documentation != null && !documentation.isEmpty()) {
            Element annotation = doc.createElementNS(XSD_NS, "xs:annotation");
            Element docElement = doc.createElementNS(XSD_NS, "xs:documentation");
            docElement.setTextContent(documentation);
            annotation.appendChild(docElement);

            // Insert annotation as first child
            Node firstChild = element.getFirstChild();
            if (firstChild != null) {
                element.insertBefore(annotation, firstChild);
            } else {
                element.appendChild(annotation);
            }
        }
    }
}
