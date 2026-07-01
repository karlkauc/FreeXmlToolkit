package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
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

/**
 * The Validation activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Validation" (node 40:48): a SOURCES section showing the
 * bound XSD/Schematron with "Change" links, a Single-file/Batch segmented
 * toggle, a primary "Run Validation" button, a RESULTS list with per-file
 * severity icons and problem-count badges (batch runs), and the PROBLEMS list.
 * Secondary features (Schematron tools, JSON Schema, FundsXML, the
 * validate-while-typing toggle, the batch text report) live in the header's
 * overflow (⋮) menu. Validation runs off the UI thread via
 * {@link ValidationRunner}; selecting a problem jumps to its line.
 */
public class ValidationPanel extends VBox {

    private final EditorHost editorHost;
    private final ObservableList<ValidationProblem> problems = FXCollections.observableArrayList();
    private final ListView<ValidationProblem> problemsList = new ListView<>(problems);
    private final ObservableList<ValidationRunner.FileValidationResult> batchResults =
            FXCollections.observableArrayList();
    private final ListView<ValidationRunner.FileValidationResult> batchList = new ListView<>(batchResults);
    private final Label status = new Label("Not validated");
    private final Label xsdName = new Label("none");
    private final Label schematronName = new Label("none");
    private final Label resultsHeaderLabel = new Label("RESULTS");
    private final MenuButton xsdFavoritesMenu = new MenuButton();
    private final MenuButton overflowMenu = new MenuButton();
    private final MenuItem openBatchReport = new MenuItem("Open last batch report");
    private final CheckMenuItem liveValidation = new CheckMenuItem("Validate while typing");
    private final ToggleButton singleMode = new ToggleButton("Single file");
    private final ToggleButton batchMode = new ToggleButton("Batch");
    private final Button exportProblems = new Button();
    private final ContextMenu batchSourceMenu = new ContextMenu();
    private final PauseTransition debounce = new PauseTransition(Duration.millis(600));
    private File jsonSchemaFile;
    private String lastBatchReport;

    public ValidationPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-validation-panel");

        // --- header: VALIDATION ........ ⋮ -------------------------------
        // Carries the shared side-panel-title class too: the shell convention (and
        // UnifiedShellViewTest) identify the active panel's title by it; the later
        // .fxt-vp-title rule wins the visual styling.
        Label title = new Label("VALIDATION");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        overflowMenu.setId("validation-overflow");
        overflowMenu.setGraphic(icon("bi-three-dots-vertical", 15));
        overflowMenu.getStyleClass().add("fxt-vp-overflow");
        buildOverflowMenu();
        HBox header = new HBox(title, headerSpacer, overflowMenu);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- SOURCES ------------------------------------------------------
        xsdName.getStyleClass().add("fxt-vp-source-name");
        schematronName.getStyleClass().add("fxt-vp-source-name");
        xsdFavoritesMenu.setGraphic(icon("bi-star", 13));
        xsdFavoritesMenu.getStyleClass().add("fxt-vp-source-fav");
        xsdFavoritesMenu.setOnShowing(e -> refreshXsdFavoritesMenu());
        HBox xsdRow = sourceRow("bi-diagram-3", xsdName, this::chooseXsd, xsdFavoritesMenu);
        HBox schematronRow = sourceRow("bi-ui-checks-grid", schematronName, this::chooseSchematron);
        // One click on a bound source opens it in the editor for direct editing.
        makeSourceNameOpenable(xsdName, () -> editorHost.activeSchemaProperty().get());
        makeSourceNameOpenable(schematronName, editorHost::getActiveSchematron);
        refreshSchematronStatus();
        refreshXsdStatus();
        // Opening the panel binds the schema referenced inside the XML (xsi:schemaLocation /
        // xsi:noNamespaceSchemaLocation) when none is bound yet; an explicit Change or
        // favorite choice still wins because a bound schema is never overridden.
        editorHost.redetectSchemaForActiveDocument();

        // --- mode toggle + Run Validation ---------------------------------
        ToggleGroup modeGroup = new ToggleGroup();
        singleMode.setToggleGroup(modeGroup);
        batchMode.setToggleGroup(modeGroup);
        singleMode.getStyleClass().add("fxt-seg");
        batchMode.getStyleClass().add("fxt-seg");
        singleMode.setMaxWidth(Double.MAX_VALUE);
        batchMode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(singleMode, Priority.ALWAYS);
        HBox.setHgrow(batchMode, Priority.ALWAYS);
        singleMode.setSelected(true);
        // A segmented control always has exactly one active segment.
        modeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) {
                modeGroup.selectToggle(oldV);
            }
        });
        HBox segGroup = new HBox(2, singleMode, batchMode);
        segGroup.getStyleClass().add("fxt-seg-group");

        Button run = new Button("Run Validation", icon("bi-play-fill", 14));
        run.setId("validation-run");
        run.getStyleClass().add("fxt-primary-button");
        run.setMaxWidth(Double.MAX_VALUE);
        MenuItem batchFilesItem = new MenuItem("Select XML files…", icon("bi-file-earmark-text", 16));
        batchFilesItem.setOnAction(e -> chooseBatch());
        MenuItem batchFolderItem = new MenuItem("Select folder…", icon("bi-folder2-open", 16));
        batchFolderItem.setOnAction(e -> chooseBatchFolder());
        batchSourceMenu.getItems().addAll(batchFilesItem, batchFolderItem);
        run.setOnAction(e -> {
            if (isBatchMode()) {
                batchSourceMenu.show(run, Side.BOTTOM, 0, 0);
            } else {
                revalidate();
            }
        });

        VBox runBox = new VBox(10, segGroup, run);
        runBox.getStyleClass().add("fxt-vp-run-box");

        status.getStyleClass().add("fxt-vp-status");

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
                editorHost.goToLine(newV.line());
            }
        });

        // Export the problems to an Excel workbook (enabled only when there are problems).
        exportProblems.setId("validation-export-problems");
        exportProblems.getStyleClass().add("fxt-sp-action");
        exportProblems.setGraphic(icon("bi-file-earmark-excel", 14));
        exportProblems.setTooltip(new javafx.scene.control.Tooltip("Export problems to Excel"));
        exportProblems.setOnAction(e -> exportProblemsToExcel());
        exportProblems.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(problems));
        Label problemsLabel = new Label("PROBLEMS");
        CollapsibleSection problemsSection = new CollapsibleSection(
                problemsLabel, problemsList, true, exportProblems);
        problemsSection.setId("validation-problems-section");

        // Continuous (debounced) validation: re-validate shortly after the active
        // document changes (typing / tab switch / schema binding), when enabled.
        liveValidation.setSelected(true);
        liveValidation.setOnAction(e -> scheduleRevalidation());
        debounce.setOnFinished(e -> {
            if (liveValidation.isSelected()) {
                revalidate();
            }
        });
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> scheduleRevalidation());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> {
            refreshXsdStatus();
            editorHost.redetectSchemaForActiveDocument();
            scheduleRevalidation();
        });
        editorHost.activeSchemaProperty().addListener((obs, oldV, newV) -> {
            refreshXsdStatus();
            scheduleRevalidation();
        });

        getChildren().addAll(header,
                sectionHeader(new Label("SOURCES")), xsdRow, schematronRow,
                runBox, status,
                resultsSection,
                problemsSection);
    }

    /** Builds the ⋮ overflow menu (tools that are not part of the mockup's main flow). */
    private void buildOverflowMenu() {
        Menu schematronTools = new Menu("Schematron Tools");
        schematronTools.getItems().addAll(
                menuItem("Rule Templates", this::openSchematronTemplates),
                menuItem("Tester", this::openSchematronTester),
                menuItem("Rule Builder", this::openSchematronBuilder),
                menuItem("Check Rules", this::openSchematronCheck),
                menuItem("Documentation", this::openSchematronDocumentation));
        overflowMenu.getItems().addAll(schematronTools,
                new SeparatorMenuItem(), menuItem("JSON Schema…", this::chooseJsonSchema));
        // FundsXML extension — only when enabled in the settings. The FundsXML activity
        // is its primary home; this link keeps it reachable from the validation context.
        if (FundsXmlRunner.isEnabled()) {
            overflowMenu.getItems().add(menuItem("Validate against FundsXML", this::validateFundsXml));
        }
        openBatchReport.setDisable(true);
        openBatchReport.setOnAction(e -> {
            if (lastBatchReport != null) {
                editorHost.openGeneratedDocument(lastBatchReport, EditorFileType.OTHER, "BatchReport.txt");
            }
        });
        overflowMenu.getItems().addAll(new SeparatorMenuItem(), liveValidation,
                new SeparatorMenuItem(), openBatchReport);
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(e -> action.run());
        return item;
    }

    /** A SOURCES/RESULTS/PROBLEMS section header: chevron + small bold label. */
    private static HBox sectionHeader(Label label) {
        IconifyIcon chevron = new IconifyIcon("bi-chevron-down");
        chevron.setIconSize(11);
        HBox header = new HBox(6, chevron, label);
        header.getStyleClass().add("fxt-vp-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
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

    private void validateFundsXml() {
        String xml = editorHost.getActiveText().orElse(null);
        PanelStatus.info(status, "Validating against FundsXML…");
        FxtGui.executorService.submit(() -> {
            String summary = FundsXmlRunner.validateSummary(xml);
            Platform.runLater(() -> PanelStatus.info(status, summary));
        });
    }

    /** Opens the Schematron rule-template library as a tool tab; inserts into the active editor. */
    public void openSchematronTemplates() {
        var library = new org.fxt.freexmltoolkit.controls.SchematronTemplateLibrary();
        library.setTemplateInsertCallback(editorHost::insertTextAtCaret);
        editorHost.openToolTab("Schematron Templates", "bi-collection", library);
    }

    /** Opens the Schematron tester as a tool tab, pre-loading the bound Schematron if any. */
    public void openSchematronTester() {
        var tester = new org.fxt.freexmltoolkit.controls.SchematronTester();
        File schematron = editorHost.getActiveSchematron();
        if (schematron != null) {
            tester.setSchematronFile(schematron);
        }
        editorHost.openToolTab("Schematron Tester", "bi-play-circle", tester);
    }

    /** Opens the visual Schematron rule builder as a tool tab. */
    public void openSchematronBuilder() {
        editorHost.openToolTab("Schematron Builder", "bi-tools",
                new org.fxt.freexmltoolkit.controls.SchematronVisualBuilder());
    }

    /**
     * Runs the Schematron error detector over the active document (off-thread) and shows the
     * categorised issues (XML-syntax / structural / XPath / semantic / best-practice) in a tool tab.
     */
    public void openSchematronCheck() {
        String text = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var issues = SchematronCheckRunner.check(text);
            javafx.application.Platform.runLater(() -> editorHost.openToolTab(
                    "Schematron Check", "bi-bug", new SchematronCheckResultView(issues)));
        });
    }

    /** Opens the Schematron documentation generator as a tool tab. */
    public void openSchematronDocumentation() {
        editorHost.openToolTab("Schematron Documentation", "bi-file-earmark-text",
                new org.fxt.freexmltoolkit.controls.SchematronDocumentationGenerator());
    }

    /** Schedules a debounced re-validation if live validation is on and the active doc is XML-family. */
    private void scheduleRevalidation() {
        if (liveValidation.isSelected() && isXmlFamilyActive()) {
            debounce.playFromStart();
        }
    }

    /** @return {@code true} if the active document is XML-family (not JSON / other). */
    private boolean isXmlFamilyActive() {
        return editorHost.getActiveDocument().map(d -> switch (d.getFileType()) {
            case XML, XSD, XSLT, SCHEMATRON -> true;
            default -> false;
        }).orElse(false);
    }

    /** Runs validation of the active document (XSD + optional Schematron), async. */
    public void revalidate() {
        if (editorHost.getActiveDocument().isEmpty()) {
            PanelStatus.precondition(status, "No document open");
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
        boolean json = editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.JSON).orElse(false);
        File xsd = editorHost.activeSchemaProperty().get();
        File schematron = editorHost.getActiveSchematron();
        File jsonSchema = this.jsonSchemaFile;
        PanelStatus.info(status, "Validating…");
        FxtGui.executorService.submit(() -> {
            List<ValidationProblem> result = json
                    ? ValidationRunner.validateJson(content, jsonSchema)
                    : ValidationRunner.run(content, xsd, schematron);
            Platform.runLater(() -> {
                setProblems(result);
                boolean hasSchema = json ? jsonSchema != null : (xsd != null || schematron != null);
                String summary = result.isEmpty()
                        ? (hasSchema ? "Valid" : "Well-formed")
                        : result.size() + " problem(s)";
                if (result.isEmpty()) {
                    PanelStatus.success(status, summary);
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

    /** @return the current validation status text (e.g. "Well-formed", "Valid", "N problem(s)"). */
    public String getStatusText() {
        return status.getText();
    }

    /** @return {@code true} while the "Validate while typing" toggle is on (for tests/observers). */
    public boolean isLiveValidationEnabled() {
        return liveValidation.isSelected();
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
        if (files == null || files.isEmpty()) {
            return;
        }
        File xsd = editorHost.activeSchemaProperty().get();
        File schematron = editorHost.getActiveSchematron();
        PanelStatus.info(status, "Validating " + files.size() + " file(s)…");
        FxtGui.executorService.submit(() -> {
            List<ValidationRunner.FileValidationResult> results = ValidationRunner.batch(files, xsd, schematron);
            String report = ValidationRunner.report(results, xsd, schematron);
            javafx.application.Platform.runLater(() -> showBatchResults(results, report));
        });
    }

    /** Publishes a finished batch run: RESULTS header + list and the ⋮ report entry. */
    void showBatchResults(List<ValidationRunner.FileValidationResult> results, String report) {
        batchList.getSelectionModel().clearSelection();
        batchResults.setAll(results);
        lastBatchReport = report;
        openBatchReport.setDisable(false);
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

    private void chooseBatch() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select XML files to validate");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XML", "*.xml"));
        java.util.List<File> files = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenMultipleDialog(chooser, 
                getScene() != null ? getScene().getWindow() : null);
        runBatch(files);
    }

    private void chooseBatchFolder() {
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
    private void chooseXsd() {
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
        if (editorHost.setSchemaForActiveDocument(xsd)) {
            refreshXsdStatus();
            revalidate();
        }
    }

    private void refreshXsdStatus() {
        File xsd = editorHost.activeSchemaProperty().get();
        setSourceName(xsdName, xsd != null ? xsd.getName() : null);
    }

    private void refreshSchematronStatus() {
        File schematron = editorHost.getActiveSchematron();
        setSourceName(schematronName, schematron != null ? schematron.getName() : null);
    }

    /**
     * Makes a source-row file name clickable: one click opens the currently bound
     * file in the editor for direct editing (no-op while the source is unset).
     */
    private void makeSourceNameOpenable(Label label, java.util.function.Supplier<File> file) {
        label.getStyleClass().add("fxt-vp-source-open");
        label.setTooltip(new javafx.scene.control.Tooltip("Open in editor"));
        label.setOnMouseClicked(e -> {
            File bound = file.get();
            if (bound != null && bound.exists()) {
                editorHost.openFile(bound.toPath());
            }
        });
    }

    /** Shows the file name, or a muted "none" while the source is unset. */
    private static void setSourceName(Label label, String name) {
        label.setText(name != null ? name : "none");
        if (name != null) {
            label.getStyleClass().remove("fxt-vp-source-none");
        } else if (!label.getStyleClass().contains("fxt-vp-source-none")) {
            label.getStyleClass().add("fxt-vp-source-none");
        }
    }

    private void chooseSchematron() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Schematron");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Schematron", "*.sch", "*.schematron"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.setActiveSchematron(file);
            refreshSchematronStatus();
        }
    }

    private void chooseJsonSchema() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select JSON Schema");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Schema", "*.json"));
        File file = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            jsonSchemaFile = file;
            PanelStatus.info(status, "JSON Schema: " + file.getName());
            revalidate();
        }
    }

    /** Sets the JSON Schema used when validating a JSON document (for tests/observers). */
    public void setJsonSchema(File schema) {
        this.jsonSchemaFile = schema;
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
        PanelStatus.info(status, "Exporting " + snapshot.size() + " problem(s)…");
        FxtGui.executorService.submit(() -> {
            try {
                ValidationExcelExporter.export(snapshot, sourceName, output.toPath());
                Platform.runLater(() -> {
                    PanelStatus.success(status, "Exported " + snapshot.size() + " problem(s)");
                    org.fxt.freexmltoolkit.util.DialogHelper.showInformation("Export problems",
                            null, "Wrote " + snapshot.size() + " problem(s) to:\n" + output.getAbsolutePath());
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

        CollapsibleSection(Label titleLabel, Region body, boolean growWhenExpanded,
                           javafx.scene.Node... trailing) {
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
            header.getChildren().addAll(trailing);

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
    private static final class ProblemCell extends ListCell<ValidationProblem> {
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
                return;
            }
            String line = item.line() > 0 ? "Ln " + item.line() + ": " : "";
            setText("[" + item.source() + "] " + line + item.message());
            boolean warning = "warning".equalsIgnoreCase(item.severity());
            IconifyIcon icon = icon(warning ? "bi-exclamation-triangle-fill" : "bi-x-circle", 13);
            icon.getStyleClass().add(warning ? "sev-warning" : "sev-error");
            setGraphic(icon);
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
