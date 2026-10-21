package org.fxt.freexmltoolkit.service.update;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;

/**
 * Applies a downloaded macOS app-image update by swapping the whole {@code .app} bundle.
 *
 * <p>The macOS update ZIP holds the signed {@code FreeXmlToolkit.app} plus a plain
 * {@code FreeXmlToolkit/} folder with extras (examples, {@code log4j2.xml}) that must
 * live <em>next to</em> the bundle. Copying files into a signed bundle breaks its code
 * signature, so the bundle is never patched in place:
 * <ol>
 *   <li>the ZIP is extracted with {@code ditto} (keeps the runtime's symlinks, modes and
 *       extended attributes, which {@link java.util.zip.ZipInputStream} cannot),</li>
 *   <li>the new bundle is staged next to the installed one (same volume, so the swap is
 *       two renames),</li>
 *   <li>the installed bundle is moved aside, the staged one moved into place — if that
 *       second move fails the old bundle is restored,</li>
 *   <li>the old bundle is deleted (the running process keeps its open files) and the new
 *       one is started with {@code open -n}.</li>
 * </ol>
 * The extras folder is only refreshed when the user keeps one next to the bundle.
 */
public final class MacBundleUpdater {

    /** Bundle name inside the macOS update ZIP. */
    public static final String BUNDLE_NAME = "FreeXmlToolkit.app";
    /** Extras folder inside the macOS update ZIP, shipped next to the bundle. */
    public static final String EXTRAS_NAME = "FreeXmlToolkit";

    /** Runs an external command and returns its exit code. */
    @FunctionalInterface
    public interface ProcessRunner {
        int run(List<String> command) throws IOException, InterruptedException;
    }

    private final ProcessRunner runner;
    private final Consumer<String> log;

    /**
     * @param runner runs {@code ditto} / {@code open} (injectable for tests)
     * @param log    receives progress lines for the update debug log
     */
    public MacBundleUpdater(ProcessRunner runner, Consumer<String> log) {
        this.runner = runner;
        this.log = log;
    }

    /** Runner backed by {@link ProcessBuilder}, output discarded. */
    public static ProcessRunner systemRunner() {
        return command -> new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
                .waitFor();
    }

    /**
     * Extracts the update ZIP with {@code ditto}.
     *
     * @param zipFile      the downloaded update
     * @param extractedDir empty target directory
     * @throws IOException when {@code ditto} fails
     */
    public void extract(Path zipFile, Path extractedDir) throws IOException {
        run(List.of("ditto", "-x", "-k", zipFile.toString(), extractedDir.toString()), "ditto -x");
    }

    /**
     * Installs the extracted bundle over the running one and starts it.
     *
     * @param extractedDir directory the update ZIP was extracted to
     * @param installedBundle the running application's {@code .app} bundle
     * @throws IOException with a user-facing message when the update cannot be applied;
     *                     the installed bundle is left intact in that case
     */
    public void install(Path extractedDir, Path installedBundle) throws IOException {
        Path newBundle = extractedDir.resolve(BUNDLE_NAME);
        if (!Files.isDirectory(newBundle)) {
            throw new IOException("The update does not contain " + BUNDLE_NAME);
        }
        if (installedBundle == null || installedBundle.getFileName() == null
                || !installedBundle.getFileName().toString().endsWith(".app")
                || !Files.isDirectory(installedBundle)) {
            throw new IOException("The running application is not an .app bundle: " + installedBundle);
        }
        Path parent = installedBundle.toAbsolutePath().getParent();
        if (parent == null || !isWritable(parent)) {
            throw new IOException("No write access to " + parent
                    + ". Please download the new version and replace the application manually.");
        }

        String suffix = "-" + ProcessHandle.current().pid() + "-" + System.currentTimeMillis();
        String name = installedBundle.getFileName().toString();
        Path staged = parent.resolve("." + name + ".new" + suffix);
        Path backup = parent.resolve("." + name + ".old" + suffix);

        log.accept("Staging new bundle: " + newBundle + " -> " + staged);
        try {
            run(List.of("ditto", newBundle.toString(), staged.toString()), "ditto (stage)");
        } catch (IOException e) {
            deleteQuietly(staged);
            throw e;
        }

        log.accept("Swapping bundles: " + installedBundle + " -> " + backup);
        try {
            Files.move(installedBundle, backup, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(staged);
            throw new IOException("Could not move the installed application aside: " + e.getMessage(), e);
        }
        try {
            Files.move(staged, installedBundle, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.accept("Swap failed, restoring the previous bundle: " + e.getMessage());
            Files.move(backup, installedBundle, StandardCopyOption.ATOMIC_MOVE);
            deleteQuietly(staged);
            throw new IOException("Could not install the new application: " + e.getMessage(), e);
        }
        deleteQuietly(backup);

        updateExtras(extractedDir.resolve(EXTRAS_NAME), parent.resolve(EXTRAS_NAME));

        log.accept("Launching new bundle: " + installedBundle);
        run(List.of("open", "-n", installedBundle.toString()), "open");
    }

    /** Refreshes the extras folder next to the bundle, but only when the user keeps one there. */
    private void updateExtras(Path source, Path target) {
        if (!Files.isDirectory(source) || !Files.isDirectory(target)) {
            return;
        }
        try {
            log.accept("Updating extras: " + source + " -> " + target);
            run(List.of("ditto", source.toString(), target.toString()), "ditto (extras)");
        } catch (IOException e) {
            // the extras are sample files; a failure must not fail the update itself
            log.accept("Extras not updated: " + e.getMessage());
        }
    }

    private void run(List<String> command, String what) throws IOException {
        int exit;
        try {
            exit = runner.run(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(what + " was interrupted", e);
        }
        if (exit != 0) {
            throw new IOException(what + " failed with exit code " + exit);
        }
    }

    /** Probe-file check; {@link Files#isWritable(Path)} ignores ACLs on some volumes. */
    static boolean isWritable(Path dir) {
        Path probe = dir.resolve(".fxt-write-probe-" + ProcessHandle.current().pid());
        try {
            Files.writeString(probe, "");
            Files.delete(probe);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Deletes a directory tree without following symlinks; failures are ignored. */
    static void deleteQuietly(Path root) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // leftovers are hidden dot-folders next to the bundle; nothing else depends on them
        }
    }
}
