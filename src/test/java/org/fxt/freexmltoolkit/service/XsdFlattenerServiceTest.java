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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link XsdFlattenerService}.
 */
class XsdFlattenerServiceTest {

    private XsdFlattenerService flattenerService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        flattenerService = new XsdFlattenerService();
    }

    /**
     * Tests the flattening process for a schema that uses <xs:include>.
     */
    @Test
    void testFlattenWithInclude() throws Exception {
        // --- Arrange ---
        // Create a child schema to be included
        String childXsdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "    <xs:simpleType name=\"IncludedType\">\n" +
                "        <xs:restriction base=\"xs:string\"/>\n" +
                "    </xs:simpleType>\n" +
                "</xs:schema>";
        createTestFile("child.xsd", childXsdContent);

        // Create a parent schema that includes the child
        String parentXsdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                "    <xs:include schemaLocation=\"child.xsd\"/>\n" +
                "    <xs:element name=\"RootElement\" type=\"IncludedType\"/>\n" +
                "</xs:schema>";
        File parentXsd = createTestFile("parent_include.xsd", parentXsdContent);

        System.out.println("\n--- Running: testFlattenWithInclude ---");
        System.out.println("\n--- Child XSD (child.xsd) ---");
        System.out.println(childXsdContent);
        System.out.println("\n--- Parent XSD (parent_include.xsd) ---");
        System.out.println(parentXsdContent);

        // --- Act ---
        File flattenedXsd = tempDir.resolve("flattened_include.xsd").toFile();
        String result = flattenerService.flatten(parentXsd, flattenedXsd);

        System.out.println("\n--- Flattened Result ---");
        System.out.println(result);
        System.out.println("--- End of test: testFlattenWithInclude ---\n");

        // --- Assert ---
        assertTrue(flattenedXsd.exists(), "The flattened file should be created.");
        assertFalse(result.contains("<xs:include"), "The flattened XSD should not contain an <xs:include> tag.");
        assertTrue(result.contains("name=\"IncludedType\""), "The flattened XSD should contain the included type definition.");
        assertTrue(result.contains("name=\"RootElement\""), "The flattened XSD should retain its original content.");
    }

    /**
     * Tests the flattening process for a schema that uses &lt;xs:import&gt;.
     * <p>
     * NOTE: This test verifies that the flattener correctly removes the &lt;xs:import&gt;
     * and includes the type definitions from the imported file. However, this
     * flattening approach has a significant limitation: it strips the original
     * namespace from the imported components. The resulting schema may not be
     * valid if validated by a strict parser, but it can be sufficient for tools
     * that perform simple name-based lookups without strict namespace enforcement.
     * </p>
     */
    @Test
    void testFlattenWithImport() throws Exception {
        // --- Arrange ---
        // Create a child schema with a different namespace to be imported
        String childXsdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           targetNamespace=\"http://www.example.com/imported\"\n" +
                "           elementFormDefault=\"qualified\">\n" +
                "    <xs:simpleType name=\"ImportedType\">\n" +
                "        <xs:restriction base=\"xs:integer\"/>\n" +
                "    </xs:simpleType>\n" +
                "</xs:schema>";
        createTestFile("child_import.xsd", childXsdContent);

        // Create a parent schema that imports the child
        String parentXsdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           xmlns:imp=\"http://www.example.com/imported\">\n" +
                "    <xs:import namespace=\"http://www.example.com/imported\" schemaLocation=\"child_import.xsd\"/>\n" +
                "    <xs:element name=\"RootElement\" type=\"imp:ImportedType\"/>\n" +
                "</xs:schema>";
        File parentXsd = createTestFile("parent_import.xsd", parentXsdContent);

        System.out.println("\n--- Running: testFlattenWithImport ---");
        System.out.println("\n--- Child XSD (child_import.xsd) ---");
        System.out.println(childXsdContent);
        System.out.println("\n--- Parent XSD (parent_import.xsd) ---");
        System.out.println(parentXsdContent);

        // --- Act ---
        File flattenedXsd = tempDir.resolve("flattened_import.xsd").toFile();
        String result = flattenerService.flatten(parentXsd, flattenedXsd);

        System.out.println("\n--- Flattened Result ---");
        System.out.println(result);
        System.out.println("--- End of test: testFlattenWithImport ---\n");

        // --- Assert ---
        assertTrue(flattenedXsd.exists(), "The flattened file should be created.");
        assertFalse(result.contains("<xs:import"), "The flattened XSD should not contain an <xs:import> tag.");
        assertTrue(result.contains("name=\"ImportedType\""), "The flattened XSD should contain the imported type definition.");
        // We explicitly assert that the targetNamespace of the imported file is NOT present.
        // The current flattening strategy copies the *contents* of the <xs:schema> tag,
        // not the tag itself, thereby losing the original namespace context.
        assertFalse(result.contains("targetNamespace=\"http://www.example.com/imported\""), "The flattened XSD should NOT contain the targetNamespace attribute from the imported file.");
    }

    /**
     * Tests the flattening process for a schema that uses both &lt;xs:include&gt; and &lt;xs:import&gt;.
     */
    @Test
    void testFlattenWithIncludeAndImport() throws Exception {
        // --- Arrange ---
        // Create a schema for inclusion (same target namespace as parent)
        String childIncludeContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://www.example.com/main\">\n" +
                "    <xs:simpleType name=\"IncludedType\">\n" +
                "        <xs:restriction base=\"xs:string\"/>\n" +
                "    </xs:simpleType>\n" +
                "</xs:schema>";
        createTestFile("child_include.xsd", childIncludeContent);

        // Create a schema for importation (different target namespace)
        String childImportContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" targetNamespace=\"http://www.example.com/imported\">\n" +
                "    <xs:simpleType name=\"ImportedType\">\n" +
                "        <xs:restriction base=\"xs:integer\"/>\n" +
                "    </xs:simpleType>\n" +
                "</xs:schema>";
        createTestFile("child_import.xsd", childImportContent);

        // Create a parent schema that uses both directives
        String parentXsdContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "           xmlns:imp=\"http://www.example.com/imported\"\n" +
                "           xmlns:tns=\"http://www.example.com/main\"\n" +
                "           targetNamespace=\"http://www.example.com/main\">\n" +
                "    <xs:import namespace=\"http://www.example.com/imported\" schemaLocation=\"child_import.xsd\"/>\n" +
                "    <xs:include schemaLocation=\"child_include.xsd\"/>\n" +
                "    <xs:element name=\"RootElement\">\n" +
                "        <xs:complexType><xs:sequence>\n" +
                "            <xs:element name=\"IncludedElement\" type=\"tns:IncludedType\"/>\n" +
                "            <xs:element name=\"ImportedElement\" type=\"imp:ImportedType\"/>\n" +
                "        </xs:sequence></xs:complexType>\n" +
                "    </xs:element>\n" +
                "</xs:schema>";
        File parentXsd = createTestFile("parent_mixed.xsd", parentXsdContent);

        System.out.println("\n--- Running: testFlattenWithIncludeAndImport ---");
        System.out.println("\n--- Child Include XSD (child_include.xsd) ---");
        System.out.println(childIncludeContent);
        System.out.println("\n--- Child Import XSD (child_import.xsd) ---");
        System.out.println(childImportContent);
        System.out.println("\n--- Parent XSD (parent_mixed.xsd) ---");
        System.out.println(parentXsdContent);

        // --- Act ---
        File flattenedXsd = tempDir.resolve("flattened_mixed.xsd").toFile();
        String result = flattenerService.flatten(parentXsd, flattenedXsd);

        System.out.println("\n--- Flattened Result ---");
        System.out.println(result);
        System.out.println("--- End of test: testFlattenWithIncludeAndImport ---\n");

        // --- Assert ---
        assertTrue(flattenedXsd.exists(), "The flattened file should be created.");
        assertFalse(result.contains("<xs:include"), "The flattened XSD should not contain an <xs:include> tag.");
        assertFalse(result.contains("<xs:import"), "The flattened XSD should not contain an <xs:import> tag.");
        assertTrue(result.contains("name=\"IncludedType\""), "The flattened XSD should contain the included type.");
        assertTrue(result.contains("name=\"ImportedType\""), "The flattened XSD should contain the imported type.");
    }

    private File createTestFile(String fileName, String content) throws IOException {
        Path filePath = tempDir.resolve(fileName);
        Files.writeString(filePath, content);
        return filePath.toFile();
    }
}