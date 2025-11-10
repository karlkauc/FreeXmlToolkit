package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD selector (xs:selector) used in identity constraints.
 * The selector specifies an XPath expression that selects a set of elements.
 *
 * @since 2.0
 */
public class XsdSelector extends XsdNode {

    private String xpath; // XPath expression

    /**
     * Creates a new XSD selector.
     */
    public XsdSelector() {
        super("selector");
    }

    /**
     * Creates a new XSD selector with an XPath expression.
     *
     * @param xpath the XPath expression
     */
    public XsdSelector(String xpath) {
        super("selector");
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
        return XsdNodeType.SELECTOR;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Selector name is always "selector", suffix is not applied
        XsdSelector copy = new XsdSelector();

        // Copy XsdSelector-specific properties
        copy.setXpath(this.xpath);

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
