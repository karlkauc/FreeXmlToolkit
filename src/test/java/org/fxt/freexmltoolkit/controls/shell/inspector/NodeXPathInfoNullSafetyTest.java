package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathCalculator;
import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.XPathContext;
import org.junit.jupiter.api.Test;

/**
 * {@link XPathCalculator#calculate} can return a {@code null} {@link XPathContext} (the underlying
 * analyzer yields none for some inputs, and the null is even cached). The inspector must degrade to
 * document-level info instead of throwing an NPE (regression for the crash in populateCaret).
 */
class NodeXPathInfoNullSafetyTest {

    @Test
    void nullXPathContextYieldsDocumentInfo() {
        XPathCalculator nullCalc = new XPathCalculator() {
            @Override
            public XPathContext calculate(String text, int position) {
                return null;
            }
        };
        NodeXPathInfo info = NodeXPathInfo.fromCaret(nullCalc, "<a/>", 1);
        assertEquals("Document", info.kind());
        assertEquals("", info.name());
        assertEquals(0, info.depth());
        assertNotNull(info.xpath());
    }
}
