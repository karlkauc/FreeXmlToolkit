package org.fxt.freexmltoolkit.controls.v2.editor.intellisense.context;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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

    @Test
    void closingTag_simpleElement() {
        // <a>text</ → should identify "a" as the element to close
        String xml = "<a>text</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertNotNull(context.getXPathContext());
        assertEquals("a", context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_nestedElements() {
        // <a><b>text</ → should identify "b" (innermost unclosed)
        String xml = "<a><b>text</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertEquals("b", context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_afterClosedSibling() {
        // <a><b>text</b></ → "b" is closed, should identify "a"
        String xml = "<a><b>text</b></";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertEquals("a", context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_afterSelfClosing() {
        // <a><br/>text</ → <br/> is self-closing, should identify "a"
        String xml = "<a><br/>text</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertEquals("a", context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_withNamespacePrefix() {
        // <ns:element>text</ → should identify "ns:element"
        String xml = "<ns:element>text</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertEquals("ns:element", context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_atRootLevel() {
        // </ with no opening tags → no element to close
        String xml = "</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertNotNull(context.getXPathContext());
        assertNull(context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_deeplyNested() {
        // <a><b><c>text</ → should identify "c"
        String xml = "<a><b><c>text</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertEquals("c", context.getXPathContext().getCurrentElement());
    }

    @Test
    void closingTag_withAttributes() {
        // <div class="main"><span id="x">text</ → should identify "span"
        String xml = "<div class=\"main\"><span id=\"x\">text</";
        XmlContext context = ContextAnalyzer.analyze(xml, xml.length());
        assertEquals("span", context.getXPathContext().getCurrentElement());
    }
}
