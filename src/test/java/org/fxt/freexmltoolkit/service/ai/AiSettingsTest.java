package org.fxt.freexmltoolkit.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fxt.freexmltoolkit.service.ai.context.DataLevel;
import org.junit.jupiter.api.Test;

class AiSettingsTest {

    private static AiSettings settings(String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return new AiSettings(map::get);
    }

    @Test
    void defaults() {
        AiSettings s = settings();
        assertFalse(s.enabled());
        assertEquals(DataLevel.EXCERPTS, s.dataLevel());
        assertEquals(DataLevel.SCHEMA, s.dataLevelRemote());
        assertTrue(s.previewAlways());
        assertEquals(List.of(), s.localHosts());
        assertEquals(Duration.ofSeconds(30), s.toolLimits().timeout());
    }

    @Test
    void remoteProvidersAreCapped() {
        AiSettings s = settings("ai.dataLevel", "EXCERPTS", "ai.dataLevel.remote", "STRUCTURE");
        assertEquals(DataLevel.EXCERPTS, s.effectiveLevel(true));
        assertEquals(DataLevel.STRUCTURE, s.effectiveLevel(false));
        assertEquals(DataLevel.SCHEMA, settings("ai.dataLevel", "schema").effectiveLevel(false));
    }

    @Test
    void unknownValuesFallBack() {
        AiSettings s = settings("ai.dataLevel", "everything", "ai.tool.timeoutSeconds", "x", "ai.preview.always", "no");
        assertEquals(DataLevel.EXCERPTS, s.dataLevel());
        assertEquals(Duration.ofSeconds(30), s.toolLimits().timeout());
        assertFalse(s.previewAlways());
    }

    @Test
    void localHostsAndClampedLimits() {
        AiSettings s = settings("ai.localHosts", "gateway.corp, llm.intra ;x", "ai.tool.timeoutSeconds", "99999",
                "ai.tool.maxOutputKb", "8");
        assertEquals(List.of("gateway.corp", "llm.intra", "x"), s.localHosts());
        assertEquals(Duration.ofSeconds(600), s.toolLimits().timeout());
        assertEquals(8 * 1024, s.toolLimits().maxOutputChars());
    }

    @Test
    void dataLevelHelpers() {
        assertEquals(DataLevel.SCHEMA, DataLevel.EXCERPTS.min(DataLevel.SCHEMA));
        assertTrue(DataLevel.EXCERPTS.allows(DataLevel.STRUCTURE));
        assertFalse(DataLevel.SCHEMA.allows(DataLevel.STRUCTURE));
    }
}
