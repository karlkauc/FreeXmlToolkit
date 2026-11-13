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
     * One-line preview of a collapsed container row (inline preview): a compact
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

    /**
     * @param row a row
     * @return the labels of the node types the row can be converted to (click the
     *         type icon), in menu order; empty when the row's type cannot change
     */
    default List<String> typeTargets(FlatRow row) {
        return List.of();
    }

    /**
     * Converts the row's node to another type through the command stack.
     *
     * @param row         the row
     * @param targetLabel one of {@link #typeTargets(FlatRow)}
     * @return whether the model changed
     */
    default boolean changeType(FlatRow row, String targetLabel) {
        return false;
    }

    /**
     * @param row a row
     * @return the source line of the row's node (1-based; the start tag's line for an element,
     *         its owner's for an attribute), 0 when unknown
     */
    default int sourceLine(FlatRow row) {
        return 0;
    }

    /**
     * @param row a table-owner row
     * @return the source line of every member of its repeating group (0 = unknown), in row order
     */
    default int[] memberSourceLines(FlatRow row) {
        return new int[0];
    }

    /**
     * A formula row as the grid shows it (the Formula node type).
     *
     * @param name       the formula's name
     * @param expression the expression
     * @param result     the live result ({@code null} on error)
     * @param error      the error message ({@code null} on success)
     * @param stored     whether the result is also written into the document
     */
    record FormulaView(String name, String expression, String result, String error, boolean stored) {
    }

    /** @return whether the row shows a formula (cheap: no evaluation) */
    default boolean isFormulaRow(FlatRow row) {
        return false;
    }

    /**
     * @param row a row
     * @return the evaluated formula when the row is a formula, else {@code null}; the canvas
     *         caches it on the row (rows are rebuilt after every edit)
     */
    default FormulaView formula(FlatRow row) {
        return null;
    }

    /** @return whether formulas can be added (XML: {@code <?fxt-formula …?>} instructions) */
    default boolean supportsFormulas() {
        return false;
    }

    /**
     * @return whether the model supports filters (a funnel on an element whose
     *         expression decides which nodes below it stay visible)
     */
    default boolean supportsFilters() {
        return false;
    }

    /**
     * @param row        the filtered container row
     * @param expression the filter expression (XQuery for XML)
     * @return a predicate over model nodes below the row (the matches, their ancestors up to the
     *         row and their descendants pass), or {@code null} when the model has no filters
     * @throws IllegalArgumentException when the expression fails
     */
    default java.util.function.Predicate<Object> filterPredicate(FlatRow row, String expression) {
        return null;
    }

    /**
     * @return whether the filter is still being evaluated in the background (its predicate is
     *         the previous result, or none); the filter line says so
     */
    default boolean isFilterPending(FlatRow row, String expression) {
        return false;
    }

    /**
     * @return the filters saved in the document, by the key of their container row (see
     *         {@link #stableKey}); empty when the model cannot store filters
     */
    default java.util.Map<String, String> savedFilters() {
        return java.util.Map.of();
    }

    /** @return whether filters can be saved in the document */
    default boolean supportsSavedFilters() {
        return false;
    }

    /** @return whether the container row's filter is saved in the document */
    default boolean isSavedFilter(FlatRow row) {
        return false;
    }

    /**
     * Saves (or updates) the filter of a container row in the document, as one undoable edit.
     *
     * @return whether the document changed
     */
    default boolean saveFilter(FlatRow row, String expression) {
        return false;
    }

    /** Removes the saved filter of a container row from the document; @return whether it changed */
    default boolean removeSavedFilter(FlatRow row) {
        return false;
    }

    /** Removes every saved filter from the document in one undoable edit; @return whether it changed */
    default boolean removeAllSavedFilters() {
        return false;
    }

    /**
     * @param root   the root row
     * @param target the model node to focus (a row's node or a member of an embedded table)
     * @return the filter expression that, set on {@code root}, shows only {@code target} and its
     *         subtree ("Filter to Focus"), or {@code null} when not supported
     */
    default String focusFilterExpression(FlatRow root, Object target) {
        return null;
    }

    /**
     * Suggests the filter expression that keeps the members whose value equals the one the user
     * started "Filter…" on (e.g. {@code Position[Country='AT']}); evaluated on the member's parent.
     *
     * @param member    the repeating member (a table row's node, or the parent of a leaf row)
     * @param columnKey the value's key below the member: an element name, {@code @attribute} or
     *                  {@code #text} for the member's own text
     * @param value     the value as shown
     * @return the expression, or {@code null} when the model cannot suggest one
     */
    default String valueFilterExpression(Object member, String columnKey, String value) {
        return null;
    }

    /** @return the text put on the clipboard of a drag (the node serialized), or {@code null} */
    default String dragText(FlatRow row) {
        return null;
    }

    /** @return whether the row's node can be dragged to another place in the grid */
    default boolean canDrag(FlatRow row) {
        return false;
    }

    /**
     * @param dragged  the dragged row ({@code null} for external text)
     * @param target   the row under the pointer
     * @param position where the payload would go
     * @return whether a drop there is allowed (never into the dragged node itself or its descendants)
     */
    default boolean canDrop(FlatRow dragged, FlatRow target, DropPosition position) {
        return false;
    }

    /**
     * Moves (or copies) the dragged row's node next to / into the target, through the
     * command stack.
     *
     * @return whether the model changed
     */
    default boolean dropRow(FlatRow dragged, FlatRow target, DropPosition position, boolean copy) {
        return false;
    }

    /**
     * Drops external text (from another application): on a value cell it replaces the value,
     * elsewhere an XML fragment becomes new nodes, plain text a text node.
     *
     * @return whether the model changed
     */
    default boolean dropText(FlatRow target, DropPosition position, String text) {
        return false;
    }

    /** @return the model node behind a row */
    N nodeOf(FlatRow row);

    /** @return the model node behind an embedded-table row */
    N nodeOf(RepeatingElementsTable.TableRow row);

    /** @return whether {@code node} is {@code ancestor} or lies inside it (problem reveal) */
    default boolean contains(N ancestor, N node) {
        return ancestor == node;
    }

    /**
     * @return whether {@code node} lies in the column {@code column} of the table member
     *         {@code member} when that column has no single cell node (several same-name children)
     */
    default boolean containsInColumn(N member, String column, N node) {
        return false;
    }

    /**
     * @return the node a table cell stands for — the target of the context menu's node actions on
     *         that cell — or {@code null} when the cell is no node of its own (the row's node then)
     */
    default N cellNode(RepeatingElementsTable.TableRow row, String column) {
        return null;
    }

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

    /**
     * Moves a table column before/after another column in every member of the group
     * (drag a column header onto another one).
     *
     * @return whether the model changed
     */
    default boolean moveTableColumn(RepeatingElementsTable table, int from, int to, boolean before) {
        return false;
    }

    /** Sorts the repeating group behind the table by a column, through the command stack. */
    void sortTable(RepeatingElementsTable table, String columnName, boolean ascending);

    /** What applying a sort that was computed off the FX thread did. */
    enum SortOutcome {
        /** The model changed meanwhile: nothing was applied (the caller may compute again). */
        STALE,
        /** The model was sorted and the table's rows were put into the new order in place. */
        REORDERED,
        /** The model was sorted; the grid must rebuild its rows. */
        CHANGED
    }

    /**
     * Prepares a sort in three steps: this call (FX thread) takes what the computation needs, the
     * returned job runs on a worker thread, and the step it returns is applied on the FX thread.
     * By default everything happens in the last step ({@link #sortTable}).
     */
    default java.util.concurrent.Callable<java.util.function.Supplier<SortOutcome>> prepareSort(
            RepeatingElementsTable table, String columnName, boolean ascending) {
        return () -> () -> {
            sortTable(table, columnName, ascending);
            return SortOutcome.CHANGED;
        };
    }

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
