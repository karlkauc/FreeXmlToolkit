/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
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

package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.editor.StatusLineController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for XSD parsing status functionality in XmlCodeEditor.
 */
@ExtendWith(ApplicationExtension.class)
class XsdParsingStatusTest {

    private XmlCodeEditor xmlCodeEditor;

    @Start
    void start(Stage stage) {
        xmlCodeEditor = new XmlCodeEditor();
        stage.setScene(new javafx.scene.Scene(xmlCodeEditor, 800, 600));
        stage.show();
    }

    @BeforeEach
    void setUp() {
        Platform.runLater(() -> xmlCodeEditor.setText(""));
    }

    @Test
    void testXsdParsingStatusInitialization() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Initially should be NOT_STARTED
                StatusLineController.XsdParsingStatus status = xmlCodeEditor.getXsdParsingStatus();
                assertEquals(StatusLineController.XsdParsingStatus.NOT_STARTED, status,
                        "Initial XSD parsing status should be NOT_STARTED");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within 5 seconds");
    }

    @Test
    void testXsdParsingStatusTransitions() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Test all status transitions
                xmlCodeEditor.setXsdParsingStarted();
                assertEquals(StatusLineController.XsdParsingStatus.PARSING,
                        xmlCodeEditor.getXsdParsingStatus(), "Status should be PARSING");

                xmlCodeEditor.setXsdParsingCompleted();
                assertEquals(StatusLineController.XsdParsingStatus.COMPLETED,
                        xmlCodeEditor.getXsdParsingStatus(), "Status should be COMPLETED");

                xmlCodeEditor.setXsdParsingError();
                assertEquals(StatusLineController.XsdParsingStatus.ERROR,
                        xmlCodeEditor.getXsdParsingStatus(), "Status should be ERROR");

                xmlCodeEditor.setXsdParsingNotStarted();
                assertEquals(StatusLineController.XsdParsingStatus.NOT_STARTED,
                        xmlCodeEditor.getXsdParsingStatus(), "Status should be NOT_STARTED");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test should complete within 5 seconds");
    }

    @Test
    void testXsdParsingStatusIcons() {
        // Test that the status enum has the expected icons
        assertEquals("⚫", StatusLineController.XsdParsingStatus.NOT_STARTED.getIcon());
        assertEquals("🔄", StatusLineController.XsdParsingStatus.PARSING.getIcon());
        assertEquals("✅", StatusLineController.XsdParsingStatus.COMPLETED.getIcon());
        assertEquals("❌", StatusLineController.XsdParsingStatus.ERROR.getIcon());
    }

    @Test
    void testXsdParsingStatusTexts() {
        // Test that the status enum has the expected texts
        assertEquals("No XSD", StatusLineController.XsdParsingStatus.NOT_STARTED.getText());
        assertEquals("Parsing XSD...", StatusLineController.XsdParsingStatus.PARSING.getText());
        assertEquals("XSD Ready", StatusLineController.XsdParsingStatus.COMPLETED.getText());
        assertEquals("XSD Error", StatusLineController.XsdParsingStatus.ERROR.getText());
    }
}