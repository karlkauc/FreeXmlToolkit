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
     * @param publicId the public id, or {@code null}
     * @param systemId the system id as written, or {@code null}
     * @param baseUri  the URI the system id is relative to, or {@code null}
     * @return the local file of the resource
     * @throws IOException when it cannot be found or is not allowed
     */
    public static Path resolveToFile(String publicId, String systemId, String baseUri) throws IOException {
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
        switch (scheme) {
            case "file" -> {
                Path file = Path.of(target);
                if (!Files.isRegularFile(file)) {
                    throw new IOException("Not found: " + file);
                }
                return file;
            }
            case "http", "https" -> {
                return SchemaResourceCache.shared().getOrDownload(target.toString());
            }
            default -> throw new IOException("Grammars are loaded from files and http(s) only, not from " + target);
        }
    }

    /** @return the source for a parser, reading the local file but keeping the original system id */
    public static InputSource resolve(String publicId, String systemId, String baseUri) throws IOException {
        Path file = resolveToFile(publicId, systemId, baseUri);
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
