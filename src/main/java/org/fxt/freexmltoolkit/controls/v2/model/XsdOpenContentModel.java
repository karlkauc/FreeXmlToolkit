package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Objects;

/**
 * Model representing an XSD 1.1 openContent element.
 * OpenContent allows additional elements (wildcards) to appear in a complex type.
 *
 * <p>Example with interleave mode:</p>
 * <pre>{@code
 * <xs:complexType name="PersonType">
 *   <xs:openContent mode="interleave">
 *     <xs:any namespace="##any" processContents="lax"/>
 *   </xs:openContent>
 *   <xs:sequence>
 *     <xs:element name="name" type="xs:string"/>
 *     <xs:element name="age" type="xs:integer"/>
 *   </xs:sequence>
 * </xs:complexType>
 * }</pre>
 *
 * <p>Example with suffix mode:</p>
 * <pre>{@code
 * <xs:complexType name="PersonType">
 *   <xs:openContent mode="suffix">
 *     <xs:any namespace="##any"/>
 *   </xs:openContent>
 *   <xs:sequence>
 *     <xs:element name="name" type="xs:string"/>
 *   </xs:sequence>
 * </xs:complexType>
 * }</pre>
 *
 * @since 2.0
 */
public class XsdOpenContentModel {

    /**
     * Mode for open content placement.
     */
    public enum Mode {
        INTERLEAVE,  // Additional elements can appear anywhere (default)
        SUFFIX       // Additional elements can only appear after declared elements
    }

    /**
     * Process contents mode for wildcard validation.
     */
    public enum ProcessContents {
        STRICT,  // Must have schema declaration (default)
        LAX,     // Validate if schema declaration exists
        SKIP     // No validation
    }

    private final String id;
    private Mode mode = Mode.INTERLEAVE;
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Wildcard properties (from xs:any child element)
    private String wildcardNamespace;  // ##any, ##other, ##targetNamespace, ##local, or space-separated list
    private ProcessContents processContents = ProcessContents.STRICT;
    private String notNamespace;  // XSD 1.1: negated namespace
    private String notQName;      // XSD 1.1: negated QName

    /**
     * Creates a new open content model.
     *
     * @param id unique identifier
     */
    public XsdOpenContentModel(String id) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
    }

    /**
     * Returns the unique identifier.
     *
     * @return the ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the mode.
     *
     * @return the mode
     */
    public Mode getMode() {
        return mode;
    }

    /**
     * Sets the mode.
     *
     * @param mode the mode
     */
    public void setMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.INTERLEAVE;
    }

    /**
     * Returns the documentation.
     *
     * @return the documentation
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation.
     *
     * @param documentation the documentation
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Returns the documentation info.
     *
     * @return the doc info
     */
    public XsdDocInfo getDocInfo() {
        return docInfo;
    }

    /**
     * Sets the documentation info.
     *
     * @param docInfo the doc info
     */
    public void setDocInfo(XsdDocInfo docInfo) {
        this.docInfo = docInfo != null ? docInfo : new XsdDocInfo();
    }

    /**
     * Returns the wildcard namespace.
     *
     * @return the namespace specification
     */
    public String getWildcardNamespace() {
        return wildcardNamespace;
    }

    /**
     * Sets the wildcard namespace.
     *
     * @param wildcardNamespace the namespace specification
     */
    public void setWildcardNamespace(String wildcardNamespace) {
        this.wildcardNamespace = wildcardNamespace;
    }

    /**
     * Returns the process contents mode.
     *
     * @return the process contents mode
     */
    public ProcessContents getProcessContents() {
        return processContents;
    }

    /**
     * Sets the process contents mode.
     *
     * @param processContents the process contents mode
     */
    public void setProcessContents(ProcessContents processContents) {
        this.processContents = processContents != null ? processContents : ProcessContents.STRICT;
    }

    /**
     * Returns the notNamespace attribute (XSD 1.1).
     *
     * @return the not namespace specification
     */
    public String getNotNamespace() {
        return notNamespace;
    }

    /**
     * Sets the notNamespace attribute (XSD 1.1).
     *
     * @param notNamespace the not namespace specification
     */
    public void setNotNamespace(String notNamespace) {
        this.notNamespace = notNamespace;
    }

    /**
     * Returns the notQName attribute (XSD 1.1).
     *
     * @return the not QName specification
     */
    public String getNotQName() {
        return notQName;
    }

    /**
     * Sets the notQName attribute (XSD 1.1).
     *
     * @param notQName the not QName specification
     */
    public void setNotQName(String notQName) {
        this.notQName = notQName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("XsdOpenContentModel{");
        sb.append("mode=").append(mode);
        if (wildcardNamespace != null) {
            sb.append(", namespace='").append(wildcardNamespace).append("'");
        }
        sb.append(", processContents=").append(processContents);
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdOpenContentModel that = (XsdOpenContentModel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
