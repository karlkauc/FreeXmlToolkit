package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command to extract an inline complexType definition to a global complexType.
 * This refactoring improves reusability by converting inline type definitions
 * to named global types that can be referenced by multiple elements.
 */
public class ExtractComplexTypeCommand implements XsdCommand {
    private final Document xsdDocument;
    private final Element elementWithInlineType;
    private final String newTypeName;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element originalInlineComplexType;
    private Element createdGlobalComplexType;
    private String originalTypeAttribute;
    private boolean wasExecuted = false;

    public ExtractComplexTypeCommand(Document xsdDocument, Element elementWithInlineType,
                                     String newTypeName, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.elementWithInlineType = elementWithInlineType;
        this.newTypeName = newTypeName;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Validate that element has inline complexType
            Element inlineComplexType = findInlineComplexType(elementWithInlineType);
            if (inlineComplexType == null) {
                throw new IllegalArgumentException("Element does not contain an inline complexType");
            }

            // Check if type name already exists
            if (globalTypeExists(newTypeName)) {
                throw new IllegalArgumentException("Global type '" + newTypeName + "' already exists");
            }

            // Store backup information
            originalInlineComplexType = (Element) inlineComplexType.cloneNode(true);
            originalTypeAttribute = elementWithInlineType.getAttribute("type");

            // Create new global complexType
            createdGlobalComplexType = (Element) inlineComplexType.cloneNode(true);
            createdGlobalComplexType.setAttribute("name", newTypeName);

            // Find schema root element
            Element schemaRoot = findSchemaRoot();
            if (schemaRoot == null) {
                throw new IllegalStateException("Cannot find schema root element");
            }

            // Insert global complexType in correct position
            insertGlobalComplexTypeInCorrectPosition(schemaRoot, createdGlobalComplexType);

            // Replace inline complexType with type reference
            elementWithInlineType.removeChild(inlineComplexType);
            elementWithInlineType.setAttribute("type", newTypeName);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Restore original state if something went wrong
            if (originalInlineComplexType != null) {
                try {
                    // Remove created global type if it was added
                    if (createdGlobalComplexType != null && createdGlobalComplexType.getParentNode() != null) {
                        createdGlobalComplexType.getParentNode().removeChild(createdGlobalComplexType);
                    }

                    // Restore inline complexType
                    elementWithInlineType.appendChild(originalInlineComplexType);

                    // Remove type attribute if it wasn't there originally
                    if (originalTypeAttribute == null || originalTypeAttribute.isEmpty()) {
                        elementWithInlineType.removeAttribute("type");
                    } else {
                        elementWithInlineType.setAttribute("type", originalTypeAttribute);
                    }
                } catch (Exception restoreException) {
                    // Log but don't throw - original exception is more important
                }
            }
            throw new RuntimeException("Failed to extract complexType: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            // Remove the created global complexType
            if (createdGlobalComplexType != null && createdGlobalComplexType.getParentNode() != null) {
                createdGlobalComplexType.getParentNode().removeChild(createdGlobalComplexType);
            }

            // Remove type attribute and restore inline complexType
            elementWithInlineType.removeAttribute("type");
            if (originalTypeAttribute != null && !originalTypeAttribute.isEmpty()) {
                elementWithInlineType.setAttribute("type", originalTypeAttribute);
            }
            elementWithInlineType.appendChild(originalInlineComplexType);

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo complexType extraction: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        String elementName = elementWithInlineType.getAttribute("name");
        return "Extract complexType from element '" + elementName + "' to global type '" + newTypeName + "'";
    }

    /**
     * Find inline complexType within the element
     */
    private Element findInlineComplexType(Element element) {
        NodeList complexTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");

        // Look for a direct child complexType (inline definition)
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == element) {
                // Make sure it's an inline type (no name attribute)
                if (!complexType.hasAttribute("name") || complexType.getAttribute("name").isEmpty()) {
                    return complexType;
                }
            }
        }

        return null;
    }

    /**
     * Check if a global type with the given name already exists
     */
    private boolean globalTypeExists(String typeName) {
        Element schemaRoot = findSchemaRoot();
        if (schemaRoot == null) {
            return false;
        }

        // Check for existing complexType with same name
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == schemaRoot && typeName.equals(complexType.getAttribute("name"))) {
                return true;
            }
        }

        // Check for existing simpleType with same name
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (simpleType.getParentNode() == schemaRoot && typeName.equals(simpleType.getAttribute("name"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the schema root element
     */
    private Element findSchemaRoot() {
        Node current = elementWithInlineType;
        while (current != null) {
            if (current instanceof Element element && "schema".equals(element.getLocalName())) {
                return element;
            }
            current = current.getParentNode();
        }
        return null;
    }

    /**
     * Insert the global complexType in the correct position within the schema
     */
    private void insertGlobalComplexTypeInCorrectPosition(Element schemaRoot, Element globalComplexType) {
        // XSD recommends order: import/include, redefine, annotation, 
        // (simpleType | complexType | group | attributeGroup), element, attribute, notation

        Node insertBefore = null;
        NodeList children = schemaRoot.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                String localName = childElement.getLocalName();

                // Insert before first element, attribute, or notation
                if ("element".equals(localName) || "attribute".equals(localName) || "notation".equals(localName)) {
                    insertBefore = child;
                    break;
                }
            }
        }

        if (insertBefore != null) {
            schemaRoot.insertBefore(globalComplexType, insertBefore);
        } else {
            schemaRoot.appendChild(globalComplexType);
        }
    }

    /**
     * Static helper method to check if an element can have its complexType extracted
     */
    public static boolean canExtractComplexType(Element element) {
        if (element == null || !"element".equals(element.getLocalName())) {
            return false;
        }

        // Element must have an inline complexType (no name attribute)
        NodeList complexTypes = element.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == element) {
                // Must be inline (no name attribute)
                return !complexType.hasAttribute("name") || complexType.getAttribute("name").isEmpty();
            }
        }

        return false;
    }
}