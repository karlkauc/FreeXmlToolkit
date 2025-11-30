package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents XSD complex content (xs:complexContent).
 * Used to extend or restrict a complex type that has element content.
 *
 * @since 2.0
 */
public class XsdComplexContent extends XsdNode {

    private boolean mixed; // If true, allows mixed content (elements and text)

    /**
     * Creates a new XSD complex content.
     */
    public XsdComplexContent() {
        super("complexContent");
    }

    /**
     * Checks if this complex content allows mixed content.
     *
     * @return true if mixed
     */
    public boolean isMixed() {
        return mixed;
    }

    /**
     * Sets the mixed flag.
     *
     * @param mixed true if mixed content is allowed
     */
    public void setMixed(boolean mixed) {
        boolean oldValue = this.mixed;
        this.mixed = mixed;
        pcs.firePropertyChange("mixed", oldValue, mixed);
    }

    /**
     * Gets the extension child, if present.
     *
     * @return the extension, or null
     */
    public XsdExtension getExtension() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdExtension)
                .map(child -> (XsdExtension) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the restriction child, if present.
     *
     * @return the restriction, or null
     */
    public XsdRestriction getRestriction() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdRestriction)
                .map(child -> (XsdRestriction) child)
                .findFirst()
                .orElse(null);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.COMPLEX_CONTENT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // ComplexContent name is always "complexContent", suffix is not applied
        XsdComplexContent copy = new XsdComplexContent();

        // Copy XsdComplexContent-specific properties
        copy.setMixed(this.mixed);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
