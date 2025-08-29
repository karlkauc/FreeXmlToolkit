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
import org.w3c.dom.NodeList;

/**
 * Command for editing existing ComplexType definitions in XSD
 */
public class EditComplexTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(EditComplexTypeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final XsdNodeInfo complexTypeNode;
    private final ComplexTypeResult newResult;
    private Element originalElement;
    private Element backupElement;

    public EditComplexTypeCommand(XsdDomManipulator domManipulator, XsdNodeInfo complexTypeNode,
                                  ComplexTypeResult newResult) {
        this.domManipulator = domManipulator;
        this.complexTypeNode = complexTypeNode;
        this.newResult = newResult;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Editing complexType: {}", complexTypeNode.name());

            originalElement = (Element) domManipulator.findNodeByPath(complexTypeNode.xpath());
            if (originalElement == null) {
                logger.error("ComplexType element not found: {}", complexTypeNode.xpath());
                return false;
            }

            // Create backup for undo
            backupElement = (Element) originalElement.cloneNode(true);

            // Update the complexType
            updateComplexTypeElement(originalElement, newResult);

            logger.info("Successfully edited complexType: {}", newResult.name());
            return true;

        } catch (Exception e) {
            logger.error("Error editing complexType", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (originalElement != null && backupElement != null) {
                // Replace current element with backup
                Node parent = originalElement.getParentNode();
                if (parent != null) {
                    // Import backup to current document
                    Document doc = domManipulator.getDocument();
                    Element restoredElement = (Element) doc.importNode(backupElement, true);
                    parent.replaceChild(restoredElement, originalElement);

                    logger.info("Restored complexType: {}", complexTypeNode.name());
                }
            }
            return true;
        } catch (Exception e) {
            logger.error("Error undoing complexType edit", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Edit complexType '%s'", complexTypeNode.name());
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
     * Update the complexType element with new values
     */
    private void updateComplexTypeElement(Element complexTypeEl, ComplexTypeResult result) {
        Document doc = complexTypeEl.getOwnerDocument();

        // Update name attribute
        complexTypeEl.setAttribute("name", result.name());

        // Update mixed attribute
        if (result.mixedContent()) {
            complexTypeEl.setAttribute("mixed", "true");
        } else {
            complexTypeEl.removeAttribute("mixed");
        }

        // Update abstract attribute
        if (result.abstractType()) {
            complexTypeEl.setAttribute("abstract", "true");
        } else {
            complexTypeEl.removeAttribute("abstract");
        }

        // Clear existing content
        clearComplexTypeContent(complexTypeEl);

        // Add documentation if present
        if (result.documentation() != null && !result.documentation().trim().isEmpty()) {
            Element annotation = doc.createElement("xs:annotation");
            Element documentation = doc.createElement("xs:documentation");
            documentation.setTextContent(result.documentation().trim());
            annotation.appendChild(documentation);
            complexTypeEl.appendChild(annotation);
        }

        // Handle derivation
        if (!"none".equals(result.derivationType())) {
            Element complexContent = doc.createElement("xs:complexContent");
            Element derivation = doc.createElement("xs:" + result.derivationType());
            derivation.setAttribute("base", result.baseType());

            addContentModel(derivation, result, doc);

            complexContent.appendChild(derivation);
            complexTypeEl.appendChild(complexContent);
        } else {
            // Direct content model
            addContentModel(complexTypeEl, result, doc);
        }
    }

    /**
     * Clear existing content from complexType
     */
    private void clearComplexTypeContent(Element complexType) {
        NodeList children = complexType.getChildNodes();

        // Collect nodes to remove (except attributes on the element itself)
        var nodesToRemove = new java.util.ArrayList<Node>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                nodesToRemove.add(child);
            }
        }

        // Remove nodes
        for (Node node : nodesToRemove) {
            complexType.removeChild(node);
        }
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
}