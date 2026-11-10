package org.fxt.freexmltoolkit.service.ai.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.fxt.freexmltoolkit.service.ai.tool.AiTool;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolLimits;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.fxt.freexmltoolkit.service.ai.tool.ToolResult;
import org.fxt.freexmltoolkit.service.ai.tool.ToolSchemas;
import org.fxt.freexmltoolkit.service.ai.tool.WorkspaceAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class StdoutGuardTest {

    @TempDir
    Path root;

    private static final class NoisyTool implements AiTool {
        @Override public String name() { return "noisy"; }
        @Override public String description() { return "prints"; }
        @Override public JsonObject inputSchema() { return ToolSchemas.object(); }
        @Override
        public ToolResult call(JsonObject arguments, ToolContext context) {
            System.out.println("noise");
            System.out.print("more noise without a newline");
            return ToolResult.ok("quiet result", null);
        }
    }

    @Test
    void libraryOutputGoesToStderrAndTheProtocolStreamStaysClean() throws Exception {
        PrintStream realOut = System.out;
        PrintStream realErr = System.err;
        ByteArrayOutputStream fakeStdout = new ByteArrayOutputStream();
        ByteArrayOutputStream fakeStderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(fakeStdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(fakeStderr, true, StandardCharsets.UTF_8));
        try (StdoutGuard guard = StdoutGuard.install()) {
            McpToolServer server = new McpToolServer(new ToolRegistry(List.of(new NoisyTool())),
                    ToolContext.of(new WorkspaceAccess(List.of(root)), ToolLimits.DEFAULTS), "freexmltoolkit", "test");
            try (McpTestClient client = new McpTestClient(server)) {
                client.initialize("2025-11-25");
                JsonObject call = client.callTool("noisy", "{}");
                assertEquals("quiet result", call.getAsJsonObject("result").getAsJsonArray("content")
                        .get(0).getAsJsonObject().get("text").getAsString());
            }
            // anything written through System.out while the guard is installed reached stderr, not stdout
            System.out.println("after the call");
            System.out.flush();
            assertEquals("", fakeStdout.toString(StandardCharsets.UTF_8));
            String err = fakeStderr.toString(StandardCharsets.UTF_8);
            assertTrue(err.contains("noise") && err.contains("after the call"), err);
            // the protocol stream is the original stdout
            guard.protocolStream().write("{\"jsonrpc\":\"2.0\"}\n".getBytes(StandardCharsets.UTF_8));
            guard.protocolStream().flush();
            String protocol = fakeStdout.toString(StandardCharsets.UTF_8);
            for (String line : protocol.split("\n")) {
                JsonParser.parseString(line);
            }
        } finally {
            System.setOut(realOut);
            System.setErr(realErr);
        }
    }

    @Test
    void closingRestoresStdout() throws IOException {
        PrintStream before = System.out;
        StdoutGuard guard = StdoutGuard.install();
        assertSame(System.err, System.out);
        guard.close();
        assertSame(before, System.out);
    }
}
