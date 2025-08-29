package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command to convert an XSD element to an attribute.
 * This refactoring is useful when an element contains only simple content
 * and would be better represented as an attribute.
 */
public class ConvertElementToAttributeCommand implements XsdCommand {
    private final Document xsdDocument;
    private final Element elementToConvert;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element originalElement;
    private Node originalParent;
    private Node originalNextSibling;
    private Element createdAttribute;
    private boolean wasExecuted = false;

    public ConvertElementToAttributeCommand(Document xsdDocument, Element elementToConvert, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.elementToConvert = elementToConvert;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Validate that conversion is possible
            if (!canConvertToAttribute(elementToConvert)) {
                throw new IllegalArgumentException("Element cannot be converted to attribute: contains complex content");
            }

            // Store backup information
            originalElement = (Element) elementToConvert.cloneNode(true);
            originalParent = elementToConvert.getParentNode();
            originalNextSibling = elementToConvert.getNextSibling();

            // Extract element information
            String elementName = elementToConvert.getAttribute("name");
            String elementType = elementToConvert.getAttribute("type");
            String defaultValue = elementToConvert.getAttribute("default");
            String fixedValue = elementToConvert.getAttribute("fixed");
            String minOccurs = elementToConvert.getAttribute("minOccurs");
            String maxOccurs = elementToConvert.getAttribute("maxOccurs");

            // Determine attribute properties
            String use = determineAttributeUse(minOccurs, maxOccurs);

            // Find the appropriate parent complexType for the attribute
            Element targetParent = findAttributeInsertionParent(originalParent);
            if (targetParent == null) {
                throw new IllegalStateException("Cannot find appropriate parent for attribute");
            }

            // Create new attribute element
            createdAttribute = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:attribute");
            createdAttribute.setAttribute("name", elementName);

            // Set type (default to xs:string if not specified or if was complex)
            if (elementType != null && !elementType.isEmpty() && isSimpleType(elementType)) {
                createdAttribute.setAttribute("type", elementType);
            } else {
                createdAttribute.setAttribute("type", "xs:string");
            }

            // Set use attribute
            if (!"optional".equals(use)) {
                createdAttribute.setAttribute("use", use);
            }

            // Set default or fixed value
            if (fixedValue != null && !fixedValue.isEmpty()) {
                createdAttribute.setAttribute("fixed", fixedValue);
            } else if (defaultValue != null && !defaultValue.isEmpty()) {
                createdAttribute.setAttribute("default", defaultValue);
            }

            // Copy documentation if present
            copyDocumentation(elementToConvert, createdAttribute);

            // Remove original element
            originalParent.removeChild(elementToConvert);

            // Insert attribute in correct position (after content model, before other attributes)
            insertAttributeInCorrectPosition(targetParent, createdAttribute);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Restore original state if something went wrong
            if (originalElement != null && originalParent != null) {
                try {
                    if (originalNextSibling != null) {
                        originalParent.insertBefore(elementToConvert, originalNextSibling);
                    } else {
                        originalParent.appendChild(elementToConvert);
                    }
                    if (createdAttribute != null && createdAttribute.getParentNode() != null) {
                        createdAttribute.getParentNode().removeChild(createdAttribute);
                    }
                } catch (Exception restoreException) {
                    // Log but don't throw - original exception is more important
                }
            }
            throw new RuntimeException("Failed to convert element to attribute: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            // Remove the created attribute
            if (createdAttribute != null && createdAttribute.getParentNode() != null) {
                createdAttribute.getParentNode().removeChild(createdAttribute);
            }

            // Restore original element
            if (originalNextSibling != null) {
                originalParent.insertBefore(originalElement, originalNextSibling);
            } else {
                originalParent.appendChild(originalElement);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo element to attribute conversion: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        String elementName = elementToConvert.getAttribute("name");
        return "Convert element '" + elementName + "' to attribute";
    }

    /**
     * Check if an element can be converted to an attribute
     */
    private boolean canConvertToAttribute(Element element) {
        // Element must not have child elements (complex content)
        var childElements = element.getElementsByTagName("*");

        // Check for inline complexType or simpleType that would indicate complex content
        for (int i = 0; i < childElements.getLength(); i++) {
            Element child = (Element) childElements.item(i);
            String localName = child.getLocalName();

            // If it has complexType with content model, it cannot be an attribute
            if ("complexType".equals(localName)) {
                return false;
            }

            // If it has sequence, choice, all, etc., it cannot be an attribute
            if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                return false;
            }

            // If it has element children, it cannot be an attribute
            if ("element".equals(localName)) {
                return false;
            }
        }

        // Check type attribute - must be simple type if specified
        String type = element.getAttribute("type");
        if (type != null && !type.isEmpty() && !isSimpleType(type)) {
            return false;
        }

        // Element should not have maxOccurs > 1 (attributes can't repeat)
        String maxOccurs = element.getAttribute("maxOccurs");
        if (maxOccurs != null && !maxOccurs.isEmpty() &&
                !"1".equals(maxOccurs) && !"unbounded".equals(maxOccurs)) {
            try {
                int max = Integer.parseInt(maxOccurs);
                if (max > 1) {
                    return false;
                }
            } catch (NumberFormatException e) {
                // If we can't parse, assume it might be invalid
                return false;
            }
        }

        return true;
    }

    /**
     * Determine the 'use' attribute value based on minOccurs/maxOccurs
     */
    private String determineAttributeUse(String minOccurs, String maxOccurs) {
        // Default is optional
        if (minOccurs == null || minOccurs.isEmpty()) {
            minOccurs = "1";
        }

        if (maxOccurs == null || maxOccurs.isEmpty()) {
            maxOccurs = "1";
        }

        try {
            int min = Integer.parseInt(minOccurs);

            if (min == 0) {
                return "optional";
            } else if (min >= 1) {
                return "required";
            }
        } catch (NumberFormatException e) {
            // If we can't parse, default to optional
        }

        return "optional";
    }

    /**
     * Check if a type is a simple type
     */
    private boolean isSimpleType(String typeName) {
        // Built-in simple types
        if (typeName.startsWith("xs:") || typeName.startsWith("xsd:")) {
            return true;
        }

        // Check if it's a user-defined simpleType
        // This is a simplified check - in a full implementation, 
        // we would resolve the type definition
        return !typeName.contains("ComplexType") && !typeName.contains("complexType");
    }

    /**
     * Find the appropriate parent element where attributes should be inserted
     */
    private Element findAttributeInsertionParent(Node parentNode) {
        if (parentNode instanceof Element parent) {
            String localName = parent.getLocalName();

            // If parent is complexType, that's where attributes go
            if ("complexType".equals(localName)) {
                return parent;
            }

            // If parent is element, look for or create complexType
            if ("element".equals(localName)) {
                // Look for existing complexType
                var complexTypes = parent.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
                if (complexTypes.getLength() > 0) {
                    return (Element) complexTypes.item(0);
                }

                // Create inline complexType if none exists
                Element complexType = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:complexType");
                parent.appendChild(complexType);
                return complexType;
            }

            // If parent is sequence/choice/all, go up to find complexType
            if ("sequence".equals(localName) || "choice".equals(localName) || "all".equals(localName)) {
                return findAttributeInsertionParent(parent.getParentNode());
            }
        }

        return null;
    }

    /**
     * Insert attribute in the correct position within complexType
     */
    private void insertAttributeInCorrectPosition(Element complexType, Element attribute) {
        // Attributes should come after content model but before other attributes
        Node insertBefore = null;

        var children = complexType.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                String localName = childElement.getLocalName();

                // Insert before first existing attribute or anyAttribute
                if ("attribute".equals(localName) || "attributeGroup".equals(localName) ||
                        "anyAttribute".equals(localName)) {
                    insertBefore = child;
                    break;
                }
            }
        }

        if (insertBefore != null) {
            complexType.insertBefore(attribute, insertBefore);
        } else {
            complexType.appendChild(attribute);
        }
    }

    /**
     * Copy documentation from element to attribute
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