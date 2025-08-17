package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SchematronService functionality.
 */
class SchematronServiceTest {

    private SchematronService schematronService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        schematronService = new SchematronServiceImpl();
    }

    @Test
    void testValidateXmlWithNullContent() throws IOException {
        // Create a simple Schematron file
        File schematronFile = createSimpleSchematronFile();

        List<SchematronService.SchematronValidationError> errors = schematronService.validateXml(null, schematronFile);

        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("XML content is null or empty", errors.get(0).message());
    }

    @Test
    void testValidateXmlWithEmptyContent() throws IOException {
        // Create a simple Schematron file
        File schematronFile = createSimpleSchematronFile();

        List<SchematronService.SchematronValidationError> errors = schematronService.validateXml("", schematronFile);

        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("XML content is null or empty", errors.get(0).message());
    }

    @Test
    void testValidateXmlWithNullSchematronFile() {
        String xmlContent = "<root><element>test</element></root>";

        List<SchematronService.SchematronValidationError> errors = schematronService.validateXml(xmlContent, null);

        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("Schematron file is null or does not exist", errors.get(0).message());
    }

    @Test
    void testValidateXmlWithNonExistentSchematronFile() {
        String xmlContent = "<root><element>test</element></root>";
        File nonExistentFile = new File("non-existent-file.sch");

        List<SchematronService.SchematronValidationError> errors = schematronService.validateXml(xmlContent, nonExistentFile);

        assertNotNull(errors);
        assertEquals(1, errors.size());
        assertEquals("Schematron file is null or does not exist", errors.get(0).message());
    }

    @Test
    void testIsValidSchematronFileWithValidFile() throws IOException {
        File schematronFile = createSimpleSchematronFile();

        boolean isValid = schematronService.isValidSchematronFile(schematronFile);

        assertTrue(isValid);
    }

    @Test
    void testIsValidSchematronFileWithInvalidFile() throws IOException {
        File invalidFile = createInvalidFile();

        boolean isValid = schematronService.isValidSchematronFile(invalidFile);

        assertFalse(isValid);
    }

    @Test
    void testIsValidSchematronFileWithNullFile() {
        boolean isValid = schematronService.isValidSchematronFile(null);

        assertFalse(isValid);
    }

    @Test
    void testIsValidSchematronFileWithNonExistentFile() {
        File nonExistentFile = new File("non-existent-file.sch");

        boolean isValid = schematronService.isValidSchematronFile(nonExistentFile);

        assertFalse(isValid);
    }

    /**
     * Creates a simple Schematron file for testing.
     */
    private File createSimpleSchematronFile() throws IOException {
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <title>Simple Test Schema</title>
                    <ns uri="http://example.com" prefix="ex"/>
                    <pattern id="test-pattern">
                        <title>Test Pattern</title>
                        <rule context="root">
                            <assert test="element">Root element must contain an element child</assert>
                        </rule>
                    </pattern>
                </schema>
                """;

        File schematronFile = tempDir.resolve("test.sch").toFile();
        Files.write(schematronFile.toPath(), schematronContent.getBytes());
        return schematronFile;
    }

    /**
     * Creates an invalid file for testing.
     */
    private File createInvalidFile() throws IOException {
        String invalidContent = "This is not a valid XML or Schematron file";

        File invalidFile = tempDir.resolve("invalid.txt").toFile();
        Files.write(invalidFile.toPath(), invalidContent.getBytes());
        return invalidFile;
    }
}
