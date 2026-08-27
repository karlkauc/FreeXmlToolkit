package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.EntrySource;
import org.fxt.freexmltoolkit.domain.SchemaEntryStatus;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;

/**
 * The single Schema Library resolution policy shared by every resolver hook
 * (XSD validation's {@code LSResourceResolver}, the legacy {@code SchemaResolver},
 * the V2 {@code XsdNodeFactory} import/include resolution and Saxon's
 * {@code doc()}/{@code document()} resource resolver).
 *
 * <p>Lookup order — system identifier, public identifier, namespace — mirrors what an
 * OASIS catalog processor does. On top of that it enforces the <b>offline rule</b>:</p>
 * <ul>
 *   <li>a local ({@code file:} / plain path) hit is always served;</li>
 *   <li>a remote hit that is already in the schema cache is always served;</li>
 *   <li>a remote hit that is <i>not</i> cached is downloaded only when downloads are
 *       allowed — see {@link SchemaLibraryService#isRemoteDownloadAllowed()} and
 *       {@code XsdNodeFactory}'s {@code remoteNamespaceFallbackEnabled} flag. The test
 *       suite sets {@code fxt.schema.namespaceFallback=false}, so hooks stay offline.</li>
 * </ul>
 *
 * <p>Every method is best-effort and never throws: a failure is a miss, so the caller's
 * own resolution logic always gets its chance.</p>
 */
public final class SchemaLibraryLookup {

    private static final Logger logger = LogManager.getLogger(SchemaLibraryLookup.class);

    private SchemaLibraryLookup() { }

    /**
     * A resolved local file plus which branch of the lookup order matched, so callers can
     * tell a catalog-mediated resolution ({@code system}/{@code rewriteSystem}/{@code public}
     * catalog entries, or a {@code uri} catalog entry consulted by namespace) from a plain
     * Schema Library mapping (a user or bundled namespace/root-element entry).
     *
     * @param file       the resolved local file
     * @param viaCatalog {@code true} when the hit came from an XML catalog entry
     * @param detail     for a catalog hit, the resolved target location; for a library hit,
     *                   the namespace (or root element) that matched
     */
    public record Hit(Path file, boolean viaCatalog, String detail) { }

    /**
     * Resolves a schema reference to a readable local file through the Schema Library,
     * reporting which branch matched.
     *
     * @param library          the library to consult (a null library is a miss)
     * @param namespace        the target namespace to fall back to, may be null/blank
     * @param systemId         the declared system identifier / schema location, may be null/blank
     * @param publicId         the declared public identifier (catalogs only), may be null/blank
     * @param baseUri          base URI used to absolutize a relative {@code systemId}, may be null
     * @param downloadsAllowed whether an uncached remote entry may be downloaded
     * @return the resolved hit, or empty on a miss
     */
    public static Optional<Hit> lookup(SchemaLibraryService library, String namespace, String systemId,
                                       String publicId, String baseUri, boolean downloadsAllowed) {
        if (library == null) return Optional.empty();
        try {
            if (systemId != null && !systemId.isBlank()) {
                Optional<URI> resolved = library.resolveSystemId(systemId, baseUri);
                if (resolved.isPresent()) {
                    Optional<Path> file = serve(resolved.get(), downloadsAllowed);
                    if (file.isPresent()) return Optional.of(new Hit(file.get(), true, resolved.get().toString()));
                }
            }
            if (publicId != null && !publicId.isBlank()) {
                Optional<URI> resolved = library.resolvePublicId(publicId);
                if (resolved.isPresent()) {
                    Optional<Path> file = serve(resolved.get(), downloadsAllowed);
                    if (file.isPresent()) return Optional.of(new Hit(file.get(), true, resolved.get().toString()));
                }
            }
            if (namespace != null && !namespace.isBlank()) {
                Optional<SchemaLibraryEntry> entry = library.resolveNamespace(namespace, SchemaKind.XSD);
                if (entry.isPresent()) {
                    Optional<Path> file = materializeIfAllowed(library, entry.get(), downloadsAllowed);
                    if (file.isPresent()) {
                        boolean viaCatalog = entry.get().source() == EntrySource.CATALOG;
                        String detail = viaCatalog ? entry.get().location() : namespace;
                        return Optional.of(new Hit(file.get(), viaCatalog, detail));
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Schema Library lookup failed for '{}' (ns {}): {}", systemId, namespace, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Convenience overload without a public identifier.
     *
     * @param library          the library to consult
     * @param namespace        the target namespace, may be null/blank
     * @param systemId         the declared system identifier, may be null/blank
     * @param baseUri          base URI for a relative {@code systemId}, may be null
     * @param downloadsAllowed whether an uncached remote entry may be downloaded
     * @return the resolved hit, or empty on a miss
     */
    public static Optional<Hit> lookup(SchemaLibraryService library, String namespace, String systemId,
                                       String baseUri, boolean downloadsAllowed) {
        return lookup(library, namespace, systemId, null, baseUri, downloadsAllowed);
    }

    /**
     * Resolves a schema reference to a readable local file through the Schema Library.
     *
     * @param library          the library to consult (a null library is a miss)
     * @param namespace        the target namespace to fall back to, may be null/blank
     * @param systemId         the declared system identifier / schema location, may be null/blank
     * @param publicId         the declared public identifier (catalogs only), may be null/blank
     * @param baseUri          base URI used to absolutize a relative {@code systemId}, may be null
     * @param downloadsAllowed whether an uncached remote entry may be downloaded
     * @return the local file backing the resolved entry, or empty on a miss
     */
    public static Optional<Path> localFileFor(SchemaLibraryService library, String namespace, String systemId,
                                              String publicId, String baseUri, boolean downloadsAllowed) {
        return lookup(library, namespace, systemId, publicId, baseUri, downloadsAllowed).map(Hit::file);
    }

    /**
     * Convenience overload without a public identifier.
     *
     * @param library          the library to consult
     * @param namespace        the target namespace, may be null/blank
     * @param systemId         the declared system identifier, may be null/blank
     * @param baseUri          base URI for a relative {@code systemId}, may be null
     * @param downloadsAllowed whether an uncached remote entry may be downloaded
     * @return the local file backing the resolved entry, or empty on a miss
     */
    public static Optional<Path> localFileFor(SchemaLibraryService library, String namespace, String systemId,
                                              String baseUri, boolean downloadsAllowed) {
        return localFileFor(library, namespace, systemId, null, baseUri, downloadsAllowed);
    }

    /**
     * Materializes {@code entry} under the offline rule: local entries and already-cached
     * remote entries always, an uncached remote entry only when {@code downloadsAllowed}.
     *
     * @param library          the library owning the entry (its cache decides "cached")
     * @param entry            the entry to materialize, may be null
     * @param downloadsAllowed whether an uncached remote entry may be downloaded
     * @return the local file, or empty when the entry is not (yet) available
     */
    public static Optional<Path> materializeIfAllowed(SchemaLibraryService library, SchemaLibraryEntry entry,
                                                      boolean downloadsAllowed) {
        if (library == null || entry == null || !mayServe(library, entry, downloadsAllowed)) return Optional.empty();
        try {
            return library.materialize(entry).filter(Files::isRegularFile);
        } catch (Exception e) {
            logger.debug("Schema Library: cannot materialize {}: {}", entry.location(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Applies the offline rule to a resolved target URI: {@code file:} URIs are served from
     * disk, anything else goes through the shared schema cache (SSRF-checked there) and is
     * downloaded only when {@code downloadsAllowed}.
     */
    private static Optional<Path> serve(URI uri, boolean downloadsAllowed) {
        if (uri == null) return Optional.empty();
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                Path p = Path.of(uri);
                return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
        String url = uri.toString();
        SchemaResourceCache cache = SchemaResourceCache.shared();
        if (!downloadsAllowed && !cache.isCached(url)) {
            logger.debug("Schema Library: not downloading {} (remote downloads are disabled)", url);
            return Optional.empty();
        }
        try {
            Path p = cache.getOrDownload(url);
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        } catch (IOException e) {
            logger.debug("Schema Library: cannot fetch {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Whether {@code entry} may be materialized: local entries and entries whose remote copy
     * is already cached always may; an uncached remote entry only when downloads are allowed.
     * Uses {@link SchemaLibraryService#statusOf(SchemaLibraryEntry)} so the library's own
     * cache instance decides (tests inject a temp-dir-backed one).
     */
    private static boolean mayServe(SchemaLibraryService library, SchemaLibraryEntry entry, boolean downloadsAllowed) {
        if (!entry.isRemote() || downloadsAllowed) return true;
        boolean cached = library.statusOf(entry) == SchemaEntryStatus.CACHED;
        if (!cached) {
            logger.debug("Schema Library: not downloading {} (remote downloads are disabled)", entry.location());
        }
        return cached;
    }
}
