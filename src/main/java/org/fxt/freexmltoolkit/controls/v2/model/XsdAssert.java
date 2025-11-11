package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD 1.1 assert constraint (xs:assert).
 * Assertions allow XPath 2.0 expressions to validate complex constraints
 * that cannot be expressed with traditional XSD 1.0 features.
 *
 * @since 2.0
 */
public class XsdAssert extends XsdNode {

    private String test; // XPath 2.0 expression
    private String xpathDefaultNamespace; // Default namespace for XPath expressions

    /**
     * Creates a new XSD assert constraint with default name.
     */
    public XsdAssert() {
        super("assert");
    }

    /**
     * Creates a new XSD assert constraint with XPath test expression.
     *
     * @param test the XPath 2.0 test expression
     */
    public XsdAssert(String test) {
        super("assert");
        this.test = test;
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
     * Gets the xpathDefaultNamespace attribute.
     *
     * @return the default namespace for XPath expressions, or null
     */
    public String getXpathDefaultNamespace() {
        return xpathDefaultNamespace;
    }

    /**
     * Sets the xpathDefaultNamespace attribute.
     *
     * @param xpathDefaultNamespace the default namespace for XPath expressions
     */
    public void setXpathDefaultNamespace(String xpathDefaultNamespace) {
        String oldValue = this.xpathDefaultNamespace;
        this.xpathDefaultNamespace = xpathDefaultNamespace;
        pcs.firePropertyChange("xpathDefaultNamespace", oldValue, xpathDefaultNamespace);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ASSERT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdAssert copy = new XsdAssert(this.test);

        // Apply name suffix
        if (suffix != null && !suffix.isEmpty()) {
            copy.setName(getName() + suffix);
        }

        // Copy XsdAssert-specific properties
        copy.setXpathDefaultNamespace(this.xpathDefaultNamespace);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
