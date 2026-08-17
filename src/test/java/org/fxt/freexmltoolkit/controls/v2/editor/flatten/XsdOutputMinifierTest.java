package org.fxt.freexmltoolkit.controls.v2.editor.flatten;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link XsdOutputMinifier}: inter-tag whitespace collapses, text content
 * (documentation with newlines and escaped characters) and comment interiors
 * stay untouched, and the result still parses.
 */
class XsdOutputMinifierTest {

    @Test
    void collapsesInterTagWhitespaceAndStillParses() throws Exception {
        String pretty = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="person" type="xs:string"/>
                    <xs:complexType name="PersonType">
                        <xs:sequence>
                            <xs:element name="name" type="xs:string"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:schema>
                """;
        String minified = XsdOutputMinifier.minify(pretty);
        assertFalse(minified.matches("(?s).*>\\s+<.*"), minified);
        assertTrue(minified.length() < pretty.length());
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(minified.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void preservesDocumentationTextContent() {
        String xml = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:annotation>
                        <xs:documentation>Line one
                keeps &gt; internal   spacing
                and line breaks</xs:documentation>
                    </xs:annotation>
                </xs:schema>
                """;
        String minified = XsdOutputMinifier.minify(xml);
        assertTrue(minified.contains("Line one\n"), minified);
        assertTrue(minified.contains("&gt; internal   spacing"), minified);
        assertTrue(minified.contains("<xs:annotation><xs:documentation>"), minified);
    }

    @Test
    void preservesCommentInteriors() {
        String xml = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <!-- multi
                line   > comment < with markup-ish text -->
                    <xs:element name="a" type="xs:string"/>
                </xs:schema>
                """;
        String minified = XsdOutputMinifier.minify(xml);
        assertTrue(minified.contains("<!-- multi\nline   > comment < with markup-ish text -->"), minified);
        String withoutComments = minified.replaceAll("(?s)<!--.*?-->", "");
        assertFalse(withoutComments.matches("(?s).*>\\s+<.*"),
                "no inter-tag whitespace expected outside comments: " + minified);
    }

    @Test
    void handlesNullAndBlank() {
        assertNull(XsdOutputMinifier.minify(null));
        assertEquals("", XsdOutputMinifier.minify(""));
    }
}
