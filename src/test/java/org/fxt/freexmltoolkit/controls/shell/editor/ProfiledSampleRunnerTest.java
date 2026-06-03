package org.fxt.freexmltoolkit.controls.shell.editor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.domain.GeneratedFile;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathInfo;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * UI-free runner that drives the profiled XML generator (per-XPath rules, named profiles, batch) for
 * the shell — the foundation for the advanced "Generate Sample XML" UI. Wraps
 * {@link org.fxt.freexmltoolkit.service.ProfiledXmlGeneratorService} + {@code XsdDocumentationService}.
 */
class ProfiledSampleRunnerTest {

    private static final String XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="order">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element name="country" type="xs:string"/>
                  </xs:sequence>
                  <xs:attribute name="id" type="xs:string"/>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private File writeXsd(Path tmp) throws Exception {
        Path xsd = tmp.resolve("order.xsd");
        Files.writeString(xsd, XSD);
        return xsd.toFile();
    }

    @Test
    void extractsXPathsFromSchema(@TempDir Path tmp) throws Exception {
        List<XPathInfo> xpaths = ProfiledSampleRunner.extractXPaths(writeXsd(tmp));
        assertFalse(xpaths.isEmpty(), "schema XPaths must be extracted for rule editing");
        assertTrue(xpaths.stream().anyMatch(x -> x.xpath().endsWith("country")),
                "the country element XPath must be present, was: " + xpaths);
    }

    @Test
    void fixedRuleProducesTheLiteralValue(@TempDir Path tmp) throws Exception {
        File xsd = writeXsd(tmp);
        String countryXPath = ProfiledSampleRunner.extractXPaths(xsd).stream()
                .map(XPathInfo::xpath).filter(x -> x.endsWith("country")).findFirst().orElseThrow();

        GenerationProfile profile = new GenerationProfile("test");
        profile.addRule(new XPathRule(countryXPath, GenerationStrategy.FIXED,
                java.util.Map.of("value", "AT")));

        String xml = ProfiledSampleRunner.generate(xsd, profile);
        assertFalse(xml.startsWith("ERROR"), xml);
        assertTrue(xml.contains("<country>AT</country>"),
                "the FIXED rule value must appear in the generated XML, was:\n" + xml);
    }

    @Test
    void batchGeneratesMultipleNamedFiles(@TempDir Path tmp) throws Exception {
        File xsd = writeXsd(tmp);
        GenerationProfile profile = new GenerationProfile("batch");
        profile.setBatchCount(3);
        profile.setFileNamePattern("order_{seq:3}.xml");

        List<GeneratedFile> files = ProfiledSampleRunner.generateBatch(xsd, profile);
        assertEquals(3, files.size(), "batchCount files must be generated");
        assertTrue(files.stream().allMatch(f -> f.fileName().startsWith("order_")), files.toString());
        assertEquals(3, files.stream().map(GeneratedFile::fileName).distinct().count(),
                "each batch file must have a distinct name");
    }

    @Test
    void missingFileYieldsErrorOrEmpty(@TempDir Path tmp) {
        File missing = tmp.resolve("nope.xsd").toFile();
        assertTrue(ProfiledSampleRunner.generate(missing, new GenerationProfile("x")).startsWith("ERROR"));
        assertTrue(ProfiledSampleRunner.extractXPaths(missing).isEmpty());
        assertTrue(ProfiledSampleRunner.generateBatch(missing, new GenerationProfile("x")).isEmpty());
    }
}
