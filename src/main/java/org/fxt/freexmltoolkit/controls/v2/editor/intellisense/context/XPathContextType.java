package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

/**
 * Context types for XPath/XQuery expression parsing.
 * Determines what kind of completions should be offered based on cursor position.
 */
public enum XPathContextType {

    /**
     * At the start of the expression or after a path separator.
     * Suggests: elements, axes, functions.
     */
    PATH_START,

    /**
     * After a '/' character - expecting element name or axis.
     * Suggests: elements, axes, node tests.
     */
    AFTER_SLASH,

    /**
     * After '//' - expecting descendant element or axis.
     * Suggests: elements, axes, node tests.
     */
    AFTER_DOUBLE_SLASH,

    /**
     * Inside a predicate '[...]' - expecting expression.
     * Suggests: functions, attributes, operators, elements.
     */
    IN_PREDICATE,

    /**
     * Inside function arguments '(...)'.
     * Suggests: elements, attributes, functions, variables.
     */
    IN_FUNCTION_ARGS,

    /**
     * After an axis specifier '::'.
     * Suggests: node tests (element names, *, node(), text()).
     */
    AFTER_AXIS,

    /**
     * After '@' - expecting attribute name.
     * Suggests: attributes only.
     */
    AFTER_AT,

    /**
     * After '$' - expecting variable name.
     * Suggests: variables (XQuery mode).
     */
    AFTER_DOLLAR,

    /**
     * Inside a string literal (single or double quotes).
     * No suggestions needed.
     */
    IN_STRING_LITERAL,

    /**
     * Inside an XPath comment '(: ... :)'.
     * No suggestions needed.
     */
    IN_COMMENT,

    /**
     * After an operator (and, or, =, etc.) - expecting expression.
     * Suggests: elements, functions, variables.
     */
    AFTER_OPERATOR,

    /**
     * XQuery: After 'for' keyword - expecting variable declaration.
     * Suggests: $variable pattern.
     */
    AFTER_FOR,

    /**
     * XQuery: After 'let' keyword - expecting variable declaration.
     * Suggests: $variable pattern.
     */
    AFTER_LET,

    /**
     * XQuery: After 'in' or ':=' - expecting sequence expression.
     * Suggests: elements, paths, functions.
     */
    AFTER_BINDING,

    /**
     * XQuery: After 'where' keyword - expecting boolean expression.
     * Suggests: comparison expressions, functions.
     */
    AFTER_WHERE,

    /**
     * XQuery: After 'return' keyword - expecting expression.
     * Suggests: elements, constructors, functions.
     */
    AFTER_RETURN,

    /**
     * XQuery: In the main body, general context.
     * Suggests: FLWOR keywords, elements, functions.
     */
    XQUERY_BODY,

    /**
     * Unknown or unrecognized context.
     * Default suggestions.
     */
    UNKNOWN
}
