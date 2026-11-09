package org.fxt.freexmltoolkit.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltProfile.ComponentStats;
import org.fxt.freexmltoolkit.service.XsltProfile.Kind;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.ProfiledTransformation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests the XQuery profiler: user functions as components, the query body as root, lines, errors. */
@DisplayName("XQuery profiler")
class XQueryProfilerTest {

    private static final String QUERY = """
            declare function local:fib($n as xs:integer) as xs:integer {
              if ($n lt 2) then $n else local:fib($n - 1) + local:fib($n - 2)
            };
            declare function local:double($x) { $x * 2 };
            declare variable $limit external;
            <out>{
              for $n in //n
              return <fib n="{$n}">{local:fib(xs:integer($n)) + local:double(xs:integer($n) + xs:integer($limit))}</fib>
            }</out>
            """;

    private static final String XML = "<r><n>10</n><n>12</n><n>5</n></r>";

    private static ProfiledTransformation run(String query, int warmups) {
        return XsltTransformationEngine.getInstance().profileXQuery(XML, null, query, "file:///tmp/p.xq",
                Map.of("limit", "4"), OutputFormat.XML, warmups);
    }

    private static ComponentStats function(XsltProfile profile, String name) {
        return profile.components().stream()
                .filter(s -> s.component().kind() == Kind.FUNCTION && s.component().name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(name + " not profiled: " + profile.toText(20)));
    }

    @Test
    @DisplayName("functions are components, recursion counted, the query body is the root")
    void functionsAndRoot() {
        ProfiledTransformation profiled = run(QUERY, 1);
        assertTrue(profiled.result().isSuccess(), profiled.result().getErrorMessage());
        assertTrue(profiled.result().getOutputContent().contains("<fib n=\"10\">83</fib>"),
                profiled.result().getOutputContent());
        XsltProfile profile = profiled.profile();
        assertEquals(1, profile.warmupRuns());

        ComponentStats fib = function(profile, "local:fib#1");
        assertTrue(fib.calls() > 100, "recursive calls: " + fib.calls());
        assertTrue(fib.selfNanos() <= fib.totalNanos());
        assertEquals(3, function(profile, "local:double#1").calls());

        assertEquals("(query body)", profile.root().label());
        assertTrue(profile.root().children().stream().anyMatch(c -> c.label().equals("local:fib#1")));
        assertFalse(profile.lines().isEmpty());
        assertTrue(profile.lines().stream().allMatch(l -> l.systemId().endsWith("p.xq")), profile.toText(20));
        assertTrue(profile.toJson().contains("local:fib#1"));
    }

    @Test
    @DisplayName("a static error is a compile failure with its line, no profile")
    void compileError() {
        ProfiledTransformation profiled = run("let $x := 1\nreturn $x +", 0);
        assertFalse(profiled.result().isSuccess());
        assertTrue(profiled.result().getErrorMessage().startsWith("XQuery compilation failed at line 2"),
                profiled.result().getErrorMessage());
        assertNull(profiled.profile());
    }

    @Test
    @DisplayName("a dynamic error fails the run, no profile")
    void dynamicError() {
        ProfiledTransformation profiled = run("error(xs:QName('local:boom'), 'boom')", 0);
        assertFalse(profiled.result().isSuccess());
        assertTrue(profiled.result().getErrorMessage().contains("boom"), profiled.result().getErrorMessage());
        assertNull(profiled.profile());
    }

    @Test
    @DisplayName("an XSLT profile keeps its root name")
    void xsltRootUnchanged() {
        assertEquals("(transformation)", new XsltProfilingTraceListener().profile(1, 0).root().label());
    }
}
