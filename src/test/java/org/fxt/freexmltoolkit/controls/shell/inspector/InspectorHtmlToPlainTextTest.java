package org.fxt.freexmltoolkit.controls.shell.inspector;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The inspector renders XSD documentation (which may be HTML from the Markdown renderer) as
 * plain text. Block boundaries must become line breaks so multiple {@code <xs:documentation>}
 * entries (e.g. English + German) stay on separate lines instead of running together.
 */
class InspectorHtmlToPlainTextTest {

    @Test
    void separatesParagraphsWithNewlines() {
        String html = "<p>This element contains the Name.</p><p>Das Element bezeichnet den Datenlieferanten.</p>";
        assertEquals("This element contains the Name.\nDas Element bezeichnet den Datenlieferanten.",
                InspectorPanel.htmlToPlainText(html));
    }

    @Test
    void brBecomesNewline() {
        assertEquals("line one\nline two", InspectorPanel.htmlToPlainText("line one<br/>line two"));
    }

    @Test
    void stripsTagsAndDecodesEntities() {
        assertEquals("a < b & c", InspectorPanel.htmlToPlainText("<span>a &lt; b &amp; c</span>"));
    }

    @Test
    void collapsesWhitespaceButKeepsLineBreaks() {
        assertEquals("hello world\nnext", InspectorPanel.htmlToPlainText("<p>hello    world</p><p>next</p>"));
    }

    @Test
    void nullAndPlainPassThrough() {
        assertEquals("", InspectorPanel.htmlToPlainText(null));
        assertEquals("just text", InspectorPanel.htmlToPlainText("just text"));
    }
}
