package org.fxt.freexmltoolkit.controls.shell;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
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
    private static final double INSPECTOR_WIDTH = 384;

    private final ActivitySelectionModel selectionModel = new ActivitySelectionModel();
    private final StackPane sidePanelHost = new StackPane();

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

        Region editorHost = buildEditorPlaceholder();
        HBox.setHgrow(editorHost, Priority.ALWAYS);

        Region inspector = buildInspector();

        HBox work = new HBox(sidePanelHost, editorHost, inspector);
        work.getStyleClass().add("fxt-work-area");
        return work;
    }

    private void showSidePanelFor(Activity activity) {
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

    private Region buildEditorPlaceholder() {
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

    private Region buildInspector() {
        VBox inspector = new VBox();
        inspector.getStyleClass().add("fxt-inspector");
        inspector.setPrefWidth(INSPECTOR_WIDTH);
        inspector.setMinWidth(INSPECTOR_WIDTH);

        Label header = new Label("PROPERTIES");
        header.getStyleClass().add("fxt-inspector-header");
        inspector.getChildren().add(header);

        // The four inspector sections required to be identical across all views.
        for (String section : new String[]{
                "Node & XPath", "Type & Facets", "Cardinality & Use", "Documentation & Refs"}) {
            Label body = new Label("—");
            body.getStyleClass().add("fxt-placeholder-text");
            TitledPane pane = new TitledPane(section, body);
            pane.setExpanded(true);
            pane.getStyleClass().add("fxt-inspector-section");
            inspector.getChildren().add(pane);
        }
        return inspector;
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
