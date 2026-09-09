package org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.selection;

import org.fxt.freexmltoolkit.controls.v2.editor.core.NodeSelectionModel;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;

/**
 * Manages selection state in the XML editor.
 *
 * <p>Thin {@link XmlNode}-typed specialisation of the generic
 * {@link NodeSelectionModel}; all behaviour (single/multiple selection, the
 * "selectedNode" / "selectedNodes" / "selectionCleared" events) lives in the
 * generic base class.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
public class SelectionModel extends NodeSelectionModel<XmlNode> {

    /**
     * Constructs a new SelectionModel with single selection mode.
     */
    public SelectionModel() {
        super();
    }

    /**
     * Constructs a new SelectionModel with specified selection mode.
     *
     * @param multipleSelectionEnabled true to enable multiple selection
     */
    public SelectionModel(boolean multipleSelectionEnabled) {
        super(multipleSelectionEnabled);
    }
}
