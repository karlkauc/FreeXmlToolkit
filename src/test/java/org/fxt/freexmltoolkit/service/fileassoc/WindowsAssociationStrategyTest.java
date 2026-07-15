package org.fxt.freexmltoolkit.service.fileassoc;

import org.fxt.freexmltoolkit.domain.FileAssociationResult;
import org.fxt.freexmltoolkit.service.FileAssociationService.RegistrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsAssociationStrategyTest {

    private static final Path LAUNCHER = Path.of("C:\\Program Files\\FreeXmlToolkit\\FreeXmlToolkit.exe");
    private static final FileTypeDescriptor XML =
            new FileTypeDescriptor("XML", "XML Document", Set.of("xml"), "application/xml");
    private static final FileTypeDescriptor XSLT =
            new FileTypeDescriptor("XSLT", "XSLT Stylesheet", Set.of("xsl", "xslt"), "application/xslt+xml");

    private RecordingCommandRunner runner;
    private WindowsAssociationStrategy strategy;

    @BeforeEach
    void setUp() {
        runner = new RecordingCommandRunner();
        strategy = new WindowsAssociationStrategy(runner, LAUNCHER);
    }

    @Test
    void registerCommandsContainProgIdIconOpenCommandAndCapabilities() {
        List<List<String>> commands = strategy.buildRegisterCommands(List.of(XML));
        List<String> flat = commands.stream().map(c -> String.join(" ", c)).toList();

        assertTrue(flat.stream().anyMatch(c ->
                c.contains("HKCU\\Software\\Classes\\FreeXmlToolkit.xml")
                        && c.contains("XML Document (FreeXmlToolkit)")));
        assertTrue(commands.stream().anyMatch(c ->
                c.contains("HKCU\\Software\\Classes\\FreeXmlToolkit.xml\\DefaultIcon")
                        && c.contains("\"" + LAUNCHER + "\",0")));
        assertTrue(commands.stream().anyMatch(c ->
                c.contains("HKCU\\Software\\Classes\\FreeXmlToolkit.xml\\shell\\open\\command")
                        && c.contains("\"" + LAUNCHER + "\" \"%1\"")));
        assertTrue(commands.stream().anyMatch(c ->
                c.contains("HKCU\\Software\\Classes\\.xml\\OpenWithProgids")
                        && c.contains("FreeXmlToolkit.xml")));
        assertTrue(commands.stream().anyMatch(c ->
                c.contains("HKCU\\Software\\FreeXmlToolkit\\Capabilities\\FileAssociations")
                        && c.contains(".xml") && c.contains("FreeXmlToolkit.xml")));
        assertTrue(commands.stream().anyMatch(c ->
                c.contains("HKCU\\Software\\RegisteredApplications") && c.contains("FreeXmlToolkit")));
    }

    @Test
    void registerCoversEveryExtensionOfAType() {
        List<List<String>> commands = strategy.buildRegisterCommands(List.of(XSLT));
        List<String> flat = commands.stream().map(c -> String.join(" ", c)).toList();
        assertTrue(flat.stream().anyMatch(c -> c.contains("FreeXmlToolkit.xsl ")
                || c.contains("Classes\\FreeXmlToolkit.xsl")));
        assertTrue(flat.stream().anyMatch(c -> c.contains("Classes\\FreeXmlToolkit.xslt")));
    }

    @Test
    void unregisterCommandsDeleteProgIdOpenWithAndCapabilityValue() {
        List<List<String>> commands = strategy.buildUnregisterCommands(List.of(XML));
        List<String> flat = commands.stream().map(c -> String.join(" ", c)).toList();
        assertTrue(flat.contains("reg delete HKCU\\Software\\Classes\\FreeXmlToolkit.xml /f"));
        assertTrue(flat.contains("reg delete HKCU\\Software\\Classes\\.xml\\OpenWithProgids /v FreeXmlToolkit.xml /f"));
        assertTrue(flat.contains("reg delete HKCU\\Software\\FreeXmlToolkit\\Capabilities\\FileAssociations /v .xml /f"));
    }

    @Test
    void registerOpensDefaultAppsSettingsPage() {
        FileAssociationResult result = strategy.register(List.of(XML), List.of(XML, XSLT));
        assertTrue(result.success());
        assertTrue(result.systemSettingsOpened());
        assertTrue(runner.flatCommands().stream().anyMatch(c ->
                c.contains("ms-settings:defaultapps?registeredAppUser=FreeXmlToolkit")));
        assertTrue(strategy.opensSystemSettingsPage());
    }

    @Test
    void registerFallsBackToPlainSettingsUriWhenDeepLinkFails() {
        runner.responder = command -> {
            String flat = String.join(" ", command);
            if (flat.contains("registeredAppUser")) {
                return new CommandRunner.CommandResult(1, "", "no such page");
            }
            return new CommandRunner.CommandResult(0, "", "");
        };
        FileAssociationResult result = strategy.register(List.of(XML), List.of(XML));
        assertTrue(result.success());
        assertTrue(result.systemSettingsOpened());
        assertTrue(runner.flatCommands().stream().anyMatch(c ->
                c.endsWith("ms-settings:defaultapps")));
    }

    @Test
    void registerAggregatesRegAddFailures() {
        runner.responder = command -> command.contains("add")
                ? new CommandRunner.CommandResult(1, "", "Access is denied.")
                : new CommandRunner.CommandResult(0, "", "");
        FileAssociationResult result = strategy.register(List.of(XML), List.of(XML));
        assertFalse(result.success());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().getFirst().contains("Access is denied."));
    }

    @Test
    void stateIsRegisteredWhenCapabilityValueExists() {
        runner.responder = command -> new CommandRunner.CommandResult(0,
                "    .xml    REG_SZ    FreeXmlToolkit.xml", "");
        assertEquals(RegistrationState.REGISTERED, strategy.state(XML));
    }

    @Test
    void stateIsNotRegisteredWhenQueryFails() {
        runner.responder = command -> new CommandRunner.CommandResult(1, "",
                "ERROR: The system was unable to find the specified registry key or value.");
        assertEquals(RegistrationState.NOT_REGISTERED, strategy.state(XML));
    }

    @Test
    void parseRegQueryValueExtractsData() {
        String stdout = """
                HKEY_CURRENT_USER\\Software\\FreeXmlToolkit\\Capabilities\\FileAssociations
                    .xml    REG_SZ    FreeXmlToolkit.xml
                """;
        assertEquals("FreeXmlToolkit.xml",
                WindowsAssociationStrategy.parseRegQueryValue(stdout, ".xml"));
        assertNull(WindowsAssociationStrategy.parseRegQueryValue(stdout, ".xsd"));
        assertNull(WindowsAssociationStrategy.parseRegQueryValue(null, ".xml"));
    }

    @Test
    void unregisterRemovesCapabilitiesWhenNothingRemains() {
        runner.responder = command -> {
            if (command.contains("query")) {
                return new CommandRunner.CommandResult(1, "", "not found");
            }
            return new CommandRunner.CommandResult(0, "", "");
        };
        FileAssociationResult result = strategy.unregister(List.of(XML), false);
        assertTrue(result.success());
        assertTrue(runner.flatCommands().contains("reg delete HKCU\\Software\\FreeXmlToolkit /f"));
        assertTrue(runner.flatCommands().contains(
                "reg delete HKCU\\Software\\RegisteredApplications /v FreeXmlToolkit /f"));
    }
}
