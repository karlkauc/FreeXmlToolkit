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

    /**
     * The canvas-level actions a menu can offer besides its model edits.
     *
     * @param expandAll          expands every container
     * @param collapseAll        collapses every container
     * @param expandFully        expands the selected row and all its descendants
     * @param collapseUnselected collapses everything except the selected row and its ancestors
     * @param toggleTableDisplay shows the selection's repeating group as table / as rows
     * @param tableDisplayState  {@code TRUE} when the selection's group is a table, {@code FALSE}
     *                           when shown as rows, {@code null} when it belongs to no group
     * @param flipTable          flips the rows and columns of the selection's table
     * @param flipState          the flip state of the selection's table, {@code null} when in none
     * @param editFilter         asks for the filter expression of the selected container
     * @param filterToFocus      filters the root so that only the selection and its subtree remain
     * @param filterSupported    whether the selection can carry a filter
     */
    record ViewActions(Runnable expandAll, Runnable collapseAll, Runnable expandFully, Runnable collapseUnselected,
                       Runnable toggleTableDisplay, java.util.function.Supplier<Boolean> tableDisplayState,
                       Runnable flipTable, java.util.function.Supplier<Boolean> flipState,
                       Runnable editFilter, Runnable filterToFocus, java.util.function.BooleanSupplier filterSupported,
                       Runnable expandOneLevel, Runnable collapseOneLevel) {
        /** @return actions that do nothing (for menus built without a canvas, e.g. in tests) */
        public static ViewActions none() {
            return new ViewActions(() -> { }, () -> { }, () -> { }, () -> { },
                    () -> { }, () -> null, () -> { }, () -> null, () -> { }, () -> { }, () -> false,
                    () -> { }, () -> { });
        }
    }

    /**
     * Lets the user rename a table column (every member's attribute/child of that name);
     * the canvas calls it on a double-click of a column header. Does nothing by default.
     *
     * @param table      the table
     * @param columnName the column
     */
    default void renameColumn(RepeatingElementsTable table, String columnName) {
    }

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
