package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The Validation activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Validation" (node 40:48): a SOURCES section showing the
 * bound XSD/Schematron (XML family) or JSON Schema (JSON documents) with
 * "Change" links, a Single-file/Batch segmented toggle, a primary "Run
 * Validation" button, a RESULTS list with per-file severity icons and
 * problem-count badges (batch runs), and the PROBLEMS list. Secondary features
 * (Schematron tools, FundsXML, the validate-while-typing toggle, the batch text
 * report) live in the header's overflow (⋮) menu. Validation runs off the UI
 * thread via {@link ValidationRunner}; selecting a problem jumps to its line.
 */
public class ValidationPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> problems = FXCollections.observableArrayList();
    private final ListView<ValidationProblem> problemsList = new ListView<>(problems);
    private final ObservableList<ValidationRunner.FileValidationResult> batchResults =
            FXCollections.observableArrayList();
    private final ListView<ValidationRunner.FileValidationResult> batchList = new ListView<>(batchResults);
    @FXML private Label status;
    @FXML private PanelProgress progress;
    @FXML private Label xsdName;
    @FXML private Label schematronName;
    @FXML private Label jsonSchemaName;
    private final Label resultsHeaderLabel = new Label("RESULTS");
    private final MenuButton xsdFavoritesMenu = new MenuButton();
    private final MenuButton schematronFavoritesMenu;
    @FXML private MenuButton overflowMenu;
    @FXML private CheckMenuItem liveValidation;
    @FXML private PanelActionList authoringTools;
    /** Single-file mode: "N problems — show below the editor" (jumps to the PROBLEMS dock). */
    private final javafx.scene.control.Button problemsJump =
            PanelActionList.inlineRow("", "bi-arrow-down-square", this::revealProblemsDock);
    @FXML private ToggleGroup modeGroup;
    @FXML private ToggleButton singleMode;
    @FXML private ToggleButton batchMode;
    @FXML private Button run;
    @FXML private PanelActionList tools;
    @FXML private Button exportProblems;
    @FXML private Button schematronReportButton;
    @FXML private Button batchReportButton;
    private SchematronReportData lastSchematronReport;
    @FXML private ContextMenu batchSourceMenu;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(600));
    private String lastBatchReport;
    private final javafx.beans.property.BooleanProperty batchReportMissing =
            new javafx.beans.property.SimpleBooleanProperty(true);
    // SOURCES rows; the visible set follows the active document's type (XML family
    // shows XSD + Schematron, JSON shows the JSON Schema row).
    @FXML private SourceRow xsdRow;
    @FXML private SourceRow schematronRow;
    @FXML private SourceRow jsonSchemaRow;
    private final MenuButton jsonSchemaFavoritesMenu;

    /** Loads the layout from {@code /pages/panels/ValidationPanel.fxml} and wires the data-driven parts. */
    public ValidationPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/ValidationPanel.fxml");

        // --- SOURCES ------------------------------------------------------
        xsdFavoritesMenu.setGraphic(icon("bi-star", 13));
        xsdFavoritesMenu.getStyleClass().add("fxt-vp-source-fav");
        xsdFavoritesMenu.setOnShowing(e -> refreshXsdFavoritesMenu());
        xsdRow.addExtras(xsdFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(xsdRow,
                org.fxt.freexmltoolkit.service.DragDropService.XSD_EXTENSIONS, this::useXsd);
        schematronFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.SCHEMATRON,
                "Schematron favorites", this::useSchematron);
        schematronRow.addExtras(schematronFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(schematronRow,
                org.fxt.freexmltoolkit.service.DragDropService.SCHEMATRON_EXTENSIONS, this::useSchematron);
        // Favorites are typed by extension, so this lists all favorited .json files —
        // schemas and data documents are indistinguishable there.
        jsonSchemaFavoritesMenu = FavoritesMenu.create(
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.JSON,
                "JSON Schema favorites", this::useJsonSchema);
        jsonSchemaRow.addExtras(jsonSchemaFavoritesMenu);
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(jsonSchemaRow,
                org.fxt.freexmltoolkit.service.DragDropService.JSON_SCHEMA_EXTENSIONS, this::useJsonSchema);
        // One click on a bound source opens it in the editor for direct editing.
        makeSourceNameOpenable(xsdName, () -> editorHost.activeSchemaProperty().get());
        makeSourceNameOpenable(schematronName, editorHost::getActiveSchematron);
        makeSourceNameOpenable(jsonSchemaName, () -> editorHost.activeSchemaProperty().get());
        refreshSchematronStatus();
        refreshXsdStatus();
        refreshJsonSchemaStatus();
        updateSourceRowVisibility();
        // Opening the panel binds the schema referenced inside the XML (xsi:schemaLocation /
        // xsi:noNamespaceSchemaLocation) when none is bound yet; an explicit Change or
        // favorite choice still wins because a bound schema is never overridden.
        editorHost.redetectSchemaForActiveDocument();

        // A segmented control always has exactly one active segment.
        modeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                modeGroup.selectToggle(oldV);
            }
        });

        // --- TOOLS ---------------------------------------------------------
        exportProblems.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(problems));
        batchReportButton.disableProperty().bind(batchReportMissing);

        // --- RESULTS (batch) -----------------------------------------------
        resultsHeaderLabel.setId("validation-results-header");
        batchList.setId("validation-results-list");
        batchList.getStyleClass().add("fxt-vp-results");
        batchList.setCellFactory(lv -> new BatchResultCell());
        batchList.setVisible(false);
        batchList.setManaged(false);
        batchList.setFixedCellSize(31);
        // Depend on the list itself (not Bindings.size(...)): a lazy size binding
        // that is never read stops firing after its first invalidation.
        batchList.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(180.0, batchResults.size() * 31.0 + 2), batchResults));
        batchList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                setProblems(newV.problems());
            }
        });
        batchList.setOnMouseClicked(e -> {
            var selected = batchList.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && selected != null && selected.file().exists()) {
                editorHost.openFile(selected.file().toPath());
            }
        });

        // --- RESULTS section (collapsible; natural height) -----------------
        // batchList keeps its own data-driven visibility inside this wrapper, so the
        // section's collapse state and the "has results" state compose cleanly.
        VBox resultsBody = new VBox(batchList);
        CollapsibleSection resultsSection = new CollapsibleSection(resultsHeaderLabel, resultsBody, false);
        resultsSection.setId("validation-results-section");

        // --- PROBLEMS -------------------------------------------------------
        problemsList.setId("validation-problems-list");
        problemsList.getStyleClass().add("fxt-vp-problems");
        // A small min height lets the surrounding VBox shrink the list to the available
        // space (instead of the list forcing the panel taller than the viewport), so the
        // list's own vertical scrollbar appears when there are more problems than fit.
        problemsList.setMinHeight(0);
        VBox.setVgrow(problemsList, Priority.ALWAYS);
        problemsList.setPlaceholder(new Label("No problems"));
        problemsList.setCellFactory(lv -> new ProblemCell());
        problemsList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.line() > 0) {
                editorHost.goToLine(newV.line(), newV.column());
            }
        });

        Label problemsLabel = new Label("PROBLEMS");
        CollapsibleSection problemsSection = new CollapsibleSection(problemsLabel, problemsList, true);
        problemsSection.setId("validation-problems-section");

        // Continuous (debounced) validation: re-validate shortly after the active
        // document changes (typing / tab switch / schema binding), when enabled.
        debounce.setOnFinished(e -> {
            if (liveValidation.isSelected()) {
                revalidate(true);
            }
        });
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> scheduleRevalidation());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> {
            refreshXsdStatus();
            refreshJsonSchemaStatus();
            updateSourceRowVisibility();
            editorHost.redetectSchemaForActiveDocument();
            scheduleRevalidation();
        });
        editorHost.activeSchemaProperty().addListener((obs, oldV, newV) -> {
            refreshXsdStatus();
            refreshJsonSchemaStatus();
            scheduleRevalidation();
        });
        // After a quick fix is applied, re-validate immediately (even with live
        // validation off) so the fixed problem disappears from every list.
        editorHost.getQuickFixController().setOnFixApplied(this::revalidate);

        // RESULTS and the per-file PROBLEMS list only exist for batch runs; a single-file run
        // lists its problems in the PROBLEMS dock below the editor, and the panel only shows
        // the count with a jump there (UX plan 2.4).
        for (CollapsibleSection batchOnly : new CollapsibleSection[]{resultsSection, problemsSection}) {
            batchOnly.visibleProperty().bind(batchMode.selectedProperty());
            batchOnly.managedProperty().bind(batchMode.selectedProperty());
        }
        problemsJump.setId("validation-show-problems");
        problemsJump.getStyleClass().add("fxt-vp-problems-jump");
        Runnable syncJump = () -> {
            boolean show = !isBatchMode() && !problems.isEmpty();
            problemsJump.setText(EditorActions.problemCount(problems.size()) + " — show below the editor");
            problemsJump.setVisible(show);
            problemsJump.setManaged(show);
        };
        problems.addListener((javafx.collections.ListChangeListener<ValidationProblem>) c -> syncJump.run());
        batchMode.selectedProperty().addListener((obs, oldV, newV) -> syncJump.run());
        syncJump.run();
        getChildren().add(getChildren().indexOf(status) + 1, problemsJump);
        // Sections are inserted before the TOOLS groups: results belong to the run above them.
        getChildren().addAll(getChildren().indexOf(status) + 2, java.util.List.of(resultsSection, problemsSection));
    }

    @FXML
    public void onLiveValidationToggled() {
        scheduleRevalidation();
    }

    /** Batch mode asks for the files or folder first; single mode validates the active document. */
    @FXML
    public void onRun() {
        if (isBatchMode()) {
            batchSourceMenu.show(run, Side.BOTTOM, 0, 0);
        } else {
            revalidate();
        }
    }

    @FXML
    public void onOpenBatchReport() {
        if (lastBatchReport != null) {
            editorHost.openGeneratedDocument(lastBatchReport, EditorFileType.OTHER, "BatchReport.txt");
        }
    }

    /**
     * Opens the detailed Schematron report of the last validation run as a tool tab
     * (findings with rule/test and XPath context; savable as HTML or raw SVRL).
     */
    @FXML
    public void openSchematronReport() {
        if (lastSchematronReport == null) {
            PanelStatus.precondition(status,
                    "No Schematron report yet — bind a Schematron and validate first");
            return;
        }
        editorHost.openToolTab("Schematron Report", "bi-journal-check",
                new SchematronReportView(lastSchematronReport, editorHost));
    }

    /** Opens the Schematron rule-template library as a tool tab; inserts into the active editor. */
    @FXML
    public void openSchematronTemplates() {
        var library = new org.fxt.freexmltoolkit.controls.SchematronTemplateLibrary();
        library.setTemplateInsertCallback(editorHost::insertTextAtCaret);
        editorHost.openToolTab("Schematron Templates", "bi-collection", library);
    }

    /** Opens the Schematron tester as a tool tab, pre-loading the bound Schematron if any. */
    @FXML
    public void openSchematronTester() {
        var tester = new org.fxt.freexmltoolkit.controls.SchematronTester();
        File schematron = editorHost.getActiveSchematron();
        if (schematron != null) {
            tester.setSchematronFile(schematron);
        }
        editorHost.openToolTab("Schematron Tester", "bi-play-circle", tester);
    }

    /** Opens the visual Schematron rule builder as a tool tab. */
    @FXML
    public void openSchematronBuilder() {
        editorHost.openToolTab("Schematron Builder", "bi-tools",
                new org.fxt.freexmltoolkit.controls.SchematronVisualBuilder());
    }

    /**
     * Runs the Schematron error detector over the active document (off-thread) and shows the
     * categorised issues (XML-syntax / structural / XPath / semantic / best-practice) in a tool tab.
     */
    @FXML
    public void openSchematronCheck() {
        String text = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var issues = SchematronCheckRunner.check(text);
            UsageEvents.schematronChecked(issues.size(), t0);
            javafx.application.Platform.runLater(() -> editorHost.openToolTab(
                    "Schematron Check", "bi-bug", new SchematronCheckResultView(issues)));
        });
    }

    /** Opens the Schematron documentation generator as a tool tab. */
    @FXML
    public void openSchematronDocumentation() {
        editorHost.openToolTab("Schematron Documentation", "bi-file-earmark-text",
                new org.fxt.freexmltoolkit.controls.SchematronDocumentationGenerator());
    }

    /** Schedules a debounced re-validation if live validation is on and the active doc is validatable. */
    private void scheduleRevalidation() {
        if (liveValidation.isSelected() && (isXmlFamilyActive() || isJsonActive())) {
            debounce.playFromStart();
        }
    }

    /** @return {@code true} if the active document is XML-family (not JSON / other). */
    private boolean isXmlFamilyActive() {
        return editorHost.getActiveDocument().map(d -> switch (d.getFileType()) {
            case XML, XSD, XSLT, SCHEMATRON, XPROC -> true;
            default -> false;
        }).orElse(false);
    }

    /** @return {@code true} if the active document is JSON (validated against a JSON Schema). */
    private boolean isJsonActive() {
        return editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
    }

    /**
     * Shows the SOURCES rows matching the active document's type: the JSON Schema row
     * for JSON documents, the XSD + Schematron rows for everything else (both
     * {@code visible} and {@code managed}, so hidden rows collapse out of the layout).
     */
    private void updateSourceRowVisibility() {
        boolean json = isJsonActive();
        xsdRow.setVisible(!json);
        xsdRow.setManaged(!json);
        schematronRow.setVisible(!json);
        schematronRow.setManaged(!json);
        jsonSchemaRow.setVisible(json);
        jsonSchemaRow.setManaged(json);
    }

    /** Runs validation of the active document (XSD + optional Schematron), async. */
    public void revalidate() {
        revalidate(false);
    }

    /**
     * @param live {@code true} when triggered by continuous (typing) validation rather than an
     *             explicit user action — only affects usage statistics (throttled)
     */
    private void revalidate(boolean live) {
        if (editorHost.getActiveDocument().isEmpty()) {
            PanelStatus.precondition(status, "No document open");
            setProblems(List.of());
            editorHost.setValidationStatus(EditorHost.ValidationState.NOT_VALIDATED, 0, "Not validated");
            return;
        }
        // Re-check the type at run time: the debounce timer may fire after the user
        // switched to a non-XML tab (e.g. an XQuery document) — validating that as
        // XML would report bogus "not well-formed" problems.
        boolean validatable = isXmlFamilyActive() || editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
        if (!validatable) {
            PanelStatus.precondition(status, "Not a validatable document type");
            setProblems(List.of());
            editorHost.setValidationStatus(EditorHost.ValidationState.NOT_VALIDATED, 0, "Not validated");
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        if (content.isBlank()) {
            // Nothing to validate (e.g. a document still loading) — avoid parsing empty text.
            PanelStatus.precondition(status, "Empty document");
            setProblems(List.of());
            editorHost.setValidationStatus(EditorHost.ValidationState.NOT_VALIDATED, 0, "Not validated");
            return;
        }
        boolean json = isJsonActive();
        // Resolved on the worker thread: reconciles the binding with the schema location
        // declared in the buffer (added/changed/removed references take effect this run) —
        // the xsi:* declarations for the XML family, a top-level "$schema" for JSON.
        var schemaSupplier = editorHost.schemaForValidation(content);
        File schematron = editorHost.getActiveSchematron();
        String documentName = editorHost.getActiveDocument()
                .map(OpenDocument::getDisplayName).orElse(null);
        var docKind = editorHost.getActiveDocument().map(d -> d.getFileType().docKind()).orElse(null);
        PanelStatus.info(status, "Validating…");
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var probe = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().begin(
                    org.fxt.freexmltoolkit.service.ExecutionStats.OperationType.VALIDATION,
                    documentName != null ? documentName : "document");
            File schema = schemaSupplier.get();
            ValidationRunner.RunResult runResult = json
                    ? new ValidationRunner.RunResult(
                            ValidationRunner.validateJson(content, schema), null)
                    : ValidationRunner.runWithReport(content, schema, schematron, documentName);
            List<ValidationProblem> result = runResult.problems();
            UsageEvents.validated(UsageEvents.schemaKind(json, schema != null, !json && schematron != null),
                    docKind, content, result.size(), t0, false, live);
            probe.phase("XSD", runResult.xsdMillis());
            probe.phase("Schematron", runResult.schematronMillis());
            long elapsedMs = probe.finish(content.length(), -1, result.isEmpty(),
                    result.isEmpty() ? "" : result.get(0).message());
            boolean showStats = org.fxt.freexmltoolkit.service.ExecutionStatsService.getInstance().isEnabled();
            Platform.runLater(() -> {
                setProblems(result);
                lastSchematronReport = runResult.schematronReport();
                schematronReportButton.setDisable(lastSchematronReport == null);
                boolean hasSchema = json ? schema != null : (schema != null || schematron != null);
                String summary = (result.isEmpty()
                        ? (hasSchema ? "Valid" : EditorActions.WELL_FORMED_NO_SCHEMA)
                        : EditorActions.problemCount(result.size()))
                        + (showStats ? " · " + elapsedMs + " ms" : "");
                if (result.isEmpty() && hasSchema) {
                    PanelStatus.success(status, summary);
                } else if (result.isEmpty()) {
                    // Only well-formedness was checked: neutral, and the SOURCES slot above
                    // ("No XSD bound — choose or drop a file") is the next step.
                    PanelStatus.info(status, summary);
                } else {
                    PanelStatus.info(status, summary);
                }
                editorHost.setValidationStatus(
                        result.isEmpty() ? EditorHost.ValidationState.VALID : EditorHost.ValidationState.INVALID,
                        result.size(), summary);
            });
        });
    }

    /**
     * Replaces the problems list, clearing the ListView selection first. This
     * avoids a JavaFX {@code ListViewBehavior} {@code IndexOutOfBoundsException}
     * that can occur when {@code items.setAll(...)} runs (e.g. on every keystroke
     * via continuous validation) while a row is selected. Also publishes the
     * problems to the host so the PROBLEMS panel below the editor mirrors them.
     */
    private void setProblems(List<ValidationProblem> result) {
        problemsList.getSelectionModel().clearSelection();
        problems.setAll(result);
        editorHost.setActiveProblems(result);
    }

    /** @return the number of problems currently shown (for tests/observers). */
    public int getProblemCount() {
        return problems.size();
    }

    /** @return the current validation status text (e.g. "Well-formed · no schema bound", "Valid", "N problems"). */
    public String getStatusText() {
        return status.getText();
    }

    /** @return {@code true} while the "Validate while typing" toggle is on (for tests/observers). */
    public boolean isLiveValidationEnabled() {
        return liveValidation.isSelected();
    }

    private void revealProblemsDock() {
        editorHost.revealProblems();
    }

    /** @return the TOOLS rows' labels in display order (for tests/observers). */
    public List<String> toolLabels() {
        List<String> labels = new java.util.ArrayList<>(tools.labels());
        labels.addAll(authoringTools.labels());
        return labels;
    }

    /** @return all ⋮-menu item texts, flattened including submenu entries (for tests/observers). */
    public List<String> overflowMenuItemTexts() {
        List<String> texts = new ArrayList<>();
        collectMenuTexts(overflowMenu.getItems(), texts);
        return texts;
    }

    private static void collectMenuTexts(List<MenuItem> items, List<String> into) {
        for (MenuItem item : items) {
            if (item instanceof SeparatorMenuItem) {
                continue;
            }
            if (item.getText() != null) {
                into.add(item.getText());
            }
            if (item instanceof Menu menu) {
                collectMenuTexts(menu.getItems(), into);
            }
        }
    }

    /** Switches between Single-file and Batch mode (the segmented toggle). */
    public void setBatchMode(boolean batch) {
        (batch ? batchMode : singleMode).setSelected(true);
    }

    /** @return {@code true} while the Batch segment is active. */
    public boolean isBatchMode() {
        return batchMode.isSelected();
    }

    /** @return the number of per-file batch results currently shown (for tests/observers). */
    public int batchResultCount() {
        return batchResults.size();
    }

    /** @return how many of the shown batch results failed (for tests/observers). */
    public long batchFailedCount() {
        return batchResults.stream().filter(ValidationRunner.FileValidationResult::failed).count();
    }

    /** Validates the given files against the bound XSD/Schematron and fills the RESULTS list (async). */
    public void runBatch(java.util.List<File> files) {
        runBatch(files, editorHost.getActiveSchematron());
    }

    /**
     * Validates the given files against the bound XSD and the given Schematron and
     * fills the RESULTS list (async). Used by the Explorer's one-click Schematron
     * validation, where files may be validated without any open document to bind
     * the Schematron to.
     *
     * @param files      the XML files to validate
     * @param schematron the Schematron to validate against (may be {@code null})
     */
    public void runBatch(java.util.List<File> files, File schematron) {
        if (files == null || files.isEmpty()) {
            return;
        }
        setBatchMode(true); // RESULTS is only shown in batch mode
        File xsd = editorHost.activeSchemaProperty().get();
        int total = files.size();
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        PanelStatus.info(status, "Validating " + total + " file(s)…");
        progress.beginDeterminate(total, () -> {
            cancelled.set(true);
            PanelStatus.info(status, "Cancelling…");
        });
        FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            List<ValidationRunner.FileValidationResult> results = ValidationRunner.batch(files, xsd, schematron,
                    done -> javafx.application.Platform.runLater(() -> progress.setProgress(done)),
                    cancelled::get);
            UsageEvents.batchValidated(UsageEvents.schemaKind(false, xsd != null, schematron != null),
                    results.size(), (int) results.stream().mapToLong(ValidationRunner.FileValidationResult::errorCount).sum(), t0,
                    cancelled.get());
            String report = ValidationRunner.report(results, xsd, schematron);
            javafx.application.Platform.runLater(() -> {
                progress.finish();
                showBatchResults(results, report);
                if (cancelled.get()) {
                    // showBatchResults set a "N valid/failed" status; correct it for a partial run.
                    PanelStatus.info(status,
                            "Cancelled — validated " + results.size() + " of " + total + " file(s)");
                }
            });
        });
    }

    /** Publishes a finished batch run: RESULTS header + list and the TOOLS report row. */
    void showBatchResults(List<ValidationRunner.FileValidationResult> results, String report) {
        batchList.getSelectionModel().clearSelection();
        batchResults.setAll(results);
        lastBatchReport = report;
        batchReportMissing.set(false);
        long failed = batchFailedCount();
        resultsHeaderLabel.setText(failed > 0
                ? "RESULTS · " + failed + " OF " + results.size() + " FAILED"
                : "RESULTS · " + results.size() + " OK");
        batchList.setVisible(!results.isEmpty());
        batchList.setManaged(!results.isEmpty());
        if (failed > 0) {
            PanelStatus.info(status, failed + " of " + results.size() + " file(s) failed");
        } else {
            PanelStatus.success(status, results.size() + " file(s) valid");
        }
    }

    @FXML
    public void onChooseBatch() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select XML files to validate");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XML", "*.xml"));
        java.util.List<File> files = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenMultipleDialog(chooser, 
                getScene() != null ? getScene().getWindow() : null);
        runBatch(files);
    }

    @FXML
    public void onChooseBatchFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select folder with XML files");
        File dir = org.fxt.freexmltoolkit.util.FileChooserHelper.showDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (dir != null) {
            runBatchForDirectory(dir);
        }
    }

    /**
     * Recursively collects every {@code *.xml} file under {@code dir} (async) and
     * validates the result like {@link #runBatch(java.util.List)}.
     */
    public void runBatchForDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        PanelStatus.info(status, "Scanning " + dir.getName() + "…");
        FxtGui.executorService.submit(() -> {
            List<File> files;
            try (var paths = java.nio.file.Files.walk(dir.toPath())) {
                files = paths.filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".xml"))
                        .map(java.nio.file.Path::toFile)
                        .sorted()
                        .toList();
            } catch (java.io.IOException ex) {
                Platform.runLater(() -> PanelStatus.failure(status, "Validation failed",
                        "Could not scan " + dir.getName() + ": " + ex.getMessage()));
                return;
            }
            Platform.runLater(() -> {
                if (files.isEmpty()) {
                    PanelStatus.info(status, "No XML files found in " + dir.getName());
                } else {
                    runBatch(files);
                }
            });
        });
    }

    /** Lets the user pick an XSD to validate the active XML document against. */
    @FXML
    public void onChooseXsd() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select XSD schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML Schema", "*.xsd"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            useXsd(file);
        }
    }

    /** Rebuilds the XSD-favorites quick-select menu from the favorites store (type XSD). */
    public void refreshXsdFavoritesMenu() {
        xsdFavoritesMenu.getItems().clear();
        java.util.List<org.fxt.freexmltoolkit.domain.FileFavorite> favorites;
        try {
            favorites = org.fxt.freexmltoolkit.service.FavoritesService.getInstance()
                    .getFavoritesByType(org.fxt.freexmltoolkit.domain.FileFavorite.FileType.XSD);
        } catch (Throwable t) {
            org.apache.logging.log4j.LogManager.getLogger(ValidationPanel.class)
                    .warn("Could not load XSD favorites; showing an empty quick-select menu", t);
            favorites = java.util.List.of();
        }
        for (var favorite : favorites) {
            String name = favorite.getName() != null && !favorite.getName().isBlank()
                    ? favorite.getName() : new File(favorite.getFilePath()).getName();
            MenuItem item = new MenuItem(name);
            item.setOnAction(e -> useXsd(new File(favorite.getFilePath())));
            xsdFavoritesMenu.getItems().add(item);
        }
        xsdFavoritesMenu.setDisable(xsdFavoritesMenu.getItems().isEmpty());
    }

    /** @return the favorite-XSD names currently in the quick-select menu (for tests/observers). */
    public java.util.List<String> xsdFavoriteNames() {
        return xsdFavoritesMenu.getItems().stream().map(MenuItem::getText).toList();
    }

    /**
     * Binds {@code xsd} to the active document (for validation and IntelliSense) and
     * re-validates against it. No-op if the active editor does not support schemas.
     *
     * @param xsd the schema file to validate against
     */
    public void useXsd(File xsd) {
        // The schema is parsed off the FX thread; the future completes back on it.
        editorHost.setSchemaForActiveDocument(xsd).thenAccept(bound -> {
            if (bound) {
                refreshXsdStatus();
                revalidate();
            }
        });
    }

    private void refreshXsdStatus() {
        File xsd = editorHost.activeSchemaProperty().get();
        showOpenableSource(xsdRow, xsd);
    }

    private void refreshJsonSchemaStatus() {
        // activeSchema carries whichever schema kind the active tab holds (XSD for the
        // XML family, JSON Schema for JSON) — the visible row decides the interpretation.
        File schema = editorHost.activeSchemaProperty().get();
        jsonSchemaRow.showFile(schema);
    }

    private void refreshSchematronStatus() {
        File schematron = editorHost.getActiveSchematron();
        showOpenableSource(schematronRow, schematron);
    }

    /**
     * Makes a source-row file name clickable: one click opens the currently bound
     * file in the editor for direct editing (no-op while the source is unset).
     */
    private void makeSourceNameOpenable(Label label, java.util.function.Supplier<File> file) {
        label.getStyleClass().add("fxt-vp-source-open");
        label.setOnMouseClicked(e -> {
            File bound = file.get();
            if (bound != null && bound.exists()) {
                editorHost.openFile(bound.toPath());
            }
        });
    }

    /** Shows a bound file whose name opens it in the editor (path + hint as tooltip). */
    private static void showOpenableSource(SourceRow row, File file) {
        row.showName(file != null ? file.getName() : null,
                file != null ? file.getAbsolutePath() + "\nClick to open in the editor" : null);
    }

    @FXML
    public void onChooseSchematron() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Schematron");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Schematron", "*.sch", "*.schematron"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            useSchematron(file);
        }
    }

    /** Binds {@code schematron} to the active document (from the chooser or a favorite). */
    private void useSchematron(File schematron) {
        editorHost.setActiveSchematron(schematron);
        refreshSchematronStatus();
    }

    /** @return the favorite-Schematron names currently in the quick-select menu (for tests). */
    public java.util.List<String> schematronFavoriteNames() {
        FavoritesMenu.populate(schematronFavoritesMenu,
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.SCHEMATRON, this::useSchematron);
        return FavoritesMenu.leafNames(schematronFavoritesMenu);
    }

    /** @return the favorite-JSON names currently in the JSON Schema quick-select menu (for tests). */
    public java.util.List<String> jsonSchemaFavoriteNames() {
        FavoritesMenu.populate(jsonSchemaFavoritesMenu,
                org.fxt.freexmltoolkit.domain.FileFavorite.FileType.JSON, this::useJsonSchema);
        return FavoritesMenu.leafNames(jsonSchemaFavoritesMenu);
    }

    @FXML
    public void onChooseJsonSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select JSON Schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Schema", "*.json"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            useJsonSchema(file);
        }
    }

    /**
     * Binds {@code schema} to the active JSON document (from the chooser, a favorite,
     * or a drop) and re-validates against it. No-op if the active editor does not
     * support schemas.
     *
     * @param schema the JSON Schema file to validate against
     */
    public void useJsonSchema(File schema) {
        editorHost.setSchemaForActiveDocument(schema).thenAccept(bound -> {
            if (bound) {
                refreshJsonSchemaStatus();
                revalidate();
            }
        });
    }

    /** Binds the JSON Schema used when validating a JSON document (for tests/observers). */
    public void setJsonSchema(File schema) {
        useJsonSchema(schema);
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * Exports the currently shown problems to a user-chosen .xlsx file (off-thread),
     * then reports success/failure. No-op when there are no problems.
     */
    @FXML
    public void exportProblemsToExcel() {
        if (problems.isEmpty()) {
            return;
        }
        List<ValidationProblem> snapshot = List.copyOf(problems);
        String sourceName = editorHost.getActiveDocument()
                .map(OpenDocument::getPath)
                .map(p -> p.getFileName().toString())
                .orElse(null);
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export problems to Excel");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"));
        chooser.setInitialFileName((sourceName != null ? stripExtension(sourceName) : "validation") + "-problems.xlsx");
        File target = org.fxt.freexmltoolkit.util.FileChooserHelper.showSaveDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (target == null) {
            return;
        }
        File output = target.getName().toLowerCase().endsWith(".xlsx")
                ? target : new File(target.getParentFile(), target.getName() + ".xlsx");
        PanelStatus.info(status, "Exporting " + EditorActions.problemCount(snapshot.size()) + "…");
        FxtGui.executorService.submit(() -> {
            try {
                ValidationExcelExporter.export(snapshot, sourceName, output.toPath());
                Platform.runLater(() -> {
                    PanelStatus.success(status, "Exported " + EditorActions.problemCount(snapshot.size()));
                    org.fxt.freexmltoolkit.util.DialogHelper.showInformation("Export problems",
                            null, "Wrote " + EditorActions.problemCount(snapshot.size()) + " to:\n" + output.getAbsolutePath());
                });
            } catch (Exception ex) {
                Platform.runLater(() -> PanelStatus.failure(status, "Export problems",
                        "Could not write the Excel file.",
                        org.fxt.freexmltoolkit.util.DialogHelper.Remedies.EXPORT, ex));
            }
        });
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * A side-panel section with a clickable header (chevron + title + optional trailing
     * controls) that collapses/expands its body. Collapsing also drops the section's
     * {@code Vgrow} so a collapsed section no longer reserves vertical space.
     */
    private static final class CollapsibleSection extends VBox {
        private final Region body;
        private final IconifyIcon chevron;
        private final boolean growWhenExpanded;
        private boolean expanded = true;

        CollapsibleSection(Label titleLabel, Region body, boolean growWhenExpanded) {
            this.body = body;
            this.growWhenExpanded = growWhenExpanded;
            getStyleClass().add("fxt-vp-section");

            chevron = new IconifyIcon("bi-chevron-down");
            chevron.setIconSize(11);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox clickable = new HBox(6, chevron, titleLabel, spacer);
            clickable.getStyleClass().add("fxt-vp-section-header");
            clickable.setAlignment(Pos.CENTER_LEFT);
            clickable.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(clickable, Priority.ALWAYS);
            clickable.setOnMouseClicked(e -> toggle());

            HBox header = new HBox(4, clickable);
            header.setAlignment(Pos.CENTER_LEFT);

            if (growWhenExpanded) {
                VBox.setVgrow(body, Priority.ALWAYS);
                VBox.setVgrow(this, Priority.ALWAYS);
            }
            getChildren().addAll(header, body);
        }

        private void toggle() {
            setExpanded(!expanded);
        }

        private void setExpanded(boolean expand) {
            expanded = expand;
            body.setVisible(expand);
            body.setManaged(expand);
            chevron.setIconLiteral(expand ? "bi-chevron-down" : "bi-chevron-right");
            // A collapsed grow-section must release its vertical space; a non-grow
            // section never grabs space either way.
            VBox.setVgrow(this, (expand && growWhenExpanded) ? Priority.ALWAYS : Priority.NEVER);
        }
    }

    /** Renders a problem as "[source] Ln N: message" with a colored severity icon. */
    private final class ProblemCell extends ListCell<ValidationProblem> {
        private ProblemCell() {
            // Follow the ListView width (ellipsize) instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ValidationProblem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                setContextMenu(null);
                return;
            }
            String line = item.line() > 0 ? "Ln " + item.line() + ": " : "";
            setText("[" + item.source() + "] " + line + item.message());
            boolean warning = "warning".equalsIgnoreCase(item.severity());
            IconifyIcon icon = icon(warning ? "bi-exclamation-triangle-fill" : "bi-x-circle", 13);
            icon.getStyleClass().add(warning ? "sev-warning" : "sev-error");
            setGraphic(icon);
            setContextMenu(item.hasFixes()
                    ? new javafx.scene.control.ContextMenu(
                            org.fxt.freexmltoolkit.controls.shell.editor.quickfix.QuickFixMenuFactory
                                    .buildQuickFixMenu(item, fix -> editorHost
                                            .getQuickFixController().applyFix(item, fix)))
                    : null);
        }
    }

    /** A batch result row: severity icon · file name · error/warning count badge. */
    private static final class BatchResultCell extends ListCell<ValidationRunner.FileValidationResult> {
        private BatchResultCell() {
            // Follow the ListView width (ellipsize) instead of forcing a horizontal scrollbar.
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ValidationRunner.FileValidationResult item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("failed");
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            boolean failed = item.failed();
            boolean warningsOnly = !failed && item.warningCount() > 0;
            IconifyIcon icon = icon(failed ? "bi-x-circle"
                    : warningsOnly ? "bi-exclamation-triangle-fill" : "bi-check-circle-fill", 15);
            icon.getStyleClass().add(failed ? "sev-error" : warningsOnly ? "sev-warning" : "sev-ok");

            Label name = new Label(item.file().getName());
            name.getStyleClass().add(failed ? "fxt-vp-result-name-failed" : "fxt-vp-result-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(8, icon, name, spacer);
            long badgeCount = failed ? item.errorCount() : item.warningCount();
            if (badgeCount > 0) {
                Label badge = new Label(String.valueOf(badgeCount));
                badge.getStyleClass().add(failed ? "fxt-badge-danger" : "fxt-badge-warning");
                row.getChildren().add(badge);
            }
            row.setAlignment(Pos.CENTER_LEFT);
            if (failed) {
                getStyleClass().add("failed");
            }
            setText(null);
            setGraphic(row);
        }
    }
}
