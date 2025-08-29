package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command to convert an XSD attribute to an element.
 * This refactoring is useful when an attribute needs to be promoted to an element
 * to support more complex content or better extensibility.
 */
public class ConvertAttributeToElementCommand implements XsdCommand {
    private final Document xsdDocument;
    private final Element attributeToConvert;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element originalAttribute;
    private Node originalParent;
    private Node originalNextSibling;
    private Element createdElement;
    private boolean wasExecuted = false;

    public ConvertAttributeToElementCommand(Document xsdDocument, Element attributeToConvert, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.attributeToConvert = attributeToConvert;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Store backup information
            originalAttribute = (Element) attributeToConvert.cloneNode(true);
            originalParent = attributeToConvert.getParentNode();
            originalNextSibling = attributeToConvert.getNextSibling();

            // Extract attribute information
            String attributeName = attributeToConvert.getAttribute("name");
            String attributeType = attributeToConvert.getAttribute("type");
            String defaultValue = attributeToConvert.getAttribute("default");
            String fixedValue = attributeToConvert.getAttribute("fixed");
            String use = attributeToConvert.getAttribute("use");

            // Determine element properties from attribute
            String minOccurs = determineMinOccurs(use);
            String maxOccurs = "1"; // Attributes are single-valued, so elements will be too

            // Find the appropriate parent for the new element
            Element targetParent = findElementInsertionParent(originalParent);
            if (targetParent == null) {
                throw new IllegalStateException("Cannot find appropriate parent for element");
            }

            // Create new element
            createdElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:element");
            createdElement.setAttribute("name", attributeName);

            // Set type (preserve from attribute or default to xs:string)
            if (attributeType != null && !attributeType.isEmpty()) {
                createdElement.setAttribute("type", attributeType);
            } else {
                createdElement.setAttribute("type", "xs:string");
            }

            // Set occurrence constraints
            if (!"1".equals(minOccurs)) {
                createdElement.setAttribute("minOccurs", minOccurs);
            }
            if (!"1".equals(maxOccurs)) {
                createdElement.setAttribute("maxOccurs", maxOccurs);
            }

            // Set default or fixed value
            if (fixedValue != null && !fixedValue.isEmpty()) {
                createdElement.setAttribute("fixed", fixedValue);
            } else if (defaultValue != null && !defaultValue.isEmpty()) {
                createdElement.setAttribute("default", defaultValue);
            }

            // Copy documentation if present
            copyDocumentation(attributeToConvert, createdElement);

            // Remove original attribute
            originalParent.removeChild(attributeToConvert);

            // Insert element in correct position within the content model
            insertElementInCorrectPosition(targetParent, createdElement);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Restore original state if something went wrong
            if (originalAttribute != null && originalParent != null) {
                try {
                    if (originalNextSibling != null) {
                        originalParent.insertBefore(attributeToConvert, originalNextSibling);
                    } else {
                        originalParent.appendChild(attributeToConvert);
                    }
                    if (createdElement != null && createdElement.getParentNode() != null) {
                        createdElement.getParentNode().removeChild(createdElement);
                    }
                } catch (Exception restoreException) {
                    // Log but don't throw - original exception is more important
                }
            }
            throw new RuntimeException("Failed to convert attribute to element: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            // Remove the created element
            if (createdElement != null && createdElement.getParentNode() != null) {
                createdElement.getParentNode().removeChild(createdElement);
            }

            // Restore original attribute
            if (originalNextSibling != null) {
                originalParent.insertBefore(originalAttribute, originalNextSibling);
            } else {
                originalParent.appendChild(originalAttribute);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo attribute to element conversion: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        String attributeName = attributeToConvert.getAttribute("name");
        return "Convert attribute '" + attributeName + "' to element";
    }

    /**
     * Determine minOccurs based on attribute 'use' value
     */
    private String determineMinOccurs(String use) {
        if ("required".equals(use)) {
            return "1";
        } else if ("optional".equals(use) || use == null || use.isEmpty()) {
            return "0";
        } else if ("prohibited".equals(use)) {
            return "0"; // Prohibited attributes become optional elements
        }
        return "0"; // Default to optional
    }

    /**
     * Find the appropriate parent element where the new element should be inserted
     */
    private Element findElementInsertionParent(Node attributeParent) {
        if (attributeParent instanceof Element parent) {
            String localName = parent.getLocalName();

            // If parent is complexType, we need to find or create a content model
            if ("complexType".equals(localName)) {
                // Look for existing content model (sequence, choice, all)
                Element contentModel = findOrCreateContentModel(parent);
                return contentModel;
            }

            // If parent is element, look for complexType
            if ("element".equals(localName)) {
                var complexTypes = parent.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
                if (complexTypes.getLength() > 0) {
                    Element complexType = (Element) complexTypes.item(0);
                    return findOrCreateContentModel(complexType);
                }

                // Create inline complexType with sequence
                Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
                Element sequence = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");
                complexType.appendChild(sequence);
                parent.appendChild(complexType);
                return sequence;
            }
        }

        return null;
    }

    /**
     * Find existing content model or create a sequence if none exists
     */
    private Element findOrCreateContentModel(Element complexType) {
        // Look for existing content model elements
        String[] contentModels = {"sequence", "choice", "all"};

        for (String modelName : contentModels) {
            var models = complexType.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", modelName);
            if (models.getLength() > 0) {
                return (Element) models.item(0);
            }
        }

        // No content model exists - create a sequence
        Element sequence = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:sequence");

        // Insert sequence before any attributes
        Node insertBefore = null;
        var children = complexType.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                String localName = childElement.getLocalName();
                if ("attribute".equals(localName) || "attributeGroup".equals(localName) ||
                        "anyAttribute".equals(localName)) {
                    insertBefore = child;
                    break;
                }
            }
        }

        if (insertBefore != null) {
            complexType.insertBefore(sequence, insertBefore);
        } else {
            complexType.appendChild(sequence);
        }

        return sequence;
    }

    /**
     * Insert element in the correct position within the content model
     */
    private void insertElementInCorrectPosition(Element contentModel, Element element) {
        // Elements can be inserted at the end of sequence/choice/all
        // For now, we simply append to the content model
        contentModel.appendChild(element);
    }

    /**
     * Copy documentation from attribute to element
     */
    private void copyDocumentation(Element source, Element target) {
        var annotations = source.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation");
        if (annotations.getLength() > 0) {
            Element annotation = (Element) annotations.item(0);
            Element clonedAnnotation = (Element) annotation.cloneNode(true);
            target.appendChild(clonedAnnotation);
        }
    }
}