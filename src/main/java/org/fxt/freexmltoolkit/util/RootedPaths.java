package org.fxt.freexmltoolkit.util;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Containment checks against a set of root folders that cannot be fooled by {@code ..} segments or symbolic
 * links: the path is normalised and resolved with {@link Path#toRealPath}; for a path that does not exist yet
 * the nearest existing parent is resolved and the missing names are appended, so a linked folder cannot lead
 * outside either. A dangling link counts as outside. Nothing is logged — callers decide what to report.
 *
 * <p>Unlike {@link PathValidator#isPathWithinDirectory(Path, Path)}, which compares the unresolved path when
 * the file does not exist, this class always resolves links.
 */
public final class RootedPaths {

    private RootedPaths() {
    }

    /**
     * The real path of {@code path} (absolute; normalised here), or empty when it cannot be resolved (a
     * dangling link, an unreadable parent).
     */
    public static Optional<Path> realPath(Path path) {
        Path existing = path.toAbsolutePath().normalize();
        List<Path> missing = new ArrayList<>();
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            missing.addFirst(existing.getFileName());
            existing = existing.getParent();
        }
        if (existing == null) {
            return Optional.empty();
        }
        Path real;
        try {
            real = existing.toRealPath();
        } catch (IOException e) {
            return Optional.empty();
        }
        for (Path name : missing) {
            real = real.resolve(name);
        }
        return Optional.of(real);
    }

    /**
     * The real path of {@code path} when it lies inside one of {@code realRoots}.
     *
     * @param realRoots roots that are already real paths ({@link Path#toRealPath})
     */
    public static Optional<Path> within(Path path, List<Path> realRoots) {
        return realPath(path).filter(real -> realRoots.stream().anyMatch(real::startsWith));
    }

    /**
     * Whether {@code uri} is a {@code file:} URI inside one of {@code realRoots}. Any other scheme, an opaque
     * or malformed URI is outside. A query part (as in {@code collection('dir/?select=*.xml')}) is ignored.
     */
    public static boolean isFileUriWithin(URI uri, List<Path> realRoots) {
        if (uri == null || !"file".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque()) {
            return false;
        }
        try {
            URI withoutQuery = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
            return within(Path.of(withoutQuery), realRoots).isPresent();
        } catch (Exception malformed) {
            return false;
        }
    }

    /** {@code paths} as real paths. @throws IOException when one does not exist */
    public static List<Path> toRealPaths(List<Path> paths) throws IOException {
        List<Path> real = new ArrayList<>();
        for (Path path : paths) {
            real.add(path.toRealPath());
        }
        return List.copyOf(real);
    }
}
