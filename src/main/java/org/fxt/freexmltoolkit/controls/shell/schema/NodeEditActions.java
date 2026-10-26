package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.NewAttributeSpec;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.NewElementSpec;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Editing actions a structured view (Tree/Graphic) can request for a node. The
 * view handles the user interaction (dialogs); the implementation routes the
 * request through the command stack. Keeps the renderer decoupled from the
 * command layer.
 */
public interface NodeEditActions {

    void addElement(XsdNode parent, String name);

    /** Adds an element with every property of {@code spec} (type, cardinality, …) in one step. */
    default void addElement(XsdNode parent, NewElementSpec spec) {
        addElement(parent, spec.name());
    }

    /** Adds an {@code <!-- … -->} comment node as a child of {@code parent}. */
    void addComment(XsdNode parent, String content);

    /** Adds a container element (inline complexType + sequence) able to hold child elements. */
    void addContainerElement(XsdNode parent, String name);

    void addAttribute(XsdNode parent, String name);

    /** Adds an attribute with every property of {@code spec} (type, use, …) in one step. */
    default void addAttribute(XsdNode parent, NewAttributeSpec spec) {
        addAttribute(parent, spec.name());
    }

    /** Asks for and declares a new top-level type of the schema ({@code kind} preselected). */
    default void newType(AddGlobalTypeCommand.Kind kind) {
    }

    void addSequence(XsdNode element);

    void addChoice(XsdNode element);

    void addAll(XsdNode element);

    void duplicate(XsdNode node);

    void moveUp(XsdNode node);

    void moveDown(XsdNode node);

    void copy(XsdNode node);

    void cut(XsdNode node);

    void paste(XsdNode target);

    /** @return whether the clipboard currently holds a node to paste. */
    boolean canPaste();

    /** @return whether the clipboard's node may be pasted under {@code target} (valid XSD). */
    default boolean canPasteInto(XsdNode target) {
        return canPaste();
    }

    void rename(XsdNode node, String newName);

    void changeType(XsdNode node, String newType);

    void changeCardinality(XsdNode node, int minOccurs, int maxOccurs);

    void delete(XsdNode node);
}
