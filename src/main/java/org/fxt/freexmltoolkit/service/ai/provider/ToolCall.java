package org.fxt.freexmltoolkit.service.ai.provider;

import com.google.gson.JsonObject;

/**
 * A tool call the model made.
 *
 * @param arguments    the parsed arguments, or {@code null} when {@code rawArguments} is not a JSON object (a model
 *                     may produce invalid JSON; the caller answers with a tool error)
 * @param rawArguments the arguments as the model wrote them
 */
public record ToolCall(String id, String name, JsonObject arguments, String rawArguments) {
}
