package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Represents an XML comment node.
 *
 * <p>Comments are human-readable annotations ignored by parsers.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <!-- This is a comment -->
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>text - Comment text</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlComment extends XmlNode {

    /**
     * The comment text.
     */
    private String text;

    /**
     * Whether the serialized comment puts one space after {@code <!--} and before {@code -->}
     * (the usual style, and that of comments added in the editor). A parsed comment without
     * those spaces keeps its form: writing {@code <!--x-->} back as {@code <!-- x -->}, and a
     * parsed {@code " x "} as {@code <!--  x  -->}, changed untouched comments on every edit.
     */
    private boolean padded = true;

    /**
     * A comment as read from a document: one space on both sides of the raw text is the padding
     * (not part of {@link #getText()}), any other form is kept exactly.
     *
     * @param raw the characters between {@code <!--} and {@code -->}
     * @return the comment
     */
    public static XmlComment fromSource(String raw) {
        String source = raw != null ? raw : "";
        if (source.length() >= 2 && source.charAt(0) == ' ' && source.charAt(source.length() - 1) == ' ') {
            return new XmlComment(source.substring(1, source.length() - 1));
        }
        XmlComment comment = new XmlComment(source);
        comment.padded = false;
        return comment;
    }

    /**
     * Constructs a new XmlComment.
     *
     * @param text the comment text
     */
    public XmlComment(String text) {
        super();
        this.text = text != null ? text : "";
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original comment to copy from
     */
    private XmlComment(XmlComment original) {
        super(original);
        this.text = original.text;
        this.padded = original.padded;
    }

    /**
     * Returns the comment text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the comment text.
     * Fires a "text" property change event.
     *
     * @param text the new text
     */
    public void setText(String text) {
        String oldText = this.text;
        this.text = text != null ? text : "";
        firePropertyChange("text", oldText, this.text);
    }

    // ==================== XmlNode Implementation ====================

    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.COMMENT;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        return new XmlComment(this);
    }

    @Override
    public String serialize(int indent) {
        return serialize(indent, 2);
    }

    @Override
    public String serialize(int indent, int indentSize) {
        String indentStr = " ".repeat(indent * indentSize);
        return padded ? indentStr + "<!-- " + text + " -->" : indentStr + "<!--" + text + "-->";
    }

    @Override
    public void accept(XmlNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        String preview = text.length() > 30 ? text.substring(0, 30) + "..." : text;
        return "XmlComment[\"" + preview + "\"]";
    }
}
