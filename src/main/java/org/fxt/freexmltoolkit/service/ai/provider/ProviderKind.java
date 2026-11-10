package org.fxt.freexmltoolkit.service.ai.provider;

/** The wire protocol of a provider (decisions D5, D13, D14). */
public enum ProviderKind {
    /** OpenAI-compatible Chat Completions: OpenAI, Ollama, LM Studio, vLLM, Azure OpenAI v1, gateways. */
    OPENAI_COMPATIBLE,
    /** The Anthropic Messages API, through the official Java SDK on the {@code ConnectionService} adapter. */
    ANTHROPIC
}
