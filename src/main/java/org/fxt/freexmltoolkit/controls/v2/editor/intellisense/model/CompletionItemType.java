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
    SNIPPET
}
