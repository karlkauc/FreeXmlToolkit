package org.fxt.freexmltoolkit.controls.commands;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Command to convert a simple type element to a complex type with sequence.
 * This allows adding child elements to an element that was previously bound to a simple type.
 */
public class ConvertToComplexTypeCommand implements XsdCommand {

    private final Document xsdDocument;
    private final Element elementNode;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private String originalTypeAttribute;
    private Element createdComplexType;
    private Element createdSequence;
    private boolean wasExecuted = false;

    public ConvertToComplexTypeCommand(Document xsdDocument, Element elementNode, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.elementNode = elementNode;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Validate that element has a simple type
            originalTypeAttribute = elementNode.getAttribute("type");
            if (originalTypeAttribute == null || originalTypeAttribute.isEmpty()) {
                throw new IllegalArgumentException("Element has no type attribute to convert");
            }

            // Check if it's actually a simple type (built-in XSD type)
            if (!originalTypeAttribute.startsWith("xs:") && !originalTypeAttribute.startsWith("xsd:")) {
                throw new IllegalArgumentException("Element does not have a simple type: " + originalTypeAttribute);
            }

            // Remove the type attribute
            elementNode.removeAttribute("type");

            // Create inline complexType
            String xsdNamespace = "http://www.w3.org/2001/XMLSchema";
            String xsdPrefix = getXsdPrefix();

            createdComplexType = xsdDocument.createElementNS(xsdNamespace, xsdPrefix + ":complexType");

            // Create sequence within complexType
            createdSequence = xsdDocument.createElementNS(xsdNamespace, xsdPrefix + ":sequence");
            createdComplexType.appendChild(createdSequence);

            // Add the complexType to the element
            elementNode.appendChild(createdComplexType);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Restore original state if something went wrong
            if (originalTypeAttribute != null) {
                try {
                    // Remove created elements if they were added
                    if (createdComplexType != null && createdComplexType.getParentNode() != null) {
                        createdComplexType.getParentNode().removeChild(createdComplexType);
                    }

                    // Restore type attribute
                    elementNode.setAttribute("type", originalTypeAttribute);
                } catch (Exception restoreException) {
                    // Log but don't throw - original exception is more important
                }
            }
            throw new RuntimeException("Failed to convert to complex type: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            // Remove the created complexType and sequence
            if (createdComplexType != null && createdComplexType.getParentNode() != null) {
                createdComplexType.getParentNode().removeChild(createdComplexType);
            }

            // Restore the original type attribute
            if (originalTypeAttribute != null && !originalTypeAttribute.isEmpty()) {
                elementNode.setAttribute("type", originalTypeAttribute);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo complex type conversion: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        String elementName = elementNode.getAttribute("name");
        return "Convert element '" + elementName + "' from " + originalTypeAttribute + " to complex type";
    }

    /**
     * Get the XSD prefix used in the document
     */
    private String getXsdPrefix() {
        // Find the schema root to determine the prefix
        Element schemaRoot = findSchemaRoot();
        if (schemaRoot != null) {
            String prefix = schemaRoot.getPrefix();
            if (prefix != null && !prefix.isBlank()) {
                return prefix;
            }
        }
        return "xs"; // Default fallback
    }

    /**
     * Find the schema root element
     */
    private Element findSchemaRoot() {
        Element current = elementNode;
        while (current != null) {
            if ("schema".equals(current.getLocalName()) &&
                    "http://www.w3.org/2001/XMLSchema".equals(current.getNamespaceURI())) {
                return current;
            }
            if (current.getParentNode() instanceof Element) {
                current = (Element) current.getParentNode();
            } else {
                break;
            }
        }
        return null;
    }
}