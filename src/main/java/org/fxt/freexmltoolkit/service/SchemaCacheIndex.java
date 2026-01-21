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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central index for cached schema metadata.
 *
 * <p>This class manages a JSON-based index file that stores comprehensive metadata
 * about all cached XSD schema files. The index is persisted to disk as {@code cache-index.json}
 * in the cache directory.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Thread-safe entry management with ConcurrentHashMap</li>
 *   <li>JSON serialization with Gson</li>
 *   <li>Automatic statistics tracking</li>
 *   <li>Lazy loading and periodic saving</li>
 * </ul>
 */
public class SchemaCacheIndex {

    private static final Logger logger = LogManager.getLogger(SchemaCacheIndex.class);
    private static final String INDEX_VERSION = "1.0";
    private static final Gson gson = createGson();

    /**
     * Global cache index path in the shared cache directory.
     */
    private static final Path GLOBAL_INDEX_PATH = Path.of(
            org.apache.commons.io.FileUtils.getUserDirectory().getAbsolutePath(),
            ".freeXmlToolkit", "cache", "cache-index.json"
    );

    private static volatile SchemaCacheIndex globalInstance;
    private static final Object lock = new Object();

    /**
     * Gets the global cache index instance (singleton).
     * This index tracks all cached schemas regardless of which caching mechanism was used.
     *
     * @return the global cache index
     */
    public static SchemaCacheIndex getGlobalInstance() {
        if (globalInstance == null) {
            synchronized (lock) {
                if (globalInstance == null) {
                    globalInstance = load(GLOBAL_INDEX_PATH);
                }
            }
        }
        return globalInstance;
    }

    /**
     * Saves the global cache index to disk.
     */
    public static void saveGlobalIndex() {
        if (globalInstance != null) {
            synchronized (lock) {
                globalInstance.save(GLOBAL_INDEX_PATH);
            }
        }
    }

    private String version = INDEX_VERSION;
    private Instant lastUpdated;
    private String cacheDirectory;
    private Map<String, SchemaCacheEntry> entries;
    private CacheStatistics statistics;

    /**
     * Default constructor for Gson deserialization.
     */
    public SchemaCacheIndex() {
        this.entries = new ConcurrentHashMap<>();
        this.statistics = new CacheStatistics();
        this.lastUpdated = Instant.now();
    }

    /**
     * Creates a new index for the specified cache directory.
     *
     * @param cacheDirectory the path to the cache directory
     */
    public SchemaCacheIndex(Path cacheDirectory) {
        this();
        this.cacheDirectory = cacheDirectory.toString();
    }

    /**
     * Creates and configures the Gson instance with custom type adapters.
     */
    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .create();
    }

    /**
     * Custom TypeAdapter for java.time.Instant.
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return Instant.parse(in.nextString());
        }
    }

    /**
     * Loads an index from a JSON file.
     *
     * @param indexFile the path to the index file
     * @return the loaded index, or a new empty index if the file doesn't exist
     */
    public static SchemaCacheIndex load(Path indexFile) {
        if (!Files.exists(indexFile)) {
            logger.info("No existing cache index found, creating new one: {}", indexFile);
            SchemaCacheIndex index = new SchemaCacheIndex();
            index.cacheDirectory = indexFile.getParent().toString();
            return index;
        }

        try (FileReader reader = new FileReader(indexFile.toFile())) {
            logger.debug("Loading cache index from: {}", indexFile);
            SchemaCacheIndex index = gson.fromJson(reader, SchemaCacheIndex.class);

            if (index == null) {
                index = new SchemaCacheIndex();
                index.cacheDirectory = indexFile.getParent().toString();
            }

            // Ensure entries map is concurrent
            if (index.entries == null) {
                index.entries = new ConcurrentHashMap<>();
            } else {
                index.entries = new ConcurrentHashMap<>(index.entries);
            }

            if (index.statistics == null) {
                index.statistics = new CacheStatistics();
            }

            logger.info("Loaded cache index with {} entries", index.entries.size());
            return index;
        } catch (IOException e) {
            logger.error("Failed to load cache index, creating new one: {}", e.getMessage());
            SchemaCacheIndex index = new SchemaCacheIndex();
            index.cacheDirectory = indexFile.getParent().toString();
            return index;
        }
    }

    /**
     * Saves the index to a JSON file.
     *
     * @param indexFile the path to save the index to
     */
    public void save(Path indexFile) {
        try (FileWriter writer = new FileWriter(indexFile.toFile())) {
            this.lastUpdated = Instant.now();
            updateStatistics();
            gson.toJson(this, writer);
            logger.debug("Saved cache index with {} entries to: {}", entries.size(), indexFile);
        } catch (IOException e) {
            logger.error("Failed to save cache index: {}", e.getMessage(), e);
        }
    }

    /**
     * Adds or updates an entry in the index.
     *
     * @param entry the entry to add or update
     */
    public void addOrUpdateEntry(SchemaCacheEntry entry) {
        if (entry == null || entry.localFilename() == null) {
            logger.warn("Cannot add null entry or entry without filename");
            return;
        }
        entries.put(entry.localFilename(), entry);
        logger.debug("Added/updated cache entry: {} -> {}", entry.localFilename(), entry.remoteUrl());
    }

    /**
     * Gets an entry by its local filename.
     *
     * @param localFilename the local filename
     * @return the entry, or empty if not found
     */
    public Optional<SchemaCacheEntry> getEntry(String localFilename) {
        return Optional.ofNullable(entries.get(localFilename));
    }

    /**
     * Gets an entry by its remote URL.
     *
     * @param remoteUrl the remote URL
     * @return the entry, or empty if not found
     */
    public Optional<SchemaCacheEntry> getEntryByUrl(String remoteUrl) {
        return entries.values().stream()
                .filter(e -> remoteUrl.equals(e.remoteUrl()))
                .findFirst();
    }

    /**
     * Removes an entry from the index.
     *
     * @param localFilename the local filename of the entry to remove
     */
    public void removeEntry(String localFilename) {
        SchemaCacheEntry removed = entries.remove(localFilename);
        if (removed != null) {
            logger.debug("Removed cache entry: {}", localFilename);
        }
    }

    /**
     * Records an access to a cached schema, incrementing the access count.
     *
     * @param localFilename the local filename of the accessed schema
     */
    public void recordAccess(String localFilename) {
        SchemaCacheEntry entry = entries.get(localFilename);
        if (entry != null) {
            entries.put(localFilename, entry.withAccessRecorded());
        }
    }

    /**
     * Adds a reference from one schema to another.
     *
     * @param localFilename  the local filename of the schema being referenced
     * @param referencingUrl the URL of the schema that references it
     */
    public void addReference(String localFilename, String referencingUrl) {
        SchemaCacheEntry entry = entries.get(localFilename);
        if (entry != null) {
            entries.put(localFilename, entry.withReferencedBy(referencingUrl));
        }
    }

    /**
     * Clears all entries from the index.
     */
    public void clear() {
        entries.clear();
        statistics = new CacheStatistics();
        lastUpdated = Instant.now();
        logger.info("Cleared cache index");
    }

    /**
     * Updates the statistics based on current entries.
     */
    private void updateStatistics() {
        long totalSize = entries.values().stream()
                .mapToLong(SchemaCacheEntry::fileSizeBytes)
                .sum();
        long totalHits = entries.values().stream()
                .filter(e -> e.usage() != null)
                .mapToLong(e -> e.usage().accessCount())
                .sum();

        statistics = new CacheStatistics(
                entries.size(),
                totalSize,
                totalHits,
                statistics.totalCacheMisses(),
                statistics.totalDownloadErrors()
        );
    }

    /**
     * Increments the cache miss counter.
     */
    public void recordCacheMiss() {
        statistics = new CacheStatistics(
                statistics.totalEntries(),
                statistics.totalSizeBytes(),
                statistics.totalCacheHits(),
                statistics.totalCacheMisses() + 1,
                statistics.totalDownloadErrors()
        );
    }

    /**
     * Increments the download error counter.
     */
    public void recordDownloadError() {
        statistics = new CacheStatistics(
                statistics.totalEntries(),
                statistics.totalSizeBytes(),
                statistics.totalCacheHits(),
                statistics.totalCacheMisses(),
                statistics.totalDownloadErrors() + 1
        );
    }

    // Getters and setters for Gson

    /**
     * Returns the version of the cache index format.
     *
     * @return the index format version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the version of the cache index format.
     *
     * @param version the index format version string
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Returns the timestamp when the index was last updated.
     *
     * @return the last update timestamp, or {@code null} if never updated
     */
    public Instant getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets the timestamp when the index was last updated.
     *
     * @param lastUpdated the last update timestamp
     */
    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    /**
     * Returns the path to the cache directory.
     *
     * @return the cache directory path as a string
     */
    public String getCacheDirectory() {
        return cacheDirectory;
    }

    /**
     * Sets the path to the cache directory.
     *
     * @param cacheDirectory the cache directory path as a string
     */
    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    /**
     * Returns a copy of all entries in the index.
     *
     * <p>The returned map is a defensive copy and modifications to it
     * will not affect the internal index.</p>
     *
     * @return a map of local filenames to their corresponding cache entries
     */
    public Map<String, SchemaCacheEntry> getEntries() {
        return new HashMap<>(entries);
    }

    /**
     * Sets the entries in the index.
     *
     * <p>The provided map is copied into a thread-safe {@link ConcurrentHashMap}.</p>
     *
     * @param entries the map of local filenames to cache entries, or {@code null} to clear
     */
    public void setEntries(Map<String, SchemaCacheEntry> entries) {
        this.entries = entries != null ? new ConcurrentHashMap<>(entries) : new ConcurrentHashMap<>();
    }

    /**
     * Returns the current cache statistics.
     *
     * <p>This method updates the statistics before returning them to ensure
     * they reflect the current state of the cache.</p>
     *
     * @return the current cache statistics
     */
    public CacheStatistics getStatistics() {
        updateStatistics();
        return statistics;
    }

    /**
     * Sets the cache statistics.
     *
     * @param statistics the cache statistics, or {@code null} to reset to empty statistics
     */
    public void setStatistics(CacheStatistics statistics) {
        this.statistics = statistics != null ? statistics : new CacheStatistics();
    }

    /**
     * Returns the number of entries in the index.
     *
     * @return the number of cached schema entries
     */
    public int size() {
        return entries.size();
    }

    /**
     * Statistics record for the cache.
     *
     * <p>This immutable record holds cumulative statistics about cache operations,
     * including entries, size, hits, misses, and download errors.</p>
     *
     * @param totalEntries        the total number of cache entries
     * @param totalSizeBytes      the total size of all cached files in bytes
     * @param totalCacheHits      the total number of cache hits (successful lookups)
     * @param totalCacheMisses    the total number of cache misses (failed lookups requiring download)
     * @param totalDownloadErrors the total number of download errors encountered
     */
    public record CacheStatistics(
            long totalEntries,
            long totalSizeBytes,
            long totalCacheHits,
            long totalCacheMisses,
            long totalDownloadErrors
    ) {
        /**
         * Creates a new CacheStatistics instance with all values set to zero.
         *
         * <p>This is the canonical constructor for creating empty statistics.</p>
         */
        public CacheStatistics() {
            this(0, 0, 0, 0, 0);
        }

        /**
         * Returns the total cache size in a human-readable format.
         *
         * <p>The size is automatically formatted with appropriate units:
         * <ul>
         *   <li>Bytes (B) for sizes less than 1 KB</li>
         *   <li>Kilobytes (KB) for sizes less than 1 MB</li>
         *   <li>Megabytes (MB) for larger sizes</li>
         * </ul>
         *
         * @return the formatted size string with unit suffix (e.g., "512 B", "1.5 KB", "10.3 MB")
         */
        public String getTotalSizeFormatted() {
            if (totalSizeBytes < 1024) {
                return totalSizeBytes + " B";
            } else if (totalSizeBytes < 1024 * 1024) {
                return String.format("%.1f KB", totalSizeBytes / 1024.0);
            } else {
                return String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024.0));
            }
        }

        /**
         * Calculates and returns the cache hit ratio as a percentage.
         *
         * <p>The hit ratio is calculated as:
         * {@code (totalCacheHits * 100.0) / (totalCacheHits + totalCacheMisses)}</p>
         *
         * @return the cache hit ratio as a percentage (0.0 to 100.0), or 0.0 if no cache operations have occurred
         */
        public double getHitRatio() {
            long total = totalCacheHits + totalCacheMisses;
            return total > 0 ? (totalCacheHits * 100.0) / total : 0.0;
        }
    }
}
