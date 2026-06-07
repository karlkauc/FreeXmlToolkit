package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * Batch-transform tool tab: pick XML files, run the active stylesheet/XQuery over each,
 * inspect per-file results, and save them all to a directory.
 */
public class BatchTransformView extends VBox {

    /** Whether the batch runs the XSLT stylesheet or the XQuery script. */
    public enum Kind { XSLT, XQUERY }

    private final Kind kind;
    private final String transformContent;
    private final Map<String, Object> parameters;
    private final OutputFormat format;

    private final TableView<BatchFileResult> table = new TableView<>();
    private final List<File> inputFiles = new ArrayList<>();
    private final TextArea resultArea = new TextArea();
    private final Label summary = new Label("No files selected.");
    private List<BatchFileResult> lastResults = List.of();

    public BatchTransformView(Kind kind, String transformContent,
            Map<String, Object> parameters, OutputFormat format) {
        this.kind = kind;
        this.transformContent = transformContent == null ? "" : transformContent;
        this.parameters = parameters == null ? Map.of() : parameters;
        this.format = format == null ? OutputFormat.XML : format;

        setSpacing(8);
        setPadding(new Insets(12));
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("BATCH (" + kind + ")");
        title.getStyleClass().add("fxt-side-panel-title");

        Button addFiles = button("Add Files…", "bi-file-earmark-plus", this::addFiles);
        Button addDir = button("Add Directory…", "bi-folder-plus", this::addDirectory);
        Button removeSel = button("Remove", "bi-x-circle", this::removeSelected);
        Button clear = button("Clear", "bi-trash", () -> { inputFiles.clear(); refreshFileTable(); });
        HBox fileButtons = new HBox(6, addFiles, addDir, removeSel, clear);

        table.getColumns().add(DebugTableColumns.col("File", r -> r.file().getName(), 200));
        table.getColumns().add(DebugTableColumns.col("Status", r -> r.ok() ? "OK" : "ERROR", 80));
        table.getColumns().add(DebugTableColumns.col("ms", r -> Long.toString(r.timeMs()), 60));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Add XML files to process."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, sel) ->
                resultArea.setText(sel == null ? "" : (sel.ok() ? sel.output() : sel.error())));
        VBox.setVgrow(table, Priority.ALWAYS);

        Button run = button("Run Batch", "bi-play-fill", this::run);
        Button saveAll = button("Save All…", "bi-save", this::saveAll);
        HBox runButtons = new HBox(8, run, saveAll, summary);
        runButtons.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        resultArea.setEditable(false);
        resultArea.setPrefRowCount(8);
        resultArea.getStyleClass().add("fxt-transform-output");

        getChildren().addAll(title, fileButtons, table, runButtons,
                new Label("RESULT"), resultArea);
        refreshFileTable();
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add XML files");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        List<File> chosen = chooser.showOpenMultipleDialog(getScene() != null ? getScene().getWindow() : null);
        if (chosen != null) {
            inputFiles.addAll(chosen);
            refreshFileTable();
        }
    }

    private void addDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Add a directory of XML files");
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir != null && dir.isDirectory()) {
            File[] xmls = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
            if (xmls != null) {
                inputFiles.addAll(List.of(xmls));
                refreshFileTable();
            }
        }
    }

    private void removeSelected() {
        BatchFileResult sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            return;
        }
        // Keep inputFiles, lastResults and the visible table consistent across both states:
        // pre-run the table shows placeholder rows over inputFiles; post-run it shows lastResults.
        inputFiles.remove(sel.file());
        if (!lastResults.isEmpty()) {
            List<BatchFileResult> remaining = new ArrayList<>(lastResults);
            remaining.remove(sel);
            lastResults = remaining;
        }
        table.getItems().remove(sel);
        summary.setText(inputFiles.size() + " file(s).");
    }

    /** Shows the current input files (un-run) as placeholder rows. */
    private void refreshFileTable() {
        List<BatchFileResult> rows = new ArrayList<>();
        for (File f : inputFiles) {
            rows.add(new BatchFileResult(f, null, true, null, 0));
        }
        table.getItems().setAll(rows);
        summary.setText(inputFiles.size() + " file(s).");
    }

    /** Runs the batch off the UI thread and shows per-file outcomes. */
    public void run() {
        if (inputFiles.isEmpty()) {
            summary.setText("Add files first.");
            return;
        }
        List<File> files = new ArrayList<>(inputFiles);
        summary.setText("Running…");
        FxtGui.executorService.submit(() -> {
            List<BatchFileResult> results = kind == Kind.XQUERY
                    ? BatchTransformRunner.runXQueryBatch(files, transformContent, parameters, format)
                    : BatchTransformRunner.runXsltBatch(files, transformContent, parameters, format);
            long ok = results.stream().filter(BatchFileResult::ok).count();
            Platform.runLater(() -> {
                lastResults = results;
                table.getItems().setAll(results);
                summary.setText(ok + " ok · " + (results.size() - ok) + " error(s)");
            });
        });
    }

    private void saveAll() {
        if (lastResults.isEmpty()) {
            summary.setText("Run the batch first.");
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Save all results to…");
        File dir = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (dir == null) {
            return;
        }
        int written = BatchTransformRunner.writeAll(lastResults, Path.of(dir.getAbsolutePath()),
                format.getFileExtension());
        summary.setText("Wrote " + written + " file(s) to " + dir.getName());
    }

    public int getFileCount() {
        return inputFiles.size();
    }

    /** Adds input files directly (for tests). */
    public void addInputFiles(List<File> files) {
        inputFiles.addAll(files);
        refreshFileTable();
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }
}
