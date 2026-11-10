package org.fxt.freexmltoolkit.service.ai.provider;

import java.util.List;

/**
 * One request to a model.
 *
 * @param system          the system prompt (instructions; document data never goes here, see D12)
 * @param maxOutputTokens 0 = the provider's configured limit
 * @param temperature     OpenAI-compatible providers only; never sent to Anthropic (API version 2026-10-21 rejects
 *                        it); {@code null} = the server's default
 */
public record ChatRequest(String system, List<ChatMessage> messages, List<ToolSpec> tools, int maxOutputTokens,
                          Double temperature) {

    public ChatRequest {
        system = system == null ? "" : system;
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    public static ChatRequest of(String system, String userText) {
        return new ChatRequest(system, List.of(ChatMessage.user(userText)), List.of(), 0, null);
    }

    /** The same request continued with more messages. */
    public ChatRequest plus(List<ChatMessage> more) {
        List<ChatMessage> all = new java.util.ArrayList<>(messages);
        all.addAll(more);
        return new ChatRequest(system, all, tools, maxOutputTokens, temperature);
    }
}
