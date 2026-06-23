/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2026.
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that HTML documentation generation copies not only the main XSD but also all
 * transitively referenced xs:include / local xs:import schema files into the output directory,
 * preserving their relative layout so internal schemaLocation references keep resolving.
 */
@DisplayName("XSD documentation - include/import schema files are copied to output")
class XsdDocumentationIncludeImportCopyTest {

    private static final String MAIN_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       xmlns:o="urn:other"
                       targetNamespace="urn:main"
                       elementFormDefault="qualified">
              <xs:import namespace="urn:other" schemaLocation="%s"/>
              <xs:include schemaLocation="%s"/>
              <xs:element name="root">
                <xs:complexType>
                  <xs:sequence>
                    <xs:element ref="child"/>
                    <xs:element name="ext" type="o:OtherType"/>
                  </xs:sequence>
                </xs:complexType>
              </xs:element>
            </xs:schema>
            """;

    private static final String CHILD_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="urn:main"
                       elementFormDefault="qualified">
              <xs:element name="child" type="xs:string"/>
            </xs:schema>
            """;

    private static final String OTHER_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                       targetNamespace="urn:other"
                       elementFormDefault="qualified">
              <xs:complexType name="OtherType">
                <xs:sequence>
                  <xs:element name="v" type="xs:string"/>
                </xs:sequence>
              </xs:complexType>
            </xs:schema>
            """;

    private static void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static XsdDocumentationService newService(Path mainXsd) {
        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(mainXsd.toString());
        service.setMethod(XsdDocumentationService.ImageOutputMethod.SVG);
        service.setParallelProcessing(false);
        return service;
    }

    @Test
    @DisplayName("processAllSchemas collects main, include and import files")
    void collectsAllInvolvedSchemaFiles(@TempDir Path tmp) throws Exception {
        Path schemaDir = tmp.resolve("schema");
        Path main = schemaDir.resolve("main.xsd");
        write(schemaDir.resolve("sub/child.xsd"), CHILD_XSD);
        write(schemaDir.resolve("other.xsd"), OTHER_XSD);
        write(main, MAIN_XSD.formatted("other.xsd", "sub/child.xsd"));

        XsdDocumentationService service = newService(main);
        service.processXsd(true);

        Set<String> names = service.getProcessedSchemaFiles().stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toSet());

        assertTrue(names.contains("main.xsd"), "main schema must be collected");
        assertTrue(names.contains("child.xsd"), "included schema must be collected");
        assertTrue(names.contains("other.xsd"), "imported schema must be collected");
    }

    @Test
    @DisplayName("Sub-directory references keep their relative layout under output root")
    void copiesReferencedFilesPreservingSubDirectories(@TempDir Path tmp) throws Exception {
        Path schemaDir = tmp.resolve("schema");
        Path main = schemaDir.resolve("main.xsd");
        write(schemaDir.resolve("sub/child.xsd"), CHILD_XSD);
        write(schemaDir.resolve("other.xsd"), OTHER_XSD);
        write(main, MAIN_XSD.formatted("other.xsd", "sub/child.xsd"));

        File outputDir = tmp.resolve("out").toFile();
        newService(main).generateXsdDocumentation(outputDir);

        // All files share the main schema's directory as common base -> main stays at the root.
        assertTrue(new File(outputDir, "main.xsd").isFile(), "main XSD must be copied to output root");
        assertTrue(new File(outputDir, "sub/child.xsd").isFile(), "included XSD must keep its sub-directory");
        assertTrue(new File(outputDir, "other.xsd").isFile(), "imported XSD must be copied");
    }

    @Test
    @DisplayName("Parent-directory ('../') references are reproduced structurally under output")
    void copiesParentReferencesPreservingStructure(@TempDir Path tmp) throws Exception {
        // Main schema lives in a sub-directory and references files above it via "../".
        Path mainDir = tmp.resolve("main");
        Path sharedDir = tmp.resolve("shared");
        Path main = mainDir.resolve("main.xsd");
        write(sharedDir.resolve("child.xsd"), CHILD_XSD);
        write(sharedDir.resolve("other.xsd"), OTHER_XSD);
        write(main, MAIN_XSD.formatted("../shared/other.xsd", "../shared/child.xsd"));

        File outputDir = tmp.resolve("out").toFile();
        newService(main).generateXsdDocumentation(outputDir);

        // Common base is the parent of both directories -> structure is reproduced under output.
        Path outRoot = outputDir.toPath();
        assertTrue(Files.isRegularFile(outRoot.resolve("main/main.xsd")), "main XSD must be nested under output");
        assertTrue(Files.isRegularFile(outRoot.resolve("shared/child.xsd")), "included XSD must keep its relative position");
        assertTrue(Files.isRegularFile(outRoot.resolve("shared/other.xsd")), "imported XSD must keep its relative position");

        // The main schema's "../shared/child.xsd" reference must still resolve from its new location.
        Path resolved = outRoot.resolve("main/main.xsd").getParent().resolve("../shared/child.xsd").normalize();
        assertTrue(Files.isRegularFile(resolved), "relative schemaLocation must still resolve in the output");
    }
}
