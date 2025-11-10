package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD sequence compositor (xs:sequence).
 *
 * @since 2.0
 */
public class XsdSequence extends XsdNode {

    /**
     * Creates a new XSD sequence.
     */
    public XsdSequence() {
        super("sequence");
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.SEQUENCE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Sequence name is always "sequence", suffix is not applied
        XsdSequence copy = new XsdSequence();

        // No XsdSequence-specific properties to copy

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
