package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Represents an XML text node.
 *
 * <p>Text nodes contain character data within an element.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <title>XML Guide</title>
 *        ^^^^^^^^^^
 *        Text node
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>text - The text content</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlText extends XmlNode {

    /**
     * The text content.
     */
    private String text;

    /**
     * Constructs a new XmlText with the given content.
     *
     * @param text the text content
     */
    public XmlText(String text) {
        super();
        this.text = text != null ? text : "";
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original text node to copy from
     */
    private XmlText(XmlText original) {
        super(original);
        this.text = original.text;
    }

    /**
     * Returns the text content.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the text content.
     * Fires a "text" property change event.
     *
     * @param text the new text content
     */
    public void setText(String text) {
        String oldText = this.text;
        this.text = text != null ? text : "";
        firePropertyChange("text", oldText, this.text);
    }

    /**
     * Checks if this text node is whitespace-only.
     *
     * @return true if the text contains only whitespace
     */
    public boolean isWhitespace() {
        return text.trim().isEmpty();
    }

    // ==================== XmlNode Implementation ====================

    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.TEXT;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        return new XmlText(this);
    }

    @Override
    public String serialize(int indent) {
        return escapeXml(text);
    }

    @Override
    public void accept(XmlNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        String preview = text.length() > 30 ? text.substring(0, 30) + "..." : text;
        return "XmlText[\"" + preview + "\"]";
    }

    // ==================== Utility ====================

    /**
     * Escapes XML special characters in text.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
