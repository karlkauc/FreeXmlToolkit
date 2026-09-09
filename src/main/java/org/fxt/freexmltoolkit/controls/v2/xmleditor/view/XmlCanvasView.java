package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * The XMLSpy-style grid canvas over an XML instance document: the shared
 * {@link GridCanvasView} bound to an {@link XmlEditorContext} through {@link XmlGridAdapter}.
 *
 * <p>Rendering, scrolling, hit-testing, keyboard navigation, inline editing and search live
 * in the generic base; this class only fixes the model type and keeps the historical name
 * used by the shell ({@code XmlGridView}) and the tests.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class XmlCanvasView extends GridCanvasView<XmlNode> {

    public XmlCanvasView(XmlEditorContext context) {
        super(new XmlGridAdapter(context));
    }
}
