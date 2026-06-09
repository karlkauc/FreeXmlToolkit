package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.ErrorSeverity;
import org.fxt.freexmltoolkit.controls.SchematronErrorDetector.SchematronError;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Read-only result view for a Schematron rule check: a severity summary plus a table of the detected
 * issues (severity, type, line/column, message). Opened as a tool tab by the Validation panel.
 */
public class SchematronCheckResultView extends VBox {

    private final TableView<SchematronError> table = new TableView<>();
    private final Label summary = new Label();
    private final List<SchematronError> issues;

    public SchematronCheckResultView(List<SchematronError> issues) {
        this.issues = issues == null ? List.of() : List.copyOf(issues);
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        summary.getStyleClass().add("fxt-side-panel-title");
        summary.setText(summaryFor(issues));

        Button exportCsv = exportButton("Export CSV", "bi-filetype-csv", "csv", "CSV", SchematronResultExport::toCsv);
        Button exportJson = exportButton("Export JSON", "bi-filetype-json", "json", "JSON", SchematronResultExport::toJson);
        boolean hasIssues = !this.issues.isEmpty();
        exportCsv.setDisable(!hasIssues);
        exportJson.setDisable(!hasIssues);
        HBox toolbar = new HBox(8, exportCsv, exportJson);

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

        getChildren().addAll(summary, toolbar, table);
    }

    /** Builds an export button that writes the issues to a chosen file via {@code formatter}. */
    private Button exportButton(String text, String icon, String extension, String filterName,
            java.util.function.Function<List<SchematronError>, String> formatter) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> export(extension, filterName, formatter.apply(issues)));
        return button;
    }

    /** Prompts for a target file and writes {@code content} to it. */
    private void export(String extension, String filterName, String content) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Schematron results as " + filterName);
        chooser.setInitialFileName("schematron-results." + extension);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(filterName + " File (*." + extension + ")", "*." + extension));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), content);
            summary.setText("Exported " + issues.size() + " issue(s) to " + file.getName());
        } catch (Exception ex) {
            summary.setText("Export failed: " + ex.getMessage());
        }
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
