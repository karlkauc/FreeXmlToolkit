package org.fxt.freexmltoolkit.service.ai.provider;

/**
 * The request field that limits the answer's length on an OpenAI-compatible server: OpenAI deprecates
 * {@code max_tokens} for {@code max_completion_tokens}, Ollama and most local servers document {@code max_tokens}.
 */
public enum TokenLimitField {
    MAX_TOKENS("max_tokens"), MAX_COMPLETION_TOKENS("max_completion_tokens");

    private final String json;

    TokenLimitField(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

    public static TokenLimitField of(String name) {
        return name != null && name.equalsIgnoreCase(MAX_COMPLETION_TOKENS.name()) ? MAX_COMPLETION_TOKENS : MAX_TOKENS;
    }
}
