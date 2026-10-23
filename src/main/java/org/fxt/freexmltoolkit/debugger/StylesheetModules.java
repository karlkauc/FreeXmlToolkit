package org.fxt.freexmltoolkit.debugger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Finds the local modules a stylesheet pulls in through {@code xsl:include} / {@code xsl:import}
 * (transitively), so a debug session can load the breakpoints set in those files before Saxon
 * ever reaches them. A text scan, not a parse: good enough for href attributes, and it never
 * fails the session — unreadable files or odd markup simply contribute nothing.
 */
public final class StylesheetModules {

    private static final Logger logger = LogManager.getLogger(StylesheetModules.class);

    /** Nested includes deeper than this are ignored (guards against runaway chains). */
    static final int MAX_DEPTH = 10;

    /** {@code <xsl:include href="…">} / {@code <xsl:import href='…'>}, any prefix, attributes in any order. */
    private static final Pattern DIRECTIVE = Pattern.compile(
            "<\\s*(?:[\\w.-]+:)?(?:include|import)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile(
            "\\bhref\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    private StylesheetModules() {
    }

    /**
     * @param main the main stylesheet file
     * @return the normalised paths of every existing local module it includes or imports,
     *         transitively, without the main file itself (never {@code null})
     */
    public static Set<String> resolve(Path main) {
        Set<String> found = new LinkedHashSet<>();
        if (main == null) {
            return found;
        }
        String mainPath = SourceLocations.normalize(main.toString());
        Set<String> visited = new LinkedHashSet<>();
        visited.add(mainPath);
        collect(main, 0, visited, found);
        found.remove(mainPath);
        return found;
    }

    private static void collect(Path file, int depth, Set<String> visited, Set<String> found) {
        if (depth > MAX_DEPTH) {
            return;
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            logger.debug("Cannot scan {} for includes: {}", file, e.getMessage());
            return;
        }
        Path base = file.toAbsolutePath().getParent();
        Matcher directive = DIRECTIVE.matcher(text);
        while (directive.find()) {
            Matcher href = HREF.matcher(directive.group(1));
            if (!href.find()) {
                continue;
            }
            Path target = resolveHref(base, href.group(2).trim());
            if (target == null || !Files.isRegularFile(target)) {
                continue;
            }
            String normalized = SourceLocations.normalize(target.toString());
            if (!visited.add(normalized)) {
                continue;
            }
            found.add(normalized);
            collect(target, depth + 1, visited, found);
        }
    }

    /** Resolves a relative or {@code file:} href against the including file; other schemes yield {@code null}. */
    private static Path resolveHref(Path base, String href) {
        if (href.isEmpty()) {
            return null;
        }
        try {
            if (href.regionMatches(true, 0, "file:", 0, 5)) {
                String normalized = SourceLocations.normalize(href);
                return normalized.isEmpty() ? null : Path.of(normalized);
            }
            if (href.matches("(?i)^[a-z][a-z0-9+.-]+:.*")) {
                return null; // http:, jar:, classpath: … not a local module
            }
            String noFragment = href.split("[#?]", 2)[0];
            if (noFragment.isEmpty()) {
                return null;
            }
            Path candidate = Path.of(noFragment);
            return (base == null || candidate.isAbsolute() ? candidate : base.resolve(candidate)).normalize();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
