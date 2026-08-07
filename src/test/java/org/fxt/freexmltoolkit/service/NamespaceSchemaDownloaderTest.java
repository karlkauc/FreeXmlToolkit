package org.fxt.freexmltoolkit.service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NamespaceSchemaDownloader} — resolving import schemas via their
 * namespace URL. All network and cache interactions are mocked; no real HTTP requests.
 */
@ExtendWith(MockitoExtension.class)
class NamespaceSchemaDownloaderTest {

    private static final String XSD_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="http://example.org/ns#">
                <xs:element name="test" type="xs:string"/>
            </xs:schema>
            """;

    private static final String HTML_CONTENT = """
            <!DOCTYPE html>
            <html><head><title>Namespace document</title></head>
            <body>See xmldsig-core-schema.xsd for the XML Schema.</body></html>
            """;

    @Mock
    private ConnectionService connectionService;

    @Mock
    private SchemaResourceCache schemaCache;

    private NamespaceSchemaDownloader downloader() {
        return new NamespaceSchemaDownloader(connectionService, schemaCache);
    }

    @Test
    void nonHttpNamespaceReturnsEmptyWithoutAnyInteraction() {
        Optional<NamespaceSchemaDownloader.ResolvedNamespaceSchema> result =
                downloader().resolve("urn:example:ns", "schema.xsd");

        assertTrue(result.isEmpty());
        verifyNoInteractions(connectionService, schemaCache);
    }

    @Test
    void nullNamespaceReturnsEmpty() {
        assertTrue(downloader().resolve(null, "schema.xsd").isEmpty());
        verifyNoInteractions(connectionService, schemaCache);
    }

    @Test
    void cachedSchemaIsServedOffline(@TempDir Path tempDir) throws Exception {
        Path cached = Files.writeString(tempDir.resolve("cached.xsd"), XSD_CONTENT);
        when(schemaCache.findCachedByTargetNamespace("http://example.org/ns#"))
                .thenReturn(Optional.of(cached));

        Optional<NamespaceSchemaDownloader.ResolvedNamespaceSchema> result =
                downloader().resolve("http://example.org/ns#", "schema.xsd");

        assertTrue(result.isPresent());
        assertEquals(XSD_CONTENT, result.get().content());
        assertEquals(cached, result.get().cachedPath());
        // Offline path: no HTTP request at all
        verifyNoInteractions(connectionService);
    }

    @Test
    void namespaceUrlServingXsdDirectlyIsDownloadedViaCache(@TempDir Path tempDir) throws Exception {
        when(schemaCache.findCachedByTargetNamespace(any())).thenReturn(Optional.empty());
        when(connectionService.executeHttpRequest(URI.create("http://example.org/ns")))
                .thenReturn(new ConnectionResult(
                        URI.create("https://example.org/final/ns.xsd"), 200, 10L,
                        new String[0], XSD_CONTENT));
        Path cached = Files.writeString(tempDir.resolve("dl.xsd"), XSD_CONTENT);
        when(schemaCache.getOrDownload("https://example.org/final/ns.xsd", "http://example.org/ns#"))
                .thenReturn(cached);

        Optional<NamespaceSchemaDownloader.ResolvedNamespaceSchema> result =
                downloader().resolve("http://example.org/ns#", "schema.xsd");

        assertTrue(result.isPresent());
        assertEquals(XSD_CONTENT, result.get().content());
        assertEquals("https://example.org/final/ns.xsd", result.get().sourceUrl());
    }

    @Test
    void htmlNamespaceDocumentResolvesFilenameAgainstFinalRedirectUri(@TempDir Path tempDir) throws Exception {
        // W3C xmldsig pattern: namespace URL redirects to an HTML spec directory;
        // the schema file lives next to that final URL.
        when(schemaCache.findCachedByTargetNamespace(any())).thenReturn(Optional.empty());
        when(connectionService.executeHttpRequest(URI.create("http://www.w3.org/2000/09/xmldsig")))
                .thenReturn(new ConnectionResult(
                        URI.create("https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/"), 200, 10L,
                        new String[0], HTML_CONTENT));
        Path cached = Files.writeString(tempDir.resolve("xmldsig.xsd"), XSD_CONTENT);
        when(schemaCache.getOrDownload(
                "https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd",
                "http://www.w3.org/2000/09/xmldsig#"))
                .thenReturn(cached);

        Optional<NamespaceSchemaDownloader.ResolvedNamespaceSchema> result =
                downloader().resolve("http://www.w3.org/2000/09/xmldsig#", "xmldsig-core-schema.xsd");

        assertTrue(result.isPresent());
        assertEquals(XSD_CONTENT, result.get().content());
        assertEquals(cached, result.get().cachedPath());
        verify(schemaCache).getOrDownload(
                "https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd",
                "http://www.w3.org/2000/09/xmldsig#");
    }

    @Test
    void nonXsdDownloadIsRejected(@TempDir Path tempDir) throws Exception {
        when(schemaCache.findCachedByTargetNamespace(any())).thenReturn(Optional.empty());
        when(connectionService.executeHttpRequest(any()))
                .thenReturn(new ConnectionResult(
                        URI.create("https://example.org/dir/"), 200, 10L,
                        new String[0], HTML_CONTENT));
        // The resolved candidate is also HTML, not a schema
        Path cached = Files.writeString(tempDir.resolve("notaschema.xsd"), HTML_CONTENT);
        when(schemaCache.getOrDownload(any(), any())).thenReturn(cached);

        assertTrue(downloader().resolve("http://example.org/ns", "schema.xsd").isEmpty());
    }

    @Test
    void failedDiscoveryRequestReturnsEmpty() {
        when(schemaCache.findCachedByTargetNamespace(any())).thenReturn(Optional.empty());
        when(connectionService.executeHttpRequest(any()))
                .thenReturn(new ConnectionResult(URI.create("http://example.org/ns"), 404, 10L,
                        new String[0], "not found"));

        assertTrue(downloader().resolve("http://example.org/ns", "schema.xsd").isEmpty());
    }

    @Test
    void looksLikeXsdSchemaAcceptsSchemaDocument() {
        assertTrue(NamespaceSchemaDownloader.looksLikeXsdSchema(XSD_CONTENT));
    }

    @Test
    void looksLikeXsdSchemaRejectsHtmlAndEmptyAndGarbage() {
        assertFalse(NamespaceSchemaDownloader.looksLikeXsdSchema(HTML_CONTENT));
        assertFalse(NamespaceSchemaDownloader.looksLikeXsdSchema(""));
        assertFalse(NamespaceSchemaDownloader.looksLikeXsdSchema(null));
        assertFalse(NamespaceSchemaDownloader.looksLikeXsdSchema("plain text, no XML"));
        assertFalse(NamespaceSchemaDownloader.looksLikeXsdSchema(
                "<schema>no namespace</schema>"));
    }

    @Test
    void stripFragmentRemovesTrailingFragment() {
        assertEquals("http://www.w3.org/2000/09/xmldsig",
                NamespaceSchemaDownloader.stripFragment("http://www.w3.org/2000/09/xmldsig#"));
        assertEquals("http://example.org/ns",
                NamespaceSchemaDownloader.stripFragment("http://example.org/ns"));
    }

    @Test
    void extractFilenameHandlesPathsAndEdgeCases() {
        assertEquals("xmldsig-core-schema.xsd",
                NamespaceSchemaDownloader.extractFilename("xmldsig-core-schema.xsd"));
        assertEquals("b.xsd", NamespaceSchemaDownloader.extractFilename("a/b.xsd"));
        assertEquals("b.xsd", NamespaceSchemaDownloader.extractFilename("..\\dir\\b.xsd"));
        assertNull(NamespaceSchemaDownloader.extractFilename("dir/"));
        assertNull(NamespaceSchemaDownloader.extractFilename(""));
        assertNull(NamespaceSchemaDownloader.extractFilename(null));
    }
}
