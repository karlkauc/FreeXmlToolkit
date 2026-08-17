package org.fxt.freexmltoolkit.controls.v2.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.fxt.freexmltoolkit.service.NamespaceSchemaDownloader;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;

/**
 * Shared state for one transitive import-resolution run of {@link XsdNodeFactory}.
 *
 * <p>The root factory creates one context per parse; every child factory spawned for an
 * imported schema receives the same instance. It provides:</p>
 * <ul>
 *   <li>a DFS resolution stack of canonical schema keys for circular-import detection,</li>
 *   <li>a registry of already-parsed imported schemas so diamond imports
 *       (A imports B and C, both import D) reuse one parsed instance,</li>
 *   <li>the root schema, into which transitively imported schemas are flattened so that
 *       one-level consumers of {@link XsdSchema#getImportedSchemas()} see all of them,</li>
 *   <li>shared lazily-created network helpers (namespace-URL downloader, schema cache).</li>
 * </ul>
 *
 * <p>Canonical keys: local files use their real path, remote schemas their URL, and
 * namespace-fallback downloads the real path of the cached file.</p>
 */
final class ImportResolutionContext {

    /** Maximum depth of transitive imports before resolution is aborted. */
    static final int MAX_IMPORT_DEPTH = 10;

    private final XsdSchema rootSchema;
    private final boolean remoteNamespaceFallbackEnabled;
    private final Deque<String> resolutionStack = new ArrayDeque<>();
    private final Map<String, XsdSchema> resolvedByKey = new HashMap<>();
    private NamespaceSchemaDownloader downloader;
    private SchemaResourceCache schemaCache;

    ImportResolutionContext(XsdSchema rootSchema, boolean remoteNamespaceFallbackEnabled,
                            NamespaceSchemaDownloader downloader) {
        this.rootSchema = rootSchema;
        this.remoteNamespaceFallbackEnabled = remoteNamespaceFallbackEnabled;
        this.downloader = downloader;
    }

    XsdSchema rootSchema() {
        return rootSchema;
    }

    boolean isRemoteNamespaceFallbackEnabled() {
        return remoteNamespaceFallbackEnabled;
    }

    NamespaceSchemaDownloader downloader() {
        if (downloader == null) {
            downloader = new NamespaceSchemaDownloader();
        }
        return downloader;
    }

    SchemaResourceCache schemaCache() {
        if (schemaCache == null) {
            schemaCache = new SchemaResourceCache();
        }
        return schemaCache;
    }

    boolean isOnStack(String canonicalKey) {
        return resolutionStack.contains(canonicalKey);
    }

    void push(String canonicalKey) {
        resolutionStack.addLast(canonicalKey);
    }

    void pop(String canonicalKey) {
        resolutionStack.removeLastOccurrence(canonicalKey);
    }

    /** Returns the current resolution path plus the offending key, for cycle error messages. */
    String chainWith(String canonicalKey) {
        StringBuilder chain = new StringBuilder();
        for (String entry : resolutionStack) {
            chain.append(entry).append(" -> ");
        }
        return chain.append(canonicalKey).toString();
    }

    void registerResolved(String canonicalKey, XsdSchema schema) {
        resolvedByKey.put(canonicalKey, schema);
    }

    XsdSchema getResolved(String canonicalKey) {
        return resolvedByKey.get(canonicalKey);
    }
}
