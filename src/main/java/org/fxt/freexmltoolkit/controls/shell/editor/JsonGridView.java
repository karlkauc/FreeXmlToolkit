package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Consumer;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.grid.JsonCanvasView;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;

/**
 * The JSON Grid view for the unified shell: the same editable grid as the
 * XML one ({@link XmlGridView}), backed by the Canvas-based {@link JsonCanvasView} over
 * the shell's shared {@link JsonEditorContext}.
 *
 * <p>Acts as the toast container for the canvas and round-trips edits back to the owning
 * editor via {@link #setOnModified(Consumer)}.</p>
 */
public class JsonGridView extends GridViewShell {

    private Consumer<String> onModified;
    private Runnable onModelChanged;
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

    /**
     * Sets the callback invoked whenever the grid edits the document, without serializing it
     * (the editor reads the shared model itself).
     *
     * @param onModelChanged the callback (may be {@code null} to clear)
     */
    public void setOnModelChanged(Runnable onModelChanged) {
        this.onModelChanged = onModelChanged;
    }

    /** Rebuilds the rows from the shared model without a round-trip (e.g. after shell undo). */
    public void rebuild() {
        if (canvasView != null) {
            canvasView.rebuild();
        }
        rebuildSecondary();
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
        view.setOnModelModified(() -> {
            if (onModelChanged != null) {
                onModelChanged.run();
            }
            if (onModified != null) {
                String modified = view.getAdapter().serialize();
                if (modified != null) {
                    onModified.accept(modified);
                }
            }
        });
        installCanvas(view);
        if (problems != null) {
            view.getJsonAdapter().setSourceText(problemsText);
            view.setProblems(problems);
        }
        if (entryHelpers != null) {
            entryHelpers.bind(ctx, view, schemaSupplier);
        }
    }

    /** The document's bound JSON Schema (entry helpers), a supplier of {@code null} while none. */
    private java.util.function.Supplier<org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver> schemaSupplier =
            () -> null;
    /** The entry helpers beside the grid, created on first use. */
    private GridEntryHelpersPanel entryHelpers;

    /**
     * @param supplier the document's bound JSON Schema (the entry helpers' suggestions)
     */
    public void setSchemaSupplier(
            java.util.function.Supplier<org.fxt.freexmltoolkit.service.jsonschema.JsonSchemaResolver> supplier) {
        this.schemaSupplier = supplier != null ? supplier : () -> null;
        if (entryHelpers != null) {
            entryHelpers.bind(context, canvasView, schemaSupplier);
        }
    }

    @Override
    protected boolean hasSidePanel() {
        return true;
    }

    @Override
    protected javafx.scene.Node sidePanel() {
        if (entryHelpers == null) {
            entryHelpers = new GridEntryHelpersPanel();
            entryHelpers.bind(context, canvasView, schemaSupplier);
        }
        return entryHelpers;
    }

    /** @return the entry helpers (created on first use) */
    GridEntryHelpersPanel getEntryHelpers() {
        return (GridEntryHelpersPanel) sidePanel();
    }

    /** The last validation problems by line and the text they refer to, re-applied on a rebuild. */
    private java.util.Map<Integer, java.util.List<String>> problems;
    private String problemsText;

    /**
     * Shows the document's validation problems as markers in the grid.
     *
     * @param list the problems of the document (an empty list clears the markers)
     * @param text the text the problems were found in (their lines refer to it)
     */
    public void setProblems(java.util.List<ValidationProblem> list, String text) {
        java.util.Map<Integer, java.util.List<String>> byLine = new java.util.HashMap<>();
        if (list != null) {
            for (ValidationProblem problem : list) {
                if (problem.line() > 0) {
                    byLine.computeIfAbsent(problem.line(), l -> new java.util.ArrayList<>()).add(problem.message());
                }
            }
        }
        problems = byLine.isEmpty() ? null : byLine;
        problemsText = problems == null ? null : text;
        if (canvasView != null) {
            canvasView.getJsonAdapter().setSourceText(problemsText);
            canvasView.setProblems(problems);
        }
    }

    /** Shows the parse-error placeholder (the text is not valid JSON). */
    public void showParseError(String message) {
        this.context = null;
        this.canvasView = null;
        showPlaceholder("Cannot display grid:\n\n" + message + "\n\nFix the JSON errors first.");
    }

    @Override
    protected org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView<?> createSplitCanvas() {
        if (context == null || canvasView == null) {
            return null;
        }
        return new JsonCanvasView(context);
    }

}
