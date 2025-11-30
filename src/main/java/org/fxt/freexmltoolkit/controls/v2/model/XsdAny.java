package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD any element wildcard (xs:any).
 * Allows elements from specified namespaces to appear in the content model.
 *
 * @since 2.0
 */
public class XsdAny extends XsdNode {

    /**
     * Process contents mode.
     */
    public enum ProcessContents {
        STRICT("strict"),
        LAX("lax"),
        SKIP("skip");

        private final String value;

        ProcessContents(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static ProcessContents fromString(String value) {
            if (value == null) {
                return STRICT; // default
            }
            for (ProcessContents pc : values()) {
                if (pc.value.equalsIgnoreCase(value)) {
                    return pc;
                }
            }
            return STRICT;
        }
    }

    private String namespace = "##any"; // ##any, ##other, ##local, ##targetNamespace, or URI list
    private ProcessContents processContents = ProcessContents.STRICT;

    /**
     * Creates a new XSD any wildcard with default settings.
     */
    public XsdAny() {
        super("any");
    }

    /**
     * Gets the namespace attribute.
     * Values: ##any, ##other, ##local, ##targetNamespace, or space-separated list of URIs.
     *
     * @return the namespace value
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace attribute.
     *
     * @param namespace the namespace value
     */
    public void setNamespace(String namespace) {
        String oldValue = this.namespace;
        this.namespace = namespace;
        pcs.firePropertyChange("namespace", oldValue, namespace);
    }

    /**
     * Gets the processContents attribute.
     *
     * @return the process contents mode
     */
    public ProcessContents getProcessContents() {
        return processContents;
    }

    /**
     * Sets the processContents attribute.
     *
     * @param processContents the process contents mode
     */
    public void setProcessContents(ProcessContents processContents) {
        ProcessContents oldValue = this.processContents;
        this.processContents = processContents;
        pcs.firePropertyChange("processContents", oldValue, processContents);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ANY;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Wildcards don't have meaningful names to suffix
        XsdAny copy = new XsdAny();

        // Copy XsdAny-specific properties
        copy.setNamespace(this.namespace);
        copy.setProcessContents(this.processContents);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
