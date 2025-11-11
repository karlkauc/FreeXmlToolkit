package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD include element (xs:include).
 * Include brings components from another schema document with the same target namespace
 * into the current schema. It allows splitting a large schema into multiple files
 * while maintaining the same target namespace.
 *
 * Unlike import (which is for different namespaces), include is for schemas with the
 * same target namespace or no target namespace.
 *
 * @since 2.0
 */
public class XsdInclude extends XsdNode {

    private String schemaLocation; // URI of the schema to include

    /**
     * Creates a new XSD include element with default name.
     */
    public XsdInclude() {
        super("include");
    }

    /**
     * Creates a new XSD include element with schema location.
     *
     * @param schemaLocation the URI of the schema to include
     */
    public XsdInclude(String schemaLocation) {
        super("include");
        this.schemaLocation = schemaLocation;
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
     * @param schemaLocation the URI of the schema to include
     */
    public void setSchemaLocation(String schemaLocation) {
        String oldValue = this.schemaLocation;
        this.schemaLocation = schemaLocation;
        pcs.firePropertyChange("schemaLocation", oldValue, schemaLocation);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.INCLUDE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdInclude copy = new XsdInclude(this.schemaLocation);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
