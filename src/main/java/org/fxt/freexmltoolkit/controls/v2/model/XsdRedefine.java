package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.List;

/**
 * Represents an XSD redefine element (xs:redefine).
 * Redefine allows including and modifying components from another schema document.
 * It combines the functionality of include with the ability to refine types, groups,
 * and attribute groups from the included schema.
 *
 * Note: xs:redefine is deprecated in XSD 1.1 in favor of xs:override.
 * However, it remains widely used in XSD 1.0 schemas.
 *
 * @since 2.0
 */
public class XsdRedefine extends XsdSchemaReference {

    private XsdSchema referencedSchema; // The file this builds on, kept apart from the redefining schema

    /**
     * Creates a new XSD redefine element with default name.
     */
    public XsdRedefine() {
        super("redefine");
    }

    /**
     * Creates a new XSD redefine element with schema location.
     *
     * @param schemaLocation the URI of the schema to redefine
     */
    public XsdRedefine(String schemaLocation) {
        super("redefine");
        this.schemaLocation = schemaLocation;
    }

    /**
     * Gets all redefined element declarations.
     *
     * @return list of redefined elements
     */
    public List<XsdElement> getElements() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdElement)
                .map(child -> (XsdElement) child)
                .toList();
    }

    /**
     * Gets all redefined complex type definitions.
     *
     * @return list of redefined complex types
     */
    public List<XsdComplexType> getComplexTypes() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdComplexType)
                .map(child -> (XsdComplexType) child)
                .toList();
    }

    /**
     * Gets all redefined simple type definitions.
     *
     * @return list of redefined simple types
     */
    public List<XsdSimpleType> getSimpleTypes() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdSimpleType)
                .map(child -> (XsdSimpleType) child)
                .toList();
    }

    /**
     * Gets all redefined group definitions.
     *
     * @return list of redefined groups
     */
    public List<XsdGroup> getGroups() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdGroup)
                .map(child -> (XsdGroup) child)
                .toList();
    }

    /**
     * Gets all redefined attribute group definitions.
     *
     * @return list of redefined attribute groups
     */
    public List<XsdAttributeGroup> getAttributeGroups() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdAttributeGroup)
                .map(child -> (XsdAttributeGroup) child)
                .toList();
    }

    @Override
    public XsdSchema getReferencedSchema() {
        return referencedSchema;
    }

    @Override
    public void setReferencedSchema(XsdSchema schema) {
        this.referencedSchema = schema;
        this.resolved = schema != null;
    }

    @Override
    protected void clearReferencedSchema() {
        this.referencedSchema = null;
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.REDEFINE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdRedefine copy = new XsdRedefine(this.schemaLocation);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        // The target is the same file for the copy, like the imported schema of an XsdImport
        copy.referencedSchema = this.referencedSchema;
        copy.resolved = this.resolved;
        copy.resolvedPath = this.resolvedPath;
        copy.resolutionError = this.resolutionError;

        return copy;
    }
}
