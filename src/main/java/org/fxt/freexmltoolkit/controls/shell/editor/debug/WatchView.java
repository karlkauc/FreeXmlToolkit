package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;

/**
 * Add/remove XPath watch expressions. On each pause they are evaluated against the paused
 * context item and the variables in scope (via {@link WatchEvaluator}); while the run is not
 * paused they read {@code <not paused>}.
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

    public WatchView() {
        setSpacing(6);
        setPadding(new Insets(8));
        getStyleClass().add("fxt-side-panel-content");
        Label title = new Label("WATCH");
        title.getStyleClass().add("fxt-side-panel-title");

        table.getColumns().add(DebugTableColumns.col("Expression", WatchRow::getExpression, 180));
        table.getColumns().add(DebugTableColumns.col("Value", WatchRow::getValue, -1));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No watches."));
        VBox.setVgrow(table, Priority.ALWAYS);

        input.setPromptText("XPath against the paused context, e.g. count(item) or $total");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> addWatch());
        Button add = new Button("Add");
        add.getStyleClass().add("fxt-tool-button");
        add.setOnAction(e -> addWatch());
        Button remove = new Button("Remove");
        remove.getStyleClass().add("fxt-tool-button");
        remove.setOnAction(e -> {
            WatchRow r = table.getSelectionModel().getSelectedItem();
            if (r != null) {
                table.getItems().remove(r);
            }
        });

        getChildren().addAll(title, table, new HBox(6, input, add, remove));
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
}
