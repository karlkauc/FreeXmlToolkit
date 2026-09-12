package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * The shared Markdown helper: rendering, the {@code @markdown} appinfo flag in both encodings,
 * the escape-when-off rule that makes {@code th:utext} safe, and the plain-text flattening used
 * by the PDF/Word/Excel exporters.
 */
class MarkdownSupportTest {

    /** @return the {@code xs:annotation} node of the single element in the snippet. */
    private static Node annotationOf(String elementXml) throws Exception {
        String xsd = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  %s
                </xs:schema>
                """.formatted(elementXml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);            // getLocalName() is null otherwise
        Document doc = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xsd.getBytes(StandardCharsets.UTF_8)));
        return doc.getElementsByTagNameNS("http://www.w3.org/2001/XMLSchema", "annotation").item(0);
    }

    @Test
    @DisplayName("renders GitHub-flavoured Markdown")
    void rendersBoldAndTables() {
        assertTrue(MarkdownSupport.render("A **bold** word.").contains("<strong>bold</strong>"));
        assertTrue(MarkdownSupport.render("| a | b |\n|---|---|\n| 1 | 2 |").contains("<table>"),
                "the TablesExtension must stay enabled");
        assertTrue(MarkdownSupport.render("~~gone~~").contains("<del>gone</del>"),
                "the StrikethroughExtension must stay enabled");
    }

    @Test
    @DisplayName("@markdown is read from the canonical and the legacy encoding")
    void markdownFlagReadsCanonicalAndLegacyAppinfo() throws Exception {
        assertEquals(Boolean.TRUE, MarkdownSupport.markdownFlag(annotationOf("""
                <xs:element name="E"><xs:annotation>
                  <xs:appinfo source="@markdown">true</xs:appinfo>
                </xs:annotation></xs:element>""")));

        assertEquals(Boolean.FALSE, MarkdownSupport.markdownFlag(annotationOf("""
                <xs:element name="E"><xs:annotation>
                  <xs:appinfo source="@markdown">false</xs:appinfo>
                </xs:annotation></xs:element>""")));

        assertEquals(Boolean.TRUE, MarkdownSupport.markdownFlag(annotationOf("""
                <xs:element name="E"><xs:annotation>
                  <xs:appinfo source="@markdown true"/>
                </xs:annotation></xs:element>""")), "legacy source-only encoding");
    }

    @Test
    @DisplayName("a silent or unparsable annotation yields null, not a default")
    void markdownFlagIsNullWhenAbsentOrUnparsable() throws Exception {
        assertNull(MarkdownSupport.markdownFlag(null));

        assertNull(MarkdownSupport.markdownFlag(annotationOf("""
                <xs:element name="E"><xs:annotation>
                  <xs:documentation>No appinfo at all.</xs:documentation>
                </xs:annotation></xs:element>""")));

        assertNull(MarkdownSupport.markdownFlag(annotationOf("""
                <xs:element name="E"><xs:annotation>
                  <xs:appinfo source="@since">4.0.0</xs:appinfo>
                </xs:annotation></xs:element>""")), "an unrelated tag must not be read as a flag");

        assertNull(MarkdownSupport.markdownFlag(annotationOf("""
                <xs:element name="E"><xs:annotation>
                  <xs:appinfo source="@markdown">vielleicht</xs:appinfo>
                </xs:annotation></xs:element>""")), "an unparsable value states nothing");
    }

    @Test
    @DisplayName("renderOrEscape escapes when Markdown is off, so th:utext stays safe")
    void renderOrEscapeEscapesWhenOff() {
        assertTrue(MarkdownSupport.renderOrEscape("A **bold** word.", true).contains("<strong>bold</strong>"));

        String off = MarkdownSupport.renderOrEscape("A **bold** word.", false);
        assertEquals("A **bold** word.", off, "literal Markdown must stay visible as typed");

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;",
                MarkdownSupport.renderOrEscape("<script>alert(1)</script>", false));
        assertEquals("", MarkdownSupport.renderOrEscape(null, true));
    }

    @Test
    @DisplayName("toPlainText decodes entities and keeps block boundaries apart")
    void toPlainTextDecodesEntitiesAndSeparatesBlocks() {
        assertEquals("bold\na\nb",
                MarkdownSupport.toPlainText("<p><strong>bold</strong></p><ul><li>a</li><li>b</li></ul>"),
                "list items must not run together");
        assertEquals("a < b & c", MarkdownSupport.toPlainText("<span>a &lt; b &amp; c</span>"));
        assertEquals("line one\nline two", MarkdownSupport.toPlainText("line one<br/>line two"));
        assertEquals("", MarkdownSupport.toPlainText(null));
        assertEquals("just text", MarkdownSupport.toPlainText("just text"));
    }
}
