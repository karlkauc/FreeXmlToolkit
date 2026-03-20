package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ContextAnalyzer}, focusing on XPath extraction at various cursor positions.
 */
class ContextAnalyzerTest {

    private static final String XML =
            "<root>\n" +
            "  <parent>\n" +
            "    <child>text</child>\n" +
            "  </parent>\n" +
            "</root>";

    @Test
    void xpathInsideTextContent() {
        // Cursor inside the text content of <child>: "<root>\n  <parent>\n    <child>te"
        int caretPos = XML.indexOf("text") + 2; // inside "text"
        XmlContext context = ContextAnalyzer.analyze(XML, caretPos);

        assertEquals("/root/parent/child", context.getXPath());
    }

    @Test
    void xpathOnOpeningTagName() {
        // Cursor inside the opening tag name: "<root>\n  <parent>\n    <chi"
        int caretPos = XML.indexOf("<child>") + 4; // on "chi|ld>"
        XmlContext context = ContextAnalyzer.analyze(XML, caretPos);

        assertEquals("/root/parent/child", context.getXPath());
    }

    @Test
    void xpathRightAfterOpenAngleBracket() {
        // Cursor right after '<' of <child>: "<root>\n  <parent>\n    <"
        int caretPos = XML.indexOf("<child>") + 1; // right after '<'
        XmlContext context = ContextAnalyzer.analyze(XML, caretPos);

        // The element name starts being typed; even partial should resolve
        assertEquals("/root/parent/child", context.getXPath());
    }

    @Test
    void xpathOnParentElement() {
        // Cursor in the text area of <parent> (before <child>)
        int caretPos = XML.indexOf("<child>") - 1; // whitespace before <child>
        XmlContext context = ContextAnalyzer.analyze(XML, caretPos);

        assertEquals("/root/parent", context.getXPath());
    }

    @Test
    void xpathOnRootElement() {
        // Cursor right after <root> opening tag
        int caretPos = XML.indexOf(">") + 1; // right after first '>'
        XmlContext context = ContextAnalyzer.analyze(XML, caretPos);

        assertEquals("/root", context.getXPath());
    }

    @Test
    void xpathAfterClosingTag() {
        // Cursor right after </child>
        int caretPos = XML.indexOf("</child>") + "</child>".length();
        XmlContext context = ContextAnalyzer.analyze(XML, caretPos);

        // After </child> closes, we're back inside <parent>
        assertEquals("/root/parent", context.getXPath());
    }

    @Test
    void xpathWithAttributes() {
        String xml = "<root><item id=\"123\" name=\"test\">value</item></root>";

        // Cursor inside opening tag with attributes (between attribute name)
        int caretPos = xml.indexOf("name");
        XmlContext context = ContextAnalyzer.analyze(xml, caretPos);

        assertEquals("/root/item", context.getXPath());
    }

    @Test
    void xpathEmptyDocument() {
        XmlContext context = ContextAnalyzer.analyze("", 0);
        assertEquals("/", context.getXPath());
    }

    @Test
    void xpathSelfClosingElement() {
        String xml = "<root><empty/><after>text</after></root>";

        // Cursor inside <after>'s text
        int caretPos = xml.indexOf("text");
        XmlContext context = ContextAnalyzer.analyze(xml, caretPos);

        assertEquals("/root/after", context.getXPath());
    }

    @Test
    void xpathSiblingElements() {
        String xml = "<root><first>a</first><second>b</second></root>";

        // Cursor inside <second>'s text
        int caretPos = xml.indexOf("b");
        XmlContext context = ContextAnalyzer.analyze(xml, caretPos);

        assertEquals("/root/second", context.getXPath());
    }
}
