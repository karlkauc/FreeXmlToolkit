package org.fxt.freexmltoolkit.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context.ContextAnalyzer;
import org.junit.jupiter.api.Test;

/** Correctness gate for the buildXPathContext optimization: the XPath must be unchanged. */
class ContextAnalyzerEquivalenceTest {

    private static String xpathAt(String xml, int caret) {
        return ContextAnalyzer.analyze(xml, caret).getXPath();
    }

    @Test
    void xpathStackIsUnchangedAcrossCaretBattery() {
        String xml = "<root>\n  <a>\n    <b>text</b>\n    <c x=\"1\"/>\n  </a>\n  <!-- note -->\n"
                + "  <d><![CDATA[stuff]]></d>\n  <e>\n";

        // caret right after "<b>" opening (inside element b under a under root)
        int inB = xml.indexOf("text");
        assertEquals("/root/a/b", xpathAt(xml, inB));

        // caret after the self-closing <c x="1"/> — back to /root/a
        int afterC = xml.indexOf("/>", xml.indexOf("<c")) + 2;
        assertEquals("/root/a", xpathAt(xml, afterC));

        // caret after </a> — back to /root
        int afterCloseA = xml.indexOf("</a>") + 4;
        assertEquals("/root", xpathAt(xml, afterCloseA));

        // caret after the comment — still /root
        int afterComment = xml.indexOf("-->") + 3;
        assertEquals("/root", xpathAt(xml, afterComment));

        // caret inside <d> after the CDATA — /root/d
        int inD = xml.indexOf("]]>") + 3;
        assertEquals("/root/d", xpathAt(xml, inD));

        // caret at the very end, inside the still-open <e> — /root/e
        assertEquals("/root/e", xpathAt(xml, xml.length()));

        // caret inside an incomplete tag: "<chi" with no '>' yet.
        // ADJUSTED to current behavior (Task 1 Step 6): the current ContextAnalyzer reports the
        // xpath of the enclosing complete element (/root/parent) for an unterminated start tag —
        // it exposes the partial name only via getCurrentElement() ('chi'), not in getXPath().
        // The golden is "no CHANGE later", so we pin the CURRENT output, not an idealized one.
        String incomplete = "<root>\n  <parent>\n    <chi";
        assertEquals("/root/parent", xpathAt(incomplete, incomplete.length()));
    }
}
