package org.fxt.freexmltoolkit.service.ai.tool;

import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.args;
import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class MoreToolsTest {

    private static final String GREET_XSL = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                            xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xsl:function name="f:greet" as="xs:string">
                <xsl:param name="name" as="xs:string"/>
                <xsl:sequence select="'Hello ' || $name"/>
              </xsl:function>
              <xsl:template match="person">
                <p><xsl:value-of select="f:greet(@name)"/></p>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String XSPEC = """
            <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="greet.xsl">
              <x:scenario label="greet">
                <x:call function="f:greet"><x:param select="'Ada'"/></x:call>
                <x:expect label="says hello" select="'Hello Ada'"/>
                <x:expect label="is wrong on purpose" select="'Goodbye Ada'"/>
              </x:scenario>
              <x:scenario label="template">
                <x:context><person name="Bob"/></x:context>
                <x:expect label="a paragraph"><p>Hello Bob</p></x:expect>
              </x:scenario>
            </x:description>
            """;

    @TempDir
    Path temp;

    private Path root;

    @BeforeAll
    static void services() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
        Files.writeString(root.resolve("greet.xsl"), GREET_XSL);
        Files.writeString(root.resolve("greet.xspec"), XSPEC);
        Files.writeString(temp.resolve("secret.xml"), "<secret>top</secret>");
    }

    private List<Path> files() throws IOException {
        try (Stream<Path> files = Files.walk(temp)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    // ---- document_outline ----

    @Test
    void documentOutlineOfAFile() throws Exception {
        Files.writeString(root.resolve("d.xml"), "<a xmlns:p=\"urn:p\"><p:b x=\"1\">v</p:b><p:b/></a>");
        ToolResult result = new DocumentOutlineTool().call(args("xml_path", "d.xml"), context(root));
        assertFalse(result.error(), result.text());
        JsonObject s = result.structured();
        assertEquals("a", s.get("root").getAsString());
        assertEquals("urn:p", s.getAsJsonObject("namespaces").get("p").getAsString());
        JsonObject b = s.getAsJsonArray("paths").get(1).getAsJsonObject();
        assertEquals("/a/p:b", b.get("path").getAsString());
        assertEquals(2, b.get("count").getAsInt());
        assertEquals("v", b.get("sample").getAsString());
        assertTrue(s.get("size_bytes").getAsLong() > 0);
    }

    @Test
    void documentOutlineOfMalformedXmlIsAnInputError() throws Exception {
        ToolResult result = new DocumentOutlineTool().call(args("xml_content", "<a><b></a>"), context(root));
        assertTrue(result.error());
        assertEquals("input", result.structured().getAsJsonObject("error").get("phase").getAsString());
    }

    // ---- xspec_run ----

    @Test
    void xspecRunsWithoutWritingFiles() throws Exception {
        List<Path> before = files();
        ToolResult result = new XSpecRunTool().call(args("xspec_path", "greet.xspec"), context(root));
        assertEquals(before, files(), "the tested stylesheet wrote a file");
        JsonObject s = result.structured();
        assertTrue(s.get("error").isJsonNull(), result.text());
        assertEquals("xslt", s.get("kind").getAsString());
        assertFalse(s.get("passed").getAsBoolean());
        assertEquals("3 tests: 2 passed, 1 failed, 0 pending", s.get("summary").getAsString());
        assertTrue(result.error(), "a failing test is a failed result");
        JsonObject wrong = s.getAsJsonArray("scenarios").get(0).getAsJsonObject()
                .getAsJsonArray("tests").get(1).getAsJsonObject();
        assertEquals("failed", wrong.get("status").getAsString());
        assertTrue(wrong.get("actual").getAsString().contains("Hello Ada"), wrong.toString());
        assertTrue(result.text().contains("is wrong on purpose"), result.text());
    }

    @Test
    void xspecInlineContentUsesBasePath() throws Exception {
        String passing = XSPEC.replace("<x:expect label=\"is wrong on purpose\" select=\"'Goodbye Ada'\"/>", "");
        ToolResult result = new XSpecRunTool().call(args("xspec_content", passing, "base_path", "greet.xspec"),
                context(root));
        assertFalse(result.error(), result.text());
        assertTrue(result.structured().get("passed").getAsBoolean());
    }

    @Test
    void xspecCannotReadOutsideTheRoots() throws Exception {
        String xspec = """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="greet.xsl">
                  <x:scenario label="reads a secret">
                    <x:call function="f:greet"><x:param select="string(doc('%s'))"/></x:call>
                    <x:expect label="gets it" select="'Hello top'"/>
                  </x:scenario>
                </x:description>""".formatted(temp.resolve("secret.xml").toUri());
        ToolResult result = new XSpecRunTool().call(args("xspec_content", xspec, "base_path", "greet.xspec"),
                context(root));
        assertTrue(result.error(), result.text());
        assertFalse(result.structured().get("passed").getAsBoolean(), result.text());
    }

    @Test
    void xspecOfAnEndlessStylesheetTimesOut() throws Exception {
        Files.writeString(root.resolve("loop.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xsl:function name="f:loop" as="xs:integer">
                    <xsl:iterate select="1 to 2000000000">
                      <xsl:param name="n" select="0" as="xs:integer"/>
                      <xsl:on-completion select="$n"/>
                      <xsl:next-iteration><xsl:with-param name="n" select="$n + 1"/></xsl:next-iteration>
                    </xsl:iterate>
                  </xsl:function>
                </xsl:stylesheet>""");
        String xspec = """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="loop.xsl">
                  <x:scenario label="loops"><x:call function="f:loop"/><x:expect label="ends" select="0"/></x:scenario>
                </x:description>""";
        ToolContext shortContext = ToolContext.of(new WorkspaceAccess(List.of(root)),
                new ToolLimits(java.time.Duration.ofSeconds(4), 64 * 1024, 1_000_000, 50));
        long start = System.nanoTime();
        ToolResult result = new XSpecRunTool().call(args("xspec_content", xspec, "base_path", "loop.xspec"), shortContext);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertTrue(result.structured().get("timed_out").getAsBoolean(), result.text());
        assertTrue(millis < 20_000, millis + " ms");
    }

    @Test
    void xspecThatIsNoXSpecIsAnError() throws Exception {
        ToolResult result = new XSpecRunTool().call(args("xspec_content", "<a/>"), context(root));
        assertTrue(result.error());
        assertFalse(result.structured().get("error").isJsonNull());
    }

    // ---- schematron_check ----

    @Test
    void schematronCheckFindsABrokenTest() throws Exception {
        String sch = """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern><rule context="a"><assert test="count(b) &gt;">needs b</assert></rule></pattern>
                </schema>""";
        ToolResult result = new SchematronCheckTool().call(args("schematron_content", sch), context(root));
        assertTrue(result.error(), result.text());
        JsonArray issues = result.structured().getAsJsonArray("issues");
        assertTrue(issues.size() >= 1);
        assertTrue(issues.get(0).getAsJsonObject().has("severity"));
        assertFalse(result.structured().get("ok").getAsBoolean());
    }

    @Test
    void schematronCheckOfAGoodSchematron() throws Exception {
        String sch = """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <pattern id="p"><rule context="a"><assert test="b">An a needs a b.</assert></rule></pattern>
                </schema>""";
        ToolResult result = new SchematronCheckTool().call(args("schematron_content", sch), context(root));
        assertTrue(result.structured().get("ok").getAsBoolean(), result.text());
    }

    // ---- compare ----

    @Test
    void compareEquivalentAndDifferent() throws Exception {
        ToolResult same = new CompareTool().call(args("left_content", "<a><b>1</b></a>",
                "right_content", "<a>\n  <b>1</b>\n</a>"), context(root));
        assertFalse(same.error(), same.text());
        assertTrue(same.structured().get("equivalent").getAsBoolean());

        ToolResult different = new CompareTool().call(args("left_content", "<a><b>1</b><c/></a>",
                "right_content", "<a><b>2</b></a>"), context(root));
        assertTrue(different.error());
        JsonObject s = different.structured();
        assertFalse(s.get("equivalent").getAsBoolean());
        assertEquals(2, s.get("total").getAsInt(), s.toString());
        JsonObject first = s.getAsJsonArray("differences").get(0).getAsJsonObject();
        assertTrue(first.has("left_xpath") && first.has("description"), first.toString());
    }

    @Test
    void compareOptions() throws Exception {
        ToolResult tolerant = new CompareTool().call(args("left_content", "<a><n>1.00</n><t>x</t></a>",
                "right_content", "<a><n>1.004</n><t>y</t></a>", "numeric_tolerance", 0.01,
                "ignore_xpaths", ToolTestSupport.json("{\"x\": [\"/a/t\"]}").get("x")), context(root));
        assertTrue(tolerant.structured().get("equivalent").getAsBoolean(), tolerant.text());
        ToolResult unordered = new CompareTool().call(args("left_content", "<a><b/><c/></a>",
                "right_content", "<a><c/><b/></a>", "ignore_order", true), context(root));
        assertTrue(unordered.structured().get("equivalent").getAsBoolean(), unordered.text());
    }

    @Test
    void compareRejectsABadIgnoreXPath() {
        assertThrows(ToolException.class, () -> new CompareTool().call(args("left_content", "<a/>",
                "right_content", "<a/>", "ignore_xpaths", ToolTestSupport.json("{\"x\": [\"/a[\"]}").get("x")),
                context(root)));
    }

    @Test
    void compareOfMalformedInputIsAnError() throws Exception {
        ToolResult result = new CompareTool().call(args("left_content", "<a>", "right_content", "<a/>"), context(root));
        assertTrue(result.error());
        assertFalse(result.structured().get("left_error").isJsonNull());
    }
}
