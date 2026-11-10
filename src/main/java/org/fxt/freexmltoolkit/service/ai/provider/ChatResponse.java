package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.List;

/**
 * A model's answer.
 *
 * @param message the answer as an assistant message, ready to be appended to the conversation unchanged
 */
public record ChatResponse(String text, List<ToolCall> toolCalls, StopReason stop, Usage usage, String model,
                           ChatMessage message) {

    public ChatResponse {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        usage = usage == null ? Usage.UNKNOWN : usage;
    }
}
