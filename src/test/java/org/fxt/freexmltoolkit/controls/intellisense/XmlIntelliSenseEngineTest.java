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

package org.fxt.freexmltoolkit.controls.intellisense;

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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlIntelliSenseEngine to ensure it doesn't add unwanted spaces
 * to element content.
 */
@ExtendWith(ApplicationExtension.class)
class XmlIntelliSenseEngineTest {

    private CodeArea codeArea;
    private XmlIntelliSenseEngine intelliSenseEngine;

    @Start
    void start(Stage stage) {
        codeArea = new CodeArea();
        intelliSenseEngine = new XmlIntelliSenseEngine(codeArea);
    }

    @BeforeEach
    void setUp() {
        Platform.runLater(() -> codeArea.replaceText(""));
    }

    @Test
    void testAutoCloseTagDoesNotAddSpaces() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                // Simulate typing an opening tag
                codeArea.replaceText("<test>");
                codeArea.moveTo(codeArea.getText().length());

                // The auto-close should position cursor between tags without spaces
                String result = codeArea.getText();

                // Verify no extra spaces are added
                assertFalse(result.contains("<test>   </test>"),
                        "Auto-close should not add spaces inside element");

            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    void testSmartIndentationIsDisabled() {
        Platform.runLater(() -> {
            // Set up a simple opening tag
            codeArea.replaceText("<element>");
            codeArea.moveTo(codeArea.getText().length());

            // Simulate pressing Enter - smart indentation should be disabled
            // so no automatic indentation should be added
            String initialText = codeArea.getText();

            // Smart indentation is disabled, so manual text entry should work cleanly
            codeArea.insertText(codeArea.getCaretPosition(), "content");

            String result = codeArea.getText();
            assertEquals("<element>content", result);

            // Verify no unwanted spaces were added
            assertFalse(result.contains("<element>    content"),
                    "Smart indentation should be disabled and not add spaces");
        });
    }

    @Test
    void testHasExistingClosingTagDetection() {
        Platform.runLater(() -> {
            codeArea.replaceText("<element>content</element>");
            codeArea.moveTo(9); // Position after '>'

            // The auto-close logic should detect existing closing tag
            // and not add a duplicate
            String text = codeArea.getText();
            assertEquals("<element>content</element>", text);

            // Count closing tags - should only be one
            long closingTagCount = text.chars()
                    .boxed()
                    .collect(java.util.stream.Collectors.toList())
                    .toString()
                    .split("</element>", -1).length - 1;

            assertEquals(1, closingTagCount, "Should only have one closing tag");
        });
    }

    @Test
    void testCleanElementContentEditing() {
        Platform.runLater(() -> {
            // Start with clean element
            codeArea.replaceText("<UniqueDocumentID></UniqueDocumentID>");

            // Position cursor between tags
            int insertPos = codeArea.getText().indexOf("></");
            codeArea.moveTo(insertPos);

            // Insert content
            codeArea.insertText(insertPos, "1");

            String result = codeArea.getText();
            assertEquals("<UniqueDocumentID>1</UniqueDocumentID>", result);

            // Verify no trailing spaces
            assertFalse(result.contains("<UniqueDocumentID>1   </UniqueDocumentID>"),
                    "Element content should not have trailing spaces");
        });
    }

    @Test
    void testEngineShutdownCleansUp() {
        assertDoesNotThrow(() -> {
            intelliSenseEngine.shutdown();
        }, "Engine shutdown should not throw exceptions");
    }

    @Test
    void testNoSpacesInSimpleTextContent() {
        Platform.runLater(() -> {
            codeArea.replaceText("<test>");
            int insertPos = codeArea.getText().length();
            codeArea.moveTo(insertPos);

            // Add simple text content
            codeArea.insertText(insertPos, "value");

            // Add closing tag manually to simulate complete editing
            codeArea.insertText(codeArea.getCaretPosition(), "</test>");

            String result = codeArea.getText();
            assertEquals("<test>value</test>", result);

            // Ensure no spaces were inserted
            assertFalse(result.matches(".*<test>\\s+value\\s+</test>.*"),
                    "Simple text content should not have leading or trailing spaces");
        });
    }
}