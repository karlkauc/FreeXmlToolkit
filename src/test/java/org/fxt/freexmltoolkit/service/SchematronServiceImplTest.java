package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SchematronServiceImplTest {

    private SchematronService schematronService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        schematronService = new SchematronServiceImpl();
    }

    @Test
    void testValidateXmlWithMultipleErrors() throws IOException {
        // Create test Schematron file
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="test-pattern">
                        <rule context="//person">
                            <assert test="@id">Person must have an id attribute</assert>
                            <assert test="name">Person must have a name element</assert>
                            <assert test="age">Person must have an age element</assert>
                        </rule>
                        <rule context="//age">
                            <assert test="number(.) &gt;= 0">Age must be a non-negative number</assert>
                            <assert test="number(.) &lt;= 150">Age must be realistic (≤ 150)</assert>
                        </rule>
                    </pattern>
                </schema>
                """;

        // Create test XML with multiple errors
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <people>
                    <person>
                        <name>John Doe</name>
                    </person>
                    <person id="2">
                        <age>-5</age>
                    </person>
                    <person id="3">
                        <name>Jane Smith</name>
                        <age>200</age>
                    </person>
                </people>
                """;

        // Write files to temp directory
        Path schematronFile = tempDir.resolve("test.sch");
        Files.writeString(schematronFile, schematronContent);

        // Test validation
        List<SchematronService.SchematronValidationError> errors =
                schematronService.validateXml(xmlContent, schematronFile.toFile());

        // Print errors for debugging
        System.out.println("Detected " + (errors != null ? errors.size() : 0) + " Schematron errors:");
        if (errors != null) {
            for (SchematronService.SchematronValidationError error : errors) {
                System.out.println("- " + error.message() + " (Rule: " + error.ruleId() + ")");
            }
        }

        // Should have multiple errors
        assertNotNull(errors);
        assertTrue(errors.size() > 1, "Should detect multiple validation errors");
    }

    @Test
    void testValidXmlReturnsNoErrors() throws IOException {
        // Create test Schematron file
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="test-pattern">
                        <rule context="//person">
                            <assert test="@id">Person must have an id attribute</assert>
                            <assert test="name">Person must have a name element</assert>
                        </rule>
                    </pattern>
                </schema>
                """;

        // Create valid XML
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <people>
                    <person id="1">
                        <name>John Doe</name>
                    </person>
                    <person id="2">
                        <name>Jane Smith</name>
                    </person>
                </people>
                """;

        // Write files to temp directory
        Path schematronFile = tempDir.resolve("test.sch");
        Files.writeString(schematronFile, schematronContent);

        // Test validation
        List<SchematronService.SchematronValidationError> errors =
                schematronService.validateXml(xmlContent, schematronFile.toFile());

        // Should have no errors
        assertNotNull(errors);
        assertTrue(errors.isEmpty(), "Valid XML should not have validation errors");
    }

    @Test
    void testValidateXmlWithComplexXPathExpressions() throws IOException {
        // Create test Schematron file with XPath expressions that contain < and > operators
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="business-rules">
                        <rule context="//person">
                            <assert test="@id">Person must have an id attribute</assert>
                            <assert test="name">Person must have a name element</assert>
                            <assert test="age">Person must have an age element</assert>
                        </rule>
                        <rule context="//age">
                            <assert test="number(.) >= 0">Age must be a non-negative number</assert>
                            <assert test="number(.) &lt;= 150">Age must be realistic (≤ 150)</assert>
                            <assert test="string-length(.) > 0">Age cannot be empty</assert>
                        </rule>
                        <rule context="//name">
                            <assert test="string-length(.) >= 2">Name must have at least 2 characters</assert>
                            <assert test="not(contains(., '  '))">Name should not contain double spaces</assert>
                        </rule>
                    </pattern>
                </schema>
                """;

        // Create test XML with multiple types of errors
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <people>
                    <person>
                        <name>A</name>
                    </person>
                    <person id="2">
                        <age>-5</age>
                    </person>
                    <person id="3">
                        <name>Jane  Smith</name>
                        <age>200</age>
                    </person>
                    <person id="4">
                        <name></name>
                        <age></age>
                    </person>
                </people>
                """;

        // Write files to temp directory
        Path schematronFile = tempDir.resolve("complex.sch");
        Files.writeString(schematronFile, schematronContent);

        // Test validation
        List<SchematronService.SchematronValidationError> errors =
                schematronService.validateXml(xmlContent, schematronFile.toFile());

        // Print errors for debugging
        System.out.println("Complex XPath test - Detected " + (errors != null ? errors.size() : 0) + " Schematron errors:");
        if (errors != null) {
            for (SchematronService.SchematronValidationError error : errors) {
                System.out.println("- " + error.message() + " (Rule: " + error.ruleId() + ")");
            }
        }

        // Should have multiple errors (at least 5-6 different rule violations)
        assertNotNull(errors);
        assertTrue(errors.size() >= 5, "Should detect multiple validation errors with complex XPath expressions");
    }

    @Test
    void testIsValidSchematronFile() throws IOException {
        // Create valid Schematron file
        String validSchematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="test-pattern">
                        <rule context="//element">
                            <assert test="@attr">Element must have attr attribute</assert>
                        </rule>
                    </pattern>
                </schema>
                """;

        Path schematronFile = tempDir.resolve("valid.sch");
        Files.writeString(schematronFile, validSchematronContent);

        assertTrue(schematronService.isValidSchematronFile(schematronFile.toFile()));

        // Test with invalid file
        Path invalidFile = tempDir.resolve("invalid.txt");
        Files.writeString(invalidFile, "This is not XML");

        assertFalse(schematronService.isValidSchematronFile(invalidFile.toFile()));

        // Test with null file
        assertFalse(schematronService.isValidSchematronFile(null));
    }
}