package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * The Transform activity side panel: transforms the active XML with a chosen
 * XSLT, and evaluates XPath expressions against it. Reuses {@link TransformRunner}
 * (Saxon / XmlService); runs off the UI thread and shows the result in a panel
 * output area.
 * <p>
 * Supports XSLT parameters (name/value rows), the output format (XML / HTML /
 * XHTML / TEXT / JSON), XPath/JSONPath evaluation, an XQuery console, and a
 * debounced live preview that re-transforms while the document is edited.
 */
public class TransformPanel extends VBox {

    private final EditorHost editorHost;
    private final TextArea output = new TextArea();
    private final TableView<List<String>> resultTable = new TableView<>();
    private final ToggleButton textToggle = new ToggleButton("Text");
    private final ToggleButton tableToggle = new ToggleButton("Table");
    private final Label statsLabel = new Label();
    private final TextArea xqueryArea = new TextArea();
    private final TextField xpathField = new TextField();
    private final Label pathLabel = new Label("XPATH");
    private final Label xsltStatus = new Label("XSLT: none");
    private final VBox paramRows = new VBox(4);
    private final ComboBox<OutputFormat> outputFormat = new ComboBox<>();
    private final MenuButton savedQueriesMenu;
    private final MenuButton recentXsltMenu = new MenuButton("Recent");
    private final CheckBox livePreview = new CheckBox("Live preview");
    private final PauseTransition liveDebounce = new PauseTransition(Duration.millis(600));
    private File xsltFile;

    public TransformPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("TRANSFORM");
        title.getStyleClass().add("fxt-side-panel-title");

        Button setXslt = button("Set XSLT…", "bi-file-earmark-code", this::chooseXslt);
        recentXsltMenu.setGraphic(icon("bi-clock-history"));
        recentXsltMenu.getStyleClass().add("fxt-tool-button");
        recentXsltMenu.setOnShowing(e -> refreshRecentXsltMenu());
        Button transform = button("Transform", "bi-arrow-repeat", this::transform);
        xsltStatus.getStyleClass().add("fxt-placeholder-text");

        // Live preview: re-run the XSLT transform shortly after the active document
        // changes (typing / tab switch), when enabled and a stylesheet is set.
        liveDebounce.setOnFinished(e -> {
            if (livePreview.isSelected() && xsltFile != null && editorHost.getActiveDocument().isPresent()) {
                transform();
            }
        });
        livePreview.setOnAction(e -> scheduleLivePreview());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> scheduleLivePreview());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> scheduleLivePreview());

        // XSLT parameters (name/value rows) + output format.
        Label paramsLabel = new Label("PARAMETERS");
        paramsLabel.getStyleClass().add("fxt-side-panel-title");
        Button addParam = button("Add Parameter", "bi-plus-circle", () -> addParameter("", ""));

        outputFormat.getItems().setAll(OutputFormat.values());
        outputFormat.setValue(OutputFormat.XML);
        Label outputFormatLabel = new Label("OUTPUT");
        outputFormatLabel.getStyleClass().add("fxt-side-panel-title");

        pathLabel.getStyleClass().add("fxt-side-panel-title");
        xpathField.getStyleClass().add("fxt-xpath-field");
        HBox.setHgrow(xpathField, Priority.ALWAYS);
        Button runXPath = button("Run", "bi-lightning-charge", this::runXPath);
        xpathField.setOnAction(e -> runXPath());
        updatePathMode();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updatePathMode());

        // Saved queries (reuses FavoritesService storage).
        Button saveQuery = button("Save Query", "bi-save", this::saveCurrentQuery);
        savedQueriesMenu = new MenuButton("Saved");
        savedQueriesMenu.setGraphic(icon("bi-collection"));
        savedQueriesMenu.getStyleClass().add("fxt-tool-button");
        savedQueriesMenu.setOnShowing(e -> refreshSavedQueriesMenu());

        Label resultLabel = new Label("RESULT");
        resultLabel.getStyleClass().add("fxt-side-panel-title");
        output.setEditable(false);
        output.getStyleClass().add("fxt-transform-output");

        // Result area: a Text view (the serialized output) and a Table view (an XQuery result
        // sequence projected into rows/columns), switched by a Text/Table toggle.
        ToggleGroup resultMode = new ToggleGroup();
        textToggle.setToggleGroup(resultMode);
        tableToggle.setToggleGroup(resultMode);
        textToggle.setSelected(true);
        textToggle.getStyleClass().add("fxt-tool-button");
        tableToggle.getStyleClass().add("fxt-tool-button");
        resultMode.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                oldT.setSelected(true); // keep one always selected
            } else {
                updateResultView();
            }
        });
        resultTable.setPlaceholder(new Label("Run an XQuery returning a sequence to see a table."));
        StackPane resultStack = new StackPane(output, resultTable);
        VBox.setVgrow(resultStack, Priority.ALWAYS);
        Region resultSpacer = new Region();
        HBox.setHgrow(resultSpacer, Priority.ALWAYS);
        statsLabel.getStyleClass().add("fxt-placeholder-text");
        HBox resultHeader = new HBox(8, resultLabel, statsLabel, resultSpacer, textToggle, tableToggle);
        resultHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        updateResultView();

        Label xqueryLabel = new Label("XQUERY");
        xqueryLabel.getStyleClass().add("fxt-side-panel-title");
        xqueryArea.setPromptText("for $x in /root/item return string($x)");
        xqueryArea.setPrefRowCount(4);
        xqueryArea.getStyleClass().add("fxt-xpath-field");
        Button runXQuery = button("Run XQuery", "bi-braces", this::runXQuery);

        getChildren().addAll(title,
                xsltRow(setXslt), SidePanelLayout.fill(transform), livePreview, xsltStatus,
                paramsLabel, paramRows, SidePanelLayout.fill(addParam),
                outputFormatLabel, outputFormat,
                pathLabel, new HBox(6, xpathField, runXPath),
                new HBox(6, saveQuery, savedQueriesMenu),
                xqueryLabel, xqueryArea, SidePanelLayout.fill(runXQuery),
                resultHeader, resultStack);
    }

    /** Shows either the Text output or the Table view per the toggle selection. */
    private void updateResultView() {
        boolean table = tableToggle.isSelected();
        output.setVisible(!table);
        output.setManaged(!table);
        resultTable.setVisible(table);
        resultTable.setManaged(table);
    }

    /** Rebuilds the result table from a tabular XQuery result (columns + string rows). */
    private void populateResultTable(XQueryTableRunner.XQueryTable table) {
        resultTable.getColumns().clear();
        resultTable.getItems().clear();
        if (table.isError()) {
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

    /** Executes the XQuery against the active XML (async), honouring params + output format. */
    public void runXQuery() {
        if (editorHost.getActiveDocument().isEmpty()) {
            output.setText("No document open.");
            return;
        }
        String xquery = xqueryArea.getText();
        if (xquery == null || xquery.isBlank()) {
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        Map<String, Object> params = collectParameters();
        OutputFormat format = outputFormat.getValue() != null ? outputFormat.getValue() : OutputFormat.XML;
        output.setText("Running…");
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String result = TransformRunner.runXQuery(xml, xquery, params, format);
            XQueryTableRunner.XQueryTable table = XQueryTableRunner.run(xml, xquery);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Platform.runLater(() -> {
                output.setText(result);
                statsLabel.setText(statsText(result, elapsedMs));
                populateResultTable(table);
                // Prefer the table view when the result is a non-empty sequence; otherwise show text.
                (!table.isError() && !table.isEmpty() ? tableToggle : textToggle).setSelected(true);
            });
        });
    }

    /** Sets the XQuery text (for tests/observers). */
    public void setXQuery(String xquery) {
        xqueryArea.setText(xquery);
    }

    /** @return the current result-table column headers (for tests/observers). */
    public List<String> getResultColumns() {
        return resultTable.getColumns().stream().map(TableColumn::getText).toList();
    }

    /** @return the current result-table row count (for tests/observers). */
    public int getResultRowCount() {
        return resultTable.getItems().size();
    }

    /** @return {@code true} if the Table view (not the Text view) is currently shown. */
    public boolean isResultTableShown() {
        return tableToggle.isSelected();
    }

    /** Adds an XSLT parameter row (used by the "Add Parameter" button and tests). */
    public void addParameter(String name, String value) {
        paramRows.getChildren().add(new ParamRow(name, value));
    }

    /** Collects the non-blank parameter rows into a name→value map. */
    Map<String, Object> collectParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        for (var node : paramRows.getChildren()) {
            if (node instanceof ParamRow row && !row.name().isBlank()) {
                params.put(row.name(), row.value());
            }
        }
        return params;
    }

    /** Sets the output format (also from the combo); used by tests. */
    public void setOutputFormat(OutputFormat format) {
        outputFormat.setValue(format);
    }

    /** Sets the stylesheet used by {@link #transform()} (also from the file chooser). */
    public void setXsltFile(File file) {
        this.xsltFile = file;
        xsltStatus.setText(file != null ? "XSLT: " + file.getName() : "XSLT: none");
        if (file != null) {
            try {
                org.fxt.freexmltoolkit.di.ServiceRegistry
                        .get(org.fxt.freexmltoolkit.service.PropertiesService.class).addRecentXsltFile(file);
            } catch (Throwable ignored) {
                // properties service unavailable — no recent-files persistence
            }
        }
    }

    private HBox xsltRow(Button setXslt) {
        HBox.setHgrow(setXslt, Priority.ALWAYS);
        setXslt.setMaxWidth(Double.MAX_VALUE);
        HBox row = new HBox(6, setXslt, recentXsltMenu);
        return row;
    }

    /** Rebuilds the Recent XSLT menu from the persisted recent-stylesheet list. */
    public void refreshRecentXsltMenu() {
        recentXsltMenu.getItems().clear();
        java.util.List<File> recent;
        try {
            recent = org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).getRecentXsltFiles();
        } catch (Throwable t) {
            recent = java.util.List.of();
        }
        for (File file : recent) {
            MenuItem item = new MenuItem(file.getName());
            item.setOnAction(e -> setXsltFile(file));
            recentXsltMenu.getItems().add(item);
        }
        recentXsltMenu.setDisable(recentXsltMenu.getItems().isEmpty());
        if (!recentXsltMenu.getItems().isEmpty()) {
            MenuItem clear = new MenuItem("Clear recent");
            clear.setOnAction(e -> {
                try {
                    org.fxt.freexmltoolkit.di.ServiceRegistry
                            .get(org.fxt.freexmltoolkit.service.PropertiesService.class).clearRecentXsltFiles();
                } catch (Throwable ignored) {
                    // ignore
                }
                refreshRecentXsltMenu();
            });
            recentXsltMenu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
            recentXsltMenu.getItems().add(clear);
        }
    }

    /** @return the recent-XSLT file names currently in the menu (for tests/observers). */
    public java.util.List<String> recentXsltNames() {
        return recentXsltMenu.getItems().stream()
                .filter(i -> !(i instanceof javafx.scene.control.SeparatorMenuItem))
                .map(MenuItem::getText)
                .filter(t -> !"Clear recent".equals(t))
                .toList();
    }

    /** Enables/disables XSLT live preview (for tests/observers). */
    public void setLivePreview(boolean enabled) {
        livePreview.setSelected(enabled);
        scheduleLivePreview();
    }

    /** Schedules a debounced live re-transform when live preview is on and a stylesheet is set. */
    private void scheduleLivePreview() {
        if (livePreview.isSelected() && xsltFile != null) {
            liveDebounce.playFromStart();
        }
    }

    /** Transforms the active XML with the selected XSLT (async). */
    public void transform() {
        if (xsltFile == null) {
            output.setText("Select an XSLT stylesheet first.");
            return;
        }
        if (editorHost.getActiveDocument().isEmpty()) {
            output.setText("No document open.");
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        File xslt = xsltFile;
        Map<String, Object> params = collectParameters();
        OutputFormat format = outputFormat.getValue() != null ? outputFormat.getValue() : OutputFormat.XML;
        output.setText("Transforming…");
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String result;
            try {
                String xsltContent = Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                result = TransformRunner.xsltTransform(xml, xsltContent, params, format);
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String finalResult = result;
            Platform.runLater(() -> {
                output.setText(finalResult);
                statsLabel.setText(statsText(finalResult, elapsedMs));
                textToggle.setSelected(true);
            });
        });
    }

    /** @return a compact "N ms · M chars" stat, or "error" for a failed run. */
    private static String statsText(String result, long elapsedMs) {
        if (result == null || result.startsWith("ERROR")) {
            return "error";
        }
        return elapsedMs + " ms · " + result.length() + " chars";
    }

    /** @return the last transform/query stats text (for tests/observers). */
    public String getTransformStats() {
        return statsLabel.getText();
    }

    /** Evaluates the XPath field against the active XML (async). */
    public void runXPath() {
        if (editorHost.getActiveDocument().isEmpty()) {
            output.setText("No document open.");
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        String path = xpathField.getText();
        if (path == null || path.isBlank()) {
            return;
        }
        boolean json = isJsonActive();
        output.setText("Running…");
        FxtGui.executorService.submit(() -> {
            String result = json ? TransformRunner.runJsonPath(content, path)
                    : TransformRunner.runXPath(content, path);
            Platform.runLater(() -> { output.setText(result); textToggle.setSelected(true); });
        });
    }

    private boolean isJsonActive() {
        return editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
    }

    private void updatePathMode() {
        boolean json = isJsonActive();
        pathLabel.setText(json ? "JSONPATH" : "XPATH");
        xpathField.setPromptText(json ? "$.root.element" : "/root/element");
    }

    /** @return the current output text (for tests/observers). */
    public String getOutputText() {
        return output.getText();
    }

    /** Sets the XPath expression (for tests/observers). */
    public void setXPathExpression(String expression) {
        xpathField.setText(expression);
    }

    /** @return the current query expression text. */
    public String getQueryText() {
        return xpathField.getText();
    }

    /** Loads a saved query file's content into the query field. */
    public void loadQueryFromFile(File file) {
        try {
            xpathField.setText(Files.readString(file.toPath(), StandardCharsets.UTF_8).strip());
        } catch (Exception e) {
            output.setText("Could not load query: " + e.getMessage());
        }
    }

    /** Saves the current query expression under a chosen name (reuses FavoritesService). */
    public void saveCurrentQuery() {
        String expression = xpathField.getText();
        if (expression == null || expression.isBlank()) {
            output.setText("Enter a query expression to save.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Save Query");
        dialog.setHeaderText(null);
        dialog.setContentText("Query name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                return;
            }
            File saved = FavoritesService.getInstance().saveXPathQuery(name.trim(), expression);
            output.setText(saved != null ? "Saved query: " + saved.getName() : "Could not save query.");
        });
    }

    private void refreshSavedQueriesMenu() {
        savedQueriesMenu.getItems().clear();
        var files = FavoritesService.getInstance().getSavedXPathQueries();
        if (files.isEmpty()) {
            MenuItem empty = new MenuItem("(no saved queries)");
            empty.setDisable(true);
            savedQueriesMenu.getItems().add(empty);
            return;
        }
        for (File file : files) {
            MenuItem item = new MenuItem(file.getName().replaceFirst("\\.xpath$", ""));
            item.setOnAction(e -> loadQueryFromFile(file));
            savedQueriesMenu.getItems().add(item);
        }
    }

    private IconifyIcon icon(String literal) {
        IconifyIcon graphic = new IconifyIcon(literal);
        graphic.setIconSize(16);
        return graphic;
    }

    private void chooseXslt() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSLT");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSLT", "*.xsl", "*.xslt"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setXsltFile(file);
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

    /** A single editable XSLT parameter (name, value) with a remove button. */
    private final class ParamRow extends HBox {
        private final TextField nameField = new TextField();
        private final TextField valueField = new TextField();

        ParamRow(String name, String value) {
            super(6);
            nameField.setPromptText("name");
            nameField.setText(name);
            valueField.setPromptText("value");
            valueField.setText(value);
            HBox.setHgrow(valueField, Priority.ALWAYS);
            Button remove = button("", "bi-x-circle", () -> paramRows.getChildren().remove(this));
            getChildren().addAll(nameField, valueField, remove);
        }

        String name() {
            return nameField.getText() == null ? "" : nameField.getText().trim();
        }

        String value() {
            return valueField.getText() == null ? "" : valueField.getText();
        }
    }
}
