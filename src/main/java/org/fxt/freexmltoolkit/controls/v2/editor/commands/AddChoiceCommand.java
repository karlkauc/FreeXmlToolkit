package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.model.*;

/**
 * Command to add a choice compositor to an element.
 * <p>
 * If the element has no complexType, creates an inline complexType with a choice.
 * If the element has a complexType without a compositor, adds a choice to it.
 * <p>
 * Creates structure:
 * <pre>{@code
 * <xs:element name="elementName">
 *     <xs:complexType>
 *         <xs:choice/>
 *     </xs:complexType>
 * </xs:element>
 * }</pre>
 * <p>
 * Supports undo by removing the added choice/complexType.
 *
 * @since 2.0
 */
public class AddChoiceCommand implements XsdCommand {

    private final AddCompositorCommand<XsdChoice> delegate;

    /**
     * Creates a new add choice command.
     *
     * @param targetElement the element to add the choice to
     * @throws IllegalArgumentException if targetElement is null
     */
    public AddChoiceCommand(XsdElement targetElement) {
        this.delegate = AddCompositorCommand.choice(targetElement);
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
     * Gets the added choice (after execute() has been called).
     *
     * @return the added choice, or null if not yet executed
     */
    public XsdChoice getAddedChoice() {
        return delegate.getAddedCompositor();
    }
}
