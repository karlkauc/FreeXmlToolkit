package org.fxt.freexmltoolkit.service.ai.provider;

/** Why the model stopped. */
public enum StopReason {
    /** The answer is complete. */
    END,
    /** The length limit cut the answer. */
    MAX_TOKENS,
    /** The model wants tool results. */
    TOOL_USE,
    /** The provider's safety system declined the request (Anthropic {@code refusal}). */
    REFUSED,
    /** The provider filtered the answer (OpenAI {@code content_filter}). */
    CONTENT_FILTER,
    OTHER
}
