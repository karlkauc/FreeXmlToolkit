package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD extension (xs:extension).
 * Used within simpleContent or complexContent to extend a base type.
 *
 * @since 2.0
 */
public class XsdExtension extends XsdNode {

    private String base; // Base type being extended

    /**
     * Creates a new XSD extension.
     */
    public XsdExtension() {
        super("extension");
    }

    /**
     * Creates a new XSD extension with a base type.
     *
     * @param base the base type
     */
    public XsdExtension(String base) {
        super("extension");
        this.base = base;
    }

    /**
     * Gets the base type.
     *
     * @return the base type, or null
     */
    public String getBase() {
        return base;
    }

    /**
     * Sets the base type.
     *
     * @param base the base type
     */
    public void setBase(String base) {
        String oldValue = this.base;
        this.base = base;
        pcs.firePropertyChange("base", oldValue, base);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.EXTENSION;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Extension name is always "extension", suffix is not applied
        XsdExtension copy = new XsdExtension();

        // Copy XsdExtension-specific properties
        copy.setBase(this.base);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
