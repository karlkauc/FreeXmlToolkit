package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD choice compositor (xs:choice).
 *
 * @since 2.0
 */
public class XsdChoice extends XsdNode {

    /**
     * Creates a new XSD choice.
     */
    public XsdChoice() {
        super("choice");
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.CHOICE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Choice name is always "choice", suffix is not applied
        XsdChoice copy = new XsdChoice();

        // No XsdChoice-specific properties to copy

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
