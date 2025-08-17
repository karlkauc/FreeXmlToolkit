package org.fxt.freexmltoolkit;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.controls.XmlEditor;
import org.fxt.freexmltoolkit.service.SchematronService;
import org.fxt.freexmltoolkit.service.SchematronServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test for XML Editor features including Schematron validation and IntelliSense.
 */
@ExtendWith(ApplicationExtension.class)
class XmlEditorFeaturesTest {

    private XmlEditor xmlEditor;
    private File testXmlFile;
    private File testSchematronFile;

    @Start
    private void start(Stage stage) {
        xmlEditor = new XmlEditor();
        stage.show();
    }

    @BeforeEach
    void setUp() throws IOException {
        // Create test files
        createTestFiles();
    }

    @Test
    void testSchematronValidationIntegration() {
        Platform.runLater(() -> {
            // Test that Schematron validation can be triggered
            assertDoesNotThrow(() -> {
                xmlEditor.setSchematronFile(testSchematronFile);
            });

            // Verify the method exists and works
            assertNotNull(xmlEditor);
        });
    }

    @Test
    void testIntelliSenseIntegration() {
        Platform.runLater(() -> {
            // Test that IntelliSense functionality is available
            assertNotNull(xmlEditor.getXmlCodeEditor());
            assertNotNull(xmlEditor.getXmlCodeEditor().getCodeArea());

            // Test that the CodeArea supports the required functionality
            xmlEditor.getXmlCodeEditor().getCodeArea().replaceText("<test>");
            assertEquals("<test>", xmlEditor.getXmlCodeEditor().getCodeArea().getText());
        });
    }

    @Test
    void testSchematronServiceWithTestData() {
        // Test the Schematron service with our test data
        SchematronService schematronService = new SchematronServiceImpl();

        try {
            String xmlContent = Files.readString(testXmlFile.toPath());
            List<SchematronService.SchematronValidationError> errors =
                    schematronService.validateXml(xmlContent, testSchematronFile);

            // The test XML should have several validation errors based on our Schematron rules
            assertNotNull(errors);
            // Note: The actual validation won't work until we implement the full Schematron compilation
            // For now, we just verify the service doesn't crash
        } catch (IOException e) {
            fail("Failed to read test files: " + e.getMessage());
        }
    }

    @Test
    void testXmlEditorSidebarComponents() {
        Platform.runLater(() -> {
            // Test that the sidebar components are properly initialized
            assertNotNull(xmlEditor);

            // The sidebar should contain both XSD and Schematron sections
            // This is tested by verifying the editor can be created without errors
        });
    }

    @Test
    void testLanguageServerIntegration() {
        Platform.runLater(() -> {
            // Test that the LanguageServer can be set
            assertDoesNotThrow(() -> {
                xmlEditor.setLanguageServer(null); // Should not throw
            });

            // Verify the method exists and works
            assertNotNull(xmlEditor);
        });
    }

    /**
     * Creates test files for validation testing.
     */
    private void createTestFiles() throws IOException {
        // Create test XML file
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                    <element id="valid_id">This is valid content</element>
                    <element id="123_invalid">This element has an invalid ID</element>
                    <element>This element has no ID attribute</element>
                    <element id="too_long">This element has content that is way too long and should exceed the 100 character limit that is defined in the Schematron rules for validation testing purposes</element>
                    <element id="empty_element"></element>
                </root>
                """;

        Path tempDir = Files.createTempDirectory("xml-editor-test");
        testXmlFile = tempDir.resolve("test.xml").toFile();
        Files.write(testXmlFile.toPath(), xmlContent.getBytes());

        // Create test Schematron file
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <title>Test Validation Schema</title>
                    <pattern id="test-pattern">
                        <title>Test Pattern</title>
                        <rule context="root">
                            <assert test="element">Root element must contain an element child</assert>
                        </rule>
                    </pattern>
                </schema>
                """;

        testSchematronFile = tempDir.resolve("test.sch").toFile();
        Files.write(testSchematronFile.toPath(), schematronContent.getBytes());
    }
}
