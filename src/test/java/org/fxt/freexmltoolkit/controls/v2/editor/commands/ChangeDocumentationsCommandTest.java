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

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChangeDocumentationsCommand")
class ChangeDocumentationsCommandTest {

    private XsdEditorContext editorContext;
    private XsdSchema schema;
    private XsdElement element;

    @BeforeEach
    void setUp() {
        schema = new XsdSchema();
        schema.setTargetNamespace("http://test.example.com");
        editorContext = new XsdEditorContext(schema);

        element = new XsdElement("testElement");
        schema.addChild(element);
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Throws for null editor context")
        void throwsForNullContext() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ChangeDocumentationsCommand(null, element, List.of()));
        }

        @Test
        @DisplayName("Throws for null node")
        void throwsForNullNode() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ChangeDocumentationsCommand(editorContext, null, List.of()));
        }

        @Test
        @DisplayName("Throws for null documentations list")
        void throwsForNullList() {
            assertThrows(IllegalArgumentException.class, () ->
                    new ChangeDocumentationsCommand(editorContext, element, null));
        }

        @Test
        @DisplayName("Accepts empty list")
        void acceptsEmptyList() {
            assertDoesNotThrow(() ->
                    new ChangeDocumentationsCommand(editorContext, element, List.of()));
        }
    }

    @Nested
    @DisplayName("Execute")
    class ExecuteTests {

        @Test
        @DisplayName("Sets new documentations")
        void setsNewDocumentations() {
            List<XsdDocumentation> newDocs = List.of(
                    new XsdDocumentation("English doc", "en"),
                    new XsdDocumentation("German doc", "de")
            );

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(editorContext, element, newDocs);
            boolean result = command.execute();

            assertTrue(result);
            assertEquals(2, element.getDocumentations().size());
            assertEquals("English doc", element.getDocumentations().get(0).getText());
            assertEquals("German doc", element.getDocumentations().get(1).getText());
        }

        @Test
        @DisplayName("Clears legacy documentation")
        void clearsLegacyDocumentation() {
            element.setDocumentation("Old legacy doc");

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("New doc")));
            command.execute();

            assertNull(element.getDocumentation());
        }

        @Test
        @DisplayName("Can set empty list")
        void canSetEmptyList() {
            element.setDocumentations(List.of(new XsdDocumentation("Existing")));

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());
            command.execute();

            assertTrue(element.getDocumentations().isEmpty());
        }
    }

    @Nested
    @DisplayName("Undo")
    class UndoTests {

        @Test
        @DisplayName("Restores old documentations")
        void restoresOldDocumentations() {
            List<XsdDocumentation> oldDocs = List.of(new XsdDocumentation("Old"));
            element.setDocumentations(new ArrayList<>(oldDocs));

            List<XsdDocumentation> newDocs = List.of(new XsdDocumentation("New"));
            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(editorContext, element, newDocs);

            command.execute();
            assertEquals("New", element.getDocumentations().get(0).getText());

            boolean undoResult = command.undo();
            assertTrue(undoResult);
            assertEquals(1, element.getDocumentations().size());
            assertEquals("Old", element.getDocumentations().get(0).getText());
        }

        @Test
        @DisplayName("Restores legacy documentation")
        void restoresLegacyDocumentation() {
            element.setDocumentation("Legacy doc");

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("New")));

            command.execute();
            assertNull(element.getDocumentation());

            command.undo();
            assertEquals("Legacy doc", element.getDocumentation());
        }
    }

    @Nested
    @DisplayName("Description")
    class DescriptionTests {

        @Test
        @DisplayName("Description for adding documentations")
        void descriptionForAdding() {
            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("Doc1"), new XsdDocumentation("Doc2")));

            String desc = command.getDescription();
            assertTrue(desc.contains("Add"));
            assertTrue(desc.contains("2"));
            assertTrue(desc.contains("testElement"));
        }

        @Test
        @DisplayName("Description for removing documentations")
        void descriptionForRemoving() {
            element.setDocumentations(List.of(new XsdDocumentation("Old")));

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());

            String desc = command.getDescription();
            assertTrue(desc.contains("Remove"));
            assertTrue(desc.contains("testElement"));
        }

        @Test
        @DisplayName("Description for editing documentations")
        void descriptionForEditing() {
            element.setDocumentations(List.of(new XsdDocumentation("Old")));

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("New")));

            String desc = command.getDescription();
            assertTrue(desc.contains("Edit") || desc.contains("Change"));
        }

        @Test
        @DisplayName("Description for changing count")
        void descriptionForChangingCount() {
            element.setDocumentations(List.of(new XsdDocumentation("One")));

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("A"), new XsdDocumentation("B")));

            String desc = command.getDescription();
            assertTrue(desc.contains("1") && desc.contains("2") || desc.contains("Change"));
        }

        @Test
        @DisplayName("Handles unnamed node")
        void handlesUnnamedNode() {
            XsdElement unnamedElement = new XsdElement(null);

            ChangeDocumentationsCommand command = new ChangeDocumentationsCommand(
                    editorContext, unnamedElement, List.of());

            String desc = command.getDescription();
            assertTrue(desc.contains("unnamed"));
        }
    }

    @Nested
    @DisplayName("Merge")
    class MergeTests {

        @Test
        @DisplayName("Can merge with same node")
        void canMergeWithSameNode() {
            ChangeDocumentationsCommand cmd1 = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("First")));
            ChangeDocumentationsCommand cmd2 = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("Second")));

            assertTrue(cmd1.canMergeWith(cmd2));
        }

        @Test
        @DisplayName("Cannot merge with different node")
        void cannotMergeWithDifferentNode() {
            XsdElement otherElement = new XsdElement("otherElement");

            ChangeDocumentationsCommand cmd1 = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("First")));
            ChangeDocumentationsCommand cmd2 = new ChangeDocumentationsCommand(
                    editorContext, otherElement, List.of(new XsdDocumentation("Second")));

            assertFalse(cmd1.canMergeWith(cmd2));
        }

        @Test
        @DisplayName("Cannot merge with different command type")
        void cannotMergeWithDifferentType() {
            ChangeDocumentationsCommand cmd = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());

            assertFalse(cmd.canMergeWith(new RenameNodeCommand(element, "newName")));
        }

        @Test
        @DisplayName("Merge uses new documentations from other command")
        void mergeUsesNewDocumentations() {
            ChangeDocumentationsCommand cmd1 = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("First")));
            ChangeDocumentationsCommand cmd2 = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("Second")));

            XsdCommand merged = cmd1.mergeWith(cmd2);
            assertNotNull(merged);

            assertTrue(merged instanceof ChangeDocumentationsCommand);
            ChangeDocumentationsCommand mergedCmd = (ChangeDocumentationsCommand) merged;
            assertEquals("Second", mergedCmd.getNewDocumentations().get(0).getText());
        }
    }

    @Nested
    @DisplayName("Getters")
    class GetterTests {

        @Test
        @DisplayName("getNode returns the node")
        void getNodeReturnsNode() {
            ChangeDocumentationsCommand cmd = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());

            assertSame(element, cmd.getNode());
        }

        @Test
        @DisplayName("getOldDocumentations returns copy")
        void getOldDocumentationsReturnsCopy() {
            element.setDocumentations(List.of(new XsdDocumentation("Old")));

            ChangeDocumentationsCommand cmd = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());

            List<XsdDocumentation> oldDocs = cmd.getOldDocumentations();
            assertEquals(1, oldDocs.size());

            // Verify it's a copy
            oldDocs.clear();
            assertEquals(1, cmd.getOldDocumentations().size());
        }

        @Test
        @DisplayName("getNewDocumentations returns copy")
        void getNewDocumentationsReturnsCopy() {
            ChangeDocumentationsCommand cmd = new ChangeDocumentationsCommand(
                    editorContext, element, List.of(new XsdDocumentation("New")));

            List<XsdDocumentation> newDocs = cmd.getNewDocumentations();
            assertEquals(1, newDocs.size());

            // Verify it's a copy
            newDocs.clear();
            assertEquals(1, cmd.getNewDocumentations().size());
        }

        @Test
        @DisplayName("getOldLegacyDocumentation returns legacy doc")
        void getOldLegacyDocumentation() {
            element.setDocumentation("Legacy");

            ChangeDocumentationsCommand cmd = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());

            assertEquals("Legacy", cmd.getOldLegacyDocumentation());
        }
    }

    @Nested
    @DisplayName("canUndo")
    class CanUndoTests {

        @Test
        @DisplayName("Always returns true")
        void alwaysReturnsTrue() {
            ChangeDocumentationsCommand cmd = new ChangeDocumentationsCommand(
                    editorContext, element, List.of());

            assertTrue(cmd.canUndo());
        }
    }
}
