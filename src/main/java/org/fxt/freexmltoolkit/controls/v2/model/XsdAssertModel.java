package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Objects;

/**
 * Model representing an XSD 1.1 assertion.
 * Can represent both xs:assert (used in complex types) and xs:assertion (used in simple types).
 *
 * <p>Example xs:assert:</p>
 * <pre>{@code
 * <xs:complexType name="ProductType">
 *   <xs:sequence>
 *     <xs:element name="price" type="xs:decimal"/>
 *     <xs:element name="discount" type="xs:decimal"/>
 *   </xs:sequence>
 *   <xs:assert test="price > discount"/>
 * </xs:complexType>
 * }</pre>
 *
 * <p>Example xs:assertion:</p>
 * <pre>{@code
 * <xs:simpleType name="PositiveInteger">
 *   <xs:restriction base="xs:integer">
 *     <xs:assertion test="$value > 0"/>
 *   </xs:restriction>
 * </xs:simpleType>
 * }</pre>
 *
 * @since 2.0
 */
public class XsdAssertModel {

    private final String id;
    private final String test;  // XPath 2.0 expression (required)
    private String xpathDefaultNamespace;  // Optional default namespace for XPath
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    /**
     * Creates a new assertion model.
     *
     * @param id   unique identifier
     * @param test XPath 2.0 expression that must evaluate to true
     */
    public XsdAssertModel(String id, String test) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
        this.test = Objects.requireNonNull(test, "Test expression cannot be null");
    }

    /**
     * Returns the unique identifier of this assertion.
     *
     * @return the assertion ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the XPath 2.0 test expression.
     *
     * @return the test expression
     */
    public String getTest() {
        return test;
    }

    /**
     * Returns the XPath default namespace.
     *
     * @return the XPath default namespace, or null if not set
     */
    public String getXpathDefaultNamespace() {
        return xpathDefaultNamespace;
    }

    /**
     * Sets the XPath default namespace.
     *
     * @param xpathDefaultNamespace the default namespace for XPath expressions
     */
    public void setXpathDefaultNamespace(String xpathDefaultNamespace) {
        this.xpathDefaultNamespace = xpathDefaultNamespace;
    }

    /**
     * Returns the documentation for this assertion.
     *
     * @return the documentation, or null if not set
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation for this assertion.
     *
     * @param documentation the documentation text
     */
    public void setDocumentation(String documentation) {
        this.documentation = documentation;
    }

    /**
     * Returns the documentation info object.
     *
     * @return the doc info
     */
    public XsdDocInfo getDocInfo() {
        return docInfo;
    }

    /**
     * Sets the documentation info object.
     *
     * @param docInfo the doc info
     */
    public void setDocInfo(XsdDocInfo docInfo) {
        this.docInfo = docInfo != null ? docInfo : new XsdDocInfo();
    }

    @Override
    public String toString() {
        return "XsdAssertModel{" +
                "test='" + test + '\'' +
                (xpathDefaultNamespace != null ? ", xpathDefaultNamespace='" + xpathDefaultNamespace + '\'' : "") +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdAssertModel that = (XsdAssertModel) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(test, that.test);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, test);
    }
}
