package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents XSD simple content (xs:simpleContent).
 * Used to extend or restrict a complex type that has text content only.
 *
 * @since 2.0
 */
public class XsdSimpleContent extends XsdNode {

    /**
     * Creates a new XSD simple content.
     */
    public XsdSimpleContent() {
        super("simpleContent");
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
        return XsdNodeType.SIMPLE_CONTENT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // SimpleContent name is always "simpleContent", suffix is not applied
        XsdSimpleContent copy = new XsdSimpleContent();

        // No XsdSimpleContent-specific properties to copy

        // Copy base properties and children
        copyBasicPropertiesTo(copy);

        return copy;
    }
}
