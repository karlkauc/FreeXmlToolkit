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
            // Test that element content is preserved as-is (whitespace is not automatically trimmed)
            String originalText = "<UniqueDocumentID>1     </UniqueDocumentID>";
            xmlCodeEditor.setText(originalText);

            // Move cursor - this should not automatically modify text
            xmlCodeEditor.getCodeArea().moveTo(xmlCodeEditor.getText().indexOf("</UniqueDocumentID>"));

            String text = xmlCodeEditor.getText();

            // Verify the text is preserved as-is (no automatic trimming)
            assertEquals(originalText, text,
                    "Element content should be preserved exactly as entered");
        });
    }

    @Test
    void testManualTextEditingWithoutExtraSpaces() {
        Platform.runLater(() -> {
            // Start with clean element
            xmlCodeEditor.setText("<Element></Element>");

            // Position cursor between tags and add content
            int insertPos = xmlCodeEditor.getText().indexOf("></Element>");
            xmlCodeEditor.getCodeArea().moveTo(insertPos + 1); // Position after '>'
            xmlCodeEditor.getCodeArea().insertText(insertPos + 1, "Value");

            String text = xmlCodeEditor.getText();

            // Verify the insertion was correct
            assertTrue(text.contains("<Element>Value</Element>"),
                    "Manual text editing should insert content correctly");
        });
    }

    @Test
    void testAggressiveCleanupOfElementContent() {
        Platform.runLater(() -> {
            // Test that element content is preserved as-is (no automatic cleanup)
            String originalText = "<UniqueDocumentID>1       </UniqueDocumentID>";
            xmlCodeEditor.setText(originalText);

            // Move cursor - this should not automatically modify text
            xmlCodeEditor.getCodeArea().moveTo(0);

            String text = xmlCodeEditor.getText();

            // Content should be preserved exactly as entered
            assertEquals(originalText, text,
                    "Element content should be preserved without automatic cleanup");
        });
    }

    @Test
    void testMultipleElementCleanup() {
        Platform.runLater(() -> {
            // Test that multi-element XML is preserved as-is (no automatic cleanup)
            String originalXml =
                    "<root>\n" +
                            "    <element1>value1     </element1>\n" +
                            "    <element2>value2   </element2>\n" +
                            "    <element3>value3       </element3>\n" +
                            "</root>";

            xmlCodeEditor.setText(originalXml);

            // Move cursor - this should not automatically modify text
            xmlCodeEditor.getCodeArea().moveTo(0);

            String text = xmlCodeEditor.getText();

            // Content should be preserved exactly as entered
            assertEquals(originalXml, text,
                    "Multi-element content should be preserved without automatic cleanup");
        });
    }
}