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
import org.fxt.freexmltoolkit.i18n.I18n;
import org.fxt.freexmltoolkit.service.FavoritesService;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.project.FxtProject;
import org.fxt.freexmltoolkit.service.project.ProjectService;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The Transform activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Transform (XSLT)" (node 46:48): an open OPTIONS &amp; TOOLS
 * section on top (live preview, watch file, profile, trace, auto-open result tab,
 * Debug/Batch tools), then collapsible STYLESHEET and INPUT sections with "Change"
 * links, a segmented OUTPUT METHOD control, a PARAMETERS section (name = value rows),
 * and a primary "Run Transform" button. Queries live in the Query Console.
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
    @FXML private ContextMenu xsltMenu;
    @FXML private javafx.scene.control.ComboBox<FormatChoice> formatChoice;
    @FXML private VBox paramsBox;
    @FXML private VBox paramRows;
    @FXML private VBox runBox;
    @FXML private Button debugButton;
    private final MenuButton recentXsltMenu = new MenuButton();
    @FXML private PanelActionList tools;
    @FXML private PanelActionButton generateStylesheetButton;
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
    /** An open editor tab chosen as the input (instead of following the active editor), or {@code null}. */
    private OpenDocument inputDocumentOverride;
    /**
     * An open editor tab chosen as the stylesheet, or {@code null}: its editor text runs (saved or not);
     * {@link #xsltFile} is its path while it has one.
     */
    private OpenDocument xsltDocument;
    /** Number of FXML items at the top of the STYLESHEET / INPUT menus (the open tabs follow them). */
    private int xsltMenuStaticItems;
    private int inputMenuStaticItems;
    /** Drops a closed chosen tab; kept as a field because the editor's list holds it weakly. */
    private final javafx.collections.ListChangeListener<OpenDocument> openDocumentsListener = change -> onOpenDocumentsChanged();
    /** Follows Save As of the chosen stylesheet tab (path and name). */
    private final javafx.beans.value.ChangeListener<String> xsltDocumentNameListener = (obs, oldV, newV) -> syncStylesheetDocument();
    /** The source document of the latest transform (so a re-run from the result tab works). */
    private OpenDocument sourceDocument;
    /** The last active document that can be an input (no stylesheet, no result); the input while a stylesheet tab is in front. */
    private OpenDocument lastInputDocument;
    @FXML private PanelProgress progress;
    @FXML private Button transformButton;

    // --- project scenarios (P3) ---
    @FXML private SidePanelSection scenarioSection;
    @FXML private javafx.scene.control.ComboBox<String> scenarioChoice;
    @FXML private Label scenarioInfo;
    @FXML private Button runScenarioButton;
    @FXML private Button deleteScenarioButton;
    @FXML private Button checkScenarioButton;
    @FXML private HBox checkActions;
    /** The last regression check (for Show Differences / Accept), or {@code null}. */
    private LastCheck lastCheck;

    /** A check's outcome with what it ran for. */
    record LastCheck(FxtProject project, FxtProject.Scenario scenario, java.nio.file.Path file,
                     ScenarioCheck.Result result) {
    }
    /** Guards {@link #onScenarioChosen()} while the choice box is refilled. */
    private boolean fillingScenarios;
    private final java.util.function.Consumer<org.fxt.freexmltoolkit.service.project.FxtProject> projectListener =
            project -> {
                if (Platform.isFxApplicationThread()) {
                    refreshScenarios();
                } else {
                    Platform.runLater(this::refreshScenarios);
                }
            };

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
    /** "Use the current tab" buttons of the STYLESHEET / INPUT rows (enabled while the active tab fits). */
    private final Button useTabXsltBtn = useTabButton("transform-xslt-use-tab",
            "TransformPanel.use-current-tab-as-stylesheet", this::useActiveTabAsStylesheet);
    private final Button useTabInputBtn = useTabButton("transform-input-use-tab",
            "TransformPanel.use-current-tab-as-input", this::useActiveTabAsInput);
    private final Label inputPos = new Label();

    /** Loads the layout from {@code /pages/panels/TransformPanel.fxml} and wires the data-driven parts. */
    public TransformPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        this.out = editorHost.transformOutputPanel();
        FxmlViews.load(this, "/pages/panels/TransformPanel.fxml");
        generateStylesheetButton.visibleProperty().bind(org.fxt.freexmltoolkit.controls.shell.editor.ai.AiState.availableProperty());
        generateStylesheetButton.managedProperty().bind(generateStylesheetButton.visibleProperty());

        // --- STYLESHEET -----------------------------------------------------
        recentXsltMenu.setGraphic(icon("bi-clock-history", 13));
        recentXsltMenu.getStyleClass().add("fxt-vp-source-fav");
        recentXsltMenu.setTooltip(new javafx.scene.control.Tooltip(org.fxt.freexmltoolkit.i18n.I18n.t("TransformPanel.recentStylesheets")));
        recentXsltMenu.setOnShowing(e -> refreshRecentXsltMenu());
        xsltFavMenu.setGraphic(icon("bi-star", 13));
        xsltFavMenu.getStyleClass().add("fxt-vp-source-fav");
        xsltFavMenu.setTooltip(new javafx.scene.control.Tooltip(org.fxt.freexmltoolkit.i18n.I18n.t("TransformPanel.xsltFavorites")));
        xsltFavMenu.setOnShowing(e -> refreshXsltFavMenu());
        xsltPos.getStyleClass().add("fxt-vp-browse-pos");
        xsltPos.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // "2 / 5" never shrinks to "…"
        xsltRow.addExtras(useTabXsltBtn, recentXsltMenu, xsltFavMenu, prevXsltBtn, xsltPos, nextXsltBtn);
        // Drops behave like picking a favorite: record access, sync the browse position, load the
        // file into the editor, auto-run.
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xsltRow,
                org.fxt.freexmltoolkit.service.DragDropService.XSLT_EXTENSIONS, this::pickXslt);
        updateXsltPos();

        // --- INPUT ------------------------------------------------------------
        inputFavMenu.setGraphic(icon("bi-star", 13));
        inputFavMenu.getStyleClass().add("fxt-vp-source-fav");
        inputFavMenu.setTooltip(new javafx.scene.control.Tooltip(org.fxt.freexmltoolkit.i18n.I18n.t("TransformPanel.xmlFavorites")));
        inputFavMenu.setOnShowing(e -> refreshInputFavMenu());
        inputPos.getStyleClass().add("fxt-vp-browse-pos");
        inputPos.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); // "2 / 5" never shrinks to "…"
        inputRow.addExtras(useTabInputBtn, inputFavMenu, prevInputBtn, inputPos, nextInputBtn);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(inputRow,
                org.fxt.freexmltoolkit.service.DragDropService.XML_EXTENSIONS, this::pickInput);
        updateInputPos();
        refreshInputName();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshInputName());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> chooseBoundScenario());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refreshUseTabButtons());
        refreshUseTabButtons();
        xsltMenuStaticItems = xsltMenu.getItems().size();
        inputMenuStaticItems = inputMenu.getItems().size();
        editorHost.getOpenDocuments().addListener(
                new javafx.collections.WeakListChangeListener<>(openDocumentsListener));

        // --- OUTPUT METHOD -----------------------------------------------------
        fillFormatChoice();

        // --- PARAMETERS ----------------------------------------------------------
        Button addParam = PanelActionList.inlineRow(org.fxt.freexmltoolkit.i18n.I18n.t("TransformPanel.addParameter"), "bi-plus-circle", () -> addParameter("", ""));
        addParam.setId("transform-add-parameter");
        paramsBox.getChildren().add(addParam);

        // --- Run Transform / Debug --------------------------------------------
        // (Run grows to fill the row; Debug keeps its natural width — both declared in FXML.)

        // Live preview: re-run the XSLT transform shortly after the active document
        // changes (typing / tab switch), when enabled and a stylesheet is set.
        // Never re-transform the transform-result tab itself (feedback loop).
        liveDebounce.setOnFinished(e -> {
            if (livePreview.isSelected() && hasXslt() && isTransformableDocumentActive()) {
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
                ProjectService.getInstance().removeListener(projectListener);
            } else if (oldScene == null) {
                ProjectService.getInstance().removeListener(projectListener); // never twice
                ProjectService.getInstance().addListener(projectListener);
                refreshScenarios();
            }
        });

        // --- SCENARIO (shown while a project is open) ------------------------------
        ProjectService.getInstance().addListener(projectListener);
        refreshScenarios();
    }

    // ----- project scenarios ------------------------------------------------------

    /** Shows the SCENARIO section for the open project and refills its scenario list. */
    void refreshScenarios() {
        Optional<FxtProject> project = ProjectService.getInstance().current();
        scenarioSection.setVisible(project.isPresent());
        scenarioSection.setManaged(project.isPresent());
        String selected = scenarioChoice.getValue();
        fillingScenarios = true;
        try {
            scenarioChoice.getItems().setAll(project.map(p -> p.scenarios().stream()
                    .map(s -> s.name).filter(n -> n != null && !n.isBlank()).toList()).orElse(List.of()));
            scenarioChoice.setValue(selected != null && scenarioChoice.getItems().contains(selected) ? selected : null);
        } finally {
            fillingScenarios = false;
        }
        project.ifPresent(p -> scenarioSection.setTitle(I18n.t("TransformPanel.scenario", p.name())));
        updateScenarioButtons();
        chooseBoundScenario();
    }

    /** The scenario chosen because the project binds it to the active document (not by the user). */
    private String boundChoice;

    /**
     * Chooses the scenario the project binds to the active document, unless the user chose one
     * themselves; a scenario chosen this way is replaced when another document becomes active.
     */
    void chooseBoundScenario() {
        Optional<FxtProject> project = ProjectService.getInstance().current();
        if (project.isEmpty() || scenarioChoice == null) {
            return;
        }
        String current = scenarioChoice.getValue();
        if (current != null && !current.equals(boundChoice)) {
            return; // the user's choice stays
        }
        java.nio.file.Path active = editorHost.getActiveDocument().map(OpenDocument::getPath).orElse(null);
        Optional<FxtProject.Scenario> bound = active == null ? Optional.empty() : project.get().scenarioFor(active);
        if (bound.isEmpty()) {
            if (current != null) {
                fillingScenarios = true;
                scenarioChoice.setValue(null);
                fillingScenarios = false;
                boundChoice = null;
                updateScenarioButtons();
            }
            return;
        }
        String name = bound.get().name;
        if (name.equals(current)) {
            return;
        }
        fillingScenarios = true;
        scenarioChoice.setValue(name);
        fillingScenarios = false;
        boundChoice = name;
        updateScenarioButtons();
        applyScenario(project.get(), bound.get());
    }

    /** @return the scenario chosen because the project binds it to the active document, or {@code null} */
    String boundScenarioChoice() {
        return boundChoice;
    }

    private void updateScenarioButtons() {
        boolean none = scenarioChoice.getValue() == null;
        runScenarioButton.setDisable(none);
        deleteScenarioButton.setDisable(none);
        checkScenarioButton.setDisable(none || chosenScenario().map(s -> !s.hasExpected()).orElse(true));
        showCheckActions(false);
        if (none) {
            showScenarioInfo(scenarioChoice.getItems().isEmpty()
                    ? "No scenarios yet — set up a transformation and save it with the disk button." : null);
        }
    }

    private void showScenarioInfo(String text) {
        boolean show = text != null && !text.isBlank();
        scenarioInfo.setText(show ? text : "");
        scenarioInfo.setVisible(show);
        scenarioInfo.setManaged(show);
    }

    private Optional<FxtProject.Scenario> chosenScenario() {
        String name = scenarioChoice.getValue();
        return name == null ? Optional.empty()
                : ProjectService.getInstance().current().flatMap(p -> p.scenario(name));
    }

    /** Loads the chosen scenario into the panel (stylesheet, input, parameters, output method). */
    @FXML
    public void onScenarioChosen() {
        if (fillingScenarios) {
            return;
        }
        boundChoice = null; // chosen by the user
        updateScenarioButtons();
        Optional<FxtProject> project = ProjectService.getInstance().current();
        Optional<FxtProject.Scenario> chosen = chosenScenario();
        if (project.isEmpty() || chosen.isEmpty()) {
            return;
        }
        applyScenario(project.get(), chosen.get());
    }

    /** Fills the panel from a scenario; XQuery and XProc scenarios only run through Run Scenario. */
    void applyScenario(FxtProject project, FxtProject.Scenario scenario) {
        boolean xslt = scenario.stylesheet != null
                && ScenarioRunner.languageOf(scenario.stylesheet) == ScenarioRunner.Language.XSLT;
        if (xslt) {
            setXsltFile(project.resolve(scenario.stylesheet).toFile());
        }
        // An empty input or ${file} means the active document
        setInputOverride(scenario.input == null || scenario.input.isBlank() || scenario.input.contains(FxtProject.VAR_FILE)
                ? null : project.resolve(scenario.input).toFile());
        paramRows.getChildren().clear();
        if (scenario.parameters != null) {
            scenario.parameters.forEach(this::addParameter);
        }
        OutputFormat format = null;
        if (scenario.outputFormat != null && !scenario.outputFormat.isBlank()) {
            try {
                format = OutputFormat.valueOf(scenario.outputFormat.strip().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // unknown name: Auto
            }
        }
        setOutputFormat(format);
        String target = scenario.output == null || scenario.output.isBlank() ? null : "Output: " + scenario.output;
        if (scenario.isPdf()) {
            target = "PDF" + (target == null ? " — the scenario needs an output file." : ". " + target)
                    + " Use Run Scenario.";
        }
        showScenarioInfo(xslt ? target
                : scenario.stylesheet + " is not a stylesheet — use Run Scenario"
                        + (target == null ? "." : ". " + target));
    }

    /** Saves the panel's settings as a scenario of the open project (asks for name and output). */
    @FXML
    public void onSaveScenario() {
        Optional<FxtProject> open = ProjectService.getInstance().current();
        if (open.isEmpty()) {
            return;
        }
        FxtProject project = open.get();
        if (xsltFile == null) {
            out.showError(I18n.t(stylesheetDocument() != null ? "TransformPanel.stylesheet-tab-unsaved"
                    : "TransformPanel.choose-a-stylesheet-before-saving"));
            return;
        }
        Optional<FxtProject.Scenario> chosen = chosenScenario();
        String initialName = chosen.map(s -> s.name).orElse(xsltFile.getName().replaceFirst("\\.[^.]*$", ""));
        ScenarioDialog dialog = new ScenarioDialog(project, initialName, chosen.map(s -> s.output).orElse(null),
                chosen.map(s -> s.input != null && s.input.contains(FxtProject.VAR_FILE)).orElse(false));
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(request -> saveScenario(project, request.name(), request.output(), request.perFile()));
    }

    /** Stores the panel's current settings under {@code name} and saves the project. */
    void saveScenario(FxtProject project, String name, java.nio.file.Path output) {
        saveScenario(project, name, output, false);
    }

    /**
     * Stores the panel's current settings under {@code name} and saves the project.
     *
     * @param perFile the scenario's input is {@code ${file}}: the file it runs for
     */
    void saveScenario(FxtProject project, String name, java.nio.file.Path output, boolean perFile) {
        java.nio.file.Path input = perFile ? null : inputOverride != null ? inputOverride.toPath()
                : inputDocument().map(OpenDocument::getPath).orElse(null);
        Map<String, String> parameters = new LinkedHashMap<>();
        collectParameters().forEach((k, v) -> parameters.put(k, String.valueOf(v)));
        FormatChoice choice = formatChoice.getValue();
        project.putScenario(new FxtProject.Scenario(name, project.relativize(xsltFile.toPath()),
                perFile ? FxtProject.VAR_FILE : input == null ? null : project.relativize(input), parameters,
                choice == null || choice.format == null ? null : choice.format.name(),
                output == null ? null : project.relativize(output)));
        try {
            ProjectService.getInstance().save();
        } catch (java.io.IOException e) {
            out.showFailure(I18n.t("TransformPanel.the-project-could-not-be", e.getMessage()));
            return;
        }
        refreshScenarios();
        fillingScenarios = true;
        scenarioChoice.setValue(name);
        fillingScenarios = false;
        updateScenarioButtons();
        showScenarioInfo(output == null ? "Saved." : "Saved. Output: " + project.relativize(output));
    }

    /** Removes the chosen scenario from the project. */
    @FXML
    public void onDeleteScenario() {
        String name = scenarioChoice.getValue();
        Optional<FxtProject> project = ProjectService.getInstance().current();
        if (name == null || project.isEmpty()
                || !org.fxt.freexmltoolkit.util.DialogHelper.showConfirmation(I18n.t("TransformPanel.delete-scenario"),
                        I18n.t("TransformPanel.delete-the-scenario", name), I18n.t("TransformPanel.it-is-removed-from", project.get().file().getFileName()))) {
            return;
        }
        deleteScenario(project.get(), name);
    }

    void deleteScenario(FxtProject project, String name) {
        project.removeScenario(name);
        try {
            ProjectService.getInstance().save();
        } catch (java.io.IOException e) {
            out.showFailure(I18n.t("TransformPanel.the-project-could-not-be", e.getMessage()));
        }
        refreshScenarios();
    }

    /** Runs the chosen scenario from the files on disk; the result goes to the output panel (and its file). */
    @FXML
    public void onRunScenario() {
        Optional<FxtProject> project = ProjectService.getInstance().current();
        Optional<FxtProject.Scenario> chosen = chosenScenario();
        if (project.isEmpty() || chosen.isEmpty()) {
            return;
        }
        FxtProject.Scenario scenario = chosen.get();
        java.nio.file.Path forFile = null;
        if (needsFile(scenario)) {
            // ${file} (or no input for a stylesheet): the active, saved document
            forFile = editorHost.getActiveDocument().map(OpenDocument::getPath).orElse(null);
            if (forFile == null) {
                out.showFailure(I18n.t("TransformPanel.scenario-failed", scenario.name,
                        "The scenario runs for a file: open and save the document to run it for."));
                return;
            }
        }
        out.showPending(I18n.t("TransformPanel.running-scenario", scenario.name));
        runScenarioButton.setDisable(true);
        long t0 = System.nanoTime();
        java.nio.file.Path file = forFile;
        FxtGui.executorService.submit(() -> {
            ScenarioRunner.Result result = ScenarioRunner.run(project.get(), scenario, file);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            Platform.runLater(() -> showScenarioResult(project.get(), scenario, result, ms));
        });
    }

    void showScenarioResult(FxtProject project, FxtProject.Scenario scenario, ScenarioRunner.Result result, long ms) {
        runScenarioButton.setDisable(scenarioChoice.getValue() == null);
        if (!result.ok()) {
            out.showFailure(I18n.t("TransformPanel.scenario-failed", scenario.name, result.error()));
            showScenarioInfo("Failed: " + result.error());
            return;
        }
        if (scenario.isPdf() && result.written() != null) {
            out.showMessages(scenario.name, result.text());
            editorHost.openPdfPreview(result.written().toFile());
        } else {
            out.showTransformResult(result.text(), result.format() != null ? result.format() : OutputFormat.XML, ms);
        }
        showScenarioInfo(result.written() != null ? "Written to " + project.relativize(result.written()) : "Done.");
    }

    /**
     * Regression check: runs the chosen scenario like Run and compares its result with the scenario's
     * expected file ({@link ScenarioCheck}); a difference offers Show Differences and Accept.
     */
    @FXML
    public void onCheckScenario() {
        Optional<FxtProject> project = ProjectService.getInstance().current();
        Optional<FxtProject.Scenario> chosen = chosenScenario();
        if (project.isEmpty() || chosen.isEmpty()) {
            return;
        }
        FxtProject.Scenario scenario = chosen.get();
        java.nio.file.Path forFile = null;
        if (needsFile(scenario)) {
            forFile = editorHost.getActiveDocument().map(OpenDocument::getPath).orElse(null);
            if (forFile == null) {
                out.showFailure(I18n.t("TransformPanel.scenario-failed", scenario.name,
                        "The scenario runs for a file: open and save the document to check it for."));
                return;
            }
        }
        showCheckActions(false);
        out.showPending(I18n.t("TransformPanel.checking-scenario", scenario.name));
        checkScenarioButton.setDisable(true);
        long t0 = System.nanoTime();
        java.nio.file.Path file = forFile;
        FxtGui.executorService.submit(() -> {
            ScenarioCheck.Result result = ScenarioCheck.check(project.get(), scenario, file);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            Platform.runLater(() -> showCheckResult(new LastCheck(project.get(), scenario, file, result), ms));
        });
    }

    void showCheckResult(LastCheck check, long ms) {
        lastCheck = check;
        checkScenarioButton.setDisable(false);
        ScenarioCheck.Result result = check.result();
        String expected = result.expected() == null ? "" : check.project().relativize(result.expected());
        switch (result.status()) {
            case PASSED -> {
                out.showTransformResult(result.actual(), OutputFormat.XML, ms);
                showScenarioInfo(I18n.t("TransformPanel.check-passed", expected));
            }
            case FAILED -> {
                out.showTransformResult(result.actual(), OutputFormat.XML, ms);
                StringBuilder text = new StringBuilder(I18n.t("TransformPanel.check-failed", result.summary(), expected));
                result.differences().stream().limit(3).forEach(d -> text.append("\n• ").append(d.describe()));
                if (result.differences().size() > 3) {
                    text.append("\n…");
                }
                showScenarioInfo(text.toString());
            }
            case NO_EXPECTED -> {
                if (result.actual() != null) {
                    out.showTransformResult(result.actual(), OutputFormat.XML, ms);
                }
                showScenarioInfo(result.message());
            }
            case ERROR -> {
                out.showFailure(I18n.t("TransformPanel.scenario-failed", check.scenario().name, result.message()));
                showScenarioInfo("Failed: " + result.message());
            }
        }
        showCheckActions(result.actual() != null
                && (result.status() == ScenarioCheck.Status.FAILED || result.status() == ScenarioCheck.Status.NO_EXPECTED));
        lookupCheckDifferences().ifPresent(b -> b.setDisable(result.status() != ScenarioCheck.Status.FAILED));
    }

    private Optional<javafx.scene.Node> lookupCheckDifferences() {
        return checkActions.getChildren().stream().filter(n -> "transform-check-differences".equals(n.getId())).findFirst();
    }

    private void showCheckActions(boolean show) {
        if (checkActions != null) {
            checkActions.setVisible(show);
            checkActions.setManaged(show);
        }
    }

    /** Opens the expected result next to the actual one, compared as the scenario says. */
    @FXML
    public void onShowCheckDifferences() {
        LastCheck check = lastCheck;
        if (check == null || check.result().expected() == null || check.result().actual() == null) {
            return;
        }
        editorHost.openScenarioDiff(check.result().expected(), check.result().actual(),
                check.scenario().name + "-actual" + extensionOf(check.result().expected()),
                FxtProject.CompareSettings.toOptions(check.scenario().compare));
    }

    private static String extensionOf(java.nio.file.Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : ".xml";
    }

    /** Makes the actual result the expected one (placeholders of the old expected file are kept). */
    @FXML
    public void onAcceptCheck() {
        LastCheck check = lastCheck;
        if (check == null || check.result().actual() == null) {
            return;
        }
        FxtGui.executorService.submit(() -> {
            try {
                java.nio.file.Path written = ScenarioCheck.accept(check.project(), check.scenario(), check.file(),
                        check.result().actual());
                Platform.runLater(() -> {
                    showCheckActions(false);
                    showScenarioInfo(I18n.t("TransformPanel.check-accepted", check.project().relativize(written)));
                });
            } catch (java.io.IOException e) {
                Platform.runLater(() -> out.showFailure(e.getMessage()));
            }
        });
    }

    /** @return the last check (tests) */
    LastCheck lastCheckForTesting() {
        return lastCheck;
    }

    /** @return whether a scenario runs for a file: {@code ${file}} in its input, or a stylesheet without input */
    static boolean needsFile(FxtProject.Scenario scenario) {
        boolean fileVariable = scenario.input != null && scenario.input.contains(FxtProject.VAR_FILE);
        boolean stylesheetWithoutInput = (scenario.input == null || scenario.input.isBlank()) && scenario.stylesheet != null
                && ScenarioRunner.languageOf(scenario.stylesheet) == ScenarioRunner.Language.XSLT;
        return fileVariable || stylesheetWithoutInput;
    }

    @FXML
    public void onLivePreviewToggled() {
        scheduleLivePreview();
    }

    @FXML
    public void onChangeInput() {
        fillTabEntries(inputMenu, inputMenuStaticItems, "transform-input-tab-",
                inputTabCandidates(), isOpen(inputDocumentOverride) ? inputDocumentOverride : null,
                this::useInputDocument, "TransformPanel.no-open-xml-tabs");
        addCurrentTabItem(inputMenu, inputMenuStaticItems, "transform-input-current-tab", activeInputTab());
        inputMenu.show(inputName, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /** STYLESHEET "Change": choose a file, or one of the open XSLT tabs. */
    @FXML
    public void onChangeXslt() {
        fillTabEntries(xsltMenu, xsltMenuStaticItems, "transform-xslt-tab-",
                xsltTabCandidates(), stylesheetDocument(), this::useStylesheetDocument,
                "TransformPanel.no-open-xslt-tabs");
        addCurrentTabItem(xsltMenu, xsltMenuStaticItems, "transform-xslt-current-tab", activeXsltTab());
        xsltMenu.show(xsltName, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    /**
     * Puts "Use current tab (name)" right after the menu's FXML items; disabled when the active
     * tab cannot be used.
     */
    private void addCurrentTabItem(ContextMenu menu, int at, String id, Optional<OpenDocument> activeTab) {
        String label = activeTab.map(d -> I18n.t("TransformPanel.use-current-tab-named", d.getDisplayName()))
                .orElse(I18n.t("TransformPanel.use-current-tab"));
        MenuItem item = new MenuItem(label, icon("bi-box-arrow-in-down", 16));
        item.setId(id);
        item.setMnemonicParsing(false);
        item.setDisable(activeTab.isEmpty());
        item.setOnAction(e -> {
            if (menu == xsltMenu) {
                useActiveTabAsStylesheet();
            } else {
                useActiveTabAsInput();
            }
        });
        menu.getItems().add(at, item);
    }

    /** @return the active tab when it is an XSLT document (it can be the stylesheet). */
    Optional<OpenDocument> activeXsltTab() {
        return editorHost.getActiveDocument().filter(d -> d.getFileType() == EditorFileType.XSLT);
    }

    /** @return the active tab when it is an XML document that can be the input. */
    Optional<OpenDocument> activeInputTab() {
        return editorHost.getActiveDocument().filter(d -> d.getFileType().isXmlFamily() && isInputCandidate(d));
    }

    /** Sets the current editor tab as the stylesheet (STYLESHEET row button / menu). */
    public void useActiveTabAsStylesheet() {
        activeXsltTab().ifPresent(this::useStylesheetDocument);
        refreshUseTabButtons();
    }

    /** Sets the current editor tab as the input (INPUT row button / menu). */
    public void useActiveTabAsInput() {
        activeInputTab().ifPresent(this::useInputDocument);
        refreshUseTabButtons();
    }

    /** Enables the "Use current tab" buttons only while the active tab fits the row. */
    private void refreshUseTabButtons() {
        useTabXsltBtn.setDisable(activeXsltTab().isEmpty());
        useTabInputBtn.setDisable(activeInputTab().isEmpty());
    }

    /** A compact icon-only "Use current tab" button for a source row. */
    private static Button useTabButton(String id, String tooltipKey, Runnable action) {
        Button button = new Button(null, icon("bi-box-arrow-in-down", 13));
        button.setId(id);
        button.getStyleClass().add("fxt-vp-browse-nav");
        button.setTooltip(new javafx.scene.control.Tooltip(I18n.t(tooltipKey)));
        button.setAccessibleText(I18n.t(tooltipKey));
        button.setOnAction(e -> action.run());
        return button;
    }

    /** @return the open XSLT tabs, the active one first (the STYLESHEET menu's entries). */
    List<OpenDocument> xsltTabCandidates() {
        return activeFirst(editorHost.getOpenDocuments().stream()
                .filter(d -> d.getFileType() == EditorFileType.XSLT).toList());
    }

    /** @return the open XML tabs that can be the input, the active one first (the INPUT menu's entries). */
    List<OpenDocument> inputTabCandidates() {
        return activeFirst(editorHost.getOpenDocuments().stream()
                .filter(d -> d.getFileType().isXmlFamily() && isInputCandidate(d)).toList());
    }

    private List<OpenDocument> activeFirst(List<OpenDocument> documents) {
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        List<OpenDocument> sorted = new java.util.ArrayList<>(documents);
        if (active != null && sorted.remove(active)) {
            sorted.addFirst(active);
        }
        return sorted;
    }

    /**
     * Replaces the dynamic part of a source menu with a header and one entry per open tab (the
     * current choice checked, the active tab marked).
     */
    private void fillTabEntries(ContextMenu menu, int staticItems, String idPrefix, List<OpenDocument> documents,
                                OpenDocument current, java.util.function.Consumer<OpenDocument> choose,
                                String emptyKey) {
        menu.getItems().remove(staticItems, menu.getItems().size());
        menu.getItems().add(new SeparatorMenuItem());
        MenuItem header = new MenuItem(I18n.t("TransformPanel.open-tabs"), icon("bi-window", 16));
        header.setId(idPrefix + "header");
        header.setDisable(true);
        menu.getItems().add(header);
        if (documents.isEmpty()) {
            MenuItem none = new MenuItem(I18n.t(emptyKey));
            none.setId(idPrefix + "none");
            none.setDisable(true);
            menu.getItems().add(none);
            return;
        }
        OpenDocument active = editorHost.getActiveDocument().orElse(null);
        for (int i = 0; i < documents.size(); i++) {
            OpenDocument document = documents.get(i);
            String label = document.getDisplayName()
                    + (document.getPath() == null ? " — " + I18n.t("TransformPanel.unsaved") : "")
                    + (document == active ? " (" + I18n.t("TransformPanel.active-tab") + ")" : "");
            javafx.scene.control.CheckMenuItem item = new javafx.scene.control.CheckMenuItem(label,
                    icon(document.getFileType().icon(), 16));
            item.setId(idPrefix + i);
            item.setMnemonicParsing(false);
            item.setSelected(document == current);
            item.setOnAction(e -> choose.accept(document));
            menu.getItems().add(item);
        }
    }

    @FXML
    public void onUseActiveEditor() {
        setInputOverride(null);
    }

    @FXML
    public void onTransform() {
        transform();
    }

    /** Generate stylesheet… (AI): from the panel's input document and a target sample. */
    @FXML
    public void onGenerateStylesheet() {
        String source;
        String name;
        File file;
        try {
            if (inputOverride != null) {
                file = inputOverride;
                name = inputOverride.getName();
                source = java.nio.file.Files.readString(inputOverride.toPath());
            } else {
                OpenDocument input = inputDocument().orElse(null);
                if (input == null) {
                    org.fxt.freexmltoolkit.util.DialogHelper.showInformation(I18n.t("GenerateStylesheetDialog.title"),
                            I18n.t("TransformPanel.ai.noInput"), "");
                    return;
                }
                name = input.getDisplayName();
                file = input.getPath() == null ? null : input.getPath().toFile();
                source = editorHost.getDocumentText(input).orElse(file != null ? java.nio.file.Files.readString(file.toPath()) : "");
            }
        } catch (java.io.IOException e) {
            org.fxt.freexmltoolkit.util.DialogHelper.showError(I18n.t("GenerateStylesheetDialog.title"), e.getMessage(), "");
            return;
        }
        new org.fxt.freexmltoolkit.controls.shell.editor.ai.GenerateStylesheetDialog(source, name, file).showAndWait()
                .ifPresent(request -> org.fxt.freexmltoolkit.controls.shell.editor.ai.AiActions.generateStylesheet(
                        editorHost, request));
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
        chooser.setTitle(I18n.t("TransformPanel.select-input-xml"));
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
        this.inputDocumentOverride = null;
        refreshInputName();
    }

    /**
     * Uses the open editor tab {@code document} as the input (its current editor text, saved or
     * not) until another input is chosen or the tab is closed.
     */
    public void useInputDocument(OpenDocument document) {
        this.inputOverride = null;
        this.inputDocumentOverride = document;
        refreshInputName();
        if (readyToAutoRun()) {
            transform();
        }
    }

    /** @return the open tab chosen as the input, or {@code null} (for tests). */
    OpenDocument currentInputDocumentOverride() {
        return isOpen(inputDocumentOverride) ? inputDocumentOverride : null;
    }

    /** Updates the INPUT row label: the override file, or the active document (live). */
    private void refreshInputName() {
        refreshUseTabButtons(); // the stylesheet tab can never be the input
        if (inputOverride != null) {
            inputRow.showFile(inputOverride);
            return;
        }
        if (isOpen(inputDocumentOverride) && !isTheStylesheet(inputDocumentOverride)) {
            inputRow.showName(inputDocumentOverride.getDisplayName(), editorTabTooltip(inputDocumentOverride));
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
        if (isOpen(inputDocumentOverride) && !isTheStylesheet(inputDocumentOverride)) {
            return Optional.of(inputDocumentOverride);
        }
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
            out.showError(I18n.t(stylesheetDocument() != null ? "TransformPanel.stylesheet-tab-unsaved"
                    : "TransformPanel.select-an-xslt-stylesheet-first"));
            return;
        }
        OpenDocument pinnedInput = inputOverride == null && isOpen(inputDocumentOverride)
                && !isTheStylesheet(inputDocumentOverride) ? inputDocumentOverride : null;
        // Same input as Run Transform: the INPUT override file when one is set (drop, favourite,
        // ◀ / ▶ browsing), otherwise the active document.
        String xml;
        if (inputOverride != null) {
            try {
                xml = Files.readString(inputOverride.toPath(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                out.showFailure(I18n.t("TransformPanel.could-not-read-input-file", e.getMessage()));
                return;
            }
        } else if (pinnedInput != null) {
            xml = editorHost.getDocumentText(pinnedInput).orElse("");
        } else {
            if (editorHost.getActiveDocument().isEmpty()) {
                out.showError(I18n.t("TransformPanel.no-document-open"));
                return;
            }
            xml = editorHost.getActiveText().orElse("");
        }
        OutputFormat format;
        try {
            String text = stylesheetEditorText();
            format = resolveXsltFormat(text != null ? text : Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            format = chosenFormat(OutputFormat.XML);
        }
        if (inputOverride != null) {
            editorHost.setDebugInputFile(inputOverride.toPath()); // else: matched to the active document
        } else if (pinnedInput != null && pinnedInput.getPath() != null) {
            editorHost.setDebugInputFile(pinnedInput.getPath());
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
        if (hasXslt()) {
            String xsltContent = stylesheetEditorText();
            try {
                if (xsltContent == null) {
                    xsltContent = Files.readString(xsltFile.toPath(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                out.showFailure(I18n.t("TransformPanel.could-not-read-stylesheet", e.getMessage()));
                return;
            }
            view = new BatchTransformView(BatchTransformView.Kind.XSLT, xsltContent,
                    stylesheetSystemId(null), collectParameters(), resolveXsltFormat(xsltContent));
        } else if (xqueryText != null && !xqueryText.isBlank()) {
            // the editor text runs, under the query file's location so relative module imports resolve
            String querySystemId = editorHost.getActiveDocument()
                    .map(OpenDocument::getPath).map(p -> p.toUri().toString()).orElse(null);
            view = new BatchTransformView(BatchTransformView.Kind.XQUERY,
                    xqueryText, querySystemId, collectParameters(), chosenFormat(OutputFormat.XML));
        } else {
            out.showError(I18n.t("TransformPanel.set-an-xslt-stylesheet-or"));
            return;
        }
        editorHost.openToolTab(I18n.t("TransformPanel.batch"), "bi-files", view);
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
        releaseStylesheetDocument();
        this.xsltFile = file;
        this.lastXsltModified = file != null ? file.lastModified() : 0L;
        xsltRow.showFile(file);
        refreshInputName(); // the stylesheet is never the input
        if (file != null) {
            addRecentXslt(file);
        }
    }

    /**
     * Uses the open editor tab {@code document} as the stylesheet: its current editor text runs,
     * saved or not (a generated stylesheet, a file opened from the menu or the Explorer).
     */
    public void useStylesheetDocument(OpenDocument document) {
        releaseStylesheetDocument();
        xsltDocument = document;
        document.displayNameProperty().addListener(xsltDocumentNameListener);
        File file = document.getPath() != null ? document.getPath().toFile() : null;
        this.xsltFile = file;
        this.lastXsltModified = file != null ? file.lastModified() : 0L;
        if (file != null) {
            recordAccess(file);
            addRecentXslt(file);
        }
        refreshXsltName();
        refreshInputName(); // the stylesheet is never the input
        if (readyToAutoRun()) {
            transform();
        }
    }

    /** @return the open tab chosen as the stylesheet, or {@code null} (none chosen, or closed). */
    OpenDocument stylesheetDocument() {
        return isOpen(xsltDocument) ? xsltDocument : null;
    }

    /** @return whether a stylesheet is chosen: a file or an open tab. */
    private boolean hasXslt() {
        return xsltFile != null || stylesheetDocument() != null;
    }

    /** @return the chosen stylesheet tab's editor text, or {@code null} when the stylesheet is a file. */
    private String stylesheetEditorText() {
        OpenDocument document = stylesheetDocument();
        return document == null ? null : editorHost.getDocumentText(document).orElse(null);
    }

    /**
     * @return the stylesheet's location for relative includes, imports and {@code document()}: its
     * file, or - for an unsaved tab - a name in the input's folder (else the working directory)
     */
    private String stylesheetSystemId(java.nio.file.Path inputPath) {
        if (xsltFile != null) {
            return xsltFile.toURI().toString();
        }
        java.nio.file.Path dir = inputPath != null ? inputPath.toAbsolutePath().getParent() : null;
        if (dir == null) {
            dir = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        }
        return dir.resolve("untitled.xsl").toUri().toString();
    }

    /** @return the stylesheet's name for statistics and tab titles. */
    private String stylesheetName() {
        OpenDocument document = stylesheetDocument();
        return xsltFile != null ? xsltFile.getName() : document != null ? document.getDisplayName() : "";
    }

    private void releaseStylesheetDocument() {
        if (xsltDocument != null) {
            xsltDocument.displayNameProperty().removeListener(xsltDocumentNameListener);
            xsltDocument = null;
        }
    }

    /** The chosen stylesheet tab was saved (Save As gives an untitled tab its path): follow it. */
    private void syncStylesheetDocument() {
        OpenDocument document = stylesheetDocument();
        if (document == null) {
            return;
        }
        java.nio.file.Path path = document.getPath();
        File file = path != null ? path.toFile() : null;
        if (file != null && !file.equals(xsltFile)) {
            xsltFile = file;
            lastXsltModified = file.lastModified();
            addRecentXslt(file);
        }
        refreshXsltName();
    }

    /** A chosen stylesheet / input tab was closed: a saved stylesheet stays as its file, else the choice ends. */
    private void onOpenDocumentsChanged() {
        if (xsltDocument != null && !isOpen(xsltDocument)) {
            releaseStylesheetDocument();
            refreshXsltName();
        }
        if (inputDocumentOverride != null && !isOpen(inputDocumentOverride)) {
            inputDocumentOverride = null;
        }
        refreshInputName();
    }

    /** Updates the STYLESHEET row: the chosen tab or file. */
    private void refreshXsltName() {
        OpenDocument document = stylesheetDocument();
        if (document != null) {
            xsltRow.showName(document.getDisplayName(), editorTabTooltip(document));
        } else {
            xsltRow.showFile(xsltFile);
        }
    }

    /** @return the tooltip of a source row showing an editor tab. */
    private static String editorTabTooltip(OpenDocument document) {
        return document.getPath() != null
                ? I18n.t("TransformPanel.editor-tab-path", document.getPath().toString())
                : I18n.t("TransformPanel.editor-tab-unsaved");
    }

    private boolean isOpen(OpenDocument document) {
        return document != null && editorHost.getOpenDocuments().contains(document);
    }

    private static void addRecentXslt(File file) {
        try {
            org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).addRecentXsltFile(file);
        } catch (Throwable ignored) {
            // properties service unavailable — no recent-files persistence
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
            MenuItem clear = new MenuItem(I18n.t("TransformPanel.clear-recent"));
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
        button.setAccessibleText(org.fxt.freexmltoolkit.i18n.I18n.t(iconLiteral.endsWith("left") ? "TransformPanel.previous" : "TransformPanel.next"));
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
        return runAutomatically.isSelected() && hasXslt() && hasInput;
    }

    /** @return whether {@code document} is the selected stylesheet itself (it cannot be its own input). */
    private boolean isTheStylesheet(OpenDocument document) {
        return document != null && document == xsltDocument || xsltFile != null && document.getPath() != null
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
        return xsltFile != null && xsltFile.isFile() || stylesheetDocument() != null;
    }

    /** @return the currently selected stylesheet file (for tests). */
    File currentXsltFile() {
        return xsltFile;
    }

    /** @return the SCENARIO section, its choice box and info line (for tests). */
    SidePanelSection scenarioSectionForTesting() {
        return scenarioSection;
    }

    javafx.scene.control.ComboBox<String> scenarioChoiceForTesting() {
        return scenarioChoice;
    }

    String scenarioInfoForTesting() {
        return scenarioInfo.isVisible() ? scenarioInfo.getText() : "";
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
    /** Test hook: ticks Profile run. */
    void setProfileRunForTesting(boolean enabled) {
        profileCheck.setSelected(enabled);
    }

    public void setAutoOpenResultTab(boolean enabled) {
        autoOpenResultTab.setSelected(enabled);
    }

    /** Schedules a debounced live re-transform when live preview is on and a stylesheet is set. */
    private void scheduleLivePreview() {
        if (livePreview.isSelected() && hasXslt()) {
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

    /**
     * Waits until the input document's text holds its deferred grid edits (written back off the FX
     * thread for a large document), then transforms.
     *
     * @param live triggered by live preview / stylesheet watch (only affects usage statistics)
     */
    private void transform(boolean live) {
        OpenDocument input = null;
        if (inputOverride == null) {
            OpenDocument active = editorHost.getActiveDocument().orElse(null);
            input = active != null && active == out.getResultDocument() ? sourceDocument : inputDocument().orElse(null);
        }
        editorHost.whenDocumentTextCurrent(input)
                .thenCompose(v -> editorHost.whenDocumentTextCurrent(stylesheetDocument()))
                .thenRun(() -> transformNow(live)).exceptionally(e -> {
            org.apache.logging.log4j.LogManager.getLogger(TransformPanel.class).error("Transform failed", e);
            return null;
        });
    }

    private void transformNow(boolean live) {
        if (!hasXslt()) {
            out.showError(I18n.t("TransformPanel.select-an-xslt-stylesheet-first"));
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
                    out.showError(I18n.t("TransformPanel.select-the-source-document-to"));
                    return;
                }
                xml = sourceText.get();
            } else {
                OpenDocument input = inputDocument().orElse(null);
                if (input == null) {
                    out.showError(I18n.t("TransformPanel.no-xml-input-open-the"));
                    return;
                }
                sourceDocument = input;
                xml = editorHost.getDocumentText(input).orElse("");
            }
        }
        File xslt = xsltFile;
        // A stylesheet chosen as an editor tab runs with its editor text (saved or not)
        String xsltEditorText = stylesheetEditorText();
        String xsltName = stylesheetName();
        String xmlBaseUri = inputFile != null ? inputFile.toURI().toString()
                : sourceDocument != null && sourceDocument.getPath() != null
                        ? sourceDocument.getPath().toUri().toString() : null;
        // The input's location and schema, so the result's own tab can get a schema too
        java.nio.file.Path inputPath = inputFile != null ? inputFile.toPath()
                : sourceDocument != null ? sourceDocument.getPath() : null;
        // Locations of stylesheet and input: relative xsl:import/xsl:include/document() hrefs
        // resolve against the stylesheet's folder, relative URIs in the input against its own
        String xsltSystemId = stylesheetSystemId(inputPath);
        File openInputSchema = inputFile != null ? editorHost.schemaFileOf(inputFile)
                : sourceDocument != null ? editorHost.schemaFileOf(sourceDocument) : null;
        Map<String, Object> params = collectParameters();
        boolean wantProfile = profileCheck.isSelected();
        boolean wantTrace = traceCheck.isSelected();
        OutputFormat chosen = chosenFormat(null); // null = Auto, resolved from the stylesheet below
        out.showPending(I18n.t("TransformPanel.transforming"));
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
            out.showPending(I18n.t("TransformPanel.cancelled"));
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
                            out.showFailure(I18n.t("TransformPanel.could-not-read-input-file", e.getMessage()));
                        }
                    });
                    return;
                }
            } else {
                xmlInput = editorXml;
            }
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance()
                    .begin(org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.XSLT, xsltName);
            String result;
            OutputFormat format = chosen != null ? chosen : OutputFormat.XML;
            org.fxt.freexmltoolkit.service.XsltTransformationResult fullResult = null;
            org.fxt.freexmltoolkit.service.XsltTransformationResult traceResult = null;
            org.fxt.freexmltoolkit.service.XsltProfile profile = null;
            String profiledXslt = null;
            try {
                String xsltContent = xsltEditorText != null ? xsltEditorText
                        : Files.readString(xslt.toPath(), StandardCharsets.UTF_8);
                // Auto: detect the format from the stylesheet's xsl:output declaration.
                format = chosen != null ? chosen : TransformRunner.detectXsltOutputFormat(xsltContent);
                // One single run: the full result feeds the output view, the statistics
                // probe, and (when requested) the Profile/Trace report tabs.
                if (wantProfile) {
                    // the profiler's run delivers the output too; a trace needs its own run
                    var profiled = TransformRunner.profile(xmlInput, xmlBaseUri, xsltContent, xsltSystemId, params,
                            format, 0);
                    fullResult = profiled.result();
                    profile = profiled.profile();
                    profiledXslt = xsltContent;
                    if (wantTrace && fullResult.isSuccess()) {
                        traceResult = TransformRunner.transformForReport(xmlInput, xmlBaseUri, xsltContent,
                                xsltSystemId, params, format);
                    }
                } else {
                    fullResult = wantTrace
                            ? TransformRunner.transformForReport(xmlInput, xmlBaseUri, xsltContent, xsltSystemId, params, format)
                            : TransformRunner.xsltTransformResult(xmlInput, xmlBaseUri, xsltContent, xsltSystemId, params, format);
                    traceResult = fullResult;
                }
                result = fullResult.isSuccess()
                        ? fullResult.getOutputContent()
                        : "ERROR: " + fullResult.getErrorMessage();
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
                fullResult = org.fxt.freexmltoolkit.service.XsltTransformationResult.error(e.getMessage(), e);
            }
            if (fullResult != null && fullResult.isSuccess()) {
                probe.phase("Compile", fullResult.getCompilationTime());
                probe.phase("Transform", fullResult.getTransformationTime());
            }
            boolean ok = !result.startsWith("ERROR");
            UsageEvents.xsltTransformed(1, t0, live, fullResult != null ? fullResult.getErrorPhase() : null,
                    TransformRunner.failureOf(fullResult), xmlInput.length());
            long elapsedMs = probe.finish(xmlInput.length(), ok ? result.length() : -1, ok,
                    org.fxt.freexmltoolkit.service.ExecutionStats.firstLine(result));
            String finalResult = result;
            OutputFormat finalFormat = format;
            TransformOutputPanel.ResultSource resultSource = ok
                    ? resultSource(inputPath, openInputSchema, inputFile != null ? xmlInput : null) : null;
            org.fxt.freexmltoolkit.service.XsltTransformationResult report =
                    (wantProfile || wantTrace) ? fullResult : null;
            org.fxt.freexmltoolkit.service.XsltTransformationResult trace = traceResult;
            org.fxt.freexmltoolkit.service.XsltProfile finalProfile = profile;
            String xsltForProfile = profiledXslt;
            String xmlForProfile = xmlInput;
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
                    out.showTransformResult(finalResult, finalFormat, elapsedMs, resultSource);
                }
                if (!finalResult.startsWith("ERROR") && autoOpenResultTab.isSelected()) {
                    out.openResultInEditor();
                }
                if (report != null && report.isSuccess()) {
                    if (wantProfile && finalProfile != null) {
                        showProfile(finalProfile, report, xmlForProfile, xmlBaseUri, xsltForProfile, xsltSystemId,
                                params, finalFormat, xsltName, null);
                    }
                    if (wantTrace && trace != null && trace.isSuccess()) {
                        editorHost.openToolTab(I18n.t("TransformPanel.trace"), "bi-list-columns",
                                new org.fxt.freexmltoolkit.controls.shell.editor.debug.TraceView(trace));
                    }
                }
            });
        });
    }

    /**
     * Shows a profile in its tool tab ({@link ProfileTabs}); Profile Again measures once more with
     * one warm-up run.
     */
    private void showProfile(org.fxt.freexmltoolkit.service.XsltProfile profile,
                             org.fxt.freexmltoolkit.service.XsltTransformationResult result, String xml,
                             String xmlBaseUri, String xsltContent, String xsltSystemId, Map<String, Object> params,
                             OutputFormat format, String stylesheetName, javafx.scene.control.Tab tab) {
        ProfileTabs.show(editorHost, I18n.t("TransformPanel.profile", stylesheetName), profile, result,
                () -> TransformRunner.profile(xml, xmlBaseUri, xsltContent, xsltSystemId, params, format, 1),
                message -> out.showFailure(I18n.t("TransformPanel.profiling-failed", message)), tab);
    }

    /**
     * Worker thread: the origin of a transform result - the input's location and schema (the one
     * bound to its open tab, else the one an input file declares).
     *
     * @param inputXml the input text when it was read from a file that may not be open, else {@code null}
     */
    static TransformOutputPanel.ResultSource resultSource(java.nio.file.Path inputPath, File openInputSchema,
                                                          String inputXml) {
        File schema = openInputSchema;
        if (schema == null && inputXml != null && inputPath != null) {
            // the document element with its attributes is enough to find the declaration
            String head = inputXml.length() > 65_536 ? inputXml.substring(0, 65_536) : inputXml;
            java.nio.file.Path dir = inputPath.toAbsolutePath().getParent();
            schema = EditorSchemaBinding.declaredSchemaFile(head, dir != null ? dir.toFile() : null).orElse(null);
        }
        return inputPath == null && schema == null ? null : new TransformOutputPanel.ResultSource(inputPath, schema);
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
        chooser.setTitle(I18n.t("TransformPanel.select-xslt"));
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
            return org.fxt.freexmltoolkit.i18n.I18n.or("TransformPanel.FormatChoice." + name(), label);
        }
    }
}
