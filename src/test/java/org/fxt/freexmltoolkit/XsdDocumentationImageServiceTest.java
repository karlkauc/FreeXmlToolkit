package org.fxt.freexmltoolkit;

import org.fxt.freexmltoolkit.domain.ExtendedXsdElement;
import org.fxt.freexmltoolkit.service.XsdDocumentationImageService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

class XsdDocumentationImageServiceTest {

    private XsdDocumentationImageService service;
    private Map<String, ExtendedXsdElement> extendedXsdElements;

    @BeforeEach
    void setUp() {
        extendedXsdElements = new HashMap<>();
        service = new XsdDocumentationImageService(extendedXsdElements);
    }

    @Test
    void generateImageWithValidInput() {
        ExtendedXsdElement element = Mockito.mock(ExtendedXsdElement.class);
        Mockito.when(element.getElementName()).thenReturn("TestElement");
        extendedXsdElements.put("/root", element);

        File file = new File("test.jpg");
        String result = service.generateImage("/root", file);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(file.exists());
        // file.delete();
    }

    @Test
    void generateImageWithInvalidInput() {
        File file = new File("test.jpg");
        String result = service.generateImage("/invalid", file);

        Assertions.assertNull(result);
        Assertions.assertFalse(file.exists());
    }

    @Test
    void generateSvgStringWithValidInput() {
        ExtendedXsdElement element = Mockito.mock(ExtendedXsdElement.class);
        Mockito.when(element.getElementName()).thenReturn("TestElement");
        extendedXsdElements.put("/root", element);

        String result = service.generateSvgString("/root");

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("svg"));
    }

    @Test
    void generateSvgStringWithInvalidInput() {
        String result = service.generateSvgString("/invalid");

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void generateSvgDiagramWithValidElement() {
        Document document = Mockito.mock(Document.class);
        Element element = Mockito.mock(Element.class);
        Mockito.when(element.getLocalName()).thenReturn("TestElement");
        Mockito.when(document.getDocumentElement()).thenReturn(element);

        //  result = service.generateSvgDiagram(element);

        // Assertions.assertNotNull(result);
    }

    @Test
    void generateSvgDiagramWithNullElement() {
        // Document result = service.generateSvgDiagram(null);

        // Assertions.assertNotNull(result);
    }
}