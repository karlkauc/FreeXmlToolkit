package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;

/**
 * Add/remove XPath watch expressions. On each pause they are evaluated against the paused
 * context item and the variables in scope (via {@link WatchEvaluator}); while the run is not
 * paused they read {@code <not paused>}. Shown as the "Watch" tab of the DEBUG pane: a compact
 * toolbar (expression field, Add, Remove) above the table; {@code Delete} removes the selected
 * watch.
 */
public class WatchView extends VBox {

    /** One watch row: the expression and its last evaluated value/error. */
    public static final class WatchRow {
        private final String expression;
        private String value = "<not evaluated>";

        public WatchRow(String expression) {
            this.expression = expression;
        }

        public String getExpression() { return expression; }
        public String getValue() { return value; }
    }

    private final TableView<WatchRow> table = new TableView<>();
    private final TextField input = new TextField();
    private WatchEvaluator evaluator;
    private PausedSnapshot lastSnapshot;
    /** Bumped per evaluation so a slow earlier pass cannot overwrite a newer one. */
    private int generation;
    private Consumer<WatchRow> onOpenValue = r -> { };
    private Consumer<WatchRow> onCopyValue = r -> { };

    public WatchView() {
        getStyleClass().add("fxt-debug-panel");

        table.getColumns().add(DebugTableColumns.col("Expression", WatchRow::getExpression, 160));
        table.getColumns().add(DebugTableColumns.valueColumn("Value", WatchRow::getValue, -1,
                r -> onOpenValue.accept(r)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No watches."));
        table.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                removeSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                WatchRow r = table.getSelectionModel().getSelectedItem();
                if (r != null) {
                    onOpenValue.accept(r);
                    e.consume();
                }
            }
        });
        table.setContextMenu(DebugValueMenu.create(
                () -> table.getSelectionModel().getSelectedItem(), r -> onOpenValue.accept(r),
                r -> onCopyValue.accept(r)));
        VBox.setVgrow(table, Priority.ALWAYS);

        input.setPromptText("XPath against the paused context, e.g. count(item) or $total");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> addWatch());
        Button add = DebugToolbar.action("Add", "bi-plus-lg", "Add the expression as a watch (Enter)");
        add.setOnAction(e -> addWatch());
        Button remove = DebugToolbar.action("Remove", "bi-dash-lg", "Remove the selected watch (Delete)");
        remove.setOnAction(e -> removeSelected());

        HBox toolbar = new HBox(input, add, remove);
        toolbar.getStyleClass().add("fxt-debug-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(toolbar, table);
    }

    /** The evaluator bound to the debugged run's processor (set per session/launch). */
    public void setEvaluator(WatchEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** Adds a watch programmatically (also used by tests). */
    public void addWatch(String expression) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        table.getItems().add(new WatchRow(expression.trim()));
        evaluateAll(lastSnapshot);
    }

    private void addWatch() {
        String expr = input.getText();
        input.clear();
        addWatch(expr);
    }

    /** Removes the selected watch, if any (Remove button, Delete key). */
    public void removeSelected() {
        WatchRow r = table.getSelectionModel().getSelectedItem();
        if (r != null) {
            table.getItems().remove(r);
        }
    }

    /** Selects the watch at {@code index} (tests, keyboard flows). */
    public void select(int index) {
        table.getSelectionModel().clearAndSelect(index);
    }

    /**
     * Re-evaluates every watch against {@code snapshot} (call on each pause; {@code null}
     * marks the watches as not paused). The XPath evaluation runs off the FX thread;
     * results are applied back on the FX thread.
     */
    public void evaluateAll(PausedSnapshot snapshot) {
        this.lastSnapshot = snapshot;
        final List<WatchRow> rows = new ArrayList<>(table.getItems());
        if (rows.isEmpty()) {
            return;
        }
        final WatchEvaluator eval = evaluator;
        final int gen = ++generation;
        if (snapshot == null || eval == null) {
            for (WatchRow row : rows) {
                row.value = "<not paused>";
            }
            table.refresh();
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            List<String> values = new ArrayList<>(rows.size());
            for (WatchRow row : rows) {
                try {
                    values.add(eval.evaluate(row.expression, snapshot));
                } catch (Throwable t) {
                    values.add("ERROR: " + t.getMessage());
                }
            }
            javafx.application.Platform.runLater(() -> {
                if (gen != generation) {
                    return;
                }
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).value = values.get(i);
                }
                table.refresh();
            });
        });
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    /** @return the current value text of the watch at {@code index} (for tests/observers). */
    public String getValueAt(int index) {
        return table.getItems().get(index).getValue();
    }

    /** Invoked with the watch whose full result should be opened (click on a value, Enter, menu). */
    public void setOnOpenValue(Consumer<WatchRow> handler) {
        this.onOpenValue = handler == null ? r -> { } : handler;
    }

    /** Invoked with the watch whose full result should be copied (context menu). */
    public void setOnCopyValue(Consumer<WatchRow> handler) {
        this.onCopyValue = handler == null ? r -> { } : handler;
    }

    /** Opens the value of watch {@code index} as a click would (tests). */
    void fireOpen(int index) {
        onOpenValue.accept(table.getItems().get(index));
    }

    /** The table, for tests that drive it with key events. */
    TableView<WatchRow> table() {
        return table;
    }
}
