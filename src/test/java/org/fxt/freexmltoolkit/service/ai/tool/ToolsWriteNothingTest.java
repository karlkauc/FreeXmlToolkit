package org.fxt.freexmltoolkit.service.ai.tool;

import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.args;
import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;

/**
 * Definition of done of Phase 1: no tool call creates or changes a file in the workspace. Every standard tool is
 * called with inputs that make it do its work (including a stylesheet with {@code xsl:result-document}); the
 * folder's files, sizes and modification times are compared before and after.
 */
class ToolsWriteNothingTest {

    @TempDir
    Path root;

    @BeforeAll
    static void services() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    private Map<Path, String> snapshot() throws IOException {
        Map<Path, String> state = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.toList()) {
                state.put(root.relativize(file), Files.isDirectory(file) ? "dir"
                        : Files.size(file) + "@" + Files.getLastModifiedTime(file).toMillis());
            }
        }
        return state;
    }

    @Test
    void noToolWritesAFile() throws Exception {
        Files.writeString(root.resolve("main.xsd"), SchemaToolsTestSchemas.MAIN);
        Files.writeString(root.resolve("other.xsd"), SchemaToolsTestSchemas.OTHER);
        Files.writeString(root.resolve("doc.xml"), "<Order xmlns=\"urn:m\" version=\"1\"><Id>AB</Id><Cash>1</Cash></Order>");
        Files.writeString(root.resolve("t.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/">
                    <xsl:result-document href="written-by-a-tool.xml"><x/></xsl:result-document>
                    <xsl:result-document href="sub/deeper.xml"><x/></xsl:result-document>
                    <out/>
                  </xsl:template>
                </xsl:stylesheet>""");
        Files.writeString(root.resolve("f.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                                xmlns:f="urn:f" xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xsl:function name="f:id" as="xs:string"><xsl:param name="s" as="xs:string"/><xsl:sequence select="$s"/></xsl:function>
                </xsl:stylesheet>""");
        Files.writeString(root.resolve("t.xspec"), """
                <x:description xmlns:x="http://www.jenitennison.com/xslt/xspec" xmlns:f="urn:f" stylesheet="f.xsl">
                  <x:scenario label="id"><x:call function="f:id"><x:param select="'a'"/></x:call>
                    <x:expect label="same" select="'a'"/></x:scenario>
                </x:description>""");
        Files.writeString(root.resolve("r.sch"), """
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
                  <ns prefix="m" uri="urn:m"/>
                  <pattern><rule context="m:Order"><assert test="m:Id">needs an id</assert></rule></pattern>
                </schema>""");
        Files.writeString(root.resolve("q.xq"), "declare namespace m = 'urn:m'; count(//m:Id)");

        Map<String, JsonObject> calls = Map.ofEntries(
                Map.entry("validate", args("xml_path", "doc.xml", "xsd_path", "main.xsd", "schematron_path", "r.sch")),
                Map.entry("xpath", args("xml_path", "doc.xml", "expression", "//*:Id")),
                Map.entry("xquery", args("xml_path", "doc.xml", "query_path", "q.xq")),
                Map.entry("xslt_transform", args("xml_path", "doc.xml", "xslt_path", "t.xsl")),
                Map.entry("schema_outline", args("xsd_path", "main.xsd", "element", "Order")),
                Map.entry("schema_describe", args("xsd_path", "main.xsd", "instance_path", "/Order/Id")),
                Map.entry("sample_xml", args("xsd_path", "main.xsd", "seed", 1)),
                Map.entry("document_outline", args("xml_path", "doc.xml")),
                Map.entry("xspec_run", args("xspec_path", "t.xspec")),
                Map.entry("schematron_check", args("schematron_path", "r.sch")),
                Map.entry("compare", args("left_path", "doc.xml", "right_path", "doc.xml")));

        ToolRegistry registry = ToolRegistry.standard();
        Set<String> covered = new HashSet<>(calls.keySet());
        assertEquals(registry.tools().stream().map(AiTool::name).collect(java.util.stream.Collectors.toSet()), covered,
                "every standard tool must be exercised here");

        Map<Path, String> before = snapshot();
        for (String name : registry.tools().stream().map(AiTool::name).toList()) {
            ToolResult result = registry.call(name, calls.get(name), context(root));
            assertTrue(!result.error(), name + " failed: " + result.text());
        }
        assertEquals(before, snapshot(), "a tool created or changed a file");
    }
}
