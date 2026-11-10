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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

class XsltTransformToolTest {

    private static final String XML = "<items><item>a</item><item>b</item></items>";

    private static final String XSLT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:param name="sep" select="','"/>
              <xsl:output method="text"/>
              <xsl:template match="/">
                <xsl:message>converting</xsl:message>
                <xsl:value-of select="//item" separator="{$sep}"/>
                <xsl:result-document href="count.txt" method="text"><xsl:value-of select="count(//item)"/></xsl:result-document>
              </xsl:template>
            </xsl:stylesheet>""";

    @TempDir
    Path temp;

    private Path root;
    private final XsltTransformTool tool = new XsltTransformTool();

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
        Files.writeString(root.resolve("in.xml"), XML);
        Files.writeString(root.resolve("t.xsl"), XSLT);
    }

    @Test
    void transformsInlineContentWithParametersAndDetectedOutput() throws Exception {
        ToolResult result = tool.call(args("xml_content", XML, "xslt_content", XSLT,
                "parameters", json("{\"sep\": \";\"}")), context(root));
        assertFalse(result.error(), result.text());
        JsonObject s = result.structured();
        assertTrue(s.get("ok").getAsBoolean());
        assertEquals("a;b", s.get("output").getAsString().strip());
        assertEquals("text", s.get("output_method").getAsString());
        assertFalse(s.get("timed_out").getAsBoolean());
        JsonObject documents = s.getAsJsonObject("result_documents");
        assertEquals(1, documents.size(), documents.toString());
        assertEquals("2", documents.entrySet().iterator().next().getValue().getAsString().strip());
        assertTrue(s.getAsJsonArray("messages").get(0).getAsJsonObject().get("text").getAsString()
                .contains("converting"));
    }

    @Test
    void transformsFilesWithoutWritingAnything() throws Exception {
        List<Path> before = files();
        ToolResult result = tool.call(args("xml_path", "in.xml", "xslt_path", "t.xsl"), context(root));
        assertFalse(result.error(), result.text());
        assertEquals(before, files(), "the tool wrote a file");
        String uri = result.structured().getAsJsonObject("result_documents").keySet().iterator().next();
        assertTrue(uri.endsWith("count.txt"), uri);
    }

    @Test
    void compileErrorHasCodePhaseAndLine() throws Exception {
        String broken = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <xsl:value-of select="//item["/>
                  </xsl:template>
                </xsl:stylesheet>""";
        ToolResult result = tool.call(args("xml_content", XML, "xslt_content", broken), context(root));
        assertTrue(result.error());
        JsonObject error = result.structured().getAsJsonObject("error");
        assertEquals("compile", error.get("phase").getAsString());
        assertTrue(error.get("code").getAsString().startsWith("X"), error.toString());
        assertEquals(3, error.get("line").getAsInt(), error.toString());
        assertTrue(result.text().contains("line 3"), result.text());
    }

    @Test
    void outputIsTruncated() throws Exception {
        String big = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="string-join((1 to 500) ! 'abcdefghij')"/></xsl:template>
                </xsl:stylesheet>""";
        ToolResult result = tool.call(args("xml_content", XML, "xslt_content", big), context(root, 100, 200));
        assertTrue(result.structured().get("truncated").getAsBoolean());
        assertTrue(result.structured().get("output").getAsString().contains("characters in total"));
    }

    @Test
    void explicitOutputMethodAndBadValue() throws Exception {
        ToolResult result = tool.call(args("xml_content", XML, "xslt_content", XSLT, "output", "xml"), context(root));
        assertEquals("xml", result.structured().get("output_method").getAsString());
        assertThrows(ToolException.class, () -> tool.call(args("xml_content", XML, "xslt_content", XSLT,
                "output", "pdf"), context(root)));
    }

    @Test
    void bothInputsAreRequired() {
        assertThrows(ToolException.class, () -> tool.call(args("xml_content", XML), context(root)));
        assertThrows(ToolException.class, () -> tool.call(args("xslt_content", XSLT), context(root)));
    }

    private List<Path> files() throws IOException {
        try (Stream<Path> files = Files.walk(temp)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
