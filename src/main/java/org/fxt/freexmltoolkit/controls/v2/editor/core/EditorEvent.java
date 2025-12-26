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

        /** Find & Replace dialog requested */
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

        public TextChangedEvent(String oldText, String newText) {
            super(Type.TEXT_CHANGED);
            this.oldText = oldText;
            this.newText = newText;
        }

        public String getOldText() {
            return oldText;
        }

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

        public CaretMovedEvent(int oldPosition, int newPosition) {
            super(Type.CARET_MOVED);
            this.oldPosition = oldPosition;
            this.newPosition = newPosition;
        }

        public int getOldPosition() {
            return oldPosition;
        }

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

        public ModeChangedEvent(EditorMode oldMode, EditorMode newMode) {
            super(Type.MODE_CHANGED);
            this.oldMode = oldMode;
            this.newMode = newMode;
        }

        public EditorMode getOldMode() {
            return oldMode;
        }

        public EditorMode getNewMode() {
            return newMode;
        }
    }

    /**
     * Event fired when schema changes.
     */
    public static class SchemaChangedEvent extends EditorEvent {
        private final boolean hasSchema;

        public SchemaChangedEvent(boolean hasSchema) {
            super(Type.SCHEMA_CHANGED);
            this.hasSchema = hasSchema;
        }

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

        public ValidationCompletedEvent(boolean hasErrors, int errorCount) {
            super(Type.VALIDATION_COMPLETED);
            this.hasErrors = hasErrors;
            this.errorCount = errorCount;
        }

        public boolean hasErrors() {
            return hasErrors;
        }

        public int getErrorCount() {
            return errorCount;
        }
    }

    /**
     * Event fired when Find & Replace dialog is requested.
     */
    public static class FindReplaceRequestedEvent extends EditorEvent {
        public FindReplaceRequestedEvent() {
            super(Type.FIND_REPLACE_REQUESTED);
        }
    }

    /**
     * Event fired when validation is requested from context menu.
     */
    public static class ValidationRequestedEvent extends EditorEvent {
        public ValidationRequestedEvent() {
            super(Type.VALIDATION_REQUESTED);
        }
    }
}
