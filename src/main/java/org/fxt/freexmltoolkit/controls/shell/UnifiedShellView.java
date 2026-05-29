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

    public UnifiedShellView() {
        getStyleClass().add("fxt-shell");

        setLeft(new ActivityBar(selectionModel));
        setCenter(buildWorkArea());
        setBottom(buildStatusBar());

        // React to activity changes: swap the side panel.
        showSidePanelFor(selectionModel.getActive());
        selectionModel.activeProperty().addListener((obs, oldV, newV) -> showSidePanelFor(newV));
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
        javafx.scene.control.Button save = toolButton("bi-save", "Save (Ctrl+S)", this::saveActive);
        javafx.scene.control.Button saveAs = toolButton("bi-save2", "Save As…", this::saveActiveAs);
        javafx.scene.control.Button setXsd = toolButton("bi-diagram-3", "Set XSD schema for IntelliSense", this::setSchema);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL);

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

        HBox bar = new HBox(6, save, saveAs, sep, setXsd, spacer, schemaStatus);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("fxt-editor-toolbar");
        return bar;
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
                statusLabel("Ln 1, Col 1"),
                statusLabel("UTF-8"),
                statusLabel("Spaces: 2"),
                spacer,
                statusLabel("No file open"));
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
