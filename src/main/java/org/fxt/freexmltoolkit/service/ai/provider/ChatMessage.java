package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.List;

/**
 * One message of a conversation, provider-neutral.
 *
 * @param toolCalls     for an assistant message: the calls it made
 * @param toolCallId    for a tool result: the call it answers
 * @param toolError     for a tool result: the call failed
 * @param providerState for an assistant message: what the provider needs to replay it unchanged (Anthropic thinking
 *                      blocks must go back exactly as received); opaque, {@code null} for messages we write
 */
public record ChatMessage(Role role, String text, List<ToolCall> toolCalls, String toolCallId, boolean toolError,
                          Object providerState) {

    public enum Role { USER, ASSISTANT, TOOL }

    public ChatMessage {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, List.of(), null, false, null);
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, text, List.of(), null, false, null);
    }

    public static ChatMessage toolResult(String toolCallId, String text, boolean error) {
        return new ChatMessage(Role.TOOL, text, List.of(), toolCallId, error, null);
    }
}
