package org.fxt.freexmltoolkit.controls;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adapter class to integrate existing XSD functionality with the new
 * Enhanced IntelliSense system.
 */
public class XsdIntegrationAdapter {

    private static final Logger logger = LogManager.getLogger(XsdIntegrationAdapter.class);

    private XmlEditor xmlEditor;
    private boolean hasSchema = false;

    public XsdIntegrationAdapter() {
        // Default constructor
    }

    public XsdIntegrationAdapter(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
        updateSchemaStatus();
    }

    /**
     * Set the XML editor reference
     */
    public void setXmlEditor(XmlEditor xmlEditor) {
        this.xmlEditor = xmlEditor;
        updateSchemaStatus();
    }

    /**
     * Refresh schema information
     */
    public void refreshSchema() {
        updateSchemaStatus();
        logger.debug("Schema information refreshed");
    }

    /**
     * Check if XSD schema is available
     */
    public boolean hasSchema() {
        return hasSchema;
    }

    /**
     * Update schema availability status
     */
    private void updateSchemaStatus() {
        if (xmlEditor != null) {
            try {
                // Check if XSD documentation data is available
                Object xsdData = getXsdDocumentationData();
                hasSchema = (xsdData != null);
                logger.debug("Schema status updated: hasSchema = {}", hasSchema);
            } catch (Exception e) {
                logger.debug("Error checking schema status: {}", e.getMessage());
                hasSchema = false;
            }
        } else {
            hasSchema = false;
        }
    }

    /**
     * Get XSD documentation data from the XML editor
     */
    public Object getXsdDocumentationData() {
        if (xmlEditor != null) {
            try {
                // Use reflection to access the XSD documentation data
                java.lang.reflect.Method getXsdDataMethod = xmlEditor.getClass().getMethod("getXsdDocumentationData");
                return getXsdDataMethod.invoke(xmlEditor);
            } catch (Exception e) {
                logger.debug("Could not access XSD documentation data: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * Get available elements from the schema
     */
    public java.util.List<String> getAvailableElements() {
        if (xmlEditor != null) {
            try {
                // Access the available element names
                java.lang.reflect.Field availableElementsField = xmlEditor.getClass()
                        .getDeclaredField("availableElementNames");
                availableElementsField.setAccessible(true);

                @SuppressWarnings("unchecked")
                java.util.List<String> elements = (java.util.List<String>) availableElementsField.get(xmlEditor);
                return elements != null ? new java.util.ArrayList<>(elements) : new java.util.ArrayList<>();
            } catch (Exception e) {
                logger.debug("Could not get available elements: {}", e.getMessage());
                return new java.util.ArrayList<>();
            }
        }
        return new java.util.ArrayList<>();
    }
}