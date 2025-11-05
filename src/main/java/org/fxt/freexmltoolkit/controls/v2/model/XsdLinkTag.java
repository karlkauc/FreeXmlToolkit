package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Objects;

/**
 * Represents a {@link XPath} tag in XSD documentation.
 * Used within @see or @deprecated annotations to create clickable links to other elements.
 * <p>
 * Example: {@link /FundsXML4/ControlData/RelatedDocumentIDs/RelatedDocumentID}
 *
 * @since 2.0
 */
public class XsdLinkTag {

    private final String xpathExpression;
    private String resolvedTargetId; // Resolved element ID for navigation

    /**
     * Creates a new link tag with the given XPath expression.
     *
     * @param xpathExpression the XPath expression pointing to another element
     */
    public XsdLinkTag(String xpathExpression) {
        this.xpathExpression = Objects.requireNonNull(xpathExpression, "XPath expression cannot be null");
    }

    /**
     * Returns the XPath expression.
     *
     * @return the XPath expression (absolute path from root)
     */
    public String getXpathExpression() {
        return xpathExpression;
    }

    /**
     * Returns the resolved target element ID for navigation.
     * This is set after resolving the XPath to an actual element in the schema.
     *
     * @return the target element ID, or null if not yet resolved
     */
    public String getResolvedTargetId() {
        return resolvedTargetId;
    }

    /**
     * Sets the resolved target element ID.
     *
     * @param targetId the target element ID
     */
    public void setResolvedTargetId(String targetId) {
        this.resolvedTargetId = targetId;
    }

    /**
     * Checks if this link has been resolved to a target element.
     *
     * @return true if resolved, false otherwise
     */
    public boolean isResolved() {
        return resolvedTargetId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdLinkTag that = (XsdLinkTag) o;
        return Objects.equals(xpathExpression, that.xpathExpression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(xpathExpression);
    }

    @Override
    public String toString() {
        return "{@link " + xpathExpression + "}";
    }
}
