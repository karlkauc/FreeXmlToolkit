package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XmlCodeEditor syntax highlighting functionality.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorSyntaxHighlightingTest {

    private XmlCodeEditor xmlCodeEditor;

    @Start
    private void start(Stage stage) {
        xmlCodeEditor = new XmlCodeEditor();
        stage.show();
    }

    @BeforeEach
    void setUp() {
        Platform.runLater(() -> {
            xmlCodeEditor.getCodeArea().clear();
        });
    }

    @Test
    void testComputeHighlightingWithBasicXml() {
        String testXml = "<root>content</root>";

        StyleSpans<Collection<String>> result = XmlCodeEditor.computeHighlighting(testXml);

        assertNotNull(result, "Syntax highlighting should return a result");
        assertTrue(result.getSpanCount() > 0, "Should have style spans for XML content");

        System.out.println("Test XML: " + testXml);
        System.out.println("Number of spans: " + result.getSpanCount());

        // Check that we have multiple spans (indicating highlighting is working)
        assertTrue(result.getSpanCount() >= 3, "Should have at least 3 spans for opening tag, content, and closing tag");
    }

    @Test
    void testComputeHighlightingWithAttributes() {
        String testXml = "<root attribute=\"value\">content</root>";

        StyleSpans<Collection<String>> result = XmlCodeEditor.computeHighlighting(testXml);

        assertNotNull(result, "Syntax highlighting should return a result");
        assertTrue(result.getSpanCount() > 0, "Should have style spans for XML with attributes");

        System.out.println("Test XML with attributes: " + testXml);
        System.out.println("Number of spans: " + result.getSpanCount());

        // Check that we have even more spans when attributes are present
        assertTrue(result.getSpanCount() >= 5, "Should have more spans for XML with attributes");
    }

    @Test
    void testComputeHighlightingWithComments() {
        String testXml = "<!-- comment --><root>content</root>";

        StyleSpans<Collection<String>> result = XmlCodeEditor.computeHighlighting(testXml);

        assertNotNull(result, "Syntax highlighting should return a result");
        assertTrue(result.getSpanCount() > 0, "Should have style spans for XML with comments");

        System.out.println("Test XML with comment: " + testXml);
        System.out.println("Number of spans: " + result.getSpanCount());
    }

    @Test
    void testTextChangeListenerAppliesHighlighting() {
        Platform.runLater(() -> {
            // Clear and set new content
            String testXml = "<test>content</test>";
            xmlCodeEditor.getCodeArea().replaceText(testXml);

            // The text change listener should have applied highlighting
            // We can't directly check the styles in a unit test, but we can verify
            // that the text was set and the highlighting method doesn't crash
            assertEquals(testXml, xmlCodeEditor.getCodeArea().getText());

            System.out.println("Text change listener test completed successfully");
        });
    }

    @Test
    void testEmptyStringHandling() {
        String emptyText = "";

        StyleSpans<Collection<String>> result = XmlCodeEditor.computeHighlighting(emptyText);

        assertNotNull(result, "Should handle empty string without crashing");
        assertEquals(1, result.getSpanCount(), "Empty string should result in one empty span");
    }

    @Test
    void testCssInjection() {
        Platform.runLater(() -> {
            try {
                // Create a simple stage to test CSS injection
                Stage testStage = new Stage();
                testStage.setTitle("CSS Injection Test");

                // Create XmlCodeEditor
                XmlCodeEditor editor = new XmlCodeEditor();

                // Set test content
                String testXml = "<root><element attribute=\"value\">content</element></root>";
                editor.getCodeArea().replaceText(testXml);

                // Apply syntax highlighting
                editor.refreshSyntaxHighlighting();

                // Debug CSS status
                editor.debugCssStatus();

                // Create scene and show stage
                javafx.scene.Scene scene = new javafx.scene.Scene(editor, 800, 600);
                testStage.setScene(scene);
                testStage.show();

                // Close stage after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(2000); // Show for 2 seconds
                        Platform.runLater(() -> testStage.close());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                System.out.println("CSS injection test completed");

            } catch (Exception e) {
                System.err.println("Error in CSS injection test: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Test
    void testSyntaxHighlightingInSimpleApp() {
        Platform.runLater(() -> {
            try {
                // Create a simple stage to test the XmlCodeEditor
                Stage testStage = new Stage();
                testStage.setTitle("Syntax Highlighting Test");

                // Create XmlCodeEditor
                XmlCodeEditor editor = new XmlCodeEditor();

                // Set test content
                String testXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<!-- This is a test comment -->\n" +
                        "<root>\n" +
                        "    <element attribute=\"value\">content</element>\n" +
                        "</root>";

                editor.getCodeArea().replaceText(testXml);

                // Apply syntax highlighting
                editor.refreshSyntaxHighlighting();

                // Debug CSS status
                editor.debugCssStatus();

                // Create scene and show stage
                javafx.scene.Scene scene = new javafx.scene.Scene(editor, 800, 600);
                testStage.setScene(scene);
                testStage.show();

                // Close stage after a short delay
                new Thread(() -> {
                    try {
                        Thread.sleep(3000); // Show for 3 seconds
                        Platform.runLater(() -> testStage.close());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();

                System.out.println("Simple app test completed");

            } catch (Exception e) {
                System.err.println("Error in simple app test: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Test
    void testCssLoadingAndDebug() {
        Platform.runLater(() -> {
            // Test CSS loading
            xmlCodeEditor.debugCssStatus();

            // Test with sample XML
            String testXml = "<root><element attribute=\"value\">content</element></root>";
            xmlCodeEditor.getCodeArea().replaceText(testXml);

            // Test syntax highlighting
            xmlCodeEditor.refreshSyntaxHighlighting();

            // Debug again after highlighting
            xmlCodeEditor.debugCssStatus();

            System.out.println("CSS loading and debug test completed");
        });
    }

    @Test
    void testNullStringHandling() {
        // This should not crash the highlighting system
        assertDoesNotThrow(() -> {
            XmlCodeEditor.computeHighlighting(null);
        });
    }
}