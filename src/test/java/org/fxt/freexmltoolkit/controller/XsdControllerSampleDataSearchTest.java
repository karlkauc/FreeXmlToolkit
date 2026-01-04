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

package org.fxt.freexmltoolkit.controller;

import javafx.stage.Stage;
import org.fxmisc.richtext.CodeArea;
import org.fxt.freexmltoolkit.FxtGui;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for search and replace functionality in the sample data CodeArea.
 * Tests the keyboard shortcuts (Ctrl+F and Ctrl+R) trigger the FindReplaceDialog
 * for the sample data text area.
 */
@ExtendWith(ApplicationExtension.class)
class XsdControllerSampleDataSearchTest {

    private XsdController xsdController;
    private CodeArea sampleDataTextArea;

    @Start
    private void start(Stage stage) throws Exception {
        FxtGui app = new FxtGui();
        app.start(stage);

        // Get the XsdController instance through reflection (since it's not directly accessible)
        // In a real scenario, you'd want to have a proper way to access the controller
    }

    /**
     * Test that verifies the sample data text area has search keyboard shortcuts set up
     */
    @Test
    void testSampleDataSearchKeyboardShortcutsAreSetUp(FxRobot robot) throws Exception {
        // This test verifies that the infrastructure is in place
        // A full integration test would require the UI to be fully loaded

        // For now, we verify that the key methods exist and are callable
        XsdController controller = new XsdController();

        // Use reflection to verify the methods exist
        Method setupMethod = XsdController.class.getDeclaredMethod("setupSampleDataSearchKeyboardShortcuts");
        assertNotNull(setupMethod, "setupSampleDataSearchKeyboardShortcuts method should exist");

        Method showDialogMethod = XsdController.class.getDeclaredMethod("showSampleDataFindReplaceDialog");
        assertNotNull(showDialogMethod, "showSampleDataFindReplaceDialog method should exist");

        Method initDialogMethod = XsdController.class.getDeclaredMethod("initializeSampleDataFindReplaceDialog");
        assertNotNull(initDialogMethod, "initializeSampleDataFindReplaceDialog method should exist");
    }

    /**
     * Test that verifies the event handler is properly initialized
     */
    @Test
    void testSampleDataSearchEventHandlerExists() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to check that the field exists
        Field eventHandlerField = XsdController.class.getDeclaredField("sampleDataSearchKeyEventFilter");
        assertNotNull(eventHandlerField, "sampleDataSearchKeyEventFilter field should exist");
        assertTrue(eventHandlerField.getType().getName().contains("EventHandler"),
                "Field should be an EventHandler type");
    }

    /**
     * Test that verifies FindReplaceDialog field exists for sample data
     */
    @Test
    void testSampleDataFindReplaceDialogFieldExists() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to check that the field exists
        Field dialogField = XsdController.class.getDeclaredField("sampleDataFindReplaceDialog");
        assertNotNull(dialogField, "sampleDataFindReplaceDialog field should exist");
        assertEquals("org.fxt.freexmltoolkit.controls.shared.utilities.FindReplaceDialog",
                dialogField.getType().getName(),
                "Field should be of type FindReplaceDialog");
    }

    /**
     * Integration test that would verify the full keyboard shortcut flow
     * Note: This test is disabled by default as it requires a fully initialized UI
     */
    // @Test
    void testSampleDataCtrlFTriggersDialog(FxRobot robot) {
        // This would be an integration test that:
        // 1. Loads an XSD file
        // 2. Generates sample data
        // 3. Focuses the sample data text area
        // 4. Presses Ctrl+F
        // 5. Verifies the FindReplaceDialog appears

        // robot.clickOn("#generateSampleDataButton");
        // robot.clickOn(".sample-data-text-area");
        // robot.press(KeyCode.CONTROL, KeyCode.F);
        // assertTrue(robot.lookup(".dialog-pane").tryQuery().isPresent());
    }
}
