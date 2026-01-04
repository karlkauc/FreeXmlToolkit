package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add a sequence compositor to an element.
 * <p>
 * If the element has no complexType, creates an inline complexType with a sequence.
 * If the element has a complexType without a compositor, adds a sequence to it.
 * <p>
 * Creates structure:
 * <pre>{@code
 * <xs:element name="elementName">
 *     <xs:complexType>
 *         <xs:sequence/>
 *     </xs:complexType>
 * </xs:element>
 * }</pre>
 * <p>
 * Supports undo by removing the added sequence/complexType.
 *
 * @since 2.0
 */
public class AddSequenceCommand implements XsdCommand {

    private final AddCompositorCommand<XsdSequence> delegate;

    /**
     * Creates a new add sequence command.
     *
     * @param targetElement the element to add the sequence to
     * @throws IllegalArgumentException if targetElement is null
     */
    public AddSequenceCommand(XsdElement targetElement) {
        this.delegate = AddCompositorCommand.sequence(targetElement);
    }

    @Override
    public boolean execute() {
        return delegate.execute();
    }

    @Override
    public boolean undo() {
        return delegate.undo();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public boolean canUndo() {
        return delegate.canUndo();
    }

    @Override
    public boolean canMergeWith(XsdCommand other) {
        return delegate.canMergeWith(other);
    }

    /**
     * Gets the added sequence (after execute() has been called).
     *
     * @return the added sequence, or null if not yet executed
     */
    public XsdSequence getAddedSequence() {
        return delegate.getAddedCompositor();
    }
}
