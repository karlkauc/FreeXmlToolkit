package org.fxt.freexmltoolkit.controls.shell.editor.search;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * UI-free engine for "Find in Files": walks a folder, filters by glob, and
 * matches a plain-text / whole-word / regex query per file. Follows the
 * {@code ValidationRunner} batch conventions (static, records, progress
 * callback, cooperative cancellation); the panel does the threading.
 */
public final class FileSearchRunner {

    /** Files larger than this are skipped (matches the editor's comfort zone). */
    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    /** Per-file match cap so a degenerate query cannot flood the results tree. */
    public static final int MAX_MATCHES_PER_FILE = 1000;

    /** Default file-name globs, mirroring the Explorer workspace tree's extensions. */
    public static final List<String> DEFAULT_GLOBS = List.of(
            "*.xml", "*.xsd", "*.xsl", "*.xslt", "*.sch", "*.schematron", "*.json",
            "*.xq", "*.xquery", "*.xqm", "*.xqy", "*.xpath", "*.xpl", "*.xproc");

    private FileSearchRunner() {
    }

    /** The user's query with its options and the file-name globs to search. */
    public record TextSearchQuery(String pattern, boolean caseSensitive, boolean wholeWord,
                                  boolean regex, List<String> globs) {
    }

    /**
     * One match: absolute char offsets {@code [start, end)} in the searched text,
     * plus the 1-based line number, the line's start offset and its text for display.
     */
    public record TextMatch(int lineNumber, int lineStart, int start, int end, String lineText) {
    }

    /** Snapshot of the searched content, re-checked before any replacement is applied. */
    public record FileFingerprint(long mtime, long size, int textHash) {
    }

    /**
     * All matches of one file. {@code error} is non-null when the file could not
     * be searched (unreadable, binary, too large); {@code matches} is empty then.
     */
    public record FileSearchResult(Path file, Charset charset, boolean bom,
                                   List<TextMatch> matches, FileFingerprint fingerprint,
                                   boolean fromEditorBuffer, boolean truncated, String error) {
    }

    /**
     * Compiles the query into a {@link Pattern}. Non-regex queries are quoted;
     * whole-word wraps the pattern in letter/digit/underscore lookarounds.
     *
     * @throws java.util.regex.PatternSyntaxException for an invalid user regex
     */
    public static Pattern compile(TextSearchQuery query) {
        String p = query.regex() ? query.pattern() : Pattern.quote(query.pattern());
        if (query.wholeWord()) {
            p = "(?<![\\p{L}\\p{N}_])(?:" + p + ")(?![\\p{L}\\p{N}_])";
        }
        int flags = query.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(p, flags);
    }

    /**
     * Recursively collects the regular files under {@code root} whose file name
     * matches any of {@code globs}, sorted by path. Oversized files are excluded
     * here; binary detection happens at read time.
     */
    public static List<Path> collectFiles(Path root, List<String> globs, BooleanSupplier cancelled) {
        List<PathMatcher> matchers = globs.stream()
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g))
                .toList();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            var it = walk.iterator();
            while (it.hasNext()) {
                if (cancelled.getAsBoolean()) {
                    break;
                }
                Path path = it.next();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                Path name = path.getFileName();
                if (matchers.stream().noneMatch(m -> m.matches(name))) {
                    continue;
                }
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    if (attrs.size() > MAX_FILE_SIZE) {
                        continue;
                    }
                } catch (IOException e) {
                    continue;
                }
                files.add(path);
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            // unreadable subtree — return what was collected so far
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    /**
     * Searches one file. When {@code bufferOverride} returns non-null text for the
     * file (an open, dirty editor document), that text is searched instead of disk.
     */
    public static FileSearchResult searchFile(Path file, Pattern pattern,
                                              Function<Path, String> bufferOverride) {
        String buffer = bufferOverride != null ? bufferOverride.apply(file) : null;
        if (buffer != null) {
            return matchText(file, buffer, pattern, StandardCharsets.UTF_8, false, true, 0, 0);
        }
        try {
            byte[] head = readHead(file);
            if (EncodingSniffer.isBinary(head)) {
                return error(file, "Binary file skipped");
            }
            EncodingSniffer.Loaded loaded = EncodingSniffer.load(file);
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return matchText(file, loaded.text(), pattern, loaded.charset(), loaded.bom(), false,
                    attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (IOException e) {
            return error(file, "Cannot read file: " + e.getMessage());
        }
    }

    /**
     * Searches all matching files under {@code root}. Files without matches are
     * omitted; files with read errors are kept so the UI can report them.
     */
    public static List<FileSearchResult> search(Path root, TextSearchQuery query,
                                                Function<Path, String> bufferOverride,
                                                IntConsumer onFileDone, BooleanSupplier cancelled) {
        Pattern pattern = compile(query);
        List<Path> files = collectFiles(root, query.globs(), cancelled);
        List<FileSearchResult> results = new ArrayList<>();
        int done = 0;
        for (Path file : files) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            FileSearchResult result = searchFile(file, pattern, bufferOverride);
            if (!result.matches().isEmpty() || result.error() != null) {
                results.add(result);
            }
            done++;
            if (onFileDone != null) {
                onFileDone.accept(done);
            }
        }
        return results;
    }

    // ---------------------------------------------------------------------

    private static FileSearchResult error(Path file, String message) {
        return new FileSearchResult(file, StandardCharsets.UTF_8, false, List.of(),
                new FileFingerprint(0, 0, 0), false, false, message);
    }

    private static FileSearchResult matchText(Path file, String text, Pattern pattern,
                                              Charset charset, boolean bom,
                                              boolean fromBuffer, long mtime, long size) {
        List<TextMatch> matches = new ArrayList<>();
        boolean truncated = false;
        Matcher matcher = pattern.matcher(text);
        // Track line number/start incrementally while walking the matches in order.
        int line = 1;
        int lineStart = 0;
        int scanned = 0;
        int from = 0;
        while (from <= text.length() && matcher.find(from)) {
            int start = matcher.start();
            int end = matcher.end();
            for (int i = scanned; i < start; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    lineStart = i + 1;
                }
            }
            scanned = Math.max(scanned, start);
            int lineEnd = text.indexOf('\n', start);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String lineText = text.substring(lineStart, lineEnd);
            if (lineText.endsWith("\r")) {
                lineText = lineText.substring(0, lineText.length() - 1);
            }
            matches.add(new TextMatch(line, lineStart, start, end, lineText));
            if (matches.size() >= MAX_MATCHES_PER_FILE) {
                truncated = true;
                break;
            }
            from = end > start ? end : start + 1; // always advance past a zero-width match
        }
        return new FileSearchResult(file, charset, bom, List.copyOf(matches),
                new FileFingerprint(mtime, size, text.hashCode()), fromBuffer, truncated, null);
    }

    private static byte[] readHead(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(8192);
        }
    }
}
