package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.Objects;

/**
 * Model representing an XSD 1.1 alternative element (xs:alternative).
 * Alternatives provide conditional type assignment (CTA) based on XPath expressions.
 *
 * <p>Example with test attribute:</p>
 * <pre>{@code
 * <xs:element name="value">
 *   <xs:alternative test="@kind='int'" type="xs:integer"/>
 *   <xs:alternative test="@kind='string'" type="xs:string"/>
 *   <xs:alternative type="xs:anyType"/>
 * </xs:element>
 * }</pre>
 *
 * <p>Example with inline type:</p>
 * <pre>{@code
 * <xs:element name="data">
 *   <xs:alternative test="@format='simple'">
 *     <xs:simpleType>
 *       <xs:restriction base="xs:string"/>
 *     </xs:simpleType>
 *   </xs:alternative>
 * </xs:element>
 * }</pre>
 *
 * @since 2.0
 */
public class XsdAlternativeModel {

    private final String id;
    private String test;  // XPath 2.0 expression (optional, if missing this is the default alternative)
    private String type;  // Type reference (optional if inline type is present)
    private String xpathDefaultNamespace;  // Optional default namespace for XPath
    private String documentation;
    private XsdDocInfo docInfo = new XsdDocInfo();

    // Inline type definitions (mutually exclusive - either simpleType or complexType)
    private XsdSimpleTypeModel inlineSimpleType;
    private XsdComplexTypeModel inlineComplexType;

    /**
     * Creates a new alternative model.
     *
     * @param id unique identifier
     */
    public XsdAlternativeModel(String id) {
        this.id = Objects.requireNonNull(id, "ID cannot be null");
    }

    /**
     * Returns the unique identifier of this alternative.
     *
     * @return the alternative ID
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the XPath 2.0 test expression.
     * If null, this is the default alternative (used when no other alternative matches).
     *
     * @return the test expression, or null for default alternative
     */
    public String getTest() {
        return test;
    }

    /**
     * Sets the XPath 2.0 test expression.
     *
     * @param test the test expression
     */
    public void setTest(String test) {
        this.test = test;
    }

    /**
     * Returns the type reference.
     *
     * @return the type reference, or null if inline type is used
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type reference.
     *
     * @param type the type reference
     */
    public void setType(String type) {
        this.type = type;
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
     * Returns the documentation for this alternative.
     *
     * @return the documentation, or null if not set
     */
    public String getDocumentation() {
        return documentation;
    }

    /**
     * Sets the documentation for this alternative.
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

    /**
     * Returns the inline simple type definition.
     *
     * @return the inline simple type, or null if not present
     */
    public XsdSimpleTypeModel getInlineSimpleType() {
        return inlineSimpleType;
    }

    /**
     * Sets the inline simple type definition.
     *
     * @param inlineSimpleType the inline simple type
     */
    public void setInlineSimpleType(XsdSimpleTypeModel inlineSimpleType) {
        this.inlineSimpleType = inlineSimpleType;
        if (inlineSimpleType != null) {
            this.inlineComplexType = null; // Mutually exclusive
        }
    }

    /**
     * Returns the inline complex type definition.
     *
     * @return the inline complex type, or null if not present
     */
    public XsdComplexTypeModel getInlineComplexType() {
        return inlineComplexType;
    }

    /**
     * Sets the inline complex type definition.
     *
     * @param inlineComplexType the inline complex type
     */
    public void setInlineComplexType(XsdComplexTypeModel inlineComplexType) {
        this.inlineComplexType = inlineComplexType;
        if (inlineComplexType != null) {
            this.inlineSimpleType = null; // Mutually exclusive
        }
    }

    /**
     * Checks if this is a default alternative (no test expression).
     *
     * @return true if this is the default alternative
     */
    public boolean isDefaultAlternative() {
        return test == null || test.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("XsdAlternativeModel{");
        if (test != null && !test.isEmpty()) {
            sb.append("test='").append(test).append("'");
        } else {
            sb.append("default");
        }
        if (type != null) {
            sb.append(", type='").append(type).append("'");
        }
        if (inlineSimpleType != null) {
            sb.append(", inlineSimpleType=").append(inlineSimpleType);
        }
        if (inlineComplexType != null) {
            sb.append(", inlineComplexType=").append(inlineComplexType);
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        XsdAlternativeModel that = (XsdAlternativeModel) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
