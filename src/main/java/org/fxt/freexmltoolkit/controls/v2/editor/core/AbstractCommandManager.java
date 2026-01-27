package org.fxt.freexmltoolkit.controls.v2.editor.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Abstract base class for command managers implementing undo/redo functionality.
 *
 * <p>Uses a dual-stack architecture:</p>
 * <ul>
 *   <li>undoStack - commands that can be undone</li>
 *   <li>redoStack - commands that can be redone</li>
 * </ul>
 *
 * <p><strong>Observable Properties:</strong></p>
 * <ul>
 *   <li>canUndo - boolean, fires when undo availability changes</li>
 *   <li>canRedo - boolean, fires when redo availability changes</li>
 *   <li>dirty - boolean, fires when document is modified</li>
 *   <li>undoDescription - String, description of next undo command</li>
 *   <li>redoDescription - String, description of next redo command</li>
 * </ul>
 *
 * @param <T> the type of command this manager handles
 * @since 2.0
 */
public abstract class AbstractCommandManager<T extends Command<T>> {

    private static final Logger logger = LogManager.getLogger(AbstractCommandManager.class);

    /**
     * Default history limit (100 commands).
     */
    private static final int DEFAULT_HISTORY_LIMIT = 100;

    /**
     * Stack of commands that can be undone.
     */
    private final Deque<T> undoStack = new ArrayDeque<>();

    /**
     * Stack of commands that can be redone.
     */
    private final Deque<T> redoStack = new ArrayDeque<>();

    /**
     * Maximum number of commands to keep in history.
     */
    private int historyLimit;

    /**
     * Dirty flag - true if document has unsaved changes.
     */
    private boolean dirty = false;

    /**
     * PropertyChangeSupport for observable properties.
     */
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /**
     * Constructs a new CommandManager with default history limit.
     */
    protected AbstractCommandManager() {
        this(DEFAULT_HISTORY_LIMIT);
    }

    /**
     * Constructs a new CommandManager with custom history limit.
     *
     * @param historyLimit the maximum number of commands to keep
     */
    protected AbstractCommandManager(int historyLimit) {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("History limit must be at least 1");
        }
        this.historyLimit = historyLimit;
    }

    // ==================== Command Execution ====================

    /**
     * Executes a command and adds it to the undo stack.
     *
     * <p>If execution succeeds:</p>
     * <ul>
     *   <li>Command is pushed to undo stack</li>
     *   <li>Redo stack is cleared</li>
     *   <li>Dirty flag is set to true</li>
     *   <li>Property change events are fired</li>
     * </ul>
     *
     * <p>If the command can be merged with the last command,
     * it is merged instead of being added separately.</p>
     *
     * @param command the command to execute
     * @return true if the command executed successfully
     */
    public boolean executeCommand(T command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }

        logger.debug("Executing command: {}", command.getDescription());

        // Execute the command FIRST (this updates the model)
        boolean success = command.execute();

        if (!success) {
            logger.warn("Command execution failed: {}", command.getDescription());
            return false;
        }

        // Try to merge with previous command (AFTER execution)
        if (!undoStack.isEmpty()) {
            T lastCommand = undoStack.peek();
            if (lastCommand.canMergeWith(command)) {
                T merged = lastCommand.mergeWith(command);
                undoStack.pop();
                undoStack.push(merged);
                // Clear redo stack on new command
                redoStack.clear();
                setDirty(true);
                firePropertyChanges();
                logger.debug("Merged command with previous: {}", merged.getDescription());
                return true;
            }
        }

        // Not mergeable, add as new command
        // (command was already executed above)

        // Add to undo stack
        undoStack.push(command);

        // Enforce history limit
        if (undoStack.size() > historyLimit) {
            undoStack.removeLast();
            logger.debug("History limit reached, removed oldest command");
        }

        // Clear redo stack
        redoStack.clear();

        // Set dirty flag
        setDirty(true);

        // Fire property changes
        firePropertyChanges();

        logger.debug("Command executed successfully: {}", command.getDescription());

        return true;
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

        T command = undoStack.pop();
        logger.debug("Undoing command: {}", command.getDescription());

        boolean success = command.undo();

        if (success) {
            redoStack.push(command);
            setDirty(true);
            firePropertyChanges();
            logger.debug("Command undone successfully: {}", command.getDescription());
        } else {
            // Restore command to undo stack if undo failed
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

        T command = redoStack.pop();
        logger.debug("Redoing command: {}", command.getDescription());

        boolean success = command.execute();

        if (success) {
            undoStack.push(command);
            setDirty(true);
            firePropertyChanges();
            logger.debug("Command redone successfully: {}", command.getDescription());
        } else {
            // Restore command to redo stack if execute failed
            redoStack.push(command);
            logger.error("Redo failed: {}", command.getDescription());
        }

        return success;
    }

    // ==================== Query Methods ====================

    /**
     * Checks if undo is available.
     *
     * @return true if there are commands to undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty() && undoStack.peek().canUndo();
    }

    /**
     * Checks if redo is available.
     *
     * @return true if there are commands to redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Returns the description of the next undo command.
     *
     * @return the undo description, or null if no undo available
     */
    public String getUndoDescription() {
        if (!canUndo()) {
            return null;
        }
        return undoStack.peek().getDescription();
    }

    /**
     * Returns the description of the next redo command.
     *
     * @return the redo description, or null if no redo available
     */
    public String getRedoDescription() {
        if (!canRedo()) {
            return null;
        }
        return redoStack.peek().getDescription();
    }

    /**
     * Returns the number of commands in the undo stack.
     *
     * @return the undo stack size
     */
    public int getUndoStackSize() {
        return undoStack.size();
    }

    /**
     * Returns the number of commands in the redo stack.
     *
     * @return the redo stack size
     */
    public int getRedoStackSize() {
        return redoStack.size();
    }

    // ==================== History Management ====================

    /**
     * Clears all undo and redo history.
     */
    public void clear() {
        boolean hadUndo = canUndo();
        boolean hadRedo = canRedo();

        undoStack.clear();
        redoStack.clear();

        if (hadUndo || hadRedo) {
            firePropertyChanges();
        }

        logger.debug("Command history cleared");
    }

    /**
     * Marks the current state as saved.
     * Clears the dirty flag.
     */
    public void markAsSaved() {
        setDirty(false);
    }

    /**
     * Returns the history limit.
     *
     * @return the maximum number of commands to keep
     */
    public int getHistoryLimit() {
        return historyLimit;
    }

    /**
     * Sets the history limit.
     *
     * @param historyLimit the new history limit (must be > 0)
     */
    public void setHistoryLimit(int historyLimit) {
        if (historyLimit <= 0) {
            throw new IllegalArgumentException("History limit must be > 0");
        }
        this.historyLimit = historyLimit;

        // Trim undo stack if needed
        while (undoStack.size() > historyLimit) {
            undoStack.removeLast();
        }
    }

    // ==================== Dirty Flag ====================

    /**
     * Checks if the document has unsaved changes.
     *
     * @return true if dirty
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty flag.
     * Fires a "dirty" property change event.
     *
     * @param dirty the new dirty value
     */
    private void setDirty(boolean dirty) {
        boolean oldDirty = this.dirty;
        this.dirty = dirty;
        pcs.firePropertyChange("dirty", oldDirty, dirty);
    }

    // ==================== Property Change Support ====================

    /**
     * Adds a PropertyChangeListener.
     *
     * @param listener the listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    /**
     * Removes a PropertyChangeListener.
     *
     * @param listener the listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    /**
     * Adds a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to add
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(propertyName, listener);
    }

    /**
     * Removes a PropertyChangeListener for a specific property.
     *
     * @param propertyName the property name
     * @param listener     the listener to remove
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(propertyName, listener);
    }

    /**
     * Fires property change events for canUndo, canRedo, and descriptions.
     */
    protected void firePropertyChanges() {
        pcs.firePropertyChange("canUndo", null, canUndo());
        pcs.firePropertyChange("canRedo", null, canRedo());
        pcs.firePropertyChange("undoDescription", null, getUndoDescription());
        pcs.firePropertyChange("redoDescription", null, getRedoDescription());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[undoStack=" + undoStack.size() +
                ", redoStack=" + redoStack.size() +
                ", dirty=" + dirty + "]";
    }
}
