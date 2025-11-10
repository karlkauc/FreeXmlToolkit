package org.fxt.freexmltoolkit.controls.v2.editor.commands;

/**
 * Command interface for XSD editor operations.
 * Implements the Command Pattern for undo/redo functionality.
 *
 * @since 2.0
 */
public interface XsdCommand {

    /**
     * Executes the command.
     *
     * @return true if execution was successful, false otherwise
     */
    boolean execute();

    /**
     * Undoes the command.
     *
     * @return true if undo was successful, false otherwise
     */
    boolean undo();

    /**
     * Returns a human-readable description of this command.
     * Used for undo/redo history display.
     *
     * @return description of the command
     */
    String getDescription();

    /**
     * Checks if this command can be undone.
     *
     * @return true if undoable, false otherwise
     */
    default boolean canUndo() {
        return true;
    }

    /**
     * Checks if this command can be merged with another command.
     * Useful for grouping similar consecutive operations.
     *
     * @param other the other command to check
     * @return true if mergeable, false otherwise
     */
    default boolean canMergeWith(XsdCommand other) {
        return false;
    }

    /**
     * Merges this command with another command.
     *
     * @param other the other command to merge with
     * @return the merged command
     */
    default XsdCommand mergeWith(XsdCommand other) {
        throw new UnsupportedOperationException("This command does not support merging");
    }
}
