package org.fxt.freexmltoolkit.service.fileassoc;

import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.service.FileAssociationService.RegistrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxAssociationStrategyTest {

    private static final FileTypeDescriptor XML =
            new FileTypeDescriptor("XML", "XML Document", Set.of("xml"), "application/xml");
    private static final FileTypeDescriptor XSD =
            new FileTypeDescriptor("XSD", "XML Schema Definition", Set.of("xsd"), "application/xml");
    private static final FileTypeDescriptor SCHEMATRON =
            new FileTypeDescriptor("SCHEMATRON", "Schematron Schema", Set.of("sch", "schematron"),
                    LinuxAssociationStrategy.SCHEMATRON_MIME);
    private static final FileTypeDescriptor JSON =
            new FileTypeDescriptor("JSON", "JSON Document", Set.of("json"), "application/json");

    @TempDir
    Path userHome;

    private Path launcher;
    private RecordingCommandRunner runner;
    private InMemoryKeyValueStore store;
    private LinuxAssociationStrategy strategy;

    @BeforeEach
    void setUp() {
        launcher = userHome.resolve("app/bin/FreeXmlToolkit");
        runner = new RecordingCommandRunner();
        store = new InMemoryKeyValueStore();
        strategy = new LinuxAssociationStrategy(runner, launcher, userHome, store);
    }

    @Test
    void desktopEntryDeclaresLauncherAndAllMimeTypes() {
        String entry = LinuxAssociationStrategy.buildDesktopEntry(launcher, null,
                List.of(XML, SCHEMATRON, JSON));
        assertTrue(entry.contains("[Desktop Entry]"));
        assertTrue(entry.contains("Exec=\"" + launcher + "\" %f"));
        assertTrue(entry.contains(
                "MimeType=application/xml;application/x-schematron+xml;application/json;"));
        assertFalse(entry.contains("Icon="));
        assertTrue(entry.contains("Terminal=false"));
    }

    @Test
    void desktopEntryIncludesIconWhenPresent() {
        Path icon = userHome.resolve("app/lib/freexmltoolkit.png");
        String entry = LinuxAssociationStrategy.buildDesktopEntry(launcher, icon, List.of(XML));
        assertTrue(entry.contains("Icon=" + icon));
    }

    @Test
    void schematronMimeXmlDeclaresGlobsAndSubclass() {
        String xml = LinuxAssociationStrategy.buildSchematronMimeXml();
        assertTrue(xml.contains("application/x-schematron+xml"));
        assertTrue(xml.contains("<glob pattern=\"*.sch\"/>"));
        assertTrue(xml.contains("<glob pattern=\"*.schematron\"/>"));
        assertTrue(xml.contains("<sub-class-of type=\"application/xml\"/>"));
    }

    @Test
    void registerWritesDesktopFileAndSetsDefaults() {
        FileAssociationResult result = strategy.register(List.of(XML, XSD), List.of(XML, XSD, JSON));
        assertTrue(result.success());
        assertTrue(Files.exists(userHome.resolve(".local/share/applications/freexmltoolkit.desktop")));
        // xml + xsd share application/xml — default must be set exactly once
        long defaults = runner.flatCommands().stream()
                .filter(c -> c.equals("xdg-mime default freexmltoolkit.desktop application/xml"))
                .count();
        assertEquals(1, defaults);
        assertFalse(runner.flatCommands().stream()
                .anyMatch(c -> c.contains("default") && c.contains("application/json")));
    }

    @Test
    void registerInstallsSchematronMimeOnlyWhenSelected() {
        strategy.register(List.of(XML), List.of(XML, SCHEMATRON));
        assertFalse(runner.flatCommands().stream().anyMatch(c -> c.contains("xdg-mime install")));

        runner.commands.clear();
        strategy.register(List.of(SCHEMATRON), List.of(XML, SCHEMATRON));
        assertTrue(runner.flatCommands().stream().anyMatch(c ->
                c.startsWith("xdg-mime install --mode user")
                        && c.endsWith(LinuxAssociationStrategy.MIME_PACKAGE_FILE_NAME)));
    }

    @Test
    void registerRecordsPreviousDefault() {
        runner.responder = command -> {
            if (command.contains("query")) {
                return new CommandRunner.CommandResult(0, "org.gnome.gedit.desktop\n", "");
            }
            return new CommandRunner.CommandResult(0, "", "");
        };
        strategy.register(List.of(XML), List.of(XML));
        assertEquals("org.gnome.gedit.desktop",
                store.values.get(LinuxAssociationStrategy.PREVIOUS_DEFAULT_KEY_PREFIX + "application/xml"));
    }

    @Test
    void registerAggregatesXdgMimeFailures() {
        runner.responder = command -> command.contains("default")
                ? new CommandRunner.CommandResult(2, "", "xdg-mime: bad mimetype")
                : new CommandRunner.CommandResult(0, "", "");
        FileAssociationResult result = strategy.register(List.of(XML), List.of(XML));
        assertFalse(result.success());
        assertTrue(result.errors().getFirst().contains("application/xml"));
    }

    @Test
    void unregisterRestoresRecordedPreviousDefault() {
        store.set(LinuxAssociationStrategy.PREVIOUS_DEFAULT_KEY_PREFIX + "application/xml",
                "org.gnome.gedit.desktop");
        FileAssociationResult result = strategy.unregister(List.of(XML), false);
        assertTrue(result.success());
        assertTrue(runner.flatCommands().contains(
                "xdg-mime default org.gnome.gedit.desktop application/xml"));
    }

    @Test
    void unregisterWithoutPreviousRewritesMimeappsList() throws IOException {
        Path mimeapps = userHome.resolve(".config/mimeapps.list");
        Files.createDirectories(mimeapps.getParent());
        Files.writeString(mimeapps, """
                [Default Applications]
                application/xml=freexmltoolkit.desktop;
                text/plain=org.gnome.gedit.desktop;

                [Added Associations]
                application/xml=org.gnome.gedit.desktop;freexmltoolkit.desktop;
                """);
        FileAssociationResult result = strategy.unregister(List.of(XML), false);
        assertTrue(result.success());
        String rewritten = Files.readString(mimeapps);
        assertFalse(rewritten.contains("application/xml=freexmltoolkit.desktop"));
        assertTrue(rewritten.contains("text/plain=org.gnome.gedit.desktop;"));
        assertTrue(rewritten.contains("application/xml=org.gnome.gedit.desktop;"));
    }

    @Test
    void unregisterAllTypesRemovesDesktopFile() throws IOException {
        Path desktopFile = userHome.resolve(".local/share/applications/freexmltoolkit.desktop");
        Files.createDirectories(desktopFile.getParent());
        Files.writeString(desktopFile, "[Desktop Entry]\n");
        strategy.unregister(List.of(XML, XSD, SCHEMATRON, JSON), true);
        assertFalse(Files.exists(desktopFile));
    }

    @Test
    void removeDefaultsDropsLineWhenNoHandlerRemains() {
        String content = """
                [Default Applications]
                application/xml=freexmltoolkit.desktop;
                application/json=code.desktop;freexmltoolkit.desktop;
                """;
        String rewritten = LinuxAssociationStrategy.removeDefaults(content,
                Set.of("application/xml", "application/json"), "freexmltoolkit.desktop");
        assertFalse(rewritten.contains("application/xml="));
        assertTrue(rewritten.contains("application/json=code.desktop;"));
    }

    @Test
    void removeDefaultsLeavesUnrelatedSectionsUntouched() {
        String content = """
                [Removed Associations]
                application/xml=freexmltoolkit.desktop;
                """;
        String rewritten = LinuxAssociationStrategy.removeDefaults(content,
                Set.of("application/xml"), "freexmltoolkit.desktop");
        assertTrue(rewritten.contains("application/xml=freexmltoolkit.desktop;"));
    }

    @Test
    void stateReflectsXdgMimeQuery() {
        runner.responder = command -> new CommandRunner.CommandResult(0, "freexmltoolkit.desktop\n", "");
        assertEquals(RegistrationState.DEFAULT, strategy.state(XML));

        runner.responder = command -> new CommandRunner.CommandResult(0, "org.gnome.gedit.desktop\n", "");
        assertEquals(RegistrationState.NOT_REGISTERED, strategy.state(XML));
    }
}
