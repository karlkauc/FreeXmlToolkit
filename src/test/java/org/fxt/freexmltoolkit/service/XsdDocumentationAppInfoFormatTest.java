package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;

import org.fxt.freexmltoolkit.domain.XsdDocInfo;
import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for the documentation pipeline's appinfo reader: both tag encodings, example
 * values in either namespace, and the three {@link XsdDocumentationService.MarkdownMode}s.
 */
@DisplayName("XsdDocumentationService appinfo formats")
class XsdDocumentationAppInfoFormatTest {

    private static final File FIXTURE = new File("src/test/resources/test-appinfo-formats.xsd");

    private static XsdDocumentationService process(XsdDocumentationService.MarkdownMode mode) throws Exception {
        assertTrue(FIXTURE.exists(), "fixture must exist: " + FIXTURE);
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(FIXTURE.getAbsolutePath());
        service.processXsd(mode);
        return service;
    }

    private static XsdExtendedElement element(XsdDocumentationService service, String name) {
        XsdExtendedElement element = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> name.equals(e.getElementName()))
                .findFirst()
                .orElse(null);
        assertNotNull(element, "element not found: " + name);
        return element;
    }

    @Test
    @DisplayName("the canonical tag form feeds the JavaDoc block")
    void canonicalTagsAreRead() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.ALL);
        XsdDocInfo docInfo = element(service, "CanonicalTags").getXsdDocInfo();

        assertNotNull(docInfo);
        assertEquals("4.0.0", docInfo.getSince());
        assertEquals(List.of("{@link /Root/LegacyTags}"), docInfo.getSee());
        assertEquals("Use LegacyTags instead.", docInfo.getDeprecated());
    }

    @Test
    @DisplayName("the legacy tag form still feeds the JavaDoc block")
    void legacyTagsAreRead() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.ALL);
        XsdDocInfo docInfo = element(service, "LegacyTags").getXsdDocInfo();

        assertNotNull(docInfo);
        assertEquals("4.0.0", docInfo.getSince());
        assertEquals(List.of("{@link /Root/CanonicalTags}"), docInfo.getSee());
    }

    @Test
    @DisplayName("@version stays a generic appinfo, @markdown never does")
    void unmodelledTagsBecomeGenericAppInfos() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.PER_NODE);
        List<String> generic = element(service, "CanonicalTags").getGenericAppInfos();

        assertNotNull(generic, "the @version tag must reach the Additional Information section");
        assertTrue(generic.contains("@version 1.2"), generic.toString());
        assertTrue(generic.stream().noneMatch(s -> s.contains("@markdown")),
                "@markdown is a rendering hint, not documentation: " + generic);
    }

    @Test
    @DisplayName("example values are read from the fxt and the legacy Altova namespace")
    void exampleValuesAreReadFromBothNamespaces() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.ALL);

        assertEquals(List.of("TRX-0815", "TRX-0816"), element(service, "FxtExamples").getExampleValues());
        assertEquals(List.of("WBAH", "XLON"), element(service, "AltovaExamples").getExampleValues());
    }

    @Test
    @DisplayName("MarkdownMode.ALL renders every node")
    void markdownModeAll() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.ALL);

        assertTrue(isRendered(service, "CanonicalTags"));
        assertTrue(isRendered(service, "MarkdownOff"));
        assertTrue(isRendered(service, "MarkdownUnset"));
    }

    @Test
    @DisplayName("MarkdownMode.OFF renders no node, whatever @markdown says")
    void markdownModeOff() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.OFF);

        assertFalseRendered(service, "CanonicalTags");
        assertFalseRendered(service, "MarkdownOff");
        assertFalseRendered(service, "MarkdownUnset");
    }

    @Test
    @DisplayName("MarkdownMode.PER_NODE renders only the nodes that ask for it")
    void markdownModePerNode() throws Exception {
        XsdDocumentationService service = process(XsdDocumentationService.MarkdownMode.PER_NODE);

        assertTrue(isRendered(service, "CanonicalTags"), "@markdown = true must render");
        assertFalseRendered(service, "MarkdownOff");
        assertFalseRendered(service, "MarkdownUnset");
    }

    @Test
    @DisplayName("the boolean overloads keep working")
    void booleanOverloadsMapToTheModes() throws Exception {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(FIXTURE.getAbsolutePath());
        service.setUseMarkdownRenderer(true);
        assertEquals(XsdDocumentationService.MarkdownMode.ALL, service.getMarkdownMode());
        service.setUseMarkdownRenderer(false);
        assertEquals(XsdDocumentationService.MarkdownMode.OFF, service.getMarkdownMode());

        service.processXsd(false);
        assertEquals(XsdDocumentationService.MarkdownMode.OFF, service.getMarkdownMode());
        assertFalseRendered(service, "MarkdownUnset");
    }

    /** @return true when the element's documentation was run through the Markdown renderer. */
    private static boolean isRendered(XsdDocumentationService service, String name) {
        return element(service, name).getDocumentationAsHtml().contains("<strong>bold</strong>");
    }

    private static void assertFalseRendered(XsdDocumentationService service, String name) {
        String html = element(service, name).getDocumentationAsHtml();
        assertTrue(html.contains("**bold**"), name + " must stay plain text, was: " + html);
        assertFalse(html.contains("<strong>"), name + " must not be rendered, was: " + html);
    }
}
