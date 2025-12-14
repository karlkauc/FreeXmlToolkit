package org.fxt.freexmltoolkit.controls.v2.xmleditor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.core.Command;

/**
 * Interface for all XML editing commands.
 * Extends the generic Command interface for XML-specific operations.
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
 * @since 2.0
 */
public interface XmlCommand extends Command<XmlCommand> {

    // All methods are inherited from Command<XmlCommand>
    // Implementations should implement execute(), undo(), getDescription()
    // Optionally override canUndo(), canMergeWith(), mergeWith()
}
