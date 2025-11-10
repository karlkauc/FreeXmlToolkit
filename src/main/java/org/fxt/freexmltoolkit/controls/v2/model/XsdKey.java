package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD key constraint (xs:key).
 * A key specifies that an attribute or element value must be unique within a scope.
 *
 * @since 2.0
 */
public class XsdKey extends XsdIdentityConstraint {

    /**
     * Creates a new XSD key constraint.
     */
    public XsdKey() {
        super("key");
    }

    /**
     * Creates a new XSD key constraint with a name.
     *
     * @param name the key name
     */
    public XsdKey(String name) {
        super(name);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.KEY;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdKey copy = new XsdKey(newName);

        // No XsdKey-specific properties to copy

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
