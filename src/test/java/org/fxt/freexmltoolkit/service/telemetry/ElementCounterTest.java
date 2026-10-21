package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ElementCounterTest {

    private static int count(String xml) {
        return ElementCounter.countStartTags(xml);
    }

    @Test
    void nullIsUnknownAndEmptyIsZero() {
        assertEquals(-1, ElementCounter.countStartTags(null));
        assertEquals(0, count(""));
        assertEquals(0, count("plain text"));
        assertEquals(0, count("<"));
    }

    @Test
    void countsStartAndSelfClosingTagsButNotEndTags() {
        assertEquals(1, count("<root/>"));
        assertEquals(3, count("<root><a>x</a><b/></root>"));
        assertEquals(2, count("<ns:root xmlns:ns=\"urn:x\"><ns:child/></ns:root>"));
        assertEquals(2, count("<_a><ärger/></_a>"));
    }

    @Test
    void skipsXmlDeclarationAndProcessingInstructions() {
        assertEquals(1, count("<?xml version=\"1.0\"?>\n<?xml-stylesheet href=\"<a>.xsl\"?><root/>"));
    }

    @Test
    void skipsCommentsIncludingMarkupInside() {
        assertEquals(1, count("<!-- <a><b/></a> --><root/>"));
        assertEquals(1, count("<root><!-- unterminated <a>"));
    }

    @Test
    void skipsCdataIncludingMarkupInside() {
        assertEquals(1, count("<root><![CDATA[<a><b/>]]></root>"));
        assertEquals(2, count("<root><![CDATA[ ]] > <x> ]]><c/></root>"));
    }

    @Test
    void skipsDoctypeDeclarations() {
        String xml = "<!DOCTYPE root [<!ELEMENT root (#PCDATA)><!ATTLIST root a CDATA #IMPLIED>]><root/>";
        assertEquals(1, count(xml));
    }

    @Test
    void greaterThanInAttributeValuesAndTextDoesNotMatter() {
        assertEquals(2, count("<root a=\"1 > 0\" b='x>y'><c d=\">\"/> 2 > 1 </root>"));
    }

    @Test
    void entitiesAndWhitespaceAfterLessThanAreNotElements() {
        assertEquals(1, count("<root>&lt;a&gt; < b</root>"));
    }
}
