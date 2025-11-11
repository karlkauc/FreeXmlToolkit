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
public class XsdOverride extends XsdNode {

    private String schemaLocation; // URI of the schema to override

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
     * @param schemaLocation the URI of the schema to override
     */
    public void setSchemaLocation(String schemaLocation) {
        String oldValue = this.schemaLocation;
        this.schemaLocation = schemaLocation;
        pcs.firePropertyChange("schemaLocation", oldValue, schemaLocation);
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
    public XsdNode deepCopy(String suffix) {
        XsdOverride copy = new XsdOverride(this.schemaLocation);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
