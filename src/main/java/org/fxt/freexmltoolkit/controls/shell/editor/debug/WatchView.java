package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.XmlService;

/** Add/remove XPath watch expressions, evaluated against the active XML on each pause. */
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
    private Supplier<String> xmlSupplier = () -> "";

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

        input.setPromptText("XPath to watch, e.g. count(//item)");
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

    /** Supplies the XML text watches are evaluated against (the active document). */
    public void setXmlSupplier(Supplier<String> supplier) {
        this.xmlSupplier = supplier == null ? () -> "" : supplier;
    }

    private void addWatch() {
        String expr = input.getText();
        if (expr == null || expr.isBlank()) {
            return;
        }
        table.getItems().add(new WatchRow(expr.trim()));
        input.clear();
        evaluateAll();
    }

    /**
     * Re-evaluates every watch against the current XML (call on each pause). The XPath
     * evaluation runs off the FX thread; results are applied back on the FX thread.
     */
    public void evaluateAll() {
        final String xml = xmlSupplier.get();
        final java.util.List<WatchRow> rows = new java.util.ArrayList<>(table.getItems());
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            for (WatchRow row : rows) {
                try {
                    String result = ServiceRegistry.get(XmlService.class)
                            .getXmlFromXpath(xml, row.expression);
                    row.value = result == null ? "" : result;
                } catch (Throwable t) {
                    row.value = "ERROR: " + t.getMessage();
                }
            }
            javafx.application.Platform.runLater(table::refresh);
        });
    }

    public int getRowCount() {
        return table.getItems().size();
    }
}
