package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Installation directory and launcher for the jpackage layouts of each platform.
 */
class ApplicationLauncherLocatorTest {

    @Test
    void windowsImageRootHoldsTheJarInApp() {
        Path root = Path.of("C:/Program Files/FreeXmlToolkit");
        Path dir = ApplicationLauncherLocator.applicationDirectory(root.resolve("app/FreeXmlToolkit.jar"));
        assertEquals(root, dir);
        assertEquals(root.resolve("FreeXmlToolkit.exe"),
                ApplicationLauncherLocator.applicationLauncher(dir, true, false, p -> true));
    }

    @Test
    void linuxImageRootHoldsTheJarInLibApp() {
        Path root = Path.of("/opt/freexmltoolkit");
        Path dir = ApplicationLauncherLocator.applicationDirectory(root.resolve("lib/app/FreeXmlToolkit.jar"));
        assertEquals(root, dir);
        assertEquals(root.resolve("bin/FreeXmlToolkit"),
                ApplicationLauncherLocator.applicationLauncher(dir, false, false, p -> true));
    }

    @Test
    void macBundleIsTheInstallationDirectory() {
        Path bundle = Path.of("/Applications/FreeXmlToolkit.app");
        Path dir = ApplicationLauncherLocator.applicationDirectory(
                bundle.resolve("Contents/app/FreeXmlToolkit.jar"));
        assertEquals(bundle, dir);
        assertEquals(bundle.resolve("Contents/MacOS/FreeXmlToolkit"),
                ApplicationLauncherLocator.applicationLauncher(dir, false, true, p -> true));
    }

    @Test
    void macWithoutBundleFallsBackToBin() {
        Path dir = Path.of("/Users/u/FreeXmlToolkit");
        assertEquals(dir.resolve("bin/FreeXmlToolkit"),
                ApplicationLauncherLocator.applicationLauncher(dir, false, true, p -> false));
    }

    @Test
    void otherJarLocationsKeepTheGrandparent() {
        Path jar = Path.of("/home/u/FreeXmlToolkit/build/libs/FreeXmlToolkit.jar");
        assertEquals(Path.of("/home/u/FreeXmlToolkit/build"), ApplicationLauncherLocator.applicationDirectory(jar));
        // an "app" folder that is not inside lib/ or Contents/ is not a jpackage layout
        assertEquals(Path.of("/srv/tools"),
                ApplicationLauncherLocator.applicationDirectory(Path.of("/srv/tools/app/FreeXmlToolkit.jar")));
    }
}
