package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.TypeAwareWidgetFactory;

/**
 * Everything the model-agnostic {@link GridCanvasView} needs from a document model.
 *
 * <p>The canvas owns rendering, scrolling, hit-testing, keyboard navigation, inline
 * editing chrome and search; the adapter turns the document into {@link FlatRow}s,
 * attaches embedded tables, maps rows back to model nodes, drives the selection model,
 * and translates edits into commands. Implementations: {@link XmlGridAdapter} for the
 * XML instance grid and the JSON grid's adapter.</p>
 *
 * @param <N> the model node type (XmlNode / JsonNode)
 */
public interface GridModelAdapter<N> {

    /**
     * What the canvas installs for an inline edit: the initial text, an optional
     * type-aware widget (e.g. a date picker or boolean toggle; {@code null} for a plain
     * text field) and an optional documentation tooltip for the text field.
     */
    record EditSpec(String currentValue, TypeAwareWidgetFactory.EditWidget widget, String tooltip) {
        /** @return a plain text-field spec without widget or tooltip */
        public static EditSpec plain(String currentValue) {
            return new EditSpec(currentValue, null, null);
        }
    }

    /** @return whether a document is loaded (otherwise the empty state is drawn) */
    boolean hasDocument();

    /** @return the document flattened into rows, in document order */
    List<FlatRow> flatten();

    /**
     * Flattens the document, showing each repeating group either as one table-owner row or
     * as individual numbered rows, as {@code groupAsTable} decides per group key.
     *
     * @param groupAsTable whether the group with the given key is shown as a table
     * @return the rows, in document order
     */
    default List<FlatRow> flatten(java.util.function.Predicate<String> groupAsTable) {
        return flatten();
    }

    /**
     * Attaches {@link RepeatingElementsTable}s to the rows that represent repeating groups.
     *
     * @param rows            the rows produced by {@link #flatten()}
     * @param onLayoutChanged callback the tables invoke when their layout changes
     */
    void attachTables(List<FlatRow> rows, Runnable onLayoutChanged);

    /**
     * Registers a listener invoked whenever the document is replaced or changed outside
     * the canvas (e.g. shell undo), so the canvas rebuilds its rows.
     */
    void addModelListener(Runnable onModelChanged);

    /**
     * Hook for model-specific one-off view behaviour (e.g. the XML mixed-content warning,
     * the JSON lossy-format toast). Called once from the canvas constructor.
     */
    default void installViewHooks(GridCanvasView<N> view) {
    }

    /**
     * One-line preview of a collapsed container row (XMLSpy's inline preview): a compact
     * serialization of the node, cut off after about {@code maxChars} characters. The canvas
     * caches the result on the row, so an implementation may serialize eagerly but must stop
     * early at the limit (the root of a large document is collapsed on every load).
     *
     * @param row      a container row
     * @param maxChars the soft length limit
     * @return the preview text, or {@code null} for none
     */
    default String collapsedPreview(FlatRow row, int maxChars) {
        return null;
    }

    /**
     * A key that identifies the row's node across rebuilds (the positional XPath of an XML
     * node, the path of a JSON node): the canvas keeps expand state, selection and column
     * widths by it. The default falls back to type, depth and label, which collides for
     * same-named siblings.
     *
     * @param row a row
     * @return the stable key, never {@code null}
     */
    default String stableKey(FlatRow row) {
        return row.getType() + ":" + row.getDepth() + ":" + row.getLabel();
    }

    /** @return the model node behind a row */
    N nodeOf(FlatRow row);

    /** @return the model node behind an embedded-table row */
    N nodeOf(RepeatingElementsTable.TableRow row);

    /** Makes the node the current selection of the model's selection model. */
    void select(N node);

    /** @return the text drawn for a leaf-with-value row's value (e.g. quoted) */
    String decorateLeafValue(FlatRow row);

    /** @return whether the row's name/key can be edited inline */
    boolean canEditName(FlatRow row);

    /** @return whether the row's value can be edited inline */
    boolean canEditValue(FlatRow row);

    /**
     * Describes the inline editor for a row.
     *
     * @param row      the row being edited
     * @param nameEdit {@code true} for the name/key, {@code false} for the value
     */
    EditSpec editSpec(FlatRow row, boolean nameEdit);

    /** Describes the inline editor for an embedded-table cell. */
    EditSpec cellEditSpec(RepeatingElementsTable table, int rowIndex, String columnName);

    /**
     * Applies an inline row edit through the model's command stack.
     *
     * @return {@code true} when the edit was accepted (or ignored); {@code false} to keep
     * the editor open because the value was rejected (the adapter should have told the user)
     */
    boolean commitRowEdit(FlatRow row, boolean nameEdit, String newValue);

    /** Applies an embedded-table cell edit; same contract as {@link #commitRowEdit}. */
    boolean commitCellEdit(RepeatingElementsTable table, int rowIndex, String columnName, String newValue);

    /**
     * Writes a pasted block of cell values into the table starting at the given cell: cell by
     * cell through {@link #commitCellEdit} by default (one undo step per cell, values beyond
     * the table ignored); models with a composite command override this.
     *
     * @param table    the table
     * @param startRow the target cell's row
     * @param startCol the target cell's column
     * @param values   the pasted values, row by row
     * @return whether anything was written
     */
    default boolean pasteCells(RepeatingElementsTable table, int startRow, int startCol,
                               java.util.List<java.util.List<String>> values) {
        boolean changed = false;
        for (int i = 0; i < values.size(); i++) {
            int r = startRow + i;
            if (r >= table.getRows().size()) {
                break;
            }
            java.util.List<String> row = values.get(i);
            for (int j = 0; j < row.size(); j++) {
                int c = startCol + j;
                if (c >= table.getColumns().size()) {
                    break;
                }
                changed |= commitCellEdit(table, r, table.getColumn(c).getName(), row.get(j));
            }
        }
        return changed;
    }

    /** Sorts the repeating group behind the table by a column, through the command stack. */
    void sortTable(RepeatingElementsTable table, String columnName, boolean ascending);

    /** @return the document serialized to text (for the round-trip into the text editor) */
    String serialize();

    /**
     * @param refresh rebuilds the canvas after an edit
     * @param actions the view's expand/collapse actions the menu offers
     * @return the context menu for this model
     */
    GridContextMenu<N> createContextMenu(Runnable refresh, GridContextMenu.ViewActions actions);

    /** @return the text drawn when no document is loaded */
    String emptyStateText();
}
