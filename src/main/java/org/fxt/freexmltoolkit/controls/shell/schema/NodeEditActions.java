package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;

/**
 * Editing actions a structured view (Tree/Graphic) can request for a node. The
 * view handles the user interaction (dialogs); the implementation routes the
 * request through the command stack. Keeps the renderer decoupled from the
 * command layer.
 */
public interface NodeEditActions {

    void addElement(XsdNode parent, String name);

    /** Adds a container element (inline complexType + sequence) able to hold child elements. */
    void addContainerElement(XsdNode parent, String name);

    void addAttribute(XsdNode parent, String name);

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

    void rename(XsdNode node, String newName);

    void changeType(XsdNode node, String newType);

    void changeCardinality(XsdNode node, int minOccurs, int maxOccurs);

    void delete(XsdNode node);
}
