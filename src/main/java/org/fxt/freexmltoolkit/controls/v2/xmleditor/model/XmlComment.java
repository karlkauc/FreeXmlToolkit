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
        String indentStr = " ".repeat(indent * 2);
        return indentStr + "<!-- " + text + " -->";
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
