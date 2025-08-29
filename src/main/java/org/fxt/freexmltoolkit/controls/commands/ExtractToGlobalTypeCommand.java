package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Command for extracting inline type definitions to global types.
 * Converts anonymous types to named global types and updates references.
 */
public class ExtractToGlobalTypeCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(ExtractToGlobalTypeCommand.class);

    private final XsdDomManipulator domManipulator;
    private final Element inlineTypeElement;
    private final String newTypeName;
    private final boolean isComplexType;

    private String backupXml;
    private Element extractedGlobalType;
    private final Element originalParent;
    private boolean wasExecuted = false;

    public ExtractToGlobalTypeCommand(XsdDomManipulator domManipulator,
                                      Element inlineTypeElement,
                                      String newTypeName) {
        this.domManipulator = domManipulator;
        this.inlineTypeElement = inlineTypeElement;
        this.newTypeName = newTypeName;
        this.isComplexType = "complexType".equals(inlineTypeElement.getLocalName()) ||
                "complexType".equals(inlineTypeElement.getTagName());
        this.originalParent = (Element) inlineTypeElement.getParentNode();
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Extracting inline {} to global type: {}",
                    isComplexType ? "complexType" : "simpleType", newTypeName);

            // Create backup
            backupXml = domManipulator.getXmlContent();

            // Check if type name already exists
            if (typeNameExists(newTypeName)) {
                logger.error("Type name '{}' already exists", newTypeName);
                return false;
            }

            Document doc = domManipulator.getDocument();
            Element schemaRoot = getSchemaRoot(doc);

            if (schemaRoot == null) {
                logger.error("Could not find schema root element");
                return false;
            }

            // Clone the inline type to create the global type
            extractedGlobalType = (Element) inlineTypeElement.cloneNode(true);

            // Add name attribute to make it global
            extractedGlobalType.setAttribute("name", newTypeName);

            // Find appropriate insertion point in schema
            Element insertionPoint = findGlobalTypeInsertionPoint(schemaRoot, isComplexType);

            if (insertionPoint != null) {
                schemaRoot.insertBefore(extractedGlobalType, insertionPoint);
            } else {
                schemaRoot.appendChild(extractedGlobalType);
            }

            // Replace the inline type with a type reference
            replaceInlineTypeWithReference();

            wasExecuted = true;
            logger.info("Successfully extracted type to: {}", newTypeName);
            return true;

        } catch (Exception e) {
            logger.error("Error extracting type to global: " + newTypeName, e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (wasExecuted && backupXml != null) {
                domManipulator.loadXsd(backupXml);
                logger.info("Undid extraction of type: {}", newTypeName);
                wasExecuted = false;
                extractedGlobalType = null;
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.error("Error undoing type extraction: " + newTypeName, e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return String.format("Extract %s to global type '%s'",
                isComplexType ? "complexType" : "simpleType", newTypeName);
    }

    @Override
    public boolean canUndo() {
        return wasExecuted && backupXml != null;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    /**
     * Check if a type name already exists in the schema
     */
    private boolean typeNameExists(String typeName) {
        Document doc = domManipulator.getDocument();
        if (doc == null) return false;

        // Check simple types
        var simpleTypes = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "simpleType");
        for (int i = 0; i < simpleTypes.getLength(); i++) {
            Element element = (Element) simpleTypes.item(i);
            if (typeName.equals(element.getAttribute("name"))) {
                return true;
            }
        }

        // Check complex types
        var complexTypes = doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "complexType");
        for (int i = 0; i < complexTypes.getLength(); i++) {
            Element element = (Element) complexTypes.item(i);
            if (typeName.equals(element.getAttribute("name"))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get the schema root element
     */
    private Element getSchemaRoot(Document doc) {
        Element root = doc.getDocumentElement();
        if ("schema".equals(root.getLocalName()) || "schema".equals(root.getTagName())) {
            return root;
        }
        return null;
    }

    /**
     * Find appropriate insertion point for the global type
     */
    private Element findGlobalTypeInsertionPoint(Element schemaRoot, boolean isComplexType) {
        var children = schemaRoot.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element elem = (Element) child;
                String localName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

                // Insert complex types before elements, simple types can go before complex types
                if (isComplexType) {
                    if ("element".equals(localName) || "group".equals(localName) ||
                            "attributeGroup".equals(localName)) {
                        return elem;
                    }
                } else {
                    if ("complexType".equals(localName) || "element".equals(localName) ||
                            "group".equals(localName) || "attributeGroup".equals(localName)) {
                        return elem;
                    }
                }
            }
        }

        return null; // Insert at end
    }

    /**
     * Replace the inline type with a type reference
     */
    private void replaceInlineTypeWithReference() {
        if (originalParent == null) return;

        // Remove the inline type
        originalParent.removeChild(inlineTypeElement);

        // Determine the appropriate namespace prefix
        String prefix = determineNamespacePrefix();
        String typeReference = prefix.isEmpty() ? newTypeName : prefix + ":" + newTypeName;

        // Add type attribute to parent element
        originalParent.setAttribute("type", typeReference);

        logger.debug("Replaced inline type with reference: {}", typeReference);
    }

    /**
     * Determine the appropriate namespace prefix for the schema
     */
    private String determineNamespacePrefix() {
        Document doc = domManipulator.getDocument();
        Element schemaRoot = getSchemaRoot(doc);

        if (schemaRoot == null) return "";

        // Look for target namespace and its prefix
        String targetNamespace = schemaRoot.getAttribute("targetNamespace");
        if (targetNamespace == null || targetNamespace.isEmpty()) {
            return ""; // No target namespace, use unqualified names
        }

        // Find the prefix used for the target namespace
        var attributes = schemaRoot.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith("xmlns:") &&
                    targetNamespace.equals(attr.getNodeValue())) {
                return attr.getNodeName().substring(6); // Remove "xmlns:"
            }
        }

        // If no explicit prefix found, check if default namespace matches target
        String defaultNamespace = schemaRoot.getAttribute("xmlns");
        if (targetNamespace.equals(defaultNamespace)) {
            return ""; // Use default namespace (unqualified)
        }

        // Fallback: use "tns" as prefix for target namespace
        return "tns";
    }

    /**
     * Get the name of the new global type
     */
    public String getNewTypeName() {
        return newTypeName;
    }

    /**
     * Get the original inline type element
     */
    public Element getInlineTypeElement() {
        return inlineTypeElement;
    }

    /**
     * Check if this is a complex type extraction
     */
    public boolean isComplexType() {
        return isComplexType;
    }
}