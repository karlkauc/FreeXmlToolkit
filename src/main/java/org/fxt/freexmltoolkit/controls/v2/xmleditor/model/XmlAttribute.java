package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Represents an XML attribute.
 *
 * <p>Attributes are name-value pairs attached to elements.
 * Note: In the tree structure, attributes are stored in the
 * element's attribute map, not as separate child nodes.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * <book id="123" lang="en">
 *       ^^^^^^^^  ^^^^^^^^^
 *       Attributes
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>name - Attribute name</li>
 *   <li>value - Attribute value</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlAttribute extends XmlNode {

    /**
     * Attribute name.
     */
    private String name;

    /**
     * Attribute value.
     */
    private String value;

    /**
     * Constructs a new XmlAttribute.
     *
     * @param name  the attribute name
     * @param value the attribute value
     */
    public XmlAttribute(String name, String value) {
        super();
        this.name = name;
        this.value = value != null ? value : "";
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original attribute to copy from
     */
    private XmlAttribute(XmlAttribute original) {
        super(original);
        this.name = original.name;
        this.value = original.value;
    }

    /**
     * Returns the attribute name.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the attribute name.
     * Fires a "name" property change event.
     *
     * @param name the new name
     */
    public void setName(String name) {
        String oldName = this.name;
        this.name = name;
        firePropertyChange("name", oldName, name);
    }

    /**
     * Returns the attribute value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the attribute value.
     * Fires a "value" property change event.
     *
     * @param value the new value
     */
    public void setValue(String value) {
        String oldValue = this.value;
        this.value = value != null ? value : "";
        firePropertyChange("value", oldValue, this.value);
    }

    // ==================== XmlNode Implementation ====================

    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.ATTRIBUTE;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        return new XmlAttribute(this);
    }

    @Override
    public String serialize(int indent) {
        return name + "=\"" + escapeXml(value) + "\"";
    }

    @Override
    public void accept(XmlNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "XmlAttribute[" + name + "=\"" + value + "\"]";
    }

    // ==================== Utility ====================

    /**
     * Escapes XML special characters in attribute values.
     *
     * @param text the text to escape
     * @return the escaped text
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
