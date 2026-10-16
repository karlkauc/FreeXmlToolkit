package org.fxt.freexmltoolkit.controls.shell.schema;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlCData;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlComment;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlProcessingInstruction;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlText;

/**
 * The Bootstrap icon of an XML-instance node kind, shared by the instance tree rows and the
 * inspector's node header tile (the XML counterpart of {@link XsdNodeLabels#icon}).
 */
public final class XmlNodeIcons {

    private XmlNodeIcons() {
    }

    /** @return the {@code bi-*} literal for the node; elements (and unknown kinds) use {@code bi-code-slash} */
    public static String icon(XmlNode node) {
        if (node instanceof XmlText) {
            return "bi-fonts";
        }
        if (node instanceof XmlComment) {
            return "bi-chat-left-text";
        }
        if (node instanceof XmlCData) {
            return "bi-file-earmark-code";
        }
        if (node instanceof XmlProcessingInstruction) {
            return "bi-gear";
        }
        if (node instanceof XmlDocument) {
            return "bi-file-earmark";
        }
        return "bi-code-slash";
    }
}
