package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.fxt.freexmltoolkit.service.XsltProfile.CallNode;
import org.fxt.freexmltoolkit.service.XsltProfile.ComponentStats;
import org.fxt.freexmltoolkit.service.XsltProfile.Kind;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.ProfiledTransformation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests the XSLT profiler: components, recursion, call tree, lines, reports. */
@DisplayName("XSLT profiler")
class XsltProfilerTest {

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema" exclude-result-prefixes="f xs">
              <xsl:output method="xml"/>
              <xsl:function name="f:fib" as="xs:integer">
                <xsl:param name="n" as="xs:integer"/>
                <xsl:sequence select="if ($n lt 2) then $n else f:fib($n - 1) + f:fib($n - 2)"/>
              </xsl:function>
              <xsl:template match="/">
                <out>
                  <xsl:apply-templates select="//n"/>
                  <xsl:apply-templates select="//n" mode="twice"/>
                  <xsl:call-template name="footer"/>
                </out>
              </xsl:template>
              <xsl:template match="n">
                <fib><xsl:value-of select="f:fib(xs:integer(.))"/></fib>
              </xsl:template>
              <xsl:template match="n" mode="twice">
                <twice><xsl:value-of select=". * 2"/></twice>
              </xsl:template>
              <xsl:template name="footer">
                <footer/>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String XML = "<r><n>10</n><n>12</n><n>5</n></r>";

    private static ProfiledTransformation run(int warmups) {
        return XsltTransformationEngine.getInstance().profileTransformation(XML, null, XSLT, "file:///tmp/p.xsl",
                Map.of(), OutputFormat.XML, warmups, null);
    }

    private static Optional<ComponentStats> component(XsltProfile profile, Kind kind, String name) {
        return profile.components().stream()
                .filter(s -> s.component().kind() == kind && s.component().name().equals(name)).findFirst();
    }

    @Test
    @DisplayName("calls, total and self time per template and function; output as usual")
    void components() {
        ProfiledTransformation run = run(1);
        assertTrue(run.result().isSuccess(), run.result().getErrorMessage());
        assertTrue(run.result().getOutputContent().contains("<fib>55</fib>"), run.result().getOutputContent());
        XsltProfile profile = run.profile();
        assertNotNull(profile);
        assertEquals(1, profile.warmupRuns());

        // fib(10) + fib(12) + fib(5) make 177 + 465 + 15 calls
        ComponentStats fib = component(profile, Kind.FUNCTION, "f:fib#1").orElseThrow(() ->
                new AssertionError(profile.components().toString()));
        assertEquals(657, fib.calls());
        assertEquals(3, component(profile, Kind.TEMPLATE_RULE, "match=\"n\"").orElseThrow().calls());
        assertEquals(3, component(profile, Kind.TEMPLATE_RULE, "match=\"n\" mode=\"twice\"").orElseThrow().calls());
        assertEquals(1, component(profile, Kind.NAMED_TEMPLATE, "footer").orElseThrow().calls());

        ComponentStats root = component(profile, Kind.TEMPLATE_RULE, "match=\"/\"").orElseThrow();
        assertTrue(root.totalNanos() >= root.selfNanos());
        assertTrue(root.totalNanos() <= profile.totalNanos(), "nothing longer than the run");
        assertTrue(fib.totalNanos() <= profile.totalNanos(), "recursion is not counted twice");
        for (ComponentStats s : profile.components()) {
            assertTrue(s.selfNanos() <= s.totalNanos() || s.calls() > 0, s.toString());
        }
        assertEquals("p.xsl", fib.component().module());
        assertTrue(fib.component().line() > 0);
        assertEquals(profile.totalCalls(), profile.components().stream().mapToLong(ComponentStats::calls).sum());
    }

    @Test
    @DisplayName("call tree: root → match=\"/\" → rules → function; direct recursion folded into one node")
    void callTree() {
        XsltProfile profile = run(0).profile();
        CallNode root = profile.root();
        assertNull(root.component());
        assertEquals(profile.totalNanos(), root.totalNanos());
        assertEquals(1, root.children().size());
        CallNode document = root.children().get(0);
        assertEquals("match=\"/\"", document.label());
        CallNode n = document.children().stream().filter(c -> c.label().equals("match=\"n\"")).findFirst().orElseThrow();
        assertEquals(3, n.calls());
        CallNode fib = n.children().get(0);
        assertEquals("f:fib#1", fib.label());
        assertTrue(fib.children().isEmpty(), "the recursive calls are in the same node");
        assertEquals(657, fib.calls());
        assertTrue(fib.totalNanos() <= n.totalNanos());
        assertTrue(document.children().stream().anyMatch(c -> c.label().equals("footer")));
    }

    @Test
    @DisplayName("self time per line, text and JSON reports; failures have no profile")
    void linesAndReports() {
        XsltProfile profile = run(0).profile();
        assertFalse(profile.lines().isEmpty());
        assertTrue(profile.lines().stream().allMatch(l -> l.line() > 0 && "p.xsl".equals(l.module())));
        String text = profile.toText(5);
        assertTrue(text.startsWith("Total "), text);
        assertTrue(text.contains("function f:fib#1 (p.xsl:"), text);
        String json = profile.toJson();
        assertTrue(json.contains("\"callTree\"") && json.contains("\"selfMs\""), json);

        ProfiledTransformation broken = XsltTransformationEngine.getInstance().profileTransformation(XML, null,
                "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"><xsl:bogus/></xsl:stylesheet>",
                null, Map.of(), OutputFormat.XML, 0, null);
        assertFalse(broken.result().isSuccess());
        assertNull(broken.profile());
    }
}
