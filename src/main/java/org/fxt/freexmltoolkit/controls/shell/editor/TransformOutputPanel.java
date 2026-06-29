package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * The OUTPUT panel docked below the editor tabs (Figma mockup "Redesign · Unified —
 * Transform (XSLT)", node 47:3): source on top, transformation result underneath.
 * Shows the result of XSLT transforms, XPath/JSONPath and XQuery runs triggered from
 * the {@link TransformPanel} — a format badge, a status line ("Transformed · 42 ms"),
 * and the result as rendered HTML preview (WebView), plain text, or an XQuery table,
 * switched by a Preview/Text/Table segmented toggle.
 * <p>
 * The panel is long-lived and owned by the {@link EditorHost} (the Transform side
 * panel is recreated on every activity switch), so results survive switching
 * activities. It hides itself via the ✕ button and re-appears on the next run.
 */
public class TransformOutputPanel extends VBox {

    private final EditorHost editorHost;
    private final TextArea output = new TextArea();
    private final TableView<List<String>> resultTable = new TableView<>();
    private final StackPane previewHolder = new StackPane();
    private final Label badge = new Label();
    private final IconifyIcon statusIcon = new IconifyIcon("bi-check-circle-fill");
    private final Label statusLabel = new Label();
    private final ToggleButton previewToggle = new ToggleButton("Preview");
    private final ToggleButton textToggle = new ToggleButton("Text");
    private final ToggleButton tableToggle = new ToggleButton("Table");
    /** Lazily created WebView updater (or a text fallback in stripped runtimes). */
    private java.util.function.Consumer<String> htmlPreviewUpdater;
    /** The raw "N ms · M chars" stats of the latest run ("error" for a failed one). */
    private String statsText = "";
    /** The output format of the most recent successful run (for the Editor/Save actions). */
    private OutputFormat lastResultFormat = OutputFormat.XML;
    /** The (re-used) editor tab holding the latest opened result, if still open. */
    private OpenDocument resultDocument;

    public TransformOutputPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-output-panel");
        setPrefHeight(260);
        setMinHeight(120);

        Label title = new Label("OUTPUT");
        title.getStyleClass().add("fxt-output-title");
        badge.getStyleClass().add("fxt-output-badge");
        badge.setVisible(false);
        statusIcon.setIconSize(12);
        statusIcon.setVisible(false);
        statusLabel.getStyleClass().add("fxt-output-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Preview/Text/Table view toggles: exactly one stays selected; Preview only
        // appears for HTML results, Table only for tabular XQuery results.
        ToggleGroup viewGroup = new ToggleGroup();
        for (ToggleButton toggle : List.of(previewToggle, textToggle, tableToggle)) {
            toggle.setToggleGroup(viewGroup);
            toggle.getStyleClass().add("fxt-seg");
        }
        HBox viewSeg = new HBox(2, previewToggle, textToggle, tableToggle);
        viewSeg.getStyleClass().add("fxt-seg-group");
        textToggle.setSelected(true);
        setToggleAvailable(previewToggle, false);
        setToggleAvailable(tableToggle, false);
        viewGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                oldT.setSelected(true); // a segmented control always has one active segment
            } else {
                updateContentView();
            }
        });

        Button editorAction = flatAction("transform-output-editor", "bi-file-earmark-plus",
                "Open result as editor tab", this::openResultInEditor);
        Button browserAction = flatAction("transform-output-browser", "bi-box-arrow-up-right",
                "Open in browser", this::openInBrowser);
        Button saveAction = flatAction("transform-output-save", "bi-download",
                "Save result…", this::saveResult);
        Button closeAction = flatAction("transform-output-close", "bi-x",
                "Hide output panel", this::hide);

        HBox header = new HBox(10, title, badge, statusIcon, statusLabel, spacer,
                viewSeg, editorAction, browserAction, saveAction, closeAction);
        header.getStyleClass().add("fxt-output-header");
        header.setAlignment(Pos.CENTER_LEFT);

        output.setEditable(false);
        output.getStyleClass().add("fxt-transform-output");
        resultTable.setPlaceholder(new Label("Run an XQuery returning a sequence to see a table."));
        previewHolder.setVisible(false);
        previewHolder.setManaged(false);
        StackPane content = new StackPane(output, resultTable, previewHolder);
        VBox.setVgrow(content, Priority.ALWAYS);
        updateContentView();

        getChildren().addAll(header, content);
    }

    // ----- result display (called on the FX thread by TransformPanel) ------

    /** Shows a progress message ("Transforming…" / "Running…") while a run is in flight. */
    public void showPending(String message) {
        reveal();
        output.setText(message);
        statusIcon.setVisible(false);
        statusLabel.setText(message);
        textToggle.setSelected(true);
    }

    /** Shows a finished XSLT transform result (success or "ERROR: …"). */
    public void showTransformResult(String text, OutputFormat format, long elapsedMs) {
        showRun("Transformed", text, format, elapsedMs, null);
    }

    /** Shows an XPath/JSONPath query result. */
    public void showQueryResult(String text, long elapsedMs) {
        showRun("Query", text, null, elapsedMs, null);
    }

    /** Shows an XQuery result, with its tabular projection when available. */
    public void showXQueryResult(String text, XQueryTableRunner.XQueryTable table,
                                 OutputFormat format, long elapsedMs) {
        showRun("XQuery", text, format, elapsedMs, table);
    }

    /**
     * Shows a real failure: the message appears red in the output panel (as
     * {@link #showError}) <b>and</b> an unmistakable modal error dialog is raised.
     * Use this for actions that could not be performed (transform/query errors,
     * unreadable files); use {@link #showError} for mild guards that should not pop
     * a dialog.
     */
    public void showFailure(String message) {
        showError(message);
        org.fxt.freexmltoolkit.util.DialogHelper.notifyActionFailure(
                "Transform failed", PanelStatus.strip(message));
    }

    /** Shows an error or guard message (e.g. "No document open."). */
    public void showError(String message) {
        reveal();
        output.setText(message);
        statsText = "error";
        statusIcon.setIconLiteral("bi-x-circle");
        statusIcon.getStyleClass().removeAll("sev-ok", "sev-error");
        statusIcon.getStyleClass().add("sev-error");
        statusIcon.setVisible(true);
        statusLabel.setText("Error");
        badge.setVisible(false);
        setToggleAvailable(previewToggle, false);
        setToggleAvailable(tableToggle, false);
        textToggle.setSelected(true);
        updateContentView();
    }

    private void showRun(String verb, String text, OutputFormat format, long elapsedMs,
                         XQueryTableRunner.XQueryTable table) {
        if (text != null && text.startsWith("ERROR")) {
            showFailure(text);
            return;
        }
        reveal();
        output.setText(text);
        if (format != null) {
            lastResultFormat = format;
            badge.setText(format.name());
            badge.setVisible(true);
        } else {
            badge.setVisible(false);
        }
        statsText = statsText(text, elapsedMs);
        statusIcon.setIconLiteral("bi-check-circle-fill");
        statusIcon.getStyleClass().removeAll("sev-ok", "sev-error");
        statusIcon.getStyleClass().add("sev-ok");
        statusIcon.setVisible(true);
        statusLabel.setText(verb + " · " + statsText);

        boolean html = format == OutputFormat.HTML || format == OutputFormat.XHTML;
        setToggleAvailable(previewToggle, html);
        if (html) {
            updateHtmlPreview(text);
        }
        boolean tabular = table != null && !table.isError() && !table.isEmpty();
        setToggleAvailable(tableToggle, tabular);
        populateResultTable(table);
        // Auto-select the best view: rendered preview for HTML, the table for a
        // tabular XQuery sequence, plain text otherwise.
        (html ? previewToggle : tabular ? tableToggle : textToggle).setSelected(true);
        updateContentView();
    }

    /** @return a compact "N ms · M chars" stat, or "error" for a failed run. */
    private static String statsText(String result, long elapsedMs) {
        if (result == null || result.startsWith("ERROR")) {
            return "error";
        }
        return elapsedMs + " ms · " + result.length() + " chars";
    }

    // ----- header actions ---------------------------------------------------

    /** Opens (or refreshes) the current result text as an untitled editor tab. */
    public void openResultInEditor() {
        String result = output.getText();
        if (result == null || result.isBlank() || result.startsWith("ERROR")
                || "Transforming…".equals(result) || "Running…".equals(result)) {
            output.setText(result != null && result.startsWith("ERROR") ? result
                    : "Run a transform or query first.");
            return;
        }
        EditorFileType type = switch (lastResultFormat) {
            case JSON -> EditorFileType.JSON;
            case TEXT -> EditorFileType.OTHER;
            default -> EditorFileType.XML; // XML / HTML / XHTML
        };
        String name = "Transform-Result." + extension(lastResultFormat);
        boolean sameTab = resultDocument != null && name.equals(resultDocument.getDisplayName());
        if (!sameTab || !editorHost.updateGeneratedDocument(resultDocument, result)) {
            resultDocument = editorHost.openGeneratedDocument(result, type, name);
        }
    }

    /** Opens the current result in the system browser (renders HTML output). */
    public void openInBrowser() {
        try {
            File file = writeHtmlPreview(output.getText());
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(file.toURI());
            }
        } catch (Exception e) {
            statusLabel.setText("browser: " + e.getMessage());
        }
    }

    /** Saves the current result to a file chosen by the user (extension per format). */
    public void saveResult() {
        String result = output.getText();
        if (result == null || result.isBlank()) {
            return;
        }
        String extension = extension(lastResultFormat);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Transform Result");
        chooser.setInitialFileName("transform-result." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                extension.toUpperCase(java.util.Locale.ROOT), "*." + extension));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            saveResultTo(file);
        }
    }

    /** Writes the current result text to {@code file} (UTF-8); also used by tests. */
    public void saveResultTo(File file) {
        try {
            Files.writeString(file.toPath(), output.getText() == null ? "" : output.getText(),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            statusLabel.setText("save: " + e.getMessage());
        }
    }

    /** Writes {@code html} to a temporary {@code .html} file (for the browser preview). */
    public static File writeHtmlPreview(String html) throws java.io.IOException {
        File file = File.createTempFile("fxt-transform-", ".html");
        file.deleteOnExit();
        Files.writeString(file.toPath(), html == null ? "" : html, StandardCharsets.UTF_8);
        return file;
    }

    private static String extension(OutputFormat format) {
        return switch (format) {
            case JSON -> "json";
            case TEXT -> "txt";
            case HTML, XHTML -> "html";
            default -> "xml";
        };
    }

    // ----- content switching -------------------------------------------------

    /** Shows the content view matching the selected toggle (Preview / Text / Table). */
    private void updateContentView() {
        boolean preview = previewToggle.isSelected();
        boolean table = tableToggle.isSelected();
        previewHolder.setVisible(preview);
        previewHolder.setManaged(preview);
        resultTable.setVisible(table);
        resultTable.setManaged(table);
        output.setVisible(!preview && !table);
        output.setManaged(!preview && !table);
    }

    /**
     * Loads {@code html} into the rendered preview, creating it lazily on first use:
     * a WebView when available, otherwise a read-only text fallback (e.g. in
     * stripped-down runtimes).
     */
    private void updateHtmlPreview(String html) {
        if (htmlPreviewUpdater == null) {
            try {
                javafx.scene.web.WebView view = new javafx.scene.web.WebView();
                htmlPreviewUpdater = content -> view.getEngine().loadContent(content == null ? "" : content);
                previewHolder.getChildren().setAll(view);
            } catch (Throwable t) {
                TextArea fallback = new TextArea();
                fallback.setEditable(false);
                fallback.getStyleClass().add("fxt-transform-output");
                htmlPreviewUpdater = fallback::setText;
                previewHolder.getChildren().setAll(fallback);
            }
        }
        htmlPreviewUpdater.accept(html);
    }

    /** Rebuilds the result table from a tabular XQuery result (columns + string rows). */
    private void populateResultTable(XQueryTableRunner.XQueryTable table) {
        resultTable.getColumns().clear();
        resultTable.getItems().clear();
        if (table == null || table.isError()) {
            return;
        }
        List<String> columns = table.columns();
        for (int i = 0; i < columns.size(); i++) {
            final int index = i;
            TableColumn<List<String>, String> column = new TableColumn<>(columns.get(i));
            column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                    index < cell.getValue().size() ? cell.getValue().get(index) : ""));
            resultTable.getColumns().add(column);
        }
        resultTable.getItems().setAll(table.rows());
    }

    private void setToggleAvailable(ToggleButton toggle, boolean available) {
        toggle.setVisible(available);
        toggle.setManaged(available);
    }

    // ----- visibility ---------------------------------------------------------

    /** Un-hides the panel (every run re-shows it after a ✕). */
    private void reveal() {
        setVisible(true);
        setManaged(true);
    }

    /** Hides the panel (the ✕ header action); the next run re-shows it. */
    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    /** @return {@code true} while the panel is docked visible below the editor. */
    public boolean isShowing() {
        return isVisible();
    }

    // ----- accessors (TransformPanel delegates / tests) -----------------------

    /** @return the editor tab document holding the last opened result, or {@code null}. */
    public OpenDocument getResultDocument() {
        return resultDocument;
    }

    /** @return the current output text (for tests/observers). */
    public String getOutputText() {
        return output.getText();
    }

    /** @return the raw "N ms · M chars" stats of the latest run (for tests/observers). */
    public String getStatsText() {
        return statsText;
    }

    /** @return {@code true} if the Table view is currently shown. */
    public boolean isResultTableShown() {
        return tableToggle.isSelected();
    }

    /** @return {@code true} if the rendered HTML preview is currently shown. */
    public boolean isHtmlPreviewShown() {
        return previewToggle.isSelected() && isShowing();
    }

    /** @return the current result-table column headers (for tests/observers). */
    public List<String> getResultColumns() {
        return resultTable.getColumns().stream().map(TableColumn::getText).toList();
    }

    /** @return the current result-table row count (for tests/observers). */
    public int getResultRowCount() {
        return resultTable.getItems().size();
    }

    private static Button flatAction(String id, String iconLiteral, String tooltip, Runnable action) {
        Button button = new Button();
        button.setId(id);
        button.getStyleClass().add("fxt-sp-action");
        IconifyIcon icon = new IconifyIcon(iconLiteral);
        icon.setIconSize(14);
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }
}
