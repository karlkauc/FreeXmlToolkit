package org.fxt.freexmltoolkit.service.ai.mcp;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.ai.tool.AiTool;
import org.fxt.freexmltoolkit.service.ai.tool.ToolContext;
import org.fxt.freexmltoolkit.service.ai.tool.ToolRegistry;
import org.fxt.freexmltoolkit.service.ai.tool.ToolResult;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Serves a {@link ToolRegistry} over the Model Context Protocol on a stream pair (stdio): the adapter between the
 * tool layer and the MCP Java SDK, the only place that knows the SDK ({@code AiLayeringGuardTest}). Tools only — no
 * resources, prompts or sampling. The SDK speaks protocol revisions up to 2025-11-25 with the {@code initialize}
 * handshake; a client of revision 2026-07-28 probes with {@code server/discover}, gets "method not found" and
 * falls back to the handshake (decision D4).
 *
 * <p>Tool failures (a stylesheet that does not compile, a timeout) are tool results with {@code isError}; protocol
 * misuse is answered by the SDK as a JSON-RPC error.
 */
public final class McpToolServer {

    private static final Logger logger = LogManager.getLogger(McpToolServer.class);
    private static final Gson GSON = new Gson();

    /** Why {@link #serve} returned. */
    public enum Ending {
        /** The client closed the input stream. */
        INPUT_CLOSED,
        /** Too many tool calls kept running past their time limit ({@link ToolRegistry#runawayCount()}). */
        RUNAWAYS
    }

    /** Runaway calls after which the server stops (the process exits, which ends them). */
    public static final int DEFAULT_RUNAWAY_LIMIT = 3;

    private final ToolRegistry registry;
    private final ToolContext context;
    private final String serverName;
    private final String version;
    private int runawayLimit = DEFAULT_RUNAWAY_LIMIT;

    public McpToolServer(ToolRegistry registry, ToolContext context, String serverName, String version) {
        this.registry = registry;
        this.context = context;
        this.serverName = serverName;
        this.version = version;
    }

    /** Stop after this many runaway calls instead of {@value #DEFAULT_RUNAWAY_LIMIT}. */
    public McpToolServer withRunawayLimit(int limit) {
        this.runawayLimit = Math.max(1, limit);
        return this;
    }

    /**
     * Serves until the input ends or too many calls ran away. Never writes anything but protocol messages to
     * {@code out}.
     */
    public Ending serve(InputStream in, OutputStream out) throws IOException {
        CompletableFuture<Ending> ending = new CompletableFuture<>();
        InputStream watched = new FilterInputStream(in) {
            @Override
            public int read() throws IOException {
                return signal(super.read());
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return signal(super.read(b, off, len));
            }

            private int signal(int result) {
                if (result < 0) {
                    ending.complete(Ending.INPUT_CLOSED);
                }
                return result;
            }
        };
        McpJsonMapper mapper = McpJsonDefaults.getMapper();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(mapper, watched, out);
        List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<>();
        for (AiTool tool : registry.tools()) {
            specifications.add(specification(tool, mapper, ending));
        }
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(serverName, version)
                .instructions("XML tools of FreeXmlToolkit: validate documents (XSD, DTD, RELAX NG, Schematron), "
                        + "query them (XPath, XQuery), run and test XSLT (XSpec), explore schemas, compare documents. "
                        + "Pass a stylesheet or query you wrote as *_content to check it without saving it. Nothing "
                        + "is written to disk; files are read only inside the folders the server was started with.")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .requestTimeout(context.limits().timeout().plus(Duration.ofSeconds(30)))
                .tools(specifications)
                .build();
        try {
            return ending.join();
        } finally {
            try {
                server.closeGracefully();
            } catch (RuntimeException e) {
                logger.debug("MCP server did not close cleanly", e);
            }
        }
    }

    private McpServerFeatures.SyncToolSpecification specification(AiTool tool, McpJsonMapper mapper,
                                                                  CompletableFuture<Ending> ending) {
        McpSchema.Tool definition = McpSchema.Tool.builder(tool.name(), mapper, tool.inputSchema().toString())
                .description(tool.description())
                .annotations(new McpSchema.ToolAnnotations(null, tool.readOnly(), !tool.readOnly(), true, false, null))
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(definition)
                .callHandler((exchange, request) -> {
                    ToolResult result = registry.call(tool.name(), arguments(request.arguments()), context);
                    McpSchema.CallToolResult.Builder answer = McpSchema.CallToolResult.builder()
                            .addTextContent(result.text())
                            .isError(result.error());
                    if (result.structured() != null) {
                        String json = result.structured().toString();
                        answer.structuredContent(mapper, json);
                        // The specification asks for the structured result as serialized JSON in a text block as
                        // well; clients that show only text content (Claude Code does so for error results) would
                        // otherwise lose the fields (error code, phase, line).
                        answer.addTextContent(json);
                    }
                    checkRunaways(ending);
                    return answer.build();
                })
                .build();
    }

    /** After a call: when too many have run away, end {@link #serve} once this answer has been written. */
    private void checkRunaways(CompletableFuture<Ending> ending) {
        int runaways = registry.runawayCount();
        if (runaways >= runawayLimit && !ending.isDone()) {
            logger.warn("{} tool calls are still running past their time limit; stopping the MCP server", runaways);
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(1000); // let the transport write the answer of this call
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ending.complete(Ending.RUNAWAYS);
            });
        }
    }

    private static JsonObject arguments(Map<String, Object> arguments) {
        if (arguments == null) {
            return new JsonObject();
        }
        JsonElement tree = GSON.toJsonTree(arguments);
        return tree.isJsonObject() ? tree.getAsJsonObject() : new JsonObject();
    }
}
