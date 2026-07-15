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
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacAssociationStrategyTest {

    private static final Path LAUNCHER =
            Path.of("/Applications/FreeXmlToolkit.app/Contents/MacOS/FreeXmlToolkit");
    private static final FileTypeDescriptor XML =
            new FileTypeDescriptor("XML", "XML Document", Set.of("xml"), "application/xml");

    private RecordingCommandRunner runner;
    private InMemoryKeyValueStore store;
    private MacAssociationStrategy strategy;

    @BeforeEach
    void setUp() {
        runner = new RecordingCommandRunner();
        store = new InMemoryKeyValueStore();
        strategy = new MacAssociationStrategy(runner, LAUNCHER, store);
    }

    @Test
    void setHandlerScriptUsesLaunchServicesApi() {
        String script = MacAssociationStrategy.buildSetHandlerScript();
        assertTrue(script.contains("UTTypeCreatePreferredIdentifierForTag"));
        assertTrue(script.contains("LSSetDefaultRoleHandlerForContentType"));
        assertTrue(script.contains("LSCopyDefaultRoleHandlerForContentType"));
        assertTrue(script.contains("ObjC.import('CoreServices')"));
    }

    @Test
    void parseSetHandlerOutputRecordsPreviousHandlerAndStatus() {
        String stdout = """
                xml|public.xml|com.apple.TextEdit|0
                xsd|dyn.ah62d4rv4ge81k3p2su11zzz|com.example.other|-54
                """;
        List<MacAssociationStrategy.HandlerChange> changes =
                MacAssociationStrategy.parseSetHandlerOutput(stdout);
        assertEquals(2, changes.size());
        assertEquals("xml", changes.get(0).extension());
        assertEquals("public.xml", changes.get(0).uti());
        assertEquals("com.apple.TextEdit", changes.get(0).previousHandler());
        assertTrue(changes.get(0).success());
        assertFalse(changes.get(1).success());
        assertEquals(-54, changes.get(1).status());
    }

    @Test
    void registerStoresPreviousHandlerAndReportsSuccess() {
        runner.responder = command -> new CommandRunner.CommandResult(0,
                "xml|public.xml|com.apple.TextEdit|0", "");
        FileAssociationResult result = strategy.register(List.of(XML), List.of(XML));
        assertTrue(result.success());
        assertEquals("com.apple.TextEdit",
                store.values.get(MacAssociationStrategy.PREVIOUS_HANDLER_KEY_PREFIX + "xml"));
        // osascript invocation carries the ext=bundleId pair
        assertTrue(runner.flatCommands().stream().anyMatch(c ->
                c.startsWith("osascript -l JavaScript")
                        && c.endsWith("xml=" + MacAssociationStrategy.BUNDLE_ID)));
    }

    @Test
    void registerReportsPerExtensionFailures() {
        runner.responder = command -> new CommandRunner.CommandResult(0,
                "xml|public.xml||-54", "");
        FileAssociationResult result = strategy.register(List.of(XML), List.of(XML));
        assertFalse(result.success());
        assertTrue(result.errors().getFirst().contains(".xml"));
        assertTrue(result.errors().getFirst().contains("-54"));
    }

    @Test
    void unregisterRestoresRecordedPreviousHandler() {
        store.set(MacAssociationStrategy.PREVIOUS_HANDLER_KEY_PREFIX + "xml", "com.apple.TextEdit");
        runner.responder = command -> new CommandRunner.CommandResult(0,
                "xml|public.xml|org.fxt.freexmltoolkit|0", "");
        FileAssociationResult result = strategy.unregister(List.of(XML), true);
        assertTrue(result.success());
        assertTrue(runner.flatCommands().stream().anyMatch(c ->
                c.endsWith("xml=com.apple.TextEdit")));
        assertEquals("", store.values.get(MacAssociationStrategy.PREVIOUS_HANDLER_KEY_PREFIX + "xml"));
    }

    @Test
    void unregisterWithoutRecordedHandlerPointsToFinder() {
        FileAssociationResult result = strategy.unregister(List.of(XML), true);
        assertTrue(result.success());
        assertTrue(result.message().contains("Finder"));
        assertTrue(runner.commands.isEmpty());
    }

    @Test
    void stateIsDefaultWhenAllExtensionsResolveToOurBundle() {
        runner.responder = command -> new CommandRunner.CommandResult(0,
                "xml|" + MacAssociationStrategy.BUNDLE_ID, "");
        assertEquals(RegistrationState.DEFAULT, strategy.state(XML));
    }

    @Test
    void stateIsRegisteredWhenAnotherAppIsDefault() {
        runner.responder = command -> new CommandRunner.CommandResult(0,
                "xml|com.apple.TextEdit", "");
        assertEquals(RegistrationState.REGISTERED, strategy.state(XML));
    }

    @Test
    void appBundleResolvesFromLauncherPath() {
        assertEquals(Path.of("/Applications/FreeXmlToolkit.app"), strategy.appBundle());
    }
}
