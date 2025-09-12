package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for the refactored XmlCodeEditor.
 * Tests verify that the basic functionality is preserved after refactoring.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class XmlCodeEditorTest {

    private XmlCodeEditor editor;

    @BeforeAll
    public static void initJavaFX() {
        // Initialize JavaFX toolkit
        new JFXPanel();
    }

    @BeforeEach
    public void setUp() {
        Platform.runLater(() -> {
            editor = new XmlCodeEditor();
        });

        // Wait for JavaFX to complete initialization
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void testInitialization() {
        Platform.runLater(() -> {
            assertNotNull(editor, "Editor should be initialized");
            assertNotNull(editor.getCodeArea(), "CodeArea should be initialized");
            assertEquals("", editor.getText(), "Initial text should be empty");
            assertEquals(XmlCodeEditor.EditorMode.XML_WITHOUT_XSD, editor.getEditorMode(),
                    "Default mode should be XML_WITHOUT_XSD");
        });
    }

    @Test
    public void testTextSetAndGet() {
        String testXml = "<?xml version=\"1.0\"?><root><child>content</child></root>";

        Platform.runLater(() -> {
            editor.setText(testXml);
            assertEquals(testXml, editor.getText(), "Text should be set and retrieved correctly");
        });
    }

    @Test
    public void testDocumentUri() {
        String testUri = "file:///test/document.xml";

        Platform.runLater(() -> {
            editor.setDocumentUri(testUri);
            assertEquals(testUri, editor.getDocumentUri(), "Document URI should be set correctly");
        });
    }

    @Test
    public void testEditorModeChanges() {
        Platform.runLater(() -> {
            // Test mode changes
            editor.setEditorMode(XmlCodeEditor.EditorMode.XML_WITH_XSD);
            assertEquals(XmlCodeEditor.EditorMode.XML_WITH_XSD, editor.getEditorMode(),
                    "Mode should change to XML_WITH_XSD");

            editor.setEditorMode(XmlCodeEditor.EditorMode.SCHEMATRON);
            assertEquals(XmlCodeEditor.EditorMode.SCHEMATRON, editor.getEditorMode(),
                    "Mode should change to SCHEMATRON");

            editor.setEditorMode(XmlCodeEditor.EditorMode.XSD);
            assertEquals(XmlCodeEditor.EditorMode.XSD, editor.getEditorMode(),
                    "Mode should change to XSD");
        });
    }

    @Test
    public void testFontSizeOperations() {
        Platform.runLater(() -> {
            // Test font size increase
            int initialSize = 11; // DEFAULT_FONT_SIZE
            editor.increaseFontSize();
            // We can't easily test the actual font size change without accessing private fields
            // But we can test that the methods don't throw exceptions

            editor.decreaseFontSize();
            editor.resetFontSize();
            // Should not throw any exceptions
        });
    }

    @Test
    public void testCursorMovement() {
        String testXml = "<?xml version=\"1.0\"?>\n<root>\n  <child>content</child>\n</root>";

        Platform.runLater(() -> {
            editor.setText(testXml);

            // Test cursor movement
            editor.moveUp();
            assertEquals(0, editor.getCodeArea().getCaretPosition(),
                    "Cursor should be at beginning after moveUp");

            editor.moveDown();
            assertEquals(testXml.length(), editor.getCodeArea().getCaretPosition(),
                    "Cursor should be at end after moveDown");
        });
    }

    @Test
    public void testSyntaxHighlightingRefresh() {
        String testXml = "<?xml version=\"1.0\"?><root><element>value</element></root>";

        Platform.runLater(() -> {
            editor.setText(testXml);

            // Test syntax highlighting refresh - should not throw exceptions
            editor.refreshHighlighting();
            editor.refreshSyntaxHighlighting();
        });
    }

    @Test
    public void testFileMonitoringIntegration() {
        Platform.runLater(() -> {
            // Test file monitoring methods don't throw exceptions
            java.io.File testFile = new java.io.File("test.xml");
            editor.setCurrentFile(testFile);
            editor.notifyFileSaved();
        });
    }

    @Test
    public void testAllEditorModes() {
        Platform.runLater(() -> {
            // Test all editor modes can be set without throwing exceptions
            for (XmlCodeEditor.EditorMode mode : XmlCodeEditor.EditorMode.values()) {
                editor.setEditorMode(mode);
                assertEquals(mode, editor.getEditorMode(), "Mode should be set to " + mode);
            }
        });
    }

    @Test
    public void testEmptyContentHandling() {
        Platform.runLater(() -> {
            // Test handling of null and empty content
            editor.setText(null);
            editor.setText("");
            editor.setText("   ");

            // Should not throw exceptions
            editor.refreshHighlighting();
        });
    }

    @Test
    public void testLargeContentHandling() {
        Platform.runLater(() -> {
            // Test with larger XML content
            StringBuilder largeXml = new StringBuilder();
            largeXml.append("<?xml version=\"1.0\"?>\n<root>\n");
            for (int i = 0; i < 100; i++) {
                largeXml.append("  <element").append(i).append(">value").append(i).append("</element").append(i).append(">\n");
            }
            largeXml.append("</root>");

            editor.setText(largeXml.toString());
            assertEquals(largeXml.toString(), editor.getText(), "Large XML should be handled correctly");
        });
    }
}