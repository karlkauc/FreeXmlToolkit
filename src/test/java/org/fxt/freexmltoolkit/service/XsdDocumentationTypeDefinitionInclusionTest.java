package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the type definition inclusion feature in XSD documentation.
 * Verifies that when enabled, referenced type definitions are included in the source code display.
 */
class XsdDocumentationTypeDefinitionInclusionTest {

    @TempDir
    Path tempDir;

    private static final String TEST_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
            
                <xs:complexType name="FundType">
                    <xs:sequence>
                        <xs:element name="FundName" type="xs:string"/>
                        <xs:element name="FundValue" type="xs:decimal"/>
                    </xs:sequence>
                </xs:complexType>
            
                <xs:simpleType name="CodeType">
                    <xs:restriction base="xs:string">
                        <xs:minLength value="3"/>
                        <xs:maxLength value="10"/>
                    </xs:restriction>
                </xs:simpleType>
            
                <xs:element name="Fund" type="FundType">
                    <xs:annotation>
                        <xs:documentation>A fund element using FundType</xs:documentation>
                    </xs:annotation>
                </xs:element>
            
                <xs:element name="Code" type="CodeType">
                    <xs:annotation>
                        <xs:documentation>A code element using CodeType</xs:documentation>
                    </xs:annotation>
                </xs:element>
            
                <xs:element name="SimpleElement" type="xs:string">
                    <xs:annotation>
                        <xs:documentation>An element with built-in type</xs:documentation>
                    </xs:annotation>
                </xs:element>
            
            </xs:schema>
            """;

    private XsdDocumentationService service;
    private File xsdFile;

    @BeforeEach
    void setUp() throws Exception {
        // Create a temporary XSD file
        xsdFile = tempDir.resolve("test.xsd").toFile();
        Files.writeString(xsdFile.toPath(), TEST_XSD);

        // Initialize the service
        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
    }

    @Test
    void testTypeDefinitionInclusionDisabled() throws Exception {
        // Given - Type definition inclusion is disabled (default)
        service.setIncludeTypeDefinitionsInSourceCode(false);

        // When - Process the XSD
        service.processXsd(false);

        // Then - Verify that the Fund element's source code does NOT include FundType definition
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();
        var fundElement = extendedElementMap.get("/Fund");

        assertNotNull(fundElement, "Fund element should be in the map");
        String sourceCode = fundElement.getSourceCode();
        assertNotNull(sourceCode, "Source code should not be null");

        // Source code should contain the element definition
        assertTrue(sourceCode.contains("name=\"Fund\""), "Should contain element name");
        assertTrue(sourceCode.contains("type=\"FundType\""), "Should contain type reference");

        // But should NOT contain the type definition itself
        assertFalse(sourceCode.contains("Referenced Type Definition"),
                "Should not contain type definition marker comment");
        assertFalse(sourceCode.contains("FundName"),
                "Should not contain FundType's child elements");
    }

    @Test
    void testTypeDefinitionInclusionEnabledForComplexType() throws Exception {
        // Given - Type definition inclusion is enabled
        service.setIncludeTypeDefinitionsInSourceCode(true);

        // When - Process the XSD
        service.processXsd(false);

        // Then - Verify that the Fund element's source code INCLUDES FundType definition
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();
        var fundElement = extendedElementMap.get("/Fund");

        assertNotNull(fundElement, "Fund element should be in the map");
        String sourceCode = fundElement.getSourceCode();
        assertNotNull(sourceCode, "Source code should not be null");

        // Debug output
        System.out.println("===== Fund element source code =====");
        System.out.println(sourceCode);
        System.out.println("=====================================");

        // Source code should contain the element definition
        assertTrue(sourceCode.contains("name=\"Fund\""), "Should contain element name");
        assertTrue(sourceCode.contains("type=\"FundType\""), "Should contain type reference");

        // AND should contain the type definition itself
        assertTrue(sourceCode.contains("Element Definition"),
                "Should contain element definition marker comment");
        assertTrue(sourceCode.contains("Referenced Type Definition: FundType"),
                "Should contain type definition marker comment");
        assertTrue(sourceCode.contains("complexType") && sourceCode.contains("name=\"FundType\""),
                "Should contain FundType definition");
        assertTrue(sourceCode.contains("FundName"),
                "Should contain FundType's child elements");
        assertTrue(sourceCode.contains("FundValue"),
                "Should contain FundType's child elements");
    }

    @Test
    void testTypeDefinitionInclusionEnabledForSimpleType() throws Exception {
        // Given - Type definition inclusion is enabled
        service.setIncludeTypeDefinitionsInSourceCode(true);

        // When - Process the XSD
        service.processXsd(false);

        // Then - Verify that the Code element's source code INCLUDES CodeType definition
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();
        var codeElement = extendedElementMap.get("/Code");

        assertNotNull(codeElement, "Code element should be in the map");
        String sourceCode = codeElement.getSourceCode();
        assertNotNull(sourceCode, "Source code should not be null");

        // Debug output
        System.out.println("===== Code element source code =====");
        System.out.println(sourceCode);
        System.out.println("=====================================");

        // Source code should contain the element definition
        assertTrue(sourceCode.contains("name=\"Code\""), "Should contain element name");
        assertTrue(sourceCode.contains("type=\"CodeType\""), "Should contain type reference");

        // AND should contain the type definition itself
        assertTrue(sourceCode.contains("Referenced Type Definition: CodeType"),
                "Should contain type definition marker comment");
        assertTrue(sourceCode.contains("simpleType") && sourceCode.contains("name=\"CodeType\""),
                "Should contain CodeType definition");
        assertTrue(sourceCode.contains("minLength"),
                "Should contain restriction facets");
        assertTrue(sourceCode.contains("maxLength"),
                "Should contain restriction facets");
    }

    @Test
    void testBuiltInTypeNotIncluded() throws Exception {
        // Given - Type definition inclusion is enabled
        service.setIncludeTypeDefinitionsInSourceCode(true);

        // When - Process the XSD
        service.processXsd(false);

        // Then - Verify that elements with built-in types (xs:string) do NOT include type definition
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();
        var simpleElement = extendedElementMap.get("/SimpleElement");

        assertNotNull(simpleElement, "SimpleElement should be in the map");
        String sourceCode = simpleElement.getSourceCode();
        assertNotNull(sourceCode, "Source code should not be null");

        // Source code should contain the element definition
        assertTrue(sourceCode.contains("name=\"SimpleElement\""), "Should contain element name");
        assertTrue(sourceCode.contains("type=\"xs:string\""), "Should contain type reference");

        // But should NOT try to include the built-in type definition
        assertFalse(sourceCode.contains("Referenced Type Definition"),
                "Should not contain type definition marker for built-in types");
    }

    @Test
    void testSetterGetter() {
        // Test the setter and getter methods
        service.setIncludeTypeDefinitionsInSourceCode(true);
        assertTrue(service.includeTypeDefinitionsInSourceCode, "Should be enabled");

        service.setIncludeTypeDefinitionsInSourceCode(false);
        assertFalse(service.includeTypeDefinitionsInSourceCode, "Should be disabled");
    }
}
