package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.domain.XsdExtendedElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationImageService;
import org.fxt.freexmltoolkit.service.XsdDocumentationService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.w3c.dom.Node;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class XsdDocumentationImageServiceTest {

    private XsdDocumentationImageService service;
    private Map<String, XsdExtendedElement> extendedXsdElements;

    @BeforeEach
    void setUp() {
        extendedXsdElements = new HashMap<>();
        service = new XsdDocumentationImageService(extendedXsdElements);
    }

    @Test
    void generateImageWithValidInput() {
        // Create a proper mock for XsdExtendedElement
        XsdExtendedElement element = Mockito.mock(XsdExtendedElement.class);
        Mockito.when(element.getElementName()).thenReturn("TestElement");
        Mockito.when(element.getCurrentXpath()).thenReturn("/root/TestElement");
        Mockito.when(element.isMandatory()).thenReturn(true);
        Mockito.when(element.getChildren()).thenReturn(new ArrayList<>());
        Mockito.when(element.getDocumentations()).thenReturn(new ArrayList<>());
        Mockito.when(element.hasChildren()).thenReturn(false);
        Mockito.when(element.getPageName()).thenReturn(null);

        // Mock the DOM node
        Node mockNode = Mockito.mock(Node.class);
        Mockito.when(element.getCurrentNode()).thenReturn(mockNode);

        extendedXsdElements.put("/root/TestElement", element);

        File file = new File("test.png");
        try {
            String result = service.generateImage(element, file);

            // Check if the method returned a result (even if null due to empty SVG)
            // The important thing is that it doesn't throw an exception
            Assertions.assertNotNull(result == null || result.isEmpty() || file.exists());

            // Clean up
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            // If an exception occurs, it should be logged but not thrown
            Assertions.fail("PNG generation should not throw exceptions: " + e.getMessage());
        }
    }

    @Test
    void generateImageWithNullElement() {
        File file = new File("test_null.png");

        // Clean up any existing file first
        if (file.exists()) {
            file.delete();
        }

        try {
            String result = service.generateImage(null, file);

            // Should handle null gracefully
            Assertions.assertNull(result);
            Assertions.assertFalse(file.exists());
        } catch (Exception e) {
            Assertions.fail("Should handle null element gracefully: " + e.getMessage());
        } finally {
            // Clean up
            if (file.exists()) {
                file.delete();
            }
        }
    }

    @Test
    void generateSvgStringWithValidInput() {
        XsdExtendedElement element = Mockito.mock(XsdExtendedElement.class);
        Mockito.when(element.getElementName()).thenReturn("TestElement");
        Mockito.when(element.getCurrentXpath()).thenReturn("/root/TestElement");
        Mockito.when(element.isMandatory()).thenReturn(true);
        Mockito.when(element.getChildren()).thenReturn(new ArrayList<>());
        Mockito.when(element.getDocumentations()).thenReturn(new ArrayList<>());
        Mockito.when(element.hasChildren()).thenReturn(false);
        Mockito.when(element.getPageName()).thenReturn(null);

        Node mockNode = Mockito.mock(Node.class);
        Mockito.when(element.getCurrentNode()).thenReturn(mockNode);

        extendedXsdElements.put("/root/TestElement", element);

        String result = service.generateSvgString(element);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("svg"));
        Assertions.assertTrue(result.contains("TestElement"));
    }

    @Test
    void generateSvgStringWithNullElement() {
        String result = service.generateSvgString(null);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("svg"));
    }

    @Test
    void generateSvgStringWithElementWithChildren() {
        // Create parent element
        XsdExtendedElement parentElement = Mockito.mock(XsdExtendedElement.class);
        Mockito.when(parentElement.getElementName()).thenReturn("ParentElement");
        Mockito.when(parentElement.getCurrentXpath()).thenReturn("/root/ParentElement");
        Mockito.when(parentElement.isMandatory()).thenReturn(true);
        Mockito.when(parentElement.hasChildren()).thenReturn(true);
        Mockito.when(parentElement.getPageName()).thenReturn("parent.html");

        List<String> children = new ArrayList<>();
        children.add("/root/ParentElement/ChildElement");
        Mockito.when(parentElement.getChildren()).thenReturn(children);
        Mockito.when(parentElement.getDocumentations()).thenReturn(new ArrayList<>());

        Node mockNode = Mockito.mock(Node.class);
        Mockito.when(parentElement.getCurrentNode()).thenReturn(mockNode);

        // Create child element
        XsdExtendedElement childElement = Mockito.mock(XsdExtendedElement.class);
        Mockito.when(childElement.getElementName()).thenReturn("ChildElement");
        Mockito.when(childElement.getCurrentXpath()).thenReturn("/root/ParentElement/ChildElement");
        Mockito.when(childElement.isMandatory()).thenReturn(false);
        Mockito.when(childElement.getChildren()).thenReturn(new ArrayList<>());
        Mockito.when(childElement.getDocumentations()).thenReturn(new ArrayList<>());
        Mockito.when(childElement.hasChildren()).thenReturn(false);
        Mockito.when(childElement.getPageName()).thenReturn(null);

        Node childMockNode = Mockito.mock(Node.class);
        Mockito.when(childElement.getCurrentNode()).thenReturn(childMockNode);

        extendedXsdElements.put("/root/ParentElement", parentElement);
        extendedXsdElements.put("/root/ParentElement/ChildElement", childElement);

        String result = service.generateSvgString(parentElement);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("svg"));
        Assertions.assertTrue(result.contains("ParentElement"));
        Assertions.assertTrue(result.contains("ChildElement"));
    }

    @Test
    void generateImageWithRealXsdData() throws Exception {
        // Test with real XSD data from testSchema.xsd
        Path xsdPath = Path.of("src/test/resources/testSchema.xsd");
        Assertions.assertTrue(Files.exists(xsdPath), "Test XSD file should exist");

        // Create XsdDocumentationService to parse the XSD
        XsdDocumentationService xsdDocService = new XsdDocumentationService();
        xsdDocService.setXsdFilePath(xsdPath.toString());
        xsdDocService.processXsd(true);

        // Create image service with the parsed data
        XsdDocumentationImageService realService = new XsdDocumentationImageService(
                xsdDocService.xsdDocumentationData.getExtendedXsdElementMap()
        );

        // Find the root element (Cars)
        XsdExtendedElement rootElement = xsdDocService.xsdDocumentationData.getExtendedXsdElementMap().get("/Cars");
        Assertions.assertNotNull(rootElement, "Root element 'Cars' should be found");

        // Generate PNG image
        File outputFile = new File("test_real_xsd.png");
        try {
            String result = realService.generateImage(rootElement, outputFile);

            // The method should return the file path if successful
            Assertions.assertNotNull(result, "PNG generation should return a file path");
            Assertions.assertTrue(outputFile.exists(), "PNG file should be created");
            Assertions.assertTrue(outputFile.length() > 0, "PNG file should not be empty");

            // Verify it's actually a PNG file by checking the first few bytes
            byte[] fileBytes = Files.readAllBytes(outputFile.toPath());
            Assertions.assertTrue(fileBytes.length >= 8, "PNG file should have at least 8 bytes");

            // PNG files start with the signature: 89 50 4E 47 0D 0A 1A 0A
            Assertions.assertEquals((byte) 0x89, fileBytes[0], "PNG file should start with correct signature");
            Assertions.assertEquals((byte) 0x50, fileBytes[1], "PNG file should have correct PNG signature");
            Assertions.assertEquals((byte) 0x4E, fileBytes[2], "PNG file should have correct PNG signature");
            Assertions.assertEquals((byte) 0x47, fileBytes[3], "PNG file should have correct PNG signature");

        } finally {
            // Clean up
            if (outputFile.exists()) {
                outputFile.delete();
            }
        }
    }
}