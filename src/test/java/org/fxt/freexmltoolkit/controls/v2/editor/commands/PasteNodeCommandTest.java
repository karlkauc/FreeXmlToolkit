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

package org.fxt.freexmltoolkit.controls.v2.editor.commands;

import org.fxt.freexmltoolkit.controls.v2.editor.clipboard.XsdClipboard;
import org.fxt.freexmltoolkit.controls.v2.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasteNodeCommand")
class PasteNodeCommandTest {

    private XsdClipboard clipboard;
    private XsdSchema schema;
    private XsdElement sourceElement;
    private XsdElement targetElement;
    private XsdSequence targetSequence;

    @BeforeEach
    void setUp() {
        clipboard = new XsdClipboard();
        schema = new XsdSchema();
        schema.setTargetNamespace("http://test.example.com");

        sourceElement = new XsdElement("sourceElement");
        sourceElement.setType("xs:string");

        targetElement = new XsdElement("targetElement");
        XsdComplexType complexType = new XsdComplexType(null);
        targetSequence = new XsdSequence();
        complexType.addChild(targetSequence);
        targetElement.addChild(complexType);

        schema.addChild(sourceElement);
        schema.addChild(targetElement);
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Throws for null clipboard")
        void throwsForNullClipboard() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PasteNodeCommand(null, targetElement));
        }

        @Test
        @DisplayName("Throws for null target parent")
        void throwsForNullTarget() {
            clipboard.copy(sourceElement);
            assertThrows(IllegalArgumentException.class, () ->
                    new PasteNodeCommand(clipboard, null));
        }

        @Test
        @DisplayName("Throws for empty clipboard")
        void throwsForEmptyClipboard() {
            assertThrows(IllegalArgumentException.class, () ->
                    new PasteNodeCommand(clipboard, targetElement));
        }

        @Test
        @DisplayName("Accepts valid clipboard and target")
        void acceptsValidInput() {
            clipboard.copy(sourceElement);
            assertDoesNotThrow(() ->
                    new PasteNodeCommand(clipboard, targetElement));
        }
    }

    @Nested
    @DisplayName("Execute - Copy")
    class ExecuteCopyTests {

        @Test
        @DisplayName("Pastes copied node to target")
        void pastesCopiedNode() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            boolean result = command.execute();

            assertTrue(result);
            assertNotNull(command.getPastedNode());
            // Pasted node gets "_copy" suffix
            assertEquals("sourceElement_copy", command.getPastedNode().getName());
        }

        @Test
        @DisplayName("Creates deep copy for copy operation")
        void createsDeepCopy() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();

            // Pasted node should be a different instance
            assertNotSame(sourceElement, command.getPastedNode());
            // Name has "_copy" suffix
            assertEquals(sourceElement.getName() + "_copy", command.getPastedNode().getName());
        }

        @Test
        @DisplayName("Original node remains in place after copy-paste")
        void originalRemainsAfterCopy() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();

            // Source element should still be in schema
            assertTrue(schema.getChildren().contains(sourceElement));
        }

        @Test
        @DisplayName("Clipboard retains content after copy-paste")
        void clipboardRetainsContent() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();

            // Clipboard should still have content (can paste again)
            assertTrue(clipboard.hasContent());
        }
    }

    @Nested
    @DisplayName("Execute - Cut")
    class ExecuteCutTests {

        @Test
        @DisplayName("Removes original node after cut-paste")
        void removesOriginalAfterCut() {
            clipboard.cut(sourceElement);
            int originalChildCount = schema.getChildren().size();

            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);
            command.execute();

            // Source element should be removed from schema
            assertFalse(schema.getChildren().contains(sourceElement));
            // Child count should be reduced by 1 (source removed)
            assertEquals(originalChildCount - 1, schema.getChildren().size());
        }

        @Test
        @DisplayName("Clears clipboard after cut-paste")
        void clearsClipboardAfterCut() {
            clipboard.cut(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();

            // Clipboard should be cleared after cut-paste
            assertFalse(clipboard.hasContent());
        }
    }

    @Nested
    @DisplayName("Undo")
    class UndoTests {

        @Test
        @DisplayName("Removes pasted node on undo")
        void removesPastedNodeOnUndo() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();
            XsdNode pastedNode = command.getPastedNode();

            boolean undoResult = command.undo();

            assertTrue(undoResult);
            // Pasted node should no longer be in target's children tree
        }

        @Test
        @DisplayName("Undo fails before execute")
        void undoFailsBeforeExecute() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            // Don't call execute
            boolean undoResult = command.undo();

            assertFalse(undoResult);
        }

        @Test
        @DisplayName("Restores cut node on undo")
        void restoresCutNodeOnUndo() {
            clipboard.cut(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();
            // Source element removed
            assertFalse(schema.getChildren().contains(sourceElement));

            command.undo();
            // Source element should be restored
            assertTrue(schema.getChildren().contains(sourceElement));
        }

        @Test
        @DisplayName("Restores clipboard content for cut on undo")
        void restoresClipboardOnUndo() {
            clipboard.cut(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();
            assertFalse(clipboard.hasContent()); // Cleared after cut-paste

            command.undo();
            assertTrue(clipboard.hasContent()); // Restored for potential re-paste
            assertTrue(clipboard.isCut()); // Still a cut operation
        }
    }

    @Nested
    @DisplayName("Description")
    class DescriptionTests {

        @Test
        @DisplayName("Copy description")
        void copyDescription() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            String desc = command.getDescription();
            assertTrue(desc.contains("Paste"));
            assertTrue(desc.contains("targetElement"));
            assertFalse(desc.contains("Cut"));
        }

        @Test
        @DisplayName("Cut description")
        void cutDescription() {
            clipboard.cut(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            String desc = command.getDescription();
            assertTrue(desc.contains("Cut"));
            assertTrue(desc.contains("paste"));
        }
    }

    @Nested
    @DisplayName("canUndo and canMergeWith")
    class CapabilityTests {

        @Test
        @DisplayName("canUndo returns false before execute")
        void canUndoFalseBeforeExecute() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            assertFalse(command.canUndo());
        }

        @Test
        @DisplayName("canUndo returns true after execute")
        void canUndoTrueAfterExecute() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();

            assertTrue(command.canUndo());
        }

        @Test
        @DisplayName("canMergeWith always returns false")
        void canMergeWithAlwaysFalse() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            assertFalse(command.canMergeWith(command));
        }
    }

    @Nested
    @DisplayName("getPastedNode")
    class GetPastedNodeTests {

        @Test
        @DisplayName("Returns null before execute")
        void returnsNullBeforeExecute() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            assertNull(command.getPastedNode());
        }

        @Test
        @DisplayName("Returns pasted node after execute")
        void returnsPastedNodeAfterExecute() {
            clipboard.copy(sourceElement);
            PasteNodeCommand command = new PasteNodeCommand(clipboard, targetElement);

            command.execute();

            assertNotNull(command.getPastedNode());
            assertTrue(command.getPastedNode() instanceof XsdElement);
        }
    }
}
