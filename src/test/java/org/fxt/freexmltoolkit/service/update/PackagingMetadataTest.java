package org.fxt.freexmltoolkit.service.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.util.VersionUtil;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Guards the package-manager metadata in {@code packaging/} against drift from the app.
 */
class PackagingMetadataTest {

    private static final String APP_ID = "io.github.karlkauc.FreeXmlToolkit";
    private static final Path SHARE = Path.of("packaging/linux/share");

    @Test
    void metainfoListsTheCurrentVersion() throws IOException {
        String version = VersionUtil.getVersion();
        Assumptions.assumeTrue(version.matches("\\d+\\.\\d+\\.\\d+"), "no release version: " + version);
        String metainfo = Files.readString(SHARE.resolve("metainfo/" + APP_ID + ".metainfo.xml"));
        assertTrue(metainfo.contains("<release version=\"" + version + "\""),
                "Add a <release version=\"" + version + "\"> entry to the AppStream metainfo "
                        + "(packaging/linux/share/metainfo) — Flathub shows it as the release history");
    }

    @Test
    void desktopEntryMatchesAppId() throws IOException {
        String desktop = Files.readString(SHARE.resolve("applications/" + APP_ID + ".desktop"));
        assertTrue(desktop.contains("Icon=" + APP_ID));
        assertTrue(Files.exists(SHARE.resolve("icons/hicolor/256x256/apps/" + APP_ID + ".png")));
    }

    @Test
    void upgradeCommandsMatchManifestIds() throws IOException {
        assertTrue(InstallSource.FLATPAK.upgradeCommand().endsWith(APP_ID));
        String flatpak = Files.readString(Path.of("packaging/flatpak/" + APP_ID + ".yml"));
        assertTrue(flatpak.contains("id: " + APP_ID));
        assertTrue(InstallSource.WINGET.upgradeCommand().contains(InstallSourceDetector.WINGET_PACKAGE_ID));
        String workflow = Files.readString(Path.of(".github/workflows/build-packages-on-new-release.yml"));
        assertTrue(workflow.contains("identifier: " + InstallSourceDetector.WINGET_PACKAGE_ID));
        String scoop = Files.readString(Path.of("packaging/scoop/freexmltoolkit.json"));
        assertTrue(scoop.contains(InstallSourceDetector.MARKER_FILE) && scoop.contains("'scoop'"),
                "Scoop post_install must write the install-source marker");
    }
}
