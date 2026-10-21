package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.fxt.freexmltoolkit.service.update.InstallSource;
import org.junit.jupiter.api.Test;

class LaunchContextTest {

    private static final Map<String, String> WIN_ENV = Map.of(
            "ProgramFiles", "C:\\Program Files",
            "LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local");

    @Test
    void noLauncherMeansNone() {
        assertEquals(LaunchContext.PACKAGE_NONE,
                LaunchContext.packageKind(false, "Linux", Path.of("/opt/freexmltoolkit"), Map.of()));
    }

    @Test
    void windowsInstallerLocations() {
        assertEquals(LaunchContext.PACKAGE_INSTALLER, LaunchContext.packageKind(true, "Windows 11",
                Path.of("C:\\Program Files\\FreeXmlToolkit"), WIN_ENV));
        assertEquals(LaunchContext.PACKAGE_INSTALLER, LaunchContext.packageKind(true, "Windows 11",
                Path.of("C:\\Users\\u\\AppData\\Local\\Programs\\FreeXmlToolkit"), WIN_ENV));
        assertEquals(LaunchContext.PACKAGE_PORTABLE, LaunchContext.packageKind(true, "Windows 11",
                Path.of("D:\\tools\\FreeXmlToolkit"), WIN_ENV));
    }

    @Test
    void unixInstallerLocations() {
        assertEquals(LaunchContext.PACKAGE_INSTALLER,
                LaunchContext.packageKind(true, "Linux", Path.of("/opt/freexmltoolkit"), Map.of()));
        assertEquals(LaunchContext.PACKAGE_PORTABLE,
                LaunchContext.packageKind(true, "Linux", Path.of("/home/u/FreeXmlToolkit"), Map.of()));
        assertEquals(LaunchContext.PACKAGE_INSTALLER, LaunchContext.packageKind(true, "Mac OS X",
                Path.of("/Applications/FreeXmlToolkit.app"), Map.of()));
        assertEquals(LaunchContext.PACKAGE_PORTABLE, LaunchContext.packageKind(true, "Mac OS X",
                Path.of("/Users/u/Downloads/FreeXmlToolkit.app"), Map.of()));
    }

    @Test
    void settingsSnapshotWithoutPropertiesIsEmpty() {
        assertTrue(LaunchContext.settingsSnapshot(null).isEmpty());
    }

    @Test
    void startupMillisIsPositive() {
        assertTrue(LaunchContext.startupMillis() > 0);
    }

    @Test
    void installSourceUsesQuickDetection() {
        assertEquals("flatpak", LaunchContext.installSource(InstallSource.FLATPAK, null));
        assertEquals("scoop", LaunchContext.installSource(InstallSource.SCOOP, "winget"));
        assertEquals("standalone", LaunchContext.installSource(InstallSource.STANDALONE, null));
        assertEquals("standalone", LaunchContext.installSource(InstallSource.STANDALONE, "homebrew"));
    }

    @Test
    void wingetComesFromThePreviousFullDetection() {
        assertEquals("winget", LaunchContext.installSource(InstallSource.STANDALONE, "winget"));
    }
}
