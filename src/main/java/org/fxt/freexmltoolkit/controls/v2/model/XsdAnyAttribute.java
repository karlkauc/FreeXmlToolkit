package org.fxt.freexmltoolkit.controls.v2.model;

/**
 * Represents an XSD any attribute wildcard (xs:anyAttribute).
 * Allows attributes from specified namespaces to appear.
 *
 * @since 2.0
 */
public class XsdAnyAttribute extends XsdNode {

    private String namespace = "##any"; // ##any, ##other, ##local, ##targetNamespace, or URI list
    private XsdAny.ProcessContents processContents = XsdAny.ProcessContents.STRICT;

    /**
     * Creates a new XSD anyAttribute wildcard with default settings.
     */
    public XsdAnyAttribute() {
        super("anyAttribute");
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
    public XsdAny.ProcessContents getProcessContents() {
        return processContents;
    }

    /**
     * Sets the processContents attribute.
     *
     * @param processContents the process contents mode
     */
    public void setProcessContents(XsdAny.ProcessContents processContents) {
        XsdAny.ProcessContents oldValue = this.processContents;
        this.processContents = processContents;
        pcs.firePropertyChange("processContents", oldValue, processContents);
    }

    @Override
    public XsdNodeType getNodeType() {
        return XsdNodeType.ANY_ATTRIBUTE;
    }

    @Override
    public XsdNode deepCopy(String suffix) {
        // Wildcards don't have meaningful names to suffix
        XsdAnyAttribute copy = new XsdAnyAttribute();

        // Copy XsdAnyAttribute-specific properties
        copy.setNamespace(this.namespace);
        copy.setProcessContents(this.processContents);

        // Copy base properties and children (propagate suffix to children)
        copyBasicPropertiesTo(copy, suffix);

        return copy;
    }
}
