package org.fxt.freexmltoolkit.screenshots;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The files the documentation screenshots show. The status bar, tab tooltips, the recent-files list
 * and the Settings page print absolute paths, so nothing may come from the repository checkout (it
 * names the developer's home folder) or from a temporary folder with a random number: generators
 * open copies under one neutral root, {@code fxt.docs.root} (set by the {@code docScreenshots} task,
 * default {@code <tmp>/FreeXmlToolkit-docs}).
 */
public final class DocFiles {

    /** The neutral root every shown file lives under. */
    public static final Path ROOT = Path.of(System.getProperty("fxt.docs.root",
            Path.of(System.getProperty("java.io.tmpdir"), "FreeXmlToolkit-docs").toString()));

    private static final Path REPO_EXAMPLES = Path.of("release/examples");
    private static final Path REPO_GETTING_STARTED = Path.of("src/main/resources/examples/getting-started");
    private static final Map<String, Integer> WORK_DIRS = new HashMap<>();
    private static boolean examplesCopied;

    private DocFiles() {
    }

    /** A copy of {@code release/examples} under the root, made once per JVM (relative references keep working). */
    public static synchronized File examples() {
        Path target = ROOT.resolve("examples");
        if (!examplesCopied) {
            copyTree(REPO_EXAMPLES, target);
            examplesCopied = true;
        }
        return target.toFile();
    }

    /** A copy of a file of the bundled getting-started example ({@code order.xml}, …). */
    public static synchronized Path gettingStarted(String name) {
        Path target = ROOT.resolve("getting-started");
        copyTree(REPO_GETTING_STARTED, target);
        return target.resolve(name);
    }

    /**
     * A copy of a repository file (for example {@code src/test/resources/FundsXML_306.xml}) in the
     * folder {@code test-resources} under the root; files copied one after the other share that
     * folder, so a document finds a schema copied next to it.
     */
    public static synchronized File copyOf(String repoPath) {
        Path source = Path.of(repoPath);
        Path target = ROOT.resolve("test-resources").resolve(source.getFileName().toString());
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + source, e);
        }
        return target.toFile();
    }

    /**
     * An empty folder under the root for files a generator writes itself. Each call returns a new
     * folder ({@code name}, {@code name-2}, …), so two scenes of one generator never share one.
     */
    public static synchronized Path work(String name) {
        int n = WORK_DIRS.merge(name, 1, Integer::sum);
        Path dir = ROOT.resolve(n == 1 ? name : name + "-" + n);
        deleteTree(dir);
        try {
            return Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void copyTree(Path source, Path target) {
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path to = target.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(file, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy " + source + " to " + target, e);
        }
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(p);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not clear " + dir, e);
        }
    }
}
