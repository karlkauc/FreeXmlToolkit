package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.TemplateProfileEntry;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * Read-only performance report: overall timing/size + calls and time per template rule,
 * named template and function, slowest first. Times are inclusive (a template's time
 * contains the templates it calls).
 */
public class ProfileView extends VBox {

    private final TableView<TemplateProfileEntry> table = new TableView<>();
    private final Label summary = new Label();

    public ProfileView(XsltTransformationResult result) {
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        summary.getStyleClass().add("fxt-side-panel-title");
        long totalMs = result.getExecutionTime();
        int outputSize = result.getOutputContent() == null ? 0 : result.getOutputContent().length();
        int calls = result.getTemplateProfile().stream().mapToInt(TemplateProfileEntry::calls).sum();
        summary.setText("Total " + totalMs + " ms · output " + outputSize + " chars · "
                + calls + " template/function call(s)");

        table.getColumns().add(DebugTableColumns.col("Template / Function", TemplateProfileEntry::name, 240));
        table.getColumns().add(DebugTableColumns.col("Kind", TemplateProfileEntry::kind, 110));
        table.getColumns().add(DebugTableColumns.col("Line", t -> Integer.toString(t.lineNumber()), 60));
        table.getColumns().add(DebugTableColumns.col("Calls", t -> Integer.toString(t.calls()), 70));
        table.getColumns().add(DebugTableColumns.col("Total (ms)", t -> millis(t.totalNanos()), 90));
        table.getColumns().add(DebugTableColumns.col("Avg (ms)",
                t -> millis(t.calls() == 0 ? 0 : t.totalNanos() / t.calls()), 90));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No templates or functions were executed."));
        table.getItems().setAll(result.getTemplateProfile()); // already slowest first
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(summary, table);
    }

    /** Nanoseconds as milliseconds with three decimals (most template calls take well under 1 ms). */
    static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    public String getSummaryText() {
        return summary.getText();
    }
}
