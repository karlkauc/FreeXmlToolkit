package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;

/**
 * The one-line inline preview of a collapsed container: compact markup, whitespace runs
 * collapsed, cut off with an ellipsis at the soft limit (so the collapsed root of a large
 * document costs no more than the limit).
 */
class XmlNodePreviewTest {

    private static XmlElement root(String xml) {
        XmlEditorContext context = new XmlEditorContext();
        context.loadDocumentFromString(xml);
        return context.getDocument().getRootElement();
    }

    @Test
    void serializesCompactlyWithAttributesAndCollapsedWhitespace() {
        XmlElement person = root("""
                <Person id="7">
                  <First>Fred</First>
                  <Note>a   b
                  c</Note>
                  <!-- hi -->
                  <Empty/>
                </Person>
                """);
        assertEquals("<Person id=\"7\"><First>Fred</First><Note>a b c</Note><!--hi--><Empty/></Person>",
                XmlNodePreview.of(person, 1000));
    }

    @Test
    void cutsOffAtTheSoftLimitWithAnEllipsis() {
        StringBuilder xml = new StringBuilder("<root>");
        for (int i = 0; i < 500; i++) {
            xml.append("<item>").append(i).append("</item>");
        }
        xml.append("</root>");
        String preview = XmlNodePreview.of(root(xml.toString()), 200);
        assertTrue(preview.endsWith(XmlNodePreview.ELLIPSIS), preview);
        assertTrue(preview.length() < 240, "stops near the limit: " + preview.length());
        assertTrue(preview.startsWith("<root><item>0</item>"), preview);
    }

    @Test
    void shortDocumentsAreNotCutOff() {
        String preview = XmlNodePreview.of(root("<a><b>x</b></a>"), 200);
        assertEquals("<a><b>x</b></a>", preview);
        assertFalse(preview.contains(XmlNodePreview.ELLIPSIS));
    }

    @Test
    void cdataAndProcessingInstructionsKeepTheirMarkup() {
        XmlElement el = root("<a><?pi data?><![CDATA[raw <x>]]></a>");
        assertEquals("<a><?pi data?><![CDATA[raw <x>]]></a>", XmlNodePreview.of(el, 200));
    }
}
