package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add an all compositor to an element.
 * <p>
 * If the element has no complexType, creates an inline complexType with an all.
 * If the element has a complexType without a compositor, adds an all to it.
 * <p>
 * Creates structure:
 * <pre>{@code
 * <xs:element name="elementName">
 *     <xs:complexType>
 *         <xs:all/>
 *     </xs:complexType>
 * </xs:element>
 * }</pre>
 * <p>
 * Supports undo by removing the added all/complexType.
 *
 * @since 2.0
 */
public class AddAllCommand implements XsdCommand {

    private final AddCompositorCommand<XsdAll> delegate;

    /**
     * Creates a new add all command.
     *
     * @param targetElement the element to add the all to
     * @throws IllegalArgumentException if targetElement is null
     */
    public AddAllCommand(XsdElement targetElement) {
        this.delegate = AddCompositorCommand.all(targetElement);
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
     * Gets the added all (after execute() has been called).
     *
     * @return the added all, or null if not yet executed
     */
    public XsdAll getAddedAll() {
        return delegate.getAddedCompositor();
    }
}
