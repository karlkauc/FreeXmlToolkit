package org.fxt.freexmltoolkit.controls;

/**
 * Command interface for XSD editor operations supporting undo/redo functionality.
 * Implements the Command Pattern for all XSD manipulation operations.
 */
public interface XsdCommand {

    /**
     * Execute the command
     *
     * @return true if command was executed successfully
     */
    boolean execute();

    /**
     * Undo the command
     *
     * @return true if command was undone successfully
     */
    boolean undo();

    /**
     * Get description of the command for display in UI
     *
     * @return human-readable description
     */
    String getDescription();

    /**
     * Check if this command can be undone
     *
     * @return true if undoable
     */
    default boolean canUndo() {
        return true;
    }

    /**
     * Check if this command modifies the document
     *
     * @return true if it modifies the XSD document
     */
    default boolean isModifying() {
        return true;
    }
}