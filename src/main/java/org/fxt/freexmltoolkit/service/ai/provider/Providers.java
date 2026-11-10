package org.fxt.freexmltoolkit.service.ai.provider;

import org.fxt.freexmltoolkit.service.ai.provider.anthropic.AnthropicProvider;

/** Creates the {@link ChatProvider} for a configuration, with its key from {@link ProviderSecrets}. */
public final class Providers {

    private Providers() {
    }

    /**
     * @throws ProviderException when the configuration is incomplete or the policy refuses the provider
     */
    public static ChatProvider create(ProviderConfig config, ProviderStore store, ProviderSecrets secrets)
            throws ProviderException {
        String problem = config.problem();
        if (problem != null) {
            throw new ProviderException(ProviderException.Kind.BAD_REQUEST, problem);
        }
        String refusal = store.refusal(config);
        if (refusal != null) {
            throw new ProviderException(ProviderException.Kind.PERMISSION, refusal);
        }
        String key = secrets.apiKey(config).orElse(null);
        if (key == null && config.preset().needsApiKey()) {
            throw new ProviderException(ProviderException.Kind.AUTH, "No API key is set for " + config.name()
                    + ": enter one in Settings → AI, or name an environment variable that holds it.");
        }
        return switch (config.kind()) {
            case ANTHROPIC -> new AnthropicProvider(config, key);
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(config, key);
        };
    }
}
