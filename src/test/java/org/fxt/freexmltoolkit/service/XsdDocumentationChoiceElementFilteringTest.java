package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CHOICE/SEQUENCE/ALL element filtering in XSD documentation.
 * Verifies that compositor container elements are filtered out from HTML generation
 * and replaced with their actual child elements.
 */
class XsdDocumentationChoiceElementFilteringTest {

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
    private XsdDocumentationHtmlService htmlService;
    private File xsdFile;

    @BeforeEach
    void setUp() throws Exception {
        xsdFile = tempDir.resolve("test-choice.xsd").toFile();
        Files.writeString(xsdFile.toPath(), TEST_XSD_WITH_CHOICE);

        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.setIncludeTypeDefinitionsInSourceCode(true);
        service.processXsd(false);

        htmlService = new XsdDocumentationHtmlService();
        htmlService.setDocumentationData(service.xsdDocumentationData);
        htmlService.setXsdDocumentationService(service);
    }

    @Test
    void testChoiceContainerElementIsFilteredFromMap() throws Exception {
        // Given - The XSD is processed
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();

        // When - Check for CHOICE elements in the map
        var choiceElements = extendedElementMap.entrySet().stream()
                .filter(e -> e.getValue().getElementName() != null &&
                        e.getValue().getElementName().startsWith("CHOICE"))
                .toList();

        // Then - CHOICE elements exist in the map (for SVG generation)
        assertFalse(choiceElements.isEmpty(), "CHOICE elements should exist in the element map for SVG visualization");

        System.out.println("\n===== CHOICE Elements in Map =====");
        choiceElements.forEach(entry -> {
            System.out.println("XPath: " + entry.getKey());
            System.out.println("Element Name: " + entry.getValue().getElementName());
        });
        System.out.println("===================================");
    }

    @Test
    void testFlattenedChildrenDoesNotIncludeChoiceContainer() throws Exception {
        // Given - The Fund element with CHOICE children
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        assertNotNull(fundElement, "Fund element should exist");

        // When - Get flattened children
        List<String> flattenedChildren = htmlService.getFlattenedChildren(fundElement);

        System.out.println("\n===== Fund Element Original Children =====");
        fundElement.getChildren().forEach(System.out::println);
        System.out.println("===========================================");

        System.out.println("\n===== Fund Element Flattened Children =====");
        flattenedChildren.forEach(System.out::println);
        System.out.println("============================================");

        // Then - Flattened children should not contain CHOICE containers
        for (String childXpath : flattenedChildren) {
            var child = service.xsdDocumentationData.getExtendedXsdElementMap().get(childXpath);
            assertNotNull(child, "Child element should exist: " + childXpath);

            String childName = child.getElementName();
            assertFalse(childName.startsWith("CHOICE"),
                    "Flattened children should not include CHOICE containers: " + childXpath);
            assertFalse(childName.startsWith("SEQUENCE"),
                    "Flattened children should not include SEQUENCE containers: " + childXpath);
            assertFalse(childName.startsWith("ALL"),
                    "Flattened children should not include ALL containers: " + childXpath);
        }

        // Verify the actual children we expect
        assertTrue(flattenedChildren.stream().anyMatch(xpath -> xpath.contains("Name")),
                "Flattened children should include Name element");
        assertTrue(flattenedChildren.stream().anyMatch(xpath -> xpath.contains("SingleFund")),
                "Flattened children should include SingleFund from the CHOICE");
        assertTrue(flattenedChildren.stream().anyMatch(xpath -> xpath.contains("Subfunds")),
                "Flattened children should include Subfunds from the CHOICE");
    }

    @Test
    void testCompositorTypeDetection() throws Exception {
        // Given - Elements from the CHOICE group
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        List<String> flattenedChildren = htmlService.getFlattenedChildren(fundElement);

        System.out.println("\n===== Compositor Type Detection =====");
        for (String childXpath : flattenedChildren) {
            String compositorType = htmlService.getChildCompositorType(childXpath);
            var child = service.xsdDocumentationData.getExtendedXsdElementMap().get(childXpath);
            System.out.println("Element: " + child.getElementName() +
                    " - Compositor: " + (compositorType != null ? compositorType : "None"));
        }
        System.out.println("======================================");

        // Then - SingleFund and Subfunds should be detected as CHOICE elements
        String singleFundXpath = flattenedChildren.stream()
                .filter(xpath -> xpath.contains("SingleFund"))
                .findFirst()
                .orElse(null);
        assertNotNull(singleFundXpath, "SingleFund should be in flattened children");
        assertEquals("CHOICE", htmlService.getChildCompositorType(singleFundXpath),
                "SingleFund should be detected as part of a CHOICE");

        String subfundsXpath = flattenedChildren.stream()
                .filter(xpath -> xpath.contains("Subfunds"))
                .findFirst()
                .orElse(null);
        assertNotNull(subfundsXpath, "Subfunds should be in flattened children");
        assertEquals("CHOICE", htmlService.getChildCompositorType(subfundsXpath),
                "Subfunds should be detected as part of a CHOICE");

        // Name is inside a SEQUENCE compositor
        String nameXpath = flattenedChildren.stream()
                .filter(xpath -> xpath.contains("Name"))
                .findFirst()
                .orElse(null);
        assertNotNull(nameXpath, "Name should be in flattened children");
        assertEquals("SEQUENCE", htmlService.getChildCompositorType(nameXpath),
                "Name should be detected as part of a SEQUENCE");
    }

    @Test
    void testIsNotContainerElement() throws Exception {
        // Given - Various elements from the map
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        var nameElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> "Name".equals(e.getElementName()))
                .findFirst()
                .orElse(null);
        var choiceElement = service.xsdDocumentationData.getExtendedXsdElementMap().values().stream()
                .filter(e -> e.getElementName() != null && e.getElementName().startsWith("CHOICE"))
                .findFirst()
                .orElse(null);

        assertNotNull(fundElement, "Fund element should exist");
        assertNotNull(nameElement, "Name element should exist");
        assertNotNull(choiceElement, "CHOICE element should exist");

        // When/Then - Check filtering logic
        // Note: We can't directly test isNotContainerElement since it's private,
        // but we can verify through the flattened children list
        List<String> flattenedChildren = htmlService.getFlattenedChildren(fundElement);

        // Regular elements should be in flattened children
        assertTrue(flattenedChildren.stream().anyMatch(xpath -> xpath.contains("Name")),
                "Regular elements should be included");

        // CHOICE containers should not be in flattened children
        assertFalse(flattenedChildren.stream().anyMatch(xpath -> {
            var elem = service.xsdDocumentationData.getExtendedXsdElementMap().get(xpath);
            return elem != null && elem.getElementName() != null &&
                    elem.getElementName().startsWith("CHOICE");
        }), "CHOICE containers should be filtered out");
    }

    @Test
    void testSourceCodeStillContainsChoiceElements() throws Exception {
        // Given - The Fund element
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        assertNotNull(fundElement);

        // When - Get the source code
        String sourceCode = fundElement.getReferencedTypeCode();

        // Then - The source code should still show the <xs:choice> element correctly
        assertTrue(sourceCode.contains("<xs:choice"), "Source code should contain xs:choice element");
        assertTrue(sourceCode.contains("SingleFund"), "Source code should contain SingleFund option");
        assertTrue(sourceCode.contains("Subfunds"), "Source code should contain Subfunds option");

        System.out.println("\n===== Fund Element Source Code =====");
        System.out.println(sourceCode);
        System.out.println("====================================");
    }
}
