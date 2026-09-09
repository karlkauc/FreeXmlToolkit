package org.fxt.freexmltoolkit.controls.shell.editor;

import java.util.function.Consumer;

import org.fxt.freexmltoolkit.controls.shared.utilities.XmlSearchTarget;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.XmlCanvasView;

/**
 * The XML instance Grid view for the unified shell: an XMLSpy-style editable grid
 * over an XML document, backed by the Canvas-based {@link XmlCanvasView}.
 *
 * <p>Acts as the toast container for the canvas and round-trips edits back to the
 * owning editor via {@link #setOnModified(Consumer)} (the same mechanism the
 * retired legacy {@code XmlEditor} used for its graphic view).</p>
 */
public class XmlGridView extends GridViewShell {

    private Consumer<String> onModified;
    private Consumer<XmlNode> onSelectionChanged;
    private XmlEditorContext context;
    private XmlCanvasView canvasView;

    public XmlGridView() {
        super();
    }

    @Override
    protected String subtitle() {
        return "· nested · repeating elements as embedded grids";
    }

    /** @return the current grid's editor context (model + command stack), or {@code null}. */
    public XmlEditorContext getContext() {
        return context;
    }

    /** @return the grid's canvas as a search target for the shell's search bar, or {@code null}. */
    public XmlSearchTarget getSearchTarget() {
        return canvasView;
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
            context = null;
            canvasView = null;
            showPlaceholder("No XML content to display.");
            return;
        }
        XmlEditorContext ctx = new XmlEditorContext();
        try {
            ctx.loadDocumentFromString(xml);
        } catch (Exception e) {
            context = null;
            canvasView = null;
            showPlaceholder("Cannot display grid:\n\n" + e.getMessage() + "\n\nFix the XML errors first.");
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
        if (ctx == this.context && showsCanvas(XmlCanvasView.class)) {
            return; // already showing this context
        }
        getChildren().clear();
        this.context = ctx;
        this.canvasView = null;
        if (ctx == null || ctx.getDocument() == null) {
            showPlaceholder("No XML content to display.");
            return;
        }
        ctx.getSelectionModel().addPropertyChangeListener("selectedNode", evt -> {
            if (onSelectionChanged != null) {
                onSelectionChanged.accept((XmlNode) evt.getNewValue());
            }
        });
        XmlCanvasView view = new XmlCanvasView(ctx);
        this.canvasView = view;
        view.setOnDocumentModified(modified -> {
            if (modified != null && onModified != null) {
                onModified.accept(modified);
            }
        });
        installCanvas(view);
    }
}
