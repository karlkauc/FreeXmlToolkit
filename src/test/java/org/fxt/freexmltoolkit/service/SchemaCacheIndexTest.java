/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SchemaCacheIndex.
 */
class SchemaCacheIndexTest {

    @TempDir
    Path tempDir;

    private Path indexFile;
    private SchemaCacheIndex index;

    @BeforeEach
    void setUp() {
        indexFile = tempDir.resolve("cache-index.json");
        index = new SchemaCacheIndex(tempDir);
    }

    @Test
    void testNewIndexIsEmpty() {
        assertEquals(0, index.size());
        assertTrue(index.getEntries().isEmpty());
    }

    @Test
    void testAddEntry() {
        SchemaCacheEntry entry = createTestEntry("test.xsd", "https://example.com/test.xsd");

        index.addOrUpdateEntry(entry);

        assertEquals(1, index.size());
        assertTrue(index.getEntry("test.xsd").isPresent());
        assertEquals("https://example.com/test.xsd", index.getEntry("test.xsd").get().remoteUrl());
    }

    @Test
    void testGetEntryByUrl() {
        SchemaCacheEntry entry = createTestEntry("test.xsd", "https://example.com/test.xsd");
        index.addOrUpdateEntry(entry);

        Optional<SchemaCacheEntry> found = index.getEntryByUrl("https://example.com/test.xsd");

        assertTrue(found.isPresent());
        assertEquals("test.xsd", found.get().localFilename());
    }

    @Test
    void testGetEntryByUrl_NotFound() {
        Optional<SchemaCacheEntry> found = index.getEntryByUrl("https://example.com/nonexistent.xsd");
        assertTrue(found.isEmpty());
    }

    @Test
    void testRemoveEntry() {
        SchemaCacheEntry entry = createTestEntry("test.xsd", "https://example.com/test.xsd");
        index.addOrUpdateEntry(entry);

        index.removeEntry("test.xsd");

        assertEquals(0, index.size());
        assertTrue(index.getEntry("test.xsd").isEmpty());
    }

    @Test
    void testRecordAccess() {
        SchemaCacheEntry entry = createTestEntry("test.xsd", "https://example.com/test.xsd");
        index.addOrUpdateEntry(entry);

        long initialCount = index.getEntry("test.xsd").get().usage().accessCount();

        index.recordAccess("test.xsd");

        long newCount = index.getEntry("test.xsd").get().usage().accessCount();
        assertEquals(initialCount + 1, newCount);
    }

    @Test
    void testAddReference() {
        SchemaCacheEntry entry = createTestEntry("child.xsd", "https://example.com/child.xsd");
        index.addOrUpdateEntry(entry);

        index.addReference("child.xsd", "https://example.com/parent.xsd");

        List<String> references = index.getEntry("child.xsd").get().usage().referencedBy();
        assertTrue(references.contains("https://example.com/parent.xsd"));
    }

    @Test
    void testAddReference_NoDuplicates() {
        SchemaCacheEntry entry = createTestEntry("child.xsd", "https://example.com/child.xsd");
        index.addOrUpdateEntry(entry);

        index.addReference("child.xsd", "https://example.com/parent.xsd");
        index.addReference("child.xsd", "https://example.com/parent.xsd"); // Same reference again

        List<String> references = index.getEntry("child.xsd").get().usage().referencedBy();
        assertEquals(1, references.size());
    }

    @Test
    void testClear() {
        index.addOrUpdateEntry(createTestEntry("a.xsd", "https://example.com/a.xsd"));
        index.addOrUpdateEntry(createTestEntry("b.xsd", "https://example.com/b.xsd"));

        index.clear();

        assertEquals(0, index.size());
    }

    @Test
    void testSaveAndLoad() throws Exception {
        SchemaCacheEntry entry1 = createTestEntry("schema1.xsd", "https://example.com/schema1.xsd");
        SchemaCacheEntry entry2 = createTestEntry("schema2.xsd", "https://example.com/schema2.xsd");

        index.addOrUpdateEntry(entry1);
        index.addOrUpdateEntry(entry2);
        index.save(indexFile);

        assertTrue(Files.exists(indexFile));

        // Load into a new index
        SchemaCacheIndex loadedIndex = SchemaCacheIndex.load(indexFile);

        assertEquals(2, loadedIndex.size());
        assertTrue(loadedIndex.getEntry("schema1.xsd").isPresent());
        assertTrue(loadedIndex.getEntry("schema2.xsd").isPresent());
        assertEquals("https://example.com/schema1.xsd", loadedIndex.getEntry("schema1.xsd").get().remoteUrl());
    }

    @Test
    void testLoadNonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.json");

        SchemaCacheIndex loadedIndex = SchemaCacheIndex.load(nonExistent);

        assertNotNull(loadedIndex);
        assertEquals(0, loadedIndex.size());
    }

    @Test
    void testStatistics() {
        index.addOrUpdateEntry(createTestEntryWithSize("a.xsd", "https://a.xsd", 1000));
        index.addOrUpdateEntry(createTestEntryWithSize("b.xsd", "https://b.xsd", 2000));

        index.recordCacheMiss();
        index.recordCacheMiss();
        index.recordDownloadError();

        SchemaCacheIndex.CacheStatistics stats = index.getStatistics();

        assertEquals(2, stats.totalEntries());
        assertEquals(3000, stats.totalSizeBytes());
        assertEquals(2, stats.totalCacheMisses());
        assertEquals(1, stats.totalDownloadErrors());
    }

    @Test
    void testStatistics_HitRatio() {
        SchemaCacheIndex.CacheStatistics stats = new SchemaCacheIndex.CacheStatistics(
                10, 5000, 80, 20, 0
        );

        assertEquals(80.0, stats.getHitRatio(), 0.01);
    }

    @Test
    void testStatistics_HitRatioZero() {
        SchemaCacheIndex.CacheStatistics stats = new SchemaCacheIndex.CacheStatistics(
                0, 0, 0, 0, 0
        );

        assertEquals(0.0, stats.getHitRatio(), 0.01);
    }

    @Test
    void testStatistics_SizeFormatted() {
        assertEquals("500 B", new SchemaCacheIndex.CacheStatistics(0, 500, 0, 0, 0).getTotalSizeFormatted());
        // Use contains instead of equals to handle locale differences (1.5 vs 1,5)
        String kbFormatted = new SchemaCacheIndex.CacheStatistics(0, 1536, 0, 0, 0).getTotalSizeFormatted();
        assertTrue(kbFormatted.contains("KB") && kbFormatted.contains("1"));
        String mbFormatted = new SchemaCacheIndex.CacheStatistics(0, 2097152, 0, 0, 0).getTotalSizeFormatted();
        assertTrue(mbFormatted.contains("MB") && mbFormatted.contains("2"));
    }

    @Test
    void testEntryWithHttpInfo() {
        SchemaCacheEntry.HttpInfo httpInfo = new SchemaCacheEntry.HttpInfo(
                200,
                "application/xml",
                "Mon, 01 Jan 2024 00:00:00 GMT",
                "\"abc123\"",
                12345L,
                150
        );

        SchemaCacheEntry entry = SchemaCacheEntry.builder()
                .localFilename("test.xsd")
                .remoteUrl("https://example.com/test.xsd")
                .downloadTimestamp(Instant.now())
                .fileSizeBytes(12345)
                .md5Hash("abc123")
                .http(httpInfo)
                .build();

        index.addOrUpdateEntry(entry);
        index.save(indexFile);

        SchemaCacheIndex loaded = SchemaCacheIndex.load(indexFile);
        SchemaCacheEntry loadedEntry = loaded.getEntry("test.xsd").orElseThrow();

        assertNotNull(loadedEntry.http());
        assertEquals(200, loadedEntry.http().statusCode());
        assertEquals("application/xml", loadedEntry.http().contentType());
        assertEquals("\"abc123\"", loadedEntry.http().etag());
    }

    @Test
    void testEntryWithSchemaInfo() {
        SchemaCacheEntry.SchemaInfo schemaInfo = new SchemaCacheEntry.SchemaInfo(
                "http://example.com/schema",
                "1.1",
                List.of("https://www.w3.org/2001/XMLSchema"),
                List.of("types.xsd"),
                List.of()
        );

        SchemaCacheEntry entry = SchemaCacheEntry.builder()
                .localFilename("test.xsd")
                .remoteUrl("https://example.com/test.xsd")
                .downloadTimestamp(Instant.now())
                .fileSizeBytes(5000)
                .schema(schemaInfo)
                .build();

        index.addOrUpdateEntry(entry);
        index.save(indexFile);

        SchemaCacheIndex loaded = SchemaCacheIndex.load(indexFile);
        SchemaCacheEntry loadedEntry = loaded.getEntry("test.xsd").orElseThrow();

        assertNotNull(loadedEntry.schema());
        assertEquals("http://example.com/schema", loadedEntry.schema().targetNamespace());
        assertEquals("1.1", loadedEntry.schema().xsdVersion());
        assertEquals(1, loadedEntry.schema().imports().size());
        assertEquals(1, loadedEntry.schema().includes().size());
    }

    @Test
    void testWithAccessRecorded() {
        SchemaCacheEntry entry = createTestEntry("test.xsd", "https://example.com/test.xsd");
        long initialCount = entry.usage().accessCount();

        SchemaCacheEntry updated = entry.withAccessRecorded();

        assertEquals(initialCount + 1, updated.usage().accessCount());
        assertNotEquals(entry.usage().lastAccessTimestamp(), updated.usage().lastAccessTimestamp());
    }

    @Test
    void testWithReferencedBy() {
        SchemaCacheEntry entry = createTestEntry("test.xsd", "https://example.com/test.xsd");

        SchemaCacheEntry updated = entry.withReferencedBy("https://parent.xsd");

        assertTrue(updated.usage().referencedBy().contains("https://parent.xsd"));
    }

    // Helper methods

    private SchemaCacheEntry createTestEntry(String filename, String url) {
        return SchemaCacheEntry.builder()
                .localFilename(filename)
                .remoteUrl(url)
                .downloadTimestamp(Instant.now())
                .fileSizeBytes(1000)
                .md5Hash("d41d8cd98f00b204e9800998ecf8427e")
                .sha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .schema(SchemaCacheEntry.SchemaInfo.empty())
                .usage(SchemaCacheEntry.UsageInfo.initial())
                .build();
    }

    private SchemaCacheEntry createTestEntryWithSize(String filename, String url, long size) {
        return SchemaCacheEntry.builder()
                .localFilename(filename)
                .remoteUrl(url)
                .downloadTimestamp(Instant.now())
                .fileSizeBytes(size)
                .md5Hash("abc123")
                .usage(SchemaCacheEntry.UsageInfo.initial())
                .build();
    }
}
