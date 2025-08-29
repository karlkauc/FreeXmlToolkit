package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command to add an import statement to an XSD schema.
 * Import statements allow referencing types and elements from other namespaces.
 */
public class AddImportCommand implements XsdCommand {
    private final Document xsdDocument;
    private final String namespace;
    private final String schemaLocation;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element createdImportElement;
    private boolean wasExecuted = false;

    public AddImportCommand(Document xsdDocument, String namespace, String schemaLocation, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.namespace = namespace;
        this.schemaLocation = schemaLocation;
        this.domManipulator = domManipulator;
    }

    @Override
    public boolean execute() {
        if (wasExecuted) {
            return false;
        }

        try {
            // Find schema root element
            Element schemaRoot = findSchemaRoot();
            if (schemaRoot == null) {
                throw new IllegalStateException("Cannot find schema root element");
            }

            // Check if import already exists
            if (importAlreadyExists(schemaRoot, namespace)) {
                throw new IllegalArgumentException("Import for namespace '" + namespace + "' already exists");
            }

            // Create import element
            createdImportElement = xsdDocument.createElementNS("http://www.w3.org/2001/XMLSchema", "xs:import");

            if (namespace != null && !namespace.isEmpty()) {
                createdImportElement.setAttribute("namespace", namespace);
            }

            if (schemaLocation != null && !schemaLocation.isEmpty()) {
                createdImportElement.setAttribute("schemaLocation", schemaLocation);
            }

            // Insert import at correct position (after annotation, before other declarations)
            insertImportAtCorrectPosition(schemaRoot, createdImportElement);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            // Cleanup on error
            if (createdImportElement != null && createdImportElement.getParentNode() != null) {
                createdImportElement.getParentNode().removeChild(createdImportElement);
            }
            throw new RuntimeException("Failed to add import: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted) {
            return false;
        }

        try {
            if (createdImportElement != null && createdImportElement.getParentNode() != null) {
                createdImportElement.getParentNode().removeChild(createdImportElement);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo import addition: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        if (namespace != null && !namespace.isEmpty()) {
            return "Add import for namespace '" + namespace + "'";
        } else {
            return "Add schema import";
        }
    }

    private Element findSchemaRoot() {
        Element root = xsdDocument.getDocumentElement();
        if (root != null && "schema".equals(root.getLocalName())) {
            return root;
        }
        return null;
    }

    private boolean importAlreadyExists(Element schemaRoot, String targetNamespace) {
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");

        for (int i = 0; i < imports.getLength(); i++) {
            Element importElement = (Element) imports.item(i);
            String existingNamespace = importElement.getAttribute("namespace");

            // Check for namespace match (empty namespace is also valid)
            if ((targetNamespace == null || targetNamespace.isEmpty()) &&
                    (existingNamespace == null || existingNamespace.isEmpty())) {
                return true;
            }

            if (targetNamespace != null && targetNamespace.equals(existingNamespace)) {
                return true;
            }
        }

        return false;
    }

    private void insertImportAtCorrectPosition(Element schemaRoot, Element importElement) {
        // XSD order: annotation?, (import | include | redefine)*, 
        // ((simpleType | complexType | group | attributeGroup), annotation*)*, 
        // ((element | attribute | notation), annotation*)*

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
            schemaRoot.insertBefore(importElement, insertBefore);
        } else {
            schemaRoot.appendChild(importElement);
        }
    }
}