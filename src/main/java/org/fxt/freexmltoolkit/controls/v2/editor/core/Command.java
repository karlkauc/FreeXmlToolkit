package org.fxt.freexmltoolkit.controls.v2.editor.core;

/**
 * Base interface for all commands in the editor system.
 * Commands encapsulate operations that can be executed, undone, and redone.
 *
 * <p>This interface provides the foundation for the Command Pattern implementation,
 * enabling undo/redo functionality across XSD and XML editors.</p>
 *
 * @param <T> the type of command for merging operations
 * @since 2.0
 */
public interface Command<T extends Command<T>> {

    /**
     * Executes the command.
     *
     * @return true if execution was successful
     */
    boolean execute();

    /**
     * Undoes the command, reverting any changes made by execute().
     *
     * @return true if undo was successful
     */
    boolean undo();

    /**
     * Returns a human-readable description of the command.
     *
     * @return description of the command
     */
    String getDescription();

    /**
     * Checks if this command can be undone.
     *
     * @return true if undo is supported
     */
    default boolean canUndo() {
        return true;
    }

    /**
     * Checks if this command can be merged with another command.
     * Merging is used to combine similar consecutive operations
     * (e.g., multiple character insertions into a single text change).
     *
     * @param other the other command to potentially merge with
     * @return true if the commands can be merged
     */
    default boolean canMergeWith(T other) {
        return false;
    }

    /**
     * Merges this command with another command.
     * Called only if canMergeWith() returns true.
     *
     * @param other the command to merge with
     * @return the merged command
     */
    default T mergeWith(T other) {
        throw new UnsupportedOperationException("Merge not supported");
    }
}
