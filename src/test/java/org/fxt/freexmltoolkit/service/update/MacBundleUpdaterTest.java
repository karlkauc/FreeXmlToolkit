package org.fxt.freexmltoolkit.service.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bundle swap of the macOS updater, with {@code ditto}/{@code open} replaced by a fake runner
 * ({@code ditto src dst} = recursive copy), so it runs on every platform.
 */
class MacBundleUpdaterTest {

    @TempDir
    Path tmp;

    private Path applications;
    private Path installed;
    private Path extracted;
    private final List<List<String>> commands = new ArrayList<>();
    private String failOn;

    @BeforeEach
    void setUp() throws IOException {
        applications = Files.createDirectories(tmp.resolve("Applications"));
        installed = bundle(applications.resolve("FreeXmlToolkit.app"), "old");
        extracted = Files.createDirectories(tmp.resolve("extracted"));
        bundle(extracted.resolve(MacBundleUpdater.BUNDLE_NAME), "new");
        Files.createDirectories(extracted.resolve("FreeXmlToolkit/examples"));
        Files.writeString(extracted.resolve("FreeXmlToolkit/examples/sample.xml"), "<new/>");
    }

    private static Path bundle(Path dir, String version) throws IOException {
        Files.createDirectories(dir.resolve("Contents/MacOS"));
        Files.createDirectories(dir.resolve("Contents/app"));
        Files.writeString(dir.resolve("Contents/MacOS/FreeXmlToolkit"), version);
        Files.writeString(dir.resolve("Contents/app/FreeXmlToolkit.jar"), version);
        return dir;
    }

    private MacBundleUpdater updater() {
        return new MacBundleUpdater(command -> {
            commands.add(command);
            if (failOn != null && String.join(" ", command).contains(failOn)) {
                return 1;
            }
            if (command.getFirst().equals("ditto") && command.size() == 3) {
                copyTree(Path.of(command.get(1)), Path.of(command.get(2)));
            }
            return 0;
        }, msg -> { });
    }

    @Test
    void swapsTheWholeBundleAndStartsIt() throws IOException {
        updater().install(extracted, installed);

        assertEquals("new", Files.readString(installed.resolve("Contents/app/FreeXmlToolkit.jar")));
        assertEquals(List.of("open", "-n", installed.toString()), commands.getLast());
        // no staged copy or backup left next to the bundle
        assertEquals(List.of("FreeXmlToolkit.app"), names(applications));
    }

    @Test
    void filesOnlyInTheOldBundleDoNotSurvive() throws IOException {
        Files.writeString(installed.resolve("Contents/app/removed-in-new-version.jar"), "x");
        updater().install(extracted, installed);
        assertFalse(Files.exists(installed.resolve("Contents/app/removed-in-new-version.jar")));
    }

    @Test
    void extrasAreRefreshedOnlyWhenKeptNextToTheBundle() throws IOException {
        updater().install(extracted, installed);
        assertFalse(Files.exists(applications.resolve("FreeXmlToolkit")), "no extras folder is created");

        Files.createDirectories(applications.resolve("FreeXmlToolkit"));
        bundle(extracted.resolve(MacBundleUpdater.BUNDLE_NAME), "new");
        updater().install(extracted, installed);
        assertEquals("<new/>", Files.readString(applications.resolve("FreeXmlToolkit/examples/sample.xml")));
    }

    @Test
    void updateWithoutBundleLeavesTheInstallationAlone() throws IOException {
        Path empty = Files.createDirectories(tmp.resolve("empty"));
        IOException e = assertThrows(IOException.class, () -> updater().install(empty, installed));
        assertTrue(e.getMessage().contains(MacBundleUpdater.BUNDLE_NAME));
        assertEquals("old", Files.readString(installed.resolve("Contents/app/FreeXmlToolkit.jar")));
        assertTrue(commands.isEmpty());
    }

    @Test
    void refusesAnInstallationThatIsNoBundle() throws IOException {
        Path plain = Files.createDirectories(tmp.resolve("FreeXmlToolkit"));
        assertThrows(IOException.class, () -> updater().install(extracted, plain));
        assertTrue(commands.isEmpty());
    }

    @Test
    void failedStagingKeepsTheOldBundleAndCleansUp() throws IOException {
        failOn = ".new-";
        assertThrows(IOException.class, () -> updater().install(extracted, installed));
        assertEquals("old", Files.readString(installed.resolve("Contents/app/FreeXmlToolkit.jar")));
        assertEquals(List.of("FreeXmlToolkit.app"), names(applications));
        assertFalse(commands.stream().anyMatch(c -> c.getFirst().equals("open")));
    }

    @Test
    void extractUsesDittoAndReportsItsFailure() {
        failOn = "-x";
        assertThrows(IOException.class, () -> updater().extract(tmp.resolve("u.zip"), extracted));
        assertEquals(List.of("ditto", "-x", "-k", tmp.resolve("u.zip").toString(), extracted.toString()),
                commands.getFirst());
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static void copyTree(Path source, Path target) {
        try (Stream<Path> s = Files.walk(source)) {
            for (Path p : s.toList()) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
