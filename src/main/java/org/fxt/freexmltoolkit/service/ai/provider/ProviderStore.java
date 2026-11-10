package org.fxt.freexmltoolkit.service.ai.provider;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.AdminPolicy;
import org.fxt.freexmltoolkit.service.PropertiesServiceImpl;
import org.fxt.freexmltoolkit.service.ai.AiPolicy;
import org.fxt.freexmltoolkit.service.ai.AiSettings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * The configured AI providers: a JSON list in the setting {@value #KEY_PROVIDERS} and the default in
 * {@value #KEY_DEFAULT} (the pattern of {@code service/fo/FoProcessorStore}). A list in {@code policy.properties} is
 * fixed: the UI does not edit it ({@link #isPolicyFixed()}). Keys are stored apart ({@link ProviderSecrets}).
 */
public final class ProviderStore {

    private static final Logger logger = LogManager.getLogger(ProviderStore.class);

    public static final String KEY_PROVIDERS = "ai.providers";
    public static final String KEY_DEFAULT = "ai.provider.default";

    private static final Type LIST_TYPE = new TypeToken<List<Stored>>() { }.getType();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Function<String, String> getter;
    private final BiConsumer<String, String> setter;
    private final BooleanSupplier remoteAllowed;
    private final Predicate<String> locked;
    private final java.util.function.Supplier<List<String>> localHosts;

    public ProviderStore(Function<String, String> getter, BiConsumer<String, String> setter,
                         BooleanSupplier remoteAllowed, Predicate<String> locked,
                         java.util.function.Supplier<List<String>> localHosts) {
        this.getter = getter;
        this.setter = setter;
        this.remoteAllowed = remoteAllowed;
        this.locked = locked;
        this.localHosts = localHosts;
    }

    /** The store of this installation. */
    public static ProviderStore settings() {
        var properties = PropertiesServiceImpl.getInstance();
        return new ProviderStore(properties::get, properties::set, AiPolicy::remoteAllowed,
                key -> AdminPolicy.current().isLocked(key), () -> AiSettings.current().localHosts());
    }

    /** Whether the administrator fixed the list (it is then not edited in Settings). */
    public boolean isPolicyFixed() {
        return locked.test(KEY_PROVIDERS);
    }

    /** Whether the default is fixed by the policy. */
    public boolean isDefaultFixed() {
        return locked.test(KEY_DEFAULT);
    }

    public List<String> localHosts() {
        return localHosts.get();
    }

    /** Whether {@code provider} may be used under the policy ({@code ai.remote.allowed}). */
    public boolean allowed(ProviderConfig provider) {
        return remoteAllowed.getAsBoolean() || provider.isLocal(localHosts());
    }

    /** Why {@code provider} may not be used, or {@code null}. */
    public String refusal(ProviderConfig provider) {
        if (allowed(provider)) {
            return null;
        }
        return "Only providers on this computer may be used: the administrator switched remote AI providers off "
                + "(ai.remote.allowed=false). " + provider.host() + " is not local.";
    }

    public List<ProviderConfig> list() {
        String json = getter.apply(KEY_PROVIDERS);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Stored> stored = GSON.fromJson(json, LIST_TYPE);
            List<ProviderConfig> result = new ArrayList<>();
            if (stored != null) {
                for (Stored s : stored) {
                    if (s != null) {
                        result.add(s.toConfig());
                    }
                }
            }
            return List.copyOf(result);
        } catch (JsonParseException | IllegalStateException e) {
            logger.warn("Ignoring unreadable setting {}", KEY_PROVIDERS);
            return List.of();
        }
    }

    /** Replaces the list; a default that is gone is cleared. */
    public void save(List<ProviderConfig> providers) {
        if (isPolicyFixed()) {
            throw new IllegalStateException("The provider list is fixed by the administrator.");
        }
        List<Stored> stored = providers.stream().map(Stored::of).toList();
        setter.accept(KEY_PROVIDERS, stored.isEmpty() ? "" : GSON.toJson(stored, LIST_TYPE));
        String def = getter.apply(KEY_DEFAULT);
        if (def != null && !def.isBlank() && providers.stream().noneMatch(p -> p.id().equals(def))) {
            setter.accept(KEY_DEFAULT, "");
        }
    }

    /** The default provider: the configured default, else the only one; never one the policy refuses. */
    public Optional<ProviderConfig> defaultProvider() {
        List<ProviderConfig> all = list();
        String def = getter.apply(KEY_DEFAULT);
        Optional<ProviderConfig> chosen = all.stream().filter(p -> p.id().equals(def)).findFirst();
        if (chosen.isEmpty() && all.size() == 1) {
            chosen = Optional.of(all.getFirst());
        }
        return chosen.filter(this::allowed);
    }

    /** @throws IllegalArgumentException when the provider is unknown or refused by the policy */
    public void setDefaultId(String id) {
        if (id == null || id.isBlank()) {
            setter.accept(KEY_DEFAULT, "");
            return;
        }
        ProviderConfig provider = list().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No provider " + id));
        String refusal = refusal(provider);
        if (refusal != null) {
            throw new IllegalArgumentException(refusal);
        }
        setter.accept(KEY_DEFAULT, id);
    }

    public String defaultId() {
        return defaultProvider().map(ProviderConfig::id).orElse("");
    }

    public Optional<ProviderConfig> find(String id) {
        return list().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /** The JSON shape (field names are the file format; keep them stable). */
    private static final class Stored {
        String id;
        String name;
        String kind;
        String preset;
        String baseUrl;
        String model;
        String apiKeyEnv;
        String authHeader;
        String tokenField;
        Integer maxOutputTokens;
        Integer readTimeoutSeconds;

        static Stored of(ProviderConfig c) {
            Stored s = new Stored();
            s.id = c.id();
            s.name = c.name();
            s.kind = c.kind().name();
            s.preset = c.preset().name();
            s.baseUrl = c.baseUrl();
            s.model = c.model();
            s.apiKeyEnv = c.apiKeyEnv();
            s.authHeader = c.authHeader() == AuthHeader.BEARER ? null : c.authHeader().name();
            s.tokenField = c.tokenField() == c.preset().tokenField() ? null : c.tokenField().name();
            s.maxOutputTokens = c.maxOutputTokens() == ProviderConfig.DEFAULT_MAX_OUTPUT_TOKENS ? null : c.maxOutputTokens();
            s.readTimeoutSeconds = c.readTimeoutSeconds() == ProviderConfig.DEFAULT_READ_TIMEOUT_SECONDS
                    ? null : c.readTimeoutSeconds();
            return s;
        }

        ProviderConfig toConfig() {
            ProviderPreset p = ProviderPreset.of(preset);
            ProviderKind k = kind == null ? p.kind()
                    : "ANTHROPIC".equalsIgnoreCase(kind) ? ProviderKind.ANTHROPIC : ProviderKind.OPENAI_COMPATIBLE;
            return new ProviderConfig(id, name, k, p, baseUrl, model, apiKeyEnv, AuthHeader.of(authHeader),
                    tokenField == null ? p.tokenField() : TokenLimitField.of(tokenField),
                    maxOutputTokens == null ? 0 : maxOutputTokens, readTimeoutSeconds == null ? 0 : readTimeoutSeconds);
        }
    }
}
