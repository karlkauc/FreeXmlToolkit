package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

/**
 * Interface for all XML editing commands.
 *
 * <p>Implements the Command pattern to enable undo/redo functionality.
 * All modifications to the XML model MUST go through commands.</p>
 *
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>Commands are atomic - either fully succeed or fully fail</li>
 *   <li>Commands store complete state needed for undo</li>
 *   <li>Commands are reversible via undo()</li>
 *   <li>Commands can be merged for performance (optional)</li>
 *   <li>Commands fire PropertyChangeEvents in execute() and undo()</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * XmlCommand cmd = new RenameNodeCommand(element, "newName");
 * boolean success = cmd.execute();
 * if (success) {
 *     // Later, undo the change
 *     cmd.undo();
 * }
 * }</pre>
 *
 * @author Claude Code
 * @since 2.0
 */
public interface XmlCommand {

    /**
     * Executes the command.
     *
     * <p>This method should:</p>
     * <ul>
     *   <li>Modify the model as intended</li>
     *   <li>Fire PropertyChangeEvents for affected properties</li>
     *   <li>Return true if successful, false otherwise</li>
     * </ul>
     *
     * @return true if the command executed successfully
     */
    boolean execute();

    /**
     * Undoes the command.
     *
     * <p>This method should:</p>
     * <ul>
     *   <li>Restore the model to its previous state</li>
     *   <li>Fire PropertyChangeEvents for affected properties</li>
     *   <li>Return true if successful, false otherwise</li>
     * </ul>
     *
     * @return true if the command was undone successfully
     */
    boolean undo();

    /**
     * Returns a human-readable description of this command.
     *
     * <p>Used for displaying in the UI (e.g., "Undo Rename Element").</p>
     *
     * @return the command description
     */
    String getDescription();

    /**
     * Checks if this command can be undone.
     *
     * <p>Most commands can be undone, but some operations
     * (like save to file) might not be reversible.</p>
     *
     * @return true if this command can be undone
     */
    default boolean canUndo() {
        return true;
    }

    /**
     * Checks if this command can be merged with another command.
     *
     * <p>Command merging is used to combine consecutive similar operations
     * into a single undo step. For example, typing multiple characters
     * in a text field.</p>
     *
     * @param other the other command
     * @return true if this command can be merged with the other
     */
    default boolean canMergeWith(XmlCommand other) {
        return false;
    }

    /**
     * Merges this command with another command.
     *
     * <p>Only called if canMergeWith() returns true.</p>
     *
     * @param other the other command to merge with
     * @return a new merged command
     */
    default XmlCommand mergeWith(XmlCommand other) {
        throw new UnsupportedOperationException("Command does not support merging");
    }
}
