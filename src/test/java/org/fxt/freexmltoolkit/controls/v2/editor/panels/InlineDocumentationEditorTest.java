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

package org.fxt.freexmltoolkit.controls.v2.editor.panels;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.fxt.freexmltoolkit.controls.v2.editor.XsdEditorContext;
import org.fxt.freexmltoolkit.controls.v2.editor.selection.SelectionModel;
import org.fxt.freexmltoolkit.controls.v2.model.XsdDocumentation;
import org.fxt.freexmltoolkit.controls.v2.model.XsdElement;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * Tests for InlineDocumentationEditor.
 * Uses TestFX ApplicationExtension for JavaFX thread initialization.
 */
@ExtendWith(ApplicationExtension.class)
class InlineDocumentationEditorTest {

    private InlineDocumentationEditor editor;
    private XsdEditorContext editorContext;
    private XsdSchema schema;

    @Start
    private void start(Stage stage) {
        schema = new XsdSchema();
        schema.setTargetNamespace("http://example.com/test");
        SelectionModel selectionModel = new SelectionModel();
        editorContext = new XsdEditorContext(schema, selectionModel);
        editorContext.setEditMode(true);

        editor = new InlineDocumentationEditor(editorContext);

        Scene scene = new Scene(editor, 400, 300);
        stage.setScene(scene);
        stage.show();
    }

    @Test
    @DisplayName("Should start with empty state when no node is set")
    void testInitialEmptyState() throws Exception {
        runOnFxThread(() -> {
            assertEquals(-1, editor.getSelectedIndex());
            assertTrue(editor.getDocumentations().isEmpty());
        });
    }

    @Test
    @DisplayName("Should load documentation entries from node")
    void testLoadFromNode() throws Exception {
        runOnFxThread(() -> {
            XsdElement element = new XsdElement("testElement");
            element.addDocumentation(new XsdDocumentation("Hello world", "en"));
            element.addDocumentation(new XsdDocumentation("Hallo Welt", "de"));

            editor.setNode(element);

            assertEquals(2, editor.getDocumentations().size());
            assertEquals(0, editor.getSelectedIndex());
            assertEquals("en", editor.getDocumentations().get(0).getLang());
            assertEquals("de", editor.getDocumentations().get(1).getLang());
        });
    }

    @Test
    @DisplayName("Should show empty state when node has no documentation")
    void testEmptyNodeDocumentation() throws Exception {
        runOnFxThread(() -> {
            XsdElement element = new XsdElement("emptyElement");
            editor.setNode(element);

            assertTrue(editor.getDocumentations().isEmpty());
            assertEquals(-1, editor.getSelectedIndex());
        });
    }

    @Test
    @DisplayName("Should handle legacy documentation field")
    void testLegacyDocumentationField() throws Exception {
        runOnFxThread(() -> {
            XsdElement element = new XsdElement("legacyElement");
            element.setDocumentation("Legacy documentation text");

            editor.setNode(element);

            assertEquals(1, editor.getDocumentations().size());
            assertEquals("Legacy documentation text", editor.getDocumentations().get(0).getText());
            assertNull(editor.getDocumentations().get(0).getLang());
        });
    }

    @Test
    @DisplayName("Should clear when null node is set")
    void testSetNullNode() throws Exception {
        runOnFxThread(() -> {
            // First set a node with docs
            XsdElement element = new XsdElement("testElement");
            element.addDocumentation(new XsdDocumentation("Test", "en"));
            editor.setNode(element);

            assertEquals(1, editor.getDocumentations().size());

            // Now clear
            editor.setNode(null);

            assertTrue(editor.getDocumentations().isEmpty());
            assertEquals(-1, editor.getSelectedIndex());
        });
    }

    @Test
    @DisplayName("Should handle edit mode toggling")
    void testEditModeToggle() throws Exception {
        runOnFxThread(() -> {
            editor.setEditMode(true);
            // No exception means success - controls should be enabled

            editor.setEditMode(false);
            // No exception means success - controls should be disabled
        });
    }

    @Test
    @DisplayName("Should handle refresh")
    void testRefresh() throws Exception {
        runOnFxThread(() -> {
            XsdElement element = new XsdElement("testElement");
            element.addDocumentation(new XsdDocumentation("Original", "en"));
            editor.setNode(element);

            assertEquals(1, editor.getDocumentations().size());

            // Add doc directly to model and refresh
            element.addDocumentation(new XsdDocumentation("Added", "de"));
            editor.refresh();

            assertEquals(2, editor.getDocumentations().size());
        });
    }

    @Test
    @DisplayName("Should return unmodifiable copy from getDocumentations()")
    void testGetDocumentationsIsUnmodifiable() throws Exception {
        runOnFxThread(() -> {
            XsdElement element = new XsdElement("testElement");
            element.addDocumentation(new XsdDocumentation("Test", "en"));
            editor.setNode(element);

            List<XsdDocumentation> docs = editor.getDocumentations();
            assertThrows(UnsupportedOperationException.class, () -> docs.add(new XsdDocumentation("x")));
        });
    }

    /**
     * Runs an action on the JavaFX application thread and waits for completion.
     */
    private void runOnFxThread(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error[0] = t;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX action timed out");
        if (error[0] != null) {
            if (error[0] instanceof AssertionError ae) {
                throw ae;
            }
            throw new RuntimeException(error[0]);
        }
    }
}
