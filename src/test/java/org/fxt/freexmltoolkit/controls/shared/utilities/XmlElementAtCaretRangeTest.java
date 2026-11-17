package org.fxt.freexmltoolkit.controls.shared.utilities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class XmlElementAtCaretRangeTest {

    private static final String XSD = """
            <?xml version="1.0"?>
            <!-- <xs:fake/> -->
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:annotation><xs:documentation>d</xs:documentation></xs:annotation>
              <xs:element name="a"/>
              <xs:element name="b"><xs:complexType/></xs:element>
            </xs:schema>
            """;

    @Test
    void indexPathFindsTheElementRange() {
        var range = XmlElementAtCaret.rangeOf(XSD, List.of(2, 0), null).orElseThrow();
        assertEquals("<xs:complexType/>", XSD.substring(range.start(), range.end()));
        assertTrue(XmlElementAtCaret.rangeOf(XSD, List.of(), null).isPresent());
    }

    @Test
    void skippedNamesAreNotCounted() {
        var range = XmlElementAtCaret.rangeOf(XSD, List.of(1),
                n -> n.equals("annotation")).orElseThrow();
        assertTrue(XSD.substring(range.start(), range.end()).startsWith("<xs:element name=\"b\">"));
        assertTrue(XmlElementAtCaret.rangeOf(XSD, List.of(7), null).isEmpty());
    }

    @Test
    void startTagBeforeFindsTheElementOnItsLineAndSkipsLongerNames() {
        String xml = "<r>\n  <Datenpunkt>\n    <TemplateCodeX>a</TemplateCodeX>\n    <TemplateCode a=\"1\">x</TemplateCode>\n  </Datenpunkt>\n</r>";
        int lineEnd = xml.indexOf("</TemplateCode>") + "</TemplateCode>".length();
        int start = XmlElementAtCaret.startTagBefore(xml, lineEnd, "TemplateCode", 1000);
        org.junit.jupiter.api.Assertions.assertEquals(xml.indexOf("<TemplateCode a="), start);
        org.junit.jupiter.api.Assertions.assertEquals(-1, XmlElementAtCaret.startTagBefore(xml, lineEnd, "Missing", 1000));
    }

    @Test
    void elementEndFromSkipsCommentsCdataAndQuotedGreaterThan() {
        String xml = "<a x=\"1>2\"><a/><!-- </a> --><![CDATA[</a>]]><?pi </a>?><b>t</b></a><tail/>";
        int end = XmlElementAtCaret.elementEndFrom(xml, 0, xml.length());
        org.junit.jupiter.api.Assertions.assertEquals(xml.indexOf("<tail/>"), end);
        org.junit.jupiter.api.Assertions.assertEquals(xml.indexOf("<!--"),
                XmlElementAtCaret.elementEndFrom(xml, xml.indexOf("<a/>"), 100));
        org.junit.jupiter.api.Assertions.assertEquals(-1, XmlElementAtCaret.elementEndFrom(xml, 0, 20), "beyond the limit");
    }
}
