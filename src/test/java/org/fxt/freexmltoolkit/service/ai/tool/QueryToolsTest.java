package org.fxt.freexmltoolkit.service.ai.tool;

import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.args;
import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.context;
import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

class QueryToolsTest {

    private static final String XML = """
            <fund xmlns:x="urn:x" id="F1">
              <name>Alpha</name>
              <price>10.5</price>
              <price>11</price>
              <x:note>hello</x:note>
            </fund>""";

    @TempDir
    Path temp;

    private Path root;
    private final XPathTool xpath = new XPathTool();
    private final XQueryTool xquery = new XQueryTool();

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
        Files.writeString(root.resolve("fund.xml"), XML);
        Files.writeString(temp.resolve("secret.xml"), "<secret>top</secret>");
    }

    // ---- xpath ----

    @Test
    void xpathAtomicResult() throws Exception {
        ToolResult result = xpath.call(args("xml_content", XML, "expression", "sum(//price)"), context(root));
        assertFalse(result.error(), result.text());
        JsonObject s = result.structured();
        assertTrue(s.get("ok").getAsBoolean());
        assertEquals("21.5", s.get("result").getAsString().strip());
        assertEquals(1, s.get("item_count").getAsInt());
        assertFalse(s.get("truncated").getAsBoolean());
    }

    @Test
    void xpathNodesAttributesAndDocumentPrefixes() throws Exception {
        ToolResult result = xpath.call(args("xml_path", "fund.xml", "expression", "(/fund/@id, //name, //x:note/string())"),
                context(root));
        String text = result.structured().get("result").getAsString();
        assertEquals(3, result.structured().get("item_count").getAsInt(), text);
        assertTrue(text.contains("id=\"F1\""), text);
        assertTrue(text.contains(">Alpha</name>"), text);
        assertTrue(text.contains("hello"), text);
    }

    @Test
    void xpathWithDeclaredNamespacesAndDefaultNamespace() throws Exception {
        String xml = "<a xmlns=\"urn:d\"><b>1</b></a>";
        ToolResult result = xpath.call(args("xml_content", xml, "expression", "count(//b) + count(//q:b)",
                "namespaces", json("{\"\": \"urn:d\", \"q\": \"urn:d\"}")), context(root));
        assertEquals("2", result.structured().get("result").getAsString().strip(), result.text());
    }

    @Test
    void xpathSyntaxErrorIsAResultWithCode() throws Exception {
        ToolResult result = xpath.call(args("xml_content", XML, "expression", "//price["), context(root));
        assertTrue(result.error());
        JsonObject error = result.structured().getAsJsonObject("error");
        assertTrue(error.get("code").getAsString().startsWith("XP"), error.toString());
        assertEquals("compile", error.get("phase").getAsString());
        assertFalse(result.structured().get("ok").getAsBoolean());
    }

    @Test
    void xpathOnMalformedXmlIsAnInputError() throws Exception {
        ToolResult result = xpath.call(args("xml_content", "<a><b></a>", "expression", "1"), context(root));
        assertTrue(result.error());
        assertEquals("input", result.structured().getAsJsonObject("error").get("phase").getAsString());
        assertTrue(result.structured().getAsJsonObject("error").get("line").getAsInt() >= 1);
    }

    @Test
    void xpathCannotReadOutsideTheRoots() throws Exception {
        ToolResult result = xpath.call(args("xml_content", XML, "expression",
                "doc('" + temp.resolve("secret.xml").toUri() + "')/secret/string()"), context(root));
        assertTrue(result.error(), result.text());
        assertFalse(result.text().contains("top"), result.text());
    }

    @Test
    void xpathOutputIsTruncated() throws Exception {
        ToolResult result = xpath.call(args("xml_content", XML, "expression", "string-join((1 to 1000) ! 'abcdefghij')"),
                context(root, 100, 200));
        JsonObject s = result.structured();
        assertTrue(s.get("truncated").getAsBoolean());
        assertTrue(s.get("result").getAsString().contains("characters in total"), s.get("result").getAsString());
    }

    @Test
    void xpathNeedsAnExpression() {
        assertThrows(ToolException.class, () -> xpath.call(args("xml_content", XML), context(root)));
        assertThrows(ToolException.class, () -> xpath.call(args("expression", "1"), context(root)));
    }

    // ---- xquery ----

    @Test
    void xqueryAgainstADocumentWithVariables() throws Exception {
        ToolResult result = xquery.call(args("xml_path", "fund.xml",
                "query_content", "declare variable $factor external; <total>{sum(//price) * xs:double($factor)}</total>",
                "variables", json("{\"factor\": \"2\"}")), context(root));
        assertFalse(result.error(), result.text());
        assertTrue(result.structured().get("result").getAsString().contains("<total>43</total>"),
                result.structured().get("result").getAsString());
    }

    @Test
    void xqueryWithoutADocument() throws Exception {
        Files.writeString(root.resolve("q.xq"), "for $i in 1 to 3 return $i * 2");
        ToolResult result = xquery.call(args("query_path", "q.xq"), context(root));
        assertFalse(result.error(), result.text());
        assertEquals(3, result.structured().get("item_count").getAsInt());
        assertEquals("2 4 6", result.structured().get("result").getAsString().strip());
    }

    @Test
    void xquerySyntaxErrorHasCodeAndLine() throws Exception {
        ToolResult result = xquery.call(args("query_content", "let $a := 1\nreturn $a +"), context(root));
        assertTrue(result.error());
        JsonObject error = result.structured().getAsJsonObject("error");
        assertEquals("XPST0003", error.get("code").getAsString());
        assertEquals("compile", error.get("phase").getAsString());
        assertEquals(2, error.get("line").getAsInt(), error.toString());
    }

    /** Saxon reports an unresolved variable without a location; the code still identifies it. */
    @Test
    void xqueryUnresolvedVariableHasTheCode() throws Exception {
        ToolResult result = xquery.call(args("query_content", "let $a := 1\nreturn $b"), context(root));
        assertTrue(result.error());
        JsonObject error = result.structured().getAsJsonObject("error");
        assertEquals("XPST0008", error.get("code").getAsString());
        assertEquals("compile", error.get("phase").getAsString());
        assertTrue(error.get("message").getAsString().contains("$b"), error.toString());
    }

    @Test
    void xqueryDynamicErrorIsARuntimeError() throws Exception {
        ToolResult result = xquery.call(args("query_content", "1 div 0"), context(root));
        assertTrue(result.error());
        JsonObject error = result.structured().getAsJsonObject("error");
        assertEquals("FOAR0001", error.get("code").getAsString());
        assertEquals("runtime", error.get("phase").getAsString());
    }

    @Test
    void xqueryMapsAreSerializedAdaptively() throws Exception {
        ToolResult result = xquery.call(args("query_content", "map { 'a': 1 }"), context(root));
        assertFalse(result.error(), result.text());
        assertTrue(result.structured().get("result").getAsString().contains("\"a\""), result.text());
    }

    @Test
    void xqueryCannotReadOutsideTheRootsOrTheEnvironment() throws Exception {
        ToolResult doc = xquery.call(args("query_content",
                "doc('" + temp.resolve("secret.xml").toUri() + "')/secret/string()"), context(root));
        assertTrue(doc.error(), doc.text());
        ToolResult env = xquery.call(args("query_content", "string(environment-variable('PATH'))"), context(root));
        assertEquals("", env.structured().get("result").getAsString().strip(), env.text());
    }

    @Test
    void xqueryLoopInAFunctionIsStoppedByTheDeadline() throws Exception {
        long start = System.nanoTime();
        ToolResult result = xquery.call(args("query_content", """
                declare function local:step($i as xs:integer) as xs:integer { $i };
                count(for $i in 1 to 2000000000, $j in 1 to 2000000000 return local:step($j))"""), shortContext());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(result.error());
        assertTrue(result.structured().get("timed_out").getAsBoolean(), result.text());
        assertTrue(millis < 10_000, millis + " ms");
    }

    /**
     * A loop inside one expression enters no traced construct, so the engine's deadline does not see it;
     * the registry gives up after the timeout and counts the worker as a runaway until it ends.
     */
    @Test
    void xqueryLoopInsideOneExpressionIsAbandonedByTheRegistry() throws Exception {
        ToolRegistry registry = new ToolRegistry(java.util.List.of(xquery));
        long start = System.nanoTime();
        ToolResult result = registry.call("xquery", args("query_content",
                "count(for $i in 1 to 40000, $j in 1 to 40000 where $i + $j lt 0 return $i)"), shortContext());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(result.error());
        assertTrue(result.structured().get("timed_out").getAsBoolean(), result.text());
        assertTrue(millis < 5_000, millis + " ms");
        assertEquals(1, registry.runawayCount());
    }

    private ToolContext shortContext() {
        return ToolContext.of(new WorkspaceAccess(java.util.List.of(root)),
                new ToolLimits(java.time.Duration.ofMillis(800), 1000, 1_000_000, 10));
    }
}
