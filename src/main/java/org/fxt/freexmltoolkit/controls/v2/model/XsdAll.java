package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD all compositor (xs:all).
 * The all compositor allows child elements to appear in any order, each at most once.
 *
 * @since 2.0
 */
public class XsdAll extends XsdNode {

    /**
     * Creates a new XSD all compositor.
     */
    public XsdAll() {
        super("all");
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ALL;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // All name is always "all", suffix is not applied
        XsdAll copy = new XsdAll();

        // No XsdAll-specific properties to copy

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
