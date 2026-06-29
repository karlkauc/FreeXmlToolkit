package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.shell.schema.XsdNodeLabels;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNode;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeType;

/**
 * The Schema activity side panel, laid out after the Figma mockup
 * "Redesign · Unified — Schema (graphical)" (node 37:2): a filter field and the
 * active XSD's top-level declarations grouped into GLOBAL ELEMENTS, COMPLEX
 * TYPES, and SIMPLE TYPES. Selecting an entry reveals it in the Tree view;
 * double-clicking a type opens its dedicated editor tab; the context menu adds
 * Find Usage. The schema tools (generate/flatten/statistics/quality/sample/
 * documentation) live in the header's ⋮ overflow menu. Bound to the
 * {@link EditorHost}; refreshes on tab / view-mode changes.
 */
public class TypeLibraryPanel extends VBox {

    private final EditorHost editorHost;
    private final TextField filter = new TextField();
    private final ObservableList<XsdNode> elements = FXCollections.observableArrayList();
    private final ObservableList<XsdNode> complexTypes = FXCollections.observableArrayList();
    private final ObservableList<XsdNode> simpleTypes = FXCollections.observableArrayList();
    private final ListView<XsdNode> elementsList = new ListView<>(elements);
    private final ListView<XsdNode> complexList = new ListView<>(complexTypes);
    private final ListView<XsdNode> simpleList = new ListView<>(simpleTypes);
    private String selectedTypeName;
    private boolean syncingSelection;
    private boolean lastRefreshEmpty = true;
    private final javafx.animation.PauseTransition refreshDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));

    public TypeLibraryPanel(EditorHost editorHost) {
        this.editorHost = editorHost;
        getStyleClass().add("fxt-schema-panel");

        // --- header: SCHEMA -----------------------------------------------------
        Label title = new Label("SCHEMA");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(title, headerSpacer);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // --- schema tools (visible, directly above the filter) ----------------------
        javafx.scene.layout.FlowPane tools = new javafx.scene.layout.FlowPane(2, 2,
                toolButton("schema-tool-generate", "Generate XSD from XML", "bi-magic", this::generateXsdFromActive),
                toolButton("schema-tool-generate-batch", "Generate XSD (Batch)…", "bi-files", this::generateXsdBatch),
                toolButton("schema-tool-sample", "Generate Sample XML…", "bi-filetype-xml", this::generateSampleXmlForActive),
                toolButton("schema-tool-sample-advanced", "Generate Sample XML (Advanced)…", "bi-sliders", this::generateProfiledSampleForActive),
                toolButton("schema-tool-flatten", "Flatten Schema", "bi-layers", this::flattenActive),
                toolButton("schema-tool-statistics", "Statistics", "bi-bar-chart", this::statisticsActive),
                toolButton("schema-tool-quality", "Schema Quality", "bi-patch-check", this::qualityActive),
                toolButton("schema-tool-documentation", "Generate Documentation…", "bi-file-earmark-text", this::generateDocumentationForActive));
        tools.setId("schema-tools");
        tools.getStyleClass().add("fxt-schema-tools");

        // --- filter ----------------------------------------------------------------
        filter.setId("schema-filter");
        filter.setPromptText("Filter types…");
        filter.textProperty().addListener((obs, oldV, newV) -> refresh());
        VBox filterBox = new VBox(6, tools, filter);
        filterBox.getStyleClass().add("fxt-tp-section-body");

        // --- grouped declaration lists ----------------------------------------------
        configureList(elementsList, "schema-elements-list", elements, false);
        configureList(complexList, "schema-complex-list", complexTypes, true);
        configureList(simpleList, "schema-simple-list", simpleTypes, true);

        HBox elementsHeader = SidePanelLayout.sectionHeader(new Label("GLOBAL ELEMENTS"), elementsList);
        HBox complexHeader = SidePanelLayout.sectionHeader(new Label("COMPLEX TYPES"), complexList);
        HBox simpleHeader = SidePanelLayout.sectionHeader(new Label("SIMPLE TYPES"), simpleList);

        VBox content = new VBox(filterBox,
                elementsHeader, elementsList,
                complexHeader, complexList,
                simpleHeader, simpleList);
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);

        refresh();
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> refresh());
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> refresh());
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

    /** A flat icon-only schema-tool button (the full name is the tooltip). */
    private javafx.scene.control.Button toolButton(String id, String name, String iconLiteral, Runnable action) {
        javafx.scene.control.Button button = new javafx.scene.control.Button(null, icon(iconLiteral, 16));
        button.setId(id);
        button.getStyleClass().add("fxt-sp-action");
        button.setTooltip(new javafx.scene.control.Tooltip(name));
        button.setOnAction(e -> action.run());
        return button;
    }

    /** @return the tool buttons' tooltip texts in display order (for tests/observers). */
    public List<String> toolNames() {
        javafx.scene.Node tools = lookup("#schema-tools");
        if (!(tools instanceof javafx.scene.layout.FlowPane pane)) {
            return List.of();
        }
        return pane.getChildren().stream()
                .filter(n -> n instanceof javafx.scene.control.Button)
                .map(n -> ((javafx.scene.control.Button) n).getTooltip().getText())
                .toList();
    }

    /** @return the type context-menu item texts (for tests/observers). */
    List<String> typeContextMenuItemTexts() {
        return complexList.getContextMenu().getItems().stream()
                .map(MenuItem::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    /** Shared list styling + selection/double-click/context-menu wiring. */
    private void configureList(ListView<XsdNode> list, String id,
                               ObservableList<XsdNode> items, boolean isTypeList) {
        list.setId(id);
        list.getStyleClass().addAll("fxt-open-editors", "fxt-explorer-list");
        list.setCellFactory(lv -> new TypeCell());
        list.setFixedCellSize(26);
        list.setPlaceholder(new Label("None"));
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
                    menuItem("Find Usage", "bi-search", this::findUsageOfSelectedType));
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
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            java.util.List<String> usages = TypeUsageRunner.findUsages(xsd, typeName);
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
            String result = SchemaActionRunner.generateXsdFromMultiple(files);
            javafx.application.Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(javafx.scene.control.Alert.AlertType.ERROR, "Generate XSD (Batch)", result);
                } else {
                    editorHost.openGeneratedDocument(result, EditorFileType.XSD, "Generated.xsd");
                }
            });
        });
    }

    /** Infers an XSD from the active XML document and opens it as a new tab (async). */
    public void generateXsdFromActive() {
        runAsync(SchemaActionRunner::generateXsdFromXml, EditorFileType.XSD, "Generated.xsd");
    }

    /** Flattens the active XSD (resolves includes) and opens the result (async). */
    public void flattenActive() {
        var doc = editorHost.getActiveDocument();
        java.nio.file.Path baseDir = doc.map(OpenDocument::getPath)
                .map(java.nio.file.Path::getParent).orElse(null);
        runAsync(content -> SchemaActionRunner.flatten(content, baseDir),
                EditorFileType.XSD, "Flattened.xsd");
    }

    /** Collects statistics for the active XSD and opens a text report (async). */
    public void statisticsActive() {
        runAsync(SchemaActionRunner::statistics, EditorFileType.OTHER, "Statistics.txt");
    }

    /** Runs the schema quality checker and opens its report in a new tab. */
    public void qualityActive() {
        runAsync(SchemaActionRunner::qualityReport, EditorFileType.OTHER, "SchemaQuality.txt");
    }

    /**
     * Opens the documentation generator in the editor area ({@link DocumentationView}):
     * the full option set (format, HTML rendering options, languages, output) with a
     * live progress log — "big" editing happens in the main window.
     */
    public void generateDocumentationForActive() {
        editorHost.openOrFocusToolTab("Documentation", "bi-file-earmark-text",
                () -> new DocumentationView(editorHost));
    }

    /**
     * Generates a sample XML instance from the active schema (off the UI thread)
     * and opens it as a new editor tab.
     */
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
            String result = SampleXmlRunner.generate(xsd, mandatoryOnly, maxOccurrences, realistic);
            javafx.application.Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(javafx.scene.control.Alert.AlertType.ERROR, "Generate Sample XML", result);
                } else {
                    editorHost.openGeneratedDocument(result, EditorFileType.XML, "Sample.xml");
                }
            });
        });
    }

    /**
     * Advanced sample generation: extracts the schema's XPaths (off-thread), opens the
     * {@link ProfiledSampleDialog} for per-XPath rules + batch options, then generates a single
     * document (new tab) or a batch of files written to a chosen directory.
     */
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
                String result = ProfiledSampleRunner.generate(xsd, profile);
                javafx.application.Platform.runLater(() -> {
                    if (result.startsWith("ERROR")) {
                        alert(javafx.scene.control.Alert.AlertType.ERROR, title, result);
                    } else {
                        editorHost.openGeneratedDocument(result, EditorFileType.XML, "Sample.xml");
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
            var files = ProfiledSampleRunner.generateBatch(xsd, profile);
            var written = ProfiledSampleRunner.writeBatch(dir, files);
            javafx.application.Platform.runLater(() -> alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION, title,
                    "Wrote " + written.size() + " of " + profile.getBatchCount()
                            + " files to:\n" + dir.getAbsolutePath()));
        });
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
                return path.toFile();
            }
            File tmp = File.createTempFile("fxt-schema-", ".xsd");
            tmp.deleteOnExit();
            java.nio.file.Files.writeString(tmp.toPath(), editorHost.getActiveText().orElse(""));
            return tmp;
        } catch (Exception e) {
            alert(javafx.scene.control.Alert.AlertType.ERROR, title, e.getMessage());
            return null;
        }
    }

    private void alert(javafx.scene.control.Alert.AlertType type, String title, String message) {
        switch (type) {
            case ERROR -> org.fxt.freexmltoolkit.util.DialogHelper.showError(title, null, message);
            case WARNING -> org.fxt.freexmltoolkit.util.DialogHelper.showWarning(title, null, message);
            default -> org.fxt.freexmltoolkit.util.DialogHelper.showInformation(title, null, message);
        }
    }

    private void runAsync(java.util.function.Function<String, String> action,
                          EditorFileType outputType, String outputName) {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        String content = editorHost.getActiveText().orElse("");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            String result = action.apply(content);
            javafx.application.Platform.runLater(() -> {
                if (result.startsWith("ERROR:")) {
                    alert(javafx.scene.control.Alert.AlertType.ERROR, "Schema Tool", result);
                } else {
                    editorHost.openGeneratedDocument(result, outputType, outputName);
                }
            });
        });
    }

    /** Rebuilds the three group lists, honouring the filter text. */
    private void refresh() {
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
        MenuItem item = new MenuItem(text, icon(iconLiteral, 16));
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
