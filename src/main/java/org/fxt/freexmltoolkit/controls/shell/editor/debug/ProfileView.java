package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine.TemplateMatchInfo;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/** Read-only performance report: overall timing/size + per-template execution times. */
public class ProfileView extends VBox {

    private final TableView<TemplateMatchInfo> table = new TableView<>();
    private final Label summary = new Label();

    public ProfileView(XsltTransformationResult result) {
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        summary.getStyleClass().add("fxt-side-panel-title");
        long totalMs = result.getExecutionTime();
        int outputSize = result.getOutputContent() == null ? 0 : result.getOutputContent().length();
        int templateCount = result.getTemplateMatches() == null ? 0 : result.getTemplateMatches().size();
        summary.setText("Total " + totalMs + " ms · output " + outputSize + " chars · "
                + templateCount + " template match(es)");

        table.getColumns().add(col("Template", t -> displayName(t), 240));
        table.getColumns().add(col("Line", t -> Integer.toString(t.lineNumber()), 60));
        table.getColumns().add(col("Time (ms)", t -> Long.toString(t.executionTime()), 90));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No template timing captured."));
        if (result.getTemplateMatches() != null) {
            var sorted = new java.util.ArrayList<>(result.getTemplateMatches());
            sorted.sort(java.util.Comparator.comparingLong(TemplateMatchInfo::executionTime).reversed());
            table.getItems().setAll(sorted);
        }
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(summary, table);
    }

    private static String displayName(TemplateMatchInfo t) {
        if (t.name() != null && !t.name().isEmpty()) {
            return t.name();
        }
        return t.pattern() == null ? "" : t.pattern();
    }

    public int getRowCount() {
        return table.getItems().size();
    }

    public String getSummaryText() {
        return summary.getText();
    }

    private static TableColumn<TemplateMatchInfo, String> col(String title,
            java.util.function.Function<TemplateMatchInfo, String> value, double prefWidth) {
        TableColumn<TemplateMatchInfo, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }
}
