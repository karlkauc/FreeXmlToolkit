package org.fxt.freexmltoolkit.controls.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.NamespaceResult;
import org.fxt.freexmltoolkit.controls.XsdCommand;
import org.fxt.freexmltoolkit.service.XsdDomManipulator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Command for updating namespace declarations and settings in XSD schema
 */
public class UpdateNamespacesCommand implements XsdCommand {

    private static final Logger logger = LogManager.getLogger(UpdateNamespacesCommand.class);

    private final XsdDomManipulator domManipulator;
    private final NamespaceResult newNamespaceConfig;

    // Backup for undo
    private NamespaceResult originalConfig;

    public UpdateNamespacesCommand(XsdDomManipulator domManipulator, NamespaceResult namespaceConfig) {
        this.domManipulator = domManipulator;
        this.newNamespaceConfig = namespaceConfig;
    }

    @Override
    public boolean execute() {
        try {
            logger.info("Updating namespace configuration");

            Document doc = domManipulator.getDocument();
            Element schemaElement = doc.getDocumentElement();

            if (schemaElement == null || !"schema".equals(schemaElement.getLocalName())) {
                logger.error("Schema root element not found");
                return false;
            }

            // Backup current configuration
            originalConfig = extractCurrentNamespaceConfig(schemaElement);

            // Apply new configuration
            applyNamespaceConfiguration(schemaElement, newNamespaceConfig);

            logger.info("Successfully updated namespace configuration");
            return true;

        } catch (Exception e) {
            logger.error("Error updating namespaces", e);
            return false;
        }
    }

    @Override
    public boolean undo() {
        try {
            if (originalConfig == null) {
                logger.warn("No original namespace configuration to restore");
                return false;
            }

            Document doc = domManipulator.getDocument();
            Element schemaElement = doc.getDocumentElement();

            if (schemaElement != null) {
                applyNamespaceConfiguration(schemaElement, originalConfig);
                logger.info("Restored original namespace configuration");
                return true;
            }

            return false;

        } catch (Exception e) {
            logger.error("Error undoing namespace changes", e);
            return false;
        }
    }

    @Override
    public String getDescription() {
        return "Update namespace configuration";
    }

    @Override
    public boolean canUndo() {
        return true;
    }

    @Override
    public boolean isModifying() {
        return true;
    }

    /**
     * Extracts current namespace configuration from schema element
     */
    private NamespaceResult extractCurrentNamespaceConfig(Element schemaElement) {
        String targetNamespace = schemaElement.getAttribute("targetNamespace");
        String defaultNamespace = schemaElement.getAttribute("xmlns");

        boolean elementFormDefault = "qualified".equals(schemaElement.getAttribute("elementFormDefault"));
        boolean attributeFormDefault = "qualified".equals(schemaElement.getAttribute("attributeFormDefault"));

        Map<String, String> mappings = new HashMap<>();
        NamedNodeMap attributes = schemaElement.getAttributes();

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();

            if (attrName.startsWith("xmlns:")) {
                String prefix = attrName.substring(6);
                mappings.put(prefix, attr.getNodeValue());
            }
        }

        return new NamespaceResult(targetNamespace, defaultNamespace, elementFormDefault, attributeFormDefault, mappings);
    }

    /**
     * Applies namespace configuration to schema element
     */
    private void applyNamespaceConfiguration(Element schemaElement, NamespaceResult config) {
        // Update target namespace
        if (config.targetNamespace() != null && !config.targetNamespace().isEmpty()) {
            schemaElement.setAttribute("targetNamespace", config.targetNamespace());
        } else {
            schemaElement.removeAttribute("targetNamespace");
        }

        // Update default namespace
        if (config.defaultNamespace() != null && !config.defaultNamespace().isEmpty()) {
            schemaElement.setAttribute("xmlns", config.defaultNamespace());
        } else {
            schemaElement.removeAttribute("xmlns");
        }

        // Update form defaults
        if (config.elementFormDefault()) {
            schemaElement.setAttribute("elementFormDefault", "qualified");
        } else {
            schemaElement.removeAttribute("elementFormDefault");
        }

        if (config.attributeFormDefault()) {
            schemaElement.setAttribute("attributeFormDefault", "qualified");
        } else {
            schemaElement.removeAttribute("attributeFormDefault");
        }

        // Remove existing namespace declarations (except xmlns)
        NamedNodeMap attributes = schemaElement.getAttributes();
        java.util.List<String> attrsToRemove = new java.util.ArrayList<>();

        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if (attr.getNodeName().startsWith("xmlns:")) {
                attrsToRemove.add(attr.getNodeName());
            }
        }

        for (String attrName : attrsToRemove) {
            schemaElement.removeAttribute(attrName);
        }

        // Add new namespace mappings
        for (Map.Entry<String, String> mapping : config.namespaceMappings().entrySet()) {
            String prefix = mapping.getKey();
            String namespaceUri = mapping.getValue();

            if (prefix != null && !prefix.isEmpty() && namespaceUri != null && !namespaceUri.isEmpty()) {
                schemaElement.setAttribute("xmlns:" + prefix, namespaceUri);
            }
        }

        logger.debug("Applied namespace configuration with {} mappings", config.namespaceMappings().size());
    }
}