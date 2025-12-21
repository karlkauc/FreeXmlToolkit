package org.fxt.freexmltoolkit.controls.editor;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatusLineController functionality.
 */
@ExtendWith(ApplicationExtension.class)
class StatusLineControllerTest {

    private StatusLineController statusLineController;
    private CodeArea codeArea;
    private PropertiesService propertiesService;

    @Start
    void start(Stage stage) {
        // Initialize JavaFX components
        codeArea = new CodeArea();
        propertiesService = PropertiesServiceImpl.getInstance();
        statusLineController = new StatusLineController(codeArea, propertiesService);
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        // Reset to initial state and wait for completion to avoid race conditions
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                codeArea.replaceText("");
                statusLineController.resetToDefaults();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "setUp should complete within timeout");
    }

    @Test
    void testInitialStatusLineState() {
        assertNotNull(statusLineController.getStatusLine());
        assertTrue(statusLineController.isVisible());
        assertEquals("UTF-8", statusLineController.getEncoding());
        assertEquals("LF", statusLineController.getLineSeparator());
        assertTrue(statusLineController.isUseSpaces());
        assertEquals(StatusLineController.XsdParsingStatus.NOT_STARTED, statusLineController.getXsdParsingStatus());
        assertEquals("⚫ No XSD", statusLineController.getXsdParsingStatusLabel().getText());
    }

    @Test
    void testEncodingUpdate() {
        statusLineController.setEncoding("UTF-16");
        assertEquals("UTF-16", statusLineController.getEncoding());

        // Test null encoding defaults to UTF-8
        statusLineController.setEncoding(null);
        assertEquals("UTF-8", statusLineController.getEncoding());
    }

    @Test
    void testLineSeparatorUpdate() {
        statusLineController.setLineSeparator("CRLF");
        assertEquals("CRLF", statusLineController.getLineSeparator());

        // Test null line separator defaults to LF
        statusLineController.setLineSeparator(null);
        assertEquals("LF", statusLineController.getLineSeparator());
    }

    @Test
    void testIndentationSettings() {
        statusLineController.setIndentationSize(4);
        statusLineController.setUseSpaces(true);

        assertEquals(4, statusLineController.getIndentationSize());
        assertTrue(statusLineController.isUseSpaces());

        statusLineController.setUseSpaces(false);
        assertFalse(statusLineController.isUseSpaces());

        // Test minimum indentation size
        statusLineController.setIndentationSize(0);
        assertEquals(1, statusLineController.getIndentationSize());
    }

    @Test
    void testStatusLineVisibility() {
        statusLineController.setVisible(true);
        assertTrue(statusLineController.isVisible());

        statusLineController.setVisible(false);
        assertFalse(statusLineController.isVisible());
    }

    @Test
    void testUpdateAllStatus() {
        statusLineController.updateAllStatus("ISO-8859-1", "CRLF", 8, false);

        assertEquals("ISO-8859-1", statusLineController.getEncoding());
        assertEquals("CRLF", statusLineController.getLineSeparator());
        assertEquals(8, statusLineController.getIndentationSize());
        assertFalse(statusLineController.isUseSpaces());
    }

    @Test
    void testResetToDefaults() {
        // Change settings
        statusLineController.setEncoding("UTF-16");
        statusLineController.setLineSeparator("CRLF");
        statusLineController.setIndentationSize(8);
        statusLineController.setUseSpaces(false);

        // Reset to defaults
        statusLineController.resetToDefaults();

        assertEquals("UTF-8", statusLineController.getEncoding());
        assertEquals("LF", statusLineController.getLineSeparator());
        assertTrue(statusLineController.isUseSpaces());
    }

    @Test
    void testCustomStatusMessage() {
        statusLineController.setStatusMessage("Processing...");
        // This test verifies the method doesn't throw an exception
        // Visual verification would require checking the actual label text
        assertDoesNotThrow(() -> statusLineController.setStatusMessage("Complete"));
    }

    @Test
    void testCursorPositionRefresh() {
        // This test verifies the method doesn't throw an exception
        assertDoesNotThrow(() -> statusLineController.refreshCursorPosition());

        Platform.runLater(() -> {
            codeArea.replaceText("Line 1\nLine 2\nLine 3");
            codeArea.moveTo(10); // Move to position 10
            statusLineController.refreshCursorPosition();
        });
    }

    @Test
    void testIndentationDisplayRefresh() {
        assertDoesNotThrow(() -> statusLineController.refreshIndentationDisplay());

        statusLineController.setIndentationSize(6);
        statusLineController.setUseSpaces(false);
        assertDoesNotThrow(() -> statusLineController.refreshIndentationDisplay());
    }

    @Test
    void testStatusLineNotNull() {
        assertNotNull(statusLineController.getStatusLine());
        assertNotNull(statusLineController.getStatusLine().getChildren());
    }

    //@Test
    //void testXsdParsingStatusUpdates() throws InterruptedException {
    //    // Test PARSING status
    //    CountDownLatch parsingLatch = new CountDownLatch(1);
    //    Platform.runLater(() -> {
    //        statusLineController.updateXsdParsingStatus(StatusLineController.XsdParsingStatus.PARSING);
    //        parsingLatch.countDown();
    //    });
    //    assertTrue(parsingLatch.await(5, TimeUnit.SECONDS), "Parsing status update timed out");
    //    assertEquals(StatusLineController.XsdParsingStatus.PARSING, statusLineController.getXsdParsingStatus());
    //    assertEquals("🔄 Parsing XSD...", statusLineController.getXsdParsingStatusLabel().getText());
    //    assertTrue(statusLineController.getXsdParsingStatusLabel().getStyleClass().contains("parsing-status"));
    //
    //    // Test COMPLETED status
    //    CountDownLatch completedLatch = new CountDownLatch(1);
    //    Platform.runLater(() -> {
    //        statusLineController.updateXsdParsingStatus(StatusLineController.XsdParsingStatus.COMPLETED);
    //        completedLatch.countDown();
    //    });
    //    assertTrue(completedLatch.await(5, TimeUnit.SECONDS), "Completed status update timed out");
    //    assertEquals(StatusLineController.XsdParsingStatus.COMPLETED, statusLineController.getXsdParsingStatus());
    //    assertEquals("✅ XSD Ready", statusLineController.getXsdParsingStatusLabel().getText());
    //    assertFalse(statusLineController.getXsdParsingStatusLabel().getStyleClass().contains("parsing-status"));
    //
    //    // Test ERROR status
    //    CountDownLatch errorLatch = new CountDownLatch(1);
    //    Platform.runLater(() -> {
    //        statusLineController.setXsdParsingError();
    //        errorLatch.countDown();
    //    });
    //    assertTrue(errorLatch.await(5, TimeUnit.SECONDS), "Error status update timed out");
    //    assertEquals(StatusLineController.XsdParsingStatus.ERROR, statusLineController.getXsdParsingStatus());
    //    assertEquals("❌ XSD Error", statusLineController.getXsdParsingStatusLabel().getText());
    //
    //    // Test NOT_STARTED status
    //    CountDownLatch notStartedLatch = new CountDownLatch(1);
    //    Platform.runLater(() -> {
    //        statusLineController.setXsdParsingNotStarted();
    //        notStartedLatch.countDown();
    //    });
    //    assertTrue(notStartedLatch.await(5, TimeUnit.SECONDS), "Not Started status update timed out");
    //    assertEquals(StatusLineController.XsdParsingStatus.NOT_STARTED, statusLineController.getXsdParsingStatus());
    //    assertEquals("⚫ No XSD", statusLineController.getXsdParsingStatusLabel().getText());
    //}
}
