package org.fxt.freexmltoolkit.service.ai.tool;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The folders a tool may read from, and the only way a tool turns a path argument into a file.
 *
 * <p>A path is made absolute (a relative one against the first root), normalised, and resolved through
 * symbolic links ({@link Path#toRealPath}) before it is compared with the roots; for a file that does not
 * exist yet the nearest existing parent is resolved, so a linked folder cannot lead outside either. A
 * dangling link is refused. {@code util/PathValidator.isPathWithinDirectory} falls back to the unresolved
 * path for non-existing files and logs the path, so it is not used here: paths are tool arguments and must
 * not reach logs above DEBUG ({@code .claude/rules/ai-integration.md}).
 */
public final class WorkspaceAccess {

    private static final Pattern DECLARED_ENCODING =
            Pattern.compile("^<\\?xml[^>]*?encoding\\s*=\\s*[\"']([A-Za-z0-9._:-]+)[\"']");

    private final List<Path> roots;

    /**
     * @param roots existing folders; the first one is the base of relative paths
     * @throws IllegalArgumentException when the list is empty or a root is not an existing folder
     */
    public WorkspaceAccess(List<Path> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("at least one root folder is required");
        }
        List<Path> real = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                throw new IllegalArgumentException("not a folder: " + root);
            }
            try {
                real.add(root.toRealPath());
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot resolve folder " + root + ": " + e.getMessage(), e);
            }
        }
        this.roots = List.copyOf(real);
    }

    /** The roots as real paths. */
    public List<Path> roots() {
        return roots;
    }

    /**
     * The real path of {@code path}, which must lie inside a root. The file need not exist.
     *
     * @throws ToolException when the path is blank, malformed, a dangling link, or outside every root
     */
    public Path resolve(String path) throws ToolException {
        if (path == null || path.isBlank()) {
            throw new ToolException("a path is required");
        }
        Path candidate;
        try {
            candidate = Path.of(path.strip());
        } catch (InvalidPathException e) {
            throw new ToolException("not a valid path: '" + path + "' (" + e.getReason() + ")");
        }
        if (!candidate.isAbsolute()) {
            candidate = roots.getFirst().resolve(candidate);
        }
        Path real = realPath(candidate.normalize(), path);
        for (Path root : roots) {
            if (real.startsWith(root)) {
                return real;
            }
        }
        throw new ToolException("'" + path + "' is outside the allowed folders " + describeRoots());
    }

    /**
     * Reads a text file inside a root. The encoding comes from a byte order mark, else from the XML
     * declaration, else UTF-8; text that is not valid UTF-8 is read as ISO-8859-1.
     *
     * @throws ToolException when the file is outside the roots, missing, not a regular file, larger than
     *                       {@code maxBytes} or unreadable
     */
    public String read(Path file, long maxBytes) throws ToolException {
        Path real = resolve(file.toString());
        if (!Files.exists(real)) {
            throw new ToolException("file not found: " + file);
        }
        if (!Files.isRegularFile(real)) {
            throw new ToolException("not a file: " + file);
        }
        try {
            long size = Files.size(real);
            if (size > maxBytes) {
                throw new ToolException("file too large: " + file + " has " + size + " bytes, the limit is "
                        + maxBytes + " bytes");
            }
            return decode(Files.readAllBytes(real));
        } catch (IOException e) {
            throw new ToolException("cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    /** "(/a, /b)" for messages. */
    String describeRoots() {
        return roots.stream().map(Path::toString).toList().toString().replace('[', '(').replace(']', ')');
    }

    private Path realPath(Path normalized, String original) throws ToolException {
        Path existing = normalized;
        List<Path> missing = new ArrayList<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            throw new ToolException("'" + original + "' is outside the allowed folders " + describeRoots());
        }
        Path real;
        try {
            real = existing.toRealPath();
        } catch (IOException e) {
            throw new ToolException("cannot resolve '" + original + "' (a broken link?)");
        }
        for (Path name : missing) {
            real = real.resolve(name);
        }
        return real;
    }

    static String decode(byte[] bytes) {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        String head = new String(bytes, 0, Math.min(bytes.length, 200), StandardCharsets.ISO_8859_1);
        Matcher declared = DECLARED_ENCODING.matcher(head);
        if (declared.find()) {
            try {
                Charset charset = Charset.forName(declared.group(1));
                if (!charset.equals(StandardCharsets.UTF_8)) {
                    return new String(bytes, charset);
                }
            } catch (IllegalArgumentException unknownCharset) {
                // fall through to UTF-8
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException notUtf8) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
