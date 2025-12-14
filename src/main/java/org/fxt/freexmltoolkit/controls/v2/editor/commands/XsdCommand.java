package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.core.Command;

/**
 * Command interface for XSD editor operations.
 * Extends the generic Command interface for XSD-specific operations.
 *
 * @since 2.0
 */
public interface XsdCommand extends Command<XsdCommand> {

    // All methods are inherited from Command<XsdCommand>
    // Implementations should implement execute(), undo(), getDescription()
    // Optionally override canUndo(), canMergeWith(), mergeWith()
}
