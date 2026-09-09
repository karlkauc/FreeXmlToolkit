package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.scene.Node;
import javafx.scene.input.KeyEvent;

/**
 * Context menu of a grid canvas, supplied per document model by the
 * {@link GridModelAdapter}. The canvas only needs to show it for a node (optionally
 * with the embedded-table cell that was clicked) and to forward keyboard shortcuts.
 *
 * @param <N> the model node type (XmlNode / JsonNode)
 */
public interface GridContextMenu<N> {

    /** Shows the menu for the given node at the given screen position. */
    void show(Node anchor, double screenX, double screenY, N selectedNode);

    /** Shows the menu for a node inside an embedded table, remembering the clicked cell. */
    void show(Node anchor, double screenX, double screenY, N selectedNode,
              RepeatingElementsTable table, int rowIndex, String columnName);

    /** Hides the menu if it is showing. */
    void hide();

    /** Handles the menu's keyboard shortcuts (Delete, F2, Ctrl+C/X/V/D, …) for the node. */
    void handleKeyPress(KeyEvent event, N selectedNode);

    /** @return whether the menu's internal clipboard holds a node */
    boolean hasClipboard();
}
