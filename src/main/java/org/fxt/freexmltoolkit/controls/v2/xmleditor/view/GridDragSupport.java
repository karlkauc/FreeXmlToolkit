package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;



import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;


/**
 * Drag gestures of a {@link GridCanvasView}: dragging nodes by their name cell (and dropping
 * nodes or external text), moving table columns by their header, and resizing table columns
 * and block name columns by their separators.
 *
 * @param <N> the model node type
 */
final class GridDragSupport<N> {

    /** The view whose drags this handles. */
    private final GridCanvasView<N> view;

    GridDragSupport(GridCanvasView<N> view) {
        this.view = view;
    }

    /**
     * Installs the canvas mouse handlers of the separator drags (table columns, block name
     * columns) and of column-header moves, and remembers the node-drag candidate of a press.
     */
    void installMouseHandlers() {
    view.canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
        if (e.getButton() != MouseButton.PRIMARY || view.editor.editField != null || view.editor.activeWidgetNode != null) {
            return;
        }
        double mx = view.toModelX(e.getX());
        double my = view.toViewportY(e.getY());
        // Anchor on the SCREEN x: narrowing a column can shrink the content width and
        // pull the horizontal scroll offset back, which must not feed into the delta.
        ColumnDrag handle = findColumnHandleAt(mx, my);
        if (handle != null) {
            columnDrag = new ColumnDrag(handle.table(), handle.columnIndex(), e.getX(), handle.startWidth());
            columnDragMoved = false;
            e.consume();
            return;
        }
        BlockDrag block = findBlockSeparatorAt(mx, my);
        if (block != null) {
            columnDrag = new BlockDrag(block.key(), block.grid(), e.getX(), block.startWidth());
            columnDragMoved = false;
            e.consume();
            return;
        }
        pressedDragRow = dragCandidateAt(mx, my);
        headerDrag = headerDragAt(mx, my, e.getX());
    });
    view.canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
        if (headerDrag == null) {
            return;
        }
        if (!headerDrag.active && Math.abs(e.getX() - headerDrag.startScreenX) > 6) {
            headerDrag.active = true;
        }
        if (headerDrag.active) {
            double mx = view.toModelX(e.getX()) - headerDrag.originX;
            int dc = headerDrag.table.drawnColumnAt(mx);
            RepeatingElementsTable.DrawnCell cell = dc >= 0 ? headerDrag.table.cellAt(0, dc) : null;
            if (cell != null && cell.columnIndex() >= 0) {
                headerDrag.target = cell.columnIndex();
                headerDrag.before = mx < headerDrag.table.drawnColumnX(dc) + headerDrag.table.drawnColumnWidth(dc) / 2;
            } else {
                headerDrag.target = -1;
            }
            view.render();
            e.consume();
        }
    });
    view.canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
        HeaderDrag drag = headerDrag;
        headerDrag = null;
        if (drag == null || !drag.active) {
            return;
        }
        suppressNextClick = true; // the click after the drag must not sort
        if (drag.target >= 0 && drag.target != drag.column
                && view.adapter.moveTableColumn(drag.table, drag.column, drag.target, drag.before)) {
            view.rebuildTree();
            view.notifyDocumentModified();
        } else {
            view.render();
        }
        e.consume();
    });
    view.canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
        if (columnDrag == null) {
            return;
        }
        double delta = (e.getX() - columnDrag.startScreenX()) / view.getZoom();
        if (columnDrag instanceof ColumnDrag drag) {
            double newWidth = Math.max(RepeatingElementsTable.MIN_COLUMN_WIDTH, drag.startWidth() + delta);
            RepeatingElementsTable.TableColumn col = drag.table().getColumn(drag.columnIndex());
            if (col != null && Math.abs(col.getWidth() - newWidth) >= 0.5) {
                drag.table().setColumnUserWidth(col.getName(), newWidth);
                columnDragMoved = true;
                view.recalculateVisibleRows();
                view.updateScrollBars();
                view.render();
            }
        } else if (columnDrag instanceof BlockDrag drag) {
            double newWidth = Math.max(GridCanvasView.MIN_NAME_COL_WIDTH, drag.startWidth() + delta);
            Double current = view.viewState.nameColumnWidth(drag.key());
            if (current == null || Math.abs(current - newWidth) >= 0.5) {
                view.viewState.setNameColumnWidth(drag.key(), newWidth);
                if (drag.grid() != null) {
                    drag.grid().invalidate();
                }
                columnDragMoved = true;
                view.recalculateVisibleRows();
                view.updateScrollBars();
                view.render();
            }
        }
        view.canvas.setCursor(Cursor.H_RESIZE);
        e.consume();
    });
    view.canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
        if (columnDrag != null) {
            suppressNextClick = columnDragMoved;
            columnDrag = null;
            e.consume();
        }
    });
    }

    // Column resizing (drag a separator in an embedded table's column-header row)
    static final double RESIZE_HANDLE = 4;

    /** An in-progress separator drag: a table column or a block's name column. */
    private sealed interface Drag permits ColumnDrag, BlockDrag {
        double startScreenX();
    }

    /** A column drag: which column of which table, and where it started. */
    record ColumnDrag(RepeatingElementsTable table, int columnIndex,
                              double startScreenX, double startWidth) implements Drag {
    }

    /**
     * A name-column drag of one block (its width key, the opened cell's grid holding it or
     * {@code null} for the root grid), and where it started.
     */
    record BlockDrag(String key, GridBlock grid, double startScreenX, double startWidth) implements Drag {
    }

    Drag columnDrag = null;

    // Drag & drop of nodes (and external text)
    /** The row whose name cell was pressed (a drag candidate), or {@code null}. */
    FlatRow pressedDragRow = null;
    /** The row being dragged inside the grid, or {@code null}. */
    private FlatRow dragRow = null;
    /** The current drop target row and position (drawn as an insertion line / box). */
    FlatRow dropTargetRow = null;
    DropPosition dropPosition = null;

    /** A column-header drag that moves a table column (XMLSpy: drag a header onto another). */
    static final class HeaderDrag {
        final RepeatingElementsTable table;
        final int column;
        final double startScreenX;
        /** x offset of the coordinates the table lives in (0 for a root table) */
        final double originX;
        boolean active;
        int target = -1;
        boolean before;

        HeaderDrag(RepeatingElementsTable table, int column, double startScreenX, double originX) {
            this.table = table;
            this.column = column;
            this.startScreenX = startScreenX;
            this.originX = originX;
        }
    }

    HeaderDrag headerDrag = null;

    boolean columnDragMoved = false;
    /** Set after a drag so the MOUSE_CLICKED JavaFX fires afterwards does not sort/select. */
    boolean suppressNextClick = false;

    // ==================== Drag & drop ====================

    /** Internal drag payload: marks a drag that started in a grid canvas. */
    private static final javafx.scene.input.DataFormat GRID_NODE_FORMAT = gridNodeFormat();

    private static javafx.scene.input.DataFormat gridNodeFormat() {
        javafx.scene.input.DataFormat existing = javafx.scene.input.DataFormat.lookupMimeType("application/x-fxt-grid-node");
        return existing != null ? existing : new javafx.scene.input.DataFormat("application/x-fxt-grid-node");
    }

    /** @return a column-header drag candidate when the press is on a column header (not a resize handle) */
    HeaderDrag headerDragAt(double mx, double my, double screenX) {
        GridCanvasView.Pointer p = view.pointerAt(mx, my); // tables inside opened cells too
        RepeatingElementsTable table = p == null ? null : p.table();
        if (table == null || table.isFlipped() || table.getColumnSeparatorAt(p.lx(), RESIZE_HANDLE) >= 0) {
            return null;
        }
        int dr = table.drawnRowAt(p.ly() - p.tableTop());
        int dc = table.drawnColumnAt(p.lx());
        if (dr != 0 || dc < 1) {
            return null;
        }
        return new HeaderDrag(table, table.cellAt(0, dc).columnIndex(), screenX, p.ox());
    }

    /** @return the row whose name cell (first line, right of the expand arrow) is under the point, if draggable */
    FlatRow dragCandidateAt(double mx, double my) {
        GridCanvasView.Pointer p = view.pointerAt(mx, my); // rows inside opened cells too
        if (p == null || p.table() != null) {
            return null;
        }
        FlatRow row = p.row();
        boolean inNameCell = p.lx() >= view.rowIconX(row) && p.lx() < row.getValueX() - RESIZE_HANDLE
                && p.ly() >= p.rowTop() && p.ly() < p.rowTop() + GridCanvasView.ROW_HEIGHT;
        return inNameCell && view.adapter.canDrag(row) ? row : null;
    }

    /**
     * Where a drop at the point would go: in a name cell's first line the upper third means
     * "before", the lower third "after", the middle (and the rest of a spanning container
     * cell) "into"; a value cell means "replace the value".
     *
     * @param mx x in model units
     * @param my y in unscaled viewport units
     * @return the drop target, or {@code null} when there is no row
     */
    GridCanvasView.DropTarget dropTargetAt(double mx, double my) {
        GridCanvasView.Pointer p = view.pointerAt(mx, my); // rows inside opened cells too
        RowBox box = p == null ? null : rowBox(p.row());
        if (box == null) {
            return null;
        }
        FlatRow row = p.row();
        double rel = p.ly() - p.rowTop();
        boolean ownLine = rel < Math.max(box.own(), GridCanvasView.ROW_HEIGHT);
        if (p.lx() >= row.getValueX() && ownLine && !(row.hasRepeatingTable() && row.isExpanded())) {
            return new GridCanvasView.DropTarget(row, DropPosition.VALUE);
        }
        if (rel >= GridCanvasView.ROW_HEIGHT) {
            return new GridCanvasView.DropTarget(row, DropPosition.INTO);
        }
        if (rel < GridCanvasView.ROW_HEIGHT * 0.3) {
            return new GridCanvasView.DropTarget(row, DropPosition.BEFORE);
        }
        if (rel > GridCanvasView.ROW_HEIGHT * 0.7) {
            return new GridCanvasView.DropTarget(row, DropPosition.AFTER);
        }
        return new GridCanvasView.DropTarget(row, row.getType().isContainer() ? DropPosition.INTO : DropPosition.AFTER);
    }

    /**
     * Resolves the drop target for the current drag: internal drags never replace a value
     * (a value-cell position falls back to "into"/"after" by the vertical rule).
     */
    private GridCanvasView.DropTarget resolveDrop(double mx, double my, boolean internal) {
        GridCanvasView.DropTarget target = dropTargetAt(mx, my);
        if (target == null) {
            return null;
        }
        if (internal && target.position() == DropPosition.VALUE) {
            GridCanvasView.Pointer p = view.pointerAt(mx, my);
            double rel = p == null ? 0 : p.ly() - p.rowTop();
            DropPosition pos = rel < GridCanvasView.ROW_HEIGHT * 0.3 ? DropPosition.BEFORE
                    : rel > GridCanvasView.ROW_HEIGHT * 0.7 || !target.row().getType().isContainer() ? DropPosition.AFTER
                    : DropPosition.INTO;
            target = new GridCanvasView.DropTarget(target.row(), pos);
        }
        FlatRow dragged = internal ? dragRow : null;
        return view.adapter.canDrop(dragged, target.row(), target.position()) ? target : null;
    }

    /** Installs the drag-and-drop handlers (drag a node by its name cell; drop nodes or external text). */
    void setupDragAndDrop() {
        view.canvas.setOnDragDetected(e -> {
            if (columnDrag != null || pressedDragRow == null || view.editor.editField != null || view.editor.activeWidgetNode != null) {
                return;
            }
            String text = view.adapter.dragText(pressedDragRow);
            javafx.scene.input.Dragboard db = view.canvas.startDragAndDrop(javafx.scene.input.TransferMode.COPY_OR_MOVE);
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(text != null ? text : "");
            content.put(GRID_NODE_FORMAT, view.keyOf(pressedDragRow));
            db.setContent(content);
            dragRow = pressedDragRow;
            pressedDragRow = null;
            e.consume();
        });
        view.canvas.addEventHandler(javafx.scene.input.DragEvent.DRAG_OVER, e -> {
            boolean internal = e.getGestureSource() == view.canvas && dragRow != null;
            if (!internal && !e.getDragboard().hasString()) {
                return;
            }
            autoScrollWhileDragging(view.toViewportY(e.getY()));
            GridCanvasView.DropTarget target = resolveDrop(view.toModelX(e.getX()), view.toViewportY(e.getY()), internal);
            if (target != null) {
                e.acceptTransferModes(internal ? javafx.scene.input.TransferMode.COPY_OR_MOVE
                        : new javafx.scene.input.TransferMode[]{javafx.scene.input.TransferMode.COPY});
            }
            setDropIndicator(target);
            e.consume();
        });
        view.canvas.addEventHandler(javafx.scene.input.DragEvent.DRAG_EXITED, e -> setDropIndicator(null));
        view.canvas.addEventHandler(javafx.scene.input.DragEvent.DRAG_DROPPED, e -> {
            boolean internal = e.getGestureSource() == view.canvas && dragRow != null;
            GridCanvasView.DropTarget target = resolveDrop(view.toModelX(e.getX()), view.toViewportY(e.getY()), internal);
            boolean done = false;
            if (target != null) {
                if (internal) {
                    boolean copy = e.getTransferMode() == javafx.scene.input.TransferMode.COPY;
                    done = view.adapter.dropRow(dragRow, target.row(), target.position(), copy);
                } else if (e.getDragboard().hasString()) {
                    done = view.adapter.dropText(target.row(), target.position(), e.getDragboard().getString());
                }
            }
            setDropIndicator(null);
            e.setDropCompleted(done);
            e.consume();
            if (done) {
                javafx.application.Platform.runLater(() -> {
                    view.rebuildTree();
                    view.notifyDocumentModified();
                });
            }
        });
        view.canvas.setOnDragDone(e -> {
            dragRow = null;
            setDropIndicator(null);
        });
    }

    /** Band at the top and bottom edge where a drag scrolls the grid (unscaled pixels). */
    private static final double DRAG_SCROLL_BAND = 24;

    /** Scrolls a little while a drag is held near the top or bottom edge, so deep targets come into reach. */
    private void autoScrollWhileDragging(double my) {
        double height = view.viewportHeightModel();
        double step = 0;
        if (my < DRAG_SCROLL_BAND) {
            step = -(DRAG_SCROLL_BAND - Math.max(0, my));
        } else if (my > height - DRAG_SCROLL_BAND) {
            step = DRAG_SCROLL_BAND - Math.max(0, height - my);
        }
        if (step != 0) {
            view.vScrollBar.setValue(Math.max(view.vScrollBar.getMin(), Math.min(view.vScrollBar.getMax(), view.vScrollBar.getValue() + step)));
        }
    }

    private void setDropIndicator(GridCanvasView.DropTarget target) {
        FlatRow row = target != null ? target.row() : null;
        DropPosition pos = target != null ? target.position() : null;
        if (row != dropTargetRow || pos != dropPosition) {
            dropTargetRow = row;
            dropPosition = pos;
            view.render();
        }
    }

    /**
     * Where a row of any grid is drawn: the x offset of its grid (content x), its top (viewport y),
     * its own height, the height of its name cell and where its value cells end.
     */
    record RowBox(double ox, double top, double own, double span, double right) {
    }

    /** @return the drawn box of a visible row of the root or of an opened cell's grid, or {@code null} */
    RowBox rowBox(FlatRow row) {
        GridBlock block = row.getBlock();
        if (block == null) {
            Integer i = view.rowIndexMap.get(row);
            return i == null ? null : new RowBox(0, view.rowYPositions[i] - view.scrollOffsetY, view.rowHeights[i], view.rowSpanHeight(i),
                    Math.max(view.totalWidth, view.viewportWidthModel() + view.scrollOffsetX));
        }
        GridBlockLayout.Result g = block.geometry();
        Integer i = g == null ? null : g.index().get(row);
        double[] origin = i == null ? null : view.blockOrigin(block);
        if (origin == null) {
            return null;
        }
        int last = g.lastDescendant()[i];
        return new RowBox(origin[0], origin[1] + g.y()[i], g.height()[i], g.y()[last] + g.height()[last] - g.y()[i],
                origin[0] + g.width());
    }

    /**
     * Finds the column separator under the pointer: the pointer must sit in the
     * column-header row of an embedded table, within {@link #RESIZE_HANDLE}
     * pixels of a column's right edge.
     *
     * @param mx x in model units
     * @param my y in unscaled viewport units
     * @return the drag descriptor, or {@code null} when no handle is there
     */
    ColumnDrag findColumnHandleAt(double mx, double my) {
        GridCanvasView.Pointer p = view.pointerAt(mx, my); // the deepest table under the point (inside opened cells too)
        if (p == null || p.table() == null) {
            return null;
        }
        RepeatingElementsTable table = p.table();
        double headerTop = p.tableTop();
        if (p.ly() < headerTop || p.ly() >= headerTop + GridCanvasView.ROW_HEIGHT || p.lx() < table.getX()) {
            return null;
        }
        int colIdx = table.getColumnSeparatorAt(p.lx(), RESIZE_HANDLE);
        if (colIdx < 0) {
            return null;
        }
        return new ColumnDrag(table, colIdx, 0, table.getColumn(colIdx).getWidth());
    }

    /**
     * Finds a block's name-column separator under the pointer: the hairline between a row's
     * name cell and its value column (it spans the row's whole name cell, i.e. an expanded
     * container's child block too), within {@link #RESIZE_HANDLE} pixels. Dragging it resizes
     * the name column of the block the row belongs to.
     *
     * @param mx x in model units
     * @param my y in unscaled viewport units
     * @return the drag descriptor (start width = the block's current name-column width), or {@code null}
     */
    BlockDrag findBlockSeparatorAt(double mx, double my) {
        GridCanvasView.Pointer p = view.pointerAt(mx, my);
        return p == null ? null : blockSeparator(p.row(), p.lx());
    }

    /** @return the drag of the block of {@code row} when {@code x} (the row's grid) is on its separator */
    BlockDrag blockSeparator(FlatRow row, double x) {
        if (Math.abs(x - row.getValueX()) > RESIZE_HANDLE) {
            return null;
        }
        return new BlockDrag(widthKeyOf(row), row.getBlock(), 0, row.getValueX() - row.getLabelX());
    }

    /** @return the key the name-column width of the block holding {@code row} is stored under */
    private String widthKeyOf(FlatRow row) {
        FlatRow parent = row.getParentRow();
        if (row.getBlock() == null) {
            return view.blockKey(parent);
        }
        return parent == null || parent.getDepth() < 0 ? cellBlockKey(row.getBlock()) : view.keyOf(parent);
    }

    /**
     * @return the width key of an opened cell's top block: the member's positional path within its
     *         group and the column ({@code /a/item[2]/#cell/name}), distinct from every row key
     */
    static String cellBlockKey(GridBlock grid) {
        RepeatingElementsTable.TableRow host = grid.host();
        RepeatingElementsTable table = host == null ? null : host.getOwner();
        FlatRow owner = table == null ? null : table.getOwnerRow();
        String group = owner == null ? null : owner.getTableGroupKey();
        int r = table == null ? -1 : table.getRows().indexOf(host);
        return (group == null ? "" : group) + "[" + (r + 1) + "]/#cell/" + grid.column();
    }

    /** Returns a block to automatic (content) name-column width. */
    void resetBlockWidth(BlockDrag separator) {
        view.viewState.setNameColumnWidth(separator.key(), null);
        if (separator.grid() != null) {
            separator.grid().invalidate();
        }
    }
}
