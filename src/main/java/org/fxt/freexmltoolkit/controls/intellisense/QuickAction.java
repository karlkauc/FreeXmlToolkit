package org.fxt.freexmltoolkit.controls.intellisense;

import javafx.scene.input.KeyCombination;

/**
 * Represents a quick action that can be performed in the XML editor.
 * Quick actions provide context-sensitive operations for XML editing.
 */
public class QuickAction {

    private final String id;
    private final String name;
    private final String description;
    private final QuickActionType type;
    private final ActionHandler handler;
    private final KeyCombination shortcut;
    private final String iconName;
    private final int priority;
    private final boolean requiresSelection;

    public QuickAction(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.description = builder.description;
        this.type = builder.type;
        this.handler = builder.handler;
        this.shortcut = builder.shortcut;
        this.iconName = builder.iconName;
        this.priority = builder.priority;
        this.requiresSelection = builder.requiresSelection;
    }

    /**
     * Execute the quick action
     */
    public ActionResult execute(ActionContext context) {
        if (requiresSelection && (context.selectedText == null || context.selectedText.isEmpty())) {
            return ActionResult.failure("This action requires text selection");
        }

        try {
            return handler.handle(context);
        } catch (Exception e) {
            return ActionResult.failure("Action failed: " + e.getMessage());
        }
    }

    /**
     * Check if action is applicable in current context
     */
    public boolean isApplicable(ActionContext context) {
        if (requiresSelection && (context.selectedText == null || context.selectedText.isEmpty())) {
            return false;
        }

        return switch (type) {
            case XML_FORMATTING -> context.hasXmlContent;
            case XPATH_QUERY -> context.hasXmlContent;
            case ELEMENT_OPERATIONS -> context.hasXmlContent && context.cursorInElement;
            case ATTRIBUTE_OPERATIONS -> context.hasXmlContent && context.cursorInAttribute;
            case NAMESPACE_OPERATIONS -> context.hasXmlContent && context.hasNamespaces;
            case SCHEMA_VALIDATION -> context.hasXsdSchema;
            case TRANSFORMATION -> context.hasXmlContent;
            case GENERAL -> true;
        };
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public QuickActionType getType() {
        return type;
    }

    public KeyCombination getShortcut() {
        return shortcut;
    }

    public String getIconName() {
        return iconName;
    }

    public int getPriority() {
        return priority;
    }

    public boolean requiresSelection() {
        return requiresSelection;
    }

    @Override
    public String toString() {
        return String.format("QuickAction{id='%s', name='%s', type=%s}", id, name, type);
    }

    /**
     * Quick action types for categorization
     */
    public enum QuickActionType {
        XML_FORMATTING("XML Formatting"),
        XPATH_QUERY("XPath Query"),
        ELEMENT_OPERATIONS("Element Operations"),
        ATTRIBUTE_OPERATIONS("Attribute Operations"),
        NAMESPACE_OPERATIONS("Namespace Operations"),
        SCHEMA_VALIDATION("Schema Validation"),
        TRANSFORMATION("Transformation"),
        GENERAL("General");

        private final String displayName;

        QuickActionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Action execution context
     */
    public static class ActionContext {
        public String fullText;
        public String selectedText;
        public int caretPosition;
        public int selectionStart;
        public int selectionEnd;
        public boolean hasXmlContent;
        public boolean hasXsdSchema;
        public boolean hasNamespaces;
        public boolean cursorInElement;
        public boolean cursorInAttribute;
        public String currentElement;
        public String currentNamespace;
        public String filePath;

        public ActionContext(String fullText, String selectedText, int caretPosition) {
            this.fullText = fullText;
            this.selectedText = selectedText;
            this.caretPosition = caretPosition;
        }
    }

    /**
     * Action execution result
     */
    public static class ActionResult {
        private final boolean success;
        private final String message;
        private final String modifiedText;
        private final int newCaretPosition;

        private ActionResult(boolean success, String message, String modifiedText, int newCaretPosition) {
            this.success = success;
            this.message = message;
            this.modifiedText = modifiedText;
            this.newCaretPosition = newCaretPosition;
        }

        public static ActionResult success(String modifiedText, int newCaretPosition) {
            return new ActionResult(true, "Success", modifiedText, newCaretPosition);
        }

        public static ActionResult success(String message) {
            return new ActionResult(true, message, null, -1);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message, null, -1);
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getModifiedText() {
            return modifiedText;
        }

        public int getNewCaretPosition() {
            return newCaretPosition;
        }

        public boolean hasTextModification() {
            return modifiedText != null;
        }
    }

    /**
     * Functional interface for action handlers
     */
    @FunctionalInterface
    public interface ActionHandler {
        ActionResult handle(ActionContext context);
    }

    /**
     * Builder for QuickAction
     */
    public static class Builder {
        private final String id;
        private final String name;
        private String description = "";
        private QuickActionType type = QuickActionType.GENERAL;
        private final ActionHandler handler;
        private KeyCombination shortcut;
        private String iconName = "action";
        private int priority = 100;
        private boolean requiresSelection = false;

        public Builder(String id, String name, ActionHandler handler) {
            this.id = id;
            this.name = name;
            this.handler = handler;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(QuickActionType type) {
            this.type = type;
            return this;
        }

        public Builder shortcut(KeyCombination shortcut) {
            this.shortcut = shortcut;
            return this;
        }

        public Builder icon(String iconName) {
            this.iconName = iconName;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder requiresSelection(boolean requiresSelection) {
            this.requiresSelection = requiresSelection;
            return this;
        }

        public QuickAction build() {
            return new QuickAction(this);
        }
    }
}