package org.fxt.freexmltoolkit.domain;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a node in the XSD structure in a recursive way.
 * Each node can contain a list of child nodes (also XsdNodeInfo).
 *
 * Extended for XSD 1.1 support with additional attributes.
 */
public record XsdNodeInfo(
        String name,
        String type,
        String xpath,
        String documentation,
        List<XsdNodeInfo> children,
        List<String> exampleValues,
        String minOccurs,
        String maxOccurs,
        NodeType nodeType,
        // XSD 1.1 specific fields
        String xpathExpression,              // For assertions and alternatives
        Map<String, String> xsd11Attributes  // Additional XSD 1.1 attributes
) {
    /**
     * Convenience constructor for backward compatibility (XSD 1.0)
     */
    public XsdNodeInfo(String name, String type, String xpath, String documentation,
                       List<XsdNodeInfo> children, List<String> exampleValues,
                       String minOccurs, String maxOccurs, NodeType nodeType) {
        this(name, type, xpath, documentation, children, exampleValues,
                minOccurs, maxOccurs, nodeType, null, Collections.emptyMap());
    }

    /**
     * Convenience constructor with XPath expression (for assertions/alternatives)
     */
    public XsdNodeInfo(String name, String type, String xpath, String documentation,
                       List<XsdNodeInfo> children, List<String> exampleValues,
                       String minOccurs, String maxOccurs, NodeType nodeType,
                       String xpathExpression) {
        this(name, type, xpath, documentation, children, exampleValues,
                minOccurs, maxOccurs, nodeType, xpathExpression, Collections.emptyMap());
    }

    /**
     * Checks if this node uses XSD 1.1 features
     */
    public boolean isXsd11() {
        return nodeType.isXsd11Feature() ||
                xpathExpression != null ||
                !xsd11Attributes.isEmpty();
    }

    /**
     * Defines the type of node to control the representation in the UI.
     * Extended with XSD 1.1 node types.
     */
    public enum NodeType {
        // XSD 1.0 node types
        ELEMENT(false),
        ATTRIBUTE(false),
        SEQUENCE(false),
        CHOICE(false),
        ANY(false),
        SIMPLE_TYPE(false),
        COMPLEX_TYPE(false),
        SCHEMA(false),

        // XSD 1.1 node types
        ASSERT(true),              // xs:assert - assertions with XPath expressions
        ALTERNATIVE(true),         // xs:alternative - conditional type assignment
        OPEN_CONTENT(true),        // xs:openContent - interspersed wildcards
        OVERRIDE(true),            // xs:override - schema override mechanism
        ALL(false);                // xs:all - exists in 1.0 but enhanced in 1.1

        private final boolean xsd11Feature;

        NodeType(boolean xsd11Feature) {
            this.xsd11Feature = xsd11Feature;
        }

        /**
         * Returns true if this node type is an XSD 1.1 specific feature
         */
        public boolean isXsd11Feature() {
            return xsd11Feature;
        }
    }
}