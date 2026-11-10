package org.fxt.freexmltoolkit.service.ai.provider;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * One configured AI provider. The API key is not part of it ({@link ProviderSecrets}); {@code apiKeyEnv} names an
 * environment variable that holds the key, the preferred way.
 *
 * @param model              the model id as the provider names it; chosen by the user, never a built-in default
 * @param maxOutputTokens    the answer's length limit
 * @param readTimeoutSeconds how long to wait for data from the server (a local model may think for minutes)
 */
public record ProviderConfig(String id, String name, ProviderKind kind, ProviderPreset preset, String baseUrl,
                             String model, String apiKeyEnv, AuthHeader authHeader, TokenLimitField tokenField,
                             int maxOutputTokens, int readTimeoutSeconds) {

    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 8192;
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 300;

    public ProviderConfig {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        preset = preset == null ? ProviderPreset.CUSTOM : preset;
        kind = kind == null ? preset.kind() : kind;
        name = name == null || name.isBlank() ? preset.displayName() : name.strip();
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        model = model == null ? "" : model.strip();
        apiKeyEnv = apiKeyEnv == null || apiKeyEnv.isBlank() ? null : apiKeyEnv.strip();
        authHeader = authHeader == null ? AuthHeader.BEARER : authHeader;
        tokenField = tokenField == null ? preset.tokenField() : tokenField;
        maxOutputTokens = maxOutputTokens <= 0 ? DEFAULT_MAX_OUTPUT_TOKENS : Math.min(maxOutputTokens, 128_000);
        readTimeoutSeconds = readTimeoutSeconds <= 0 ? DEFAULT_READ_TIMEOUT_SECONDS
                : Math.max(10, Math.min(readTimeoutSeconds, 3600));
    }

    /** A new provider from a preset (no model yet). */
    public static ProviderConfig fromPreset(ProviderPreset preset) {
        return new ProviderConfig(null, preset.displayName(), preset.kind(), preset, preset.baseUrl(), "", null,
                AuthHeader.BEARER, preset.tokenField(), DEFAULT_MAX_OUTPUT_TOKENS, DEFAULT_READ_TIMEOUT_SECONDS);
    }

    /** A copy with another model. */
    public ProviderConfig withModel(String newModel) {
        return new ProviderConfig(id, name, kind, preset, baseUrl, newModel, apiKeyEnv, authHeader, tokenField,
                maxOutputTokens, readTimeoutSeconds);
    }

    /** The host of the base URL, lower case, or {@code ""}. */
    public String host() {
        try {
            String host = URI.create(baseUrl).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /**
     * Whether requests stay on this machine (or go to a host the administrator or user declared local): a loopback
     * address or name, or a host in {@code localHosts}. Names are not resolved.
     */
    public boolean isLocal(List<String> localHosts) {
        String host = host();
        if (host.isEmpty()) {
            return false;
        }
        if (host.equals("localhost") || host.endsWith(".localhost") || host.startsWith("127.") || host.equals("::1")
                || host.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        if (looksLikeIpLiteral(host)) {
            try {
                if (InetAddress.getByName(host).isLoopbackAddress()) {
                    return true;
                }
            } catch (Exception ignored) {
                // not an address after all
            }
        }
        return localHosts != null && localHosts.stream().anyMatch(h -> h.equalsIgnoreCase(host));
    }

    private static boolean looksLikeIpLiteral(String host) {
        return host.contains(":") || host.chars().allMatch(c -> Character.isDigit(c) || c == '.');
    }

    /** Whether the configuration can be used: a base URL with http(s) and a model. */
    public String problem() {
        if (baseUrl.isEmpty()) {
            return "The base URL is missing.";
        }
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))
                    || uri.getHost() == null) {
                return "The base URL must start with http:// or https:// and name a host.";
            }
        } catch (IllegalArgumentException e) {
            return "The base URL is not valid: " + e.getMessage();
        }
        if (model.isEmpty()) {
            return "No model is chosen.";
        }
        return null;
    }
}
