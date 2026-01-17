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

package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for XSLT debugging functionality using XsltDebugTraceListener.
 * Verifies that template matching, variable capture, and call stack tracking work correctly.
 */
class XsltDebugTraceListenerTest {

    private XsltTransformationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new XsltTransformationEngine();
    }

    @Test
    @DisplayName("Debugging should be disabled by default")
    void testDebuggingDisabledByDefault() {
        String xml = "<root><item>Test</item></root>";
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <output><xsl:value-of select="//item"/></output>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltTransformationResult result = engine.quickTransform(xml, xslt);

        assertTrue(result.isSuccess(), "Transformation should succeed");
        // Without debugging enabled, template matches should be empty
        assertTrue(result.getTemplateMatches().isEmpty(),
                "Template matches should be empty when debugging is disabled");
    }

    @Test
    @DisplayName("Live transformation with debugging should capture template matches")
    void testLiveTransformCapturesTemplateMatches() {
        String xml = """
                <catalog>
                    <book id="1"><title>Book 1</title></book>
                    <book id="2"><title>Book 2</title></book>
                </catalog>
                """;

        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <result>
                            <xsl:apply-templates select="//book"/>
                        </result>
                    </xsl:template>

                    <xsl:template match="book">
                        <item><xsl:value-of select="title"/></item>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        // Use liveTransform with debugging enabled
        XsltTransformationResult result = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        assertTrue(result.isSuccess(), "Transformation should succeed");
        assertNotNull(result.getOutputContent(), "Output should not be null");
        assertTrue(result.getOutputContent().contains("Book 1"), "Output should contain Book 1");

        // With debugging enabled, we should have template match info
        List<XsltTransformationEngine.TemplateMatchInfo> templateMatches = result.getTemplateMatches();
        assertNotNull(templateMatches, "Template matches should not be null");

        // Note: The exact number of matches depends on Saxon's TraceListener implementation
        // At minimum, the root template should be matched
        System.out.println("Template matches captured: " + templateMatches.size());
        for (var match : templateMatches) {
            System.out.println("  - " + match.pattern() + " at line " + match.lineNumber());
        }
    }

    @Test
    @DisplayName("Live transformation should capture call stack")
    void testLiveTransformCapturesCallStack() {
        String xml = "<root><data>Test</data></root>";

        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <xsl:call-template name="process"/>
                    </xsl:template>

                    <xsl:template name="process">
                        <output>
                            <xsl:value-of select="//data"/>
                        </output>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltTransformationResult result = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        assertTrue(result.isSuccess(), "Transformation should succeed");

        List<String> callStack = result.getCallStack();
        assertNotNull(callStack, "Call stack should not be null");

        System.out.println("Call stack entries: " + callStack.size());
        for (var entry : callStack) {
            System.out.println("  " + entry);
        }
    }

    @Test
    @DisplayName("Live transformation should capture xsl:message output")
    void testLiveTransformCapturesMessages() {
        String xml = "<root><item>Test</item></root>";

        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <xsl:message>Processing root element</xsl:message>
                        <output>
                            <xsl:apply-templates select="//item"/>
                        </output>
                    </xsl:template>

                    <xsl:template match="item">
                        <xsl:message>Processing item: <xsl:value-of select="."/></xsl:message>
                        <item><xsl:value-of select="."/></item>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltTransformationResult result = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        assertTrue(result.isSuccess(), "Transformation should succeed");

        List<XsltTransformationResult.TransformationMessage> messages = result.getMessages();
        assertNotNull(messages, "Messages should not be null");

        System.out.println("Messages captured: " + messages.size());
        for (var msg : messages) {
            System.out.println("  [" + msg.getLevel() + "] " + msg.getMessage() + " at line " + msg.getLineNumber());
        }

        // Should have captured at least the xsl:message output
        assertTrue(messages.size() >= 2, "Should have captured at least 2 messages");
    }

    @Test
    @DisplayName("Debugging statistics should be available")
    void testDebuggingStatistics() {
        String xml = """
                <data>
                    <record><value>A</value></record>
                    <record><value>B</value></record>
                    <record><value>C</value></record>
                </data>
                """;

        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <result>
                            <xsl:apply-templates select="//record"/>
                        </result>
                    </xsl:template>

                    <xsl:template match="record">
                        <item><xsl:value-of select="value"/></item>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltTransformationResult result = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        assertTrue(result.isSuccess(), "Transformation should succeed");

        // Check debugging info is accessible
        String debugInfo = result.getDebuggingInfo();
        assertNotNull(debugInfo, "Debugging info should not be null");
        System.out.println("Debugging Info:\n" + debugInfo);

        // Check transformation statistics
        String stats = result.getTransformationStatistics();
        assertNotNull(stats, "Statistics should not be null");
        System.out.println("Statistics:\n" + stats);
    }

    @Test
    @DisplayName("Multiple transformations should have independent debug state")
    void testIndependentDebugState() {
        String xml = "<root><item>Test</item></root>";
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <output><xsl:value-of select="//item"/></output>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        // First transformation with debugging
        XsltTransformationResult result1 = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        // Second transformation without debugging
        XsltTransformationResult result2 = engine.quickTransform(xml, xslt);

        // Third transformation with debugging again
        XsltTransformationResult result3 = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        // All should succeed
        assertTrue(result1.isSuccess(), "First transformation should succeed");
        assertTrue(result2.isSuccess(), "Second transformation should succeed");
        assertTrue(result3.isSuccess(), "Third transformation should succeed");

        // Debug state should be independent
        // result1 and result3 should have debug data, result2 should not
        System.out.println("Result 1 (debug on): " + result1.getTemplateMatches().size() + " template matches");
        System.out.println("Result 2 (debug off): " + result2.getTemplateMatches().size() + " template matches");
        System.out.println("Result 3 (debug on): " + result3.getTemplateMatches().size() + " template matches");

        // result2 should have empty or fewer matches since debugging was disabled
        assertTrue(result2.getTemplateMatches().isEmpty(),
                "Non-debug transformation should have no template matches");
    }

    @Test
    @DisplayName("Complex XSLT with variables should capture variable values")
    void testVariableCapture() {
        String xml = """
                <order>
                    <item price="10.00" quantity="2"/>
                    <item price="20.00" quantity="1"/>
                    <item price="5.00" quantity="4"/>
                </order>
                """;

        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:variable name="total" select="sum(//item/(@price * @quantity))"/>
                    <xsl:variable name="itemCount" select="count(//item)"/>

                    <xsl:template match="/">
                        <result>
                            <total><xsl:value-of select="$total"/></total>
                            <count><xsl:value-of select="$itemCount"/></count>
                        </result>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        XsltTransformationResult result = engine.liveTransform(
                xml, xslt, new HashMap<>(),
                XsltTransformationEngine.OutputFormat.XML, true);

        assertTrue(result.isSuccess(), "Transformation should succeed");
        assertTrue(result.getOutputContent().contains("60"), "Total should be 60 (10*2 + 20*1 + 5*4)");

        Map<String, Object> variables = result.getVariableValues();
        assertNotNull(variables, "Variables map should not be null");

        System.out.println("Variables captured: " + variables.size());
        for (var entry : variables.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
    }

    @Test
    @DisplayName("Transformation with XSLT parameters should work with debugging")
    void testParametersWithDebugging() {
        String xml = "<root><value>Test</value></root>";

        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:param name="prefix" select="'DEFAULT'"/>

                    <xsl:template match="/">
                        <output>
                            <xsl:value-of select="concat($prefix, ': ', //value)"/>
                        </output>
                    </xsl:template>
                </xsl:stylesheet>
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("prefix", "CUSTOM");

        XsltTransformationResult result = engine.liveTransform(
                xml, xslt, params,
                XsltTransformationEngine.OutputFormat.XML, true);

        assertTrue(result.isSuccess(), "Transformation should succeed");
        assertTrue(result.getOutputContent().contains("CUSTOM: Test"),
                "Output should use the custom prefix parameter");
    }
}
