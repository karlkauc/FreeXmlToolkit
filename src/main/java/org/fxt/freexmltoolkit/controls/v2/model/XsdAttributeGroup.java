package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD attribute group (xs:attributeGroup).
 * An attribute group defines a named set of attribute declarations that can be reused.
 *
 * @since 2.0
 */
public class XsdAttributeGroup extends XsdNode {

    private String ref; // Reference to a global attribute group

    /**
     * Creates a new XSD attribute group.
     */
    public XsdAttributeGroup() {
        super("attributeGroup");
    }

    /**
     * Creates a new XSD attribute group with a name.
     *
     * @param name the attribute group name
     */
    public XsdAttributeGroup(String name) {
        super(name);
    }

    /**
     * Gets the attribute group reference.
     *
     * @return the reference, or null
     */
    public String getRef() {
        return ref;
    }

    /**
     * Sets the attribute group reference.
     *
     * @param ref the reference
     */
    public void setRef(String ref) {
        String oldValue = this.ref;
        this.ref = ref;
        pcs.firePropertyChange("ref", oldValue, ref);
    }

    /**
     * Checks if this is an attribute group reference (not a definition).
     *
     * @return true if this is a reference
     */
    public boolean isReference() {
        return ref != null && !ref.isEmpty();
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ATTRIBUTE_GROUP;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdAttributeGroup copy = new XsdAttributeGroup(newName);

        // Copy XsdAttributeGroup-specific properties
        copy.setRef(this.ref);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
