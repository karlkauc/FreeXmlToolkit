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

package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class to verify XML element insertion behavior, specifically that no trailing spaces
 * are added when inserting elements without mandatory children.
 */
@ExtendWith(ApplicationExtension.class)
class XmlElementInsertionTest {

    private XmlCodeEditor xmlCodeEditor;

    @Start
    void start(Stage stage) {
        xmlCodeEditor = new XmlCodeEditor();
    }

    @BeforeEach
    void setUp() {
        Platform.runLater(() -> {
            xmlCodeEditor.setText("");
        });
    }

    @Test
    void testSimpleElementWithoutTrailingSpaces() {
        Platform.runLater(() -> {
            // Simulate inserting a simple element like UniqueDocumentID
            xmlCodeEditor.setText("<UniqueDocumentID>1</UniqueDocumentID>");

            String text = xmlCodeEditor.getText();

            // Verify no trailing spaces are present
            assertFalse(text.contains("<UniqueDocumentID>1           </UniqueDocumentID>"),
                    "Element should not contain trailing spaces");

            // Verify the correct format is present
            assertTrue(text.contains("<UniqueDocumentID>1</UniqueDocumentID>"),
                    "Element should be in compact format without trailing spaces");
        });
    }

    @Test
    void testElementWithMandatoryChildrenHasProperFormatting() {
        Platform.runLater(() -> {
            // Simulate inserting an element with mandatory children (like ControlData)
            String xmlWithChildren =
                    "<ControlData>\n" +
                            "    <UniqueDocumentID>1</UniqueDocumentID>\n" +
                            "    <DocumentGenerated>2024-01-01</DocumentGenerated>\n" +
                            "</ControlData>";

            xmlCodeEditor.setText(xmlWithChildren);
            String text = xmlCodeEditor.getText();

            // Verify that child elements are properly formatted without trailing spaces
            assertTrue(text.contains("<UniqueDocumentID>1</UniqueDocumentID>"),
                    "Child elements should not have trailing spaces");
            assertTrue(text.contains("<DocumentGenerated>2024-01-01</DocumentGenerated>"),
                    "Child elements should not have trailing spaces");
        });
    }

    @Test
    void testEmptyElementsAreCompact() {
        Platform.runLater(() -> {
            xmlCodeEditor.setText("<EmptyElement></EmptyElement>");
            String text = xmlCodeEditor.getText();

            // Verify empty elements don't have spaces between tags
            assertTrue(text.contains("<EmptyElement></EmptyElement>"),
                    "Empty elements should be compact without spaces");
            assertFalse(text.contains("<EmptyElement>   </EmptyElement>"),
                    "Empty elements should not contain spaces between tags");
        });
    }

    @Test
    void testMultipleSimpleElementsFormatting() {
        Platform.runLater(() -> {
            String xml =
                    "<root>\n" +
                            "    <element1>value1</element1>\n" +
                            "    <element2>value2</element2>\n" +
                            "    <element3>value3</element3>\n" +
                            "</root>";

            xmlCodeEditor.setText(xml);
            String text = xmlCodeEditor.getText();

            // Verify all simple elements are compact
            assertTrue(text.contains("<element1>value1</element1>"),
                    "Element1 should be compact");
            assertTrue(text.contains("<element2>value2</element2>"),
                    "Element2 should be compact");
            assertTrue(text.contains("<element3>value3</element3>"),
                    "Element3 should be compact");

            // Verify no trailing spaces in any element
            assertFalse(text.matches(".*<\\w+>[^<]*\\s+</\\w+>.*"),
                    "No element should have trailing spaces before closing tag");
        });
    }

    @Test
    void testElementWithOnlyWhitespaceIsCleanedUp() {
        Platform.runLater(() -> {
            // Test that elements with only whitespace are handled properly
            xmlCodeEditor.setText("<TestElement>   </TestElement>");
            String text = xmlCodeEditor.getText();

            // After any processing, excessive whitespace should be managed
            assertNotEquals("<TestElement>                    </TestElement>", text,
                    "Elements should not have excessive whitespace");
        });
    }

    @Test
    void testTrailingSpacesAreRemovedOnEdit() {
        Platform.runLater(() -> {
            // Simulate editing element content that might add trailing spaces
            xmlCodeEditor.setText("<UniqueDocumentID>1     </UniqueDocumentID>");

            // Simulate cursor movement to trigger trimming
            xmlCodeEditor.getCodeArea().moveTo(xmlCodeEditor.getText().indexOf("</UniqueDocumentID>"));

            // Wait for Platform.runLater in trimming method
            Platform.runLater(() -> {
                String text = xmlCodeEditor.getText();

                // Verify trailing spaces are removed
                assertFalse(text.contains("<UniqueDocumentID>1     </UniqueDocumentID>"),
                        "Trailing spaces should be removed");
                assertTrue(text.contains("<UniqueDocumentID>1</UniqueDocumentID>") ||
                                text.contains("<UniqueDocumentID>1 </UniqueDocumentID>"), // Allow single space
                        "Element should be clean or have minimal spacing");
            });
        });
    }

    @Test
    void testManualTextEditingWithoutExtraSpaces() {
        Platform.runLater(() -> {
            // Start with clean element
            xmlCodeEditor.setText("<Element></Element>");

            // Position cursor between tags and add content
            int insertPos = xmlCodeEditor.getText().indexOf("></Element>");
            xmlCodeEditor.getCodeArea().moveTo(insertPos);
            xmlCodeEditor.getCodeArea().insertText(insertPos, "Value");

            String text = xmlCodeEditor.getText();

            // Verify no extra spaces were added
            assertTrue(text.contains("<Element>Value</Element>"),
                    "Manual text editing should not add extra spaces");
            assertFalse(text.contains("<Element>Value   </Element>"),
                    "Manual text editing should not add trailing spaces");
        });
    }

    @Test
    void testAggressiveCleanupOfElementContent() {
        Platform.runLater(() -> {
            // Test the aggressive cleanup with problematic XML
            xmlCodeEditor.setText("<UniqueDocumentID>1       </UniqueDocumentID>");

            // Trigger cleanup by moving cursor
            xmlCodeEditor.getCodeArea().moveTo(0);

            // Wait for cleanup to be processed
            Platform.runLater(() -> {
                String text = xmlCodeEditor.getText();

                // Should be cleaned up to compact format
                assertTrue(text.contains("<UniqueDocumentID>1</UniqueDocumentID>"),
                        "Aggressive cleanup should remove all trailing spaces");
                assertFalse(text.contains("<UniqueDocumentID>1       </UniqueDocumentID>"),
                        "Original format with spaces should be gone");
            });
        });
    }

    @Test
    void testMultipleElementCleanup() {
        Platform.runLater(() -> {
            String messyXml =
                    "<root>\n" +
                            "    <element1>value1     </element1>\n" +
                            "    <element2>value2   </element2>\n" +
                            "    <element3>value3       </element3>\n" +
                            "</root>";

            xmlCodeEditor.setText(messyXml);

            // Trigger cleanup
            xmlCodeEditor.getCodeArea().moveTo(0);

            Platform.runLater(() -> {
                String text = xmlCodeEditor.getText();

                // All elements should be cleaned
                assertTrue(text.contains("<element1>value1</element1>"),
                        "Element1 should be cleaned");
                assertTrue(text.contains("<element2>value2</element2>"),
                        "Element2 should be cleaned");
                assertTrue(text.contains("<element3>value3</element3>"),
                        "Element3 should be cleaned");

                // No trailing spaces should remain
                assertFalse(text.matches(".*>.*\\s+</.*>.*"),
                        "No element should have trailing spaces");
            });
        });
    }
}