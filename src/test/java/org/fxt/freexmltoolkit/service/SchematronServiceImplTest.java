package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchematronServiceImplTest {

    private SchematronService schematronService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        schematronService = new SchematronServiceImpl();
    }

    @Test
    void testInvalidSchematronFileThrowsException() throws IOException {
        // Create an invalid Schematron file (not proper XML)
        Path invalidSchematronFile = tempDir.resolve("invalid.sch");
        Files.writeString(invalidSchematronFile, "This is not valid XML/Schematron content");

        // Create a valid XML file to test
        String xmlContent = "<?xml version=\"1.0\"?><root>Test</root>";

        // Test that SchematronLoadException is thrown for invalid Schematron file
        assertThrows(SchematronLoadException.class, () -> {
            schematronService.validateXml(xmlContent, invalidSchematronFile.toFile());
        });
    }

    @Test
    void testNonExistentSchematronFileThrowsException() {
        // Create a non-existent Schematron file
        Path nonExistentSchematronFile = tempDir.resolve("nonexistent.sch");

        // Create a valid XML file to test
        String xmlContent = "<?xml version=\"1.0\"?><root>Test</root>";

        // Test that SchematronLoadException is thrown for non-existent Schematron file
        SchematronLoadException exception = assertThrows(SchematronLoadException.class, () -> {
            schematronService.validateXml(xmlContent, nonExistentSchematronFile.toFile());
        });

        // Verify the exception message contains useful information
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void testValidateXmlWithMultipleErrors() throws IOException, SchematronLoadException {
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
    void testValidXmlReturnsNoErrors() throws IOException, SchematronLoadException {
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
    void testValidateXmlWithComplexXPathExpressions() throws IOException, SchematronLoadException {
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
    void testXPath2RulesAreSupported() throws IOException, SchematronLoadException {
        // queryBinding="xslt2" rules using XPath 2.0 functions must compile and fire
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                    <pattern id="xpath2-rules">
                        <rule context="person">
                            <assert test="matches(phone, '^\\+[0-9]+$')">Phone must start with + followed by digits</assert>
                            <assert test="every $h in hobby satisfies string-length($h) &gt; 2">Each hobby needs at least 3 characters</assert>
                        </rule>
                    </pattern>
                </schema>
                """;
        String xmlContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <people>
                    <person>
                        <phone>0043123</phone>
                        <hobby>ok-hobby</hobby>
                        <hobby>x</hobby>
                    </person>
                </people>
                """;

        Path schematronFile = tempDir.resolve("xpath2.sch");
        Files.writeString(schematronFile, schematronContent);

        List<SchematronService.SchematronValidationError> errors =
                schematronService.validateXml(xmlContent, schematronFile.toFile());

        assertNotNull(errors);
        assertEquals(2, errors.size(), "Both XPath 2.0 rules should fire: " + errors);
    }

    @Test
    void testSchIncludeResolvedRelativeToSchematronFile() throws IOException, SchematronLoadException {
        // sch:include with a file-relative href must be resolved against the .sch location
        Path includedFile = tempDir.resolve("included-rules.sch");
        Files.writeString(includedFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <pattern xmlns="http://purl.oclc.org/dsdl/schematron" id="included-pattern">
                    <rule context="person">
                        <assert test="@id">Person must have an id attribute</assert>
                    </rule>
                </pattern>
                """);
        Path mainFile = tempDir.resolve("main.sch");
        Files.writeString(mainFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                    <include href="included-rules.sch"/>
                </schema>
                """);

        String xmlContent = "<?xml version=\"1.0\"?><people><person><name>John</name></person></people>";

        List<SchematronService.SchematronValidationError> errors =
                schematronService.validateXml(xmlContent, mainFile.toFile());

        assertNotNull(errors);
        assertEquals(1, errors.size(), "The included rule should fire: " + errors);
        assertTrue(errors.get(0).message().contains("id attribute"));
    }

    @Test
    void testModifiedSchematronFileIsRecompiled() throws Exception {
        // The compiled-schematron cache must be invalidated when the file changes on disk
        Path schematronFile = tempDir.resolve("evolving.sch");
        Files.writeString(schematronFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="v1">
                        <rule context="person">
                            <assert test="@id">V1: id required</assert>
                        </rule>
                    </pattern>
                </schema>
                """);
        String xmlContent = "<?xml version=\"1.0\"?><people><person/></people>";

        List<SchematronService.SchematronValidationError> firstRun =
                schematronService.validateXml(xmlContent, schematronFile.toFile());
        assertEquals(1, firstRun.size());
        assertTrue(firstRun.get(0).message().contains("V1"));

        Files.writeString(schematronFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="v2">
                        <rule context="person">
                            <assert test="@id">V2: id required</assert>
                            <assert test="name">V2: name required</assert>
                        </rule>
                    </pattern>
                </schema>
                """);
        // Ensure a different mtime even on coarse-grained filesystems
        Files.setLastModifiedTime(schematronFile,
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));

        List<SchematronService.SchematronValidationError> secondRun =
                schematronService.validateXml(xmlContent, schematronFile.toFile());
        assertEquals(2, secondRun.size(), "Updated rules must be applied after the file changed: " + secondRun);
        assertTrue(secondRun.get(0).message().contains("V2"));
    }

    @Test
    void testSvrlReportIsReturnedAndParseable() throws Exception {
        String schematronContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                    <pattern id="p">
                        <rule context="person">
                            <assert test="@id">Person must have an id attribute</assert>
                        </rule>
                    </pattern>
                </schema>
                """;
        Path schematronFile = tempDir.resolve("svrl.sch");
        Files.writeString(schematronFile, schematronContent);

        SchematronService.SchematronReport report = schematronService.validateXmlWithSvrl(
                "<?xml version=\"1.0\"?><people><person/></people>", schematronFile.toFile());

        assertNotNull(report.svrl(), "Raw SVRL must be returned");
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(
                new org.xml.sax.InputSource(new java.io.StringReader(report.svrl())));
        var failed = doc.getElementsByTagNameNS("http://purl.oclc.org/dsdl/svrl", "failed-assert");
        assertEquals(1, failed.getLength(), "SVRL must contain the failed assert");

        assertEquals(1, report.errors().size());
        SchematronService.SchematronValidationError error = report.errors().get(0);
        assertNotNull(error.context(), "Finding must carry the SVRL location");
        assertNotNull(error.ruleId(), "Finding must carry the test expression");
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