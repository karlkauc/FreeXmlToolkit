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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * The detailed Schematron validation report, opened as a tool tab by the
 * Validation panel: run metadata (document, rules file), severity summary,
 * and one table row per finding — severity, line, message, the failed
 * rule/test expression and the failing node's XPath context. Rows navigate
 * the editor to their line; the report can be saved as self-contained HTML
 * or as the raw SVRL XML.
 */
public class SchematronReportView extends VBox {

    private final SchematronReportData data;
    private final TableView<ValidationProblem> table = new TableView<>();
    private final Label status = new Label();

    public SchematronReportView(SchematronReportData data, EditorHost editorHost) {
        this.data = data;
        setSpacing(10);
        setPadding(new Insets(16));
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("SCHEMATRON REPORT");
        title.getStyleClass().add("fxt-side-panel-title");

        VBox meta = new VBox(2);
        if (data.documentName() != null && !data.documentName().isBlank()) {
            meta.getChildren().add(metaLabel("Document: " + data.documentName()));
        }
        if (data.schematronFile() != null) {
            meta.getChildren().add(metaLabel("Schematron: " + data.schematronFile().getName()));
        }
        meta.getChildren().add(metaLabel(summaryFor(data)));

        Button saveHtml = button("Save Report (HTML)", "bi-filetype-html",
                () -> save("html", "HTML", SchematronReportHtml.build(data)));
        Button saveSvrl = button("Save SVRL (XML)", "bi-filetype-xml",
                () -> save("xml", "SVRL XML", data.svrl()));
        saveSvrl.setDisable(data.svrl() == null || data.svrl().isBlank());
        status.getStyleClass().add("fxt-placeholder-text");
        HBox toolbar = new HBox(8, saveHtml, saveSvrl, status);
        toolbar.setSpacing(8);

        table.setId("schematron-report-table");
        table.getColumns().add(column("Severity", p -> capitalize(p.severity()), 90));
        table.getColumns().add(column("Line", p -> p.line() > 0 ? Integer.toString(p.line()) : "", 60));
        table.getColumns().add(column("Message", ValidationProblem::message, 380));
        table.getColumns().add(column("Rule / Test", p -> nullSafe(p.ruleId()), 220));
        TableColumn<ValidationProblem, String> context = column("Context (XPath)",
                p -> nullSafe(p.context()), -1);
        table.getColumns().add(context);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("All rules passed — no findings."));
        table.getItems().setAll(data.problems());
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.line() > 0 && editorHost != null) {
                editorHost.goToLine(newV.line());
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(title, meta, toolbar, table);
    }

    /** Prompts for a target file and writes {@code content} to it. */
    private void save(String extension, String filterName, String content) {
        if (content == null) {
            status.setText("Nothing to save.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Schematron report as " + filterName);
        chooser.setInitialFileName("schematron-report." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                filterName + " File (*." + extension + ")", "*." + extension));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(
                chooser, getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(), content);
            status.setText("Saved " + file.getName());
        } catch (Exception ex) {
            status.setText("Save failed: " + ex.getMessage());
        }
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private static Label metaLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-placeholder-text");
        return label;
    }

    private static TableColumn<ValidationProblem, String> column(String title,
            java.util.function.Function<ValidationProblem, String> value, double prefWidth) {
        TableColumn<ValidationProblem, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        if (prefWidth > 0) {
            column.setPrefWidth(prefWidth);
        }
        return column;
    }

    private static String summaryFor(SchematronReportData data) {
        List<ValidationProblem> problems = data.problems();
        if (problems.isEmpty()) {
            return "All rules passed.";
        }
        long errors = data.errorCount();
        long warnings = data.warningCount();
        return errors + " error" + (errors == 1 ? "" : "s")
                + " · " + warnings + " warning" + (warnings == 1 ? "" : "s");
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "Error";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /** @return the number of findings shown (for tests/observers). */
    public int getFindingCount() {
        return table.getItems().size();
    }

    /** @return the underlying report data (for tests/observers). */
    public SchematronReportData getData() {
        return data;
    }

    /** @return the status text of the save toolbar (for tests/observers). */
    public String getStatusText() {
        return status.getText();
    }

    /** Test seam: the table region for structural assertions. */
    Region tableForTest() {
        return table;
    }
}
