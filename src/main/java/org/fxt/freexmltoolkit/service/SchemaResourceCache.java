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

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache service for remote XSD schema files.
 *
 * <p>This service downloads and caches remote schema files (HTTP/HTTPS) to avoid
 * repeated network requests. Cached schemas are stored in the user's home directory
 * under {@code ~/.freeXmlToolkit/cache/schemas/}.</p>
 *
 * <p>Features:
 * <ul>
 *   <li>Thread-safe caching with ConcurrentHashMap</li>
 *   <li>MD5-based filename generation for URL uniqueness</li>
 *   <li>HTTP/HTTPS support with configurable timeout</li>
 *   <li>Cache statistics tracking</li>
 *   <li>Manual cache clearing</li>
 * </ul>
 * </p>
 */
public class SchemaResourceCache {

    private static final Logger logger = LogManager.getLogger(SchemaResourceCache.class);

    private static final Path CACHE_DIR = Path.of(
            FileUtils.getUserDirectory().getAbsolutePath(),
            ".freeXmlToolkit", "cache", "schemas"
    );

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Path> urlToLocalPath = new ConcurrentHashMap<>();
    private final HttpClient httpClient;

    // Statistics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong downloadErrors = new AtomicLong(0);

    /**
     * Creates a new schema resource cache instance.
     */
    public SchemaResourceCache() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // Ensure cache directory exists
        try {
            if (!Files.exists(CACHE_DIR)) {
                Files.createDirectories(CACHE_DIR);
                logger.info("Created schema cache directory: {}", CACHE_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create schema cache directory: {}", CACHE_DIR, e);
        }

        // Load existing cached files into memory map
        loadExistingCache();
    }

    /**
     * Gets or downloads a remote schema file.
     *
     * <p>If the schema is already cached, returns the cached path immediately.
     * Otherwise, downloads the schema from the remote URL and caches it locally.</p>
     *
     * @param url the remote URL of the schema file
     * @return the local path to the cached schema file
     * @throws IOException if the download fails or the file cannot be written
     */
    public Path getOrDownload(String url) throws IOException {
        // Check in-memory cache first
        Path cachedPath = urlToLocalPath.get(url);
        if (cachedPath != null && Files.exists(cachedPath)) {
            cacheHits.incrementAndGet();
            logger.debug("Cache hit for schema: {}", url);
            return cachedPath;
        }

        // Check if file exists on disk (might have been cached in previous session)
        String filename = generateFilename(url);
        Path localPath = CACHE_DIR.resolve(filename);
        if (Files.exists(localPath)) {
            urlToLocalPath.put(url, localPath);
            cacheHits.incrementAndGet();
            logger.debug("Cache hit (disk) for schema: {}", url);
            return localPath;
        }

        // Download and cache
        cacheMisses.incrementAndGet();
        logger.info("Downloading remote schema: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                downloadErrors.incrementAndGet();
                throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
            }

            // Write to cache file
            try (InputStream inputStream = response.body()) {
                Files.copy(inputStream, localPath);
            }

            urlToLocalPath.put(url, localPath);
            logger.info("Cached remote schema: {} -> {}", url, localPath);
            return localPath;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            downloadErrors.incrementAndGet();
            throw new IOException("Download interrupted for URL: " + url, e);
        } catch (Exception e) {
            downloadErrors.incrementAndGet();
            throw new IOException("Failed to download schema from URL: " + url, e);
        }
    }

    /**
     * Checks if a URL is already cached (either in memory or on disk).
     *
     * @param url the URL to check
     * @return true if cached, false otherwise
     */
    public boolean isCached(String url) {
        Path cachedPath = urlToLocalPath.get(url);
        if (cachedPath != null && Files.exists(cachedPath)) {
            return true;
        }

        String filename = generateFilename(url);
        Path localPath = CACHE_DIR.resolve(filename);
        return Files.exists(localPath);
    }

    /**
     * Clears all cached schemas from disk and memory.
     *
     * @return the number of files deleted
     */
    public int clearCache() {
        int deleted = 0;

        try {
            if (Files.exists(CACHE_DIR)) {
                File[] files = CACHE_DIR.toFile().listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.delete()) {
                            deleted++;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error clearing schema cache", e);
        }

        urlToLocalPath.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        downloadErrors.set(0);

        logger.info("Cleared schema cache: {} files deleted", deleted);
        return deleted;
    }

    /**
     * Returns cache statistics.
     *
     * @return cache statistics object
     */
    public CacheStats getStats() {
        long totalFiles = 0;
        long totalSize = 0;

        try {
            if (Files.exists(CACHE_DIR)) {
                File[] files = CACHE_DIR.toFile().listFiles();
                if (files != null) {
                    totalFiles = files.length;
                    for (File file : files) {
                        totalSize += file.length();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error calculating cache stats", e);
        }

        return new CacheStats(
                cacheHits.get(),
                cacheMisses.get(),
                downloadErrors.get(),
                totalFiles,
                totalSize
        );
    }

    /**
     * Gets the cache directory path.
     *
     * @return the cache directory path
     */
    public Path getCacheDirectory() {
        return CACHE_DIR;
    }

    /**
     * Generates a unique filename for a URL using MD5 hash.
     * Preserves the original file extension.
     */
    private String generateFilename(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(url.getBytes());
            String hashString = HexFormat.of().formatHex(hash);

            // Extract extension from URL
            String extension = ".xsd";
            int lastDot = url.lastIndexOf('.');
            int lastSlash = url.lastIndexOf('/');
            if (lastDot > lastSlash && lastDot < url.length() - 1) {
                String ext = url.substring(lastDot);
                // Only use common schema extensions
                if (ext.equals(".xsd") || ext.equals(".xml") || ext.equals(".dtd")) {
                    extension = ext;
                }
            }

            return hashString + extension;
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash
            return Integer.toHexString(url.hashCode()) + ".xsd";
        }
    }

    /**
     * Loads existing cached files into the in-memory map.
     */
    private void loadExistingCache() {
        try {
            if (Files.exists(CACHE_DIR)) {
                logger.debug("Loading existing schema cache from: {}", CACHE_DIR);
                // Files are loaded on-demand, no need to pre-populate
            }
        } catch (Exception e) {
            logger.warn("Error loading existing cache", e);
        }
    }

    /**
     * Cache statistics record.
     */
    public record CacheStats(
            long cacheHits,
            long cacheMisses,
            long downloadErrors,
            long totalFiles,
            long totalSizeBytes
    ) {
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
            long total = cacheHits + cacheMisses;
            return total > 0 ? (cacheHits * 100.0) / total : 0.0;
        }
    }
}
