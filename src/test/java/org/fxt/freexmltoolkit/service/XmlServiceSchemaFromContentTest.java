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

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests {@link XmlService#getSchemaNameFromXmlContent(String, File)} — the buffer-based
 * schema-location prescan used when validating unsaved editor content — including parity
 * with the file-based {@link XmlService#getSchemaNameFromCurrentXMLFile()}.
 */
public class XmlServiceSchemaFromContentTest {

    private XmlService xmlService;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initRegistry() {
        // Eagerly resolve services to avoid recursive ConcurrentHashMap.computeIfAbsent
        // during XmlServiceImpl static initialization
        ServiceRegistry.initialize();
        ServiceRegistry.get(PropertiesService.class);
        ServiceRegistry.get(ConnectionService.class);
    }

    @BeforeEach
    void setUp() {
        xmlService = new XmlServiceImpl();
    }

    @Test
    @DisplayName("Remote noNamespaceSchemaLocation URL is returned as-is")
    void remoteNoNamespaceLocation() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <FundsXML4 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                           xsi:noNamespaceSchemaLocation="https://github.com/fundsxml/schema/releases/download/4.2.2/FundsXML.xsd">
                </FundsXML4>
                """, null);

        assertEquals(Optional.of("https://github.com/fundsxml/schema/releases/download/4.2.2/FundsXML.xsd"),
                result);
    }

    @Test
    @DisplayName("xsi:schemaLocation pair yields the URL part")
    void schemaLocationPair() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="http://example.org/ns"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://example.org/ns https://example.org/schema.xsd">
                </root>
                """, null);

        assertEquals(Optional.of("https://example.org/schema.xsd"), result);
    }

    @Test
    @DisplayName("Relative location resolves against baseDir when the file exists")
    void relativeLocationWithBaseDir() throws IOException {
        Files.writeString(tempDir.resolve("local.xsd"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="root" type="xs:string"/>
                </xs:schema>
                """);

        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="local.xsd">
                </root>
                """, tempDir.toFile());

        assertTrue(result.isPresent());
        assertTrue(result.get().startsWith("file:"),
                "Existing local schema must resolve to a file URI, got: " + result.get());
        assertTrue(result.get().contains("local.xsd"));
    }

    @Test
    @DisplayName("Relative location with null baseDir returns the raw trimmed value")
    void relativeLocationWithoutBaseDir() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="local.xsd">
                </root>
                """, null);

        assertEquals(Optional.of("local.xsd"), result);
    }

    @Test
    @DisplayName("No declaration yields empty")
    void noDeclaration() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <FundsXML4><ControlData/></FundsXML4>
                """, null);

        assertEquals(Optional.empty(), result);
    }

    @Test
    @DisplayName("xmlns ending in .xsd is used as fallback")
    void xmlnsEndingInXsd() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="http://example.org/schema.xsd"/>
                """, null);

        assertEquals(Optional.of("http://example.org/schema.xsd"), result);
    }

    @Test
    @DisplayName("Whitespace around the location is trimmed")
    void whitespaceTrimmed() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="  https://example.org/schema.xsd  ">
                </root>
                """, null);

        assertEquals(Optional.of("https://example.org/schema.xsd"), result);
    }

    @Test
    @DisplayName("A custom prefix bound to the XSI namespace is recognized")
    void customXsiPrefix() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:inst="http://www.w3.org/2001/XMLSchema-instance"
                      inst:noNamespaceSchemaLocation="https://example.org/schema.xsd">
                </root>
                """, null);

        assertEquals(Optional.of("https://example.org/schema.xsd"), result);
    }

    @Test
    @DisplayName("A document with a DOCTYPE is still scanned")
    void doctypePresent() {
        Optional<String> result = xmlService.getSchemaNameFromXmlContent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE root [<!ENTITY internal "value">]>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="https://example.org/schema.xsd">
                </root>
                """, null);

        assertEquals(Optional.of("https://example.org/schema.xsd"), result);
    }

    @Test
    @DisplayName("Malformed XML yields empty instead of throwing")
    void malformedXml() {
        assertEquals(Optional.empty(),
                xmlService.getSchemaNameFromXmlContent("<root xsi:noNamespace", null));
        assertEquals(Optional.empty(), xmlService.getSchemaNameFromXmlContent("", null));
        assertEquals(Optional.empty(), xmlService.getSchemaNameFromXmlContent(null, null));
        assertEquals(Optional.empty(), xmlService.getSchemaNameFromXmlContent("plain text", null));
    }

    @Test
    @DisplayName("Buffer-based extraction matches the file-based path on identical content")
    void parityWithFileBasedDetection() throws IOException {
        String[] documents = {
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="https://example.org/schema.xsd">
                </root>
                """,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="http://example.org/ns"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://example.org/ns https://example.org/schema.xsd">
                </root>
                """,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:noNamespaceSchemaLocation="missing.xsd">
                </root>
                """,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <root xmlns="http://example.org/schema.xsd"/>
                """,
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <root><child/></root>
                """,
        };
        for (int i = 0; i < documents.length; i++) {
            File xmlFile = tempDir.resolve("parity-" + i + ".xml").toFile();
            Files.writeString(xmlFile.toPath(), documents[i]);
            xmlService.setCurrentXmlFile(xmlFile);

            assertEquals(xmlService.getSchemaNameFromCurrentXMLFile(),
                    xmlService.getSchemaNameFromXmlContent(documents[i], tempDir.toFile()),
                    "Buffer- and file-based detection must agree for document " + i);
        }
    }
}
