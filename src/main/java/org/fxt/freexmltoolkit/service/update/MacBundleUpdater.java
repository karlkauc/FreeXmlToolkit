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
 *
 * <p><b>No process is started from this JVM after the swap.</b> On macOS the JDK starts every
 * process through {@code $JAVA_HOME/lib/jspawnhelper}, which refuses a different Java version
 * ("Incorrect Java version"); after the swap that path holds the <em>new</em> runtime, so with a
 * different JDK build {@code open} (and {@code ditto}) failed although the update was installed
 * (telemetry AL, 2.8.0 → 3.4.0). A small {@code /bin/sh} relaunch helper is therefore started
 * before the swap; it waits for a marker file, refreshes the extras and runs {@code open -n}.
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

    /** Starts an external command without waiting for it. */
    @FunctionalInterface
    public interface Launcher {
        void start(List<String> command) throws IOException;
    }

    /**
     * The relaunch helper: {@code $1} marker base path, {@code $2} bundle, {@code $3}/{@code $4} extras
     * source/target, {@code $5} the {@code open}, {@code $6} the {@code ditto} and {@code $7} the
     * {@code lsregister} program. It waits up to two minutes for {@code $1.go} (swap done) or
     * {@code $1.abort} (swap failed). Before starting the new bundle it registers it again with
     * LaunchServices and touches it, so Finder, Dock and Launchpad show its icon instead of the
     * cached one of the old bundle.
     */
    static final String RELAUNCH_SCRIPT = """
            i=0
            while [ "$i" -lt 1200 ]; do
              if [ -e "$1.go" ]; then
                if [ -d "$3" ] && [ -d "$4" ]; then "$6" "$3" "$4"; fi
                touch "$2" 2>/dev/null
                if [ -x "$7" ]; then "$7" -f "$2" >/dev/null 2>&1; fi
                exec "$5" -n "$2"
              fi
              if [ -e "$1.abort" ]; then exit 0; fi
              sleep 0.1
              i=$((i+1))
            done
            """;

    private static final String OPEN = "/usr/bin/open";
    private static final String DITTO = "/usr/bin/ditto";
    /** LaunchServices' registration tool (also used by the file associations). */
    static final String LSREGISTER = "/System/Library/Frameworks/CoreServices.framework/Frameworks/"
            + "LaunchServices.framework/Support/lsregister";

    private final ProcessRunner runner;
    private final Launcher launcher;
    private final Consumer<String> log;

    /**
     * @param runner   runs {@code ditto} / {@code open} and waits (injectable for tests)
     * @param launcher starts the relaunch helper without waiting (injectable for tests)
     * @param log      receives progress lines for the update debug log
     */
    public MacBundleUpdater(ProcessRunner runner, Launcher launcher, Consumer<String> log) {
        this.runner = runner;
        this.launcher = launcher;
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

    /** Launcher backed by {@link ProcessBuilder}, output discarded, not waited for. */
    public static Launcher systemLauncher() {
        return command -> new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
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
     * @throws IOException with a user-facing message when the update cannot be applied (the
     *                     installed bundle is left intact then), or when the new version is installed
     *                     but could not be started
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

        Path extrasSource = extractedDir.resolve(EXTRAS_NAME);
        Path extrasTarget = parent.resolve(EXTRAS_NAME);
        Path marker = extractedDir.resolve(".relaunch");
        boolean helper = startRelaunchHelper(marker, installedBundle, extrasSource, extrasTarget);

        log.accept("Swapping bundles: " + installedBundle + " -> " + backup);
        try {
            Files.move(installedBundle, backup, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            deleteQuietly(staged);
            signal(marker, "abort");
            throw new IOException("Could not move the installed application aside: " + e.getMessage(), e);
        }
        try {
            Files.move(staged, installedBundle, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.accept("Swap failed, restoring the previous bundle: " + e.getMessage());
            Files.move(backup, installedBundle, StandardCopyOption.ATOMIC_MOVE);
            deleteQuietly(staged);
            signal(marker, "abort");
            throw new IOException("Could not install the new application: " + e.getMessage(), e);
        }
        deleteQuietly(backup);

        if (helper && signal(marker, "go")) {
            log.accept("Relaunch helper started the new bundle: " + installedBundle);
            return;
        }
        // Fallback without helper: works only while the new runtime is the same Java build.
        updateExtras(extrasSource, extrasTarget);
        log.accept("Launching new bundle: " + installedBundle);
        try {
            run(List.of("open", "-n", installedBundle.toString()), "open");
        } catch (IOException e) {
            throw new IOException("The new version is installed but could not be started (" + e.getMessage()
                    + "). Please start FreeXmlToolkit again.", e);
        }
    }

    /** Starts the relaunch helper; {@code false} (logged) when it cannot be started. */
    private boolean startRelaunchHelper(Path marker, Path bundle, Path extrasSource, Path extrasTarget) {
        try {
            Files.deleteIfExists(marker.resolveSibling(marker.getFileName() + ".go"));
            Files.deleteIfExists(marker.resolveSibling(marker.getFileName() + ".abort"));
            launcher.start(relaunchCommand(marker, bundle, extrasSource, extrasTarget, OPEN, DITTO, LSREGISTER));
            log.accept("Relaunch helper waiting for " + marker);
            return true;
        } catch (IOException e) {
            log.accept("Relaunch helper not started: " + e.getMessage());
            return false;
        }
    }

    /** The {@code /bin/sh} command of the relaunch helper (programs passed in for tests). */
    static List<String> relaunchCommand(Path marker, Path bundle, Path extrasSource, Path extrasTarget,
                                        String open, String ditto, String lsregister) {
        return List.of("/bin/sh", "-c", RELAUNCH_SCRIPT, "fxt-relaunch", marker.toString(), bundle.toString(),
                extrasSource.toString(), extrasTarget.toString(), open, ditto, lsregister);
    }

    /** Writes {@code <marker>.<what>}; {@code false} (logged) when that fails. */
    private boolean signal(Path marker, String what) {
        try {
            Files.writeString(marker.resolveSibling(marker.getFileName() + "." + what), what);
            return true;
        } catch (IOException e) {
            log.accept("Could not signal the relaunch helper (" + what + "): " + e.getMessage());
            return false;
        }
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
