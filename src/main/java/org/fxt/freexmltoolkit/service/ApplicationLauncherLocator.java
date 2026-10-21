package org.fxt.freexmltoolkit.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Locates the installed application directory and native launcher of the running
 * FreeXmlToolkit instance. Shared by the auto-update subsystem and the file-association
 * service, so both resolve the same paths.
 *
 * <p>When running from the IDE or {@code ./gradlew run} there is no native launcher;
 * {@link #isInstalled()} then returns {@code false}.
 */
public final class ApplicationLauncherLocator {

    private static final Logger logger = LogManager.getLogger(ApplicationLauncherLocator.class);

    private ApplicationLauncherLocator() {
    }

    /**
     * Gets the application installation directory: the folder holding the native launcher
     * (Windows, Linux) or the {@code .app} bundle (macOS).
     *
     * <p>Derived from the location of the application JAR (see {@link #applicationDirectory(Path)}).
     * Falls back to the current working directory when running from the IDE.
     */
    public static Path getApplicationDirectory() {
        try {
            Path jarPath = Path.of(ApplicationLauncherLocator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (jarPath.toString().endsWith(".jar")) {
                return applicationDirectory(jarPath);
            }

            return Path.of(System.getProperty("user.dir"));

        } catch (Exception e) {
            logger.warn("Could not determine application directory from JAR location", e);
            return Path.of(System.getProperty("user.dir"));
        }
    }

    /**
     * Maps the application JAR to the installation directory of the jpackage layouts:
     * <ul>
     *   <li>Windows: {@code <root>/app/FreeXmlToolkit.jar} → {@code <root>}</li>
     *   <li>Linux: {@code <root>/lib/app/FreeXmlToolkit.jar} → {@code <root>}</li>
     *   <li>macOS: {@code FreeXmlToolkit.app/Contents/app/FreeXmlToolkit.jar} → {@code FreeXmlToolkit.app}</li>
     * </ul>
     * Any other location (e.g. {@code build/libs}) yields the JAR's grandparent, as before.
     *
     * @param jarPath absolute path of the application JAR
     * @return the installation directory
     */
    static Path applicationDirectory(Path jarPath) {
        Path appFolder = jarPath.getParent();
        Path dir = appFolder == null ? null : appFolder.getParent();
        if (dir == null) {
            return jarPath;
        }
        Path above = dir.getParent();
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
        if (above != null && "app".equals(String.valueOf(appFolder.getFileName()))
                && ("lib".equals(name) || "Contents".equals(name))) {
            return above;
        }
        return dir;
    }

    /**
     * Gets the path to the platform-specific native launcher of the application.
     * The returned path may not exist (IDE / gradle run mode).
     */
    public static Path getApplicationLauncher() {
        return applicationLauncher(getApplicationDirectory(), isWindows(), isMacOS(), Files::exists);
    }

    /**
     * Native launcher inside an installation directory.
     *
     * @param appDir  the installation directory ({@link #applicationDirectory(Path)})
     * @param windows running on Windows
     * @param mac     running on macOS
     * @param exists  file-existence check (injectable for tests)
     * @return the launcher path (may not exist)
     */
    static Path applicationLauncher(Path appDir, boolean windows, boolean mac,
                                    Predicate<Path> exists) {
        if (windows) {
            return appDir.resolve("FreeXmlToolkit.exe");
        }
        if (mac) {
            // On macOS the app is a .app bundle, or an unpacked image with bin/
            Path macOsLauncher = appDir.resolve("Contents/MacOS/FreeXmlToolkit");
            if (exists.test(macOsLauncher)) {
                return macOsLauncher;
            }
        }
        return appDir.resolve("bin/FreeXmlToolkit");
    }

    /**
     * Returns true when a native launcher exists, i.e. the application runs from
     * an installed package or app-image rather than from the IDE.
     */
    public static boolean isInstalled() {
        return Files.isExecutable(getApplicationLauncher());
    }

    /**
     * Checks if the current platform is Windows.
     */
    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * Checks if the current platform is macOS.
     */
    public static boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }
}
