package org.fxt.freexmltoolkit.service.ai.tool;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.JsonObject;

/**
 * The set of tools one consumer offers (the MCP server, later the in-app assistant), in a deterministic
 * order (by name — the MCP specification asks for a stable {@code tools/list}).
 */
public final class ToolRegistry {

    private static final Logger logger = LogManager.getLogger(ToolRegistry.class);

    private final Map<String, AiTool> tools;

    /** @throws IllegalArgumentException when two tools share a name */
    public ToolRegistry(Collection<? extends AiTool> tools) {
        Map<String, AiTool> byName = new LinkedHashMap<>();
        tools.stream().sorted(Comparator.comparing(AiTool::name)).forEach(tool -> {
            if (byName.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("duplicate tool name: " + tool.name());
            }
        });
        this.tools = byName;
    }

    /** All tools of Phase 1. */
    public static ToolRegistry standard() {
        return new ToolRegistry(List.of());
    }

    /**
     * The subset with the given names.
     *
     * @throws IllegalArgumentException when a name is unknown; the message lists the valid names
     */
    public ToolRegistry only(Collection<String> names) {
        List<String> unknown = names.stream().filter(n -> !tools.containsKey(n)).toList();
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown tool" + (unknown.size() > 1 ? "s " : " ")
                    + String.join(", ", unknown) + "; available: " + String.join(", ", tools.keySet()));
        }
        return new ToolRegistry(names.stream().distinct().map(tools::get).toList());
    }

    public List<AiTool> tools() {
        return List.copyOf(tools.values());
    }

    public Optional<AiTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * Runs a tool under the context's timeout. Never throws for tool-level failures: an unknown tool,
     * misuse ({@link ToolException}), a crash and a timeout all come back as an error result.
     *
     * <p>The call runs on a virtual thread. On timeout that thread is interrupted and abandoned; a
     * tool whose engine ignores interrupts (a non-terminating stylesheet) must enforce the deadline
     * itself — the sandboxed transformation does.
     */
    public ToolResult call(String name, JsonObject arguments, ToolContext context) {
        AiTool tool = tools.get(name);
        if (tool == null) {
            return ToolResult.failed("unknown tool '" + name + "'; available: " + String.join(", ", tools.keySet()),
                    null);
        }
        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        Thread worker = Thread.ofVirtual().name("fxt-tool-" + name).start(() -> {
            try {
                future.complete(tool.call(arguments, context));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        long millis = context.limits().timeout().toMillis();
        try {
            ToolResult result = future.get(millis, TimeUnit.MILLISECONDS);
            return result != null ? result : ToolResult.failed(name + " returned no result", null);
        } catch (TimeoutException e) {
            worker.interrupt();
            JsonObject structured = new JsonObject();
            structured.addProperty("timed_out", true);
            return ToolResult.failed(name + " did not finish within the time limit of " + millis + " ms",
                    structured);
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            return ToolResult.failed(name + " was cancelled", null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ToolException misuse) {
                return ToolResult.failed(misuse.getMessage(), null);
            }
            // only the type reaches the log: messages of a failing engine may quote the input
            logger.warn("Tool {} failed with {}", name, cause.getClass().getName());
            logger.debug("Tool {} failure", name, cause);
            return ToolResult.failed(name + " failed: " + cause.getClass().getSimpleName()
                    + (cause.getMessage() != null ? ": " + cause.getMessage() : ""), null);
        }
    }
}
