package org.fxt.freexmltoolkit.controls.intellisense;

/**
 * Context information for code completion requests.
 * Contains all relevant information about the current editor state
 * to provide intelligent completion suggestions.
 */
public class CompletionContext {

    private final String fullText;
    private final String selectedText;
    private final int caretPosition;
    private final String currentElement;

    // XML-specific context
    private boolean isInElement;
    private boolean isInAttribute;
    private boolean isInAttributeValue;
    private String parentElement;
    private String currentNamespace;

    // Schema context
    private boolean hasXsdSchema;
    private String documentType;

    // Completion type
    private CompletionType completionType;

    public enum CompletionType {
        ELEMENT,
        ATTRIBUTE,
        ATTRIBUTE_VALUE,
        TEXT_CONTENT,
        NAMESPACE,
        TEMPLATE
    }

    public CompletionContext(String fullText, String selectedText, int caretPosition, String currentElement) {
        this.fullText = fullText;
        this.selectedText = selectedText != null ? selectedText : "";
        this.caretPosition = caretPosition;
        this.currentElement = currentElement;
        this.completionType = CompletionType.ELEMENT; // Default
    }

    // Getters
    public String getFullText() {
        return fullText;
    }

    public String getSelectedText() {
        return selectedText;
    }

    public int getCaretPosition() {
        return caretPosition;
    }

    public String getCurrentElement() {
        return currentElement;
    }

    public boolean isInElement() {
        return isInElement;
    }

    public boolean isInAttribute() {
        return isInAttribute;
    }

    public boolean isInAttributeValue() {
        return isInAttributeValue;
    }

    public String getParentElement() {
        return parentElement;
    }

    public String getCurrentNamespace() {
        return currentNamespace;
    }

    public boolean hasXsdSchema() {
        return hasXsdSchema;
    }

    public String getDocumentType() {
        return documentType;
    }

    public CompletionType getCompletionType() {
        return completionType;
    }

    // Setters
    public void setInElement(boolean inElement) {
        this.isInElement = inElement;
    }

    public void setInAttribute(boolean inAttribute) {
        this.isInAttribute = inAttribute;
    }

    public void setInAttributeValue(boolean inAttributeValue) {
        this.isInAttributeValue = inAttributeValue;
    }

    public void setParentElement(String parentElement) {
        this.parentElement = parentElement;
    }

    public void setCurrentNamespace(String currentNamespace) {
        this.currentNamespace = currentNamespace;
    }

    public void setHasXsdSchema(boolean hasXsdSchema) {
        this.hasXsdSchema = hasXsdSchema;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public void setCompletionType(CompletionType completionType) {
        this.completionType = completionType;
    }

    @Override
    public String toString() {
        return String.format("CompletionContext{element='%s', type=%s, inElement=%s, inAttribute=%s}",
                currentElement, completionType, isInElement, isInAttribute);
    }
}