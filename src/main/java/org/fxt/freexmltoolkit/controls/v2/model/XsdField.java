package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD field (xs:field) used in identity constraints.
 * The field specifies an XPath expression that selects an attribute or element value.
 *
 * @since 2.0
 */
public class XsdField extends XsdNode {

    private String xpath; // XPath expression

    /**
     * Creates a new XSD field.
     */
    public XsdField() {
        super("field");
    }

    /**
     * Creates a new XSD field with an XPath expression.
     *
     * @param xpath the XPath expression
     */
    public XsdField(String xpath) {
        super("field");
        this.xpath = xpath;
    }

    /**
     * Gets the XPath expression.
     *
     * @return the XPath expression, or null
     */
    public String getXpath() {
        return xpath;
    }

    /**
     * Sets the XPath expression.
     *
     * @param xpath the XPath expression
     */
    public void setXpath(String xpath) {
        String oldValue = this.xpath;
        this.xpath = xpath;
        pcs.firePropertyChange("xpath", oldValue, xpath);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.FIELD;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Field name is always "field", suffix is not applied
        XsdField copy = new XsdField();

        // Copy XsdField-specific properties
        copy.setXpath(this.xpath);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
