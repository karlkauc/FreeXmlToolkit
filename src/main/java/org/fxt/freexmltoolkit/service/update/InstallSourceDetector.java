package org.fxt.freexmltoolkit.service.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ApplicationLauncherLocator;

/**
 * Determines the {@link InstallSource} of the running application.
 *
 * <p>Rules, in order (the first match wins):
 * <ol>
 *   <li>{@code FXT_INSTALL_SOURCE} environment variable (explicit override; set by the
 *       Flatpak manifest, useful for testing)</li>
 *   <li>Flatpak sandbox: {@code FLATPAK_ID} is set or {@code /.flatpak-info} exists</li>
 *   <li>Marker file {@value #MARKER_FILE} in the application directory (written by the
 *       Scoop manifest's {@code post_install})</li>
 *   <li>Scoop: the application directory lies below {@code scoop/apps/}</li>
 *   <li>Homebrew: a {@code Caskroom/freexmltoolkit} directory exists</li>
 *   <li>winget (Windows only, installer-based install location): {@code winget list} knows
 *       the package. This starts a process, so detection must not run on the FX thread.</li>
 * </ol>
 * Anything else is {@link InstallSource#STANDALONE}. Detection never throws.
 */
public final class InstallSourceDetector {

    private static final Logger logger = LogManager.getLogger(InstallSourceDetector.class);

    /** Name of the marker file a package manager may drop into the application directory. */
    public static final String MARKER_FILE = ".fxt-install-source";
    /** Environment variable that overrides detection. */
    public static final String ENV_OVERRIDE = "FXT_INSTALL_SOURCE";
    /** winget package identifier. */
    public static final String WINGET_PACKAGE_ID = "KarlKauc.FreeXmlToolkit";

    static final List<Path> CASKROOM_DIRS = List.of(
            Path.of("/opt/homebrew/Caskroom/freexmltoolkit"),
            Path.of("/usr/local/Caskroom/freexmltoolkit"));

    private static final long WINGET_TIMEOUT_SECONDS = 5;

    /** Runs an external command and returns its exit code (or -1 on failure/timeout). */
    @FunctionalInterface
    public interface CommandRunner {
        int run(List<String> command);
    }

    private static volatile InstallSource cached;

    private final Function<String, String> env;
    private final Predicate<Path> exists;
    private final Function<Path, String> readFile;
    private final CommandRunner commandRunner;
    private final boolean windows;
    private final Path applicationDirectory;

    /** Detector for the real environment of this process. */
    public InstallSourceDetector() {
        this(System::getenv, Files::exists, InstallSourceDetector::readQuietly,
                InstallSourceDetector::runProcess, ApplicationLauncherLocator.isWindows(),
                ApplicationLauncherLocator.getApplicationDirectory());
    }

    /** Fully injectable constructor (tests). */
    public InstallSourceDetector(Function<String, String> env, Predicate<Path> exists,
                                 Function<Path, String> readFile, CommandRunner commandRunner,
                                 boolean windows, Path applicationDirectory) {
        this.env = env;
        this.exists = exists;
        this.readFile = readFile;
        this.commandRunner = commandRunner;
        this.windows = windows;
        this.applicationDirectory = applicationDirectory;
    }

    /**
     * Returns the install source of this process, detected once and cached.
     * May start a short-lived {@code winget} process on Windows — call off the FX thread.
     */
    public static InstallSource current() {
        InstallSource result = cached;
        if (result == null) {
            synchronized (InstallSourceDetector.class) {
                result = cached;
                if (result == null) {
                    result = new InstallSourceDetector().detect();
                    logger.info("Install source: {}", result.id());
                    cached = result;
                }
            }
        }
        return result;
    }

    /**
     * FX-thread-safe variant of {@link #current()}: returns the cached result, or — when
     * detection has not run yet — the result of all rules except the winget process call.
     */
    public static InstallSource peek() {
        InstallSource result = cached;
        return result != null ? result : new InstallSourceDetector().detectWithoutProcess();
    }

    /**
     * Cheap check (environment and one file probe, no process) whether the app runs
     * inside a Flatpak sandbox; safe on any thread.
     */
    public static boolean isFlatpakSandbox() {
        return new InstallSourceDetector().detectWithoutProcess() == InstallSource.FLATPAK;
    }

    /** Runs all rules. Never throws; falls back to {@link InstallSource#STANDALONE}. */
    public InstallSource detect() {
        try {
            InstallSource source = detectWithoutProcess();
            if (source != InstallSource.STANDALONE) {
                return source;
            }
            if (windows && isInstallerLocation() && wingetKnowsPackage()) {
                return InstallSource.WINGET;
            }
        } catch (RuntimeException e) {
            logger.debug("Install source detection failed: {}", e.toString());
        }
        return InstallSource.STANDALONE;
    }

    /** All rules except the winget process call. */
    InstallSource detectWithoutProcess() {
        try {
            InstallSource override = InstallSource.fromId(env.apply(ENV_OVERRIDE));
            if (override != null) {
                return override;
            }
            String flatpakId = env.apply("FLATPAK_ID");
            if ((flatpakId != null && !flatpakId.isBlank()) || exists.test(Path.of("/.flatpak-info"))) {
                return InstallSource.FLATPAK;
            }
            if (applicationDirectory != null) {
                InstallSource marker = readMarker();
                if (marker != null) {
                    return marker;
                }
                String dir = applicationDirectory.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (dir.contains("/scoop/apps/")) {
                    return InstallSource.SCOOP;
                }
            }
            for (Path caskroom : CASKROOM_DIRS) {
                if (exists.test(caskroom)) {
                    return InstallSource.HOMEBREW;
                }
            }
        } catch (RuntimeException e) {
            logger.debug("Install source detection failed: {}", e.toString());
        }
        return InstallSource.STANDALONE;
    }

    private InstallSource readMarker() {
        // The marker sits in the app-image root; check the parent too because the computed
        // application directory differs by one level between platform layouts.
        for (Path dir : new Path[]{applicationDirectory, applicationDirectory.getParent()}) {
            if (dir == null) {
                continue;
            }
            Path marker = dir.resolve(MARKER_FILE);
            if (exists.test(marker)) {
                InstallSource source = InstallSource.fromId(readFile.apply(marker));
                if (source != null) {
                    return source;
                }
            }
        }
        return null;
    }

    /** True when the app lives where the MSI/EXE installer puts it. */
    private boolean isInstallerLocation() {
        if (applicationDirectory == null) {
            return false;
        }
        String dir = applicationDirectory.toString().toLowerCase(Locale.ROOT);
        for (String root : new String[]{env.apply("ProgramFiles"), env.apply("ProgramW6432"),
                env.apply("LOCALAPPDATA") == null ? null : env.apply("LOCALAPPDATA") + "\\Programs"}) {
            if (root != null && !root.isBlank() && dir.startsWith(root.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean wingetKnowsPackage() {
        int exit = commandRunner.run(List.of("winget", "list", "--id", WINGET_PACKAGE_ID, "--exact",
                "--accept-source-agreements", "--disable-interactivity"));
        return exit == 0;
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static int runProcess(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(WINGET_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }
}
