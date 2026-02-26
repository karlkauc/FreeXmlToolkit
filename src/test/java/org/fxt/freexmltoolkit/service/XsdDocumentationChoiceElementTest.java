package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CHOICE element handling in XSD documentation.
 * Verifies that CHOICE elements are correctly represented in source code.
 */
class XsdDocumentationChoiceElementTest {

    @TempDir
    Path tempDir;

    private static final String TEST_XSD_WITH_CHOICE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
            
                <xs:complexType name="FundType">
                    <xs:sequence>
                        <xs:element name="Name" type="xs:string"/>
                        <xs:choice minOccurs="0">
                            <xs:element name="SingleFund" type="xs:string">
                                <xs:annotation>
                                    <xs:documentation>Use this for non-umbrella funds</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                            <xs:element name="Subfunds" type="xs:string">
                                <xs:annotation>
                                    <xs:documentation>Use this for umbrella funds</xs:documentation>
                                </xs:annotation>
                            </xs:element>
                        </xs:choice>
                    </xs:sequence>
                </xs:complexType>
            
                <xs:element name="Fund" type="FundType"/>
            
            </xs:schema>
            """;

    private XsdDocumentationService service;
    private File xsdFile;

    @BeforeEach
    void setUp() throws Exception {
        xsdFile = tempDir.resolve("test-choice.xsd").toFile();
        Files.writeString(xsdFile.toPath(), TEST_XSD_WITH_CHOICE);

        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.setIncludeTypeDefinitionsInSourceCode(true);
    }

    @Test
    void testChoiceElementInSequence() throws Exception {
        // When - Process the XSD
        service.processXsd(false);

        // Then - Check how CHOICE elements are represented
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();

        System.out.println("===== Extended Element Map Keys =====");
        extendedElementMap.keySet().forEach(System.out::println);
        System.out.println("======================================");

        // Find CHOICE elements
        var choiceElements = extendedElementMap.entrySet().stream()
                .filter(e -> e.getKey().contains("CHOICE"))
                .toList();

        if (!choiceElements.isEmpty()) {
            System.out.println("\n===== CHOICE Element Details =====");
            choiceElements.forEach(entry -> {
                System.out.println("XPath: " + entry.getKey());
                System.out.println("Element Name: " + entry.getValue().getElementName());
                System.out.println("Source Code:");
                System.out.println(entry.getValue().getSourceCode());
                System.out.println("---");
            });
        }

        // Check Fund element with type definition
        var fundElement = extendedElementMap.get("/Fund");
        assertNotNull(fundElement);

        System.out.println("\n===== Fund Element Source Code =====");
        System.out.println(fundElement.getSourceCode());
        System.out.println("====================================");

        // The source code should include the FundType definition with xs:choice
        String sourceCode = fundElement.getReferencedTypeCode();
        assertTrue(sourceCode.contains("<xs:choice"), "Should contain xs:choice element");
        assertTrue(sourceCode.contains("SingleFund"), "Should contain SingleFund option");
        assertTrue(sourceCode.contains("Subfunds"), "Should contain Subfunds option");
    }
}
