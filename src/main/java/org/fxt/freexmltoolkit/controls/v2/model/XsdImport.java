package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD import element (xs:import).
 * Import brings components from a different target namespace into the current schema.
 * It allows referencing types, elements, and other components defined in external schemas
 * with different target namespaces.
 *
 * @since 2.0
 */
public class XsdImport extends XsdNode {

    private String namespace; // Target namespace of the imported schema
    private String schemaLocation; // Optional URI of the schema to import

    /**
     * Creates a new XSD import element with default name.
     */
    public XsdImport() {
        super("import");
    }

    /**
     * Creates a new XSD import element with namespace.
     *
     * @param namespace the target namespace of the imported schema
     */
    public XsdImport(String namespace) {
        super("import");
        this.namespace = namespace;
    }

    /**
     * Creates a new XSD import element with namespace and schema location.
     *
     * @param namespace      the target namespace of the imported schema
     * @param schemaLocation the URI of the schema to import (optional)
     */
    public XsdImport(String namespace, String schemaLocation) {
        super("import");
        this.namespace = namespace;
        this.schemaLocation = schemaLocation;
    }

    /**
     * Gets the namespace attribute.
     *
     * @return the target namespace, or null
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace attribute.
     *
     * @param namespace the target namespace of the imported schema
     */
    public void setNamespace(String namespace) {
        String oldValue = this.namespace;
        this.namespace = namespace;
        pcs.firePropertyChange("namespace", oldValue, namespace);
    }

    /**
     * Gets the schemaLocation attribute.
     *
     * @return the schema location URI, or null
     */
    public String getSchemaLocation() {
        return schemaLocation;
    }

    /**
     * Sets the schemaLocation attribute.
     *
     * @param schemaLocation the URI of the schema to import (optional)
     */
    public void setSchemaLocation(String schemaLocation) {
        String oldValue = this.schemaLocation;
        this.schemaLocation = schemaLocation;
        pcs.firePropertyChange("schemaLocation", oldValue, schemaLocation);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.IMPORT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdImport copy = new XsdImport(this.namespace, this.schemaLocation);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
