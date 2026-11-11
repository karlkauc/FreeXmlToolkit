package org.fxt.freexmltoolkit.service.ai.tool;

import static org.fxt.freexmltoolkit.service.ai.tool.SchemaToolsTestSchemas.MAIN;
import static org.fxt.freexmltoolkit.service.ai.tool.SchemaToolsTestSchemas.OTHER;
import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.args;
import static org.fxt.freexmltoolkit.service.ai.tool.ToolTestSupport.context;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class SchemaToolsTest {

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
        Files.writeString(root.resolve("main.xsd"), MAIN);
        Files.writeString(root.resolve("other.xsd"), OTHER);
    }

    private static List<String> names(JsonArray array) {
        List<String> names = new ArrayList<>();
        for (JsonElement e : array) {
            names.add(e.isJsonObject() ? e.getAsJsonObject().get("name").getAsString() : e.getAsString());
        }
        return names;
    }

    private static JsonObject child(JsonObject node, String name) {
        for (JsonElement e : node.getAsJsonArray("children")) {
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) {
                return e.getAsJsonObject();
            }
        }
        throw new AssertionError("no child " + name + " in " + node);
    }

    // ---- schema_outline ----

    @Test
    void outlineListsGlobalsAndImports() throws Exception {
        ToolResult result = new SchemaOutlineTool().call(args("xsd_path", "main.xsd"), context(root));
        assertFalse(result.error(), result.text());
        JsonObject s = result.structured();
        assertEquals("urn:m", s.get("target_namespace").getAsString());
        assertEquals(List.of("Order"), names(s.getAsJsonArray("global_elements")));
        assertTrue(names(s.getAsJsonArray("named_types")).containsAll(List.of("IdType", "StatusType", "FolderType")));
        JsonObject anImport = s.getAsJsonArray("imports").get(0).getAsJsonObject();
        assertEquals("urn:o", anImport.get("namespace").getAsString());
        assertEquals("other.xsd", anImport.get("location").getAsString());
    }

    @Test
    void outlineTreeIsCutAtTheDepth() throws Exception {
        ToolResult result = new SchemaOutlineTool().call(args("xsd_path", "main.xsd", "element", "Order", "depth", 1),
                context(root));
        JsonObject tree = result.structured().getAsJsonObject("tree");
        assertEquals("Order", tree.get("name").getAsString());
        assertEquals(List.of("Id", "Status", "Note", "Folder", "Cash", "Card"), names(tree.getAsJsonArray("children")));
        JsonObject folder = child(tree, "Folder");
        assertTrue(folder.get("more").getAsBoolean());
        assertFalse(folder.has("children") && !folder.getAsJsonArray("children").isEmpty());
        JsonObject note = child(tree, "Note");
        assertEquals(0, note.get("min").getAsInt());
        assertEquals("unbounded", note.get("max").getAsString());
        assertTrue(child(tree, "Cash").get("choice").getAsBoolean());
        assertFalse(child(tree, "Id").has("choice"));
    }

    @Test
    void outlineOfARecursiveTypeTerminates() throws Exception {
        ToolResult result = new SchemaOutlineTool().call(args("xsd_path", "main.xsd", "element", "Order", "depth", 6),
                context(root));
        JsonObject folder = child(result.structured().getAsJsonObject("tree"), "Folder");
        JsonObject inner = child(folder, "Folder");
        assertTrue(inner.get("recursive").getAsBoolean(), inner.toString());
    }

    @Test
    void outlineDepthIsBounded() {
        assertThrows(ToolException.class, () -> new SchemaOutlineTool().call(
                args("xsd_path", "main.xsd", "element", "Order", "depth", 7), context(root)));
    }

    @Test
    void outlineOfAnUnknownElementIsAnErrorNamingTheGlobals() throws Exception {
        ToolResult result = new SchemaOutlineTool().call(args("xsd_path", "main.xsd", "element", "Nope"), context(root));
        assertTrue(result.error());
        assertTrue(result.text().contains("Order"), result.text());
    }

    @Test
    void aSchemaThatDoesNotParseIsAnError() throws Exception {
        Files.writeString(root.resolve("broken.xsd"), "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">");
        ToolResult result = new SchemaOutlineTool().call(args("xsd_path", "broken.xsd"), context(root));
        assertTrue(result.error(), result.text());
    }

    // ---- schema_describe ----

    private JsonObject describe(String path) throws Exception {
        ToolResult result = new SchemaDescribeTool().call(args("xsd_path", "main.xsd", "instance_path", path), context(root));
        return result.structured();
    }

    @Test
    void describeAnEnumeratedElement() throws Exception {
        JsonObject s = describe("/Order/Status");
        assertTrue(s.get("found").getAsBoolean());
        assertEquals("Status", s.get("name").getAsString());
        assertEquals("StatusType", s.get("type").getAsString());
        assertEquals("token", s.get("builtin_base").getAsString());
        assertEquals(0, s.get("min").getAsInt());
        assertEquals(1, s.get("max").getAsInt());
        assertEquals(List.of("OPEN", "CLOSED"), names(s.getAsJsonArray("enumeration")));
    }

    @Test
    void describeFacetsOfANamedSimpleType() throws Exception {
        JsonObject facets = describe("/m:Order[1]/m:Id").getAsJsonObject("facets");
        assertEquals("10", facets.get("maxLength").getAsString());
        assertEquals("[A-Z]+", facets.get("pattern").getAsString());
    }

    @Test
    void describeTheRootWithDocumentationChildrenAndAttributes() throws Exception {
        JsonObject s = describe("/Order");
        JsonArray docs = s.getAsJsonArray("documentation");
        assertEquals(2, docs.size());
        assertEquals("en", docs.get(0).getAsJsonObject().get("lang").getAsString());
        assertEquals("Eine Bestellung", docs.get(1).getAsJsonObject().get("text").getAsString());
        assertEquals(List.of("Id", "Status", "Note", "Folder", "Cash", "Card"), names(s.getAsJsonArray("children")));
        JsonObject version = s.getAsJsonArray("attributes").get(0).getAsJsonObject();
        assertEquals("version", version.get("name").getAsString());
        assertEquals("required", version.get("use").getAsString());
    }

    @Test
    void describeAnElementFromAnImportedSchema() throws Exception {
        JsonObject s = describe("/Order/Note");
        assertTrue(s.get("found").getAsBoolean(), s.toString());
        assertTrue(s.get("declared_in").getAsString().endsWith("other.xsd"), s.toString());
        assertEquals("unbounded", s.get("max").getAsString());
        assertEquals("A free text note", s.getAsJsonArray("documentation").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    void describeAnAttribute() throws Exception {
        ToolResult result = new SchemaDescribeTool().call(args("xsd_path", "main.xsd", "instance_path", "/Order",
                "attribute", "version"), context(root));
        JsonObject s = result.structured();
        assertTrue(s.get("found").getAsBoolean());
        assertEquals("xs:string", s.get("type").getAsString());
        assertEquals("required", s.get("use").getAsString());
    }

    @Test
    void anUnknownPathNamesTheNearestKnownAncestor() throws Exception {
        ToolResult result = new SchemaDescribeTool().call(args("xsd_path", "main.xsd", "instance_path",
                "/Order/Folder/Nope/Deeper"), context(root));
        assertFalse(result.structured().get("found").getAsBoolean());
        assertEquals("/Order/Folder", result.structured().get("nearest_known_path").getAsString());
        assertTrue(result.text().contains("/Order/Folder"), result.text());
        assertTrue(result.text().contains("Name"), "lists the children that do exist: " + result.text());
    }

    // ---- sample_xml ----

    @Test
    void sampleXmlIsReproducibleWithASeed() throws Exception {
        SampleXmlTool tool = new SampleXmlTool();
        ToolResult first = tool.call(args("xsd_path", "main.xsd", "seed", 7), context(root));
        ToolResult second = tool.call(args("xsd_path", "main.xsd", "seed", 7), context(root));
        assertFalse(first.error(), first.text());
        String xml = first.structured().get("xml").getAsString();
        assertTrue(xml.contains("Order"), xml);
        assertEquals(xml, second.structured().get("xml").getAsString());
    }

    @Test
    void sampleXmlOfAMissingSchemaIsMisuse() {
        assertThrows(ToolException.class, () -> new SampleXmlTool().call(args("xsd_path", "missing.xsd"), context(root)));
    }
}
