package org.fxt.freexmltoolkit.controls.jsoneditor.grid;

import org.fxt.freexmltoolkit.controls.jsoneditor.editor.JsonEditorContext;
import org.fxt.freexmltoolkit.controls.jsoneditor.model.JsonNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.view.GridCanvasView;

/**
 * The grid canvas over a JSON document: the shared {@link GridCanvasView} bound to a
 * {@link JsonEditorContext} through {@link JsonGridAdapter}, so the JSON grid looks and
 * behaves exactly like the XML grid.
 */
public class JsonCanvasView extends GridCanvasView<JsonNode> {

    public JsonCanvasView(JsonEditorContext context) {
        super(new JsonGridAdapter(context));
    }

    /** @return the adapter's editor context */
    public JsonEditorContext getContext() {
        return ((JsonGridAdapter) getAdapter()).getContext();
    }
}
