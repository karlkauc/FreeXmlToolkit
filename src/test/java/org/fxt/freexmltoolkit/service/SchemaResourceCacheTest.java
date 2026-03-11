package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

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
}
