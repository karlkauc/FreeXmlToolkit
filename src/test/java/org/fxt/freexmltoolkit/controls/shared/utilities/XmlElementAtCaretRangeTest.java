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
}
