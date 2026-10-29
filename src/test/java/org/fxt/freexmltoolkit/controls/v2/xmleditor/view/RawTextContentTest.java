package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.commands.ReplaceChildrenCommand;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlNode;
import org.junit.jupiter.api.Test;

/** Edit as Raw Text: an element's content as markup, parsed back into children, undoable. */
class RawTextContentTest {

    @Test
    void roundTripsMixedContentAndReplacesTheChildren() {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString("<root><p>Hello <b>bold</b> &amp; more<!--c--></p></root>");
        XmlElement p = context.getDocument().getRootElement().getChildElement("p");
        String raw = RawTextContent.of(p);
        assertEquals("Hello <b>bold</b> &amp; more<!-- c -->", raw, "markup and entities stay unresolved");

        List<XmlNode> nodes = RawTextContent.parse("<i>new</i> text");
        assertTrue(context.executeCommand(new ReplaceChildrenCommand(p, nodes)));
        assertEquals("<i>new</i> text", RawTextContent.of(p));

        context.undo();
        assertEquals(raw, RawTextContent.of(p), "one undo step restores the old content");
    }

    @Test
    void malformedMarkupIsReported() {
        assertThrows(IllegalArgumentException.class, () -> RawTextContent.parse("<a><b></a>"));
        assertTrue(RawTextContent.parse("").isEmpty(), "empty content is allowed");
    }
}
