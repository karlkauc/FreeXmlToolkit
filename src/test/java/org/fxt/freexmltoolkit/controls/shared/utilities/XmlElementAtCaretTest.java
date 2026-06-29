package org.fxt.freexmltoolkit.controls.shared.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link XmlElementAtCaret}: the innermost element enclosing a
 * caret is resolved to its verbatim XML fragment and a positional XPath, while
 * comments / CDATA / processing instructions and quoted attribute values never
 * confuse the tag scan.
 */
class XmlElementAtCaretTest {

    @Test
    void innermostElementAtCaret() {
        String xml = "<root><items><item><name>Bob</name></item></items></root>";
        int caret = xml.indexOf("Bob");

        XmlElementAtCaret.Result r = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("<name>Bob</name>", r.xml());
        assertEquals("/root/items/item/name", r.xpath());
    }

    @Test
    void positionalIndexForSameNameSiblings() {
        String xml = "<root><item>A</item><item>B</item><item>C</item></root>";
        int caret = xml.indexOf('B');

        XmlElementAtCaret.Result r = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("<item>B</item>", r.xml());
        assertEquals("/root/item[2]", r.xpath());
    }

    @Test
    void selfClosingElement() {
        String xml = "<root><a/><b attr=\"x\"/></root>";
        int caret = xml.indexOf("attr");

        XmlElementAtCaret.Result r = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("<b attr=\"x\"/>", r.xml());
        assertEquals("/root/b", r.xpath());
    }

    @Test
    void attributeValueContainingGreaterThanIsNotATagEnd() {
        String xml = "<root><expr cond=\"a > b\">ok</expr></root>";
        int caret = xml.indexOf("ok");

        XmlElementAtCaret.Result r = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("<expr cond=\"a > b\">ok</expr>", r.xml());
        assertEquals("/root/expr", r.xpath());
    }

    @Test
    void markupInsideCommentAndCdataIsIgnored() {
        String xml = "<root><!-- <fake/> --><data><![CDATA[<x>]]>value</data></root>";
        int caret = xml.indexOf("value");

        XmlElementAtCaret.Result r = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("<data><![CDATA[<x>]]>value</data>", r.xml());
        assertEquals("/root/data", r.xpath());
    }

    @Test
    void prefixedNamesUseLocalNameInXPath() {
        String xml = "<a:root xmlns:a=\"urn:x\"><a:child>v</a:child></a:root>";
        int caret = xml.indexOf('v');

        XmlElementAtCaret.Result r = XmlElementAtCaret.at(xml, caret).orElseThrow();
        assertEquals("<a:child>v</a:child>", r.xml());
        assertEquals("/root/child", r.xpath());
    }

    @Test
    void caretOutsideAnyElementIsEmpty() {
        assertTrue(XmlElementAtCaret.at("", 0).isEmpty());
        Optional<XmlElementAtCaret.Result> prolog = XmlElementAtCaret.at("<?xml version=\"1.0\"?>\n", 5);
        assertFalse(prolog.isPresent());
    }
}
