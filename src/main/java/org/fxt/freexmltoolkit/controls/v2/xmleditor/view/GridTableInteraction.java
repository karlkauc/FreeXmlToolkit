package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;


import java.util.List;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;


/**
 * Mouse, keyboard and clipboard interaction of a {@link GridCanvasView} with its embedded
 * repeating-element tables: clicks resolved through the drawn grid (corner, column header,
 * row number, data cell), the cell-range selection, hover, sorting and TSV copy / paste.
 *
 * @param <N> the model node type
 */
final class GridTableInteraction<N> {

    /** The view whose tables this handles. */
    private final GridCanvasView<N> view;

    GridTableInteraction(GridCanvasView<N> view) {
        this.view = view;
    }

    // Table selection/hover state (kept for repeating table integration)
    RepeatingElementsTable selectedTable = null;
    /**
     * Cell range selected in {@link #selectedTable} (logical rows/columns, inclusive; anchor =
     * where the selection started, focus = where Shift+click extended it). -1 = none.
     */
    int selAnchorRow = -1;
    int selAnchorCol = -1;
    int selFocusRow = -1;
    int selFocusCol = -1;
    RepeatingElementsTable hoveredTable = null;
    int hoveredTableRowIndex = -1;
    int hoveredTableColumnIndex = -1;

    // ==================== Table Interaction ====================

    /**
     * Handles a mouse click inside an inline repeating table, resolved through the table's
     * drawn grid (corner, column header, row number or data cell).
     */
    void handleTableClick(MouseEvent event, RepeatingElementsTable table,
                                   double mx, double my, double tableScreenTop, FlatRow tableOwner) {
        int dr = table.drawnRowAt(my - tableScreenTop);
        if (dr == 0) {
            // A resize handle in the column-header row (the last column's lies on the table's
            // right edge, outside every drawn column): double-click returns to automatic width
            int sepIdx = table.getColumnSeparatorAt(mx, GridDragSupport.RESIZE_HANDLE);
            if (sepIdx >= 0) {
                if (event.getClickCount() == 2) {
                    table.setColumnUserWidth(table.getColumn(sepIdx).getName(), null);
                    view.recalculateVisibleRows();
                    view.updateScrollBars();
                    view.render();
                }
                return;
            }
        }
        int dc = table.drawnColumnAt(mx);
        if (dr < 0 || dc < 0) {
            return;
        }
        RepeatingElementsTable.DrawnCell cell = table.cellAt(dr, dc);

        switch (cell.kind()) {
            case CORNER -> {
                // The flip toggle (rows ↔ columns)
                if (event.getClickCount() == 1) {
                    view.flipTable(tableOwner);
                }
                return;
            }
            case COLUMN_HEADER -> {
                // Column header: a click on the arrow at the right sorts, elsewhere it selects the
                // whole column (Shift extends); a double-click renames the column
                RepeatingElementsTable.TableColumn col = table.getColumn(cell.columnIndex());
                if (col == null) {
                    return;
                }
                if (event.getClickCount() == 2) {
                    view.contextMenu().renameColumn(table, col.getName());
                    return;
                }
                if (event.getClickCount() != 1) {
                    return;
                }
                double headerRight = table.drawnColumnX(dc) + table.drawnColumnWidth(dc);
                if (mx >= headerRight - RepeatingElementsTable.SORT_ZONE) {
                    if (table.isColumnSortable(col.getName())) {
                        handleTableSort(table, col.getName(), tableOwner);
                    }
                    return;
                }
                selectTableColumn(table, cell.columnIndex(), event.isShiftDown());
                view.render();
                return;
            }
            case ROW_NUMBER -> {
                boolean extend = event.isShiftDown() && selectedTable == table && selAnchorRow >= 0;
                selectTableRow(table, cell.rowIndex());
                int lastCol = Math.max(0, table.getColumns().size() - 1);
                if (extend) {
                    selFocusRow = cell.rowIndex();
                    selFocusCol = lastCol;
                    selAnchorCol = 0;
                } else {
                    setCellRange(cell.rowIndex(), 0, cell.rowIndex(), lastCol);
                }
                view.render();
                return;
            }
            case DATA -> { /* handled below */ }
            default -> { }
        }

        int rowIdx = cell.rowIndex();
        if (rowIdx < 0 || rowIdx >= table.getRows().size()) {
            return;
        }
        RepeatingElementsTable.TableRow row = table.getRows().get(rowIdx);
        RepeatingElementsTable.TableColumn col = table.getColumn(cell.columnIndex());
        if (col == null) {
            return;
        }
        String colName = col.getName();
        RepeatingElementsTable.CellRect rect = table.cellRect(rowIdx, cell.columnIndex());
        double cellLeft = rect.x();

        // Click on a complex cell's expand arrow (first ~16px of the cell's summary line; below it
        // lies the opened cell's grid, whose own expand arrows sit in the same x range)
        RepeatingElementsTable.CellLayout summaryLayout = table.getCellLayout(row, colName);
        double summaryBottom = tableScreenTop + rect.y()
                + (summaryLayout != null ? summaryLayout.summaryHeight() : GridCanvasView.ROW_HEIGHT);
        if (event.getClickCount() == 1 && row.hasComplexChild(colName)
                && mx >= cellLeft && mx <= cellLeft + 16 && my < summaryBottom) {
            row.toggleColumnExpanded(colName);
            table.recalculateColumnWidths();
            view.recalculateVisibleRows();
            view.updateScrollBars();
            view.render();
            return;
        }

        // A click inside an opened cell goes to its grid (rows, nested tables, their cells …)
        RepeatingElementsTable.CellLayout cellLayout = table.getCellLayout(row, colName);
        if (cellLayout != null && cellLayout.grid() != null) {
            double gridTop = tableScreenTop + rect.y() + cellLayout.summaryHeight();
            double gridLeft = cellLeft + RepeatingElementsTable.CELL_PADDING;
            if (my >= gridTop && view.handleBlockClick(event, cellLayout.grid(), mx - gridLeft, my - gridTop)) {
                return;
            }
        }

        boolean extend = event.isShiftDown() && selectedTable == table && selAnchorRow >= 0;
        selectTableRow(table, rowIdx);
        if (extend) {
            selFocusRow = rowIdx;
            selFocusCol = cell.columnIndex();
        } else {
            setCellRange(rowIdx, cell.columnIndex(), rowIdx, cell.columnIndex());
        }

        // Double-click to edit cell
        if (event.getClickCount() == 2) {
            view.editor.startEditingTableCell(table, rowIdx, colName, tableScreenTop);
        }

        view.render();
    }

    /**
     * Selects a whole column of the table (all rows); with {@code extend} and a column selection
     * of the same table already present, the range grows to this column.
     */
    void selectTableColumn(RepeatingElementsTable table, int columnIndex, boolean extend) {
        int lastRow = table.getRows().size() - 1;
        if (lastRow < 0) {
            return;
        }
        boolean hadColumns = extend && selectedTable == table && selAnchorRow == 0 && selFocusRow == lastRow;
        int anchorCol = hadColumns ? selAnchorCol : columnIndex;
        selectTableRow(table, 0);
        setCellRange(0, anchorCol, lastRow, columnIndex);
    }

    /** @return whether every row of the column lies in the selected range */
    boolean isWholeColumnSelected(RepeatingElementsTable table, int col) {
        int rows = table.getRows().size();
        return rows > 0 && table == selectedTable && selAnchorRow >= 0
                && Math.min(selAnchorRow, selFocusRow) == 0 && Math.max(selAnchorRow, selFocusRow) == rows - 1
                && col >= Math.min(selAnchorCol, selFocusCol) && col <= Math.max(selAnchorCol, selFocusCol);
    }

    /** @return whether every column of the row lies in the selected range */
    boolean isWholeRowSelected(RepeatingElementsTable table, int row) {
        int cols = table.getColumns().size();
        return cols > 0 && table == selectedTable && selAnchorRow >= 0
                && Math.min(selAnchorCol, selFocusCol) == 0 && Math.max(selAnchorCol, selFocusCol) == cols - 1
                && row >= Math.min(selAnchorRow, selFocusRow) && row <= Math.max(selAnchorRow, selFocusRow);
    }

    void setCellRange(int anchorRow, int anchorCol, int focusRow, int focusCol) {
        selAnchorRow = anchorRow;
        selAnchorCol = anchorCol;
        selFocusRow = focusRow;
        selFocusCol = focusCol;
    }

    /** @return whether the logical cell lies in the selected cell range of the table */
    boolean inCellRange(RepeatingElementsTable table, int row, int col) {
        if (table != selectedTable || selAnchorRow < 0) {
            return false;
        }
        return row >= Math.min(selAnchorRow, selFocusRow) && row <= Math.max(selAnchorRow, selFocusRow)
                && col >= Math.min(selAnchorCol, selFocusCol) && col <= Math.max(selAnchorCol, selFocusCol);
    }

    // ==================== TSV clipboard ====================

    /**
     * @return the selected table cells as tab-separated text (the whole table with a header
     *         line when the selection covers every cell), or {@code null} without a cell selection
     */
    String selectedCellsAsTsv() {
        javafx.scene.input.ClipboardContent content = selectedCellsContent();
        return content == null ? null : content.getString();
    }

    /** @return the selected table cells as clipboard content (TSV + HTML), or {@code null} without a cell selection */
    private javafx.scene.input.ClipboardContent selectedCellsContent() {
        if (selectedTable == null || selAnchorRow < 0) {
            return null;
        }
        int rows = selectedTable.getRows().size();
        int cols = selectedTable.getColumns().size();
        boolean all = Math.min(selAnchorRow, selFocusRow) == 0 && Math.max(selAnchorRow, selFocusRow) == rows - 1
                && Math.min(selAnchorCol, selFocusCol) == 0 && Math.max(selAnchorCol, selFocusCol) == cols - 1;
        return TsvClipboard.content(selectedTable, selAnchorRow, selAnchorCol, selFocusRow, selFocusCol, all && rows > 1);
    }

    /** Selects every cell of the table (the corner-cell / Ctrl+A target). */
    private void selectAllCells(RepeatingElementsTable table) {
        if (table.getRows().isEmpty()) {
            return;
        }
        selectTableRow(table, 0);
        setCellRange(0, 0, table.getRows().size() - 1, Math.max(0, table.getColumns().size() - 1));
        view.render();
    }

    /**
     * Pastes a tab-separated block into the selected table, starting at the top-left selected
     * cell (rows beyond the table become new members where the model allows it).
     *
     * @param text the clipboard text
     * @return whether anything was pasted
     */
    boolean pasteTsvIntoSelection(String text) {
        if (selectedTable == null || selAnchorRow < 0 || text == null) {
            return false;
        }
        List<List<String>> values = TsvClipboard.parse(text);
        if (values.isEmpty()) {
            return false;
        }
        int startRow = Math.min(selAnchorRow, selFocusRow);
        int startCol = Math.min(selAnchorCol, selFocusCol);
        if (!view.adapter.pasteCells(selectedTable, startRow, startCol, values)) {
            return false;
        }
        view.rebuildTree();
        view.notifyDocumentModified();
        return true;
    }

    /**
     * Ctrl+C / Ctrl+V / Ctrl+A while table cells are selected: copy them as TSV, paste a TSV
     * block, select the whole table. Handled before the context-menu shortcuts (which would
     * copy/paste the row's element node instead).
     *
     * @return whether the event was handled
     */
    boolean handleTableClipboardKeys(KeyEvent event) {
        if (!event.isShortcutDown() || event.isShiftDown() || event.isAltDown()
                || selectedTable == null || selAnchorRow < 0) {
            return false;
        }
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        switch (event.getCode()) {
            case C -> {
                javafx.scene.input.ClipboardContent content = selectedCellsContent();
                if (content != null) {
                    clipboard.setContent(content);
                    event.consume();
                    return true;
                }
                return false;
            }
            case V -> {
                String text = clipboard.hasString() ? clipboard.getString() : null;
                boolean multiCell = selAnchorRow != selFocusRow || selAnchorCol != selFocusCol;
                if (text != null && (TsvClipboard.isBlock(text) || multiCell || !view.contextMenu().hasClipboard())) {
                    pasteTsvIntoSelection(text);
                    event.consume();
                    return true;
                }
                return false;
            }
            case A -> {
                selectAllCells(selectedTable);
                event.consume();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Selects a table row (clearing the tree-row selection) and tells the selection model. */
    void selectTableRow(RepeatingElementsTable table, int rowIdx) {
        if (rowIdx < 0 || rowIdx >= table.getRows().size()) {
            return;
        }
        if (selectedTable != null && selectedTable != table) {
            selectedTable.setSelectedRowIndex(-1);
        }
        table.setSelectedRowIndex(rowIdx);
        selectedTable = table;

        // Clear normal row selection (visual only, to avoid dual highlight)
        if (view.selectedRow != null) {
            view.selectedRow.setSelected(false);
            view.selectedRow = null;
        }

        // Notify SelectionModel so sidebar properties pane updates
        view.adapter.select(view.adapter.nodeOf(table.getRows().get(rowIdx)));
        view.fireSelectionChanged();
    }

    /**
     * Handles sorting of a table column.
     * Toggles sort direction if clicking the same column again.
     */
    private void handleTableSort(RepeatingElementsTable table, String columnName, FlatRow tableOwner) {
        // Toggle direction if already sorted by this column
        boolean ascending;
        if (table.isSortedBy(columnName)) {
            ascending = !table.isSortAscending();
        } else {
            ascending = true;
        }

        view.adapter.sortTable(table, columnName, ascending);

        view.rebuildTree();
        view.notifyDocumentModified();
    }

    /**
     * Updates table hover state.
     */
    void updateTableHover(RepeatingElementsTable table, double mx, double my, double tableScreenTop) {
        if (hoveredTable != table) {
            if (hoveredTable != null) {
                hoveredTable.setHoveredRowIndex(-1);
                hoveredTable.setHoveredColumnIndex(-1);
                hoveredTable.setHovered(false);
            }
            hoveredTable = table;
            table.setHovered(true);
            hoveredTableRowIndex = -1;
            hoveredTableColumnIndex = -1;
        }

        int dr = table.drawnRowAt(my - tableScreenTop);
        int dc = table.drawnColumnAt(mx);
        int rowIdx = -1;
        int colIdx = -1;
        if (dr >= 0 && dc >= 0) {
            RepeatingElementsTable.DrawnCell cell = table.cellAt(dr, dc);
            rowIdx = cell.rowIndex();
            colIdx = cell.columnIndex();
        }

        if (rowIdx != hoveredTableRowIndex || colIdx != hoveredTableColumnIndex) {
            hoveredTableRowIndex = rowIdx;
            hoveredTableColumnIndex = colIdx;
            table.setHoveredRowIndex(rowIdx);
            table.setHoveredColumnIndex(colIdx);
            view.render();
        }
    }
}
