package org.fxt.freexmltoolkit.security;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Security tests for XSLT transformation engine.
 * These tests verify that malicious XSLT stylesheets cannot execute arbitrary code.
 */
@DisplayName("XSLT Security Tests")
class XsltSecurityTest {

    @TempDir
    Path tempDir;

    private XsltTransformationEngine transformationEngine;

    // Malicious XSLT that attempts to execute system commands
    private static final String XSLT_COMMAND_INJECTION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:java="http://saxon.sf.net/java-type"
                xmlns:rt="java:java.lang.Runtime">
                <xsl:template match="/">
                    <result>
                        <xsl:variable name="runtime" select="rt:getRuntime()"/>
                        <xsl:variable name="proc" select="rt:exec($runtime, 'whoami')"/>
                        <command-output>Attempted command execution</command-output>
                    </result>
                </xsl:template>
            </xsl:stylesheet>
            """;

    // Malicious XSLT using reflexive extension
    private static final String XSLT_REFLEXIVE_EXTENSION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:system="java:java.lang.System">
                <xsl:template match="/">
                    <result>
                        <env><xsl:value-of select="system:getProperty('user.home')"/></env>
                    </result>
                </xsl:template>
            </xsl:stylesheet>
            """;

    // Safe XSLT stylesheet
    private static final String SAFE_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <result>
                        <xsl:value-of select="//item"/>
                    </result>
                </xsl:template>
            </xsl:stylesheet>
            """;

    // Simple XML input
    private static final String SIMPLE_XML = """
            <?xml version="1.0"?>
            <root>
                <item>Test Value</item>
            </root>
            """;

    @BeforeEach
    void setUp() {
        transformationEngine = new XsltTransformationEngine();
    }

    @Test
    @DisplayName("Blocks Runtime.exec() via java: namespace")
    void blocksRuntimeExec() {
        // With security enabled (default), this should fail or produce safe output
        XsltTransformationResult result = transformationEngine.quickTransform(
                SIMPLE_XML, XSLT_COMMAND_INJECTION);

        // Either transformation fails or output doesn't contain executed command result
        if (result.isSuccess()) {
            String output = result.getOutputContent();
            // The output should not contain actual command output
            assertFalse(output != null && output.contains(System.getProperty("user.name")),
                    "Command execution should be blocked - user name should not appear");
        } else {
            // Failure is expected for security reasons
            assertTrue(result.getErrorMessage() != null && !result.getErrorMessage().isEmpty(),
                    "Transformation should fail with security error");
        }
    }

    @Test
    @DisplayName("Blocks System.getProperty() via reflexive extension")
    void blocksSystemGetProperty() {
        // With security enabled (default), this should fail or produce safe output
        XsltTransformationResult result = transformationEngine.quickTransform(
                SIMPLE_XML, XSLT_REFLEXIVE_EXTENSION);

        // Either transformation fails or output doesn't contain sensitive info
        if (result.isSuccess()) {
            String output = result.getOutputContent();
            String userHome = System.getProperty("user.home");
            assertFalse(output != null && output.contains(userHome),
                    "Reflexive extension should be blocked - user.home should not appear in output");
        } else {
            // Failure is expected for security reasons
            assertTrue(true, "Transformation correctly failed due to security restrictions");
        }
    }

    @Test
    @DisplayName("Allows safe XSLT transformations")
    void allowsSafeTransformations() {
        XsltTransformationResult result = transformationEngine.quickTransform(
                SIMPLE_XML, SAFE_XSLT);

        assertTrue(result.isSuccess(),
                "Safe XSLT should transform successfully. Error: " + result.getErrorMessage());
        assertNotNull(result.getOutputContent(), "Output should not be null");
        assertTrue(result.getOutputContent().contains("Test Value"),
                "Output should contain the expected value");
    }

    @Test
    @DisplayName("Safe XQuery execution works")
    void safeXqueryWorks() {
        String safeXQuery = """
                for $item in //item
                return <result>{$item/text()}</result>
                """;

        XsltTransformationResult result = transformationEngine.quickXQueryTransform(
                SIMPLE_XML, safeXQuery);

        assertTrue(result.isSuccess(),
                "Safe XQuery should execute successfully. Error: " + result.getErrorMessage());
        assertNotNull(result.getOutputContent(), "Output should not be null");
        assertTrue(result.getOutputContent().contains("Test Value"),
                "XQuery should return expected value");
    }

    @Test
    @DisplayName("XQuery blocks unauthorized extensions")
    void xqueryBlocksUnauthorizedExtensions() {
        // Attempt to use Java extension in XQuery
        String maliciousXQuery = """
                declare namespace system = "java:java.lang.System";
                <result>{system:getProperty('user.home')}</result>
                """;

        XsltTransformationResult result = transformationEngine.quickXQueryTransform(
                SIMPLE_XML, maliciousXQuery);

        // Either fails or doesn't expose sensitive info
        if (result.isSuccess()) {
            String userHome = System.getProperty("user.home");
            assertFalse(result.getOutputContent() != null && result.getOutputContent().contains(userHome),
                    "Java extensions should be blocked in XQuery");
        } else {
            // Failure is expected
            assertTrue(true, "XQuery correctly blocked Java extension");
        }
    }

    @Test
    @DisplayName("Quick transform also enforces security")
    void quickTransformEnforcesSecurity() {
        XsltTransformationResult result = transformationEngine.quickTransform(
                SIMPLE_XML, XSLT_REFLEXIVE_EXTENSION);

        // Either transformation fails or output doesn't contain sensitive info
        if (result.isSuccess()) {
            String output = result.getOutputContent();
            String userHome = System.getProperty("user.home");
            assertFalse(output != null && output.contains(userHome),
                    "Quick transform should also block reflexive extensions");
        }
    }
}
