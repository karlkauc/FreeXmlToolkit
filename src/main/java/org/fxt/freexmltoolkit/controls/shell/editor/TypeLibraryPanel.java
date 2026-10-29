package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.FxmlViews;
import org.fxt.freexmltoolkit.controls.shell.editor.analysis.SchemaAnalysisView;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels;
import org.fxt.freexmltoolkit.controls.theme.ActionColor;
import org.fxt.freexmltoolkit.controls.theme.SemanticIcon;
import org.fxt.freexmltoolkit.controls.v2.editor.commands.AddGlobalTypeCommand;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;
import org.fxt.freexmltoolkit.service.telemetry.UsageEvents;

/**
 * The Schema activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Schema (graphical)" (node 37:2): a filter field and the
 * active XSD's top-level declarations grouped into GLOBAL ELEMENTS, COMPLEX
 * TYPES, and SIMPLE TYPES. Selecting an entry reveals it in the Tree view;
 * double-clicking a type opens its dedicated editor tab; the context menu adds
 * Find Usage. The schema tools (generate/flatten/analysis/sample/documentation) are
 * labelled {@link PanelActionList} rows in a TOOLS section above the filter. Bound to the
 * {@link EditorHost}; refreshes on tab / view-mode changes.
 */
public class TypeLibraryPanel extends VBox {

    private final EditorHost editorHost;
    @FXML private PanelActionList tools;
    @FXML private TextField filter;
    private final ObservableList<XsdNode> elements = FXCollections.observableArrayList();
    private final ObservableList<XsdNode> complexTypes = FXCollections.observableArrayList();
    private final ObservableList<XsdNode> simpleTypes = FXCollections.observableArrayList();
    @FXML private ListView<XsdNode> elementsList;
    @FXML private ListView<XsdNode> complexList;
    @FXML private ListView<XsdNode> simpleList;
    @FXML private SidePanelSection complexSection;
    @FXML private SidePanelSection simpleSection;
    private javafx.scene.control.Button newComplexButton;
    private javafx.scene.control.Button newSimpleButton;
    private String selectedTypeName;
    private boolean syncingSelection;
    private boolean lastRefreshEmpty = true;
    private final javafx.animation.PauseTransition refreshDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
    private final javafx.animation.PauseTransition filterDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(200));

    /** Loads the layout from {@code /pages/panels/TypeLibraryPanel.fxml} and wires the lists. */
    public TypeLibraryPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        FxmlViews.load(this, "/pages/panels/TypeLibraryPanel.fxml");

        // Debounced: each refresh walks the schema's declarations; typing must stay instant.
        filterDebounce.setOnFinished(e -> refresh());
        filter.textProperty().addListener((obs, oldV, newV) -> filterDebounce.playFromStart());

        // --- grouped declaration lists ----------------------------------------------
        configureList(elementsList, elements, false);
        configureList(complexList, complexTypes, true);
        configureList(simpleList, simpleTypes, true);
        newComplexButton = complexSection.addHeaderAction("bi-plus-lg", "New complex type…",
                () -> newType(AddGlobalTypeCommand.Kind.COMPLEX));
        newComplexButton.setId("schema-new-complex-type");
        newSimpleButton = simpleSection.addHeaderAction("bi-plus-lg", "New simple type…",
                () -> newType(AddGlobalTypeCommand.Kind.SIMPLE));
        newSimpleButton.setId("schema-new-simple-type");

        // Dropping an XSD anywhere on the panel opens it as a document; the library then
        // follows the newly active tab. Other extensions are rejected (red feedback).
        org.fxt.freexmltoolkit.controls.shell.FileDropSupport.install(this,
                org.fxt.freexmltoolkit.service.DragDropService.XSD_EXTENSIONS, editorHost::openFile);

        refresh();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refresh());
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> refresh());
        // Imported types arrive late when a remote import had to be downloaded first.
        editorHost.schemaImportsRevisionProperty().addListener((obs, oldV, newV) -> refresh());
        // The Text view's model is parsed in the background; list its declarations once it lands.
        editorHost.activeModelRevisionProperty().addListener((obs, oldV, newV) -> refresh());
        // The document text loads asynchronously AFTER the tab change. Refresh again
        // once content lands - but ONLY while the library is still unpopulated:
        // refresh parses the XSD text, so it must not run per keystroke.
        refreshDebounce.setOnFinished(e -> refresh());
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> {
            if (lastRefreshEmpty) {
                refreshDebounce.playFromStart();
            }
        });
    }

    /** @return the tool rows' labels in display order (for tests/observers). */
    public List<String> toolNames() {
        return tools.labels();
    }

    /** @return the type context-menu item texts (for tests/observers). */
    List<String> typeContextMenuItemTexts() {
        return complexList.getContextMenu().getItems().stream()
                .map(MenuItem::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    /** Shared list contents + selection/double-click/context-menu wiring. */
    private void configureList(ListView<XsdNode> list, ObservableList<XsdNode> items, boolean isTypeList) {
        list.setItems(items);
        list.setCellFactory(lv -> new TypeCell());
        list.prefHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.min(234.0, Math.max(1, items.size()) * 26.0 + 2), items));
        list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || syncingSelection) {
                return;
            }
            // One selection across the three group lists.
            syncingSelection = true;
            try {
                for (ListView<XsdNode> other : List.of(elementsList, complexList, simpleList)) {
                    if (other != list) {
                        other.getSelectionModel().clearSelection();
                    }
                }
            } finally {
                syncingSelection = false;
            }
            selectedTypeName = newV.getName();
            String typeName = selectedTypeName;
            // Defer out of the selection-change processing (avoids re-entering
            // the ListViewBehavior listener: IndexOutOfBoundsException).
            javafx.application.Platform.runLater(() -> editorHost.revealTypeByName(typeName));
        });
        if (isTypeList) {
            // Double-click opens the type in its own focused editor tab.
            list.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && selectedTypeName != null) {
                    String typeName = selectedTypeName;
                    javafx.application.Platform.runLater(() -> editorHost.openTypeEditorTab(typeName));
                }
            });
        }
        ContextMenu menu = new ContextMenu();
        MenuItem reveal = menuItem("Reveal in Tree", "bi-list-nested", () -> {
            if (selectedTypeName != null) {
                editorHost.revealTypeByName(selectedTypeName);
            }
        });
        menu.getItems().add(reveal);
        if (isTypeList) {
            menu.getItems().addAll(
                    menuItem("Open Type Editor", "bi-pencil-square", () -> {
                        if (selectedTypeName != null) {
                            editorHost.openTypeEditorTab(selectedTypeName);
                        }
                    }),
                    menuItem("Find Usage", "bi-search", this::findUsageOfSelectedType),
                    new javafx.scene.control.SeparatorMenuItem(),
                    list == simpleList
                            ? createItem("New Simple Type…", "bi-type", () -> newType(AddGlobalTypeCommand.Kind.SIMPLE))
                            : createItem("New Complex Type…", "bi-diagram-3",
                                    () -> newType(AddGlobalTypeCommand.Kind.COMPLEX)));
        }
        list.setContextMenu(menu);
    }

    /**
     * Finds where the selected named type is used and opens a report (async).
     * Reuses {@link TypeUsageRunner} (which reuses {@code TypeUsageFinder}).
     */
    public void findUsageOfSelectedType() {
        String typeName = selectedTypeName;
        if (typeName == null || typeName.isBlank() || editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String xsd = editorHost.getActiveText().orElse("");
        java.nio.file.Path baseDir = editorHost.getActiveDocument().map(OpenDocument::getPath)
                .map(java.nio.file.Path::getParent).orElse(null);
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            TypeUsageRunner.Usages search = TypeUsageRunner.findUsagesResult(xsd, baseDir, typeName);
            java.util.List<String> usages = search.usages();
            UsageEvents.typeUsagesSearched(usages.size(), t0, search.failure());
            if (search.failed()) {
                // type_usages carries the failure; the dialog explains it instead of "No usages found."
                javafx.application.Platform.runLater(() -> org.fxt.freexmltoolkit.util.DialogHelper.showActionError("Find Usages",
                        "The schema could not be analyzed.", "Fix the schema's errors, then search again.",
                        search.error(), false));
                return;
            }
            StringBuilder report = new StringBuilder("Usages of type '").append(typeName).append("'\n");
            report.append("=".repeat(report.length() - 1)).append('\n');
            if (usages.isEmpty()) {
                report.append("\nNo usages found.\n");
            } else {
                report.append('\n').append(usages.size()).append(" usage(s):\n\n");
                usages.forEach(u -> report.append("  • ").append(u).append('\n'));
            }
            javafx.application.Platform.runLater(() ->
                    editorHost.openGeneratedDocument(report.toString(), EditorFileType.OTHER,
                            "Usages-" + typeName + ".txt"));
        });
    }

    /**
     * Infers a single XSD from several user-selected XML files (batch) and opens
     * the result as a new tab (async). Reuses {@link SchemaActionRunner#generateXsdFromMultiple}.
     */
    @FXML
    public void generateXsdBatch() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select XML files to infer a schema from");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XML", "*.xml"));
        java.util.List<File> files = org.fxt.freexmltoolkit.util.FileChooserHelper.showOpenMultipleDialog(chooser, 
                getScene() != null ? getScene().getWindow() : null);
        if (files == null || files.isEmpty()) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            SchemaActionRunner.XsdGeneration generation = SchemaActionRunner.generateXsdBatch(files);
            UsageEvents.xsdGenerated(files.size(), t0, generation.failure());
            String result = generation.text();
            javafx.application.Platform.runLater(() -> showResult(result, "Generate XSD (Batch)",
                    EditorFileType.XSD, "Generated.xsd"));
        });
    }

    /** Infers an XSD from the active XML document and opens it as a new tab (async). */
    @FXML
    public void generateXsdFromActive() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            SchemaActionRunner.XsdGeneration generation = SchemaActionRunner.generateXsd(content);
            UsageEvents.xsdGenerated(1, t0, generation.failure());
            javafx.application.Platform.runLater(() -> showResult(generation.text(), "Schema Tool",
                    EditorFileType.XSD, "Generated.xsd"));
        });
    }

    @FXML
    public void onFlatten() {
        flattenActive();
    }

    /**
     * Flattens the active XSD (resolves includes) and opens the result (async).
     * Asks for reduction options first (strip annotations/comments, remove unused
     * types, minify) so the result can serve as a lean server-side validation schema.
     */
    public void flattenActive() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        var options = new FlattenOptionsDialog().showAndWait();
        if (options.isEmpty()) {
            return;
        }
        flattenActive(options.get());
    }

    /** Flattens the active XSD with the given options, without asking (async). */
    public void flattenActive(org.fxt.freexmltoolkit.controls.v2.editor.flatten.FlattenOptions options) {
        java.nio.file.Path schemaFile = editorHost.getActiveDocument().map(OpenDocument::getPath).orElse(null);
        java.nio.file.Path baseDir = schemaFile != null ? schemaFile.getParent() : null;
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            ActionRun run = SchemaActionRunner.flattenResult(content, baseDir, schemaFile, options);
            UsageEvents.schemaFlattened(t0, run.failure());
            javafx.application.Platform.runLater(() -> showResult(run.text(), "Schema Tool",
                    EditorFileType.XSD, "Flattened.xsd"));
        });
    }

    /**
     * Opens (or focuses) the singleton "Schema Analysis" tool tab — statistics incl. unused
     * types, quality checks, identity constraints and XPath validation for the active XSD.
     * Re-focusing an existing tab re-runs the analysis so the report follows the active document.
     */
    @FXML
    public void analyzeActive() {
        boolean existed = editorHost.hasToolTab(SchemaAnalysisView.TITLE);
        // Capture the document before the tool tab takes over as the active tab.
        var active = editorHost.getActiveDocument().orElse(null);
        javafx.scene.control.Tab tab = editorHost.openOrFocusToolTab(SchemaAnalysisView.TITLE,
                SchemaAnalysisView.ICON, () -> new SchemaAnalysisView(editorHost));
        if (tab.getContent() instanceof SchemaAnalysisView view) {
            tab.setOnClosed(e -> view.dispose());
            if (existed) {
                if (active != null && active.getFileType() == org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XSD) {
                    view.refresh(active);
                } else {
                    view.refresh();
                }
            }
        }
    }

    /**
     * Opens the documentation generator in the editor area ({@link DocumentationView}):
     * the full option set (format, HTML rendering options, languages, output) with a
     * live progress log — "big" editing happens in the main window.
     */
    @FXML
    public void generateDocumentationForActive() {
        editorHost.openOrFocusToolTab("Documentation", "bi-file-earmark-text",
                () -> new DocumentationView(editorHost));
    }

    /**
     * Generates a sample XML instance from the active schema (off the UI thread)
     * and opens it as a new editor tab.
     */
    @FXML
    public void generateSampleXmlForActive() {
        File xsd = activeXsdFileOrAlert("Generate Sample XML");
        if (xsd == null) {
            return;
        }
        var options = new SampleXmlOptionsDialog().showAndWait();
        if (options.isEmpty()) {
            return;
        }
        boolean mandatoryOnly = options.get().mandatoryOnly();
        int maxOccurrences = options.get().maxOccurrences();
        boolean realistic = options.get().realistic();
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            SampleXmlRunner.SampleRun run = SampleXmlRunner.generateResult(xsd, mandatoryOnly, maxOccurrences,
                    realistic, null);
            String result = SampleXmlRunner.format(run.text());
            UsageEvents.sampleGenerated("basic", 1, t0, run.failure());
            javafx.application.Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(javafx.scene.control.Alert.AlertType.ERROR, "Generate Sample XML", result);
                } else {
                    openSample(result, xsd);
                }
            });
        });
    }

    /**
     * Advanced sample generation: extracts the schema's XPaths (off-thread), opens the
     * {@link ProfiledSampleDialog} for per-XPath rules + batch options, then generates a single
     * document (new tab) or a batch of files written to a chosen directory.
     */
    @FXML
    public void generateProfiledSampleForActive() {
        String title = "Generate Sample XML (Advanced)";
        File xsd = activeXsdFileOrAlert(title);
        if (xsd == null) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var xpaths = ProfiledSampleRunner.extractXPaths(xsd);
            javafx.application.Platform.runLater(() -> {
                if (xpaths.isEmpty()) {
                    alert(javafx.scene.control.Alert.AlertType.WARNING, title,
                            "Could not extract any XPaths from the schema.");
                    return;
                }
                var profileOpt = new ProfiledSampleDialog(xpaths).showAndWait();
                profileOpt.ifPresent(profile -> runProfiledGeneration(xsd, profile, title));
            });
        });
    }

    private void runProfiledGeneration(File xsd,
            org.fxt.freexmltoolkit.domain.GenerationProfile profile, String title) {
        if (profile.getBatchCount() <= 1) {
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                long t0 = System.nanoTime();
                SampleXmlRunner.SampleRun run = ProfiledSampleRunner.generateResult(xsd, profile);
                String result = SampleXmlRunner.format(run.text());
                UsageEvents.sampleGenerated("profiled", 1, t0, run.failure());
                javafx.application.Platform.runLater(() -> {
                    if (result.startsWith("ERROR")) {
                        alert(javafx.scene.control.Alert.AlertType.ERROR, title, result);
                    } else {
                        openSample(result, xsd);
                    }
                });
            });
            return;
        }
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Choose output folder for " + profile.getBatchCount() + " files");
        File dir = org.fxt.freexmltoolkit.util.FileChooserHelper.showDialog(chooser, getScene() != null ? getScene().getWindow() : null);
        if (dir == null) {
            return;
        }
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            long t0 = System.nanoTime();
            var files = ProfiledSampleRunner.generateBatch(xsd, profile, dir).stream()
                    .map(f -> new org.fxt.freexmltoolkit.domain.GeneratedFile(f.fileName(),
                            SampleXmlRunner.format(f.content())))
                    .toList();
            var written = ProfiledSampleRunner.writeBatch(dir, files);
            UsageEvents.sampleGenerated("profiled", written.size(), t0, written.isEmpty()
                    ? org.fxt.freexmltoolkit.service.telemetry.QueryFailure.UNKNOWN
                    : written.size() < files.size()
                            ? new org.fxt.freexmltoolkit.service.telemetry.QueryFailure("partial_write",
                                    org.fxt.freexmltoolkit.service.telemetry.TelemetryEvent.Status.ERROR, null)
                            : null);
            javafx.application.Platform.runLater(() -> alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION, title,
                    "Wrote " + written.size() + " of " + profile.getBatchCount()
                            + " files to:\n" + dir.getAbsolutePath()));
        });
    }

    /**
     * Opens a generated sample as a new tab and binds the schema it was generated from:
     * the sample references the XSD only by file name (portable, resolvable once saved next
     * to it), so without the direct binding the unsaved tab would only be well-formedness
     * checked. FX thread.
     */
    private void openSample(String xml, File xsd) {
        editorHost.openGeneratedDocument(xml, EditorFileType.XML, "Sample.xml");
        editorHost.bindGeneratedSchemaToActiveDocument(xsd);
    }

    /**
     * Resolves the active document to an XSD file on disk (its saved path, or a
     * temp copy of the current text for unsaved schemas). Shows an alert and
     * returns {@code null} when the active document is not an XSD.
     */
    private File activeXsdFileOrAlert(String title) {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty() || docOpt.get().getFileType() != EditorFileType.XSD) {
            alert(javafx.scene.control.Alert.AlertType.WARNING, title, "Open an XSD schema first.");
            return null;
        }
        try {
            java.nio.file.Path path = docOpt.get().getPath();
            if (path != null) {
                // The tool reads the saved file: offer to save editor changes first.
                return editorHost.ensureSavedBeforeTool(path, title) ? path.toFile() : null;
            }
            File tmp = File.createTempFile("fxt-schema-", ".xsd");
            tmp.deleteOnExit();
            java.nio.file.Files.writeString(tmp.toPath(), editorHost.getActiveText().orElse(""));
            return tmp;
        } catch (Exception e) {
            org.fxt.freexmltoolkit.util.DialogHelper.showError(title, null, e.getMessage(), e);
            return null;
        }
    }

    private void alert(javafx.scene.control.Alert.AlertType type, String title, String message) {
        switch (type) {
            // Every error shown here belongs to an action that sends its own usage event with the
            // failure's classification, so the dialog must not count it a second time.
            case ERROR -> org.fxt.freexmltoolkit.util.DialogHelper.showActionError(title, message,
                    "Fix the input named in the message and run the action again.", (String) null, false);
            case WARNING -> org.fxt.freexmltoolkit.util.DialogHelper.showWarning(title, null, message);
            default -> org.fxt.freexmltoolkit.util.DialogHelper.showInformation(title, null, message);
        }
    }


    /** Opens a generated document as a new tab, or shows an {@code "ERROR: …"} result. */
    private void showResult(String result, String title, EditorFileType outputType, String outputName) {
        if (result.startsWith("ERROR:")) {
            alert(javafx.scene.control.Alert.AlertType.ERROR, title, result);
        } else {
            editorHost.openGeneratedDocument(result, outputType, outputName);
        }
    }

    /** Rebuilds the three group lists, honouring the filter text. */
    /** Asks for a new top-level type of the active XSD and opens it in its type editor tab. */
    public void newType(AddGlobalTypeCommand.Kind kind) {
        if (editorHost.newTypeInActive(kind)) {
            refresh();
        }
    }

    private MenuItem createItem(String text, String iconLiteral, Runnable action) {
        MenuItem item = new MenuItem(text, SemanticIcon.paint(icon(iconLiteral, 16), ActionColor.CREATE));
        item.setOnAction(e -> action.run());
        return item;
    }

    private void refresh() {
        boolean xsd = editorHost.getActiveDocument()
                .map(d -> d.getFileType() == EditorFileType.XSD).orElse(false);
        newComplexButton.setDisable(!xsd);
        newSimpleButton.setDisable(!xsd);
        String query = filter.getText() != null ? filter.getText().strip().toLowerCase(Locale.ROOT) : "";
        syncingSelection = true;
        try {
            elementsList.getSelectionModel().clearSelection();
            complexList.getSelectionModel().clearSelection();
            simpleList.getSelectionModel().clearSelection();
        } finally {
            syncingSelection = false;
        }
        List<XsdNode> globalElements = editorHost.getActiveGlobalElements();
        List<XsdNode> types = editorHost.getActiveNamedTypes();
        lastRefreshEmpty = globalElements.isEmpty() && types.isEmpty();
        elements.setAll(filtered(globalElements, query, null));
        complexTypes.setAll(filtered(types, query, XsdNodeType.COMPLEX_TYPE));
        simpleTypes.setAll(filtered(types, query, XsdNodeType.SIMPLE_TYPE));
    }

    private static List<XsdNode> filtered(List<XsdNode> nodes, String query, XsdNodeType type) {
        return nodes.stream()
                .filter(n -> type == null || n.getNodeType() == type)
                .filter(n -> query.isEmpty()
                        || (n.getName() != null && n.getName().toLowerCase(Locale.ROOT).contains(query)))
                .toList();
    }

    private MenuItem menuItem(String text, String iconLiteral, Runnable action) {
        // Reveal / Open Type Editor / Find Usage all navigate (spec 2026-09-26 §3).
        MenuItem item = new MenuItem(text, SemanticIcon.paint(icon(iconLiteral, 16), ActionColor.NAVIGATE));
        item.setOnAction(e -> action.run());
        return item;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    /** Renders a declaration with its node-type icon. */
    private static final class TypeCell extends ListCell<XsdNode> {
        private TypeCell() {
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(XsdNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item.getName());
            IconifyIcon icon = new IconifyIcon(XsdNodeLabels.icon(item.getNodeType()));
            icon.setIconSize(14);
            setGraphic(icon);
        }
    }
}
