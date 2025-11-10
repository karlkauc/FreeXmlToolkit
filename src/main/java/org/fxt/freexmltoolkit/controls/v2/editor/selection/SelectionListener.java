package org.fxt.freexmltoolkit.controls.v2.editor.selection;

import org.fxt.freexmltoolkit.controls.v2.view.XsdNodeRenderer.VisualNode;

import java.util.Set;

/**
 * Listener for selection changes in the XSD editor.
 *
 * @since 2.0
 */
@FunctionalInterface
public interface SelectionListener {

    /**
     * Called when selection changes.
     *
     * @param oldSelection the previous selection
     * @param newSelection the new selection
     */
    void selectionChanged(Set<VisualNode> oldSelection, Set<VisualNode> newSelection);
}
