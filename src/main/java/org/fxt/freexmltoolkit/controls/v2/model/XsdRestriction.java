package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an XSD restriction (xs:restriction).
 * Used in both simple type and complex content definitions to constrain base types.
 *
 * @since 2.0
 */
public class XsdRestriction extends XsdNode {

    private String base; // Base type being restricted
    private final List<XsdFacet> facets = new ArrayList<>();

    /**
     * Creates a new XSD restriction.
     */
    public XsdRestriction() {
        super("restriction");
    }

    /**
     * Creates a new XSD restriction with a base type.
     *
     * @param base the base type
     */
    public XsdRestriction(String base) {
        super("restriction");
        this.base = base;
    }

    /**
     * Gets the base type.
     *
     * @return the base type, or null
     */
    public String getBase() {
        return base;
    }

    /**
     * Sets the base type.
     *
     * @param base the base type
     */
    public void setBase(String base) {
        String oldValue = this.base;
        this.base = base;
        pcs.firePropertyChange("base", oldValue, base);
    }

    /**
     * Gets all facets in this restriction.
     *
     * @return a copy of the facet list
     */
    public List<XsdFacet> getFacets() {
        return new ArrayList<>(facets);
    }

    /**
     * Adds a facet to this restriction.
     *
     * @param facet the facet to add
     */
    public void addFacet(XsdFacet facet) {
        List<XsdFacet> oldValue = new ArrayList<>(facets);
        facets.add(facet);
        addChild(facet);
        pcs.firePropertyChange("facets", oldValue, new ArrayList<>(facets));
    }

    /**
     * Removes a facet from this restriction.
     *
     * @param facet the facet to remove
     */
    public void removeFacet(XsdFacet facet) {
        List<XsdFacet> oldValue = new ArrayList<>(facets);
        facets.remove(facet);
        removeChild(facet);
        pcs.firePropertyChange("facets", oldValue, new ArrayList<>(facets));
    }

    /**
     * Finds a facet by type.
     *
     * @param type the facet type to find
     * @return the facet, or null if not found
     */
    public XsdFacet getFacetByType(XsdFacetType type) {
        return facets.stream()
                .filter(f -> f.getFacetType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if this restriction has a specific facet type.
     *
     * @param type the facet type to check
     * @return true if the facet exists
     */
    public boolean hasFacet(XsdFacetType type) {
        return getFacetByType(type) != null;
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.RESTRICTION;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Restriction name is always "restriction", suffix is not applied
        XsdRestriction copy = new XsdRestriction();

        // Copy XsdRestriction-specific properties
        copy.setBase(this.base);

        // Copy base properties and children (includes facets)
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
