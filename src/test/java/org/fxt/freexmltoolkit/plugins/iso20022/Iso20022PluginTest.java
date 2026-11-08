package org.fxt.freexmltoolkit.plugins.iso20022;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.plugin.DocumentValidator.Problem;
import org.fxt.freexmltoolkit.plugin.PluginManager;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ISO 20022 plugin")
class Iso20022PluginTest {

    private final Iso20022Validator validator = new Iso20022Validator();

    @AfterEach
    void reset() {
        PluginManager.getInstance().resetForTesting();
    }

    private static String sample(String message) throws IOException {
        try (InputStream in = Iso20022PluginTest.class.getResourceAsStream("/plugins/iso20022/" + message + ".xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> messages(String xml) {
        return validator.validate(xml, "m.xml").stream().map(Problem::message).toList();
    }

    @Test
    @DisplayName("the bundled SEPA samples pass every check")
    void samplesPass() throws Exception {
        for (String message : List.of("pain.001.001.09", "pain.008.001.08")) {
            String xml = sample(message);
            assertTrue(validator.accepts(xml, null));
            assertEquals(List.of(), validator.validate(xml, null), message);
        }
        assertFalse(validator.accepts("<Document xmlns='urn:other'/>", null));
    }

    @Test
    @DisplayName("control counts and sums, identifiers and SEPA rules")
    void checks() throws Exception {
        String xml = sample("pain.001.001.09");
        List<String> wrongSum = messages(xml.replaceFirst("<CtrlSum>1234.50</CtrlSum>", "<CtrlSum>1234.00</CtrlSum>"));
        assertEquals(List.of("CtrlSum of the message is 1234.00, but its transactions add up to 1234.50."), wrongSum);

        List<String> wrongCount = messages(xml.replace("<NbOfTxs>2</NbOfTxs>", "<NbOfTxs>3</NbOfTxs>"));
        assertEquals(2, wrongCount.size(), wrongCount.toString());
        assertTrue(wrongCount.get(1).startsWith("NbOfTxs of payment group PMT-2026-0001 is 3, but it has 2"), wrongCount.toString());

        assertTrue(messages(xml.replace("DE89370400440532013000", "DE88370400440532013000")).getFirst().contains("check digits"));
        assertTrue(messages(xml.replace("BKAUATWW", "BKAU12WW")).getFirst().contains("is not a BIC"));

        List<String> usd = messages(xml.replace("Ccy=\"EUR\">234.50", "Ccy=\"USD\">234.50"));
        assertEquals(List.of("SEPA: payments are in EUR only, not USD."), usd);
        assertTrue(messages(xml.replace("INV-4711", "INV_4711")).getFirst().contains("SEPA character set"));
        assertTrue(messages(xml.replace("INV-4711", "/INV-4711")).getFirst().contains("must not start or end with /"));
        assertTrue(messages(xml.replace("SLEV", "SHAR")).getFirst().contains("charge bearer is SLEV"));
        assertTrue(messages(xml.replace("1000.00", "1000.001").replace("1234.50", "1234.501")).stream()
                .anyMatch(m -> m.contains("at most 2 decimals")));

        // Without service level SEPA the SEPA rules do not apply
        String noSepa = xml.replace("<Cd>SEPA</Cd>", "<Cd>URGP</Cd>").replace("Ccy=\"EUR\">234.50", "Ccy=\"USD\">234.50");
        assertEquals(List.of(), messages(noSepa));

        assertEquals(List.of("The message is not well-formed: ").getFirst(),
                messages("<Document xmlns='urn:iso:std:iso:20022:tech:xsd:pain.001.001.09'><a>").getFirst()
                        .substring(0, "The message is not well-formed: ".length()));
    }

    @Test
    @DisplayName("describes the message: version, name, groups, transactions, total")
    void describe() throws Exception {
        assertEquals("pain.001.001.09 · Customer Credit Transfer Initiation\n"
                + "1 payment group · 2 transactions · 1234.50 EUR · SEPA", Iso20022Plugin.describe(sample("pain.001.001.09")));
        assertEquals("camt.053.001.02 · Bank To Customer Statement",
                Iso20022Plugin.describe("<Document xmlns='urn:iso:std:iso:20022:tech:xsd:camt.053.001.02'/>"));
        assertNull(Iso20022Plugin.describe("<invoice/>"));
        assertEquals("https://www.iso20022.org/sites/default/files/documents/messages/pain/schemas/pain.001.001.09.xsd",
                Iso20022Plugin.schemaUrl("pain.001.001.09"));
    }

    @Test
    @DisplayName("off by default; switched on it adds its schemas to the Schema Library")
    void registration() {
        ServiceRegistry.initialize();
        PropertiesService props = ServiceRegistry.get(PropertiesService.class);
        PluginManager manager = PluginManager.getInstance();
        manager.resetForTesting();
        manager.loadAll();
        assertEquals(PluginManager.State.DISABLED, manager.plugins().stream()
                .filter(p -> p.id().equals(Iso20022Plugin.ID)).findFirst().orElseThrow().state());
        try {
            props.set(PluginManager.enabledKey(Iso20022Plugin.ID), "true");
            manager.resetForTesting();
            manager.loadAll();
            var entry = SchemaLibraryServiceImpl.shared().resolveNamespace(
                    "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09", org.fxt.freexmltoolkit.domain.SchemaKind.XSD).orElseThrow();
            assertEquals(org.fxt.freexmltoolkit.domain.EntrySource.PLUGIN, entry.source());
            assertEquals(Iso20022Plugin.schemaUrl("pain.001.001.09"), entry.location());
            assertTrue(manager.validators().contains(manager.validators().stream()
                    .filter(v -> v.name().equals("ISO 20022")).findFirst().orElseThrow()));
        } finally {
            props.set(PluginManager.enabledKey(Iso20022Plugin.ID), "");
        }
    }
}
