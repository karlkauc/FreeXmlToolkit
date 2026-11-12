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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The language-version arguments of the MCP tools (xpath_version, xquery_version, xslt_version, …). */
class StandardsToolsTest {

    private static final String XML = "<r><a>1</a><a>2</a></r>";

    @TempDir
    Path temp;
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
    }

    @Test
    void xpathVersion() throws Exception {
        XPathTool tool = new XPathTool();
        ToolResult v31 = tool.call(args("xml_content", XML, "expression", "head(//a)/string()"), context(root));
        assertFalse(v31.error(), v31.text());
        ToolResult v20 = tool.call(args("xml_content", XML, "expression", "head(//a)/string()",
                "xpath_version", "2.0"), context(root));
        assertTrue(v20.error(), v20.text());
        ToolResult v10 = tool.call(args("xml_content", XML, "expression", "count(//a) + '1'",
                "xpath_version", "1.0"), context(root));
        assertFalse(v10.error(), v10.text());
        assertEquals("3", v10.structured().get("result").getAsString().strip());
        assertEquals(1, v10.structured().get("item_count").getAsInt());
        ToolResult nodes10 = tool.call(args("xml_content", XML, "expression", "//a", "xpath_version", "1.0"),
                context(root));
        assertEquals(2, nodes10.structured().get("item_count").getAsInt(), nodes10.text());
        ToolResult bad10 = tool.call(args("xml_content", XML, "expression", "for $a in //a return $a",
                "xpath_version", "1.0"), context(root));
        assertTrue(bad10.error(), bad10.text());
    }

    @Test
    void unknownVersionIsMisuse() {
        XPathTool tool = new XPathTool();
        assertThrows(ToolException.class, () -> tool.call(args("xml_content", XML, "expression", "1",
                "xpath_version", "9.9"), context(root)));
    }

    @Test
    void xqueryVersion() throws Exception {
        XQueryTool tool = new XQueryTool();
        ToolResult result = tool.call(args("xml_content", XML, "query_content",
                "for $a in //a group by $k := $a return $k", "xquery_version", "1.0"), context(root));
        assertTrue(result.error(), result.text());
        assertTrue(result.text().contains("FXT-COMPAT"), result.text());
        ToolResult ok = tool.call(args("xml_content", XML, "query_content",
                "for $a in //a return string($a)", "xquery_version", "1.0"), context(root));
        assertFalse(ok.error(), ok.text());
    }

    @Test
    void xsltVersion() throws Exception {
        XsltTransformTool tool = new XsltTransformTool();
        String xslt = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="count(//a)"/>-<xsl:value-of select="system-property('xsl:version')"/></xsl:template>
                </xsl:stylesheet>
                """;
        ToolResult v10 = tool.call(args("xml_content", XML, "xslt_content", xslt, "xslt_version", "1.0"),
                context(root));
        assertFalse(v10.error(), v10.text());
        assertEquals("2-1.0", v10.structured().get("output").getAsString().strip());
        ToolResult v30 = tool.call(args("xml_content", XML, "xslt_content", xslt), context(root));
        assertEquals("2-3.0", v30.structured().get("output").getAsString().strip());
        String xslt30 = xslt.replace("count(//a)", "head(//a)");
        ToolResult v20 = tool.call(args("xml_content", XML, "xslt_content", xslt30, "xslt_version", "2.0"),
                context(root));
        assertTrue(v20.error(), v20.text());
        assertTrue(v20.text().contains("FXT-COMPAT"), v20.text());
    }

    @Test
    void xslt10ReadsOnlyInsideTheRoots() throws Exception {
        Files.writeString(temp.resolve("secret.xml"), "<secret>top</secret>");
        String xslt = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="document('%s')"/></xsl:template>
                </xsl:stylesheet>
                """.formatted(temp.resolve("secret.xml").toUri());
        ToolResult result = new XsltTransformTool().call(args("xml_content", XML, "xslt_content", xslt,
                "xslt_version", "1.0"), context(root));
        String output = result.structured().get("output").getAsString();
        assertFalse(output.contains("top"), output);
    }

    @Test
    void validateXmlVersion() throws Exception {
        ValidateTool tool = new ValidateTool();
        ToolResult result = tool.call(args("xml_content", "<?xml version=\"1.1\"?><r/>", "xml_version", "1.0"),
                context(root));
        assertFalse(result.structured().get("valid").getAsBoolean(), result.text());
        ToolResult auto = tool.call(args("xml_content", "<?xml version=\"1.1\"?><r/>"), context(root));
        assertTrue(auto.structured().get("valid").getAsBoolean(), auto.text());
    }

    @Test
    void schematronCheckBinding() throws Exception {
        String sch = """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron">
                  <pattern><rule context="r"><assert test="exists(head(a))">x</assert></rule></pattern>
                </schema>
                """;
        SchematronCheckTool tool = new SchematronCheckTool();
        ToolResult plain = tool.call(args("schematron_content", sch), context(root));
        assertFalse(plain.error(), plain.text());
        ToolResult v2 = tool.call(args("schematron_content", sch, "schematron_binding", "xslt2"), context(root));
        assertTrue(v2.error(), v2.text());
    }
}
