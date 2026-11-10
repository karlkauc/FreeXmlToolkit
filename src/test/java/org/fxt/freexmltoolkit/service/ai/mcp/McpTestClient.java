package org.fxt.freexmltoolkit.service.ai.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Drives a {@link McpToolServer} over pipes, one JSON-RPC message per line. */
final class McpTestClient implements AutoCloseable {

    final PipedOutputStream toServer = new PipedOutputStream();
    final PipedInputStream serverIn;
    final PipedOutputStream serverOut = new PipedOutputStream();
    final BufferedReader fromServer;
    final CompletableFuture<McpToolServer.Ending> ending = new CompletableFuture<>();
    private int nextId = 1;

    McpTestClient(McpToolServer server) throws IOException {
        serverIn = new PipedInputStream(toServer, 1 << 20);
        PipedInputStream responses = new PipedInputStream(serverOut, 1 << 20);
        fromServer = new BufferedReader(new InputStreamReader(responses, StandardCharsets.UTF_8));
        Thread.ofVirtual().start(() -> {
            try {
                ending.complete(server.serve(serverIn, serverOut));
            } catch (Throwable t) {
                ending.completeExceptionally(t);
            }
        });
    }

    static McpTestClient start(ToolRegistry registry, ToolContext context) throws IOException {
        return new McpTestClient(new McpToolServer(registry, context, "freexmltoolkit", "test"));
    }

    void send(String json) throws IOException {
        toServer.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        toServer.flush();
    }

    /** Sends a request and returns its response. */
    JsonObject request(String method, String params) throws Exception {
        int id = nextId++;
        send("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\""
                + (params == null ? "" : ",\"params\":" + params) + "}");
        while (true) {
            JsonObject message = receive();
            if (message.has("id") && message.get("id").getAsInt() == id) {
                return message;
            }
        }
    }

    private CompletableFuture<String> pendingLine;

    JsonObject receive() throws Exception {
        JsonObject message = receive(30_000);
        if (message == null) {
            throw new java.util.concurrent.TimeoutException("no message from the server");
        }
        return message;
    }

    /** The next message, or {@code null} when none arrives within {@code millis}. */
    JsonObject receive(long millis) throws Exception {
        if (pendingLine == null) {
            pendingLine = CompletableFuture.supplyAsync(() -> {
                try {
                    return fromServer.readLine();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        String line;
        try {
            line = pendingLine.get(millis, TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return null;
        }
        pendingLine = null;
        if (line == null) {
            throw new IllegalStateException("the server closed its output");
        }
        return JsonParser.parseString(line).getAsJsonObject();
    }

    JsonObject initialize(String protocolVersion) throws Exception {
        JsonObject response = request("initialize", "{\"protocolVersion\":\"" + protocolVersion
                + "\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}");
        send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
        return response;
    }

    JsonObject callTool(String name, String arguments) throws Exception {
        return request("tools/call", "{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}");
    }

    @Override
    public void close() throws IOException {
        toServer.close();
    }
}
