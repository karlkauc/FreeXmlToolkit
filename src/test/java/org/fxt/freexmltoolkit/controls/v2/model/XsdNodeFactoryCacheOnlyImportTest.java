package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.NamespaceSchemaDownloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Import resolution with downloads disabled ({@link XsdNodeFactory#setRemoteDownloadsAllowed}):
 * the mode used for parsing on the UI thread. Remote imports must come from the local cache
 * only, and everything that would need the network is reported instead of fetched.
 */
class XsdNodeFactoryCacheOnlyImportTest {

    private static final String NS_MAIN = "https://example.com/main";
    private static final String NS_REMOTE = "https://example.com/remote";

    private static String schema(String targetNamespace, String body) {
        return "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\""
                + targetNamespace + "\">\n" + body + "\n</xs:schema>\n";
    }

    private static String importOf(String namespace, String location) {
        return "    <xs:import namespace=\"" + namespace + "\" schemaLocation=\"" + location + "\"/>";
    }

    @Test
    void namespaceFallbackIsDeferredInsteadOfDownloaded(@TempDir Path tempDir) throws Exception {
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"),
                schema(NS_MAIN, importOf(NS_REMOTE, "does-not-exist.xsd")));

        NamespaceSchemaDownloader downloader = mock(NamespaceSchemaDownloader.class);
        when(downloader.resolveFromCache(NS_REMOTE)).thenReturn(Optional.empty());

        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        factory.setNamespaceSchemaDownloader(downloader);
        factory.setRemoteDownloadsAllowed(false);
        XsdSchema schema = factory.fromFile(mainFile);

        verify(downloader, never()).resolve(any(), any());
        assertFalse(schema.getImportedSchemas().containsKey(NS_REMOTE));
        assertEquals(java.util.Set.of(NS_REMOTE), factory.getDeferredRemoteLookups());
    }

    @Test
    void namespaceFallbackResolvesFromCacheWithoutDownload(@TempDir Path tempDir) throws Exception {
        String remoteXsd = schema(NS_REMOTE,
                "    <xs:simpleType name=\"RType\"><xs:restriction base=\"xs:string\"/></xs:simpleType>");
        Path cachedFile = Files.writeString(tempDir.resolve("cached-remote.xsd"), remoteXsd);
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"),
                schema(NS_MAIN, importOf(NS_REMOTE, "does-not-exist.xsd")));

        NamespaceSchemaDownloader downloader = mock(NamespaceSchemaDownloader.class);
        when(downloader.resolveFromCache(NS_REMOTE))
                .thenReturn(Optional.of(new NamespaceSchemaDownloader.ResolvedNamespaceSchema(
                        remoteXsd, cachedFile, "cache:" + cachedFile)));

        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        factory.setNamespaceSchemaDownloader(downloader);
        factory.setRemoteDownloadsAllowed(false);
        XsdSchema schema = factory.fromFile(mainFile);

        verify(downloader, never()).resolve(any(), any());
        assertTrue(schema.getImportedSchemas().containsKey(NS_REMOTE));
        assertTrue(factory.getDeferredRemoteLookups().isEmpty());
    }

    @Test
    void uncachedHttpSchemaLocationIsDeferred(@TempDir Path tempDir) throws Exception {
        // Never cached: the name is unique per run, and .invalid is reserved and never resolves.
        String url = "https://cache-only-" + System.nanoTime() + ".invalid/remote.xsd";
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"),
                schema(NS_MAIN, importOf(NS_REMOTE, url)));

        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(false);
        factory.setRemoteDownloadsAllowed(false);
        XsdSchema schema = factory.fromFile(mainFile);

        assertFalse(schema.getImportedSchemas().containsKey(NS_REMOTE));
        assertEquals(java.util.Set.of(url), factory.getDeferredRemoteLookups());
    }

    @Test
    void downloadsStayAllowedByDefault(@TempDir Path tempDir) throws Exception {
        Path mainFile = Files.writeString(tempDir.resolve("main.xsd"),
                schema(NS_MAIN, importOf(NS_REMOTE, "does-not-exist.xsd")));

        NamespaceSchemaDownloader downloader = mock(NamespaceSchemaDownloader.class);
        when(downloader.resolve(NS_REMOTE, "does-not-exist.xsd")).thenReturn(Optional.empty());

        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(true);
        factory.setNamespaceSchemaDownloader(downloader);
        factory.fromFile(mainFile);

        verify(downloader).resolve(NS_REMOTE, "does-not-exist.xsd");
        assertTrue(factory.getDeferredRemoteLookups().isEmpty());
    }
}
