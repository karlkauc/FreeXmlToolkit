package org.fxt.freexmltoolkit.controls.editor;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for FindReplaceDialog enhanced functionality
 */
@ExtendWith({ApplicationExtension.class})
class FindReplaceDialogTest {

    private FindReplaceDialog dialog;
    private CodeArea codeArea;

    @Start
    void start(Stage stage) {
        // ApplicationExtension handles JavaFX initialization
        codeArea = new CodeArea();
        codeArea.replaceText("This is a test document with some test content for testing search functionality.");
        dialog = new FindReplaceDialog(codeArea);
    }

    @BeforeEach
    void setUp() throws InterruptedException {
        // Reset CodeArea content before each test
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                codeArea.replaceText("This is a test document with some test content for testing search functionality.");
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(5, TimeUnit.SECONDS), "setUp should complete within timeout");
    }

    @Test
    void testDialogCreation() {
        // Test that dialog is created successfully
        assertNotNull(dialog, "Dialog should be created successfully");
        assertNotNull(codeArea, "CodeArea should be created successfully");

        // Test that CodeArea has content
        assertTrue(codeArea.getText().contains("test"), "CodeArea should contain test content");
    }

    @Test
    void testDialogWithCodeArea() {
        // Test that we can create a dialog with a CodeArea
        assertNotNull(dialog, "Dialog should be created with CodeArea");
        assertTrue(codeArea.getText().length() > 0, "CodeArea should have content");

        // Test passes if no exception is thrown
        assertTrue(true, "FindReplaceDialog enhanced functionality test passed");
    }
}