package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * A schema-level {@code xs:annotation}: a top-level child of {@code xs:schema} that keeps
 * its position among the components. Its documentation and appinfo live in the inherited
 * {@link XsdNode} documentation fields.
 * <p>
 * Annotations of components (elements, types, …) are not nodes of their own; they stay
 * in the documentation fields of the annotated node.
 *
 * @since 2.6
 */
public class XsdAnnotation extends XsdNode {

    /** Creates an empty schema-level annotation. */
    public XsdAnnotation() {
        super("annotation");
    }

    /** @return true when the annotation holds neither documentation nor appinfo */
    public boolean isEmpty() {
        return getDocumentations().isEmpty()
                && (getDocumentation() == null || getDocumentation().isBlank())
                && (getAppinfo() == null || !getAppinfo().hasEntries());
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ANNOTATION;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdAnnotation copy = new XsdAnnotation();
        copyBasicPropertiesTo(copy, suffix);
        return copy;
    }
}
