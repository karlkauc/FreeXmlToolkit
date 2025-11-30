package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD complex type (xs:complexType).
 *
 * @since 2.0
 */
public class XsdComplexType extends XsdNode {

    private boolean mixed;
    private boolean abstractType;
    private String block; // extension, restriction, #all
    private String finalValue; // extension, restriction, #all (cannot use 'final' as field name)

    /**
     * Creates a new XSD complex type.
     *
     * @param name the type name
     */
    public XsdComplexType(String name) {
        super(name);
    }

    /**
     * Checks if this is a mixed content type.
     *
     * @return true if mixed
     */
    public boolean isMixed() {
        return mixed;
    }

    /**
     * Sets the mixed content flag.
     *
     * @param mixed true if mixed
     */
    public void setMixed(boolean mixed) {
        boolean oldValue = this.mixed;
        this.mixed = mixed;
        pcs.firePropertyChange("mixed", oldValue, mixed);
    }

    /**
     * Checks if this type is abstract.
     *
     * @return true if abstract
     */
    public boolean isAbstract() {
        return abstractType;
    }

    /**
     * Sets the abstract flag.
     *
     * @param abstractType true if abstract
     */
    public void setAbstract(boolean abstractType) {
        boolean oldValue = this.abstractType;
        this.abstractType = abstractType;
        pcs.firePropertyChange("abstract", oldValue, abstractType);
    }

    /**
     * Gets the block attribute.
     * Controls derivation blocking.
     *
     * @return the block value (extension, restriction, #all), or null
     */
    public String getBlock() {
        return block;
    }

    /**
     * Sets the block attribute.
     *
     * @param block the block value (extension, restriction, #all)
     */
    public void setBlock(String block) {
        String oldValue = this.block;
        this.block = block;
        pcs.firePropertyChange("block", oldValue, block);
    }

    /**
     * Gets the final attribute.
     * Controls whether type can be further derived.
     *
     * @return the final value (extension, restriction, #all), or null
     */
    public String getFinal() {
        return finalValue;
    }

    /**
     * Sets the final attribute.
     *
     * @param finalValue the final value (extension, restriction, #all)
     */
    public void setFinal(String finalValue) {
        String oldValue = this.finalValue;
        this.finalValue = finalValue;
        pcs.firePropertyChange("final", oldValue, finalValue);
    }

    /**
     * Gets the complexContent child, if present.
     * ComplexContent is used for extending/restricting complex types.
     *
     * @return the complexContent, or null
     */
    public XsdComplexContent getComplexContent() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdComplexContent)
                .map(child -> (XsdComplexContent) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the simpleContent child, if present.
     * SimpleContent is used for extending/restricting types with text content only.
     *
     * @return the simpleContent, or null
     */
    public XsdSimpleContent getSimpleContent() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdSimpleContent)
                .map(child -> (XsdSimpleContent) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the sequence child, if present.
     * Sequence is used for direct child elements in order.
     * This is backwards-compatible with pre-ComplexContent structure.
     *
     * @return the sequence, or null
     */
    public XsdSequence getSequence() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdSequence)
                .map(child -> (XsdSequence) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the choice child, if present.
     * Choice is used for alternative child elements.
     * This is backwards-compatible with pre-ComplexContent structure.
     *
     * @return the choice, or null
     */
    public XsdChoice getChoice() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdChoice)
                .map(child -> (XsdChoice) child)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets the all child, if present.
     * All is used for unordered child elements.
     * This is backwards-compatible with pre-ComplexContent structure.
     *
     * @return the all, or null
     */
    public XsdAll getAll() {
        return getChildren().stream()
                .filter(child -> child instanceof XsdAll)
                .map(child -> (XsdAll) child)
                .findFirst()
                .orElse(null);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.COMPLEX_TYPE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        String newName = suffix != null ? getName() + suffix : getName();
        XsdComplexType copy = new XsdComplexType(newName);

        // Copy XsdComplexType-specific properties
        copy.setMixed(this.mixed);
        copy.setAbstract(this.abstractType);
        copy.setBlock(this.block);
        copy.setFinal(this.finalValue);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
