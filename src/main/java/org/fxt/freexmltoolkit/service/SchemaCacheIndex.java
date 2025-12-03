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

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public Map<String, SchemaCacheEntry> getEntries() {
        return new HashMap<>(entries);
    }

    public void setEntries(Map<String, SchemaCacheEntry> entries) {
        this.entries = entries != null ? new ConcurrentHashMap<>(entries) : new ConcurrentHashMap<>();
    }

    public CacheStatistics getStatistics() {
        updateStatistics();
        return statistics;
    }

    public void setStatistics(CacheStatistics statistics) {
        this.statistics = statistics != null ? statistics : new CacheStatistics();
    }

    /**
     * Returns the number of entries in the index.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Statistics record for the cache.
     */
    public record CacheStatistics(
            long totalEntries,
            long totalSizeBytes,
            long totalCacheHits,
            long totalCacheMisses,
            long totalDownloadErrors
    ) {
        public CacheStatistics() {
            this(0, 0, 0, 0, 0);
        }

        /**
         * Returns the total size in a human-readable format.
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
         * Returns the cache hit ratio as a percentage.
         */
        public double getHitRatio() {
            long total = totalCacheHits + totalCacheMisses;
            return total > 0 ? (totalCacheHits * 100.0) / total : 0.0;
        }
    }
}
