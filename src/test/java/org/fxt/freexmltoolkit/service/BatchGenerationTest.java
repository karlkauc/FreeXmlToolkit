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

import static org.junit.jupiter.api.Assertions.*;

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

@DisplayName("Batch Generation")
class BatchGenerationTest {

    private ProfiledXmlGeneratorService service;
    private XsdDocumentationData data;
    private String xsdFilePath;

    @BeforeEach
    void setUp() throws Exception {
        service = new ProfiledXmlGeneratorService();
        File xsdFile = new File("src/test/resources/demo-xsd/test-profiled-generation.xsd");
        xsdFilePath = xsdFile.getAbsolutePath();

        XsdDocumentationService docService = new XsdDocumentationService();
        docService.setXsdFilePath(xsdFilePath);
        docService.processXsd(false);
        data = docService.xsdDocumentationData;
    }

    @Test
    @DisplayName("Generates correct number of files")
    void correctFileCount() {
        var profile = new GenerationProfile("Batch");
        profile.setBatchCount(5);
        profile.setFileNamePattern("test_{seq:3}.xml");

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);
        assertEquals(5, files.size());
    }

    @Test
    @DisplayName("File names follow pattern")
    void fileNamesFollowPattern() {
        var profile = new GenerationProfile("Batch");
        profile.setBatchCount(3);
        profile.setFileNamePattern("order_{seq:3}.xml");

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);
        assertEquals("order_001.xml", files.get(0).fileName());
        assertEquals("order_002.xml", files.get(1).fileName());
        assertEquals("order_003.xml", files.get(2).fileName());
    }

    @Test
    @DisplayName("Each file contains valid XML")
    void eachFileIsValidXml() {
        var profile = new GenerationProfile("Batch");
        profile.setBatchCount(3);

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);
        for (GeneratedFile file : files) {
            assertTrue(file.content().startsWith("<?xml"), "File should start with XML declaration: " + file.fileName());
            assertTrue(file.content().contains("<order"), "File should contain root element: " + file.fileName());
            assertTrue(file.content().contains("</order>"), "File should close root element: " + file.fileName());
        }
    }

    @Test
    @DisplayName("Sequence counters persist across files")
    void sequenceCountersPersist() {
        var profile = new GenerationProfile("Seq Batch");
        profile.setBatchCount(3);
        profile.setMaxOccurrences(1); // 1 item per file
        profile.addRule(new XPathRule("/order/@id", GenerationStrategy.SEQUENCE,
                Map.of("pattern", "ORD-{seq:3}", "start", "1")));

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);

        assertTrue(files.get(0).content().contains("ORD-001"), "File 1 should have ORD-001");
        assertTrue(files.get(1).content().contains("ORD-002"), "File 2 should have ORD-002");
        assertTrue(files.get(2).content().contains("ORD-003"), "File 3 should have ORD-003");
    }

    @Test
    @DisplayName("Single batch count generates one file")
    void singleBatch() {
        var profile = new GenerationProfile("Single");
        profile.setBatchCount(1);

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);
        assertEquals(1, files.size());
    }

    @Test
    @DisplayName("FIXED values remain constant across files")
    void fixedValueConstant() {
        var profile = new GenerationProfile("Fixed Batch");
        profile.setBatchCount(3);
        profile.addRule(new XPathRule("/order/customer/country", GenerationStrategy.FIXED,
                Map.of("value", "AT")));

        List<GeneratedFile> files = service.generateBatch(profile, data, xsdFilePath);
        for (GeneratedFile file : files) {
            assertTrue(file.content().contains("<country>AT</country>"),
                    "Each file should have country AT: " + file.fileName());
        }
    }
}
