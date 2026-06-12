package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;

/**
 * The XML instance Grid view for the unified shell: an XMLSpy-style editable grid
 * over an XML document, backed by the existing Canvas-based {@link XmlCanvasView}.
 *
 * <p>Acts as the toast container for the canvas and round-trips edits back to the
 * owning editor via {@link #setOnModified(Consumer)} (the same mechanism the
 * legacy {@code XmlEditor} uses for its graphic view).</p>
 */
public class XmlGridView extends StackPane {

    private Consumer<String> onModified;
    private Consumer<XmlNode> onSelectionChanged;
    private XmlEditorContext context;

    public XmlGridView() {
        getStyleClass().add("fxt-xml-grid");
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    /** @return the current grid's editor context (model + command stack), or {@code null}. */
    public XmlEditorContext getContext() {
        return context;
    }

    /** Sets the callback invoked when the grid's selected node changes (for the inspector). */
    public void setOnSelectionChanged(Consumer<XmlNode> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    /**
     * Sets the callback invoked with the serialized XML whenever the grid edits
     * the document, so the editor text can be kept in sync.
     *
     * @param onModified receives the modified XML (may be {@code null} to clear)
     */
    public void setOnModified(Consumer<String> onModified) {
        this.onModified = onModified;
    }

    /**
     * (Re)builds the grid from the given XML by parsing it into a fresh context. A blank document
     * or a parse error shows a placeholder instead of the grid.
     *
     * @param xml the XML to display (may be {@code null})
     */
    public void setXml(String xml) {
        if (xml == null || xml.isBlank()) {
            getChildren().clear();
            context = null;
            getChildren().add(placeholder("No XML content to display."));
            return;
        }
        XmlEditorContext ctx = new XmlEditorContext();
        try {
            ctx.loadDocumentFromString(xml);
        } catch (Exception e) {
            getChildren().clear();
            context = null;
            getChildren().add(placeholder("Cannot display grid:\n\n"
                    + e.getMessage() + "\n\nFix the XML errors first."));
            return;
        }
        setContext(ctx);
    }

    /**
     * Renders the given (already parsed) editor context — the shell shares ONE
     * {@link XmlEditorContext} across its Text/Tree/Grid views, so edits and undo history are
     * preserved when switching modes. A {@code null}/empty context shows a placeholder. Rendering
     * the context already shown is a no-op (avoids rebinding the selection listener).
     *
     * @param ctx the shared context to render (may be {@code null})
     */
    public void setContext(XmlEditorContext ctx) {
        if (ctx == this.context && !getChildren().isEmpty()
                && getChildren().get(0) instanceof VBox box
                && box.getChildren().stream().anyMatch(n -> n instanceof XmlCanvasView)) {
            return; // already showing this context
        }
        getChildren().clear();
        this.context = ctx;
        if (ctx == null || ctx.getDocument() == null) {
            getChildren().add(placeholder("No XML content to display."));
            return;
        }
        ctx.getSelectionModel().addPropertyChangeListener("selectedNode", evt -> {
            if (onSelectionChanged != null) {
                onSelectionChanged.accept((XmlNode) evt.getNewValue());
            }
        });
        XmlCanvasView view = new XmlCanvasView(ctx);
        view.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        view.setToastContainer(this);
        view.setOnDocumentModified(modified -> {
            if (modified != null && onModified != null) {
                onModified.accept(modified);
            }
        });
        VBox.setVgrow(view, Priority.ALWAYS);
        getChildren().add(new VBox(buildHeader(view), view));
        // Arrow-key navigation should work right away, without a mouse click first.
        javafx.application.Platform.runLater(view::focusCanvas);
    }

    /** The mockup's grid header: table icon · "Grid view" · subtitle ·…· Collapse all. */
    private HBox buildHeader(XmlCanvasView view) {
        Label title = new Label("Grid view", icon("bi-table", 15));
        title.getStyleClass().add("fxt-grid-title");
        Label subtitle = new Label("· nested · repeating elements as embedded grids");
        subtitle.getStyleClass().add("fxt-grid-subtitle");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button collapseAll = new Button("Collapse all", icon("bi-arrows-collapse", 13));
        collapseAll.setId("grid-collapse-all");
        collapseAll.getStyleClass().add("fxt-tool-button");
        collapseAll.setOnAction(e -> view.collapseAll());
        HBox header = new HBox(8, title, subtitle, spacer, collapseAll);
        header.getStyleClass().add("fxt-grid-header");
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    private static IconifyIcon icon(String literal, int size) {
        IconifyIcon icon = new IconifyIcon(literal);
        icon.setIconSize(size);
        return icon;
    }

    private VBox placeholder(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("fxt-empty-state-text");
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.getStyleClass().add("fxt-empty-state");
        box.setFillWidth(true);
        VBox.setVgrow(label, Priority.NEVER);
        return box;
    }
}
