package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD facet within a restriction.
 * Facets constrain the value space of simple types (e.g., minLength, pattern, enumeration).
 *
 * @since 2.0
 */
public class XsdFacet extends XsdNode {

    private XsdFacetType facetType;
    private String value;
    private boolean fixed; // If true, the facet cannot be changed in derived types

    /**
     * Creates a new XSD facet.
     */
    public XsdFacet() {
        super("facet");
    }

    /**
     * Creates a new XSD facet with a specific type and value.
     *
     * @param facetType the facet type
     * @param value     the facet value
     */
    public XsdFacet(XsdFacetType facetType, String value) {
        super(facetType.getXmlName());
        this.facetType = facetType;
        this.value = value;
    }

    /**
     * Gets the facet type.
     *
     * @return the facet type
     */
    public XsdFacetType getFacetType() {
        return facetType;
    }

    /**
     * Sets the facet type.
     *
     * @param facetType the facet type
     */
    public void setFacetType(XsdFacetType facetType) {
        XsdFacetType oldValue = this.facetType;
        this.facetType = facetType;
        if (facetType != null) {
            setName(facetType.getXmlName());
        }
        pcs.firePropertyChange("facetType", oldValue, facetType);
    }

    /**
     * Gets the facet value.
     *
     * @return the facet value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the facet value.
     *
     * @param value the facet value
     */
    public void setValue(String value) {
        String oldValue = this.value;
        this.value = value;
        pcs.firePropertyChange("value", oldValue, value);
    }

    /**
     * Checks if this facet is fixed.
     *
     * @return true if fixed
     */
    public boolean isFixed() {
        return fixed;
    }

    /**
     * Sets the fixed flag.
     *
     * @param fixed true if fixed
     */
    public void setFixed(boolean fixed) {
        boolean oldValue = this.fixed;
        this.fixed = fixed;
        pcs.firePropertyChange("fixed", oldValue, fixed);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.FACET;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Facet uses facet type as name, suffix is not typically applied
        XsdFacet copy = new XsdFacet();

        // Copy XsdFacet-specific properties
        copy.setFacetType(this.facetType);
        copy.setValue(this.value);
        copy.setFixed(this.fixed);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
