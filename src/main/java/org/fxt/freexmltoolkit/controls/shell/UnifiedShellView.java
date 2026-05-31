package org.fxt.freexmltoolkit.controls.shell;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;

/**
 * Skeleton of the new Unified shell (UI rebuild Phase 2): the persistent frame
 * that every activity will plug into.
 * <pre>
 *   ┌────┬──────────────┬───────────────────┬──────────────┐
 *   │ A  │  side panel  │   editor / main   │  inspector   │
 *   │ c  │  (per        │   (file-type      │  (Node&XPath,│
 *   │ t  │   activity)  │    aware host)    │   Type&Facets│
 *   │ .. │              │                   │   …)         │
 *   ├────┴──────────────┴───────────────────┴──────────────┤
 *   │  status bar                                           │
 *   └──────────────────────────────────────────────────────┘
 * </pre>
 * This phase wires the structure, the Activity Bar selection, and the
 * design-token styling. Side panels, the editor host and the inspector are
 * placeholders; later phases fill them with real, migrated functionality
 * (side-by-side with the existing tabs until each reaches parity — decision D3).
 */
public class UnifiedShellView extends BorderPane {

    private static final double SIDE_PANEL_WIDTH = 260;

    private final ActivitySelectionModel selectionModel = new ActivitySelectionModel();
    private final StackPane sidePanelHost = new StackPane();
    private final org.fxt.freexmltoolkit.controls.shell.editor.EditorHost editorHost =
            new org.fxt.freexmltoolkit.controls.shell.editor.EditorHost();

    private final Label statusPosition = statusLabel("Ln 1, Col 1");
    private final Label statusType = statusLabel("");
    private final Label statusFile = statusLabel("No file open");

    public UnifiedShellView() {
        getStyleClass().add("fxt-shell");

        setLeft(new ActivityBar(selectionModel));
        setCenter(buildWorkArea());
        setBottom(buildStatusBar());

        // React to activity changes: swap the side panel.
        showSidePanelFor(selectionModel.getActive());
        selectionModel.activeProperty().addListener((obs, oldV, newV) -> showSidePanelFor(newV));

        // Keep the status bar in sync with the active editor.
        editorHost.activeCaretProperty().addListener((obs, oldV, newV) -> updateStatusBar());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> updateStatusBar());
        updateStatusBar();

        // File-operation keyboard shortcuts (scoped to the shell).
        addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, this::handleShortcut);
    }

    private void handleShortcut(javafx.scene.input.KeyEvent event) {
        if (!event.isShortcutDown()) {
            return;
        }
        switch (event.getCode()) {
            case N -> {
                if (!event.isShiftDown()) {
                    newDocument();
                    event.consume();
                }
            }
            case O -> {
                openFile();
                event.consume();
            }
            case S -> {
                if (event.isShiftDown()) {
                    saveActiveAs();
                } else {
                    saveActive();
                }
                event.consume();
            }
            default -> { /* let other shortcuts (e.g. editor undo/redo/find) through */ }
        }
    }

    private void updateStatusBar() {
        var docOpt = editorHost.getActiveDocument();
        if (docOpt.isEmpty()) {
            statusPosition.setText("Ln 1, Col 1");
            statusType.setText("");
            statusFile.setText("No file open");
            return;
        }
        int[] pos = editorHost.getActiveCaretLineColumn();
        statusPosition.setText("Ln " + pos[0] + ", Col " + pos[1]);
        var doc = docOpt.get();
        statusType.setText(doc.getFileType().label());
        statusFile.setText(doc.getPath() != null ? doc.getPath().toString() : doc.getDisplayName());
    }

    private Region buildWorkArea() {
        sidePanelHost.getStyleClass().add("fxt-side-panel");
        sidePanelHost.setPrefWidth(SIDE_PANEL_WIDTH);
        sidePanelHost.setMinWidth(SIDE_PANEL_WIDTH);

        Region editorCenter = buildEditorCenter();
        HBox.setHgrow(editorCenter, Priority.ALWAYS);

        Region inspector = new org.fxt.freexmltoolkit.controls.shell.inspector.InspectorPanel(editorHost);

        HBox work = new HBox(sidePanelHost, editorCenter, inspector);
        work.getStyleClass().add("fxt-work-area");
        return work;
    }

    private void showSidePanelFor(Activity activity) {
        if (activity == Activity.EXPLORER) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.ExplorerPanel(editorHost));
            return;
        }
        if (activity == Activity.SCHEMA) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.TypeLibraryPanel(editorHost));
            return;
        }
        if (activity == Activity.VALIDATION) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.ValidationPanel(editorHost));
            return;
        }
        if (activity == Activity.TRANSFORM) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.TransformPanel(editorHost));
            return;
        }
        if (activity == Activity.FAVORITES) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.FavoritesActivityPanel(editorHost));
            return;
        }
        if (activity == Activity.PDF_FOP) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.FopPanel(editorHost));
            return;
        }
        if (activity == Activity.SIGNATURE) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.SignaturePanel(editorHost));
            return;
        }
        if (activity == Activity.HELP) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.HelpPanel());
            return;
        }
        if (activity == Activity.SETTINGS) {
            sidePanelHost.getChildren().setAll(
                    new org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanel());
            return;
        }
        VBox panel = new VBox();
        panel.getStyleClass().add("fxt-side-panel-content");

        Label title = new Label(activity.label().toUpperCase());
        title.getStyleClass().add("fxt-side-panel-title");

        Label hint = new Label("'" + activity.label() + "' panel — coming in a later phase.");
        hint.getStyleClass().add("fxt-placeholder-text");
        hint.setWrapText(true);

        panel.getChildren().addAll(title, hint);
        sidePanelHost.getChildren().setAll(panel);
    }

    /**
     * The editor center: a welcome placeholder shown while nothing is open,
     * swapped for the editor toolbar + {@link EditorHost} once a document opens.
     */
    private Region buildEditorCenter() {
        Region welcome = buildWelcome();

        Region toolbar = buildEditorToolbar();
        VBox editorArea = new VBox(toolbar, editorHost);
        VBox.setVgrow(editorHost, Priority.ALWAYS);
        editorArea.getStyleClass().add("fxt-editor-area");

        StackPane center = new StackPane(welcome, editorArea);
        center.getStyleClass().add("fxt-editor-center");

        // Show the editor only when at least one document is open.
        editorArea.visibleProperty().bind(
                javafx.beans.binding.Bindings.isNotEmpty(editorHost.getOpenDocuments()));
        editorArea.managedProperty().bind(editorArea.visibleProperty());
        welcome.visibleProperty().bind(editorArea.visibleProperty().not());
        welcome.managedProperty().bind(welcome.visibleProperty());
        return center;
    }

    private Region buildWelcome() {
        IconifyIcon logo = new IconifyIcon("bi-stack");
        logo.setIconSize(64);

        Label headline = new Label("Unified Editor");
        headline.getStyleClass().add("fxt-welcome-headline");

        Label subtitle = new Label("Open a file from the Explorer to start editing.");
        subtitle.getStyleClass().add("fxt-placeholder-text");

        VBox box = new VBox(logo, headline, subtitle);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("fxt-editor-host");
        return box;
    }

    private Region buildEditorToolbar() {
        Label badge = new Label();
        badge.getStyleClass().add("fxt-type-badge");
        badge.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> editorHost.getActiveDocument()
                        .map(d -> d.getFileType().label()).orElse("—"),
                editorHost.activeTabProperty()));

        Label schemaStatus = new Label();
        schemaStatus.getStyleClass().add("fxt-toolbar-status");
        schemaStatus.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(
                () -> {
                    java.io.File xsd = editorHost.activeSchemaProperty().get();
                    return xsd != null ? "XSD: " + xsd.getName() : "No XSD";
                },
                editorHost.activeSchemaProperty()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(4,
                badge, vsep(),
                toolButton("bi-file-earmark-plus", "New (Ctrl+N)", this::newDocument),
                toolButton("bi-folder2-open", "Open (Ctrl+O)", this::openFile),
                toolButton("bi-save", "Save (Ctrl+S)", this::saveActive),
                toolButton("bi-save2", "Save As (Ctrl+Shift+S)", this::saveActiveAs),
                toolButton("bi-files", "Save All", editorHost::saveAll),
                vsep(),
                toolButton("bi-arrow-counterclockwise", "Undo (Ctrl+Z)", editorHost::undoActive),
                toolButton("bi-arrow-clockwise", "Redo (Ctrl+Y)", editorHost::redoActive),
                vsep(),
                toolButton("bi-text-indent-left", "Format", editorHost::formatActive),
                toolButton("bi-layout-split", "Compare with File…", this::compareWithFile),
                toolButton("bi-table", "Spreadsheet Converter… (Excel / CSV ↔ XML)", this::convertSpreadsheet),
                vsep(),
                toolButton("bi-diagram-3", "Set XSD schema for IntelliSense", this::setSchema),
                spacer, buildViewSwitch(), vsep(), schemaStatus);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("fxt-editor-toolbar");
        return bar;
    }

    private Region buildViewSwitch() {
        var group = new javafx.scene.control.ToggleGroup();
        var buttons = new java.util.EnumMap<org.fxt.freexmltoolkit.controls.shell.editor.ViewMode,
                javafx.scene.control.ToggleButton>(org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.class);
        HBox box = new HBox();
        box.getStyleClass().add("fxt-view-switch");

        for (var mode : org.fxt.freexmltoolkit.controls.shell.editor.ViewMode.values()) {
            javafx.scene.control.ToggleButton button = new javafx.scene.control.ToggleButton(mode.label());
            button.setToggleGroup(group);
            button.getStyleClass().add("fxt-view-seg");
            button.setFocusTraversable(false);
            button.setOnAction(e -> editorHost.setActiveViewMode(mode));
            buttons.put(mode, button);
            box.getChildren().add(button);
        }

        Runnable sync = () -> {
            var active = editorHost.activeViewModeProperty().get();
            boolean hasDoc = editorHost.getActiveDocument().isPresent();
            buttons.forEach((mode, button) -> {
                button.setSelected(mode == active);
                button.setDisable(!hasDoc || !editorHost.activeSupportsView(mode));
            });
        };
        sync.run();
        editorHost.activeViewModeProperty().addListener((obs, oldV, newV) -> sync.run());
        editorHost.activeTabProperty().addListener((obs, oldV, newV) -> sync.run());
        return box;
    }

    private javafx.scene.control.Separator vsep() {
        return new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL);
    }

    private void newDocument() {
        editorHost.newDocument(org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML);
    }

    private void openFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Open File");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openFile(file.toPath());
        }
    }

    private void convertSpreadsheet() {
        var dialog = new org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog();
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.showAndWait().ifPresent(this::performConversion);
    }

    private void performConversion(
            org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog.Settings settings) {
        var window = getScene() != null ? getScene().getWindow() : null;
        boolean excel = settings.format()
                == org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog.Format.EXCEL;
        if (settings.direction()
                == org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetConverterDialog.Direction.XML_TO_SPREADSHEET) {
            if (editorHost.getActiveDocument().isEmpty()) {
                return;
            }
            String xml = editorHost.getActiveText().orElse("");
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Save spreadsheet");
            chooser.getExtensionFilters().add(excel
                    ? new javafx.stage.FileChooser.ExtensionFilter("Excel", "*.xlsx")
                    : new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
            java.io.File out = chooser.showSaveDialog(window);
            if (out == null) {
                return;
            }
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                String result = excel
                        ? org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .exportToExcel(xml, out, settings.config())
                        : org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .exportToCsv(xml, out, settings.delimiter().config(), settings.config());
                javafx.application.Platform.runLater(() -> reportConversion(result, out, false));
            });
        } else {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Import spreadsheet");
            chooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Excel / CSV", "*.xlsx", "*.csv"));
            java.io.File in = chooser.showOpenDialog(window);
            if (in == null) {
                return;
            }
            // Infer the actual format from the chosen file's extension.
            boolean isExcel = in.getName().toLowerCase().endsWith(".xlsx");
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                String xml = isExcel
                        ? org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .excelToXml(in, settings.config())
                        : org.fxt.freexmltoolkit.controls.shell.editor.SpreadsheetActionRunner
                                .csvToXml(in, settings.delimiter().config(), settings.config());
                javafx.application.Platform.runLater(() -> {
                    if (xml.startsWith("ERROR:")) {
                        reportConversion(xml, in, true);
                    } else {
                        editorHost.openGeneratedDocument(xml,
                                org.fxt.freexmltoolkit.controls.shell.editor.EditorFileType.XML, "Imported.xml");
                    }
                });
            });
        }
    }

    private void reportConversion(String result, java.io.File file, boolean imported) {
        boolean ok = result.startsWith("OK:") || (imported && !result.startsWith("ERROR:"));
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(ok
                ? javafx.scene.control.Alert.AlertType.INFORMATION
                : javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle("Spreadsheet Converter");
        alert.setHeaderText(null);
        alert.setContentText(ok ? "Done: " + file.getAbsolutePath() : result);
        alert.showAndWait();
    }

    private void compareWithFile() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Compare with File…");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("XML / XSD / XSLT / Schematron / JSON",
                        "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.openDiffWithFile(file);
        }
    }

    private void setSchema() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select XSD Schema");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("XSD Schema", "*.xsd"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.setSchemaForActiveDocument(file);
        }
    }

    private javafx.scene.control.Button toolButton(String icon, String tooltip, Runnable action) {
        javafx.scene.control.Button button = new javafx.scene.control.Button();
        button.getStyleClass().add("fxt-tool-button");
        IconifyIcon graphic = new IconifyIcon(icon);
        graphic.setIconSize(16);
        button.setGraphic(graphic);
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        button.setOnAction(e -> action.run());
        return button;
    }

    private void saveActive() {
        editorHost.getActiveDocument().ifPresent(doc -> {
            if (doc.isUntitled()) {
                saveActiveAs();
            } else {
                editorHost.saveActive();
            }
        });
    }

    private void saveActiveAs() {
        if (editorHost.getActiveDocument().isEmpty()) {
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save As");
        java.io.File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            editorHost.saveActiveAs(file.toPath());
        }
    }

    /** @return the editor host (for future toolbar / inspector wiring). */
    public org.fxt.freexmltoolkit.controls.shell.editor.EditorHost getEditorHost() {
        return editorHost;
    }

    private Region buildStatusBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("fxt-status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
                statusPosition,
                statusType,
                statusLabel("UTF-8"),
                spacer,
                statusFile);
        return bar;
    }

    private Label statusLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("fxt-status-item");
        return label;
    }

    /** @return the shell's activity selection model (for future host wiring). */
    public ActivitySelectionModel getSelectionModel() {
        return selectionModel;
    }
}
