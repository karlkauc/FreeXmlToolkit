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
 * Command for editing an existing XSD 1.1 assertion (xs:assert).
 * Stores old values for undo functionality.
 */
public class EditAssertionCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(EditAssertionCommand.class);
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo assertionNode;
    private final String newTestExpression;
    private final String newXpathDefaultNamespace;
    private final String newDocumentation;

    // Store old values for undo
    private String oldTestExpression;
    private String oldXpathDefaultNamespace;
    private String oldDocumentation;
    private Element assertionElement;

    public EditAssertionCommand(XsdDomManipulator domManipulator, XsdNodeInfo assertionNode,
                                String newTestExpression, String newXpathDefaultNamespace,
                                String newDocumentation) {
        this.domManipulator = domManipulator;
        this.assertionNode = assertionNode;
        this.newTestExpression = newTestExpression;
        this.newXpathDefaultNamespace = newXpathDefaultNamespace;
        this.newDocumentation = newDocumentation;
    }

    @Override
    public boolean execute() {
        try {
            // Find assertion element in DOM
            Node node = domManipulator.findNodeByPath(assertionNode.xpath());
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                logger.error("Assertion node not found: {}", assertionNode.xpath());
                return false;
            }

            assertionElement = (Element) node;

            // Store old values for undo
            oldTestExpression = assertionElement.getAttribute("test");
            oldXpathDefaultNamespace = assertionElement.getAttribute("xpath-default-namespace");
            oldDocumentation = extractDocumentation(assertionElement);

            // Update test expression
            assertionElement.setAttribute("test", newTestExpression);

            // Update xpath-default-namespace
            if (newXpathDefaultNamespace != null && !newXpathDefaultNamespace.isEmpty()) {
                assertionElement.setAttribute("xpath-default-namespace", newXpathDefaultNamespace);
            } else {
                assertionElement.removeAttribute("xpath-default-namespace");
            }

            // Update documentation
            updateDocumentation(assertionElement, newDocumentation);

            logger.info("Edited assertion: {}", newTestExpression);
            return true;

        } catch (Exception e) {
            logger.error("Error editing assertion", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (assertionElement != null) {
                // Restore old test expression
                assertionElement.setAttribute("test", oldTestExpression);

                // Restore old xpath-default-namespace
                if (oldXpathDefaultNamespace != null && !oldXpathDefaultNamespace.isEmpty()) {
                    assertionElement.setAttribute("xpath-default-namespace", oldXpathDefaultNamespace);
                } else {
                    assertionElement.removeAttribute("xpath-default-namespace");
                }

                // Restore old documentation
                updateDocumentation(assertionElement, oldDocumentation);

                logger.info("Undone assertion edit");
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing assertion edit", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Edit assertion: " + (newTestExpression.length() > 30
                ? newTestExpression.substring(0, 30) + "..."
                : newTestExpression);
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
