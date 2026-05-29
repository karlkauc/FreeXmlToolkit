package org.fxt.freexmltoolkit.controls.shell.editor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the local XSD referenced by an XML document's
 * {@code xsi:noNamespaceSchemaLocation}, relative to the document's path.
 * <p>
 * Stateless and dependency-free (no global {@code XmlService} state), so it is
 * safe to use per tab in the Unified editor host for IntelliSense auto-schema.
 * Remote (http/https) locations and namespaced {@code schemaLocation} are out of
 * scope here (handled later via the schema service).
 */
public final class SchemaLocationResolver {

    private static final Pattern NO_NS_SCHEMA_LOCATION =
            Pattern.compile("noNamespaceSchemaLocation\\s*=\\s*[\"']([^\"']+)[\"']");

    private SchemaLocationResolver() {
    }

    /**
     * @param xmlContent the XML text (may be {@code null})
     * @param xmlFile    the document's path, used to resolve relative references (may be {@code null})
     * @return the existing local XSD path, or empty if none / not found / remote
     */
    public static Optional<Path> resolveLocalXsd(String xmlContent, Path xmlFile) {
        if (xmlContent == null || xmlFile == null) {
            return Optional.empty();
        }
        Matcher matcher = NO_NS_SCHEMA_LOCATION.matcher(xmlContent);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String location = matcher.group(1).trim();
        if (location.isEmpty() || isRemote(location)) {
            return Optional.empty();
        }
        Path candidate = Path.of(location);
        if (!candidate.isAbsolute()) {
            Path parent = xmlFile.getParent();
            candidate = parent != null ? parent.resolve(location) : candidate;
        }
        candidate = candidate.normalize();
        return Files.exists(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private static boolean isRemote(String location) {
        String lower = location.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("ftp://");
    }
}
