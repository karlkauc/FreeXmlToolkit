package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement.DocumentationInfo;
import org.fxt.freexmltoolkit.util.MarkdownSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Documentation that is not rendered as Markdown must not reach the generated HTML as markup.
 * The templates emit it with {@code th:utext}, so the escaping happens at the HTML boundary -
 * and deliberately NOT in {@link XsdExtendedElement}, because the PDF/Word/Excel exporters read
 * those getters and flatten them with {@link MarkdownSupport#toPlainText(String)}.
 */
@DisplayName("XsdDocumentationHtmlService - escaping of unrendered documentation")
class XsdDocumentationEscapingTest {

    private static final String XPATH = "/root/Element";
    private static final String RAW = "<b>bold</b> & <i>italic</i>";

    private XsdDocumentationHtmlService service;
    private XsdExtendedElement element;

    @BeforeEach
    void setUp() {
        element = new XsdExtendedElement();
        element.setElementName("Element");
        element.setCurrentXpath(XPATH);
        element.setDocumentations(List.of(new DocumentationInfo("default", RAW)));

        Map<String, XsdExtendedElement> map = new LinkedHashMap<>();
        map.put(XPATH, element);
        XsdDocumentationData data = new XsdDocumentationData();
        data.setExtendedXsdElementMap(map);

        service = new XsdDocumentationHtmlService();
        service.setDocumentationData(data);
    }

    @Test
    @DisplayName("unrendered element documentation is escaped before it reaches the page")
    void plainElementDocumentationIsEscaped() {
        element.setUseMarkdownRenderer(false);

        String cell = service.getChildDocumentation(XPATH);
        Map<String, String> docs = service.getChildDocumentations(XPATH);

        assertEquals("&lt;b&gt;bold&lt;/b&gt; &amp; &lt;i&gt;italic&lt;/i&gt;", cell);
        assertEquals(cell, docs.get("default"));
        assertFalse(cell.contains("<b>"), "raw markup must not survive into th:utext");
    }

    @Test
    @DisplayName("rendered Markdown is passed through as the HTML it already is")
    void renderedDocumentationIsNotDoubleEscaped() {
        element.setUseMarkdownRenderer(true);
        element.setDocumentations(List.of(new DocumentationInfo("default", "A **bold** word.")));

        String cell = service.getChildDocumentation(XPATH);

        assertTrue(cell.contains("<strong>bold</strong>"), "rendered Markdown must stay HTML: " + cell);
        assertFalse(cell.contains("&lt;strong&gt;"), "must not be escaped a second time: " + cell);
    }

    @Test
    @DisplayName("the plain-text exporters are unaffected: they still strip the markup")
    void plainTextExportersKeepStrippingMarkup() {
        element.setUseMarkdownRenderer(false);

        // What XsdDocumentationWordService / -PdfService / DataDictionaryExcelExporter read:
        String forExporters = element.getLanguageDocumentation().get("default");
        assertEquals(RAW, forExporters, "the element getters must stay unescaped");

        // Escaping there would decode back to visible tags instead of stripping them.
        assertEquals("bold & italic", MarkdownSupport.toPlainText(forExporters));
    }
}
