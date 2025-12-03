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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared LSResourceResolver implementation for resolving xs:import and xs:include references.
 *
 * <p>This resolver supports:
 * <ul>
 *   <li>Local file paths (relative and absolute)</li>
 *   <li>Remote URLs (HTTP/HTTPS) with caching</li>
 *   <li>Circular import detection</li>
 *   <li>Proper URI handling for Windows paths</li>
 * </ul>
 * </p>
 *
 * <p>This class is used by both SaxonXmlValidationService and XercesXmlValidationService
 * to provide consistent schema resolution behavior.</p>
 */
public class SchemaResourceResolver implements LSResourceResolver {

    private static final Logger logger = LogManager.getLogger(SchemaResourceResolver.class);

    private final SchemaResourceCache cache;
    private final ThreadLocal<Set<String>> visitedUris = ThreadLocal.withInitial(HashSet::new);

    /**
     * Creates a new schema resource resolver with the given cache.
     *
     * @param cache the schema resource cache for remote URLs
     */
    public SchemaResourceResolver(SchemaResourceCache cache) {
        this.cache = cache;
    }

    /**
     * Creates a new schema resource resolver with a default cache.
     */
    public SchemaResourceResolver() {
        this(new SchemaResourceCache());
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId,
                                   String systemId, String baseURI) {
        logger.debug("Resolving resource - type: {}, namespace: {}, systemId: {}, baseURI: {}",
                type, namespaceURI, systemId, baseURI);

        if (systemId == null || systemId.isBlank()) {
            logger.debug("SystemId is null or blank, returning null");
            return null;
        }

        // Check for circular imports
        Set<String> visited = visitedUris.get();
        String normalizedSystemId = normalizeSystemId(systemId, baseURI);
        if (visited.contains(normalizedSystemId)) {
            logger.warn("Circular import detected for: {}", normalizedSystemId);
            return null;
        }
        visited.add(normalizedSystemId);

        try {
            // Handle remote URLs (HTTP/HTTPS)
            if (isRemoteUrl(systemId)) {
                return resolveRemote(systemId, publicId);
            }

            // Handle local file paths
            return resolveLocal(systemId, publicId, baseURI);

        } catch (Exception e) {
            logger.warn("Error resolving resource '{}': {}", systemId, e.getMessage());
            return null;
        }
    }

    /**
     * Clears the visited URIs tracking for circular import detection.
     * Should be called before starting a new validation.
     */
    public void resetCircularDetection() {
        visitedUris.get().clear();
    }

    /**
     * Gets the underlying schema resource cache.
     *
     * @return the schema resource cache
     */
    public SchemaResourceCache getCache() {
        return cache;
    }

    /**
     * Checks if a systemId is a remote URL.
     */
    private boolean isRemoteUrl(String systemId) {
        return systemId.startsWith("http://") || systemId.startsWith("https://");
    }

    /**
     * Resolves a remote URL schema.
     */
    private LSInput resolveRemote(String systemId, String publicId) {
        try {
            logger.debug("Resolving remote schema: {}", systemId);
            Path cachedPath = cache.getOrDownload(systemId);
            return new LSInputImpl(
                    publicId,
                    systemId,
                    cachedPath.toUri().toString(),
                    Files.newInputStream(cachedPath)
            );
        } catch (IOException e) {
            logger.error("Failed to download remote schema '{}': {}", systemId, e.getMessage());
            return null;
        }
    }

    /**
     * Resolves a local file path schema.
     */
    private LSInput resolveLocal(String systemId, String publicId, String baseURI) {
        try {
            Path resolvedPath = resolveLocalPath(systemId, baseURI);

            if (resolvedPath != null && Files.exists(resolvedPath)) {
                logger.debug("Resolved local schema: {} -> {}", systemId, resolvedPath);
                return new LSInputImpl(
                        publicId,
                        systemId,
                        resolvedPath.toUri().toString(),
                        Files.newInputStream(resolvedPath)
                );
            } else {
                logger.warn("Could not resolve local schema '{}' (tried: {})", systemId, resolvedPath);
                return null;
            }
        } catch (java.nio.file.AccessDeniedException e) {
            logger.error("Access denied to schema file '{}'. On macOS, files in Downloads may be quarantined. " +
                        "Try: xattr -rd com.apple.quarantine <folder> or move files to another location.",
                        systemId);
            return null;
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null && message.contains("Operation not permitted")) {
                logger.error("Permission denied for schema '{}'. On macOS, this may be due to quarantine attributes. " +
                            "Try: xattr -rd com.apple.quarantine <folder>", systemId);
            } else {
                logger.warn("Error resolving local schema '{}': {}", systemId, message);
            }
            return null;
        }
    }

    /**
     * Resolves a local file path, handling both relative and absolute paths.
     * Uses proper URI handling to support Windows paths correctly.
     */
    private Path resolveLocalPath(String systemId, String baseURI) {
        try {
            // If systemId is already an absolute path
            if (systemId.startsWith("/") || (systemId.length() > 1 && systemId.charAt(1) == ':')) {
                Path absolutePath = Paths.get(systemId);
                if (Files.exists(absolutePath)) {
                    return absolutePath;
                }
            }

            // If we have a baseURI, resolve relative to it
            if (baseURI != null && !baseURI.isBlank()) {
                Path basePath = uriToPath(baseURI);
                if (basePath != null) {
                    Path baseDir = basePath.getParent();
                    if (baseDir != null) {
                        Path resolvedPath = baseDir.resolve(systemId).normalize();
                        if (Files.exists(resolvedPath)) {
                            return resolvedPath;
                        }
                    }
                }
            }

            // Try as a plain path
            Path plainPath = Paths.get(systemId);
            if (Files.exists(plainPath)) {
                return plainPath;
            }

            return null;
        } catch (Exception e) {
            logger.debug("Error resolving path '{}' with base '{}': {}",
                    systemId, baseURI, e.getMessage());
            return null;
        }
    }

    /**
     * Converts a URI string to a Path, handling file:// URIs correctly.
     */
    private Path uriToPath(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            return null;
        }

        try {
            // Handle file:// URIs properly using Java's URI class
            if (uriString.startsWith("file:")) {
                URI uri = new URI(uriString);
                return Paths.get(uri);
            }

            // Handle plain paths
            return Paths.get(uriString);
        } catch (URISyntaxException | IllegalArgumentException e) {
            logger.debug("Could not convert URI to path: {}", uriString);

            // Fallback: try manual conversion for edge cases
            try {
                String path = uriString;
                if (path.startsWith("file:///")) {
                    path = path.substring(8);
                } else if (path.startsWith("file://")) {
                    path = path.substring(7);
                } else if (path.startsWith("file:/")) {
                    path = path.substring(6);
                }
                return Paths.get(path);
            } catch (Exception fallbackException) {
                return null;
            }
        }
    }

    /**
     * Normalizes a systemId for circular import detection.
     * Resolves relative paths against baseURI if available.
     */
    private String normalizeSystemId(String systemId, String baseURI) {
        if (isRemoteUrl(systemId)) {
            return systemId;
        }

        if (baseURI != null && !baseURI.isBlank()) {
            try {
                Path basePath = uriToPath(baseURI);
                if (basePath != null) {
                    Path baseDir = basePath.getParent();
                    if (baseDir != null) {
                        return baseDir.resolve(systemId).normalize().toString();
                    }
                }
            } catch (Exception e) {
                // Fall through to return systemId as-is
            }
        }

        return systemId;
    }
}
