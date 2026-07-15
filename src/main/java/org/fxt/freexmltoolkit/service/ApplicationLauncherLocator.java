package org.fxt.freexmltoolkit.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

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
     * Gets the application installation directory.
     *
     * <p>Derived from the location of the application JAR (typical jpackage layout is
     * {@code app/lib/FreeXmlToolkit.jar}, so two levels up). Falls back to the current
     * working directory when running from the IDE.
     */
    public static Path getApplicationDirectory() {
        try {
            Path jarPath = Path.of(ApplicationLauncherLocator.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (jarPath.toString().endsWith(".jar")) {
                // Typical structure: app/lib/FreeXmlToolkit.jar -> app
                return jarPath.getParent().getParent();
            }

            return Path.of(System.getProperty("user.dir"));

        } catch (Exception e) {
            logger.warn("Could not determine application directory from JAR location", e);
            return Path.of(System.getProperty("user.dir"));
        }
    }

    /**
     * Gets the path to the platform-specific native launcher of the application.
     * The returned path may not exist (IDE / gradle run mode).
     */
    public static Path getApplicationLauncher() {
        Path appDir = getApplicationDirectory();

        if (isWindows()) {
            return appDir.resolve("FreeXmlToolkit.exe");
        } else if (isMacOS()) {
            // On macOS, the app is in a .app bundle or directly in the folder
            Path macOsLauncher = appDir.resolve("Contents/MacOS/FreeXmlToolkit");
            if (Files.exists(macOsLauncher)) {
                return macOsLauncher;
            }
            return appDir.resolve("bin/FreeXmlToolkit");
        } else {
            return appDir.resolve("bin/FreeXmlToolkit");
        }
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
