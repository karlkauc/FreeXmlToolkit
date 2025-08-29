package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Command to inline a global complexType definition into element definitions.
 * This refactoring converts a reusable global type back to inline type definitions
 * within specific elements that reference it.
 */
public class InlineTypeDefinitionCommand implements XsdCommand {
    private final Document xsdDocument;
    private final Element elementWithTypeReference;
    private final String globalTypeName;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private String originalTypeAttribute;
    private Element globalTypeDefinition;
    private Element createdInlineType;
    private boolean wasExecuted = false;

    public InlineTypeDefinitionCommand(Document xsdDocument, Element elementWithTypeReference,
                                       String globalTypeName, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.elementWithTypeReference = elementWithTypeReference;
        this.globalTypeName = globalTypeName;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Find the global type definition
            globalTypeDefinition = findGlobalType(globalTypeName);
            if (globalTypeDefinition == null) {
                throw new IllegalArgumentException("Global type '" + globalTypeName + "' not found");
            }

            // Store backup information
            originalTypeAttribute = elementWithTypeReference.getAttribute("type");

            // Validate that element references the global type
            if (!globalTypeName.equals(originalTypeAttribute)) {
                throw new IllegalArgumentException("Element does not reference the specified global type");
            }

            // Create inline copy of the global type definition
            createdInlineType = (Element) globalTypeDefinition.cloneNode(true);

            // Remove the name attribute to make it an inline definition
            createdInlineType.removeAttribute("name");

            // Remove the type attribute from the element
            elementWithTypeReference.removeAttribute("type");

            // Add the inline type definition to the element
            elementWithTypeReference.appendChild(createdInlineType);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Restore original state if something went wrong
            if (originalTypeAttribute != null) {
                try {
                    // Remove created inline type if it was added
                    if (createdInlineType != null && createdInlineType.getParentNode() != null) {
                        createdInlineType.getParentNode().removeChild(createdInlineType);
                    }

                    // Restore type attribute
                    elementWithTypeReference.setAttribute("type", originalTypeAttribute);
                } catch (Exception restoreException) {
                    // Log but don't throw - original exception is more important
                }
            }
            throw new RuntimeException("Failed to inline type definition: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            // Remove the inline type definition
            if (createdInlineType != null && createdInlineType.getParentNode() != null) {
                createdInlineType.getParentNode().removeChild(createdInlineType);
            }

            // Restore the type attribute
            if (originalTypeAttribute != null && !originalTypeAttribute.isEmpty()) {
                elementWithTypeReference.setAttribute("type", originalTypeAttribute);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo type inlining: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        String elementName = elementWithTypeReference.getAttribute("name");
        return "Inline type '" + globalTypeName + "' into element '" + elementName + "'";
    }

    /**
     * Find global type definition in the schema
     */
    private Element findGlobalType(String typeName) {
        Element schemaRoot = findSchemaRoot();
        if (schemaRoot == null) {
            return null;
        }

        // First check for complexType
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == schemaRoot && typeName.equals(complexType.getAttribute("name"))) {
                return complexType;
            }
        }

        // Then check for simpleType
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (simpleType.getParentNode() == schemaRoot && typeName.equals(simpleType.getAttribute("name"))) {
                return simpleType;
            }
        }

        return null;
    }

    /**
     * Find the schema root element
     */
    private Element findSchemaRoot() {
        org.w3c.dom.Node current = elementWithTypeReference;
        while (current != null) {
            if (current instanceof Element element && "schema".equals(element.getLocalName())) {
                return element;
            }
            current = current.getParentNode();
        }
        return null;
    }

    /**
     * Static helper method to check if an element can have its type inlined
     */
    public static boolean canInlineType(Element element, XsdDomManipulator domManipulator) {
        if (element == null || !"element".equals(element.getLocalName())) {
            return false;
        }

        // Element must have a type attribute referencing a global type
        String typeAttribute = element.getAttribute("type");
        if (typeAttribute == null || typeAttribute.isEmpty()) {
            return false;
        }

        // Don't inline built-in XSD types
        if (typeAttribute.startsWith("xs:") || typeAttribute.startsWith("xsd:")) {
            return false;
        }

        // Check if the referenced global type exists
        Element schemaRoot = findSchemaRootStatic(element);
        if (schemaRoot == null) {
            return false;
        }

        return findGlobalTypeStatic(schemaRoot, typeAttribute) != null;
    }

    /**
     * Static helper to find schema root
     */
    private static Element findSchemaRootStatic(Element element) {
        org.w3c.dom.Node current = element;
        while (current != null) {
            if (current instanceof Element el && "schema".equals(el.getLocalName())) {
                return el;
            }
            current = current.getParentNode();
        }
        return null;
    }

    /**
     * Static helper to find global type
     */
    private static Element findGlobalTypeStatic(Element schemaRoot, String typeName) {
        // Check complexTypes
        NodeList complexTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element complexType = (Element) complexTypes.item(i);
            if (complexType.getParentNode() == schemaRoot && typeName.equals(complexType.getAttribute("name"))) {
                return complexType;
            }
        }

        // Check simpleTypes
        NodeList simpleTypes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element simpleType = (Element) simpleTypes.item(i);
            if (simpleType.getParentNode() == schemaRoot && typeName.equals(simpleType.getAttribute("name"))) {
                return simpleType;
            }
        }

        return null;
    }
}