package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD group (xs:group).
 * A group defines a named set of element declarations that can be reused.
 *
 * @since 2.0
 */
public class XsdGroup extends XsdNode {

    private String ref; // Reference to a global group

    /**
     * Creates a new XSD group.
     */
    public XsdGroup() {
        super("group");
    }

    /**
     * Creates a new XSD group with a name.
     *
     * @param name the group name
     */
    public XsdGroup(String name) {
        super(name);
    }

    /**
     * Gets the group reference.
     *
     * @return the reference, or null
     */
    public String getRef() {
        return ref;
    }

    /**
     * Sets the group reference.
     *
     * @param ref the reference
     */
    public void setRef(String ref) {
        String oldValue = this.ref;
        this.ref = ref;
        pcs.firePropertyChange("ref", oldValue, ref);
    }

    /**
     * Checks if this is a group reference (not a definition).
     *
     * @return true if this is a reference
     */
    public boolean isReference() {
        return ref != null && !ref.isEmpty();
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.GROUP;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdGroup copy = new XsdGroup(newName);

        // Copy XsdGroup-specific properties
        copy.setRef(this.ref);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
