package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.debug.BatchTransformView;
import org.fxt.freexmltoolkit.domain.FileFavorite;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

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
    @FXML private SourceRow xsltRow;
    @FXML private SourceRow inputRow;
    @FXML private Label xsltName;
    @FXML private Label inputName;
    @FXML private ContextMenu inputMenu;
    @FXML private javafx.scene.control.ComboBox<FormatChoice> formatChoice;
    @FXML private VBox paramsBox;
    @FXML private VBox paramRows;
    @FXML private VBox runBox;
    @FXML private Button debugButton;
    private final MenuButton recentXsltMenu = new MenuButton();
    @FXML private PanelActionList tools;
    @FXML private javafx.scene.control.CheckBox runAutomatically;
    @FXML private javafx.scene.control.CheckBox livePreview;
    @FXML private javafx.scene.control.CheckBox watchXslt;
    @FXML private javafx.scene.control.CheckBox profileCheck;
    @FXML private javafx.scene.control.CheckBox traceCheck;
    @FXML private javafx.scene.control.CheckBox autoOpenResultTab;
    private final PauseTransition liveDebounce = new PauseTransition(Duration.millis(600));
    /** Set on the FX thread, read by the stylesheet watch on a worker. */
    private volatile File xsltFile;
    private volatile long lastXsltModified;
    /** A stylesheet poll is running on a worker (the watch timer never stacks them). */
    private final java.util.concurrent.atomic.AtomicBoolean xsltPollRunning =
            new java.util.concurrent.atomic.AtomicBoolean();
    /** A fixed input file overriding the active editor as the transform source, if set. */
    private File inputOverride;
    /** The source document of the latest transform (so a re-run from the result tab works). */
    private OpenDocument sourceDocument;
    /** The last active document that can be an input (no stylesheet, no result); the input while a stylesheet tab is in front. */
    private OpenDocument lastInputDocument;
    @FXML private PanelProgress progress;
    @FXML private Button transformButton;

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

    /** Loads the layout from {@code /pages/panels/TransformPanel.fxml} and wires the data-driven parts. */
    public TransformPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        this.out = editorHost.transformOutputPanel();
        FxmlViews.load(this, "/pages/panels/TransformPanel.fxml");

        // --- STYLESHEET -----------------------------------------------------
        recentXsltMenu.setGraphic(icon("bi-clock-history", 13));
        recentXsltMenu.getStyleClass().add("fxt-vp-source-fav");
        recentXsltMenu.setOnShowing(e -> refreshRecentXsltMenu());
        xsltFavMenu.setGraphic(icon("bi-star", 13));
        xsltFavMenu.getStyleClass().add("fxt-vp-source-fav");
        xsltFavMenu.setOnShowing(e -> refreshXsltFavMenu());
        xsltPos.getStyleClass().add("fxt-vp-browse-pos");
        xsltPos.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // "2 / 5" never shrinks to "…"
        xsltRow.addExtras(recentXsltMenu, xsltFavMenu, prevXsltBtn, xsltPos, nextXsltBtn);
        // Drops behave like picking a favorite: record access, sync the browse position, load the
        // file into the editor, auto-run.
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xsltRow,
                org.fxt.freexmltoolkit.service.DragDropService.XSLT_EXTENSIONS, this::pickXslt);
        updateXsltPos();

        // --- INPUT ------------------------------------------------------------
        inputFavMenu.setGraphic(icon("bi-star", 13));
        inputFavMenu.getStyleClass().add("fxt-vp-source-fav");
        inputFavMenu.setOnShowing(e -> refreshInputFavMenu());
        inputPos.getStyleClass().add("fxt-vp-browse-pos");
        inputPos.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // "2 / 5" never shrinks to "…"
        inputRow.addExtras(inputFavMenu, prevInputBtn, inputPos, nextInputBtn);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(inputRow,
                org.fxt.freexmltoolkit.service.DragDropService.XML_EXTENSIONS, this::pickInput);
        updateInputPos();
        refreshInputName();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshInputName());

        // --- OUTPUT METHOD -----------------------------------------------------
        fillFormatChoice();

        // --- PARAMETERS ----------------------------------------------------------
        Button addParam = PanelActionList.inlineRow("Add parameter", "bi-plus-circle", () -> addParameter("", ""));
        addParam.setId("transform-add-parameter");
        paramsBox.getChildren().add(addParam);

        // --- Run Transform / Debug --------------------------------------------
        // (Run grows to fill the row; Debug keeps its natural width — both declared in FXML.)

        // Live preview: re-run the XSLT transform shortly after the active document
        // changes (typing / tab switch), when enabled and a stylesheet is set.
        // Never re-transform the transform-result tab itself (feedback loop).
        liveDebounce.setOnFinished(e -> {
            if (livePreview.isSelected() && xsltFile != null && isTransformableDocumentActive()) {
                transform(true);
            }
        });
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> scheduleLivePreview());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> scheduleLivePreview());

        // Watch the stylesheet file: re-transform when it changes on disk (e.g. edited externally).
        javafx.animation.Timeline xsltWatch = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.millis(1500), e -> {
                    // The stat runs off the FX thread (it can stall on a network drive), one at a time.
                    if (watchXslt.isSelected() && xsltFile != null && xsltPollRunning.compareAndSet(false, true)) {
                        File watched = xsltFile;
                        FxtGui.executorService.submit(() -> {
                            boolean changed;
                            try {
                                changed = pollXsltChanged();
                            } finally {
                                xsltPollRunning.set(false);
                            }
                            if (changed) {
                                Platform.runLater(() -> {
                                    if (watched.equals(xsltFile) && isTransformableDocumentActive()) {
                                        transform(true);
                                    }
                                });
                            }
                        });
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
    }

    @FXML
    public void onLivePreviewToggled() {
        scheduleLivePreview();
    }

    @FXML
    public void onChangeInput() {
        inputMenu.show(inputName, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    @FXML
    public void onUseActiveEditor() {
        setInputOverride(null);
    }

    @FXML
    public void onTransform() {
        transform();
    }

    @FXML
    public void onOpenExecutionStats() {
        editorHost.openExecutionStats();
    }

    /** @return the TOOLS rows' labels in display order (for tests/observers). */
    public List<String> toolLabels() {
        return tools.labels();
    }

    /** Fills the OUTPUT METHOD choice: Auto (from xsl:output) or a concrete format; Auto is the default. */
    private void fillFormatChoice() {
        formatChoice.getItems().setAll(FormatChoice.values());
        formatChoice.setValue(FormatChoice.AUTO);
        // A choice always has a value: Auto stands in for "nothing chosen".
        formatChoice.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                formatChoice.setValue(FormatChoice.AUTO);
            }
        });
    }

    // ----- INPUT (active editor vs explicit file) ---------------------------

    @FXML
    public void onChooseInputFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Input XML");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML", "*.xml"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            pickInput(file);
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
        if (inputOverride != null) {
            inputRow.showFile(inputOverride);
            return;
        }
        var input = inputDocument();
        inputRow.showName(input.map(OpenDocument::getDisplayName).orElse(null),
                input.map(d -> d.getPath() != null ? d.getPath() + " (active document)" : "Active document (unsaved)")
                        .orElse(null));
    }

    /**
     * The document a transform without an input file reads: the active document, unless that is a
     * stylesheet or the transform result — then the last active document that was a valid input
     * (picking a stylesheet opens it as a tab, which must not turn it into the input).
     */
    Optional<OpenDocument> inputDocument() {
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        if (active != null && isInputCandidate(active)) {
            lastInputDocument = active;
            return Optional.of(active);
        }
        if (lastInputDocument != null && isInputCandidate(lastInputDocument)
                && editorHost.getOpenDocuments().contains(lastInputDocument)) {
            return Optional.of(lastInputDocument);
        }
        lastInputDocument = null;
        return Optional.empty();
    }

    private boolean isInputCandidate(OpenDocument document) {
        return document != out.getResultDocument()
                && document.getFileType() != EditorFileType.XSLT
                && !isTheStylesheet(document);
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

    /** Sets the output format to a concrete value (Auto for {@code null}); used by tests. */
    public void setOutputFormat(OutputFormat format) {
        for (FormatChoice choice : FormatChoice.values()) {
            if (choice.format == format) {
                formatChoice.setValue(choice);
                return;
            }
        }
        formatChoice.setValue(FormatChoice.AUTO);
    }

    /** @return the OUTPUT METHOD choice currently selected (for tests/observers). */
    public FormatChoice getFormatChoice() {
        return formatChoice.getValue();
    }

    /**
     * Resolves the segmented selection to a concrete format, falling back to
     * {@code autoFallback} for the Auto choice.
     */
    private OutputFormat chosenFormat(OutputFormat autoFallback) {
        FormatChoice choice = formatChoice.getValue() != null ? formatChoice.getValue() : FormatChoice.AUTO;
        return choice.format != null ? choice.format : autoFallback;
    }

    /**
     * Resolves the segmented selection for an XSLT run: Auto detects the format
     * from the stylesheet's {@code xsl:output} declaration.
     */
    private OutputFormat resolveXsltFormat(String xsltContent) {
        return chosenFormat(TransformRunner.detectXsltOutputFormat(xsltContent));
    }

    // ----- advanced tools (TOOLS rows) ---------------------------------------

    /** Launches the interactive XSLT debugger for the active XML + selected stylesheet. */
    @FXML
    public void startDebug() {
        if (xsltFile == null) {
            out.showError("Select an XSLT stylesheet first.");
            return;
        }
        // Same input as Run Transform: the INPUT override file when one is set (drop, favourite,
        // ◀ / ▶ browsing), otherwise the active document.
        String xml;
        if (inputOverride != null) {
            try {
                xml = Files.readString(inputOverride.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                out.showFailure("Could not read input file: " + e.getMessage());
                return;
            }
        } else {
            if (editorHost.getActiveDocument().isEmpty()) {
                out.showError("No document open.");
                return;
            }
            xml = editorHost.getActiveText().orElse("");
        }
        OutputFormat format;
        try {
            format = resolveXsltFormat(Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            format = chosenFormat(OutputFormat.XML);
        }
        if (inputOverride != null) {
            editorHost.setDebugInputFile(inputOverride.toPath()); // else: matched to the active document
        }
        editorHost.startXsltDebug(xsltFile, xml, collectParameters(), format);
    }

    /**
     * Opens the batch-transform tool tab for the selected stylesheet, or - without one - for
     * the active XQuery document (queries are written in the editor / Query Console now).
     */
    @FXML
    public void openBatch() {
        String xqueryText = editorHost.getActiveDocument()
                .filter(d -> d.getFileType() == EditorFileType.XQUERY)
                .flatMap(d -> editorHost.getActiveText())
                .orElse(null);
        BatchTransformView view;
        if (xsltFile != null) {
            String xsltContent;
            try {
                xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                out.showFailure("Could not read stylesheet: " + e.getMessage());
                return;
            }
            view = new BatchTransformView(BatchTransformView.Kind.XSLT,
                    xsltContent, collectParameters(), resolveXsltFormat(xsltContent));
        } else if (xqueryText != null && !xqueryText.isBlank()) {
            view = new BatchTransformView(BatchTransformView.Kind.XQUERY,
                    xqueryText, collectParameters(), chosenFormat(OutputFormat.XML));
        } else {
            out.showError("Set an XSLT stylesheet, or open an XQuery document, first.");
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
        File file = xsltFile;
        if (file == null || !file.isFile()) {
            return false;
        }
        long modified = file.lastModified();
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
        xsltRow.showFile(file);
        refreshInputName(); // the stylesheet is never the input
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
            item.setOnAction(e -> pickXslt(file));
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
    /** Fires the Recent XSLT entry named {@code name}, as a click on it would (tests). */
    void fireRecentXslt(String name) {
        refreshRecentXsltMenu();
        recentXsltMenu.getItems().stream().filter(i -> name.equals(i.getText())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no recent stylesheet " + name)).fire();
    }

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
        pickXslt(file);
    }

    /** Sets the input browse list to {@code group}, positions on {@code file}, and applies it (for tests/automation). */
    public void selectInput(List<File> group, File file) {
        inputBrowse = group;
        inputBrowseIdx = Math.max(0, group.indexOf(file));
        pickInput(file);
    }

    /**
     * A stylesheet picked from the favorites (star menu or ◀ / ▶) or dropped on the row is loaded
     * into the editor as well. Its tab only comes to the front while the input is a fixed file, so
     * the XML document being transformed stays visible; the stylesheet never becomes the input
     * either way ({@link #inputDocument()}).
     */
    private void pickXslt(File file) {
        editorHost.openFile(file.toPath(), inputOverride != null);
        applyXslt(file);
    }

    /** An input picked from the favorites or dropped on the row is loaded into the editor and shown — it is the input either way. */
    private void pickInput(File file) {
        applyInput(file);
        editorHost.openFile(file.toPath(), true);
    }

    /** Steps the stylesheet cursor (cyclically) and applies the new file. */
    public void nextXslt() { stepXslt(+1); }

    public void prevXslt() { stepXslt(-1); }

    private void stepXslt(int delta) {
        if (xsltBrowse.isEmpty()) {
            return;
        }
        xsltBrowseIdx = Math.floorMod(xsltBrowseIdx + delta, xsltBrowse.size());
        pickXslt(xsltBrowse.get(xsltBrowseIdx));
    }

    /** Steps the input cursor (cyclically) and applies the new file. */
    public void nextInput() { stepInput(+1); }

    public void prevInput() { stepInput(-1); }

    private void stepInput(int delta) {
        if (inputBrowse.isEmpty()) {
            return;
        }
        inputBrowseIdx = Math.floorMod(inputBrowseIdx + delta, inputBrowse.size());
        pickInput(inputBrowse.get(inputBrowseIdx));
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
        boolean hasInput = inputOverride != null || inputDocument().isPresent();
        return runAutomatically.isSelected() && xsltFile != null && hasInput;
    }

    /** @return whether {@code document} is the selected stylesheet itself (it cannot be its own input). */
    private boolean isTheStylesheet(OpenDocument document) {
        return xsltFile != null && document.getPath() != null
                && document.getPath().toAbsolutePath().normalize().equals(xsltFile.toPath().toAbsolutePath().normalize());
    }

    /** Switches the automatic run on drop / favourite pick / browse on or off (the "Run automatically" box). */
    public void setRunAutomatically(boolean enabled) {
        runAutomatically.setSelected(enabled);
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
        // Browsing needs at least two entries: with one there is nothing to step to.
        boolean active = browse.size() > 1;
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

    /** Opens the Query Console: XPath / XQuery / JSONPath against the open document live there. */
    @FXML
    public void onOpenQueryConsole() {
        editorHost.requestQueryConsole();
    }

    /** @return whether a stylesheet is set (the toolbar's Transform asks for one otherwise). */
    public boolean hasStylesheet() {
        return xsltFile != null && xsltFile.isFile();
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
     * Transforms the input (the override file if set, otherwise {@link #inputDocument()} —
     * or the remembered source, when the result tab is active).
     */
    public void transform() {
        transform(false);
    }

    /** @param live triggered by live preview / stylesheet watch (only affects usage statistics) */
    private void transform(boolean live) {
        if (xsltFile == null) {
            out.showError("Select an XSLT stylesheet first.");
            return;
        }
        String xml;
        // An input file is read on the worker below (it can be large, and live preview re-runs often)
        File inputFile = inputOverride;
        if (inputFile != null) {
            xml = null;
        } else {
            OpenDocument active = editorHost.getActiveDocument().orElse(null);
            if (active != null && active == out.getResultDocument()) {
                // Re-run from the result tab: transform the original source again.
                java.util.Optional<String> sourceText = sourceDocument != null
                        ? editorHost.getDocumentText(sourceDocument) : java.util.Optional.empty();
                if (sourceText.isEmpty()) {
                    out.showError("Select the source document to transform.");
                    return;
                }
                xml = sourceText.get();
            } else {
                OpenDocument input = inputDocument().orElse(null);
                if (input == null) {
                    out.showError("No XML input — open the XML document to transform, or pick an input file.");
                    return;
                }
                sourceDocument = input;
                xml = editorHost.getDocumentText(input).orElse("");
            }
        }
        File xslt = xsltFile;
        Map<String, Object> params = collectParameters();
        boolean wantProfile = profileCheck.isSelected();
        boolean wantTrace = traceCheck.isSelected();
        OutputFormat chosen = chosenFormat(null); // null = Auto, resolved from the stylesheet below
        out.showPending("Transforming…");
        // A transform is a single opaque call; cancellation frees the UI and abandons
        // the result (best-effort interrupt) — it may not stop Saxon mid-transform.
        java.util.concurrent.atomic.AtomicBoolean abandoned = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.Future<?>[] task = new java.util.concurrent.Future<?>[1];
        transformButton.setDisable(true);
        progress.beginIndeterminate(() -> {
            abandoned.set(true);
            if (task[0] != null) {
                task[0].cancel(true);
            }
            progress.finish();
            transformButton.setDisable(false);
            out.showPending("Cancelled");
        });
        String editorXml = xml;
        task[0] = FxtGui.executorService.submit(() -> {
            String xmlInput;
            if (inputFile != null) {
                try {
                    xmlInput = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        if (!abandoned.get()) {
                            progress.finish();
                            transformButton.setDisable(false);
                            out.showFailure("Could not read input file: " + e.getMessage());
                        }
                    });
                    return;
                }
            } else {
                xmlInput = editorXml;
            }
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance()
                    .begin(org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XSLT, xslt.getName());
            String result;
            OutputFormat format = chosen != null ? chosen : OutputFormat.XML;
            org.fxt.freexmltoolkit.service.XsltTransformationResult fullResult = null;
            try {
                String xsltContent = Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                // Auto: detect the format from the stylesheet's xsl:output declaration.
                format = chosen != null ? chosen : TransformRunner.detectXsltOutputFormat(xsltContent);
                // One single run: the full result feeds the output view, the statistics
                // probe, and (when requested) the Profile/Trace report tabs.
                fullResult = (wantProfile || wantTrace)
                        ? TransformRunner.transformForReport(xmlInput, xsltContent, params, format)
                        : TransformRunner.xsltTransformResult(xmlInput, xsltContent, params, format);
                result = fullResult.isSuccess()
                        ? fullResult.getOutputContent()
                        : "ERROR: " + fullResult.getErrorMessage();
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }
            if (fullResult != null && fullResult.isSuccess()) {
                probe.phase("Compile", fullResult.getCompilationTime());
                probe.phase("Transform", fullResult.getTransformationTime());
            }
            boolean ok = !result.startsWith("ERROR");
            UsageEvents.xsltTransformed(1, t0, ok, live,
                    fullResult != null ? fullResult.getErrorCode() : null,
                    fullResult != null ? fullResult.getErrorPhase() : null, xmlInput.length());
            long elapsedMs = probe.finish(xmlInput.length(), ok ? result.length() : -1, ok,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            String finalResult = result;
            OutputFormat finalFormat = format;
            org.fxt.freexmltoolkit.service.XsltTransformationResult report =
                    (wantProfile || wantTrace) ? fullResult : null;
            Platform.runLater(() -> {
                if (abandoned.get()) {
                    return; // user cancelled — ignore the result
                }
                progress.finish();
                transformButton.setDisable(false);
                if (live && finalResult.startsWith("ERROR")) {
                    // Automatic re-runs (live preview while typing, stylesheet watch) fail
                    // routinely on half-edited input: show the error inline, never a modal dialog.
                    out.showError(finalResult);
                } else {
                    out.showTransformResult(finalResult, finalFormat, elapsedMs);
                }
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
        if (inputOverride != null) {
            return true;
        }
        boolean resultActive = editorHost.getActiveDocument()
                .map(document -> document == out.getResultDocument()).orElse(false);
        return !resultActive && inputDocument().isPresent();
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

    @FXML
    public void onChooseXslt() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSLT");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XSLT", "*.xsl", "*.xslt"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            pickXslt(file);
        }
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon graphic = new IconifyIcon(literal);
        graphic.setIconSize(size);
        return graphic;
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
