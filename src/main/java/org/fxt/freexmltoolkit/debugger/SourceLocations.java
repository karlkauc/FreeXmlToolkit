package org.fxt.freexmltoolkit.debugger;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Normalises the many spellings of a stylesheet location so that breakpoints set on an
 * editor document (absolute file path) match the {@code systemId} Saxon reports for the
 * same module ({@code file:/x}, {@code file:///x}, {@code file:/C:/x}, URL-encoded, …).
 *
 * <p>Non-file schemes ({@code jar:}, {@code http:}) are returned unchanged; an empty or
 * {@code null} location becomes {@code ""}, which the debugger treats as "in-memory
 * stylesheet, match on line number only".</p>
 */
public final class SourceLocations {

    private SourceLocations() {
    }

    /**
     * @param systemIdOrPath a Saxon systemId, a file URI or a plain file path
     * @return the absolute, normalised file path, the unchanged non-file URI, or {@code ""}
     */
    public static String normalize(String systemIdOrPath) {
        if (systemIdOrPath == null || systemIdOrPath.isBlank()) {
            return "";
        }
        String s = systemIdOrPath.trim();
        if (s.regionMatches(true, 0, "file:", 0, 5)) {
            try {
                return normalizePath(Paths.get(URI.create(s)));
            } catch (RuntimeException e) {
                // Not a well-formed file URI (e.g. "file:relative.xsl" or spaces): strip the
                // scheme, decode and treat the rest as a plain path.
                String rest = s.substring(5);
                while (rest.startsWith("//")) {
                    rest = rest.substring(1);
                }
                return normalizePath(Paths.get(URLDecoder.decode(rest, StandardCharsets.UTF_8)));
            }
        }
        if (hasScheme(s)) {
            return s;
        }
        try {
            return normalizePath(Paths.get(s));
        } catch (RuntimeException e) {
            return s;
        }
    }

    /** @return {@code true} when both locations denote the same file (after normalisation). */
    public static boolean sameFile(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        return !na.isEmpty() && na.equals(nb);
    }

    /** @return the file name part of a location (for labels), or the location itself. */
    public static String fileName(String systemIdOrPath) {
        String n = normalize(systemIdOrPath);
        if (n.isEmpty()) {
            return "";
        }
        int cut = Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\'));
        return cut >= 0 ? n.substring(cut + 1) : n;
    }

    private static String normalizePath(Path path) {
        try {
            return path.toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            return path.normalize().toString();
        }
    }

    /** A scheme is at least two letters followed by ':' — keeps Windows drive letters as paths. */
    private static boolean hasScheme(String s) {
        int colon = s.indexOf(':');
        if (colon < 2) {
            return false;
        }
        for (int i = 0; i < colon; i++) {
            char c = s.charAt(i);
            if (!(Character.isLetter(c) || c == '+' || c == '-' || c == '.')) {
                return false;
            }
        }
        return true;
    }
}
