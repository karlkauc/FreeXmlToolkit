package org.fxt.freexmltoolkit.controls;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages undo/redo operations for XSD editor commands.
 * Maintains command stacks and provides undo/redo functionality.
 */
public class XsdUndoManager {

    private final Deque<XsdCommand> undoStack = new ArrayDeque<>();
    private final Deque<XsdCommand> redoStack = new ArrayDeque<>();
    private final int maxUndoSize;

    /**
     * Listener interface for undo/redo state changes
     */
    public interface UndoRedoListener {
        void onUndoRedoStateChanged(boolean canUndo, boolean canRedo);
    }

    private UndoRedoListener listener;

    public XsdUndoManager() {
        this(100); // Default max 100 undo operations
    }

    public XsdUndoManager(int maxUndoSize) {
        this.maxUndoSize = maxUndoSize;
    }

    /**
     * Execute a command and add it to undo stack
     *
     * @param command the command to execute
     * @return true if command was executed successfully
     */
    public boolean executeCommand(XsdCommand command) {
        if (command.execute()) {
            // Clear redo stack when new command is executed
            redoStack.clear();

            // Add to undo stack if it's undoable
            if (command.canUndo()) {
                undoStack.push(command);

                // Limit undo stack size
                while (undoStack.size() > maxUndoSize) {
                    undoStack.removeLast();
                }
            }

            notifyStateChanged();
            return true;
        }
        return false;
    }

    /**
     * Undo the last command
     *
     * @return true if undo was successful
     */
    public boolean undo() {
        if (!canUndo()) {
            return false;
        }

        XsdCommand command = undoStack.pop();
        if (command.undo()) {
            redoStack.push(command);
            notifyStateChanged();
            return true;
        } else {
            // If undo failed, put command back
            undoStack.push(command);
            return false;
        }
    }

    /**
     * Redo the last undone command
     *
     * @return true if redo was successful
     */
    public boolean redo() {
        if (!canRedo()) {
            return false;
        }

        XsdCommand command = redoStack.pop();
        if (command.execute()) {
            undoStack.push(command);
            notifyStateChanged();
            return true;
        } else {
            // If redo failed, put command back
            redoStack.push(command);
            return false;
        }
    }

    /**
     * Check if undo is possible
     *
     * @return true if there are commands to undo
     */
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    /**
     * Check if redo is possible
     *
     * @return true if there are commands to redo
     */
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Get description of next command to undo
     *
     * @return description or null if no command to undo
     */
    public String getUndoDescription() {
        return canUndo() ? undoStack.peek().getDescription() : null;
    }

    /**
     * Get description of next command to redo
     *
     * @return description or null if no command to redo
     */
    public String getRedoDescription() {
        return canRedo() ? redoStack.peek().getDescription() : null;
    }

    /**
     * Clear all undo/redo history
     */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        notifyStateChanged();
    }

    /**
     * Set listener for undo/redo state changes
     *
     * @param listener the listener to notify
     */
    public void setListener(UndoRedoListener listener) {
        this.listener = listener;
    }

    /**
     * Notify listener of state changes
     */
    private void notifyStateChanged() {
        if (listener != null) {
            listener.onUndoRedoStateChanged(canUndo(), canRedo());
        }
    }

    /**
     * Get current undo stack size
     *
     * @return number of commands in undo stack
     */
    public int getUndoStackSize() {
        return undoStack.size();
    }

    /**
     * Get current redo stack size
     *
     * @return number of commands in redo stack
     */
    public int getRedoStackSize() {
        return redoStack.size();
    }
}