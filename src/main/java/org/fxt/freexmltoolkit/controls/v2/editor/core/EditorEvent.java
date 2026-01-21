package org.fxt.freexmltoolkit.controls.v2.editor.core;

import java.util.Objects;

/**
 * Base class for all editor events.
 * Events are published via the EditorEventBus and processed by registered listeners.
 */
public abstract class EditorEvent {

    /**
     * Event type enumeration.
     */
    public enum Type {
        /** Text content has changed */
        TEXT_CHANGED,

        /** Caret position has moved */
        CARET_MOVED,

        /** Editor mode has changed */
        MODE_CHANGED,

        /** XSD schema has been loaded or unloaded */
        SCHEMA_CHANGED,

        /** Validation has completed */
        VALIDATION_COMPLETED,

        /** IntelliSense state has changed */
        INTELLISENSE_STATE_CHANGED,

        /** Folding regions have been updated */
        FOLDING_UPDATED,

        /** Find &amp; Replace dialog requested */
        FIND_REPLACE_REQUESTED,

        /** Validation requested from context menu */
        VALIDATION_REQUESTED
    }

    private final Type type;
    private final long timestamp;

    /**
     * Creates a new editor event.
     *
     * @param type the event type
     */
    protected EditorEvent(Type type) {
        this.type = Objects.requireNonNull(type, "Event type cannot be null");
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the event type.
     *
     * @return the event type
     */
    public Type getType() {
        return type;
    }

    /**
     * Gets the timestamp when this event was created.
     *
     * @return the timestamp in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{type=" + type + ", timestamp=" + timestamp + "}";
    }

    // Specific event types

    /**
     * Event fired when text content changes.
     */
    public static class TextChangedEvent extends EditorEvent {
        private final String oldText;
        private final String newText;

        /**
         * Creates a new text changed event.
         *
         * @param oldText the text before the change
         * @param newText the text after the change
         */
        public TextChangedEvent(String oldText, String newText) {
            super(Type.TEXT_CHANGED);
            this.oldText = oldText;
            this.newText = newText;
        }

        /**
         * Returns the text before the change.
         *
         * @return the text before the change
         */
        public String getOldText() {
            return oldText;
        }

        /**
         * Returns the text after the change.
         *
         * @return the text after the change
         */
        public String getNewText() {
            return newText;
        }
    }

    /**
     * Event fired when caret position changes.
     */
    public static class CaretMovedEvent extends EditorEvent {
        private final int oldPosition;
        private final int newPosition;

        /**
         * Creates a new caret moved event.
         *
         * @param oldPosition the caret position before the move
         * @param newPosition the caret position after the move
         */
        public CaretMovedEvent(int oldPosition, int newPosition) {
            super(Type.CARET_MOVED);
            this.oldPosition = oldPosition;
            this.newPosition = newPosition;
        }

        /**
         * Returns the caret position before the move.
         *
         * @return the caret position before the move
         */
        public int getOldPosition() {
            return oldPosition;
        }

        /**
         * Returns the caret position after the move.
         *
         * @return the caret position after the move
         */
        public int getNewPosition() {
            return newPosition;
        }
    }

    /**
     * Event fired when editor mode changes.
     */
    public static class ModeChangedEvent extends EditorEvent {
        private final EditorMode oldMode;
        private final EditorMode newMode;

        /**
         * Creates a new mode changed event.
         *
         * @param oldMode the editor mode before the change
         * @param newMode the editor mode after the change
         */
        public ModeChangedEvent(EditorMode oldMode, EditorMode newMode) {
            super(Type.MODE_CHANGED);
            this.oldMode = oldMode;
            this.newMode = newMode;
        }

        /**
         * Returns the editor mode before the change.
         *
         * @return the editor mode before the change
         */
        public EditorMode getOldMode() {
            return oldMode;
        }

        /**
         * Returns the editor mode after the change.
         *
         * @return the editor mode after the change
         */
        public EditorMode getNewMode() {
            return newMode;
        }
    }

    /**
     * Event fired when schema changes.
     */
    public static class SchemaChangedEvent extends EditorEvent {
        private final boolean hasSchema;

        /**
         * Creates a new schema changed event.
         *
         * @param hasSchema true if a schema is now loaded, false otherwise
         */
        public SchemaChangedEvent(boolean hasSchema) {
            super(Type.SCHEMA_CHANGED);
            this.hasSchema = hasSchema;
        }

        /**
         * Checks if a schema is loaded.
         *
         * @return true if a schema is loaded
         */
        public boolean hasSchema() {
            return hasSchema;
        }
    }

    /**
     * Event fired when validation completes.
     */
    public static class ValidationCompletedEvent extends EditorEvent {
        private final boolean hasErrors;
        private final int errorCount;

        /**
         * Creates a new validation completed event.
         *
         * @param hasErrors true if validation found errors
         * @param errorCount the number of validation errors found
         */
        public ValidationCompletedEvent(boolean hasErrors, int errorCount) {
            super(Type.VALIDATION_COMPLETED);
            this.hasErrors = hasErrors;
            this.errorCount = errorCount;
        }

        /**
         * Checks if validation found errors.
         *
         * @return true if validation found errors
         */
        public boolean hasErrors() {
            return hasErrors;
        }

        /**
         * Returns the number of validation errors.
         *
         * @return the number of validation errors
         */
        public int getErrorCount() {
            return errorCount;
        }
    }

    /**
     * Event fired when Find &amp; Replace dialog is requested.
     */
    public static class FindReplaceRequestedEvent extends EditorEvent {
        /**
         * Creates a new find and replace requested event.
         */
        public FindReplaceRequestedEvent() {
            super(Type.FIND_REPLACE_REQUESTED);
        }
    }

    /**
     * Event fired when validation is requested from context menu.
     */
    public static class ValidationRequestedEvent extends EditorEvent {
        /**
         * Creates a new validation requested event.
         */
        public ValidationRequestedEvent() {
            super(Type.VALIDATION_REQUESTED);
        }
    }
}
