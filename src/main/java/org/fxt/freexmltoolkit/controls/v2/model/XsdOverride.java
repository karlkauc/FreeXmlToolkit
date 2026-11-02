package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.List;

/**
 * Represents an XSD 1.1 override element (xs:override).
 * Override is the XSD 1.1 replacement for the deprecated xs:redefine mechanism.
 * It allows schema components from another schema to be overridden or modified.
 *
 * The override element can contain new definitions for types, groups, and other
 * schema components that replace definitions in the target schema.
 *
 * @since 2.0
 */
public class XsdOverride extends XsdSchemaReference {

    private XsdSchema referencedSchema; // The file this replaces parts of, kept apart from the overriding schema

    /**
     * Creates a new XSD override element with default name.
     */
    public XsdOverride() {
        super("override");
    }

    /**
     * Creates a new XSD override element with schema location.
     *
     * @param schemaLocation the URI of the schema to override
     */
    public XsdOverride(String schemaLocation) {
        super("override");
        this.schemaLocation = schemaLocation;
    }

    /**
     * Gets all overriding component definitions (elements, types, groups, etc.)
     * as child nodes.
     *
     * @return list of component definitions
     */
    public List<XsdNode> getComponents() {
        return getChildren();
    }

    /**
     * Gets overriding element definitions.
     *
     * @return list of XsdElement components
     */
    public List<XsdElement> getElements() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdElement)
                .map(child -> (XsdElement) child)
                .toList();
    }

    /**
     * Gets overriding complexType definitions.
     *
     * @return list of XsdComplexType components
     */
    public List<XsdComplexType> getComplexTypes() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdComplexType)
                .map(child -> (XsdComplexType) child)
                .toList();
    }

    /**
     * Gets overriding simpleType definitions.
     *
     * @return list of XsdSimpleType components
     */
    public List<XsdSimpleType> getSimpleTypes() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdSimpleType)
                .map(child -> (XsdSimpleType) child)
                .toList();
    }

    /**
     * Gets overriding group definitions.
     *
     * @return list of XsdGroup components
     */
    public List<XsdGroup> getGroups() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdGroup)
                .map(child -> (XsdGroup) child)
                .toList();
    }

    /**
     * Gets overriding attributeGroup definitions.
     *
     * @return list of XsdAttributeGroup components
     */
    public List<XsdAttributeGroup> getAttributeGroups() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdAttributeGroup)
                .map(child -> (XsdAttributeGroup) child)
                .toList();
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.OVERRIDE;
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
    public XsdNode deepCopy(String suffix) {
        XsdOverride copy = new XsdOverride(this.schemaLocation);

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
