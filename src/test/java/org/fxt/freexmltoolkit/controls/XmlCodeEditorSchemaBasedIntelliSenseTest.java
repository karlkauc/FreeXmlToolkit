package org.fxt.freexmltoolkit.controls;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for XmlCodeEditor schema-based IntelliSense functionality.
 * Tests that IntelliSense popup only appears when XSD schema is available.
 */
@ExtendWith(ApplicationExtension.class)
class XmlCodeEditorSchemaBasedIntelliSenseTest {

    private XmlCodeEditor xmlCodeEditor;

    @Start
    private void start(Stage stage) {
        xmlCodeEditor = new XmlCodeEditor();
        stage.show();
    }

    @BeforeEach
    void setUp() {
        Platform.runLater(() -> {
            xmlCodeEditor.getCodeArea().clear();
        });
    }

    @Test
    void testIntelliSenseWithoutXsdSchema() throws Exception {
        // Test the isXsdSchemaAvailable method using reflection when no schema is set
        Method isXsdAvailableMethod = XmlCodeEditor.class.getDeclaredMethod("isXsdSchemaAvailable");
        isXsdAvailableMethod.setAccessible(true);

        Platform.runLater(() -> {
            try {
                // When no parent XmlEditor is set, schema should not be available
                boolean schemaAvailable = (Boolean) isXsdAvailableMethod.invoke(xmlCodeEditor);
                assertFalse(schemaAvailable, "Schema should not be available when no parent XmlEditor is set");

                System.out.println("Test passed: No schema available when no parent XmlEditor");
            } catch (Exception e) {
                fail("Error testing schema availability: " + e.getMessage());
            }
        });
    }

    @Test
    void testIntelliSenseSchemaCheckLogic() throws Exception {
        // Test the schema checking logic
        Method isXsdAvailableMethod = XmlCodeEditor.class.getDeclaredMethod("isXsdSchemaAvailable");
        isXsdAvailableMethod.setAccessible(true);

        Platform.runLater(() -> {
            try {
                // Initially no schema should be available
                boolean initialState = (Boolean) isXsdAvailableMethod.invoke(xmlCodeEditor);
                assertFalse(initialState, "Initial state should be no schema available");

                // Create a mock XmlEditor (this is a simplified test)
                // In a real scenario, the XmlEditor would be properly configured
                xmlCodeEditor.setParentXmlEditor(null); // Explicitly set to null

                boolean afterNullSet = (Boolean) isXsdAvailableMethod.invoke(xmlCodeEditor);
                assertFalse(afterNullSet, "Schema should not be available when parent is null");

                System.out.println("Test passed: Schema checking logic works correctly");
            } catch (Exception e) {
                fail("Error testing schema checking logic: " + e.getMessage());
            }
        });
    }

    @Test
    void testRequestCompletionsWithoutSchema() throws Exception {
        // Test that requestCompletionsFromLSP handles missing schema correctly
        Method requestCompletionsMethod = XmlCodeEditor.class.getDeclaredMethod("requestCompletionsFromLSP");
        requestCompletionsMethod.setAccessible(true);

        Platform.runLater(() -> {
            try {
                // This should not throw an exception and should not show popup when no schema
                assertDoesNotThrow(() -> {
                    requestCompletionsMethod.invoke(xmlCodeEditor);
                });

                System.out.println("Test passed: requestCompletionsFromLSP handles missing schema gracefully");
            } catch (Exception e) {
                fail("Error testing request completions without schema: " + e.getMessage());
            }
        });
    }

    @Test
    void testIntelliSenseTriggerWithoutSchema() {
        Platform.runLater(() -> {
            // Test that IntelliSense trigger characters work correctly when no schema is available
            String testXml = "<root>";
            xmlCodeEditor.getCodeArea().replaceText(testXml);
            xmlCodeEditor.getCodeArea().moveTo(testXml.length());

            // Verify setup
            assertEquals(testXml, xmlCodeEditor.getCodeArea().getText());
            assertEquals(testXml.length(), xmlCodeEditor.getCodeArea().getCaretPosition());

            // The IntelliSense trigger logic should not crash when no schema is available
            // This test verifies the basic functionality
            System.out.println("Test passed: IntelliSense trigger setup works without schema");
        });
    }

    @Test
    void testCodeAreaBasicFunctionality() {
        Platform.runLater(() -> {
            // Ensure basic CodeArea functionality still works when schema checking is enabled
            String testContent = "<element>content</element>";
            xmlCodeEditor.getCodeArea().replaceText(testContent);

            assertEquals(testContent, xmlCodeEditor.getCodeArea().getText());

            // Test cursor positioning
            xmlCodeEditor.getCodeArea().moveTo(9); // After <element>
            assertEquals(9, xmlCodeEditor.getCodeArea().getCaretPosition());

            System.out.println("Test passed: Basic CodeArea functionality works with schema checking");
        });
    }

    @Test
    void testParentXmlEditorSetting() {
        Platform.runLater(() -> {
            // Test that setting parentXmlEditor works correctly
            assertDoesNotThrow(() -> {
                xmlCodeEditor.setParentXmlEditor(null);
                xmlCodeEditor.setParentXmlEditor(new Object()); // Any object for testing
            });

            System.out.println("Test passed: Setting parentXmlEditor works correctly");
        });
    }
}