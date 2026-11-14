package org.fxt.freexmltoolkit.plugins.pkmeldewesen;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.EntrySource;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The PK-Meldewesen plugin: schemas by root element, badge and hint follow its switch. */
class PkMeldewesenPluginTest {

    @TempDir
    Path store;
    private PropertiesService props;

    @BeforeEach
    void setUp() {
        ServiceRegistry.initialize();
        props = ServiceRegistry.get(PropertiesService.class);
        System.setProperty("fxt.pk.dir", store.toString());
        props.set(PkSettings.ENABLED, "false");
        props.set(PkSettings.HINT_SHOWN, "false");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("fxt.pk.dir");
        props.set(PkSettings.ENABLED, "");
        props.set(PkSettings.HINT_SHOWN, "");
        PluginManager.getInstance().resetForTesting();
    }

    private static String sample() throws Exception {
        try (var in = PkMeldewesenPluginTest.class.getResourceAsStream("/pkmeldewesen/synthetic-pfq.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void schemasAreBoundByRootElement() {
        PluginManager manager = PluginManager.getInstance();
        manager.resetForTesting();
        manager.loadAll();
        assertEquals(PluginManager.State.ACTIVE, manager.plugins().stream()
                .filter(p -> p.id().equals(PkMeldewesenPlugin.ID)).findFirst().orElseThrow().state(),
                "the plugin is always loaded; its own switch decides what it does");
        for (PkModule module : PkModule.values()) {
            var entry = SchemaLibraryServiceImpl.shared().resolveByRootElement(module.rootElement()).orElseThrow();
            assertEquals(EntrySource.PLUGIN, entry.source());
            Path schema = Path.of(entry.location());
            assertTrue(schema.startsWith(store), entry.location());
            assertTrue(Files.isRegularFile(schema), "the bundled taxonomy was installed: " + schema);
            assertTrue(schema.getFileName().toString().startsWith("pk_meldung_" + module.code()));
        }
    }

    @Test
    void badgeAndHintFollowTheSwitch() throws Exception {
        String report = sample();
        assertNull(PkMeldewesenPlugin.badgeFor(null, report), "no badge while the extension is off");
        assertTrue(PkMeldewesenPlugin.shouldOfferHint(report));
        assertFalse(PkMeldewesenPlugin.shouldOfferHint("<FundsXML4/>"));
        props.set(PkSettings.HINT_SHOWN, "true");
        assertFalse(PkMeldewesenPlugin.shouldOfferHint(report), "the hint appears only once");

        PkSettings.setEnabled(true);
        var badge = PkMeldewesenPlugin.badgeFor(null, report);
        assertNotNull(badge);
        assertEquals("PK-QMV Q1/2026", badge.text());
        assertNull(PkMeldewesenPlugin.badgeFor(null, "<other/>"));
        assertFalse(PkMeldewesenPlugin.shouldOfferHint(report), "no hint while the extension is on");
    }
}
