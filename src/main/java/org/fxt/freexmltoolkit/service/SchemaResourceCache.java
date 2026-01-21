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
import org.fxt.freexmltoolkit.util.PathValidator;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
 *   <li>Persistent metadata index (cache-index.json)</li>
 * </ul>
 */
public class SchemaResourceCache {

    private static final Logger logger = LogManager.getLogger(SchemaResourceCache.class);

    private static final Path CACHE_DIR = Path.of(
            FileUtils.getUserDirectory().getAbsolutePath(),
            ".freeXmlToolkit", "cache", "schemas"
    );

    private static final Path INDEX_FILE = CACHE_DIR.resolve("cache-index.json");

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, Path> urlToLocalPath = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final SchemaCacheIndex cacheIndex;

    // Statistics (kept for backward compatibility)
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

        // Load or create cache index
        this.cacheIndex = SchemaCacheIndex.load(INDEX_FILE);

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
        return getOrDownload(url, null);
    }

    /**
     * Gets or downloads a remote schema file, tracking the referencing schema.
     *
     * <p><b>Security:</b> This method validates URLs to prevent SSRF attacks.
     * URLs pointing to internal networks, localhost, or metadata endpoints are rejected.
     *
     * @param url            the remote URL of the schema file
     * @param referencingUrl the URL of the schema that references this one (for tracking)
     * @return the local path to the cached schema file
     * @throws IOException if the download fails or the file cannot be written
     */
    public Path getOrDownload(String url, String referencingUrl) throws IOException {
        // SECURITY: Validate URL to prevent SSRF attacks
        if (!PathValidator.isUrlSafeToAccess(url)) {
            logger.error("SECURITY: Blocked SSRF attempt in schema cache - URL targets internal/private network: {}", url);
            throw new IOException("Security: Remote schema URL is not allowed (points to internal network): " + url);
        }

        String filename = generateFilename(url);

        // Check in-memory cache first
        Path cachedPath = urlToLocalPath.get(url);
        if (cachedPath != null && Files.exists(cachedPath)) {
            cacheHits.incrementAndGet();
            cacheIndex.recordAccess(filename);
            if (referencingUrl != null) {
                cacheIndex.addReference(filename, referencingUrl);
            }
            logger.debug("Cache hit for schema: {}", url);
            return cachedPath;
        }

        // Check if file exists on disk (might have been cached in previous session)
        Path localPath = CACHE_DIR.resolve(filename);
        if (Files.exists(localPath)) {
            urlToLocalPath.put(url, localPath);
            cacheHits.incrementAndGet();
            cacheIndex.recordAccess(filename);
            if (referencingUrl != null) {
                cacheIndex.addReference(filename, referencingUrl);
            }
            logger.debug("Cache hit (disk) for schema: {}", url);
            return localPath;
        }

        // Download and cache
        cacheMisses.incrementAndGet();
        cacheIndex.recordCacheMiss();
        logger.info("Downloading remote schema: {}", url);

        long startTime = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofByteArray());

            long downloadDuration = System.currentTimeMillis() - startTime;

            if (response.statusCode() != 200) {
                downloadErrors.incrementAndGet();
                cacheIndex.recordDownloadError();
                throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
            }

            byte[] content = response.body();

            // Write to cache file
            Files.write(localPath, content);

            // Create cache entry with metadata
            SchemaCacheEntry entry = createCacheEntry(
                    filename, url, localPath, content, response, downloadDuration
            );
            cacheIndex.addOrUpdateEntry(entry);

            if (referencingUrl != null) {
                cacheIndex.addReference(filename, referencingUrl);
            }

            // Save index after download
            cacheIndex.save(INDEX_FILE);

            urlToLocalPath.put(url, localPath);
            logger.info("Cached remote schema: {} -> {}", url, localPath);
            return localPath;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            downloadErrors.incrementAndGet();
            cacheIndex.recordDownloadError();
            throw new IOException("Download interrupted for URL: " + url, e);
        } catch (Exception e) {
            downloadErrors.incrementAndGet();
            cacheIndex.recordDownloadError();
            throw new IOException("Failed to download schema from URL: " + url, e);
        }
    }

    /**
     * Creates a cache entry with all metadata.
     */
    private SchemaCacheEntry createCacheEntry(
            String filename,
            String url,
            Path localPath,
            byte[] content,
            HttpResponse<byte[]> response,
            long downloadDuration
    ) {
        // Calculate hashes
        String md5Hash = calculateHash(content, "MD5");
        String sha256Hash = calculateHash(content, "SHA-256");

        // Extract HTTP headers
        Long contentLength = response.headers().firstValueAsLong("Content-Length").stream()
                .boxed().findFirst().orElse(null);
        SchemaCacheEntry.HttpInfo httpInfo = new SchemaCacheEntry.HttpInfo(
                response.statusCode(),
                response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("Last-Modified").orElse(null),
                response.headers().firstValue("ETag").orElse(null),
                contentLength,
                downloadDuration
        );

        // Extract schema info
        SchemaCacheEntry.SchemaInfo schemaInfo = extractSchemaInfo(localPath);

        return SchemaCacheEntry.builder()
                .localFilename(filename)
                .remoteUrl(url)
                .downloadTimestamp(Instant.now())
                .fileSizeBytes(content.length)
                .md5Hash(md5Hash)
                .sha256Hash(sha256Hash)
                .http(httpInfo)
                .schema(schemaInfo)
                .usage(SchemaCacheEntry.UsageInfo.initial())
                .build();
    }

    /**
     * Calculates a hash of the given content.
     */
    private String calculateHash(byte[] content, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] hash = md.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.warn("Hash algorithm {} not available", algorithm);
            return null;
        }
    }

    /**
     * Extracts schema information from an XSD file using StAX.
     */
    private SchemaCacheEntry.SchemaInfo extractSchemaInfo(Path schemaFile) {
        String targetNamespace = null;
        String xsdVersion = "1.0"; // Default
        List<String> imports = new ArrayList<>();
        List<String> includes = new ArrayList<>();
        List<String> redefines = new ArrayList<>();

        try {
            // Use SecureXmlFactory for XXE protection
            XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);

            try (InputStream is = Files.newInputStream(schemaFile)) {
                XMLStreamReader reader = factory.createXMLStreamReader(is);

                while (reader.hasNext()) {
                    int event = reader.next();

                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String localName = reader.getLocalName();
                        String namespaceURI = reader.getNamespaceURI();

                        // Check if it's an XSD element
                        if ("http://www.w3.org/2001/XMLSchema".equals(namespaceURI)) {
                            switch (localName) {
                                case "schema" -> {
                                    targetNamespace = reader.getAttributeValue(null, "targetNamespace");
                                }
                                case "import" -> {
                                    String schemaLocation = reader.getAttributeValue(null, "schemaLocation");
                                    if (schemaLocation != null && !schemaLocation.isBlank()) {
                                        imports.add(schemaLocation);
                                    }
                                }
                                case "include" -> {
                                    String schemaLocation = reader.getAttributeValue(null, "schemaLocation");
                                    if (schemaLocation != null && !schemaLocation.isBlank()) {
                                        includes.add(schemaLocation);
                                    }
                                }
                                case "redefine" -> {
                                    String schemaLocation = reader.getAttributeValue(null, "schemaLocation");
                                    if (schemaLocation != null && !schemaLocation.isBlank()) {
                                        redefines.add(schemaLocation);
                                    }
                                }
                                case "override" -> {
                                    // XSD 1.1 feature
                                    xsdVersion = "1.1";
                                    String schemaLocation = reader.getAttributeValue(null, "schemaLocation");
                                    if (schemaLocation != null && !schemaLocation.isBlank()) {
                                        redefines.add(schemaLocation);
                                    }
                                }
                                case "assert", "assertion", "openContent", "defaultOpenContent" -> {
                                    // XSD 1.1 features
                                    xsdVersion = "1.1";
                                }
                            }
                        }
                    }
                }

                reader.close();
            }
        } catch (Exception e) {
            logger.warn("Failed to extract schema info from {}: {}", schemaFile, e.getMessage());
            return SchemaCacheEntry.SchemaInfo.empty();
        }

        return new SchemaCacheEntry.SchemaInfo(
                targetNamespace,
                xsdVersion,
                imports.isEmpty() ? List.of() : List.copyOf(imports),
                includes.isEmpty() ? List.of() : List.copyOf(includes),
                redefines.isEmpty() ? List.of() : List.copyOf(redefines)
        );
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
        cacheIndex.clear();
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
     * Gets the cache index for accessing detailed metadata.
     *
     * @return the cache index
     */
    public SchemaCacheIndex getCacheIndex() {
        return cacheIndex;
    }

    /**
     * Saves the cache index to disk.
     */
    public void saveIndex() {
        cacheIndex.save(INDEX_FILE);
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

                // Rebuild URL-to-path mappings from index
                for (var entry : cacheIndex.getEntries().entrySet()) {
                    String filename = entry.getKey();
                    SchemaCacheEntry cacheEntry = entry.getValue();
                    Path localPath = CACHE_DIR.resolve(filename);

                    if (Files.exists(localPath) && cacheEntry.remoteUrl() != null) {
                        urlToLocalPath.put(cacheEntry.remoteUrl(), localPath);
                    }
                }

                // Also scan for files not in index (from previous versions)
                File[] files = CACHE_DIR.toFile().listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && !file.getName().equals("cache-index.json")) {
                            String filename = file.getName();
                            if (!cacheIndex.getEntry(filename).isPresent()) {
                                // Create basic entry for legacy cached file
                                try {
                                    byte[] content = Files.readAllBytes(file.toPath());
                                    SchemaCacheEntry.SchemaInfo schemaInfo = extractSchemaInfo(file.toPath());

                                    SchemaCacheEntry entry = SchemaCacheEntry.builder()
                                            .localFilename(filename)
                                            .fileSizeBytes(file.length())
                                            .md5Hash(calculateHash(content, "MD5"))
                                            .sha256Hash(calculateHash(content, "SHA-256"))
                                            .schema(schemaInfo)
                                            .usage(SchemaCacheEntry.UsageInfo.initial())
                                            .build();

                                    cacheIndex.addOrUpdateEntry(entry);
                                    logger.debug("Added legacy cache entry: {}", filename);
                                } catch (IOException e) {
                                    logger.warn("Failed to index legacy cache file: {}", filename);
                                }
                            }
                        }
                    }
                }

                // Save updated index
                cacheIndex.save(INDEX_FILE);
            }
        } catch (Exception e) {
            logger.warn("Error loading existing cache", e);
        }
    }

    /**
     * Cache statistics record.
     * @param cacheHits Number of cache hits
     * @param cacheMisses Number of cache misses
     * @param downloadErrors Number of download errors
     * @param totalFiles Total number of cached files
     * @param totalSizeBytes Total size of cached files in bytes
     */
    public record CacheStats(
            long cacheHits,
            long cacheMisses,
            long downloadErrors,
            long totalFiles,
            long totalSizeBytes
    ) {
        /**
         * Formats the total cache size as a human-readable string with appropriate units.
         *
         * <p>The method automatically selects the most appropriate unit (B, KB, or MB)
         * based on the total size to provide a clear and concise representation.</p>
         *
         * @return formatted size string with units (e.g., "512 B", "1.5 KB", "2.3 MB")
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
         * Calculates the cache hit ratio as a percentage value.
         *
         * <p>The hit ratio represents the proportion of cache accesses that resulted
         * in a hit (found in cache) versus the total number of accesses. A higher
         * ratio indicates better cache effectiveness.</p>
         *
         * @return cache hit ratio as a percentage (0.0 to 100.0), or 0.0 if no accesses
         */
        public double getHitRatio() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (cacheHits * 100.0) / total : 0.0;
        }
    }
}
