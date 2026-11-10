package org.fxt.freexmltoolkit.service.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ProviderSecretsTest {

    private final Map<String, String> settings = new HashMap<>();
    private final Set<String> machineKeys = new HashSet<>();
    private final Map<String, String> env = new HashMap<>();

    private ProviderSecrets secrets() {
        return new ProviderSecrets(settings::get, settings::put, settings::remove, machineKeys::contains, env::get);
    }

    @Test
    void storedKeyAndRemoval() {
        ProviderConfig p = ProviderConfig.fromPreset(ProviderPreset.ANTHROPIC);
        secrets().store(p.id(), "  sk-123  ");
        assertEquals(Optional.of("sk-123"), secrets().apiKey(p));
        assertTrue(secrets().hasStoredKey(p.id()));
        secrets().remove(p.id());
        assertEquals(Optional.empty(), secrets().apiKey(p));
        secrets().store(p.id(), "x");
        secrets().store(p.id(), " ");
        assertFalse(settings.containsKey(ProviderSecrets.keyName(p.id())), "a blank key removes it");
    }

    @Test
    void theEnvironmentVariableWins() {
        ProviderConfig p = new ProviderConfig("p", null, null, ProviderPreset.OPENAI, "https://api.openai.com/v1", "m",
                "MY_KEY", null, null, 0, 0);
        secrets().store("p", "stored");
        env.put("MY_KEY", "from-env");
        assertEquals(Optional.of("from-env"), secrets().apiKey(p));
        env.remove("MY_KEY");
        assertEquals(Optional.of("stored"), secrets().apiKey(p), "an empty variable falls back to the stored key");
    }

    @Test
    void aKeyFromThePolicyOrDefaultsFileIsIgnored() {
        ProviderConfig p = ProviderConfig.fromPreset(ProviderPreset.ANTHROPIC);
        settings.put(ProviderSecrets.keyName(p.id()), "sk-from-policy");
        machineKeys.add(ProviderSecrets.keyName(p.id()));
        assertEquals(Optional.empty(), secrets().apiKey(p));
    }
}
