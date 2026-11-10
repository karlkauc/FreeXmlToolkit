package org.fxt.freexmltoolkit.service.fo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class FoProcessorStoreTest {

    private final Map<String, String> settings = new HashMap<>();
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private final FoProcessorStore store = new FoProcessorStore(settings::get, settings::put, allowed::get);

    @Test
    void processorsSurviveARoundTrip() {
        FoProcessorConfig xep = new FoProcessorConfig(null, "XEP 4", FoProcessorPreset.XEP, "/opt/xep/xep",
                "-fo ${fo} -pdf ${pdf}", "", 600);
        FoProcessorConfig fop = FoProcessorConfig.fromPreset(FoProcessorPreset.APACHE_FOP);
        store.save(List.of(xep, fop));
        assertEquals(List.of(xep, fop), store.list());
        assertFalse(settings.get(FoProcessorStore.KEY_PROCESSORS).contains("timeoutSeconds\":300"),
                "defaults are not written");
    }

    @Test
    void defaultFallsBackToBuiltinWhenItsProcessorIsGone() {
        FoProcessorConfig xep = FoProcessorConfig.fromPreset(FoProcessorPreset.XEP);
        store.save(List.of(xep));
        assertEquals(FoProcessorStore.BUILTIN_ID, store.defaultId());
        store.setDefaultId(xep.id());
        assertEquals(xep.id(), store.defaultId());
        assertEquals(xep.id(), store.lastId(), "no panel choice yet: the default");
        store.save(List.of());
        assertEquals(FoProcessorStore.BUILTIN_ID, store.defaultId());
    }

    @Test
    void resolveByIdOrNameAndBuiltin() {
        FoProcessorConfig xep = new FoProcessorConfig(null, "RenderX XEP", FoProcessorPreset.XEP, "", null, "", 0);
        store.save(List.of(xep));
        assertTrue(store.resolve(null).isEmpty());
        assertTrue(store.resolve("builtin").isEmpty());
        assertEquals(xep, store.resolve("renderx xep").orElseThrow());
        assertEquals(xep, store.resolve(xep.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> store.resolve("Antenna"));
    }

    @Test
    void policyTurnsExternalProcessorsOff() {
        store.save(List.of(FoProcessorConfig.fromPreset(FoProcessorPreset.XEP)));
        allowed.set(false);
        assertTrue(store.list().isEmpty());
        assertTrue(store.resolve("builtin").isEmpty());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> store.resolve("RenderX XEP"));
        assertTrue(e.getMessage().contains("administrator"));
    }

    @Test
    void unreadableSettingIsIgnored() {
        settings.put(FoProcessorStore.KEY_PROCESSORS, "{not json");
        assertTrue(store.list().isEmpty());
    }
}
