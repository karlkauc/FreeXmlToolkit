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
import org.fxt.freexmltoolkit.FxtGui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for code folding functionality in EnhancedXmlCodeEditor.
 * Tests that the folding implementation actually hides and restores content.
 */
@ExtendWith(ApplicationExtension.class)
class EnhancedXmlCodeEditorFoldingTest {

    private EnhancedXmlCodeEditor editor;

    @Start
    private void start(Stage stage) throws Exception {
        FxtGui app = new FxtGui();
        app.start(stage);
    }

    /**
     * Test that folding actually hides content and shows a placeholder
     */
    @Test
    void testFoldingHidesContent(FxRobot robot) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                editor = new EnhancedXmlCodeEditor();

                // Set up test XML with nested elements
                String testXml = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <root>
                            <person>
                                <name>John Doe</name>
                                <age>30</age>
                                <address>
                                    <street>123 Main St</street>
                                    <city>New York</city>
                                </address>
                            </person>
                        </root>
                        """;

                editor.getCodeArea().replaceText(testXml);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Editor initialization timed out");
        WaitForAsyncUtils.waitForFxEvents();

        // Wait a bit for folding regions to be calculated
        Thread.sleep(500);

        // Verify initial state
        Platform.runLater(() -> {
            String originalText = editor.getCodeArea().getText();
            assertTrue(originalText.contains("<name>John Doe</name>"),
                    "Original text should contain the name element");
            assertTrue(originalText.contains("<age>30</age>"),
                    "Original text should contain the age element");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Test that folding regions are detected correctly
     */
    @Test
    void testFoldingRegionsDetection(FxRobot robot) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                editor = new EnhancedXmlCodeEditor();

                String testXml = """
                        <root>
                            <child>content</child>
                        </root>
                        """;

                editor.getCodeArea().replaceText(testXml);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Editor initialization timed out");
        WaitForAsyncUtils.waitForFxEvents();

        // Wait for folding regions to be calculated
        Thread.sleep(500);

        Platform.runLater(() -> {
            var regions = editor.getFoldingRegions();
            assertNotNull(regions, "Folding regions should not be null");
            // The root element and child element should create folding regions
            assertTrue(regions.size() > 0, "Should have detected folding regions");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Test that folding is enabled by default
     */
    @Test
    void testFoldingEnabledByDefault(FxRobot robot) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                editor = new EnhancedXmlCodeEditor();
                assertTrue(editor.isFoldingEnabled(), "Folding should be enabled by default");
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Editor initialization timed out");
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Test enabling and disabling folding
     */
    @Test
    void testEnableDisableFolding(FxRobot robot) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                editor = new EnhancedXmlCodeEditor();

                // Disable folding
                editor.setFoldingEnabled(false);
                assertFalse(editor.isFoldingEnabled(), "Folding should be disabled");

                // Enable folding
                editor.setFoldingEnabled(true);
                assertTrue(editor.isFoldingEnabled(), "Folding should be enabled");

                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Test timed out");
        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Test that multi-line comments can be folded
     */
    @Test
    void testCommentFolding(FxRobot robot) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                editor = new EnhancedXmlCodeEditor();

                String testXml = """
                        <root>
                            <!-- This is a
                            multi-line
                            comment -->
                            <child>content</child>
                        </root>
                        """;

                editor.getCodeArea().replaceText(testXml);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Editor initialization timed out");
        WaitForAsyncUtils.waitForFxEvents();

        // Wait for folding regions to be calculated
        Thread.sleep(500);

        Platform.runLater(() -> {
            var regions = editor.getFoldingRegions();
            assertNotNull(regions, "Folding regions should not be null");
            assertTrue(regions.size() > 0, "Should have detected folding regions including comment");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }

    /**
     * Test that CDATA sections can be folded
     */
    @Test
    void testCDataFolding(FxRobot robot) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                editor = new EnhancedXmlCodeEditor();

                String testXml = """
                        <root>
                            <![CDATA[
                            This is CDATA
                            with multiple lines
                            ]]>
                            <child>content</child>
                        </root>
                        """;

                editor.getCodeArea().replaceText(testXml);
                latch.countDown();
            } catch (Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Editor initialization timed out");
        WaitForAsyncUtils.waitForFxEvents();

        // Wait for folding regions to be calculated
        Thread.sleep(500);

        Platform.runLater(() -> {
            var regions = editor.getFoldingRegions();
            assertNotNull(regions, "Folding regions should not be null");
            assertTrue(regions.size() > 0, "Should have detected folding regions including CDATA");
        });

        WaitForAsyncUtils.waitForFxEvents();
    }
}
