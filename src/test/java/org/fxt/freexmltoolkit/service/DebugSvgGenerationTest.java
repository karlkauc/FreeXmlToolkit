package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug test to examine generated SVG output
 */
class DebugSvgGenerationTest {

    @TempDir
    Path tempDir;

    private static final String TEST_XSD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:complexType name="FundType">
                    <xs:sequence>
                        <xs:element name="Name" type="xs:string"/>
                        <xs:element name="FundDynamicData" type="xs:string"/>
                        <xs:choice minOccurs="0">
                            <xs:element name="SingleFund" type="xs:string"/>
                            <xs:element name="Subfunds" type="xs:string"/>
                        </xs:choice>
                    </xs:sequence>
                </xs:complexType>
                <xs:element name="Fund" type="FundType"/>
            </xs:schema>
            """;

    @Test
    void debugSvgOutput() throws Exception {
        File xsdFile = tempDir.resolve("test.xsd").toFile();
        Files.writeString(xsdFile.toPath(), TEST_XSD);

        XsdDocumentationService service = new XsdDocumentationService();
        service.setXsdFilePath(xsdFile.getAbsolutePath());
        service.setIncludeTypeDefinitionsInSourceCode(true);
        service.processXsd(false);

        XsdDocumentationImageService imageService = new XsdDocumentationImageService(
                service.xsdDocumentationData.getExtendedXsdElementMap()
        );

        var fundElement = service.xsdDocumentationData.getExtendedXsdElementMap().get("/Fund");

        System.out.println("\n===== Fund Element Info =====");
        System.out.println("Element: " + fundElement.getElementName());
        System.out.println("Has children: " + fundElement.hasChildren());
        System.out.println("Number of children: " + (fundElement.getChildren() != null ? fundElement.getChildren().size() : 0));

        System.out.println("\n===== Fund Element Children =====");
        if (fundElement.getChildren() != null) {
            fundElement.getChildren().forEach(childXpath -> {
                var child = service.xsdDocumentationData.getExtendedXsdElementMap().get(childXpath);
                System.out.println("Child: " + childXpath + " -> " + child.getElementName() +
                        " | hasChildren: " + child.hasChildren() +
                        " | pageName: " + child.getPageName());

                // If it's a CHOICE, show its children too
                if (child.getElementName().startsWith("CHOICE") && child.getChildren() != null) {
                    System.out.println("  CHOICE children:");
                    child.getChildren().forEach(choiceChildXpath -> {
                        var choiceChild = service.xsdDocumentationData.getExtendedXsdElementMap().get(choiceChildXpath);
                        System.out.println("    " + choiceChildXpath + " -> " + choiceChild.getElementName() +
                                " | hasChildren: " + choiceChild.hasChildren() +
                                " | pageName: " + choiceChild.getPageName());
                    });
                }
            });
        }

        String svgString = imageService.generateSvgString(fundElement);

        // Save to file for inspection
        File svgFile = new File("debug_output.svg");
        Files.writeString(svgFile.toPath(), svgString);

        System.out.println("\n===== SVG Output Saved =====");
        System.out.println("File: " + svgFile.getAbsolutePath());
        System.out.println("SVG length: " + svgString.length());

        // Check for key elements
        System.out.println("\n===== SVG Content Analysis =====");
        System.out.println("Contains 'Fund': " + svgString.contains("Fund"));
        System.out.println("Contains 'Name': " + svgString.contains("Name"));
        System.out.println("Contains 'CHOICE': " + svgString.contains("CHOICE"));
        System.out.println("Contains 'SingleFund': " + svgString.contains("SingleFund"));
        System.out.println("Contains '<use': " + svgString.contains("<use"));
        System.out.println("Contains 'choice-icon': " + svgString.contains("choice-icon"));
        System.out.println("Contains 'sequence-icon': " + svgString.contains("sequence-icon"));
        System.out.println("Contains '<a href': " + svgString.contains("<a href"));
    }
}
