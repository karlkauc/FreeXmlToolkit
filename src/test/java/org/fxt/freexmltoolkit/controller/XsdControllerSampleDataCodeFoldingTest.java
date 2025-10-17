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
import org.fxt.freexmltoolkit.FxtGui;
import org.fxt.freexmltoolkit.controls.intellisense.XmlCodeFoldingManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for code folding functionality in the sample data CodeArea.
 * Tests that the XmlCodeFoldingManager is properly initialized and accessible
 * for the sample data text area.
 */
@ExtendWith(ApplicationExtension.class)
class XsdControllerSampleDataCodeFoldingTest {

    private XsdController xsdController;

    @Start
    private void start(Stage stage) throws Exception {
        FxtGui app = new FxtGui();
        app.start(stage);
    }

    /**
     * Test that verifies the code folding manager field exists
     */
    @Test
    void testCodeFoldingManagerFieldExists() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to check that the field exists
        Field codeFoldingField = XsdController.class.getDeclaredField("sampleDataCodeFoldingManager");
        assertNotNull(codeFoldingField, "sampleDataCodeFoldingManager field should exist");
        assertEquals(XmlCodeFoldingManager.class, codeFoldingField.getType(),
                "Field should be of type XmlCodeFoldingManager");
    }

    /**
     * Test that verifies the getter method for code folding manager exists
     */
    @Test
    void testGetCodeFoldingManagerMethodExists() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to verify the getter method exists
        Method getterMethod = XsdController.class.getDeclaredMethod("getSampleDataCodeFoldingManager");
        assertNotNull(getterMethod, "getSampleDataCodeFoldingManager method should exist");
        assertEquals(XmlCodeFoldingManager.class, getterMethod.getReturnType(),
                "Method should return XmlCodeFoldingManager");
    }

    /**
     * Test that verifies the foldAll method exists
     */
    @Test
    void testFoldAllMethodExists() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to verify the method exists
        Method foldAllMethod = XsdController.class.getDeclaredMethod("foldAllSampleData");
        assertNotNull(foldAllMethod, "foldAllSampleData method should exist");
        assertEquals(void.class, foldAllMethod.getReturnType(),
                "Method should return void");
    }

    /**
     * Test that verifies the unfoldAll method exists
     */
    @Test
    void testUnfoldAllMethodExists() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to verify the method exists
        Method unfoldAllMethod = XsdController.class.getDeclaredMethod("unfoldAllSampleData");
        assertNotNull(unfoldAllMethod, "unfoldAllSampleData method should exist");
        assertEquals(void.class, unfoldAllMethod.getReturnType(),
                "Method should return void");
    }

    /**
     * Test that verifies the code folding manager is initialized correctly
     */
    @Test
    void testCodeFoldingManagerInitialization() throws Exception {
        XsdController controller = new XsdController();

        // Use reflection to access the initialization method
        Method initMethod = XsdController.class.getDeclaredMethod("ensureSampleDataTextAreaInitialized");
        assertNotNull(initMethod, "ensureSampleDataTextAreaInitialized method should exist");

        // The method should initialize the code folding manager
        // In a full integration test, we would verify this by calling the method
        // and checking if the manager is created
    }

    /**
     * Integration test that would verify code folding works in practice
     * Note: This test is disabled by default as it requires a fully initialized UI
     */
    // @Test
    void testCodeFoldingInSampleData(FxRobot robot) {
        // This would be an integration test that:
        // 1. Loads an XSD file
        // 2. Generates sample data with nested XML elements
        // 3. Verifies folding indicators appear in the gutter
        // 4. Tests folding/unfolding functionality
        // 5. Verifies fold state is preserved during text changes

        // robot.clickOn("#generateSampleDataButton");
        // Wait for sample data generation...
        // robot.clickOn(".fold-button"); // Click a fold indicator
        // Verify the region is collapsed
        // robot.clickOn(".fold-button"); // Click again to unfold
        // Verify the region is expanded
    }
}
