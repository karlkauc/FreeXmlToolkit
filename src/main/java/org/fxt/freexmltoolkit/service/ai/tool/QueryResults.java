package org.fxt.freexmltoolkit.service.ai.tool;

import java.time.Duration;

import org.fxt.freexmltoolkit.service.SandboxedQuery;

import com.google.gson.JsonObject;

/** Shared result shape of {@code xpath} and {@code xquery}. */
final class QueryResults {

    private QueryResults() {
    }

    /**
     * The deadline handed to an engine: a little under the call's timeout, so the engine stops the run and
     * reports it before {@link ToolRegistry} gives up on the call.
     */
    static Duration engineDeadline(ToolLimits limits) {
        return limits.timeout().multipliedBy(9).dividedBy(10);
    }

    /** {@code ok}, {@code result}, {@code truncated}, {@code item_count}, {@code timed_out}, {@code error}. */
    static ToolResult of(SandboxedQuery.Outcome outcome, ToolLimits limits) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", outcome.ok());
        ToolJson.putText(result, "result", outcome.output(), limits);
        result.addProperty("item_count", Math.max(outcome.itemCount(), 0));
        result.addProperty("timed_out", outcome.timedOut());
        if (outcome.ok()) {
            result.add("error", com.google.gson.JsonNull.INSTANCE);
            String text = outcome.itemCount() + " item" + (outcome.itemCount() == 1 ? "" : "s")
                    + (outcome.output().isEmpty() ? "" : "\n" + limits.truncate(outcome.output()));
            return ToolResult.ok(text, result);
        }
        result.add("error", ToolJson.error(outcome.errorCode(), outcome.errorPhase(), outcome.errorLine(),
                outcome.errorMessage()));
        return ToolResult.failed(ToolJson.describe(outcome.errorCode(), outcome.errorPhase(), outcome.errorLine(),
                outcome.errorMessage()), result);
    }

    /** The {@code file:} URI of an input read from a file, else {@code null}. */
    static String uriOf(ToolInput input) {
        return input.file().map(f -> f.toUri().toString()).orElse(null);
    }
}
