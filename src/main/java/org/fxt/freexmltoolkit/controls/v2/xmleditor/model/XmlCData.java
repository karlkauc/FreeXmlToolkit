package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Represents an XML CDATA section node.
 *
 * <p>CDATA sections contain character data that should not be parsed.
 * Useful for embedding code, scripts, or other content with XML special characters.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <script>
 *   <![CDATA[
 *     if (x < 10) {
 *       alert("Less than 10");
 *     }
 *   ]]>
 * </script>
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>text - CDATA text content</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCData extends XmlNode {

    /**
     * The CDATA text content.
     */
    private String text;

    /**
     * Constructs a new XmlCData section.
     *
     * @param text the CDATA content
     */
    public XmlCData(String text) {
        super();
        this.text = text != null ? text : "";
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original CDATA section to copy from
     */
    private XmlCData(XmlCData original) {
        super(original);
        this.text = original.text;
    }

    /**
     * Returns the CDATA text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the CDATA text.
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
        return XmlNodeType.CDATA;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        return new XmlCData(this);
    }

    @Override
    public String serialize(int indent) {
        return "<![CDATA[" + text + "]]>";
    }

    @Override
    public void accept(XmlNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        String preview = text.length() > 30 ? text.substring(0, 30) + "..." : text;
        return "XmlCData[\"" + preview + "\"]";
    }
}
