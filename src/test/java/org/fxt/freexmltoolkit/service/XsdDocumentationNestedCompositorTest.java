package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for nested CHOICE/SEQUENCE/ALL compositor structures in SVG generation.
 * Verifies that the recursive SVG generation correctly handles nested compositors.
 */
class XsdDocumentationNestedCompositorTest {

    @TempDir
    Path tempDir;

    private static final String TEST_XSD_WITH_NESTED_COMPOSITORS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
            
                <xs:complexType name="FundType">
                    <xs:sequence>
                        <xs:element name="Name" type="xs:string"/>
                        <xs:element name="FundDynamicData" type="xs:string"/>
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
    private XsdDocumentationImageService imageService;
    private File xsdFile;

    @BeforeEach
    void setUp() throws Exception {
        xsdFile = tempDir.resolve("test-nested.xsd").toFile();
        Files.writeString(xsdFile.toPath(), TEST_XSD_WITH_NESTED_COMPOSITORS);

        service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.setIncludeTypeDefinitionsInSourceCode(true);
        service.processXsd(false);

        imageService = new XsdDocumentationImageService(
                service.xsdDocumentationData.getExtendedXsdElementMap()
        );
    }

    @Test
    void testNestedCompositorSvgGeneration() throws Exception {
        // Given - The Fund element with nested SEQUENCE and CHOICE
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        assertNotNull(fundElement, "Fund element should exist");

        // When - Generate SVG string
        String svgString = imageService.generateSvgString(fundElement);

        // Then - SVG string should be created successfully
        assertNotNull(svgString, "SVG string should be generated");
        assertFalse(svgString.isEmpty(), "SVG string should not be empty");
        assertTrue(svgString.contains("<svg"), "Should contain SVG element");
        assertTrue(svgString.contains("Fund"), "Should contain Fund element name");

        System.out.println("\n===== SVG Generation Test Passed =====");
        System.out.println("Fund element with nested compositors generated SVG successfully");
        System.out.println("SVG length: " + svgString.length() + " characters");
    }

    @Test
    void testCompositorStructureInElementMap() throws Exception {
        // Given - The extended element map
        var extendedElementMap = service.xsdDocumentationData.getExtendedXsdElementMap();

        System.out.println("\n===== Element Map Keys =====");
        extendedElementMap.keySet().forEach(key -> {
            var elem = extendedElementMap.get(key);
            System.out.println("XPath: " + key + " -> Element: " + elem.getElementName());
        });
        System.out.println("============================");

        // Then - Verify structure contains CHOICE and SEQUENCE
        boolean hasChoice = extendedElementMap.values().stream()
                .anyMatch(e -> e.getElementName() != null && e.getElementName().startsWith("CHOICE"));
        boolean hasSequence = extendedElementMap.values().stream()
                .anyMatch(e -> e.getElementName() != null && e.getElementName().startsWith("SEQUENCE"));

        assertTrue(hasChoice, "Element map should contain CHOICE compositor");
        assertTrue(hasSequence, "Element map should contain SEQUENCE compositor");

        // Verify that Fund has a SEQUENCE child (which contains the actual elements)
        var fundElement = extendedElementMap.get("/Fund");
        assertNotNull(fundElement);
        assertEquals(1, fundElement.getChildren().size(), "Fund should have 1 child (SEQUENCE)");

        // The SEQUENCE should have 3 children (Name, FundDynamicData, CHOICE)
        String sequenceXPath = fundElement.getChildren().get(0);
        var sequenceElement = extendedElementMap.get(sequenceXPath);
        assertNotNull(sequenceElement, "SEQUENCE element should exist");
        assertEquals(3, sequenceElement.getChildren().size(), "SEQUENCE should have 3 children (Name, FundDynamicData, CHOICE)");

        System.out.println("\n===== Compositor Structure Test Passed =====");
        System.out.println("Element map contains SEQUENCE and CHOICE compositors with correct hierarchy");
    }

    @Test
    void testNestedChoiceWithinFund() throws Exception {
        // Given - The Fund element
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        assertNotNull(fundElement);

        // When - Get the children (Fund has a SEQUENCE child)
        var children = fundElement.getChildren();
        assertNotNull(children, "Fund should have children");
        assertEquals(1, children.size(), "Fund should have 1 child (SEQUENCE)");

        // Get the SEQUENCE element
        var sequenceElement = service.xsdDocumentationData.getExtendedXsdElementMap().get(children.get(0));
        assertNotNull(sequenceElement, "SEQUENCE element should exist");
        assertTrue(sequenceElement.getElementName().startsWith("SEQUENCE"), "First child should be SEQUENCE");

        // The SEQUENCE should have 3 children (Name, FundDynamicData, CHOICE)
        var sequenceChildren = sequenceElement.getChildren();
        assertNotNull(sequenceChildren, "SEQUENCE should have children");
        assertEquals(3, sequenceChildren.size(), "SEQUENCE should have 3 children (Name, FundDynamicData, CHOICE)");

        // Then - Find CHOICE within SEQUENCE children
        var choiceChild = sequenceChildren.stream()
                .map(xpath -> service.xsdDocumentationData.getExtendedXsdElementMap().get(xpath))
                .filter(child -> child != null && child.getElementName() != null
                        && child.getElementName().startsWith("CHOICE"))
                .findFirst()
                .orElse(null);

        assertNotNull(choiceChild, "SEQUENCE should have a CHOICE child");

        // The CHOICE should have children: SingleFund and Subfunds
        var choiceChildren = choiceChild.getChildren();
        assertNotNull(choiceChildren, "CHOICE should have children");
        assertEquals(2, choiceChildren.size(), "CHOICE should have 2 children (SingleFund, Subfunds)");

        // Verify both SingleFund and Subfunds exist
        boolean hasSingleFund = choiceChildren.stream()
                .map(xpath -> service.xsdDocumentationData.getExtendedXsdElementMap().get(xpath))
                .anyMatch(child -> child != null && "SingleFund".equals(child.getElementName()));
        boolean hasSubfunds = choiceChildren.stream()
                .map(xpath -> service.xsdDocumentationData.getExtendedXsdElementMap().get(xpath))
                .anyMatch(child -> child != null && "Subfunds".equals(child.getElementName()));

        assertTrue(hasSingleFund, "CHOICE should have SingleFund child");
        assertTrue(hasSubfunds, "CHOICE should have Subfunds child");

        System.out.println("\n===== Nested Compositor Hierarchy Test Passed =====");
        System.out.println("CHOICE with SingleFund and Subfunds is correctly nested within Fund/SEQUENCE");
    }

    @Test
    void testRecursiveSvgHeightCalculation() throws Exception {
        // Given - The Fund element with nested compositors
        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");
        assertNotNull(fundElement);

        // When - Generate SVG string
        String svgString = imageService.generateSvgString(fundElement);

        // Then - SVG should have valid height attribute
        assertNotNull(svgString, "SVG string should be generated");
        assertTrue(svgString.contains("height="), "SVG should have height attribute");

        // Extract height value using regex
        String heightPattern = "height=\"([0-9.]+)\"";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(heightPattern);
        java.util.regex.Matcher matcher = pattern.matcher(svgString);

        assertTrue(matcher.find(), "Should find height attribute");
        double height = Double.parseDouble(matcher.group(1));

        assertTrue(height > 0, "SVG height should be positive");
        assertTrue(height > 100, "SVG height should be reasonable for nested structure");

        System.out.println("\n===== Height Calculation Test Passed =====");
        System.out.println("SVG height: " + height);
    }
}
