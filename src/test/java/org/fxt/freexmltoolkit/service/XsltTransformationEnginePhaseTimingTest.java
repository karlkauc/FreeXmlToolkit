package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The XSLT engine populates the phase timings (compile / transform) and the
 * previously dormant inputSize/memoryUsage fields of {@link XsltTransformationResult}.
 */
class XsltTransformationEnginePhaseTimingTest {

    private static final String XML = "<root><item>1</item><item>2</item></root>";

    // A unique comment per test run defeats the stylesheet cache for the first pass.
    private static String stylesheet(String marker) {
        return """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
                    <!-- %s -->
                    <xsl:output method="xml"/>
                    <xsl:template match="/">
                        <count><xsl:value-of select="count(//item)"/></count>
                    </xsl:template>
                </xsl:stylesheet>
                """.formatted(marker);
    }

    @Test
    void transformPopulatesPhaseTimingsAndSizes() {
        var engine = XsltTransformationEngine.getInstance();
        String xslt = stylesheet("phase-timing-" + System.identityHashCode(this));

        XsltTransformationResult result = engine.transform(XML, xslt, Map.of(),
                XsltTransformationEngine.OutputFormat.XML);

        assertTrue(result.isSuccess(), () -> "transform failed: " + result.getErrorMessage());
        assertTrue(result.getExecutionTime() >= 0);
        assertTrue(result.getCompilationTime() >= 0, "compilation phase must be measured");
        assertTrue(result.getTransformationTime() >= 0, "transformation phase must be measured");
        // Saxon serializes during the transform — total must cover both measured phases
        assertTrue(result.getExecutionTime()
                        >= result.getCompilationTime() + result.getTransformationTime() - 1,
                "phases must not exceed the total execution time");
        assertEquals(XML.length(), result.getInputSize(), "input size must be populated");
        assertTrue(result.getOutputContent().contains("<count>2</count>"));
    }

    @Test
    void cachedStylesheetReportsNearZeroCompilation() {
        var engine = XsltTransformationEngine.getInstance();
        String xslt = stylesheet("cache-hit-" + System.identityHashCode(this));

        engine.transform(XML, xslt, Map.of(), XsltTransformationEngine.OutputFormat.XML);
        XsltTransformationResult second = engine.transform(XML, xslt, Map.of(),
                XsltTransformationEngine.OutputFormat.XML);

        assertTrue(second.isSuccess());
        // The second run hits the compiled-stylesheet cache: compile phase collapses
        // (allow a small epsilon for clock granularity).
        assertTrue(second.getCompilationTime() <= 5,
                "cache hit should report (near) zero compilation, was: " + second.getCompilationTime());
    }
}
