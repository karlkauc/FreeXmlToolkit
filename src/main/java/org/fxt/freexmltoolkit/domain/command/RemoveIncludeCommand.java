package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command to remove an include statement from an XSD schema.
 * Provides undo functionality by backing up the removed element.
 */
public class RemoveIncludeCommand implements XsdCommand {
    private final Document xsdDocument;
    private final String schemaLocation;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element removedIncludeElement;
    private Node nextSibling;
    private Element parentElement;
    private boolean wasExecuted = false;

    public RemoveIncludeCommand(Document xsdDocument, String schemaLocation, XsdDomManipulator domManipulator) {
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
                throw new IllegalArgumentException("Schema location is required to identify include");
            }

            // Find schema root element
            Element schemaRoot = findSchemaRoot();
            if (schemaRoot == null) {
                throw new IllegalStateException("Cannot find schema root element");
            }

            // Find the include element to remove
            Element includeToRemove = findIncludeElement(schemaRoot, schemaLocation);
            if (includeToRemove == null) {
                throw new IllegalArgumentException("Include for schema location '" + schemaLocation + "' not found");
            }

            // Backup for undo
            removedIncludeElement = (Element) includeToRemove.cloneNode(true);
            parentElement = (Element) includeToRemove.getParentNode();
            nextSibling = includeToRemove.getNextSibling();

            // Remove the include element
            parentElement.removeChild(includeToRemove);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to remove include: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted || removedIncludeElement == null || parentElement == null) {
            return false;
        }

        try {
            // Restore the include element at the original position
            if (nextSibling != null) {
                parentElement.insertBefore(removedIncludeElement, nextSibling);
            } else {
                parentElement.appendChild(removedIncludeElement);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo include removal: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        return "Remove include for schema '" + schemaLocation + "'";
    }

    private Element findSchemaRoot() {
        Element root = xsdDocument.getDocumentElement();
        if (root != null && "schema".equals(root.getLocalName())) {
            return root;
        }
        return null;
    }

    private Element findIncludeElement(Element schemaRoot, String targetSchemaLocation) {
        NodeList includes = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "include");

        for (int i = 0; i < includes.getLength(); i++) {
            Element includeElement = (Element) includes.item(i);
            String existingLocation = includeElement.getAttribute("schemaLocation");

            if (targetSchemaLocation.equals(existingLocation)) {
                return includeElement;
            }
        }

        return null;
    }
}