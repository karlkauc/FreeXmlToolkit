package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;

/**
 * The Transform activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Transform (XSLT)" (node 46:48): collapsible STYLESHEET
 * and INPUT sections with "Change" links, a segmented OUTPUT METHOD control, a
 * PARAMETERS section (name = value rows), and a primary "Run Transform" button.
 * XPath/JSONPath and XQuery consoles live in collapsed sections below; secondary
 * toggles (live preview, watch file, profile, trace, auto-open result tab) and
 * the Debug/Batch tools live in the header's overflow (⋮) menu.
 * <p>
 * Results are shown in the {@link TransformOutputPanel} docked below the editor
 * (source on top, output underneath — per the mockup). Transformation runs off
 * the UI thread via {@link TransformRunner}.
 */
public class TransformPanel extends VBox {

    private final EditorHost editorHost;
    private final TransformOutputPanel out;
    private final TextArea xqueryArea = new TextArea();
    private final TextField xpathField = new TextField();
    private final Label pathLabel = new Label("XPATH");
    private final Label xsltName = new Label("none");
    private final Label inputName = new Label("none");
    private final VBox paramRows = new VBox(4);
    private final ToggleGroup formatGroup = new ToggleGroup();
    private final MenuButton savedQueriesMenu;
    private final MenuButton recentXsltMenu = new MenuButton();
    private final MenuButton overflowMenu = new MenuButton();
    private final CheckMenuItem livePreview = new CheckMenuItem("Live preview");
    private final CheckMenuItem watchXslt = new CheckMenuItem("Watch stylesheet file");
    private final CheckMenuItem profileCheck = new CheckMenuItem("Profile run");
    private final CheckMenuItem traceCheck = new CheckMenuItem("Trace run");
    private final CheckMenuItem autoOpenResultTab = new CheckMenuItem("Auto-open result tab");
    private final PauseTransition liveDebounce = new PauseTransition(Duration.millis(600));
    private File xsltFile;
    private long lastXsltModified;
    /** A fixed input file overriding the active editor as the transform source, if set. */
    private File inputOverride;
    /** The source document of the latest transform (so a re-run from the result tab works). */
    private OpenDocument sourceDocument;

    // --- favorites browsing (◀/▶ through XSLT / XML favorites, optionally folder-scoped) ---
    /** Star menu listing XSLT favorites (grouped by folder) for the STYLESHEET row. */
    private final MenuButton xsltFavMenu = new MenuButton();
    /** Star menu listing XML favorites (grouped by folder) for the INPUT row. */
    private final MenuButton inputFavMenu = new MenuButton();
    /** The current browse list for the stylesheet axis, and the cursor into it (-1 = none). */
    private List<File> xsltBrowse = List.of();
    private int xsltBrowseIdx = -1;
    /** The current browse list for the input axis, and the cursor into it (-1 = none). */
    private List<File> inputBrowse = List.of();
    private int inputBrowseIdx = -1;
    private final Button prevXsltBtn = navButton("bi-chevron-left", this::prevXslt);
    private final Button nextXsltBtn = navButton("bi-chevron-right", this::nextXslt);
    private final Button prevInputBtn = navButton("bi-chevron-left", this::prevInput);
    private final Button nextInputBtn = navButton("bi-chevron-right", this::nextInput);
    private final Label xsltPos = new Label();
    private final Label inputPos = new Label();

    public TransformPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        this.out = editorHost.transformOutputPanel();
        getStyleClass().add("fxt-transform-panel");

        // --- header: TRANSFORM ........ ⋮ ---------------------------------
        // Carries the shared side-panel-title class too: the shell convention (and
        // UnifiedShellViewTest) identify the active panel's title by it.
        Label title = new Label("TRANSFORM");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        overflowMenu.setId("transform-overflow");
        overflowMenu.setGraphic(icon("bi-three-dots-vertical", 15));
        overflowMenu.getStyleClass().add("fxt-vp-overflow");
        buildOverflowMenu();
        HBox header = new HBox(title, headerSpacer, overflowMenu);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- STYLESHEET -----------------------------------------------------
        xsltName.getStyleClass().addAll("fxt-vp-source-name", "fxt-vp-source-none");
        recentXsltMenu.setGraphic(icon("bi-clock-history", 13));
        recentXsltMenu.getStyleClass().add("fxt-vp-source-fav");
        recentXsltMenu.setOnShowing(e -> refreshRecentXsltMenu());
        xsltFavMenu.setGraphic(icon("bi-star", 13));
        xsltFavMenu.getStyleClass().add("fxt-vp-source-fav");
        xsltFavMenu.setOnShowing(e -> refreshXsltFavMenu());
        xsltPos.getStyleClass().add("fxt-vp-browse-pos");
        HBox xsltRow = sourceRow("bi-arrow-repeat", xsltName, this::chooseXslt,
                recentXsltMenu, xsltFavMenu, prevXsltBtn, xsltPos, nextXsltBtn);
        updateXsltPos();
        HBox stylesheetHeader = SidePanelLayout.sectionHeader(new Label("STYLESHEET"), xsltRow);

        // --- INPUT ------------------------------------------------------------
        inputName.getStyleClass().add("fxt-vp-source-name");
        ContextMenu inputMenu = new ContextMenu(
                menuItem("Select XML file…", this::chooseInputFile),
                menuItem("Use active editor", () -> setInputOverride(null)));
        inputFavMenu.setGraphic(icon("bi-star", 13));
        inputFavMenu.getStyleClass().add("fxt-vp-source-fav");
        inputFavMenu.setOnShowing(e -> refreshInputFavMenu());
        inputPos.getStyleClass().add("fxt-vp-browse-pos");
        HBox inputRow = sourceRow("bi-code-slash", inputName, () ->
                inputMenu.show(inputName, javafx.geometry.Side.BOTTOM, 0, 0),
                inputFavMenu, prevInputBtn, inputPos, nextInputBtn);
        updateInputPos();
        HBox inputHeader = SidePanelLayout.sectionHeader(new Label("INPUT"), inputRow);
        refreshInputName();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshInputName());

        // --- OUTPUT METHOD -----------------------------------------------------
        VBox formatGrid = buildFormatGrid();
        HBox outputHeader = SidePanelLayout.sectionHeader(new Label("OUTPUT METHOD"), formatGrid);

        // --- PARAMETERS ----------------------------------------------------------
        Hyperlink addParam = new Hyperlink("Add parameter");
        addParam.getStyleClass().add("fxt-vp-change");
        addParam.setOnAction(e -> addParameter("", ""));
        VBox paramsBox = new VBox(4, paramRows, addParam);
        paramsBox.getStyleClass().add("fxt-tp-params");
        HBox paramsHeader = SidePanelLayout.sectionHeader(new Label("PARAMETERS"), paramsBox);

        // --- Run Transform ----------------------------------------------------
        Button run = new Button("Run Transform", icon("bi-play-fill", 14));
        run.setId("transform-run");
        run.getStyleClass().add("fxt-primary-button");
        run.setMaxWidth(Double.MAX_VALUE);
        run.setOnAction(e -> transform());
        VBox runBox = new VBox(run);
        runBox.getStyleClass().add("fxt-vp-run-box");
        run.prefWidthProperty().bind(runBox.widthProperty());

        // --- XPATH / JSONPATH (collapsed) -------------------------------------
        xpathField.getStyleClass().add("fxt-xpath-field");
        HBox.setHgrow(xpathField, Priority.ALWAYS);
        Button runXPath = button("Run", "bi-lightning-charge", this::runXPath);
        xpathField.setOnAction(e -> runXPath());
        updatePathMode();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updatePathMode());
        Button saveQuery = button("Save Query", "bi-save", this::saveCurrentQuery);
        savedQueriesMenu = new MenuButton("Saved");
        savedQueriesMenu.setGraphic(icon("bi-collection", 16));
        savedQueriesMenu.getStyleClass().add("fxt-tool-button");
        savedQueriesMenu.setOnShowing(e -> refreshSavedQueriesMenu());
        VBox xpathBox = new VBox(6,
                new HBox(6, xpathField, runXPath),
                new HBox(6, saveQuery, savedQueriesMenu));
        xpathBox.getStyleClass().add("fxt-tp-section-body");
        HBox xpathHeader = SidePanelLayout.sectionHeader(true, pathLabel, xpathBox);

        // --- XQUERY (collapsed) -------------------------------------------------
        xqueryArea.setPromptText("for $x in /root/item return string($x)");
        xqueryArea.setPrefRowCount(4);
        xqueryArea.getStyleClass().add("fxt-xpath-field");
        Button runXQuery = button("Run XQuery", "bi-braces", this::runXQuery);
        MenuButton examplesMenu = new MenuButton("Examples");
        examplesMenu.getStyleClass().add("fxt-tool-button");
        examplesMenu.getItems().addAll(
                exampleItem("Simple", "simple"),
                exampleItem("FLWOR", "flwor"),
                exampleItem("HTML report", "html"),
                exampleItem("Data-quality check", "dq"));
        VBox xqueryBox = new VBox(6, xqueryArea, new HBox(6, runXQuery, examplesMenu));
        xqueryBox.getStyleClass().add("fxt-tp-section-body");
        HBox xqueryHeader = SidePanelLayout.sectionHeader(true, new Label("XQUERY"), xqueryBox);

        // Live preview: re-run the XSLT transform shortly after the active document
        // changes (typing / tab switch), when enabled and a stylesheet is set.
        // Never re-transform the transform-result tab itself (feedback loop).
        liveDebounce.setOnFinished(e -> {
            if (livePreview.isSelected() && xsltFile != null && isTransformableDocumentActive()) {
                transform();
            }
        });
        livePreview.setOnAction(e -> scheduleLivePreview());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> scheduleLivePreview());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> scheduleLivePreview());

        // Watch the stylesheet file: re-transform when it changes on disk (e.g. edited externally).
        javafx.animation.Timeline xsltWatch = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.millis(1500), e -> {
                    if (watchXslt.isSelected() && xsltFile != null && pollXsltChanged()
                            && isTransformableDocumentActive()) {
                        transform();
                    }
                }));
        xsltWatch.setCycleCount(javafx.animation.Animation.INDEFINITE);
        xsltWatch.play();
        // Stop polling once this panel leaves the scene (a new TransformPanel is created
        // on each activity switch) so the Timeline does not leak across switches.
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                xsltWatch.stop();
            }
        });

        // The sections scroll on short windows (the OUTPUT result lives in the
        // editor-docked TransformOutputPanel, not in this side panel).
        VBox controls = new VBox(
                stylesheetHeader, xsltRow,
                inputHeader, inputRow,
                outputHeader, formatGrid,
                paramsHeader, paramsBox,
                runBox,
                xpathHeader, xpathBox,
                xqueryHeader, xqueryBox);
        javafx.scene.control.ScrollPane controlsScroll = new javafx.scene.control.ScrollPane(controls);
        controlsScroll.setFitToWidth(true);
        controlsScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        controlsScroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(controlsScroll, Priority.ALWAYS);

        getChildren().addAll(header, controlsScroll);
    }

    /** Builds the ⋮ overflow menu (toggles and tools that are not part of the mockup's main flow). */
    private void buildOverflowMenu() {
        overflowMenu.getItems().addAll(
                livePreview, watchXslt,
                new SeparatorMenuItem(), profileCheck, traceCheck,
                new SeparatorMenuItem(), autoOpenResultTab,
                new SeparatorMenuItem(),
                menuItem("Debug XSLT…", this::startDebug),
                menuItem("Batch Transform…", this::openBatch));
    }

    /** The segmented OUTPUT METHOD control: all six choices in a 2×3 grid, one selected. */
    private VBox buildFormatGrid() {
        HBox row1 = formatRow(FormatChoice.AUTO, FormatChoice.XML, FormatChoice.HTML);
        HBox row2 = formatRow(FormatChoice.XHTML, FormatChoice.TEXT, FormatChoice.JSON);
        formatGroup.getToggles().getFirst().setSelected(true);
        // A segmented control always has exactly one active segment.
        formatGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                formatGroup.selectToggle(oldV);
            }
        });
        VBox grid = new VBox(2, row1, row2);
        grid.getStyleClass().add("fxt-tp-format-grid");
        return grid;
    }

    private HBox formatRow(FormatChoice... choices) {
        HBox row = new HBox(2);
        row.getStyleClass().add("fxt-seg-group");
        for (FormatChoice choice : choices) {
            ToggleButton toggle = new ToggleButton(choice.toString());
            toggle.setUserData(choice);
            toggle.setToggleGroup(formatGroup);
            toggle.getStyleClass().add("fxt-seg");
            toggle.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(toggle, Priority.ALWAYS);
            row.getChildren().add(toggle);
        }
        return row;
    }

    /** A source row: file-type icon · file name · (extras) · "Change" link. */
    private HBox sourceRow(String iconLiteral, Label nameLabel, Runnable changeAction,
                           javafx.scene.Node... extras) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Hyperlink change = new Hyperlink("Change");
        change.getStyleClass().add("fxt-vp-change");
        change.setOnAction(e -> changeAction.run());
        HBox row = new HBox(8, icon(iconLiteral, 15), nameLabel, spacer);
        row.getChildren().addAll(extras);
        row.getChildren().add(change);
        row.getStyleClass().add("fxt-vp-source-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ----- INPUT (active editor vs explicit file) ---------------------------

    private void chooseInputFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Input XML");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setInputOverride(file);
        }
    }

    /**
     * Sets (or clears, with {@code null}) the fixed input file used by
     * {@link #transform()} instead of the active editor document.
     */
    public void setInputOverride(File file) {
        this.inputOverride = file;
        refreshInputName();
    }

    /** Updates the INPUT row label: the override file, or the active document (live). */
    private void refreshInputName() {
        String name = inputOverride != null
                ? inputOverride.getName()
                : editorHost.getActiveDocument().map(OpenDocument::getDisplayName).orElse("none");
        inputName.setText(name);
        boolean none = inputOverride == null && editorHost.getActiveDocument().isEmpty();
        inputName.getStyleClass().remove("fxt-vp-source-none");
        if (none) {
            inputName.getStyleClass().add("fxt-vp-source-none");
        }
    }

    // ----- XQuery ------------------------------------------------------------

    /** Executes the XQuery against the active XML (async), honouring params + output format. */
    public void runXQuery() {
        if (editorHost.getActiveDocument().isEmpty()) {
            out.showError("No document open.");
            return;
        }
        String xquery = xqueryArea.getText();
        if (xquery == null || xquery.isBlank()) {
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        Map<String, Object> params = collectParameters();
        // AUTO defers to the XQuery's own output declaration (the engine detects it).
        OutputFormat format = chosenFormat(OutputFormat.XML);
        out.showPending("Running…");
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String result = TransformRunner.runXQuery(xml, xquery, params, format);
            XQueryTableRunner.XQueryTable table = XQueryTableRunner.run(xml, xquery);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Platform.runLater(() -> out.showXQueryResult(result, table, format, elapsedMs));
        });
    }

    /** Sets the XQuery text (for tests/observers). */
    public void setXQuery(String xquery) {
        xqueryArea.setText(xquery);
    }

    /** @return the current XQuery text (for tests/observers). */
    public String getXQueryText() {
        return xqueryArea.getText();
    }

    /** Inserts a built-in XQuery example by key (simple, flwor, html, dq). */
    public void insertXQueryExample(String key) {
        String example = switch (key) {
            case "simple" -> "for $x in //item return string($x)";
            case "flwor" -> "for $x in //item\norder by $x\nreturn <row>{string($x)}</row>";
            case "html" -> "<html><body><ul>{\n  for $x in //item return <li>{string($x)}</li>\n}</ul></body></html>";
            case "dq" -> "(: data-quality: items missing a value :)\nfor $x in //item[not(normalize-space())]\nreturn <missing>{name($x)}</missing>";
            default -> "for $x in //item return string($x)";
        };
        xqueryArea.setText(example);
    }

    private MenuItem exampleItem(String label, String key) {
        return menuItem(label, () -> insertXQueryExample(key));
    }

    // ----- parameters ----------------------------------------------------------

    /** Adds an XSLT parameter row (used by the "Add parameter" link and tests). */
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

    // ----- output format ----------------------------------------------------

    /** Sets the output format to a concrete value (selects the matching segment); used by tests. */
    public void setOutputFormat(OutputFormat format) {
        for (var toggle : formatGroup.getToggles()) {
            if (((FormatChoice) toggle.getUserData()).format == format) {
                formatGroup.selectToggle(toggle);
                return;
            }
        }
        formatGroup.selectToggle(formatGroup.getToggles().getFirst()); // Auto
    }

    /**
     * Resolves the segmented selection to a concrete format, falling back to
     * {@code autoFallback} for the Auto choice.
     */
    private OutputFormat chosenFormat(OutputFormat autoFallback) {
        var toggle = formatGroup.getSelectedToggle();
        FormatChoice choice = toggle != null ? (FormatChoice) toggle.getUserData() : FormatChoice.AUTO;
        return choice.format != null ? choice.format : autoFallback;
    }

    /**
     * Resolves the segmented selection for an XSLT run: Auto detects the format
     * from the stylesheet's {@code xsl:output} declaration.
     */
    private OutputFormat resolveXsltFormat(String xsltContent) {
        return chosenFormat(TransformRunner.detectXsltOutputFormat(xsltContent));
    }

    // ----- advanced tools (overflow menu) ------------------------------------

    /** Launches the interactive XSLT debugger for the active XML + selected stylesheet. */
    public void startDebug() {
        if (xsltFile == null) {
            out.showError("Select an XSLT stylesheet first.");
            return;
        }
        if (editorHost.getActiveDocument().isEmpty()) {
            out.showError("No document open.");
            return;
        }
        String xml = editorHost.getActiveText().orElse("");
        OutputFormat format;
        try {
            format = resolveXsltFormat(Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            format = chosenFormat(OutputFormat.XML);
        }
        editorHost.startXsltDebug(xsltFile, xml, collectParameters(), format);
    }

    /** Opens the batch-transform tool tab for the active stylesheet/XQuery. */
    public void openBatch() {
        String xqueryText = xqueryArea.getText();
        BatchTransformView view;
        if (xsltFile != null) {
            String xsltContent;
            try {
                xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                out.showError("Could not read stylesheet: " + e.getMessage());
                return;
            }
            view = new BatchTransformView(BatchTransformView.Kind.XSLT,
                    xsltContent, collectParameters(), resolveXsltFormat(xsltContent));
        } else if (xqueryText != null && !xqueryText.isBlank()) {
            view = new BatchTransformView(BatchTransformView.Kind.XQUERY,
                    xqueryText, collectParameters(), chosenFormat(OutputFormat.XML));
        } else {
            out.showError("Set an XSLT stylesheet or enter an XQuery first.");
            return;
        }
        editorHost.openToolTab("Batch", "bi-files", view);
    }

    // ----- stylesheet ----------------------------------------------------------

    /**
     * Polls whether the stylesheet file changed on disk since the last check (consuming the change).
     *
     * @return {@code true} once after each external modification of the current XSLT file
     */
    public boolean pollXsltChanged() {
        if (xsltFile == null || !xsltFile.isFile()) {
            return false;
        }
        long modified = xsltFile.lastModified();
        if (modified != lastXsltModified) {
            lastXsltModified = modified;
            return true;
        }
        return false;
    }

    /** Sets the stylesheet used by {@link #transform()} (also from the file chooser). */
    public void setXsltFile(File file) {
        this.xsltFile = file;
        this.lastXsltModified = file != null ? file.lastModified() : 0L;
        xsltName.setText(file != null ? file.getName() : "none");
        xsltName.getStyleClass().remove("fxt-vp-source-none");
        if (file == null) {
            xsltName.getStyleClass().add("fxt-vp-source-none");
        }
        if (file != null) {
            try {
                org.fxt.freexmltoolkit.di.ServiceRegistry
                        .get(org.fxt.freexmltoolkit.service.PropertiesService.class).addRecentXsltFile(file);
            } catch (Throwable ignored) {
                // properties service unavailable — no recent-files persistence
            }
        }
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
            recentXsltMenu.getItems().add(new SeparatorMenuItem());
            recentXsltMenu.getItems().add(clear);
        }
    }

    /** @return the recent-XSLT file names currently in the menu (for tests/observers). */
    public java.util.List<String> recentXsltNames() {
        return recentXsltMenu.getItems().stream()
                .filter(i -> !(i instanceof SeparatorMenuItem))
                .map(MenuItem::getText)
                .filter(t -> !"Clear recent".equals(t))
                .toList();
    }

    // ----- favorites browsing (◀/▶ through XSLT / XML favorites) ----------------

    /** A compact icon-only navigation button (◀/▶) for the source rows. */
    private Button navButton(String iconLiteral, Runnable action) {
        Button button = new Button(null, icon(iconLiteral, 13));
        button.getStyleClass().add("fxt-vp-browse-nav");
        button.setOnAction(e -> action.run());
        return button;
    }

    /** Rebuilds the STYLESHEET star menu: "All XSLT favorites" + one submenu per folder. */
    public void refreshXsltFavMenu() {
        populateFavMenu(xsltFavMenu, FileFavorite.FileType.XSLT, "All XSLT favorites", this::selectXslt);
    }

    /** Rebuilds the INPUT star menu: "All XML favorites" + one submenu per folder. */
    public void refreshInputFavMenu() {
        populateFavMenu(inputFavMenu, FileFavorite.FileType.XML, "All XML favorites", this::selectInput);
    }

    /**
     * Populates a star menu with favorites of {@code type}: a top-level "all" entry
     * that browses every favorite, followed by one submenu per folder. Each leaf item
     * establishes its sibling group as the browse list. Non-existent files are skipped.
     */
    private void populateFavMenu(MenuButton menu, FileFavorite.FileType type,
                                 String allLabel, java.util.function.BiConsumer<List<File>, File> onPick) {
        menu.getItems().clear();
        List<FileFavorite> favorites;
        try {
            favorites = FavoritesService.getInstance().getFavoritesByType(type).stream()
                    .filter(FileFavorite::fileExists)
                    .toList();
        } catch (Throwable t) {
            favorites = List.of();
        }
        if (favorites.isEmpty()) {
            menu.setDisable(true);
            return;
        }
        menu.setDisable(false);

        List<File> all = favorites.stream().map(f -> new File(f.getFilePath())).toList();
        MenuItem allItem = new MenuItem(allLabel + " (" + all.size() + ")");
        allItem.setOnAction(e -> onPick.accept(all, all.getFirst()));
        menu.getItems().add(allItem);

        // Group the remaining favorites by folder, building one submenu each.
        java.util.Map<String, List<FileFavorite>> byFolder = new java.util.LinkedHashMap<>();
        for (FileFavorite fav : favorites) {
            String folder = (fav.getFolderName() == null || fav.getFolderName().isBlank())
                    ? "Uncategorized" : fav.getFolderName();
            byFolder.computeIfAbsent(folder, k -> new java.util.ArrayList<>()).add(fav);
        }
        if (!byFolder.isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }
        for (var entry : byFolder.entrySet()) {
            Menu submenu = new Menu(entry.getKey());
            List<File> group = entry.getValue().stream().map(f -> new File(f.getFilePath())).toList();
            for (FileFavorite fav : entry.getValue()) {
                File file = new File(fav.getFilePath());
                MenuItem item = new MenuItem(fav.getName());
                item.setOnAction(e -> onPick.accept(group, file));
                submenu.getItems().add(item);
            }
            menu.getItems().add(submenu);
        }
    }

    /** Sets the stylesheet browse list to {@code group}, positions on {@code file}, and applies it (for tests/automation). */
    public void selectXslt(List<File> group, File file) {
        xsltBrowse = group;
        xsltBrowseIdx = Math.max(0, group.indexOf(file));
        applyXslt(file);
    }

    /** Sets the input browse list to {@code group}, positions on {@code file}, and applies it (for tests/automation). */
    public void selectInput(List<File> group, File file) {
        inputBrowse = group;
        inputBrowseIdx = Math.max(0, group.indexOf(file));
        applyInput(file);
    }

    /** Steps the stylesheet cursor (cyclically) and applies the new file. */
    public void nextXslt() { stepXslt(+1); }

    public void prevXslt() { stepXslt(-1); }

    private void stepXslt(int delta) {
        if (xsltBrowse.isEmpty()) {
            return;
        }
        xsltBrowseIdx = Math.floorMod(xsltBrowseIdx + delta, xsltBrowse.size());
        applyXslt(xsltBrowse.get(xsltBrowseIdx));
    }

    /** Steps the input cursor (cyclically) and applies the new file. */
    public void nextInput() { stepInput(+1); }

    public void prevInput() { stepInput(-1); }

    private void stepInput(int delta) {
        if (inputBrowse.isEmpty()) {
            return;
        }
        inputBrowseIdx = Math.floorMod(inputBrowseIdx + delta, inputBrowse.size());
        applyInput(inputBrowse.get(inputBrowseIdx));
    }

    private void applyXslt(File file) {
        recordAccess(file);
        setXsltFile(file);
        updateXsltPos();
        if (readyToAutoRun()) {
            transform();
        }
    }

    private void applyInput(File file) {
        recordAccess(file);
        setInputOverride(file);
        updateInputPos();
        if (readyToAutoRun()) {
            transform();
        }
    }

    private void recordAccess(File file) {
        try {
            FavoritesService.getInstance().recordAccess(file.getAbsolutePath());
        } catch (Throwable ignored) {
            // access tracking is best-effort
        }
    }

    /**
     * @return true when both transform axes are present — a stylesheet is selected and an
     * input is available (override file or active editor document) — so auto-run won't error.
     */
    boolean readyToAutoRun() {
        boolean hasInput = inputOverride != null || editorHost.getActiveDocument().isPresent();
        return xsltFile != null && hasInput;
    }

    /** Refreshes the STYLESHEET position label and shows/hides its ◀/▶ controls. */
    private void updateXsltPos() {
        updateBrowseControls(xsltBrowse, xsltBrowseIdx, xsltPos, prevXsltBtn, nextXsltBtn);
    }

    /** Refreshes the INPUT position label and shows/hides its ◀/▶ controls. */
    private void updateInputPos() {
        updateBrowseControls(inputBrowse, inputBrowseIdx, inputPos, prevInputBtn, nextInputBtn);
    }

    private void updateBrowseControls(List<File> browse, int idx, Label pos, Button prev, Button next) {
        boolean active = !browse.isEmpty();
        pos.setText(active ? (idx + 1) + " / " + browse.size() : "");
        for (var node : new javafx.scene.Node[]{pos, prev, next}) {
            node.setVisible(active);
            node.setManaged(active);
        }
    }

    /** @return the current stylesheet browse position as "i / n", or "" if not browsing (for tests). */
    public String xsltBrowsePosition() {
        return xsltPos.getText();
    }

    /** @return the current input browse position as "i / n", or "" if not browsing (for tests). */
    public String inputBrowsePosition() {
        return inputPos.getText();
    }

    /** @return the leaf labels (top-level + submenu items) of the XSLT star menu (for tests). */
    public List<String> xsltFavoriteNames() {
        return favMenuLeafNames(xsltFavMenu);
    }

    /** @return the leaf labels (top-level + submenu items) of the XML star menu (for tests). */
    public List<String> inputFavoriteNames() {
        return favMenuLeafNames(inputFavMenu);
    }

    private static List<String> favMenuLeafNames(MenuButton menu) {
        List<String> names = new java.util.ArrayList<>();
        for (MenuItem item : menu.getItems()) {
            if (item instanceof SeparatorMenuItem) {
                continue;
            }
            if (item instanceof Menu sub) {
                for (MenuItem leaf : sub.getItems()) {
                    names.add(leaf.getText());
                }
            } else {
                names.add(item.getText());
            }
        }
        return names;
    }

    /** Fires the "All …" entry of a star menu (for tests), establishing the full browse list. */
    void browseAllXsltFavorites() {
        refreshXsltFavMenu();
        if (!xsltFavMenu.getItems().isEmpty()) {
            xsltFavMenu.getItems().getFirst().fire();
        }
    }

    void browseAllInputFavorites() {
        refreshInputFavMenu();
        if (!inputFavMenu.getItems().isEmpty()) {
            inputFavMenu.getItems().getFirst().fire();
        }
    }

    /** @return the currently selected stylesheet file (for tests). */
    File currentXsltFile() {
        return xsltFile;
    }

    /** @return the current fixed input override file, or {@code null} (for tests). */
    File currentInputOverride() {
        return inputOverride;
    }

    // ----- transform ------------------------------------------------------------

    /** Enables/disables XSLT live preview (for tests/observers). */
    public void setLivePreview(boolean enabled) {
        livePreview.setSelected(enabled);
        scheduleLivePreview();
    }

    /** Enables/disables opening the result as an editor tab after each run (for tests/observers). */
    public void setAutoOpenResultTab(boolean enabled) {
        autoOpenResultTab.setSelected(enabled);
    }

    /** Schedules a debounced live re-transform when live preview is on and a stylesheet is set. */
    private void scheduleLivePreview() {
        if (livePreview.isSelected() && xsltFile != null) {
            liveDebounce.playFromStart();
        }
    }

    /**
     * Transforms the input (the override file if set, otherwise the active XML —
     * or the remembered source, when the result tab is active).
     */
    public void transform() {
        if (xsltFile == null) {
            out.showError("Select an XSLT stylesheet first.");
            return;
        }
        String xml;
        if (inputOverride != null) {
            try {
                xml = Files.readString(inputOverride.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                out.showError("Could not read input file: " + e.getMessage());
                return;
            }
        } else {
            OpenDocument active = editorHost.getActiveDocument().orElse(null);
            if (active == null) {
                out.showError("No document open.");
                return;
            }
            if (active == out.getResultDocument()) {
                // Re-run from the result tab: transform the original source again.
                java.util.Optional<String> sourceText = sourceDocument != null
                        ? editorHost.getDocumentText(sourceDocument) : java.util.Optional.empty();
                if (sourceText.isEmpty()) {
                    out.showError("Select the source document to transform.");
                    return;
                }
                xml = sourceText.get();
            } else {
                sourceDocument = active;
                xml = editorHost.getActiveText().orElse("");
            }
        }
        File xslt = xsltFile;
        Map<String, Object> params = collectParameters();
        boolean wantProfile = profileCheck.isSelected();
        boolean wantTrace = traceCheck.isSelected();
        OutputFormat chosen = chosenFormat(null); // null = Auto, resolved from the stylesheet below
        out.showPending("Transforming…");
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String result;
            String xsltContent = "";
            OutputFormat format = chosen != null ? chosen : OutputFormat.XML;
            try {
                xsltContent = Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                // Auto: detect the format from the stylesheet's xsl:output declaration.
                format = chosen != null ? chosen : TransformRunner.detectXsltOutputFormat(xsltContent);
                result = TransformRunner.xsltTransform(xml, xsltContent, params, format);
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            String finalResult = result;
            OutputFormat finalFormat = format;
            org.fxt.freexmltoolkit.service.XsltTransformationResult report =
                    (wantProfile || wantTrace) && !xsltContent.isBlank()
                            ? TransformRunner.transformForReport(xml, xsltContent, params, format)
                            : null;
            Platform.runLater(() -> {
                out.showTransformResult(finalResult, finalFormat, elapsedMs);
                if (!finalResult.startsWith("ERROR") && autoOpenResultTab.isSelected()) {
                    out.openResultInEditor();
                }
                if (report != null && report.isSuccess()) {
                    if (wantProfile) {
                        editorHost.openToolTab("Profile", "bi-speedometer2",
                                new org.fxt.freexmltoolkit.controls.shell.editor.debug.ProfileView(report));
                    }
                    if (wantTrace) {
                        editorHost.openToolTab("Trace", "bi-list-columns",
                                new org.fxt.freexmltoolkit.controls.shell.editor.debug.TraceView(report));
                    }
                }
            });
        });
    }

    /**
     * @return {@code true} if the automatic re-transform triggers (live preview, watch)
     * may run — an input override is set, or a document other than the transform-result
     * tab is active (guards against transforming their own output)
     */
    private boolean isTransformableDocumentActive() {
        return inputOverride != null
                || editorHost.getActiveDocument()
                        .map(document -> document != out.getResultDocument())
                        .orElse(false);
    }

    // ----- XPath / JSONPath -------------------------------------------------

    /** Evaluates the XPath field against the active XML (async). */
    public void runXPath() {
        if (editorHost.getActiveDocument().isEmpty()) {
            out.showError("No document open.");
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        String path = xpathField.getText();
        if (path == null || path.isBlank()) {
            return;
        }
        boolean json = isJsonActive();
        out.showPending("Running…");
        FxtGui.executorService.submit(() -> {
            long start = System.nanoTime();
            String result = json ? TransformRunner.runJsonPath(content, path)
                    : TransformRunner.runXPath(content, path);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Platform.runLater(() -> out.showQueryResult(result, elapsedMs));
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
            out.showError("Could not load query: " + e.getMessage());
        }
    }

    /** Saves the current query expression under a chosen name (reuses FavoritesService). */
    public void saveCurrentQuery() {
        String expression = xpathField.getText();
        if (expression == null || expression.isBlank()) {
            out.showError("Enter a query expression to save.");
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
            if (saved != null) {
                out.showQueryResult("Saved query: " + saved.getName(), 0);
            } else {
                out.showError("Could not save query.");
            }
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

    // ----- output-panel delegates (kept on the panel for tests/observers) ------

    /** @return the current output text shown in the docked OUTPUT panel. */
    public String getOutputText() {
        return out.getOutputText();
    }

    /** @return the last transform/query stats text ("N ms · M chars", or "error"). */
    public String getTransformStats() {
        return out.getStatsText();
    }

    /** @return the current result-table column headers (for tests/observers). */
    public List<String> getResultColumns() {
        return out.getResultColumns();
    }

    /** @return the current result-table row count (for tests/observers). */
    public int getResultRowCount() {
        return out.getResultRowCount();
    }

    /** @return {@code true} if the Table view (not the Text view) is currently shown. */
    public boolean isResultTableShown() {
        return out.isResultTableShown();
    }

    /** @return {@code true} while the rendered HTML preview is shown (for tests/observers). */
    public boolean isHtmlPreviewOpen() {
        return out.isHtmlPreviewShown();
    }

    /** Opens (or refreshes) the current result text as an untitled editor tab. */
    public void openResultInEditor() {
        out.openResultInEditor();
    }

    /** Opens the current result in the system browser (renders HTML output). */
    public void openInBrowser() {
        out.openInBrowser();
    }

    // ----- helpers --------------------------------------------------------------

    private void chooseXslt() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSLT");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSLT", "*.xsl", "*.xslt"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            setXsltFile(file);
        }
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon graphic = new IconifyIcon(literal);
        graphic.setIconSize(size);
        return graphic;
    }

    private Button button(String text, String icon, Runnable action) {
        Button button = new Button(text, icon(icon, 16));
        button.getStyleClass().add("fxt-tool-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    /** A single editable XSLT parameter: name = value, with a remove action (mockup style). */
    private final class ParamRow extends HBox {
        private final TextField nameField = new TextField();
        private final TextField valueField = new TextField();

        ParamRow(String name, String value) {
            super(6);
            setAlignment(Pos.CENTER_LEFT);
            nameField.setPromptText("name");
            nameField.setText(name);
            valueField.setPromptText("value");
            valueField.setText(value);
            Label eq = new Label("=");
            eq.getStyleClass().add("fxt-param-eq");
            HBox.setHgrow(valueField, Priority.ALWAYS);
            Button remove = new Button(null, icon("bi-x-circle", 14));
            remove.getStyleClass().add("fxt-sp-action");
            remove.setOnAction(e -> paramRows.getChildren().remove(this));
            getChildren().addAll(nameField, eq, valueField, remove);
        }

        String name() {
            return nameField.getText() == null ? "" : nameField.getText().trim();
        }

        String value() {
            return valueField.getText() == null ? "" : valueField.getText();
        }
    }

    /**
     * The OUTPUT METHOD choices: a concrete serialization format, or {@link #AUTO}
     * which defers to the stylesheet's own {@code xsl:output} declaration.
     */
    public enum FormatChoice {
        AUTO("Auto", null),
        XML("XML", OutputFormat.XML),
        HTML("HTML", OutputFormat.HTML),
        XHTML("XHTML", OutputFormat.XHTML),
        TEXT("Text", OutputFormat.TEXT),
        JSON("JSON", OutputFormat.JSON);

        private final String label;
        /** The concrete format, or {@code null} for {@link #AUTO}. */
        final OutputFormat format;

        FormatChoice(String label, OutputFormat format) {
            this.label = label;
            this.format = format;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
