package org.fxt.freexmltoolkit.plugins.kagmeldewesen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.fxt.freexmltoolkit.plugins.regreport.RegRuleSet;
import org.fxt.freexmltoolkit.plugins.regreport.RegValidator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The FMA's own sample files (downloaded into the gitignored {@code regdata/fma/samples/}, never part of the repository)
 * pass the bundled ESMA schema and every FMA rule: a finding here is a false alarm of a rule. Skipped when the folder
 * is not there (CI, other machines).
 */
class KagFmaSamplesTest {

    private static final Path SAMPLES = Path.of("regdata/fma/samples");

    @ParameterizedTest
    @ValueSource(strings = {"Registrierter AIF Sample File FMA.xml", "Registrierter AIFM Sample File FMA.xml"})
    void theFmaSamplesPassSchemaAndRules(String name) throws Exception {
        Path file = SAMPLES.resolve(name);
        assumeTrue(Files.isRegularFile(file), "FMA sample not downloaded: " + file);
        String xml = Files.readString(file);
        RegValidator.Root root = RegValidator.rootOf(xml);
        String xsd = root.localName().equals("AIFReportingInfo") ? "AIFMD_DATAIF_V1.2.xsd" : "AIFMD_DATMAN_V1.2.xsd";
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(KagFmaSamplesTest.class.getResource(KagMeldewesenPlugin.AIFMD_RESOURCES + "/" + xsd))
                .newValidator().validate(new StreamSource(file.toFile()));
        List<RegRuleSet.Finding> findings = KagMeldewesenPlugin.rulesFor(root, xml).run(xml, name);
        assertEquals(List.of(), findings, name);
    }
}
