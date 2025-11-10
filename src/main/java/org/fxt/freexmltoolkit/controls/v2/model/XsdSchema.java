package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents an XSD schema (xs:schema) - the root element.
 *
 * @since 2.0
 */
public class XsdSchema extends XsdNode {

    private String targetNamespace;
    private String elementFormDefault = "qualified";
    private String attributeFormDefault = "unqualified";
    private final Map<String, String> namespaces = new HashMap<>();

    /**
     * Creates a new XSD schema.
     */
    public XsdSchema() {
        super("schema");
        // Default namespace for XSD
        namespaces.put("xs", "http://www.w3.org/2001/XMLSchema");
    }

    /**
     * Gets the target namespace.
     *
     * @return the target namespace, or null
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Sets the target namespace.
     *
     * @param targetNamespace the target namespace
     */
    public void setTargetNamespace(String targetNamespace) {
        String oldValue = this.targetNamespace;
        this.targetNamespace = targetNamespace;
        pcs.firePropertyChange("targetNamespace", oldValue, targetNamespace);
    }

    /**
     * Gets the elementFormDefault attribute.
     *
     * @return the elementFormDefault value
     */
    public String getElementFormDefault() {
        return elementFormDefault;
    }

    /**
     * Sets the elementFormDefault attribute.
     *
     * @param elementFormDefault the elementFormDefault value (qualified/unqualified)
     */
    public void setElementFormDefault(String elementFormDefault) {
        String oldValue = this.elementFormDefault;
        this.elementFormDefault = elementFormDefault;
        pcs.firePropertyChange("elementFormDefault", oldValue, elementFormDefault);
    }

    /**
     * Gets the attributeFormDefault attribute.
     *
     * @return the attributeFormDefault value
     */
    public String getAttributeFormDefault() {
        return attributeFormDefault;
    }

    /**
     * Sets the attributeFormDefault attribute.
     *
     * @param attributeFormDefault the attributeFormDefault value (qualified/unqualified)
     */
    public void setAttributeFormDefault(String attributeFormDefault) {
        String oldValue = this.attributeFormDefault;
        this.attributeFormDefault = attributeFormDefault;
        pcs.firePropertyChange("attributeFormDefault", oldValue, attributeFormDefault);
    }

    /**
     * Gets all namespace mappings.
     *
     * @return the namespace map (prefix -> URI)
     */
    public Map<String, String> getNamespaces() {
        return new HashMap<>(namespaces);
    }

    /**
     * Adds a namespace mapping.
     *
     * @param prefix the namespace prefix
     * @param uri    the namespace URI
     */
    public void addNamespace(String prefix, String uri) {
        namespaces.put(prefix, uri);
        pcs.firePropertyChange("namespaces", null, new HashMap<>(namespaces));
    }

    /**
     * Removes a namespace mapping.
     *
     * @param prefix the namespace prefix to remove
     */
    public void removeNamespace(String prefix) {
        namespaces.remove(prefix);
        pcs.firePropertyChange("namespaces", null, new HashMap<>(namespaces));
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.SCHEMA;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Schema name is always "schema", suffix is not applied
        XsdSchema copy = new XsdSchema();

        // Copy XsdSchema-specific properties
        copy.setTargetNamespace(this.targetNamespace);
        copy.setElementFormDefault(this.elementFormDefault);
        copy.setAttributeFormDefault(this.attributeFormDefault);

        // Copy namespaces
        for (var entry : this.namespaces.entrySet()) {
            copy.addNamespace(entry.getKey(), entry.getValue());
        }

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
