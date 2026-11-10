package org.fxt.freexmltoolkit.service.ai.provider;

/** Starting points for a provider: protocol, base URL and the request details that differ between servers. */
public enum ProviderPreset {
    OLLAMA("Ollama", ProviderKind.OPENAI_COMPATIBLE, "http://localhost:11434/v1", TokenLimitField.MAX_TOKENS),
    LM_STUDIO("LM Studio", ProviderKind.OPENAI_COMPATIBLE, "http://localhost:1234/v1", TokenLimitField.MAX_TOKENS),
    OPENAI("OpenAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.openai.com/v1", TokenLimitField.MAX_COMPLETION_TOKENS),
    ANTHROPIC("Anthropic", ProviderKind.ANTHROPIC, "https://api.anthropic.com", TokenLimitField.MAX_TOKENS),
    CUSTOM("Custom (OpenAI-compatible)", ProviderKind.OPENAI_COMPATIBLE, "", TokenLimitField.MAX_TOKENS);

    private final String displayName;
    private final ProviderKind kind;
    private final String baseUrl;
    private final TokenLimitField tokenField;

    ProviderPreset(String displayName, ProviderKind kind, String baseUrl, TokenLimitField tokenField) {
        this.displayName = displayName;
        this.kind = kind;
        this.baseUrl = baseUrl;
        this.tokenField = tokenField;
    }

    public String displayName() {
        return displayName;
    }

    public ProviderKind kind() {
        return kind;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public TokenLimitField tokenField() {
        return tokenField;
    }

    /** Whether a key is usually needed (local servers ignore it). */
    public boolean needsApiKey() {
        return this == OPENAI || this == ANTHROPIC;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** The preset named {@code name} (enum name), or {@link #CUSTOM}. */
    public static ProviderPreset of(String name) {
        if (name != null) {
            for (ProviderPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(name.strip())) {
                    return preset;
                }
            }
        }
        return CUSTOM;
    }
}
