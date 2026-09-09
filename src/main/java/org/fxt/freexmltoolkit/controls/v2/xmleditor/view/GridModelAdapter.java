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

    /** Sorts the repeating group behind the table by a column, through the command stack. */
    void sortTable(RepeatingElementsTable table, String columnName, boolean ascending);

    /** @return the document serialized to text (for the round-trip into the text editor) */
    String serialize();

    /** @return the model-specific context menu for the canvas */
    GridContextMenu<N> createContextMenu(Runnable refresh);

    /** @return the text drawn when no document is loaded */
    String emptyStateText();
}
