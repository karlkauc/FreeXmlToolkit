package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.ComplexTypeResult;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.controls.XsdComplexTypeEditor;
import org.fxt.freexmltoolkit.domain.XsdNodeInfo;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for adding new ComplexType definitions to XSD
 */
public class AddComplexTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(AddComplexTypeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo parentNode;
    private final ComplexTypeResult complexTypeResult;
    private Element addedElement;

    public AddComplexTypeCommand(XsdDomManipulator domManipulator, XsdNodeInfo parentNode,
                                 ComplexTypeResult complexTypeResult) {
        this.domManipulator = domManipulator;
        this.parentNode = parentNode;
        this.complexTypeResult = complexTypeResult;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Adding complexType: {}", complexTypeResult.name());

            Document doc = domManipulator.getDocument();
            Element parent = (Element) domManipulator.findNodeByPath(parentNode.xpath());

            // If parent not found via XPath, try to find the schema element directly
            if (parent == null && parentNode.nodeType() == org.fxt.freexmltoolkit.domain.XsdNodeInfo.NodeType.SCHEMA) {
                parent = doc.getDocumentElement(); // This should be the schema element
                logger.debug("Using document root element as schema parent");
            }

            if (parent == null) {
                logger.error("Parent node not found: {}", parentNode.xpath());
                return false;
            }

            // Create complexType element
            addedElement = doc.createElement("xs:complexType");
            addedElement.setAttribute("name", complexTypeResult.name());

            // Set attributes
            if (complexTypeResult.mixedContent()) {
                addedElement.setAttribute("mixed", "true");
            }

            if (complexTypeResult.abstractType()) {
                addedElement.setAttribute("abstract", "true");
            }

            // Add documentation if present
            if (complexTypeResult.documentation() != null && !complexTypeResult.documentation().trim().isEmpty()) {
                Element annotation = doc.createElement("xs:annotation");
                Element documentation = doc.createElement("xs:documentation");
                documentation.setTextContent(complexTypeResult.documentation().trim());
                annotation.appendChild(documentation);
                addedElement.appendChild(annotation);
            }

            // Handle derivation
            if (!"none".equals(complexTypeResult.derivationType())) {
                Element complexContent = doc.createElement("xs:complexContent");
                Element derivation = doc.createElement("xs:" + complexTypeResult.derivationType());
                derivation.setAttribute("base", complexTypeResult.baseType());

                addContentModel(derivation, complexTypeResult, doc);

                complexContent.appendChild(derivation);
                addedElement.appendChild(complexContent);
            } else {
                // Direct content model
                addContentModel(addedElement, complexTypeResult, doc);
            }

            // Find appropriate insertion point
            Node insertionPoint = findComplexTypeInsertionPoint(parent);
            if (insertionPoint != null) {
                parent.insertBefore(addedElement, insertionPoint);
            } else {
                parent.appendChild(addedElement);
            }

            logger.info("Successfully added complexType: {}", complexTypeResult.name());
            return true;

        } catch (Exception e) {
            logger.error("Error adding complexType", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (addedElement != null && addedElement.getParentNode() != null) {
                addedElement.getParentNode().removeChild(addedElement);
                logger.info("Removed complexType: {}", complexTypeResult.name());
            }
            return true;
        } catch (Exception e) {
            logger.error("Error undoing complexType addition", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Add complexType '%s'", complexTypeResult.name());
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    /**
     * Add content model to the complexType
     */
    private void addContentModel(Element parent, ComplexTypeResult result, Document doc) {
        String contentModel = result.contentModel();

        if ("empty".equals(contentModel)) {
            // No content model needed
            return;
        }

        // Add elements if any and not simple content
        if (!result.elements().isEmpty() && !"simple".equals(contentModel)) {
            Element contentModelElement = doc.createElement("xs:" + contentModel);

            for (XsdComplexTypeEditor.ElementItem element : result.elements()) {
                Element elementEl = doc.createElement("xs:element");
                elementEl.setAttribute("name", element.getName());
                elementEl.setAttribute("type", element.getType());

                if (!"1".equals(element.getMinOccurs())) {
                    elementEl.setAttribute("minOccurs", element.getMinOccurs());
                }
                if (!"1".equals(element.getMaxOccurs())) {
                    elementEl.setAttribute("maxOccurs", element.getMaxOccurs());
                }

                contentModelElement.appendChild(elementEl);
            }

            parent.appendChild(contentModelElement);
        }

        // Add attributes
        for (XsdComplexTypeEditor.AttributeItem attribute : result.attributes()) {
            Element attributeEl = doc.createElement("xs:attribute");
            attributeEl.setAttribute("name", attribute.getName());
            attributeEl.setAttribute("type", attribute.getType());

            if (!"optional".equals(attribute.getUse())) {
                attributeEl.setAttribute("use", attribute.getUse());
            }

            parent.appendChild(attributeEl);
        }
    }

    /**
     * Find appropriate insertion point for complexType
     */
    private Node findComplexTypeInsertionPoint(Element parent) {
        var children = parent.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String localName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

                // Insert before elements, groups, attributeGroups
                if (localName.equals("element") || localName.equals("group") ||
                        localName.equals("attributeGroup")) {
                    return child;
                }
            }
        }

        return null; // Insert at end
    }
}