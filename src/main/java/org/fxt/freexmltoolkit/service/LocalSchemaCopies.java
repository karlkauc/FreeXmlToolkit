package org.fxt.freexmltoolkit.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps downloaded imports and includes next to the schema that references them, under their
 * original file name, instead of only in the md5-named schema cache.
 * <p>
 * A relative {@code schemaLocation} is stored exactly at its path relative to the referencing
 * schema ({@code xmldsig-core-schema.xsd}, {@code ../common/types.xsd}); an absolute
 * {@code http(s)} location under its last path segment next to it
 * ({@code http://www.w3.org/2001/xml.xsd} → {@code xml.xsd}). Existing files are never
 * overwritten. When the folder cannot be written the resolvers keep using the cache file.
 * <p>
 * Switched off with the system property {@code fxt.schema.localCopies=false} (the test task does
 * that, so tests never write next to their fixtures unless they ask for it).
 *
 * @since 2.9
 */
public final class LocalSchemaCopies {

    /** System property that disables local copies ({@code false}). */
    public static final String PROPERTY = "fxt.schema.localCopies";

    private static final Logger logger = LogManager.getLogger(LocalSchemaCopies.class);

    private LocalSchemaCopies() {
    }

    /** @return whether downloaded schemas are copied next to the referencing schema */
    public static boolean enabled() {
        return !"false".equalsIgnoreCase(System.getProperty(PROPERTY, "true"));
    }

    /**
     * Where the local copy of a referenced schema belongs.
     *
     * @param referencingDir the folder of the referencing schema (null: no local copy)
     * @param schemaLocation the declared {@code schemaLocation}
     * @return the local path, or empty for an empty location, a {@code file:} URI or no folder
     */
    public static Optional<Path> target(Path referencingDir, String schemaLocation) {
        if (referencingDir == null || schemaLocation == null || schemaLocation.isBlank()
                || isInsideAppCache(referencingDir)) {
            return Optional.empty();
        }
        String location = schemaLocation.trim();
        try {
            if (isRemote(location)) {
                String name = lastSegment(location);
                return name == null ? Optional.empty() : Optional.of(referencingDir.resolve(name).normalize());
            }
            if (location.contains(":") && location.indexOf(':') > 1) {
                return Optional.empty(); // file: or another scheme (a Windows drive letter has its colon at 1)
            }
            Path relative = Path.of(location);
            if (relative.isAbsolute()) {
                return Optional.empty();
            }
            return Optional.of(referencingDir.resolve(relative).normalize());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    /**
     * The local copy, when it exists (and, for an import, declares the expected namespace).
     *
     * @param expectedNamespace the import's namespace; null skips the check (includes)
     */
    public static Optional<Path> existing(Path referencingDir, String schemaLocation, String expectedNamespace) {
        if (!enabled()) {
            return Optional.empty();
        }
        return target(referencingDir, schemaLocation)
                .filter(Files::isRegularFile)
                .filter(p -> expectedNamespace == null || namespaceMatches(p, expectedNamespace));
    }

    /**
     * Copies a downloaded schema to its local place. Never overwrites an existing file.
     *
     * @param target     the local path ({@link #target})
     * @param downloaded the downloaded (cache) file
     * @return the local file (also when it already existed), or empty when it cannot be written
     */
    public static Optional<Path> store(Path target, Path downloaded) {
        if (!enabled() || target == null || downloaded == null || !Files.isRegularFile(downloaded)) {
            return Optional.empty();
        }
        if (Files.isRegularFile(target)) {
            return Optional.of(target);
        }
        try {
            if (target.toAbsolutePath().normalize().equals(downloaded.toAbsolutePath().normalize())) {
                return Optional.of(target);
            }
            Path parent = target.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, ".fxt-", ".xsd.part");
            try {
                Files.copy(downloaded, temp, StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, target);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
            logger.info("Saved referenced schema next to its importing schema: {}", target);
            return Optional.of(target);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            return Optional.of(target); // another resolver was faster
        } catch (IOException | RuntimeException e) {
            logger.warn("Cannot save referenced schema at {} (keeping the cache copy): {}", target, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Shortcut for the resolvers: the local copy of {@code downloaded} for this reference, or
     * {@code downloaded} itself when no copy can be made.
     */
    public static Path localize(Path referencingDir, String schemaLocation, Path downloaded) {
        if (!enabled() || downloaded == null) {
            return downloaded;
        }
        return target(referencingDir, schemaLocation)
                .flatMap(t -> store(t, downloaded))
                .orElse(downloaded);
    }

    /** @return the folder of a {@code file:} base URI (or of the file it names), else null */
    public static Path folderOf(String baseUri) {
        if (baseUri == null || baseUri.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(baseUri);
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            Path path = Path.of(uri);
            return Files.isDirectory(path) ? path : path.getParent();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A schema that only lives in the application's cache (its folder could not be written) has
     * no folder of its own: its references stay in the cache too.
     */
    static boolean isInsideAppCache(Path dir) {
        Path cache = Path.of(System.getProperty("user.home"), ".freeXmlToolkit", "cache");
        try {
            return dir.toAbsolutePath().normalize().startsWith(cache);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean isRemote(String location) {
        String lower = location.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** @return the sanitized last path segment of a URL, with {@code .xsd} when it has no extension */
    static String lastSegment(String url) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            int cut = url.indexOf('?');
            path = cut >= 0 ? url.substring(0, cut) : url;
        }
        if (path == null) {
            return null;
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            return null;
        }
        return name.contains(".") ? name : name + ".xsd";
    }

    /** @return whether the schema file's {@code targetNamespace} equals {@code expected} */
    static boolean namespaceMatches(Path file, String expected) {
        try (InputStream in = Files.newInputStream(file)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        String ns = reader.getAttributeValue(null, "targetNamespace");
                        return Objects.equals(ns == null || ns.isBlank() ? null : ns,
                                expected.isBlank() ? null : expected);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
