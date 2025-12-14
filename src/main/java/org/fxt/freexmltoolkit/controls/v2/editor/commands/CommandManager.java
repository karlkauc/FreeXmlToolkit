package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.core.AbstractCommandManager;

/**
 * Manages command execution, undo, and redo operations for XSD editing.
 * Extends AbstractCommandManager with XsdCommand type.
 *
 * @since 2.0
 */
public class CommandManager extends AbstractCommandManager<XsdCommand> {

    /**
     * Creates a command manager with default history limit of 100.
     */
    public CommandManager() {
        super();
    }

    /**
     * Creates a command manager with specified history limit.
     *
     * @param historyLimit maximum number of commands to keep in history
     */
    public CommandManager(int historyLimit) {
        super(historyLimit);
    }
}
