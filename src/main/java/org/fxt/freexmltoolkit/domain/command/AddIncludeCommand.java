package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command to add an include statement to an XSD schema.
 * Include statements allow including definitions from another schema
 * with the same target namespace.
 */
public class AddIncludeCommand implements XsdCommand {
    private final Document xsdDocument;
    private final String schemaLocation;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element createdIncludeElement;
    private boolean wasExecuted = false;

    public AddIncludeCommand(Document xsdDocument, String schemaLocation, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.schemaLocation = schemaLocation;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Validate input
            if (schemaLocation == null || schemaLocation.trim().isEmpty()) {
                throw new IllegalArgumentException("Schema location is required for include");
            }

            // Find schema root element
            Element schemaRoot = findSchemaRoot();
            if (schemaRoot == null) {
                throw new IllegalStateException("Cannot find schema root element");
            }

            // Check if include already exists
            if (includeAlreadyExists(schemaRoot, schemaLocation)) {
                throw new IllegalArgumentException("Include for schema location '" + schemaLocation + "' already exists");
            }

            // Create include element
            createdIncludeElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:include");
            createdIncludeElement.setAttribute("schemaLocation", schemaLocation);

            // Insert include at correct position (after annotation, before other declarations)
            insertIncludeAtCorrectPosition(schemaRoot, createdIncludeElement);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Cleanup on error
            if (createdIncludeElement != null && createdIncludeElement.getParentNode() != null) {
                createdIncludeElement.getParentNode().removeChild(createdIncludeElement);
            }
            throw new RuntimeException("Failed to add include: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            if (createdIncludeElement != null && createdIncludeElement.getParentNode() != null) {
                createdIncludeElement.getParentNode().removeChild(createdIncludeElement);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo include addition: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        return "Add include for schema '" + schemaLocation + "'";
    }

    private Element findSchemaRoot() {
        Element root = xsdDocument.getDocumentElement();
        if (root != null && "schema".equals(root.getLocalName())) {
            return root;
        }
        return null;
    }

    private boolean includeAlreadyExists(Element schemaRoot, String targetSchemaLocation) {
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");

        for (int i = 0; i < includes.getLength(); i++) {
            Element includeElement = (Element) includes.item(i);
            String existingLocation = includeElement.getAttribute("schemaLocation");

            if (targetSchemaLocation.equals(existingLocation)) {
                return true;
            }
        }

        return false;
    }

    private void insertIncludeAtCorrectPosition(Element schemaRoot, Element includeElement) {
        // XSD order: annotation?, (import | include | redefine)*
        // Insert include after any existing imports/includes but before type definitions

        Node insertBefore = null;
        NodeList children = schemaRoot.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                String localName = childElement.getLocalName();

                // Insert before first type definition, group, element, attribute, or notation
                if ("simpleType".equals(localName) || "complexType".equals(localName) ||
                        "group".equals(localName) || "attributeGroup".equals(localName) ||
                        "element".equals(localName) || "attribute".equals(localName) ||
                        "notation".equals(localName)) {
                    insertBefore = child;
                    break;
                }
            }
        }

        if (insertBefore != null) {
            schemaRoot.insertBefore(includeElement, insertBefore);
        } else {
            schemaRoot.appendChild(includeElement);
        }
    }
}