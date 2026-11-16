package org.fxt.freexmltoolkit.service.xsltproc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

/** The processor list in the settings: round trip, default and last choice, resolve, policy. */
class XsltProcessorStoreTest {

    private final Map<String, String> settings = new HashMap<>();
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private final XsltProcessorStore store = new XsltProcessorStore(settings::get, settings::put, allowed::get);

    @Test
    void roundTripDefaultAndResolve() {
        XsltProcessorConfig saxon = XsltProcessorConfig.fromPreset(XsltProcessorPreset.SAXON);
        XsltProcessorConfig xsltproc = new XsltProcessorConfig("p2", "libxslt", XsltProcessorPreset.XSLTPROC, "/usr/bin/xsltproc",
                null, null, "", 30);
        store.save(List.of(saxon, xsltproc));
        assertEquals(List.of(saxon, xsltproc), store.list());
        assertEquals("--stringparam {name} {value}", store.list().get(1).parameterTemplate());
        assertEquals(XsltProcessorStore.BUILTIN_ID, store.defaultId());
        store.setDefaultId("p2");
        assertEquals("p2", store.lastId(), "the default until the panel chose");
        store.setLastId(saxon.id());
        assertEquals(saxon.id(), store.lastId());
        assertEquals("p2", store.resolve("LIBXSLT").orElseThrow().id(), "names ignore case");
        assertTrue(store.resolve("builtin").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> store.resolve("nope"));
        store.save(List.of(saxon));
        assertEquals(XsltProcessorStore.BUILTIN_ID, store.defaultId(), "a removed default falls back");

        allowed.set(false);
        assertEquals(List.of(), store.list());
        assertThrows(IllegalArgumentException.class, () -> store.resolve(saxon.name()));
    }

    @Test
    void validation() {
        assertNull(XsltProcessorConfig.fromPreset(XsltProcessorPreset.RAPTORXML).validate());
        assertNotNull(new XsltProcessorConfig(null, "x", null, "", "${xml} ${out}", null, "", 0).validate());
        assertNotNull(new XsltProcessorConfig(null, "x", null, "", "${xsl} ${out} ${nope}", null, "", 0).validate());
        assertNotNull(new XsltProcessorConfig(null, "x", null, "", "${xsl} ${out}", "{value}", "", 0).validate());
        assertTrue(XsltProcessorConfig.fromPreset(XsltProcessorPreset.XSLTPROC).supportsParameters());
    }
}
