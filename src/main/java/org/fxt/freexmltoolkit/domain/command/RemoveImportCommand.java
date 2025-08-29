package org.fxt.freexmltoolkit.domain.command;

import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Command to remove an import statement from an XSD schema.
 * Provides undo functionality by backing up the removed element.
 */
public class RemoveImportCommand implements XsdCommand {
    private final Document xsdDocument;
    private final String namespace;
    private final XsdDomManipulator domManipulator;

    // Backup data for undo
    private Element removedImportElement;
    private Node nextSibling;
    private Element parentElement;
    private boolean wasExecuted = false;

    public RemoveImportCommand(Document xsdDocument, String namespace, XsdDomManipulator domManipulator) {
        this.xsdDocument = xsdDocument;
        this.namespace = namespace;
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

            // Find the import element to remove
            Element importToRemove = findImportElement(schemaRoot, namespace);
            if (importToRemove == null) {
                throw new IllegalArgumentException("Import for namespace '" + namespace + "' not found");
            }

            // Backup for undo
            removedImportElement = (Element) importToRemove.cloneNode(true);
            parentElement = (Element) importToRemove.getParentNode();
            nextSibling = importToRemove.getNextSibling();

            // Remove the import element
            parentElement.removeChild(importToRemove);

            wasExecuted = true;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to remove import: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean undo() {
        if (!wasExecuted || removedImportElement == null || parentElement == null) {
            return false;
        }

        try {
            // Restore the import element at the original position
            if (nextSibling != null) {
                parentElement.insertBefore(removedImportElement, nextSibling);
            } else {
                parentElement.appendChild(removedImportElement);
            }

            wasExecuted = false;
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to undo import removal: " + e.getMessage(), e);
        }
    }

    @Override
    public String getDescription() {
        if (namespace != null && !namespace.isEmpty()) {
            return "Remove import for namespace '" + namespace + "'";
        } else {
            return "Remove import statement";
        }
    }

    private Element findSchemaRoot() {
        Element root = xsdDocument.getDocumentElement();
        if (root != null && "schema".equals(root.getLocalName())) {
            return root;
        }
        return null;
    }

    private Element findImportElement(Element schemaRoot, String targetNamespace) {
        NodeList imports = schemaRoot.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "import");

        for (int i = 0; i < imports.getLength(); i++) {
            Element importElement = (Element) imports.item(i);
            String existingNamespace = importElement.getAttribute("namespace");

            // Handle both empty and null namespace matching
            if ((targetNamespace == null || targetNamespace.isEmpty()) &&
                    (existingNamespace == null || existingNamespace.isEmpty())) {
                return importElement;
            }

            if (targetNamespace != null && targetNamespace.equals(existingNamespace)) {
                return importElement;
            }
        }

        return null;
    }
}