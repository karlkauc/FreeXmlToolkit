package org.fxt.freexmltoolkit.plugins.esmareporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.EntrySource;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.plugins.kagmeldewesen.KagMeldewesenPlugin;
import org.fxt.freexmltoolkit.plugins.regreport.EsmaSchemas;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The two reporting extensions register their activity, validator and schemas (installed into a temp store). */
class ReportingPluginsRegistrationTest {

    @TempDir
    Path store;

    @BeforeEach
    void setUp() {
        ServiceRegistry.initialize();
        System.setProperty("fxt.regreport.dir", store.toString());
        var props = ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        props.set(PluginManager.enabledKey(KagMeldewesenPlugin.ID), "true");
        props.set(PluginManager.enabledKey(EsmaReportingPlugin.ID), "true");
        PluginManager.getInstance().resetForTesting();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("fxt.regreport.dir");
        var props = ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class);
        props.set(PluginManager.enabledKey(KagMeldewesenPlugin.ID), "");
        props.set(PluginManager.enabledKey(EsmaReportingPlugin.ID), "");
        PluginManager.getInstance().resetForTesting();
    }

    @Test
    void bothExtensionsRegisterAndBindTheirSchemas() throws Exception {
        PluginManager manager = PluginManager.getInstance();
        manager.registerForTesting(new KagMeldewesenPlugin());
        manager.registerForTesting(new EsmaReportingPlugin());
        assertTrue(manager.activities().stream().anyMatch(a -> a.id().equals(KagMeldewesenPlugin.ID)));
        assertTrue(manager.activities().stream().anyMatch(a -> a.id().equals(EsmaReportingPlugin.ID)));
        assertTrue(manager.hasElementDescribers());

        var aif = SchemaLibraryServiceImpl.shared().resolveByRootElement("AIFReportingInfo").orElseThrow();
        assertEquals(EntrySource.PLUGIN, aif.source());
        assertTrue(Files.isRegularFile(Path.of(aif.location())), aif.location());
        for (String namespace : new String[] {EsmaSchemas.ENVELOPE, EsmaSchemas.MMF, EsmaSchemas.EMIR, EsmaSchemas.SFTR, EsmaSchemas.MIFIR}) {
            var entry = SchemaLibraryServiceImpl.shared().resolveNamespace(namespace, SchemaKind.XSD).orElseThrow(
                    () -> new AssertionError("no schema for " + namespace));
            assertTrue(Files.isRegularFile(Path.of(entry.location())), entry.location());
        }

        // a report is checked by the validator of its extension (F8, Validation panel, fxt-cli)
        String emir;
        try (var in = getClass().getResourceAsStream("/plugins/esmareporting/examples/emir-eror-example.xml")) {
            emir = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var problems = manager.validate(emir.replaceFirst("\\s*<RptgTmStmp>[^<]*</RptgTmStmp>", ""), "emir.xml");
        assertTrue(problems.stream().anyMatch(p -> p.source().equals("ESMA Reporting") && p.message().startsWith("EMIR-1.1 ")),
                problems.toString());
    }
}
