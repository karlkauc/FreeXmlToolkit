package org.fxt.freexmltoolkit.service.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.fxt.freexmltoolkit.service.ai.tool.AiTool;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.fxt.freexmltoolkit.service.ai.tool.ToolResult;
import org.fxt.freexmltoolkit.service.ai.tool.ToolSchemas;
import org.fxt.freexmltoolkit.service.ai.tool.WorkspaceAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class McpToolServerTest {

    @TempDir
    Path root;

    @BeforeAll
    static void services() {
        org.fxt.freexmltoolkit.di.ServiceRegistry.initialize();
    }

    private ToolContext context() {
        return ToolContext.of(new WorkspaceAccess(List.of(root)), ToolLimits.DEFAULTS);
    }

    @Test
    void initialiseListAndCallATool() throws Exception {
        try (McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context())) {
            JsonObject init = client.initialize("2025-11-25").getAsJsonObject("result");
            assertEquals("2025-11-25", init.get("protocolVersion").getAsString());
            assertEquals("freexmltoolkit", init.getAsJsonObject("serverInfo").get("name").getAsString());
            assertTrue(init.getAsJsonObject("capabilities").has("tools"));

            JsonObject list = client.request("tools/list", "{}").getAsJsonObject("result");
            List<String> names = new ArrayList<>();
            for (JsonElement tool : list.getAsJsonArray("tools")) {
                JsonObject t = tool.getAsJsonObject();
                names.add(t.get("name").getAsString());
                assertTrue(t.getAsJsonObject("inputSchema").has("properties"), t.toString());
                assertTrue(t.getAsJsonObject("annotations").get("readOnlyHint").getAsBoolean(), t.toString());
            }
            assertEquals(ToolRegistry.standard().tools().stream().map(AiTool::name).toList(), names);

            JsonObject call = client.callTool("validate", "{\"xml_content\":\"<a/>\"}").getAsJsonObject("result");
            assertFalse(call.get("isError").getAsBoolean(), call.toString());
            assertTrue(call.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString().startsWith("valid"));
            assertTrue(call.getAsJsonObject("structuredContent").get("valid").getAsBoolean());
            // the structured result also as serialized JSON in a second text block
            assertEquals(call.getAsJsonObject("structuredContent"), com.google.gson.JsonParser.parseString(
                    call.getAsJsonArray("content").get(1).getAsJsonObject().get("text").getAsString()));
        }
    }

    @Test
    void anOlderRevisionIsNegotiated() throws Exception {
        try (McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context())) {
            JsonObject init = client.initialize("2024-11-05").getAsJsonObject("result");
            assertEquals("2024-11-05", init.get("protocolVersion").getAsString());
        }
    }

    @Test
    void aFailingArtefactIsAToolErrorNotAProtocolError() throws Exception {
        try (McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context())) {
            client.initialize("2025-11-25");
            JsonObject call = client.callTool("xpath", "{\"xml_content\":\"<a/>\",\"expression\":\"//[\"}");
            assertTrue(call.has("result"), call.toString());
            assertTrue(call.getAsJsonObject("result").get("isError").getAsBoolean());
            assertEquals("compile", call.getAsJsonObject("result").getAsJsonObject("structuredContent")
                    .getAsJsonObject("error").get("phase").getAsString());
        }
    }

    @Test
    void badArgumentsAndUnknownToolsAreReported() throws Exception {
        try (McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context())) {
            client.initialize("2025-11-25");
            JsonObject wrongType = client.callTool("xpath", "{\"xml_content\":\"<a/>\",\"expression\":5}");
            assertTrue(isErrorOrToolError(wrongType), wrongType.toString());
            JsonObject unknownArgument = client.callTool("xpath", "{\"xml_content\":\"<a/>\",\"expression\":\"1\",\"x\":1}");
            assertTrue(isErrorOrToolError(unknownArgument), unknownArgument.toString());
            JsonObject unknownTool = client.callTool("nope", "{}");
            assertTrue(isErrorOrToolError(unknownTool), unknownTool.toString());
            // the server keeps working
            JsonObject ok = client.callTool("xpath", "{\"xml_content\":\"<a/>\",\"expression\":\"1+1\"}");
            assertFalse(ok.getAsJsonObject("result").get("isError").getAsBoolean(), ok.toString());
        }
    }

    @Test
    void theModernProbeGetsAnErrorSoTheClientFallsBack() throws Exception {
        try (McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context())) {
            JsonObject discover = client.request("server/discover", "{}");
            assertTrue(discover.has("error"), discover.toString());
            assertEquals(-32601, discover.getAsJsonObject("error").get("code").getAsInt(), discover.toString());
            // the legacy handshake still works afterwards
            assertEquals("2025-11-25", client.initialize("2025-11-25").getAsJsonObject("result")
                    .get("protocolVersion").getAsString());
        }
    }

    /**
     * The SDK does not answer a request sent before the handshake with an error: it holds it until the client has
     * initialised (McpServerSession). Either way no tool runs for a client that has not initialised.
     */
    @Test
    void aToolCallBeforeInitialisationWaitsForTheHandshake() throws Exception {
        try (McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context())) {
            client.send("{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"tools/call\",\"params\":"
                    + "{\"name\":\"xpath\",\"arguments\":{\"xml_content\":\"<a/>\",\"expression\":\"1\"}}}");
            assertEquals(null, client.receive(1500), "answered before the handshake");
            client.initialize("2025-11-25");
            JsonObject held = client.receive();
            assertEquals(99, held.get("id").getAsInt(), held.toString());
        }
    }

    @Test
    void endOfInputEndsTheServer() throws Exception {
        McpTestClient client = McpTestClient.start(ToolRegistry.standard(), context());
        client.initialize("2025-11-25");
        client.close();
        assertEquals(McpToolServer.Ending.INPUT_CLOSED, client.ending.get(30, TimeUnit.SECONDS));
    }

    /** Ignores interrupts and runs past the time limit, like a runaway Saxon expression. */
    private static final class SpinTool implements AiTool {
        @Override public String name() { return "spin"; }
        @Override public String description() { return "spins"; }
        @Override public JsonObject inputSchema() { return ToolSchemas.object(); }
        @Override
        public ToolResult call(JsonObject arguments, ToolContext context) {
            long end = System.nanoTime() + 5_000_000_000L;
            while (System.nanoTime() < end) {
                Thread.onSpinWait();
            }
            return ToolResult.ok("late", null);
        }
    }

    @Test
    void theServerStopsAfterTooManyRunawayCalls() throws Exception {
        ToolContext shortContext = ToolContext.of(new WorkspaceAccess(List.of(root)),
                new ToolLimits(Duration.ofMillis(200), 1000, 1000, 10));
        McpToolServer server = new McpToolServer(new ToolRegistry(List.of(new SpinTool())), shortContext,
                "freexmltoolkit", "test").withRunawayLimit(2);
        try (McpTestClient client = new McpTestClient(server)) {
            client.initialize("2025-11-25");
            JsonObject first = client.callTool("spin", "{}");
            assertTrue(first.getAsJsonObject("result").get("isError").getAsBoolean());
            assertFalse(client.ending.isDone());
            JsonObject second = client.callTool("spin", "{}");
            assertTrue(second.getAsJsonObject("result").get("isError").getAsBoolean(), "answered before stopping");
            assertEquals(McpToolServer.Ending.RUNAWAYS, client.ending.get(30, TimeUnit.SECONDS));
        }
    }

    private static boolean isErrorOrToolError(JsonObject response) {
        return response.has("error")
                || response.has("result") && response.getAsJsonObject("result").get("isError").getAsBoolean();
    }
}
