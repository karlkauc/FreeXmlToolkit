package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.model;

/**
 * Types of completion items.
 */
public enum CompletionItemType {
    /**
     * XML element.
     */
    ELEMENT,

    /**
     * XML attribute.
     */
    ATTRIBUTE,

    /**
     * Attribute value or text content value.
     */
    VALUE,

    /**
     * Snippet or template.
     */
    SNIPPET,

    /**
     * XPath function (e.g., count(), string-length(), concat()).
     */
    XPATH_FUNCTION,

    /**
     * XPath axis (e.g., ancestor::, child::, following-sibling::).
     */
    XPATH_AXIS,

    /**
     * XPath operator (e.g., and, or, =, !=).
     */
    XPATH_OPERATOR,

    /**
     * XPath variable reference (e.g., $x, $item).
     */
    XPATH_VARIABLE,

    /**
     * XQuery keyword (e.g., for, let, where, return).
     */
    XQUERY_KEYWORD,

    /**
     * XPath node test (e.g., node(), text(), comment()).
     */
    XPATH_NODE_TEST,

    /**
     * XSD type (e.g., complexType, simpleType definitions).
     */
    TYPE
}
