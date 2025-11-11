package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD simple type (xs:simpleType).
 * A simple type defines constraints on text-only content through restrictions, lists, or unions.
 *
 * @since 2.0
 */
public class XsdSimpleType extends XsdNode {

    private String base; // Base type for restrictions
    private boolean isFinal; // final attribute

    /**
     * Creates a new XSD simple type (inline, without name).
     */
    public XsdSimpleType() {
        super(null);
    }

    /**
     * Creates a new XSD simple type with a name.
     *
     * @param name the name of the simple type
     */
    public XsdSimpleType(String name) {
        super(name);
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
     * Checks if this simple type is final.
     *
     * @return true if final
     */
    public boolean isFinal() {
        return isFinal;
    }

    /**
     * Sets the final flag.
     *
     * @param isFinal true if final
     */
    public void setFinal(boolean isFinal) {
        boolean oldValue = this.isFinal;
        this.isFinal = isFinal;
        pcs.firePropertyChange("final", oldValue, isFinal);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.SIMPLE_TYPE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdSimpleType copy = new XsdSimpleType(newName);

        // Copy XsdSimpleType-specific properties
        copy.setBase(this.base);
        copy.setFinal(this.isFinal);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
