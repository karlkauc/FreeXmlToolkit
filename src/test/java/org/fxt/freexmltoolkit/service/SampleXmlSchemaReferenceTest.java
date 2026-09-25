/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.fxt.freexmltoolkit.controls.shell.editor.ProfiledSampleRunner;
import org.fxt.freexmltoolkit.controls.shell.editor.SampleXmlRunner;
import org.fxt.freexmltoolkit.domain.GenerationProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generated samples reference their schema portably — by file name, or relative to the folder
 * the batch is written to — never by an absolute {@code file:} URI of the generating machine.
 */
class SampleXmlSchemaReferenceTest {

    private static final String NO_NAMESPACE = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema">
              <element name="Order">
                <complexType>
                  <sequence>
                    <element name="Id" type="string"/>
                  </sequence>
                </complexType>
              </element>
            </schema>
            """;

    private static final String NAMESPACED = """
            <schema xmlns="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:o" elementFormDefault="qualified">
              <element name="Order">
                <complexType>
                  <sequence>
                    <element name="Id" type="string"/>
                  </sequence>
                </complexType>
              </element>
            </schema>
            """;

    @TempDir
    Path dir;

    @Test
    void plainAndRealisticSamplesReferenceTheSchemaByFileName() throws Exception {
        Path xsd = dir.resolve("xsd").resolve("Order.xsd");
        Files.createDirectories(xsd.getParent());
        Files.createDirectories(dir.resolve("xml"));
        Files.writeString(xsd, NO_NAMESPACE);
        // unnormalized path, as produced when the XSD was opened from a sibling folder's reference
        var opened = dir.resolve("xml").resolve("..").resolve("xsd").resolve("Order.xsd").toFile();

        for (boolean realistic : new boolean[] {false, true}) {
            String xml = SampleXmlRunner.generate(opened, true, 1, realistic);
            assertTrue(xml.contains("xsi:noNamespaceSchemaLocation=\"Order.xsd\""), xml);
            assertFalse(xml.contains("file:"), xml);
        }
    }

    @Test
    void namespacedSampleKeepsTheNamespaceInTheSchemaLocationPair() throws Exception {
        Path xsd = dir.resolve("Order.xsd");
        Files.writeString(xsd, NAMESPACED);

        String xml = SampleXmlRunner.generate(xsd.toFile(), true, 1, false);

        assertTrue(xml.contains("xsi:schemaLocation=\"urn:o Order.xsd\""), xml);
    }

    @Test
    void batchFilesReferenceTheSchemaRelativeToTheirOutputFolder() throws Exception {
        Path xsd = dir.resolve("schemas").resolve("Order.xsd");
        Path out = dir.resolve("out").resolve("batch");
        Files.createDirectories(xsd.getParent());
        Files.createDirectories(out);
        Files.writeString(xsd, NO_NAMESPACE);
        GenerationProfile profile = new GenerationProfile("batch");
        profile.setBatchCount(2);

        var files = ProfiledSampleRunner.generateBatch(xsd.toFile(), profile, out.toFile());

        assertTrue(files.size() == 2, "two files expected, got " + files.size());
        for (var file : files) {
            assertTrue(file.content().contains("xsi:noNamespaceSchemaLocation=\"../../schemas/Order.xsd\""),
                    file.content());
        }
    }
}
