package org.fxt.freexmltoolkit.controls.intellisense;

/**
 * Type of completion item for IntelliSense
 */
public enum CompletionItemType {
    ELEMENT("Element"),
    ATTRIBUTE("Attribute"),
    TEXT("Text Value"),
    SNIPPET("Code Snippet"),
    NAMESPACE("Namespace"),
    COMMENT("Comment"),
    CDATA("CDATA Section"),
    PROCESSING_INSTRUCTION("Processing Instruction");

    private final String displayName;

    CompletionItemType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}