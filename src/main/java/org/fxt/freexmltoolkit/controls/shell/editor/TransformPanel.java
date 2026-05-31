package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

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
 * XHTML / TEXT / JSON), XPath/JSONPath evaluation, and an XQuery console. Live
 * preview is a follow-up increment.
 */
public class TransformPanel extends VBox {

    private final EditorHost editorHost;
    private final TextArea output = new TextArea();
    private final TextArea xqueryArea = new TextArea();
    private final TextField xpathField = new TextField();
    private final Label pathLabel = new Label("XPATH");
    private final Label xsltStatus = new Label("XSLT: none");
    private final VBox paramRows = new VBox(4);
    private final ComboBox<OutputFormat> outputFormat = new ComboBox<>();
    private final MenuButton savedQueriesMenu;
    private File xsltFile;

    public TransformPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-side-panel-content");

        Label title = new Label("TRANSFORM");
        title.getStyleClass().add("fxt-side-panel-title");

        Button setXslt = button("Set XSLT…", "bi-file-earmark-code", this::chooseXslt);
        Button transform = button("Transform", "bi-arrow-repeat", this::transform);
        xsltStatus.getStyleClass().add("fxt-placeholder-text");

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
        VBox.setVgrow(output, Priority.ALWAYS);

        Label xqueryLabel = new Label("XQUERY");
        xqueryLabel.getStyleClass().add("fxt-side-panel-title");
        xqueryArea.setPromptText("for $x in /root/item return string($x)");
        xqueryArea.setPrefRowCount(4);
        xqueryArea.getStyleClass().add("fxt-xpath-field");
        Button runXQuery = button("Run XQuery", "bi-braces", this::runXQuery);

        getChildren().addAll(title, new HBox(6, setXslt, transform), xsltStatus,
                paramsLabel, paramRows, addParam,
                outputFormatLabel, outputFormat,
                pathLabel, new HBox(6, xpathField, runXPath),
                new HBox(6, saveQuery, savedQueriesMenu),
                xqueryLabel, xqueryArea, new HBox(6, runXQuery),
                resultLabel, output);
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
            String result = TransformRunner.runXQuery(xml, xquery, params, format);
            Platform.runLater(() -> output.setText(result));
        });
    }

    /** Sets the XQuery text (for tests/observers). */
    public void setXQuery(String xquery) {
        xqueryArea.setText(xquery);
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
            String result;
            try {
                String xsltContent = Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                result = TransformRunner.xsltTransform(xml, xsltContent, params, format);
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            String finalResult = result;
            Platform.runLater(() -> output.setText(finalResult));
        });
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
            Platform.runLater(() -> output.setText(result));
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
