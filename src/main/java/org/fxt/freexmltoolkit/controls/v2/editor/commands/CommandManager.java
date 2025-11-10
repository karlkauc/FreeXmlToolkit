package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages command execution, undo, and redo operations.
 * Maintains a history stack with configurable limit.
 *
 * @since 2.0
 */
public class CommandManager {

    private static final Logger logger = LogManager.getLogger(CommandManager.class);

    private final Deque<XsdCommand> undoStack = new ArrayDeque<>();
    private final Deque<XsdCommand> redoStack = new ArrayDeque<>();
    private final int historyLimit;
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    /**
     * Creates a command manager with default history limit of 100.
     */
    public CommandManager() {
        this(100);
    }

    /**
     * Creates a command manager with specified history limit.
     *
     * @param historyLimit maximum number of commands to keep in history
     */
    public CommandManager(int historyLimit) {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("History limit must be at least 1");
        }
        this.historyLimit = historyLimit;
    }

    /**
     * Executes a command and adds it to the undo stack.
     * Clears the redo stack.
     *
     * @param command the command to execute
     * @return true if execution was successful
     */
    public boolean executeCommand(XsdCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        logger.debug("Executing command: {}", command.getDescription());

        boolean success = command.execute();

        if (success) {
            // Try to merge with previous command if possible
            if (!undoStack.isEmpty() && command.canMergeWith(undoStack.peek())) {
                XsdCommand previous = undoStack.pop();
                XsdCommand merged = previous.mergeWith(command);
                undoStack.push(merged);
                logger.debug("Merged command with previous: {}", merged.getDescription());
            } else {
                undoStack.push(command);

                // Enforce history limit
                if (undoStack.size() > historyLimit) {
                    undoStack.removeLast();
                    logger.debug("History limit reached, removed oldest command");
                }
            }

            // Clear redo stack
            redoStack.clear();

            firePropertyChange("canUndo", false, true);
            firePropertyChange("canRedo", true, false);

            logger.debug("Command executed successfully: {}", command.getDescription());
        } else {
            logger.warn("Command execution failed: {}", command.getDescription());
        }

        return success;
    }

    /**
     * Undoes the last command.
     *
     * @return true if undo was successful
     */
    public boolean undo() {
        if (!canUndo()) {
            logger.warn("Cannot undo: undo stack is empty");
            return false;
        }

        XsdCommand command = undoStack.pop();
        logger.debug("Undoing command: {}", command.getDescription());

        boolean success = command.undo();

        if (success) {
            redoStack.push(command);

            firePropertyChange("canUndo", true, canUndo());
            firePropertyChange("canRedo", false, true);

            logger.debug("Command undone successfully: {}", command.getDescription());
        } else {
            // Put command back if undo failed
            undoStack.push(command);
            logger.error("Undo failed: {}", command.getDescription());
        }

        return success;
    }

    /**
     * Redoes the last undone command.
     *
     * @return true if redo was successful
     */
    public boolean redo() {
        if (!canRedo()) {
            logger.warn("Cannot redo: redo stack is empty");
            return false;
        }

        XsdCommand command = redoStack.pop();
        logger.debug("Redoing command: {}", command.getDescription());

        boolean success = command.execute();

        if (success) {
            undoStack.push(command);

            firePropertyChange("canUndo", false, true);
            firePropertyChange("canRedo", true, canRedo());

            logger.debug("Command redone successfully: {}", command.getDescription());
        } else {
            // Put command back if redo failed
            redoStack.push(command);
            logger.error("Redo failed: {}", command.getDescription());
        }

        return success;
    }

    /**
     * Checks if undo is possible.
     *
     * @return true if there are commands to undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Checks if redo is possible.
     *
     * @return true if there are commands to redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Gets the description of the next command to undo.
     *
     * @return description or null if nothing to undo
     */
    public String getUndoDescription() {
        return canUndo() ? undoStack.peek().getDescription() : null;
    }

    /**
     * Gets the description of the next command to redo.
     *
     * @return description or null if nothing to redo
     */
    public String getRedoDescription() {
        return canRedo() ? redoStack.peek().getDescription() : null;
    }

    /**
     * Clears all undo/redo history.
     */
    public void clear() {
        boolean hadUndo = canUndo();
        boolean hadRedo = canRedo();

        undoStack.clear();
        redoStack.clear();

        if (hadUndo) {
            firePropertyChange("canUndo", true, false);
        }
        if (hadRedo) {
            firePropertyChange("canRedo", true, false);
        }

        logger.debug("Command history cleared");
    }

    /**
     * Gets the number of commands in the undo stack.
     *
     * @return size of undo stack
     */
    public int getUndoStackSize() {
        return undoStack.size();
    }

    /**
     * Gets the number of commands in the redo stack.
     *
     * @return size of redo stack
     */
    public int getRedoStackSize() {
        return redoStack.size();
    }

    /**
     * Adds a property change listener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * Removes a property change listener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    private void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        propertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
    }
}
