package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD 1.1 alternative element (xs:alternative).
 * Alternatives allow conditional type assignment based on XPath 2.0 expressions.
 * This enables different types to be applied to an element depending on context.
 *
 * @since 2.0
 */
public class XsdAlternative extends XsdNode {

    private String test; // XPath 2.0 expression for conditional test
    private String type; // Reference to a type name

    /**
     * Creates a new XSD alternative with default name.
     */
    public XsdAlternative() {
        super("alternative");
    }

    /**
     * Creates a new XSD alternative with XPath test expression.
     *
     * @param test the XPath 2.0 test expression
     */
    public XsdAlternative(String test) {
        super("alternative");
        this.test = test;
    }

    /**
     * Creates a new XSD alternative with test and type.
     *
     * @param test the XPath 2.0 test expression
     * @param type the type reference
     */
    public XsdAlternative(String test, String type) {
        super("alternative");
        this.test = test;
        this.type = type;
    }

    /**
     * Gets the test attribute (XPath 2.0 expression).
     *
     * @return the test expression, or null
     */
    public String getTest() {
        return test;
    }

    /**
     * Sets the test attribute (XPath 2.0 expression).
     *
     * @param test the XPath 2.0 test expression
     */
    public void setTest(String test) {
        String oldValue = this.test;
        this.test = test;
        pcs.firePropertyChange("test", oldValue, test);
    }

    /**
     * Gets the type attribute (type reference).
     *
     * @return the type reference, or null
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type attribute (type reference).
     *
     * @param type the type reference
     */
    public void setType(String type) {
        String oldValue = this.type;
        this.type = type;
        pcs.firePropertyChange("type", oldValue, type);
    }

    /**
     * Gets the inline simpleType child if present.
     *
     * @return the XsdSimpleType child, or null
     */
    public XsdSimpleType getSimpleType() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdSimpleType)
                .map(child -> (XsdSimpleType) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the inline complexType child if present.
     *
     * @return the XsdComplexType child, or null
     */
    public XsdComplexType getComplexType() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdComplexType)
                .map(child -> (XsdComplexType) child)
                .findFirst()
                .orElse(null);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ALTERNATIVE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdAlternative copy = new XsdAlternative(this.test, this.type);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
