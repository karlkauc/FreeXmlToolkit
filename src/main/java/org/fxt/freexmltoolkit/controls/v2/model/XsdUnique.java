package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD unique constraint (xs:unique).
 * A unique constraint specifies that a value must be unique within a scope,
 * but unlike key, null values are allowed.
 *
 * @since 2.0
 */
public class XsdUnique extends XsdIdentityConstraint {

    /**
     * Creates a new XSD unique constraint.
     */
    public XsdUnique() {
        super("unique");
    }

    /**
     * Creates a new XSD unique constraint with a name.
     *
     * @param name the unique constraint name
     */
    public XsdUnique(String name) {
        super(name);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.UNIQUE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdUnique copy = new XsdUnique(newName);

        // No XsdUnique-specific properties to copy

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
