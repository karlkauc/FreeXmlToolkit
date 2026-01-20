package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

/**
 * Types of XML contexts where IntelliSense can be triggered.
 */
public enum ContextType {
    /**
     * Unknown or invalid context.
     */
    UNKNOWN,

    /**
     * Inside element tags, after '&lt;' character.
     * Example: &lt;|
     */
    ELEMENT,

    /**
     * Inside element tags, waiting for attribute name.
     * Example: &lt;element |&gt;
     */
    ATTRIBUTE,

    /**
     * Inside attribute value quotes.
     * Example: &lt;element attr="|"&gt;
     */
    ATTRIBUTE_VALUE,

    /**
     * Inside element text content.
     * Example: &lt;element&gt;|&lt;/element&gt;
     */
    TEXT_CONTENT,

    /**
     * Inside XML comment.
     * Example: &lt;!-- | --&gt;
     */
    COMMENT,

    /**
     * Inside CDATA section.
     * Example: &lt;![CDATA[|]]&gt;
     */
    CDATA,

    /**
     * Inside processing instruction.
     * Example: &lt;?xml |?&gt;
     */
    PROCESSING_INSTRUCTION,

    /**
     * Inside DOCTYPE declaration.
     * Example: &lt;!DOCTYPE |&gt;
     */
    DOCTYPE;

    /**
     * Checks if this context type supports element completion.
     *
     * @return true if element completion is applicable
     */
    public boolean supportsElementCompletion() {
        return this == ELEMENT;
    }

    /**
     * Checks if this context type supports attribute completion.
     *
     * @return true if attribute completion is applicable
     */
    public boolean supportsAttributeCompletion() {
        return this == ATTRIBUTE;
    }

    /**
     * Checks if this context type supports value completion.
     *
     * @return true if value completion is applicable
     */
    public boolean supportsValueCompletion() {
        return this == ATTRIBUTE_VALUE || this == TEXT_CONTENT;
    }

    /**
     * Checks if IntelliSense should be disabled in this context.
     *
     * @return true if IntelliSense is not applicable
     */
    public boolean shouldDisableIntelliSense() {
        return this == COMMENT || this == CDATA ||
               this == PROCESSING_INSTRUCTION || this == UNKNOWN;
    }
}
