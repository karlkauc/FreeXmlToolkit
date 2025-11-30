package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XML comment in an XSD schema.
 * <p>
 * Comments are preserved during parsing and serialization to maintain
 * the original structure of the XSD file.
 *
 * @since 2.0
 */
public class XsdComment extends XsdNode {

    private String content;

    /**
     * Creates a new XSD comment.
     *
     * @param content the comment text (without &lt;!-- and --&gt;)
     */
    public XsdComment(String content) {
        super("comment");
        this.content = content;
    }

    /**
     * Gets the comment content.
     *
     * @return the comment text
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the comment content.
     *
     * @param content the comment text
     */
    public void setContent(String content) {
        String oldValue = this.content;
        this.content = content;
        pcs.firePropertyChange("content", oldValue, content);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.COMMENT;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        XsdComment copy = new XsdComment(this.content);
        copyBasicPropertiesTo(copy, suffix);
        return copy;
    }

    @Override
    public String toString() {
        return "XsdComment{" + (content != null ? content.substring(0, Math.min(30, content.length())) : "null") + "}";
    }
}
