package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for SchemaResourceCache service.
 * Tests cache statistics, SSRF protection, and CacheStats record.
 * Note: Actual download tests are not included to avoid network dependency.
 */
class SchemaResourceCacheTest {

    private SchemaResourceCache cache;

    @BeforeEach
    void setUp() {
        cache = new SchemaResourceCache();
    }

    // =========================================================================
    // CacheStats Record Tests
    // =========================================================================

    @Nested
    @DisplayName("CacheStats Record")
    class CacheStatsRecordTests {

        @Test
        @DisplayName("CacheStats stores values correctly")
        void storesValues() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(10, 5, 2, 8, 1024 * 1024);
            assertEquals(10, stats.cacheHits());
            assertEquals(5, stats.cacheMisses());
            assertEquals(2, stats.downloadErrors());
            assertEquals(8, stats.totalFiles());
            assertEquals(1024 * 1024, stats.totalSizeBytes());
        }

        @Test
        @DisplayName("Hit ratio with no accesses returns 0")
        void hitRatioNoAccesses() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(0, 0, 0, 0, 0);
            assertEquals(0.0, stats.getHitRatio());
        }

        @Test
        @DisplayName("Hit ratio calculated correctly")
        void hitRatioCalculation() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(75, 25, 0, 0, 0);
            assertEquals(75.0, stats.getHitRatio(), 0.001);
        }

        @Test
        @DisplayName("Hit ratio 100% for all hits")
        void hitRatioAllHits() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(100, 0, 0, 0, 0);
            assertEquals(100.0, stats.getHitRatio(), 0.001);
        }

        @Test
        @DisplayName("Format size in bytes")
        void formatSizeBytes() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(0, 0, 0, 0, 512);
            assertEquals("512 B", stats.getTotalSizeFormatted());
        }

        @Test
        @DisplayName("Format size in KB")
        void formatSizeKB() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(0, 0, 0, 0, 2048);
            assertEquals("2.0 KB", stats.getTotalSizeFormatted());
        }

        @Test
        @DisplayName("Format size in MB")
        void formatSizeMB() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(0, 0, 0, 0, 3 * 1024 * 1024);
            assertEquals("3.0 MB", stats.getTotalSizeFormatted());
        }

        @Test
        @DisplayName("Format size 0 bytes")
        void formatSizeZero() {
            SchemaResourceCache.CacheStats stats = new SchemaResourceCache.CacheStats(0, 0, 0, 0, 0);
            assertEquals("0 B", stats.getTotalSizeFormatted());
        }

        @Test
        @DisplayName("Record equality")
        void recordEquality() {
            SchemaResourceCache.CacheStats s1 = new SchemaResourceCache.CacheStats(1, 2, 3, 4, 5);
            SchemaResourceCache.CacheStats s2 = new SchemaResourceCache.CacheStats(1, 2, 3, 4, 5);
            assertEquals(s1, s2);
            assertEquals(s1.hashCode(), s2.hashCode());
        }
    }

    // =========================================================================
    // Cache Instance Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Instance")
    class CacheInstanceTests {

        @Test
        @DisplayName("Cache directory is not null")
        void cacheDirectoryNotNull() {
            assertNotNull(cache.getCacheDirectory());
        }

        @Test
        @DisplayName("Cache directory path contains expected structure")
        void cacheDirectoryPath() {
            String path = cache.getCacheDirectory().toString();
            assertTrue(path.contains(".freeXmlToolkit"));
            assertTrue(path.contains("cache"));
            assertTrue(path.contains("schemas"));
        }

        @Test
        @DisplayName("Cache index is not null")
        void cacheIndexNotNull() {
            assertNotNull(cache.getCacheIndex());
        }

        @Test
        @DisplayName("Initial stats have zero errors")
        void initialStatsZeroErrors() {
            SchemaResourceCache.CacheStats stats = cache.getStats();
            assertNotNull(stats);
            assertEquals(0, stats.downloadErrors());
        }
    }

    // =========================================================================
    // SSRF Protection Tests
    // =========================================================================

    @Nested
    @DisplayName("SSRF Protection")
    class SSRFProtectionTests {

        @Test
        @DisplayName("Blocks localhost URLs")
        void blocksLocalhost() {
            assertThrows(IOException.class,
                    () -> cache.getOrDownload("http://localhost/schema.xsd"));
        }

        @Test
        @DisplayName("Blocks 127.0.0.1 URLs")
        void blocksLoopback() {
            assertThrows(IOException.class,
                    () -> cache.getOrDownload("http://127.0.0.1/schema.xsd"));
        }

        @Test
        @DisplayName("Blocks private network URLs (10.x.x.x)")
        void blocksPrivateNetwork10() {
            assertThrows(IOException.class,
                    () -> cache.getOrDownload("http://10.0.0.1/schema.xsd"));
        }

        @Test
        @DisplayName("Blocks private network URLs (192.168.x.x)")
        void blocksPrivateNetwork192() {
            assertThrows(IOException.class,
                    () -> cache.getOrDownload("http://192.168.1.1/schema.xsd"));
        }

        @Test
        @DisplayName("Blocks cloud metadata endpoint")
        void blocksMetadata() {
            assertThrows(IOException.class,
                    () -> cache.getOrDownload("http://169.254.169.254/latest/meta-data/"));
        }

        @Test
        @DisplayName("SSRF error message mentions security")
        void ssrfErrorMessage() {
            IOException ex = assertThrows(IOException.class,
                    () -> cache.getOrDownload("http://localhost/schema.xsd"));
            assertTrue(ex.getMessage().toLowerCase().contains("security") ||
                    ex.getMessage().toLowerCase().contains("internal"));
        }
    }

    // =========================================================================
    // isCached Tests
    // =========================================================================

    @Nested
    @DisplayName("isCached")
    class IsCachedTests {

        @Test
        @DisplayName("Returns false for uncached URL")
        void uncachedUrl() {
            assertFalse(cache.isCached("http://nonexistent.example.com/schema.xsd"));
        }
    }

    // =========================================================================
    // Cache Stats Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Statistics")
    class CacheStatisticsTests {

        @Test
        @DisplayName("getStats returns valid stats")
        void validStats() {
            SchemaResourceCache.CacheStats stats = cache.getStats();
            assertNotNull(stats);
            assertTrue(stats.totalFiles() >= 0);
            assertTrue(stats.totalSizeBytes() >= 0);
        }
    }

    // =========================================================================
    // Save Index Tests
    // =========================================================================

    @Nested
    @DisplayName("Save Index")
    class SaveIndexTests {

        @Test
        @DisplayName("Save index does not throw")
        void saveIndexDoesNotThrow() {
            assertDoesNotThrow(() -> cache.saveIndex());
        }
    }

    // =========================================================================
    // Entry Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Entry management")
    class EntryManagement {

        @Test
        void listsAndRemovesEntries(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
            SchemaResourceCache c = new SchemaResourceCache(dir);
            java.nio.file.Path f = dir.resolve("abc.xsd");
            java.nio.file.Files.writeString(f, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'/>");
            c.getCacheIndex().addOrUpdateEntry(SchemaCacheEntry.builder()
                    .localFilename("abc.xsd").remoteUrl("https://example.org/abc.xsd")
                    .downloadTimestamp(java.time.Instant.now()).fileSizeBytes(10).build());
            c.saveIndex();

            assertEquals(1, c.listEntries().size());
            assertEquals(f, c.pathOf(c.listEntries().getFirst()));
            assertTrue(c.entryForUrl("https://example.org/abc.xsd").isPresent());

            assertTrue(c.removeEntry("abc.xsd"));
            assertFalse(java.nio.file.Files.exists(f));
            assertTrue(c.listEntries().isEmpty());
            assertFalse(c.removeEntry("abc.xsd"));
        }

        @Test
        void separateDirectoriesAreIsolated(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            SchemaResourceCache c = new SchemaResourceCache(dir.resolve("sub"));
            assertEquals(dir.resolve("sub"), c.getCacheDirectory());
            assertTrue(c.listEntries().isEmpty());
        }

        @Test
        void downloadFailureMessageNamesTheRootCause(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            var ex = org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                    () -> new SchemaResourceCache(dir).getOrDownload("https://schema.invalid/x.xsd"));
            assertTrue(ex.getMessage().contains("schema.invalid"), ex.getMessage());
            assertTrue(ex.getMessage().contains("UnknownHostException") || ex.getMessage().contains("ConnectException"),
                    "cause must be visible in the message: " + ex.getMessage());
        }

        @Test
        void refreshOfUnsafeUrlIsEmpty(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            assertTrue(new SchemaResourceCache(dir).refresh("http://127.0.0.1/x.xsd").isEmpty());
        }

        /**
         * A failed refresh must be non-destructive: the previously cached file and its index
         * entry survive. {@code .invalid} is reserved (RFC 2606), so the download fails
         * immediately without real network access.
         */
        @Test
        void failedRefreshKeepsTheCachedCopy(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
            SchemaResourceCache c = new SchemaResourceCache(dir);
            String url = "https://schema.invalid/keep.xsd";
            java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
            String name = java.util.HexFormat.of().formatHex(
                    md5.digest(url.getBytes(java.nio.charset.StandardCharsets.UTF_8))) + ".xsd";
            java.nio.file.Path cached = c.getCacheDirectory().resolve(name);
            java.nio.file.Files.writeString(cached, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
            c.getCacheIndex().addOrUpdateEntry(SchemaCacheEntry.builder()
                    .localFilename(name).remoteUrl(url)
                    .downloadTimestamp(java.time.Instant.now()).fileSizeBytes(50).build());
            c.saveIndex();
            assertTrue(c.isCached(url), "precondition: the URL is cached");

            assertTrue(c.refresh(url).isEmpty(), "refresh of an unreachable URL must fail");

            assertTrue(java.nio.file.Files.exists(cached), "the cached file must survive a failed refresh");
            assertTrue(c.entryForUrl(url).isPresent(), "the index entry must survive a failed refresh");
            assertTrue(c.isCached(url));
        }
    }

    // =========================================================================
    // Download via ConnectionService (proxy credentials, HTTP 407)
    // =========================================================================

    @Nested
    @DisplayName("Download via ConnectionService")
    class DownloadViaConnectionService {

        private static final String XSD_BODY =
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'/>";

        /** Stub ConnectionService that returns a fixed BinaryResponse or throws a fixed IOException. */
        private static final class StubConnectionService implements ConnectionService {
            private final BinaryResponse response;
            private final IOException failure;

            StubConnectionService(BinaryResponse response) {
                this.response = response;
                this.failure = null;
            }

            StubConnectionService(IOException failure) {
                this.response = null;
                this.failure = failure;
            }

            @Override
            public BinaryResponse fetchBinary(URI uri) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                return response;
            }

            @Override
            public java.net.Proxy resolveProxy() {
                return null;
            }

            @Override
            public org.fxt.freexmltoolkit.domain.ConnectionResult executeHttpRequest(URI url) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public String getTextContentFromURL(URI uri) {
                throw new UnsupportedOperationException("not used by this test");
            }

            @Override
            public org.fxt.freexmltoolkit.domain.ConnectionResult testHttpRequest(URI url, java.util.Properties testProperties) {
                throw new UnsupportedOperationException("not used by this test");
            }
        }

        @Test
        @DisplayName("200 response writes bytes unchanged and records schema/http metadata")
        void successfulDownloadWritesBytesAndMetadata(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
            byte[] body = XSD_BODY.getBytes(StandardCharsets.UTF_8);
            ConnectionService.BinaryResponse response = new ConnectionService.BinaryResponse(
                    URI.create("https://example.org/t.xsd"), 200,
                    Map.of("Content-Type", "application/xml"), body);
            StubConnectionService stub = new StubConnectionService(response);
            SchemaResourceCache cache = new SchemaResourceCache(dir, stub);

            java.nio.file.Path localPath = cache.getOrDownload("https://example.org/t.xsd");

            assertArrayEquals(body, java.nio.file.Files.readAllBytes(localPath), "cached bytes must match the response body unchanged");

            var entry = cache.entryForUrl("https://example.org/t.xsd");
            assertTrue(entry.isPresent(), "an index entry must be recorded");
            assertEquals("urn:t", entry.get().schema().targetNamespace());
            assertEquals("application/xml", entry.get().http().contentType());
        }

        @Test
        @DisplayName("HTTP 407 (proxy authentication required) surfaces in the thrown IOException")
        void proxyAuthenticationRequiredSurfacesInMessage(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            ConnectionService.BinaryResponse response = new ConnectionService.BinaryResponse(
                    URI.create("https://example.org/t.xsd"), 407, Map.of(), new byte[0]);
            StubConnectionService stub = new StubConnectionService(response);
            SchemaResourceCache cache = new SchemaResourceCache(dir, stub);

            IOException ex = assertThrows(IOException.class, () -> cache.getOrDownload("https://example.org/t.xsd"));
            assertTrue(ex.getMessage().contains("HTTP 407"), ex.getMessage());
        }

        @Test
        @DisplayName("transport failure from the ConnectionService (describeCause path) surfaces in the message")
        void transportFailureSurfacesInMessage(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            StubConnectionService stub = new StubConnectionService(new IOException("Proxy Authentication Required"));
            SchemaResourceCache cache = new SchemaResourceCache(dir, stub);

            IOException ex = assertThrows(IOException.class, () -> cache.getOrDownload("https://example.org/t.xsd"));
            assertTrue(ex.getMessage().contains("Proxy Authentication Required"), ex.getMessage());
        }
    }
}
