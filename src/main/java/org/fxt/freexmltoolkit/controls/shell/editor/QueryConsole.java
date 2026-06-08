package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * A bottom "Query Console" pane that runs XPath/XQuery against the active
 * document of an {@link EditorHost} and shows copyable results.
 * <p>
 * Internally a horizontal {@link SplitPane}: the left side hosts the query input
 * (an XPath single-line field or an XQuery multi-line area, switched by a mode
 * toggle) and a Run button; the right side hosts a read-only, selectable results
 * area plus a Copy button.
 * <p>
 * Execution mirrors {@link TransformPanel}: the active document text is read on
 * the FX thread, the engine ({@link TransformRunner}) runs off the FX thread via
 * {@link FxtGui#executorService}, and the result is published back with
 * {@link Platform#runLater(Runnable)}. When no document is open, Run is disabled
 * and the results pane reports "No document open.".
 */
public class QueryConsole extends Region {

    private final EditorHost editorHost;

    private final ToggleButton xpathToggle = new ToggleButton("XPath");
    private final ToggleButton xqueryToggle = new ToggleButton("XQuery");
    private final TextField xpathField = new TextField();
    private final TextArea xqueryArea = new TextArea();
    private final StackPane inputStack = new StackPane(xpathField, xqueryArea);
    private final TextArea resultsArea = new TextArea();

    // The Run button, created in buildInput() so the constructor can wire enablement.
    private Button runButton;

    public QueryConsole(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-query-console");

        SplitPane split = new SplitPane(buildInput(), buildResults());
        split.setDividerPositions(0.45);

        // Fill this Region with the SplitPane.
        getChildren().add(split);
        split.prefWidthProperty().bind(widthProperty());
        split.prefHeightProperty().bind(heightProperty());

        updateRunDisabled();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updateRunDisabled());
    }

    private Region buildInput() {
        Label title = new Label("QUERY");
        title.getStyleClass().add("fxt-side-panel-title");

        ToggleGroup modeGroup = new ToggleGroup();
        xpathToggle.setToggleGroup(modeGroup);
        xqueryToggle.setToggleGroup(modeGroup);
        xpathToggle.getStyleClass().add("fxt-tool-button");
        xqueryToggle.getStyleClass().add("fxt-tool-button");
        xpathToggle.setSelected(true);
        modeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null) {
                oldT.setSelected(true); // keep one mode always selected
            } else {
                updateMode();
            }
        });
        HBox modeRow = new HBox(6, xpathToggle, xqueryToggle);
        modeRow.setAlignment(Pos.CENTER_LEFT);

        xpathField.getStyleClass().add("fxt-xpath-field");
        xpathField.setPromptText("/root/element");
        xpathField.setOnAction(e -> run()); // Enter runs

        xqueryArea.getStyleClass().add("fxt-xpath-field");
        xqueryArea.setPromptText("for $x in /root/item return string($x)");
        xqueryArea.setPrefRowCount(4);

        VBox.setVgrow(inputStack, Priority.ALWAYS);
        updateMode();

        Button run = button("Run", "bi-play-fill", this::run);
        runButton = run;
        HBox runRow = new HBox(6, run);
        runRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, title, modeRow, inputStack, runRow);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("fxt-side-panel-content");
        return box;
    }

    private Region buildResults() {
        Label title = new Label("RESULTS");
        title.getStyleClass().add("fxt-side-panel-title");

        Button copy = button("Copy", "bi-clipboard", this::copyResults);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, title, spacer, copy);
        header.setAlignment(Pos.CENTER_LEFT);

        resultsArea.setEditable(false);
        resultsArea.setWrapText(false);
        resultsArea.getStyleClass().add("fxt-transform-output");
        VBox.setVgrow(resultsArea, Priority.ALWAYS);

        VBox box = new VBox(8, header, resultsArea);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("fxt-side-panel-content");
        return box;
    }

    /** Shows the XPath field in XPath mode and the XQuery area in XQuery mode. */
    private void updateMode() {
        boolean xquery = xqueryToggle.isSelected();
        xqueryArea.setVisible(xquery);
        xqueryArea.setManaged(xquery);
        xpathField.setVisible(!xquery);
        xpathField.setManaged(!xquery);
    }

    /** Enables/disables Run based on whether a document is currently open. */
    private void updateRunDisabled() {
        boolean noDoc = editorHost.getActiveDocument().isEmpty();
        if (runButton != null) {
            runButton.setDisable(noDoc);
        }
        if (noDoc) {
            resultsArea.setText("No document open.");
        }
    }

    /** Runs the current query (XPath or XQuery) against the active document. */
    public void run() {
        if (xqueryToggle.isSelected()) {
            runXQuery();
        } else {
            runXPath();
        }
    }

    /**
     * Evaluates the XPath field against the active document (async), mirroring
     * {@link TransformPanel#runXPath()}. Uses JSONPath for JSON documents.
     */
    private void runXPath() {
        if (editorHost.getActiveDocument().isEmpty()) {
            resultsArea.setText("No document open.");
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        String path = xpathField.getText();
        if (path == null || path.isBlank()) {
            return;
        }
        boolean json = isJsonActive();
        resultsArea.setText("Running…");
        FxtGui.executorService.submit(() -> {
            String result = json ? TransformRunner.runJsonPath(content, path)
                    : TransformRunner.runXPath(content, path);
            Platform.runLater(() -> resultsArea.setText(result));
        });
    }

    /**
     * Executes the XQuery area against the active document (async), mirroring
     * {@link TransformPanel#runXQuery()}: an empty external-variables map and the
     * default XML output format.
     */
    private void runXQuery() {
        if (editorHost.getActiveDocument().isEmpty()) {
            resultsArea.setText("No document open.");
            return;
        }
        String xquery = xqueryArea.getText();
        if (xquery == null || xquery.isBlank()) {
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        OutputFormat format = OutputFormat.XML;
        resultsArea.setText("Running…");
        FxtGui.executorService.submit(() -> {
            String result = TransformRunner.runXQuery(xml, xquery, params, format);
            Platform.runLater(() -> resultsArea.setText(result));
        });
    }

    private boolean isJsonActive() {
        return editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
    }

    /** Copies the full results text to the system clipboard. */
    private void copyResults() {
        ClipboardContent content = new ClipboardContent();
        content.putString(resultsArea.getText() == null ? "" : resultsArea.getText());
        Clipboard.getSystemClipboard().setContent(content);
    }

    private Button button(String text, String icon, Runnable action) {
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        Button button = new Button(text, graphic);
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    // ---------------------------------------------------------------------
    // Test seams (package-private): minimal accessors so the TestFX test can
    // drive the console and read its state without exposing internals publicly.
    // ---------------------------------------------------------------------

    /** Test seam: sets the XPath expression (switches to XPath mode). */
    void setXPath(String expression) {
        xpathToggle.setSelected(true);
        updateMode();
        xpathField.setText(expression);
    }

    /** Test seam: sets the XQuery expression (switches to XQuery mode). */
    void setXQuery(String expression) {
        xqueryToggle.setSelected(true);
        updateMode();
        xqueryArea.setText(expression);
    }

    /** Test seam: runs the current query (same as the Run button / Enter). */
    void runForTest() {
        run();
    }

    /** Test seam: the current results text. */
    String getResultsText() {
        return resultsArea.getText();
    }

    /** Test seam: whether the Run button is currently disabled. */
    boolean isRunDisabledForTest() {
        return runButton != null && runButton.isDisable();
    }
}
