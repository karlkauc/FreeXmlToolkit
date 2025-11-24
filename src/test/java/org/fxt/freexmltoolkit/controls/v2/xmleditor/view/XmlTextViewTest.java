package org.fxt.freexmltoolkit.controls.v2.xmleditor.view;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.editor.XmlEditorContext;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlDocument;
import org.fxt.freexmltoolkit.controls.v2.xmleditor.model.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XmlTextView class.
 *
 * <p>Note: These tests use JavaFX Platform to test UI components.</p>
 *
 * @author Claude Code
 * @since 2.0
 */
class XmlTextViewTest {

    private XmlEditorContext context;
    private XmlTextView textView;

    @BeforeAll
    static void initJavaFX() {
        // Initialize JavaFX toolkit
        new JFXPanel();
    }

    @BeforeEach
    void setUp() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            context = new XmlEditorContext();
            textView = new XmlTextView(context);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Basic Tests ====================

    @Test
    void testConstructor() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertNotNull(textView);
            assertNotNull(textView.getCodeArea());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetText() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            String text = textView.getText();
            assertNotNull(text);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testSetText() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            String xml = "<?xml version=\"1.0\"?>\n<root><child/></root>";
            textView.setText(xml);

            String result = textView.getText();
            assertTrue(result.contains("<root"));
            assertTrue(result.contains("<child"));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testClear() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setText("<?xml version=\"1.0\"?>\n<root/>");
            textView.clear();

            String result = textView.getText();
            assertTrue(result.isEmpty());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Theme Tests ====================

    @Test
    void testSetDarkTheme() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertFalse(textView.isDarkTheme());

            textView.setDarkTheme(true);

            assertTrue(textView.isDarkTheme());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testSetLightTheme() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setDarkTheme(true);
            textView.setDarkTheme(false);

            assertFalse(textView.isDarkTheme());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Editing Tests ====================

    @Test
    void testSelectAll() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setText("<?xml version=\"1.0\"?>\n<root/>");
            textView.selectAll();

            String selected = textView.getSelectedText();
            assertTrue(selected.length() > 0);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetSelectedText() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setText("<?xml version=\"1.0\"?>\n<root/>");
            textView.selectAll();

            String selected = textView.getSelectedText();
            assertNotNull(selected);
            assertTrue(selected.contains("<root"));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Caret Tests ====================

    @Test
    void testGetCaretPosition() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            int position = textView.getCaretPosition();
            assertTrue(position >= 0);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testSetCaretPosition() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setText("<?xml version=\"1.0\"?>\n<root/>");
            textView.setCaretPosition(10);

            int position = textView.getCaretPosition();
            assertEquals(10, position);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Line Tests ====================

    @Test
    void testGetCurrentLine() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            int line = textView.getCurrentLine();
            assertTrue(line >= 0);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testGetLineCount() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setText("<?xml version=\"1.0\"?>\n<root>\n  <child/>\n</root>");

            int lineCount = textView.getLineCount();
            assertTrue(lineCount >= 4);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testScrollToLine() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            textView.setText("<?xml version=\"1.0\"?>\n<root>\n  <child/>\n</root>");

            // Should not throw
            assertDoesNotThrow(() -> {
                textView.scrollToLine(2);
            });
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Format XML Tests ====================

    @Test
    void testFormatXml() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            String unformatted = "<?xml version=\"1.0\"?><root><child/></root>";
            textView.setText(unformatted);
            textView.formatXml();

            String formatted = textView.getText();
            assertNotNull(formatted);
            // Should have line breaks after formatting
            assertTrue(formatted.contains("\n") || formatted.length() > unformatted.length());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testFormatXmlInvalid() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            String invalid = "<unclosed>";
            textView.setText(invalid);

            // Should not throw, just leave text as-is
            assertDoesNotThrow(() -> {
                textView.formatXml();
            });

            String result = textView.getText();
            assertEquals(invalid, result); // Should be unchanged
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Context Integration Tests ====================

    @Test
    void testLoadDocumentText() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            XmlDocument doc = new XmlDocument();
            XmlElement root = new XmlElement("test");
            doc.setRootElement(root);

            context.loadDocumentFromString("<?xml version=\"1.0\"?>\n<test/>");

            // Wait for text view to update
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                // Ignore
            }

            String text = textView.getText();
            assertTrue(text.contains("<test"));
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testEditModeChange() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            assertTrue(textView.getCodeArea().isEditable()); // Default is editable

            context.setEditMode(false);

            // Wait for change to propagate
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            assertFalse(textView.getCodeArea().isEditable());
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Undo/Redo Tests ====================

    @Test
    void testCanUndo() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            boolean canUndo = textView.canUndo();
            // Initially should not be able to undo
            assertFalse(canUndo);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testCanRedo() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            boolean canRedo = textView.canRedo();
            // Initially should not be able to redo
            assertFalse(canRedo);
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    // ==================== Utility Tests ====================

    @Test
    void testRequestCodeAreaFocus() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            // Should not throw
            assertDoesNotThrow(() -> {
                textView.requestCodeAreaFocus();
            });
            latch.countDown();
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
