package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Consumer;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;

/**
 * The JSON Grid view for the unified shell: the same XMLSpy-style editable grid as the
 * XML one ({@link XmlGridView}), backed by the Canvas-based {@link JsonCanvasView} over
 * the shell's shared {@link JsonEditorContext}.
 *
 * <p>Acts as the toast container for the canvas and round-trips edits back to the owning
 * editor via {@link #setOnModified(Consumer)}.</p>
 */
public class JsonGridView extends GridViewShell {

    private Consumer<String> onModified;
    private Consumer<JsonNode> onSelectionChanged;
    private JsonEditorContext context;
    private JsonCanvasView canvasView;

    public JsonGridView() {
        super();
    }

    @Override
    protected String subtitle() {
        return "· nested · arrays of objects as embedded grids";
    }

    /** @return the current grid's editor context (model + command stack), or {@code null}. */
    public JsonEditorContext getContext() {
        return context;
    }

    /** @return the grid's canvas as a search target for the shell's search bar, or {@code null}. */
    public XmlSearchTarget getSearchTarget() {
        return canvasView;
    }

    /** @return the embedded canvas, or {@code null} while a placeholder is shown */
    public JsonCanvasView getCanvasView() {
        return canvasView;
    }

    /** Sets the callback invoked when the grid's selected node changes (for the inspector). */
    public void setOnSelectionChanged(Consumer<JsonNode> onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    /**
     * Sets the callback invoked with the serialized JSON whenever the grid edits the
     * document, so the editor text can be kept in sync.
     *
     * @param onModified receives the modified JSON (may be {@code null} to clear)
     */
    public void setOnModified(Consumer<String> onModified) {
        this.onModified = onModified;
    }

    /** Rebuilds the rows from the shared model without a round-trip (e.g. after shell undo). */
    public void rebuild() {
        if (canvasView != null) {
            canvasView.rebuild();
        }
    }

    /**
     * Renders the given (already parsed) editor context — the shell shares ONE
     * {@link JsonEditorContext} across its Text/Tree/Grid views, so edits and undo history are
     * preserved when switching modes. A {@code null}/empty context shows a placeholder. Rendering
     * the context already shown is a no-op (avoids rebinding the selection listener).
     *
     * @param ctx the shared context to render (may be {@code null})
     */
    public void setContext(JsonEditorContext ctx) {
        if (ctx == this.context && showsCanvas(JsonCanvasView.class)) {
            return; // already showing this context
        }
        getChildren().clear();
        this.context = ctx;
        this.canvasView = null;
        if (ctx == null || ctx.getDocument() == null || ctx.getDocument().getRootValue() == null) {
            showPlaceholder("No JSON content to display.");
            return;
        }
        ctx.getSelectionModel().addPropertyChangeListener("selectedNode", evt -> {
            if (onSelectionChanged != null) {
                onSelectionChanged.accept((JsonNode) evt.getNewValue());
            }
        });
        JsonCanvasView view = new JsonCanvasView(ctx);
        this.canvasView = view;
        view.setOnDocumentModified(modified -> {
            if (modified != null && onModified != null) {
                onModified.accept(modified);
            }
        });
        installCanvas(view);
    }

    /** Shows the parse-error placeholder (the text is not valid JSON). */
    public void showParseError(String message) {
        this.context = null;
        this.canvasView = null;
        showPlaceholder("Cannot display grid:\n\n" + message + "\n\nFix the JSON errors first.");
    }
}
