package org.fxt.freexmltoolkit.service.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProviderStoreTest {

    private final Map<String, String> settings = new HashMap<>();
    private final Set<String> locked = new HashSet<>();
    private boolean remoteAllowed = true;

    private ProviderStore store() {
        return new ProviderStore(settings::get, settings::put, () -> remoteAllowed, locked::contains,
                () -> List.of("gateway.corp"));
    }

    private static ProviderConfig ollama() {
        return ProviderConfig.fromPreset(ProviderPreset.OLLAMA).withModel("qwen2.5-coder:7b");
    }

    private static ProviderConfig anthropic() {
        return ProviderConfig.fromPreset(ProviderPreset.ANTHROPIC).withModel("some-model");
    }

    @Test
    void roundTripKeepsEveryField() {
        ProviderConfig custom = new ProviderConfig("c1", "Gateway", ProviderKind.OPENAI_COMPATIBLE, ProviderPreset.CUSTOM,
                "https://gateway.corp/v1/", "m", "GW_KEY", AuthHeader.API_KEY, TokenLimitField.MAX_COMPLETION_TOKENS,
                2048, 120);
        store().save(List.of(ollama(), custom));
        List<ProviderConfig> read = store().list();
        assertEquals(2, read.size());
        assertEquals(custom, read.get(1));
        assertEquals("https://gateway.corp/v1", read.get(1).baseUrl(), "trailing slash removed");
        assertFalse(settings.get(ProviderStore.KEY_PROVIDERS).contains("apiKey\""), "no key in the list");
    }

    @Test
    void theOnlyProviderIsTheDefaultAndAGoneDefaultIsCleared() {
        ProviderConfig a = ollama();
        ProviderConfig b = anthropic();
        store().save(List.of(a));
        assertEquals(a.id(), store().defaultProvider().orElseThrow().id());
        store().save(List.of(a, b));
        assertTrue(store().defaultProvider().isEmpty(), "two providers, none chosen");
        store().setDefaultId(b.id());
        assertEquals(b.id(), store().defaultId());
        store().save(List.of(a));
        assertEquals("", settings.get(ProviderStore.KEY_DEFAULT));
    }

    @Test
    void remoteProvidersUnderThePolicy() {
        ProviderConfig remote = anthropic();
        ProviderConfig gateway = ProviderConfig.fromPreset(ProviderPreset.CUSTOM);
        gateway = new ProviderConfig(gateway.id(), "gw", null, ProviderPreset.CUSTOM, "https://gateway.corp/v1", "m",
                null, null, null, 0, 0);
        store().save(List.of(ollama(), remote, gateway));
        remoteAllowed = false;
        String remoteId = remote.id();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> store().setDefaultId(remoteId));
        assertTrue(e.getMessage().contains("ai.remote.allowed"), e.getMessage());
        assertNotNull(store().refusal(remote));
        assertNull(store().refusal(gateway), "a host from ai.localHosts counts as local");
        assertNull(store().refusal(ollama()));
        // a default set while remote was allowed is not used once it is refused
        remoteAllowed = true;
        store().setDefaultId(remoteId);
        remoteAllowed = false;
        assertEquals(Optional.empty(), store().defaultProvider());
    }

    @Test
    void aPolicyFixedListIsNotSaved() {
        locked.add(ProviderStore.KEY_PROVIDERS);
        assertTrue(store().isPolicyFixed());
        assertThrows(IllegalStateException.class, () -> store().save(List.of(ollama())));
    }

    @Test
    void unreadableJsonIsAnEmptyList() {
        settings.put(ProviderStore.KEY_PROVIDERS, "{not json");
        assertEquals(List.of(), store().list());
    }

    @Test
    void localDetection() {
        List<String> none = List.of();
        assertTrue(cfg("http://localhost:11434/v1").isLocal(none));
        assertTrue(cfg("http://127.0.0.1:1234/v1").isLocal(none));
        assertTrue(cfg("http://[::1]:8080/v1").isLocal(none));
        assertFalse(cfg("https://api.openai.com/v1").isLocal(none));
        assertFalse(cfg("http://10.0.0.5/v1").isLocal(none));
        assertTrue(cfg("http://10.0.0.5/v1").isLocal(List.of("10.0.0.5")));
        assertFalse(cfg("not a url").isLocal(none));
    }

    @Test
    void presetsAndProblems() {
        assertEquals(ProviderPreset.CUSTOM, ProviderPreset.of("nope"));
        assertEquals(TokenLimitField.MAX_COMPLETION_TOKENS, ProviderConfig.fromPreset(ProviderPreset.OPENAI).tokenField());
        assertNotNull(ProviderConfig.fromPreset(ProviderPreset.OLLAMA).problem(), "no model yet");
        assertNull(ollama().problem());
        assertNotNull(cfg("ftp://x").withModel("m").problem());
    }

    private static ProviderConfig cfg(String url) {
        return new ProviderConfig(null, null, null, ProviderPreset.CUSTOM, url, "m", null, null, null, 0, 0);
    }
}
