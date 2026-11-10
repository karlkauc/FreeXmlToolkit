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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

class ValidateToolTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="n" type="xs:int" maxOccurs="unbounded"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    private static final String SCHEMATRON = """
            <schema xmlns="http://purl.oclc.org/dsdl/schematron" queryBinding="xslt2">
              <pattern><rule context="n">
                <report test=". = '3'" role="warning">three is suspicious</report>
              </rule></pattern>
            </schema>
            """;

    @TempDir
    Path temp;

    private Path root;
    private final ValidateTool tool = new ValidateTool();

    @BeforeEach
    void setUp() throws IOException {
        root = Files.createDirectories(temp.resolve("root"));
        Files.writeString(root.resolve("s.xsd"), XSD);
        Files.writeString(root.resolve("r.sch"), SCHEMATRON);
        Files.writeString(root.resolve("valid.xml"), "<root><n>1</n><n>3</n></root>");
    }

    @BeforeAll
    static void services() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    private ToolResult call(JsonObject args) throws ToolException {
        return tool.call(args, context(root));
    }

    @Test
    void validInlineDocument() throws Exception {
        ToolResult result = call(args("xml_content", "<root><n>1</n></root>", "xsd_path", "s.xsd"));
        assertFalse(result.error(), result.text());
        assertTrue(result.structured().get("valid").getAsBoolean());
        assertFalse(result.structured().get("not_run").getAsBoolean());
        assertEquals(0, result.structured().get("total").getAsInt());
    }

    @Test
    void invalidDocumentFromAPathHasProblemsWithLines() throws Exception {
        Files.writeString(root.resolve("bad.xml"), "<root>\n  <n>x</n>\n</root>");
        ToolResult result = call(args("xml_path", "bad.xml", "xsd_path", "s.xsd"));
        assertTrue(result.error());
        JsonObject s = result.structured();
        assertFalse(s.get("valid").getAsBoolean());
        assertTrue(s.get("error_count").getAsInt() >= 1);
        JsonObject first = s.getAsJsonArray("problems").get(0).getAsJsonObject();
        assertEquals("XSD", first.get("source").getAsString());
        assertEquals("error", first.get("severity").getAsString());
        assertEquals(2, first.get("line").getAsInt());
        assertTrue(result.text().contains("2:"), result.text());
    }

    @Test
    void schematronWarningIsReported() throws Exception {
        ToolResult result = call(args("xml_path", "valid.xml", "xsd_path", "s.xsd", "schematron_path", "r.sch"));
        JsonObject s = result.structured();
        assertTrue(s.get("valid").getAsBoolean(), result.text());
        assertEquals(1, s.get("warning_count").getAsInt(), result.text());
        JsonObject warning = s.getAsJsonArray("problems").get(0).getAsJsonObject();
        assertEquals("Schematron", warning.get("source").getAsString());
        assertEquals("warning", warning.get("severity").getAsString());
        assertTrue(warning.get("message").getAsString().contains("three is suspicious"));
    }

    @Test
    void malformedXmlIsInvalid() throws Exception {
        ToolResult result = call(args("xml_content", "<root><n>1</root>"));
        assertTrue(result.error());
        assertFalse(result.structured().get("valid").getAsBoolean());
        assertTrue(result.structured().get("error_count").getAsInt() >= 1);
    }

    @Test
    void problemsAreCappedButCounted() throws Exception {
        String xml = "<root><n>a</n><n>b</n><n>c</n><n>d</n><n>e</n></root>";
        ToolResult result = tool.call(args("xml_content", xml, "xsd_path", "s.xsd"), context(root, 64 * 1024, 2));
        JsonObject s = result.structured();
        // five invalid values; Xerces reports two problems for each
        assertEquals(s.get("error_count").getAsInt(), s.get("total").getAsInt(), result.text());
        assertTrue(s.get("total").getAsInt() >= 5, result.text());
        assertEquals(2, s.getAsJsonArray("problems").size());
        assertTrue(s.get("truncated").getAsBoolean());
    }

    @Test
    void declaredSchemaIsUsedByDefaultAndCanBeSwitchedOff() throws Exception {
        String xml = "<root xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:noNamespaceSchemaLocation=\"s.xsd\"><n>x</n></root>";
        Files.writeString(root.resolve("declared.xml"), xml);
        ToolResult declared = call(args("xml_path", "declared.xml"));
        assertFalse(declared.structured().get("valid").getAsBoolean(), declared.text());
        assertTrue(declared.structured().get("schema").getAsString().endsWith("s.xsd"));

        ToolResult wellFormedOnly = call(args("xml_path", "declared.xml", "declared_schema", false));
        assertTrue(wellFormedOnly.structured().get("valid").getAsBoolean(), wellFormedOnly.text());
        assertTrue(wellFormedOnly.structured().get("schema").isJsonNull());
    }

    @Test
    void schemaThatDoesNotLoadMeansNotRun() throws Exception {
        Files.writeString(root.resolve("broken.xsd"), "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element/>");
        ToolResult result = call(args("xml_content", "<root/>", "xsd_path", "broken.xsd"));
        assertTrue(result.error());
        assertTrue(result.structured().get("not_run").getAsBoolean(), result.text());
        assertFalse(result.structured().get("valid").getAsBoolean());
    }

    @Test
    void pairMisuseIsAToolException() {
        assertThrows(ToolException.class, () -> call(args("xml_path", "valid.xml", "xml_content", "<root/>")));
        assertThrows(ToolException.class, () -> call(args("xsd_path", "s.xsd")));
        assertThrows(ToolException.class, () -> call(args("xml_content", "<root/>", "schema", "s.xsd")));
    }

    @Test
    void pathOutsideTheRootIsAnErrorResultNamingTheRoots() throws Exception {
        Path outside = Files.writeString(temp.resolve("outside.xml"), "<root/>");
        ToolResult result = new ToolRegistry(List.of(tool)).call("validate",
                args("xml_path", outside.toString()), context(root));
        assertTrue(result.error());
        assertTrue(result.text().contains(root.toRealPath().toString()), result.text());
    }

    @Test
    void schemaDescribesTheArguments() {
        JsonObject properties = tool.inputSchema().getAsJsonObject("properties");
        for (String name : List.of("xml_path", "xml_content", "xsd_path", "schematron_path", "declared_schema")) {
            assertTrue(properties.has(name), name);
        }
        assertTrue(tool.readOnly());
        assertEquals("validate", tool.name());
        JsonArray required = tool.inputSchema().getAsJsonArray("required");
        assertTrue(required == null || required.isEmpty());
    }
}
