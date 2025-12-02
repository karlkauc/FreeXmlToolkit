/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2024.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

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
}