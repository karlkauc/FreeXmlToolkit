package org.fxt.freexmltoolkit.controls.v2.xmleditor.model;

/**
 * Represents an XML processing instruction node.
 *
 * <p>Processing instructions provide application-specific directives.</p>
 *
 * <p>Examples:</p>
 * <pre>{@code
 * <?xml-stylesheet type="text/xsl" href="style.xsl"?>
 * <?php echo "Hello"; ?>
 * }</pre>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>target - PI target (e.g., "xml-stylesheet")</li>
 *   <li>data - PI data (e.g., "type='text/xsl' href='style.xsl'")</li>
 * </ul>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlProcessingInstruction extends XmlNode {

    /**
     * Processing instruction target.
     */
    private String target;

    /**
     * Processing instruction data.
     */
    private String data;

    /**
     * Constructs a new XmlProcessingInstruction.
     *
     * @param target the PI target
     * @param data   the PI data
     */
    public XmlProcessingInstruction(String target, String data) {
        super();
        this.target = target;
        this.data = data != null ? data : "";
    }

    /**
     * Copy constructor for deep copy operations.
     *
     * @param original the original PI to copy from
     */
    private XmlProcessingInstruction(XmlProcessingInstruction original) {
        super(original);
        this.target = original.target;
        this.data = original.data;
    }

    /**
     * Returns the PI target.
     *
     * @return the target
     */
    public String getTarget() {
        return target;
    }

    /**
     * Sets the PI target.
     * Fires a "target" property change event.
     *
     * @param target the new target
     */
    public void setTarget(String target) {
        String oldTarget = this.target;
        this.target = target;
        firePropertyChange("target", oldTarget, target);
    }

    /**
     * Returns the PI data.
     *
     * @return the data
     */
    public String getData() {
        return data;
    }

    /**
     * Sets the PI data.
     * Fires a "data" property change event.
     *
     * @param data the new data
     */
    public void setData(String data) {
        String oldData = this.data;
        this.data = data != null ? data : "";
        firePropertyChange("data", oldData, this.data);
    }

    // ==================== XmlNode Implementation ====================

    @Override
    public XmlNodeType getNodeType() {
        return XmlNodeType.PROCESSING_INSTRUCTION;
    }

    @Override
    public XmlNode deepCopy(String suffix) {
        return new XmlProcessingInstruction(this);
    }

    @Override
    public String serialize(int indent) {
        String indentStr = " ".repeat(indent * 2);
        StringBuilder sb = new StringBuilder();
        sb.append(indentStr).append("<?").append(target);
        if (data != null && !data.isEmpty()) {
            sb.append(" ").append(data);
        }
        sb.append("?>");
        return sb.toString();
    }

    @Override
    public void accept(XmlNodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "XmlProcessingInstruction[target=" + target + ", data=" + data + "]";
    }
}
