package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorSeverity;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;

/**
 * Read-only result view for a Schematron rule check: a severity summary plus a table of the detected
 * issues (severity, type, line/column, message). Opened as a tool tab by the Validation panel.
 */
public class SchematronCheckResultView extends VBox {

    private final TableView<SchematronError> table = new TableView<>();
    private final Label summary = new Label();

    public SchematronCheckResultView(List<SchematronError> issues) {
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        summary.getStyleClass().add("fxt-side-panel-title");
        summary.setText(summaryFor(issues));

        table.getColumns().add(column("Severity", e -> e.severity().name(), 90));
        table.getColumns().add(column("Type", e -> e.type().name(), 120));
        table.getColumns().add(column("Line", e -> Integer.toString(e.line()), 60));
        table.getColumns().add(column("Col", e -> Integer.toString(e.column()), 50));
        TableColumn<SchematronError, String> message = column("Message", SchematronError::message, -1);
        table.getColumns().add(message);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No issues found."));
        table.getItems().setAll(issues);
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(summary, table);
    }

    private static TableColumn<SchematronError, String> column(String title,
            java.util.function.Function<SchematronError, String> value, double prefWidth) {
        TableColumn<SchematronError, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
            column.setMaxWidth(prefWidth * 2);
        }
        return column;
    }

    private static String summaryFor(List<SchematronError> issues) {
        if (issues == null || issues.isEmpty()) {
            return "No issues found.";
        }
        long errors = issues.stream().filter(i -> i.severity() == ErrorSeverity.ERROR).count();
        long warnings = issues.stream().filter(i -> i.severity() == ErrorSeverity.WARNING).count();
        long infos = issues.stream().filter(i -> i.severity() == ErrorSeverity.INFO).count();
        return errors + " error" + (errors == 1 ? "" : "s")
                + " · " + warnings + " warning" + (warnings == 1 ? "" : "s")
                + " · " + infos + " info";
    }

    /** @return the number of issues shown (for tests/observers). */
    public int getIssueCount() {
        return table.getItems().size();
    }

    /** @return the severity summary text (for tests/observers). */
    public String getSummaryText() {
        return summary.getText();
    }
}
