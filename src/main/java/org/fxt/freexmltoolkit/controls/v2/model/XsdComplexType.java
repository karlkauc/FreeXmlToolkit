package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD complex type (xs:complexType).
 *
 * @since 2.0
 */
public class XsdComplexType extends XsdNode {

    private boolean mixed;
    private boolean abstractType;

    /**
     * Creates a new XSD complex type.
     *
     * @param name the type name
     */
    public XsdComplexType(String name) {
        super(name);
    }

    /**
     * Checks if this is a mixed content type.
     *
     * @return true if mixed
     */
    public boolean isMixed() {
        return mixed;
    }

    /**
     * Sets the mixed content flag.
     *
     * @param mixed true if mixed
     */
    public void setMixed(boolean mixed) {
        boolean oldValue = this.mixed;
        this.mixed = mixed;
        pcs.firePropertyChange("mixed", oldValue, mixed);
    }

    /**
     * Checks if this type is abstract.
     *
     * @return true if abstract
     */
    public boolean isAbstract() {
        return abstractType;
    }

    /**
     * Sets the abstract flag.
     *
     * @param abstractType true if abstract
     */
    public void setAbstract(boolean abstractType) {
        boolean oldValue = this.abstractType;
        this.abstractType = abstractType;
        pcs.firePropertyChange("abstract", oldValue, abstractType);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.COMPLEX_TYPE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdComplexType copy = new XsdComplexType(newName);

        // Copy XsdComplexType-specific properties
        copy.setMixed(this.mixed);
        copy.setAbstract(this.abstractType);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
