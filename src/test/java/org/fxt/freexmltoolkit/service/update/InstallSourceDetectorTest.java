package org.fxt.freexmltoolkit.service.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class InstallSourceDetectorTest {

    private final Map<String, String> env = new HashMap<>();
    private final Set<Path> existing = new HashSet<>();
    private final Map<Path, String> files = new HashMap<>();
    private final List<List<String>> commands = new ArrayList<>();
    private int wingetExit = 1;

    private InstallSourceDetector detector(boolean windows, Path appDir) {
        return new InstallSourceDetector(env::get, existing::contains, files::get,
                cmd -> {
                    commands.add(cmd);
                    return wingetExit;
                }, windows, appDir);
    }

    @Test
    void plainInstallIsStandalone() {
        assertEquals(InstallSource.STANDALONE, detector(false, Path.of("/home/u/FreeXmlToolkit/lib")).detect());
        assertTrue(commands.isEmpty(), "winget must not run outside Windows");
    }

    @Test
    void environmentOverrideWins() {
        env.put("FXT_INSTALL_SOURCE", " Homebrew ");
        env.put("FLATPAK_ID", "io.github.karlkauc.FreeXmlToolkit");
        assertEquals(InstallSource.HOMEBREW, detector(false, Path.of("/x")).detect());
    }

    @Test
    void unknownOverrideIsIgnored() {
        env.put("FXT_INSTALL_SOURCE", "apt");
        assertEquals(InstallSource.STANDALONE, detector(false, Path.of("/x")).detect());
    }

    @Test
    void flatpakFromEnvironmentOrInfoFile() {
        env.put("FLATPAK_ID", "io.github.karlkauc.FreeXmlToolkit");
        assertEquals(InstallSource.FLATPAK, detector(false, Path.of("/app/lib")).detect());

        env.clear();
        existing.add(Path.of("/.flatpak-info"));
        assertEquals(InstallSource.FLATPAK, detector(false, Path.of("/app/lib")).detect());
    }

    @Test
    void markerFileInApplicationDirectoryOrParent() {
        Path root = Path.of("/opt/fxt");
        Path appDir = root.resolve("app");
        Path marker = root.resolve(InstallSourceDetector.MARKER_FILE);
        existing.add(marker);
        files.put(marker, "scoop\n");
        assertEquals(InstallSource.SCOOP, detector(false, appDir).detect());
    }

    @Test
    void invalidMarkerFallsThrough() {
        Path appDir = Path.of("/opt/fxt");
        Path marker = appDir.resolve(InstallSourceDetector.MARKER_FILE);
        existing.add(marker);
        files.put(marker, "garbage");
        assertEquals(InstallSource.STANDALONE, detector(false, appDir).detect());
    }

    @Test
    void scoopFromPath() {
        Path appDir = Path.of("C:\\Users\\u\\scoop\\apps\\freexmltoolkit\\2.5.0");
        assertEquals(InstallSource.SCOOP, detector(true, appDir).detect());
        assertTrue(commands.isEmpty(), "Scoop must be detected before winget runs");
    }

    @Test
    void homebrewFromCaskroom() {
        existing.add(Path.of("/opt/homebrew/Caskroom/freexmltoolkit"));
        assertEquals(InstallSource.HOMEBREW,
                detector(false, Path.of("/Applications/FreeXmlToolkit.app/Contents")).detect());
    }

    @Test
    void wingetWhenInstalledUnderProgramFilesAndKnown() {
        env.put("ProgramFiles", "C:\\Program Files");
        wingetExit = 0;
        assertEquals(InstallSource.WINGET, detector(true, Path.of("C:\\Program Files\\FreeXmlToolkit")).detect());
        assertEquals(1, commands.size());
        assertTrue(commands.getFirst().contains(InstallSourceDetector.WINGET_PACKAGE_ID));
    }

    @Test
    void wingetUnknownOrFailingIsStandalone() {
        env.put("ProgramFiles", "C:\\Program Files");
        wingetExit = -1;
        assertEquals(InstallSource.STANDALONE,
                detector(true, Path.of("C:\\Program Files\\FreeXmlToolkit")).detect());
    }

    @Test
    void wingetNotQueriedForPortableLocation() {
        env.put("ProgramFiles", "C:\\Program Files");
        wingetExit = 0;
        assertEquals(InstallSource.STANDALONE, detector(true, Path.of("D:\\tools\\FreeXmlToolkit")).detect());
        assertTrue(commands.isEmpty());
    }

    @Test
    void wingetUnderLocalAppDataPrograms() {
        env.put("LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local");
        wingetExit = 0;
        assertEquals(InstallSource.WINGET,
                detector(true, Path.of("C:\\Users\\u\\AppData\\Local\\Programs\\FreeXmlToolkit")).detect());
    }

    @Test
    void throwingEnvironmentFallsBackToStandalone() {
        InstallSourceDetector d = new InstallSourceDetector(k -> {
            throw new SecurityException("no env");
        }, p -> false, p -> null, c -> 0, true, Path.of("C:\\x"));
        assertEquals(InstallSource.STANDALONE, d.detect());
    }

    @Test
    void managedSourcesHaveUpgradeCommands() {
        for (InstallSource source : InstallSource.values()) {
            assertEquals(source.isManaged(), source.upgradeCommand() != null, source.name());
            assertEquals(source, InstallSource.fromId(source.id()));
        }
        assertTrue(InstallSource.FLATPAK.suppressesUpdateCheck());
        assertFalse(InstallSource.WINGET.suppressesUpdateCheck());
    }
}
