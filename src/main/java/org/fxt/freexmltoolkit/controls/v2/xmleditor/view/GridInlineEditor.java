package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;



import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.widgets.TypeAwareWidgetFactory;

/**
 * The inline editor of a {@link GridCanvasView}: starts editing a row's value or name or a
 * table cell, places a text field or the adapter's type-aware widget over the canvas, and
 * commits or cancels the edit through the model adapter.
 *
 * @param <N> the model node type
 */
final class GridInlineEditor<N> {

    /** The view this editor edits in. */
    private final GridCanvasView<N> view;

    GridInlineEditor(GridCanvasView<N> view) {
        this.view = view;
    }

    // ==================== Inline Editing ====================

    TextField editField = null;
    FlatRow editingRow = null;
    boolean editingValue = false;
    boolean editingElementName = false;

    // Table cell editing state (kept for repeating table integration)
    private RepeatingElementsTable editingTable = null;
    private int editingTableRowIndex = -1;
    private String editingTableColumnName = null;

    // Type-aware widget editing
    private TypeAwareWidgetFactory.EditWidget activeEditWidget = null;
    Node activeWidgetNode = null;

    // Guard to prevent double commits
    private boolean isCommitting = false;

    /**
     * Starts editing a table cell.
     */
    void startEditingTableCell(RepeatingElementsTable table, int rowIndex, String columnName,
                                        double tableScreenTop) {
        cancelEditing();

        RepeatingElementsTable.TableRow row = table.getRows().get(rowIndex);
        RepeatingElementsTable.TableColumn col = table.getColumn(columnName);
        if (col == null) {
            return;
        }

        // Don't edit complex children
        if (row.hasComplexChild(columnName)) {
            return;
        }

        editingTable = table;
        editingTableRowIndex = rowIndex;
        editingTableColumnName = columnName;

        String currentValue = row.getValue(columnName);

        // Calculate cell position from the drawn grid (works in both orientations)
        RepeatingElementsTable.CellRect rect = table.cellRect(rowIndex, table.getColumns().indexOf(col));
        double[] system = table.getOwnerRow() != null && table.getOwnerRow().getBlock() != null
                ? view.tableOrigin(table) : new double[] {0, tableScreenTop};
        if (system == null) {
            return;
        }
        double cellX = system[0] + rect.x() - view.scrollOffsetX;
        double cellY = system[1] + rect.y();

        GridModelAdapter.EditSpec spec = view.adapter.cellEditSpec(table, rowIndex, columnName);
        createEditField(spec != null ? spec : GridModelAdapter.EditSpec.plain(currentValue),
                cellX, cellY, rect.width());
    }

    // ==================== Inline Editing ====================

    /**
     * What a double-click on a row edits: in the value column the value; in the name column a
     * leaf's value, a container's name, an attribute's or PI's name, or a text-like node's value.
     */
    void editOnDoubleClick(FlatRow row, boolean inValueColumn) {
        if (inValueColumn) {
            startEditingValue(row);
        } else if (row.getType().isContainer() || row.getType().isScalarLeaf()) {
            if (row.isLeafWithValue()) {
                startEditingValue(row);
            } else {
                startEditingElementName(row);
            }
        } else if (view.adapter.canEditName(row)) {
            startEditingElementName(row);
        } else {
            startEditingValue(row);
        }
    }

    private void startEditingValue(FlatRow row) {
        cancelEditing();

        if (row == null || !view.adapter.canEditValue(row)) {
            return;
        }

        editingRow = row;
        editingValue = true;
        editingElementName = false;

        double[] origin = view.rowOrigin(row);
        if (origin == null) {
            return;
        }

        double x = origin[0] + row.getValueX() - view.scrollOffsetX;
        double y = origin[1] + 2;
        double width = Math.max(view.viewportWidthModel() - x, GridCanvasView.MIN_VALUE_COL_WIDTH);

        GridModelAdapter.EditSpec spec = view.adapter.editSpec(row, false);
        createEditField(spec != null ? spec
                : GridModelAdapter.EditSpec.plain(row.getValue() != null ? row.getValue() : ""), x, y, width);
    }

    void startEditingElementName(FlatRow row) {
        cancelEditing();

        if (row == null || !view.adapter.canEditName(row)) {
            return;
        }

        editingRow = row;
        editingValue = false;
        editingElementName = true;

        double[] origin = view.rowOrigin(row);
        if (origin == null) {
            return;
        }

        double textX = view.rowTextX(row);
        double labelX = origin[0] + textX - view.scrollOffsetX;
        double y = origin[1] + 2;
        double width = row.getValueX() - textX - 4;

        GridModelAdapter.EditSpec spec = view.adapter.editSpec(row, true);
        createEditField(spec != null ? spec : GridModelAdapter.EditSpec.plain(row.getLabel()), labelX, y, width);
    }

    /**
     * Installs the inline editor described by the spec: a type-aware widget when the
     * adapter supplied one, otherwise a plain text field (with the spec's tooltip, if any).
     *
     * @param spec  what to edit
     * @param vx    x in unscaled viewport coordinates (scroll applied, zoom not)
     * @param vy    y in unscaled viewport coordinates
     * @param width the field width in unscaled pixels
     */
    private void createEditField(GridModelAdapter.EditSpec spec, double vx, double vy, double width) {
        String currentValue = spec.currentValue();
        // Calculate minimum width based on content
        double contentBasedWidth = view.metrics.width(currentValue, GridFont.ROW) + 30;
        double z = view.getZoom();
        double effectiveWidth = Math.max(width, Math.max(contentBasedWidth, 120)) * z;
        double x = vx * z;
        double y = vy * z;
        double fieldHeight = GridCanvasView.ROW_HEIGHT * z;

        // Type-aware widget supplied by the adapter (schema-driven for XML, type-driven for JSON)
        {
            TypeAwareWidgetFactory.EditWidget widget = spec.widget();

            if (widget != null) {
                activeEditWidget = widget;
                activeWidgetNode = widget.getNode();

                double widgetMinWidth = effectiveWidth;
                if (activeWidgetNode instanceof javafx.scene.control.DatePicker) {
                    widgetMinWidth = Math.max(widgetMinWidth, 160 * z);
                } else if (activeWidgetNode instanceof javafx.scene.control.ComboBox) {
                    widgetMinWidth = Math.max(widgetMinWidth, 150 * z);
                }

                if (activeWidgetNode instanceof javafx.scene.layout.Region region) {
                    region.setLayoutX(x);
                    region.setLayoutY(y);
                    region.setPrefWidth(widgetMinWidth);
                    region.setMinWidth(widgetMinWidth);
                    region.setPrefHeight(fieldHeight);
                    region.setStyle(String.format(java.util.Locale.ROOT, "-fx-font-size: %.1fpx;", 12 * z));
                } else {
                    activeWidgetNode.setLayoutX(x);
                    activeWidgetNode.setLayoutY(y);
                }

                activeWidgetNode.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        cancelEditing();
                    } else if (e.getCode() == KeyCode.ENTER) {
                        commitEditing();
                    }
                });

                activeWidgetNode.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                    if (!isFocused && activeWidgetNode != null && !isCommitting) {
                        javafx.application.Platform.runLater(() -> {
                            if (activeWidgetNode != null && !activeWidgetNode.isFocused()) {
                                commitEditing();
                            }
                        });
                    }
                });

                view.canvasContainer.getChildren().add(activeWidgetNode);
                widget.focus();
                return;
            }
        }

        // Fallback to standard TextField
        editField = new TextField(currentValue);
        editField.setLayoutX(x);
        editField.setLayoutY(y);
        editField.setPrefWidth(effectiveWidth);
        editField.setMinWidth(effectiveWidth);
        editField.setPrefHeight(fieldHeight);
        editField.setStyle(String.format(java.util.Locale.ROOT,
                "-fx-font-size: %.1fpx; -fx-font-family: 'Segoe UI'; -fx-padding: 2 6;", 12 * z));

        // Add documentation tooltip if available
        if (spec.tooltip() != null) {
            Tooltip tooltip = new Tooltip(spec.tooltip());
            tooltip.setWrapText(true);
            tooltip.setMaxWidth(300);
            Tooltip.install(editField, tooltip);
        }

        editField.setOnAction(e -> commitEditing());
        editField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancelEditing();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                commitEditing();
                e.consume();
            }
        });

        editField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && editField != null && !isCommitting) {
                javafx.application.Platform.runLater(() -> {
                    if (editField != null && !editField.isFocused()) {
                        commitEditing();
                    }
                });
            }
        });

        view.canvasContainer.getChildren().add(editField);
        editField.requestFocus();
        editField.selectAll();
    }

    /** @return {@code true} while a text field or type-aware widget is open */
    boolean isEditing() {
        return editField != null || activeWidgetNode != null;
    }

    /**
     * Commits the open edit unless {@code target} lies inside the editor (a press in the field
     * itself keeps editing). A rejected value keeps the editor open, as on Enter.
     */
    void commitIfOutside(Node target) {
        Node editorNode = editField != null ? editField : activeWidgetNode;
        if (editorNode == null) {
            return;
        }
        for (Node n = target; n != null; n = n.getParent()) {
            if (n == editorNode) {
                return;
            }
        }
        commitEditing();
    }

    private void commitEditing() {
        if (isCommitting) {
            return;
        }
        isCommitting = true;

        try {
            // Get value from either widget or text field
            String newValue;
            if (activeEditWidget != null) {
                newValue = activeEditWidget.getValue();
            } else if (editField != null) {
                newValue = editField.getText();
            } else {
                cancelEditing();
                return;
            }

            // Handle table cell editing (for repeating tables integration)
            if (editingTable != null && editingTableRowIndex >= 0 && editingTableColumnName != null) {
                boolean accepted = view.adapter.commitCellEdit(editingTable, editingTableRowIndex,
                        editingTableColumnName, newValue);
                if (!accepted) {
                    keepEditorOpen();
                    return;
                }
                cancelEditing();
                view.rebuildTree();
                view.notifyDocumentModified();
                return;
            }

            // Handle row editing
            if (editingRow == null) {
                cancelEditing();
                return;
            }

            boolean accepted = view.adapter.commitRowEdit(editingRow, editingElementName, newValue);
            if (!accepted) {
                keepEditorOpen();
                return;
            }

            cancelEditing();
            view.rebuildTree();
            view.notifyDocumentModified();
        } finally {
            isCommitting = false;
        }
    }

    /**
     * The adapter rejected the entered value (and told the user): keep the inline editor
     * open and give it the focus back so the value can be corrected or the edit escaped.
     */
    private void keepEditorOpen() {
        javafx.application.Platform.runLater(() -> {
            if (editField != null) {
                editField.requestFocus();
                editField.selectAll();
            } else if (activeEditWidget != null) {
                activeEditWidget.focus();
            }
        });
    }

    void cancelEditing() {
        if (editField != null) {
            view.canvasContainer.getChildren().remove(editField);
            editField = null;
        }
        if (activeWidgetNode != null) {
            view.canvasContainer.getChildren().remove(activeWidgetNode);
            activeWidgetNode = null;
            activeEditWidget = null;
        }
        editingRow = null;
        editingValue = false;
        editingElementName = false;
        editingTable = null;
        editingTableRowIndex = -1;
        editingTableColumnName = null;
    }
}
