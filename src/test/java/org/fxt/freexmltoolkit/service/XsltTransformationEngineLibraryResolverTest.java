package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that Saxon's {@code doc()}/{@code document()} resolution (wired via
 * {@link net.sf.saxon.lib.ResourceResolver} in {@link XsltTransformationEngine}) consults the
 * Schema Library / catalog mappings before falling back to the remote-block behaviour.
 */
class XsltTransformationEngineLibraryResolverTest {

    @AfterEach
    void tearDown() {
        ServiceRegistry.reset();
    }

    @Test
    void docOfCatalogMappedUriIsServedLocally(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data.xml");
        Files.writeString(data, "<d>42</d>");
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<uri name='https://example.org/data.xml' uri='data.xml'/></catalog>");
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addCatalog(cat);
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><xsl:value-of select="doc('https://example.org/data.xml')/d"/></xsl:template>
                </xsl:stylesheet>""";
        XsltTransformationEngine engine = new XsltTransformationEngine();
        var result = engine.transform("<x/>", xslt, Map.of(), XsltTransformationEngine.OutputFormat.TEXT);
        assertTrue(result.isSuccess(), () -> "Transformation failed: " + result.getErrorMessage());
        assertEquals("42", result.getOutputContent().trim());
    }

    @Test
    void unmappedRemoteDocIsStillBlocked(@TempDir Path dir) {
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><xsl:value-of select="doc('https://example.org/other.xml')/d"/></xsl:template>
                </xsl:stylesheet>""";
        XsltTransformationEngine engine = new XsltTransformationEngine();
        var result = engine.quickTransform("<x/>", xslt);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Blocked remote resource"));
    }
}
