package org.fxt.freexmltoolkit.service.grammar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.xml.sax.InputSource;

/**
 * Finds the files of DTDs (and their modules) and RELAX NG schemas: through the XML catalogs and the
 * Schema Library first (public and system ids), then as local files, then — for {@code http(s)} only —
 * from the schema cache, which downloads once through the proxy settings and refuses internal
 * addresses. Every other scheme is refused. The returned source keeps the original system id, so
 * relative references inside a downloaded DTD resolve against its address.
 */
public final class GrammarResolver {

    private static final Logger logger = LogManager.getLogger(GrammarResolver.class);

    private GrammarResolver() {
    }

    /**
     * The files one parse (a validation or a translation) may read. A DTD can build the system id of
     * a parameter entity from the text of another entity, so a grammar that reads a local file and
     * then fetches a URL can send that file's contents to any server. A session therefore refuses to
     * mix the two: once it has read a local file other than the root grammar it downloads nothing,
     * and once it has downloaded something it reads no further local file. Resources mapped by an XML
     * catalog or the Schema Library are trusted and count as neither.
     */
    public static final class Session {
        private final URI root;
        private boolean readLocal;
        private boolean downloaded;

        /** @param rootUri the grammar (or document) the parse starts from, or {@code null} */
        public Session(String rootUri) {
            URI parsed = null;
            try {
                parsed = rootUri == null ? null : new URI(rootUri).normalize();
            } catch (Exception e) {
                // no root to compare with
            }
            this.root = parsed;
        }

        boolean isRoot(URI target) {
            return root != null && root.equals(target.normalize());
        }

        void check(URI target, boolean remote) throws IOException {
            if (remote ? readLocal : downloaded) {
                throw new IOException("Refused: " + target + " — a grammar that reads local files must not load "
                        + "files from the network, and the other way round, because entity text could carry the "
                        + "contents of a local file to a server. Map the resource in an XML catalog or the Schema "
                        + "Library to use it.");
            }
            if (remote) {
                downloaded = true;
            } else {
                readLocal = true;
            }
        }
    }

    /**
     * @param publicId the public id, or {@code null}
     * @param systemId the system id as written, or {@code null}
     * @param baseUri  the URI the system id is relative to, or {@code null}
     * @return the local file of the resource
     * @throws IOException when it cannot be found or is not allowed
     */
    public static Path resolveToFile(String publicId, String systemId, String baseUri) throws IOException {
        return resolveToFile(null, false, publicId, systemId, baseUri);
    }

    /**
     * @param session the parse this resource belongs to (see {@link Session}), or {@code null} for a
     *                single lookup
     * @param root    {@code true} for the grammar the parse starts from (a document's DOCTYPE)
     */
    public static Path resolveToFile(Session session, boolean root, String publicId, String systemId, String baseUri)
            throws IOException {
        Optional<URI> mapped = Optional.empty();
        try {
            if (publicId != null && !publicId.isBlank()) {
                mapped = SchemaLibraryServiceImpl.shared().resolvePublicId(publicId.strip());
            }
            if (mapped.isEmpty() && systemId != null && !systemId.isBlank()) {
                mapped = SchemaLibraryServiceImpl.shared().resolveSystemId(systemId.strip(), baseUri);
            }
        } catch (RuntimeException e) {
            logger.debug("No Schema Library for {}: {}", systemId, e.getMessage());
        }
        URI target;
        if (mapped.isPresent()) {
            target = mapped.get();
        } else if (systemId == null || systemId.isBlank()) {
            throw new IOException("No location for " + (publicId != null ? "\"" + publicId + "\"" : "the grammar")
                    + " — add it to an XML catalog or the Schema Library.");
        } else {
            target = absolute(systemId.strip(), baseUri);
        }
        String scheme = target.getScheme() == null ? "file" : target.getScheme().toLowerCase(Locale.ROOT);
        boolean remote = scheme.equals("http") || scheme.equals("https");
        if (!remote && !scheme.equals("file")) {
            throw new IOException("Grammars are loaded from files and http(s) only, not from " + target);
        }
        if (!remote && target.getRawAuthority() != null && !target.getRawAuthority().isBlank()) {
            // file://host/share: a network share, which would hand the user's credentials to that host
            throw new IOException("Grammars are not loaded from network shares: " + target);
        }
        if (session != null && mapped.isEmpty() && !root && !session.isRoot(target)) {
            session.check(target, remote);
        }
        if (remote) {
            return SchemaResourceCache.shared().getOrDownload(target.toString());
        }
        Path file = Path.of(target);
        String text = file.toString();
        if (text.startsWith("\\\\") || text.startsWith("//")) {
            throw new IOException("Grammars are not loaded from network shares: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("Not found: " + file);
        }
        return file;
    }

    /** @return the source for a parser, reading the local file but keeping the original system id */
    public static InputSource resolve(String publicId, String systemId, String baseUri) throws IOException {
        return resolve(null, false, publicId, systemId, baseUri);
    }

    /** {@link #resolve(String, String, String)} within a {@link Session}. */
    public static InputSource resolve(Session session, boolean root, String publicId, String systemId, String baseUri)
            throws IOException {
        Path file = resolveToFile(session, root, publicId, systemId, baseUri);
        InputStream in = Files.newInputStream(file);
        InputSource source = new InputSource(in);
        source.setPublicId(publicId);
        source.setSystemId(systemId == null ? file.toUri().toString() : absolute(systemId, baseUri).toString());
        return source;
    }

    static URI absolute(String systemId, String baseUri) throws IOException {
        try {
            URI uri = new URI(systemId.replace(" ", "%20").replace('\\', '/'));
            if (uri.isAbsolute()) {
                return uri;
            }
            if (baseUri != null && !baseUri.isBlank()) {
                return new URI(baseUri).resolve(uri);
            }
            return Path.of(systemId).toAbsolutePath().toUri();
        } catch (Exception e) {
            // Windows paths and other non-URI system ids
            Path path = Path.of(systemId);
            if (!path.isAbsolute() && baseUri != null && baseUri.startsWith("file:")) {
                path = Path.of(URI.create(baseUri)).resolveSibling(systemId);
            }
            return path.toAbsolutePath().normalize().toUri();
        }
    }
}
