package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
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
import org.fxt.freexmltoolkit.service.FavoritesService;
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

    // The snippets menu, refreshed from FavoritesService on showing.
    private final MenuButton snippetsMenu = new MenuButton("Snippets");

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

        Button saveSnippet = button("Save", "bi-save", this::saveSnippet);

        IconifyIcon snippetsIcon = new IconifyIcon("bi-folder2-open");
        snippetsIcon.setIconSize(16);
        snippetsMenu.setGraphic(snippetsIcon);
        snippetsMenu.getStyleClass().add("fxt-tool-button");
        snippetsMenu.setOnShowing(e -> refreshSnippetsMenu());

        HBox runRow = new HBox(6, run, saveSnippet, snippetsMenu);
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
    /** Monotonic run id; a late async result is dropped if a newer run has started (FX-thread only). */
    private int runGeneration;

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
        final int gen = ++runGeneration;
        FxtGui.executorService.submit(() -> {
            String result = json ? TransformRunner.runJsonPath(content, path)
                    : TransformRunner.runXPath(content, path);
            Platform.runLater(() -> {
                if (gen == runGeneration) {
                    resultsArea.setText(result);
                }
            });
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
        resultsArea.setText("Running…");
        final int gen = ++runGeneration;
        FxtGui.executorService.submit(() -> {
            String result = TransformRunner.runXQuery(xml, xquery, Map.of(), OutputFormat.XML);
            Platform.runLater(() -> {
                if (gen == runGeneration) {
                    resultsArea.setText(result);
                }
            });
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

    // ---------------------------------------------------------------------
    // Snippet save/load (XPath + XQuery), backed by FavoritesService.
    // ---------------------------------------------------------------------

    /** Whether the XQuery mode is currently active (otherwise XPath). */
    private boolean isXQueryMode() {
        return xqueryToggle.isSelected();
    }

    /** The current mode's expression text (XPath field or XQuery area). */
    private String currentExpression() {
        return isXQueryMode() ? xqueryArea.getText() : xpathField.getText();
    }

    /**
     * Prompts for a name and saves the current mode's expression via
     * {@link FavoritesService} (XPath snippets as {@code .xpath}, XQuery as
     * {@code .xquery}). Blank expressions are rejected with a hint in the results
     * pane and no prompt.
     */
    public void saveSnippet() {
        String expression = currentExpression();
        if (expression == null || expression.isBlank()) {
            resultsArea.setText("Enter a query to save.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Save Snippet");
        dialog.setHeaderText(null);
        dialog.setContentText("Snippet name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name.isBlank()) {
                return;
            }
            saveSnippetForTest(name.trim());
        });
    }

    /**
     * Saves the current mode's expression under {@code name} (no prompt), used by
     * {@link #saveSnippet()} and tests. XPath mode persists an {@code .xpath}
     * snippet; XQuery mode persists an {@code .xquery} snippet.
     *
     * @param name the snippet name (without extension)
     * @return the saved file, or {@code null} when the save failed
     */
    File saveSnippetForTest(String name) {
        FavoritesService favorites = FavoritesService.getInstance();
        File saved = isXQueryMode()
                ? favorites.saveXQueryQuery(name, xqueryArea.getText())
                : favorites.saveXPathQuery(name, xpathField.getText());
        resultsArea.setText(saved != null ? "Saved snippet: " + saved.getName() : "Could not save snippet.");
        return saved;
    }

    /**
     * Rebuilds the snippets menu from both XPath and XQuery saved queries. Each
     * item is labelled with the file name minus its extension and prefixed with
     * its kind ("XPath" / "XQuery"). A disabled placeholder is shown when nothing
     * is saved.
     */
    private void refreshSnippetsMenu() {
        snippetsMenu.getItems().clear();
        List<File> xpathFiles = FavoritesService.getInstance().getSavedXPathQueries();
        List<File> xqueryFiles = FavoritesService.getInstance().getSavedXQueryQueries();

        if (xpathFiles.isEmpty() && xqueryFiles.isEmpty()) {
            MenuItem empty = new MenuItem("(no saved snippets)");
            empty.setDisable(true);
            snippetsMenu.getItems().add(empty);
            return;
        }

        for (File file : xpathFiles) {
            snippetsMenu.getItems().add(snippetItem(file, false));
        }
        for (File file : xqueryFiles) {
            snippetsMenu.getItems().add(snippetItem(file, true));
        }
    }

    /** Builds a menu item that loads {@code file} as an XPath or XQuery snippet. */
    private MenuItem snippetItem(File file, boolean xquery) {
        String extension = xquery ? "\\.xquery$" : "\\.xpath$";
        String label = (xquery ? "XQuery: " : "XPath: ") + file.getName().replaceFirst(extension, "");
        IconifyIcon graphic = new IconifyIcon(xquery ? "bi-code-square" : "bi-slash-square");
        graphic.setIconSize(16);
        MenuItem item = new MenuItem(label, graphic);
        item.setOnAction(e -> loadSnippet(file, xquery));
        return item;
    }

    /**
     * Loads {@code file}'s text into the matching input, switching the mode to
     * match the snippet kind. Mirrors {@link TransformPanel#loadQueryFromFile(File)}.
     *
     * @param file    the saved snippet file
     * @param xquery  {@code true} to load into XQuery mode, {@code false} for XPath
     */
    void loadSnippet(File file, boolean xquery) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).strip();
            if (xquery) {
                xqueryToggle.setSelected(true);
                updateMode();
                xqueryArea.setText(content);
            } else {
                xpathToggle.setSelected(true);
                updateMode();
                xpathField.setText(content);
            }
        } catch (Exception e) {
            resultsArea.setText("Could not load snippet: " + e.getMessage());
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

    /** Test seam: whether XQuery mode is currently active. */
    boolean isXQueryModeForTest() {
        return isXQueryMode();
    }

    /** Test seam: the current XPath field text. */
    String getXPathTextForTest() {
        return xpathField.getText();
    }

    /** Test seam: the current XQuery area text. */
    String getXQueryTextForTest() {
        return xqueryArea.getText();
    }
}
