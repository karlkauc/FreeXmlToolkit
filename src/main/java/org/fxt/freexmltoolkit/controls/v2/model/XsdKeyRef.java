package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD keyref constraint (xs:keyref).
 * A keyref constraint enforces referential integrity by requiring values
 * to match a key or unique constraint.
 *
 * @since 2.0
 */
public class XsdKeyRef extends XsdIdentityConstraint {

    private String refer; // Reference to a key or unique constraint

    /**
     * Creates a new XSD keyref constraint.
     */
    public XsdKeyRef() {
        super("keyref");
    }

    /**
     * Creates a new XSD keyref constraint with a name.
     *
     * @param name the keyref name
     */
    public XsdKeyRef(String name) {
        super(name);
    }

    /**
     * Gets the refer attribute.
     *
     * @return the refer attribute, or null
     */
    public String getRefer() {
        return refer;
    }

    /**
     * Sets the refer attribute.
     *
     * @param refer the refer attribute (key or unique name)
     */
    public void setRefer(String refer) {
        String oldValue = this.refer;
        this.refer = refer;
        pcs.firePropertyChange("refer", oldValue, refer);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.KEYREF;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdKeyRef copy = new XsdKeyRef(newName);

        // Copy XsdKeyRef-specific properties
        copy.setRefer(this.refer);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
