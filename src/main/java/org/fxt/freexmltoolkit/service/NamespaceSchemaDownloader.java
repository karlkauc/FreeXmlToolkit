/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.ConnectionResult;
import org.fxt.freexmltoolkit.util.PathValidator;
import org.fxt.freexmltoolkit.util.SecureXmlFactory;

/**
 * Resolves an XSD import schema via its namespace URL when the schemaLocation
 * cannot be resolved locally.
 *
 * <p>Resolution strategy (verified against the W3C xmldsig setup):
 * <ol>
 *   <li><b>Offline cache lookup:</b> if a previously downloaded schema with the
 *       requested target namespace exists in the {@link SchemaResourceCache},
 *       it is served without any network access.</li>
 *   <li><b>Discovery:</b> the namespace URI (fragment stripped) is fetched via
 *       {@link ConnectionService} (proxy-aware, follows redirects). If the response
 *       body is already an XSD, that final URL is the schema URL.</li>
 *   <li><b>Relative resolution:</b> otherwise the filename from the schemaLocation
 *       is resolved against the <em>final</em> URI after redirects (W3C namespace
 *       URIs redirect to the spec directory that hosts the schema file, e.g.
 *       {@code http://www.w3.org/2000/09/xmldsig#} &rarr;
 *       {@code https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd}).</li>
 * </ol>
 * The resulting schema is downloaded through the {@link SchemaResourceCache}, which
 * persists it (including its target namespace in the cache index) so subsequent
 * loads succeed offline via step 1.</p>
 *
 * <p>Every URL is validated with {@link PathValidator#isUrlSafeToAccess(String)}
 * before any request — namespace URIs are untrusted input from the XSD file.</p>
 */
public class NamespaceSchemaDownloader {

    private static final Logger logger = LogManager.getLogger(NamespaceSchemaDownloader.class);

    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    private final ConnectionService connectionService;
    private final SchemaResourceCache schemaCache;

    /**
     * A schema resolved via its namespace URL.
     *
     * @param content    the schema content
     * @param cachedPath the local cache file holding the schema, or null if not cached
     * @param sourceUrl  the URL the schema was ultimately loaded from (or the cache description)
     */
    public record ResolvedNamespaceSchema(String content, Path cachedPath, String sourceUrl) {
    }

    /**
     * Creates a downloader using the application {@link ConnectionService} and the
     * shared {@link SchemaResourceCache} instance.
     */
    public NamespaceSchemaDownloader() {
        this(ServiceRegistry.get(ConnectionService.class), SchemaResourceCache.shared());
    }

    /**
     * Creates a downloader with explicit dependencies (used by tests).
     *
     * @param connectionService the HTTP service used for namespace discovery
     * @param schemaCache       the disk cache used for schema downloads and offline lookups
     */
    public NamespaceSchemaDownloader(ConnectionService connectionService, SchemaResourceCache schemaCache) {
        this.connectionService = connectionService;
        this.schemaCache = schemaCache;
    }

    /**
     * Attempts to resolve the schema for the given import namespace via its namespace URL.
     *
     * @param namespace      the import namespace (must be an http/https URI to be resolvable)
     * @param schemaLocation the original schemaLocation of the import (used for relative resolution)
     * @return the resolved schema, or empty if the namespace does not yield a schema
     */
    public Optional<ResolvedNamespaceSchema> resolve(String namespace, String schemaLocation) {
        if (namespace == null
                || (!namespace.startsWith("http://") && !namespace.startsWith("https://"))) {
            return Optional.empty();
        }

        // 1. Offline lookup: previously downloaded schema with this target namespace
        Optional<ResolvedNamespaceSchema> cached = lookupCachedByNamespace(namespace);
        if (cached.isPresent()) {
            return cached;
        }

        // 2. Discovery: fetch the namespace URI itself (fragment stripped, redirects followed)
        String namespaceUrl = stripFragment(namespace);
        if (!PathValidator.isUrlSafeToAccess(namespaceUrl)) {
            logger.warn("SECURITY: Blocked namespace URL lookup (unsafe URL): {}", namespaceUrl);
            return Optional.empty();
        }

        ConnectionResult discovery;
        try {
            discovery = connectionService.executeHttpRequest(URI.create(namespaceUrl));
        } catch (Exception e) {
            logger.info("Namespace URL not reachable: {} ({})", namespaceUrl, e.getMessage());
            return Optional.empty();
        }
        if (discovery == null || discovery.httpStatus() == null || discovery.httpStatus() != 200
                || discovery.resultBody() == null || discovery.resultBody().isEmpty()) {
            logger.info("Namespace URL did not return content: {} (status: {})",
                    namespaceUrl, discovery != null ? discovery.httpStatus() : null);
            return Optional.empty();
        }

        String schemaUrl;
        if (looksLikeXsdSchema(discovery.resultBody())) {
            // The namespace URL serves the schema directly
            schemaUrl = discovery.url() != null ? discovery.url().toString() : namespaceUrl;
        } else {
            // 3. The namespace URL serves a document (e.g. HTML spec page). Resolve the
            // schemaLocation filename against the final URI after redirects.
            String filename = extractFilename(schemaLocation);
            if (filename == null || discovery.url() == null) {
                return Optional.empty();
            }
            schemaUrl = discovery.url().resolve(filename).toString();
        }

        return downloadViaCache(schemaUrl, namespace);
    }

    private Optional<ResolvedNamespaceSchema> lookupCachedByNamespace(String namespace) {
        try {
            Optional<Path> cachedPath = schemaCache.findCachedByTargetNamespace(namespace);
            if (cachedPath.isPresent()) {
                String content = Files.readString(cachedPath.get());
                if (looksLikeXsdSchema(content)) {
                    logger.info("Resolved schema for namespace {} from local cache: {}",
                            namespace, cachedPath.get());
                    return Optional.of(new ResolvedNamespaceSchema(
                            content, cachedPath.get(), "cache:" + cachedPath.get()));
                }
            }
        } catch (Exception e) {
            logger.debug("Cache lookup by namespace failed for {}: {}", namespace, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<ResolvedNamespaceSchema> downloadViaCache(String schemaUrl, String namespace) {
        if (!PathValidator.isUrlSafeToAccess(schemaUrl)) {
            logger.warn("SECURITY: Blocked schema download (unsafe URL): {}", schemaUrl);
            return Optional.empty();
        }
        try {
            Path localPath = schemaCache.getOrDownload(schemaUrl, namespace);
            String content = Files.readString(localPath);
            if (!looksLikeXsdSchema(content)) {
                logger.info("Content at {} is not an XSD schema, ignoring", schemaUrl);
                return Optional.empty();
            }
            return Optional.of(new ResolvedNamespaceSchema(content, localPath, schemaUrl));
        } catch (Exception e) {
            logger.info("Failed to download schema from {}: {}", schemaUrl, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks whether the given content is an XSD schema document by parsing up to the
     * first start element and verifying it is {@code {http://www.w3.org/2001/XMLSchema}schema}.
     *
     * @param content the content to check
     * @return true if the root element is an XSD schema element
     */
    static boolean looksLikeXsdSchema(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        XMLStreamReader reader = null;
        try {
            XMLInputFactory factory = SecureXmlFactory.createSecureXMLInputFactory();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            reader = factory.createXMLStreamReader(new StringReader(content));
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    return "schema".equals(reader.getLocalName())
                            && XSD_NAMESPACE.equals(reader.getNamespaceURI());
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // nothing to clean up beyond the reader itself
                }
            }
        }
    }

    /**
     * Strips a URI fragment ({@code #...}) from a namespace URI.
     */
    static String stripFragment(String uri) {
        int hash = uri.indexOf('#');
        return hash >= 0 ? uri.substring(0, hash) : uri;
    }

    /**
     * Extracts the filename (last path segment) from a schemaLocation.
     */
    static String extractFilename(String schemaLocation) {
        if (schemaLocation == null || schemaLocation.isBlank()) {
            return null;
        }
        String normalized = schemaLocation.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return filename.isBlank() ? null : filename;
    }
}
