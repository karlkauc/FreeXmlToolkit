/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.domain.GeneratedFile;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.fxt.freexmltoolkit.domain.GenerationStrategy;
import org.fxt.freexmltoolkit.domain.XPathRule;
import org.fxt.freexmltoolkit.domain.XsdDocumentationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates XML produced by {@link ProfiledXmlGeneratorService} against the source schema.
 * Ensures that typical profile configurations (empty, FIXED, SEQUENCE, batch) still yield
 * schema-valid XML — a guarantee that protects the generator from silent regressions.
 */
@DisplayName("Generated XML schema validation")
class GeneratedXmlValidationTest {

    private ProfiledXmlGeneratorService service;
    private XsdDocumentationService docService;
    private XsdDocumentationData data;
    private String xsdFilePath;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProfiledXmlGeneratorService();
        File xsdFile = new File("src/test/resources/demo-xsd/test-profiled-generation.xsd");
        assertTrue(xsdFile.exists(), "Test XSD must exist at " + xsdFile.getAbsolutePath());
        xsdFilePath = xsdFile.getAbsolutePath();

        docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFilePath);
        docService.processXsd(false);
        data = docService.xsdDocumentationData;
    }

    @Test
    @DisplayName("Empty profile produces schema-valid XML")
    void emptyProfileIsValid() {
        String xml = service.generate(new GenerationProfile("Empty"), data, xsdFilePath);
        assertValid(xml);
    }

    @Test
    @DisplayName("Mandatory-only profile produces schema-valid XML")
    void mandatoryOnlyIsValid() {
        var profile = new GenerationProfile("MandatoryOnly");
        profile.setMandatoryOnly(true);

        String xml = service.generate(profile, data, xsdFilePath);
        assertValid(xml);
    }

    @Test
    @DisplayName("FIXED values compatible with the schema keep XML valid")
    void fixedValuesValid() {
        var profile = new GenerationProfile("Fixed");
        profile.addRule(new XPathRule("/order/@id", GenerationStrategy.FIXED,
                Map.of("value", "ORDER-42")));
        profile.addRule(new XPathRule("/order/customer/country", GenerationStrategy.FIXED,
                Map.of("value", "AT")));

        String xml = service.generate(profile, data, xsdFilePath);
        assertTrue(xml.contains("ORDER-42"), "FIXED id must appear");
        assertTrue(xml.contains("<country>AT</country>"), "FIXED country must appear");
        assertValid(xml);
    }

    @Test
    @DisplayName("SEQUENCE IDs produce schema-valid XML")
    void sequenceIdsValid() {
        var profile = new GenerationProfile("SeqIds");
        profile.setMaxOccurrences(3);
        profile.addRule(new XPathRule("/order/item/@itemId", GenerationStrategy.SEQUENCE,
                Map.of("pattern", "ITEM-{seq:4}", "start", "1")));

        String xml = service.generate(profile, data, xsdFilePath);
        assertTrue(xml.contains("ITEM-0001"), "First sequence id must appear");
        assertValid(xml);
    }

    @Test
    @DisplayName("OMIT on optional element keeps XML valid")
    void omitOptionalElementIsValid() {
        var profile = new GenerationProfile("OmitOptional");
        profile.addRule(new XPathRule("/order/notes", GenerationStrategy.OMIT));

        String xml = service.generate(profile, data, xsdFilePath);
        assertFalse(xml.contains("<notes"), "Optional notes must be omitted");
        assertValid(xml);
    }

    @Test
    @DisplayName("Batch generation produces schema-valid files")
    void batchFilesValid() {
        var profile = new GenerationProfile("Batch");
        profile.setBatchCount(4);
        profile.setFileNamePattern("order_{seq:3}.xml");
        profile.addRule(new XPathRule("/order/@id", GenerationStrategy.SEQUENCE,
                Map.of("pattern", "ORD-{seq:4}", "start", "1")));

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);
        assertTrue(files.size() == 4, "Expected 4 files, got " + files.size());

        for (GeneratedFile file : files) {
            assertValid(file.content());
        }
    }

    @Test
    @DisplayName("Empty profile output contains the declared root element")
    void outputHasSchemaRoot() {
        String xml = service.generate(new GenerationProfile("Empty"), data, xsdFilePath);
        assertNotNull(xml);
        assertTrue(xml.contains("<order"), "Generated XML must contain the schema root");
    }

    private void assertValid(String xml) {
        XsdDocumentationService.ValidationResult result = docService.validateXmlAgainstSchema(xml);
        if (!result.isValid()) {
            String sample = xml.length() > 4000 ? xml.substring(0, 4000) + "\n... (truncated)" : xml;
            throw new AssertionError(
                    "Generated XML failed schema validation.\n"
                            + "Errors (" + result.errors().size() + "):\n"
                            + result.errors().stream()
                                    .map(e -> "  - " + e.message())
                                    .limit(20)
                                    .reduce("", (a, b) -> a + b + "\n")
                            + "XML:\n" + sample);
        }
    }
}
