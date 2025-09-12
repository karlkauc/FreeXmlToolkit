package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for context menu functionality in XmlGraphicEditor.
 * Tests the new "Copy Value" and "Copy Node" menu items.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class XmlGraphicEditorContextMenuTest {

    private Document testDocument;
    private XmlGraphicEditor xmlGraphicEditor;

    @BeforeAll
    void setupJavaFX() throws Exception {
        // Initialize JavaFX Toolkit for testing
        Platform.startup(() -> {
        });

        // Create test XML document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        testDocument = builder.newDocument();

        // Create test structure: <root><child>Test Value</child></root>
        Element rootElement = testDocument.createElement("root");
        testDocument.appendChild(rootElement);

        Element childElement = testDocument.createElement("child");
        childElement.setTextContent("Test Value");
        rootElement.appendChild(childElement);

        // Add attribute to child element
        childElement.setAttribute("testAttr", "Attribute Value");

        // Create a mock XmlEditor for testing
        XmlEditor mockXmlEditor = Mockito.mock(XmlEditor.class);

        // Create XmlGraphicEditor instance for testing
        xmlGraphicEditor = new XmlGraphicEditor(rootElement, mockXmlEditor);
    }

    @Test
    void testGetNodeTextContentForElement() throws Exception {
        Element childElement = (Element) testDocument.getElementsByTagName("child").item(0);

        // Use reflection to access private method
        var method = XmlGraphicEditor.class.getDeclaredMethod("getNodeTextContent", Node.class);
        method.setAccessible(true);

        String textContent = (String) method.invoke(xmlGraphicEditor, childElement);
        assertEquals("Test Value", textContent);
    }

    @Test
    void testGetNodeTextContentForAttribute() throws Exception {
        Element childElement = (Element) testDocument.getElementsByTagName("child").item(0);
        Node attributeNode = childElement.getAttributeNode("testAttr");

        // Use reflection to access private method
        var method = XmlGraphicEditor.class.getDeclaredMethod("getNodeTextContent", Node.class);
        method.setAccessible(true);

        String textContent = (String) method.invoke(xmlGraphicEditor, attributeNode);
        assertEquals("Attribute Value", textContent);
    }

    @Test
    void testNodeToXmlString() throws Exception {
        Element childElement = (Element) testDocument.getElementsByTagName("child").item(0);

        // Use reflection to access private method
        var method = XmlGraphicEditor.class.getDeclaredMethod("nodeToXmlString", Node.class);
        method.setAccessible(true);

        String xmlString = (String) method.invoke(xmlGraphicEditor, childElement);

        // Check that the XML contains the expected elements
        assertTrue(xmlString.contains("<child"));
        assertTrue(xmlString.contains("testAttr=\"Attribute Value\""));
        assertTrue(xmlString.contains("Test Value"));
        assertTrue(xmlString.contains("</child>"));
    }

    @Test
    void testCopyNodeValueToClipboardWithTextContent() throws Exception {
        Element childElement = (Element) testDocument.getElementsByTagName("child").item(0);

        Platform.runLater(() -> {
            try {
                // Clear clipboard first
                ClipboardContent emptyContent = new ClipboardContent();
                emptyContent.putString("");
                Clipboard.getSystemClipboard().setContent(emptyContent);

                // Use reflection to access private method
                var method = XmlGraphicEditor.class.getDeclaredMethod("copyNodeValueToClipboard", Node.class);
                method.setAccessible(true);
                method.invoke(xmlGraphicEditor, childElement);

                // Check clipboard content
                String clipboardContent = Clipboard.getSystemClipboard().getString();
                assertEquals("Test Value", clipboardContent);
            } catch (Exception e) {
                fail("Exception during clipboard test: " + e.getMessage());
            }
        });

        // Wait for Platform.runLater to complete
        Thread.sleep(100);
    }

    @Test
    void testCopyNodeToClipboard() throws Exception {
        Element childElement = (Element) testDocument.getElementsByTagName("child").item(0);

        Platform.runLater(() -> {
            try {
                // Clear clipboard first
                ClipboardContent emptyContent = new ClipboardContent();
                emptyContent.putString("");
                Clipboard.getSystemClipboard().setContent(emptyContent);

                // Use reflection to access private method
                var method = XmlGraphicEditor.class.getDeclaredMethod("copyNodeToClipboard", Node.class);
                method.setAccessible(true);
                method.invoke(xmlGraphicEditor, childElement);

                // Check clipboard content
                String clipboardContent = Clipboard.getSystemClipboard().getString();
                assertNotNull(clipboardContent);
                assertTrue(clipboardContent.contains("<child"));
                assertTrue(clipboardContent.contains("testAttr=\"Attribute Value\""));
                assertTrue(clipboardContent.contains("Test Value"));
                assertTrue(clipboardContent.contains("</child>"));
            } catch (Exception e) {
                fail("Exception during clipboard test: " + e.getMessage());
            }
        });

        // Wait for Platform.runLater to complete
        Thread.sleep(100);
    }

    @Test
    void testCopyNodeValueToClipboardWithEmptyContent() throws Exception {
        // Create element with no text content
        Element emptyElement = testDocument.createElement("empty");
        testDocument.getDocumentElement().appendChild(emptyElement);

        Platform.runLater(() -> {
            try {
                // Use reflection to access private method
                var method = XmlGraphicEditor.class.getDeclaredMethod("copyNodeValueToClipboard", Node.class);
                method.setAccessible(true);

                // This should handle empty content gracefully (show info dialog)
                assertDoesNotThrow(() -> {
                    try {
                        method.invoke(xmlGraphicEditor, emptyElement);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                fail("Exception during empty content test: " + e.getMessage());
            }
        });

        // Wait for Platform.runLater to complete
        Thread.sleep(100);
    }
}