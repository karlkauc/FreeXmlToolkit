package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.core.AbstractCommandManager;

/**
 * Manages command execution, undo, and redo operations for XML editing.
 * Extends AbstractCommandManager with XmlCommand type.
 *
 * @since 2.0
 */
public class CommandManager extends AbstractCommandManager<XmlCommand> {

    /**
     * Constructs a new CommandManager with default history limit.
     */
    public CommandManager() {
        super();
    }

    /**
     * Constructs a new CommandManager with custom history limit.
     *
     * @param historyLimit the maximum number of commands to keep
     */
    public CommandManager(int historyLimit) {
        super(historyLimit);
    }
}
