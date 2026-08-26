package org.fxt.freexmltoolkit.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.util.PathValidator;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BundledSchemaLibraryTest {

    private JsonArray entries() throws Exception {
        try (var in = getClass().getResourceAsStream(SchemaLibraryServiceImpl.BUNDLED_RESOURCE)) {
            assertNotNull(in, "bundled.json missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("entries");
        }
    }

    @Test
    void entriesAreWellFormedUniqueAndHttps() throws Exception {
        JsonArray entries = entries();
        assertTrue(entries.size() >= 25, "expected the full bundled list, got " + entries.size());
        Set<String> keys = new HashSet<>();
        for (var el : entries) {
            JsonObject o = el.getAsJsonObject();
            String ns = o.get("namespace").getAsString();
            String loc = o.get("location").getAsString();
            SchemaKind kind = SchemaKind.valueOf(o.get("kind").getAsString());
            assertFalse(o.get("description").getAsString().isBlank(), "description missing for " + ns);
            assertTrue(loc.startsWith("https://"), "not https: " + loc);
            assertTrue(PathValidator.isUrlSafeToAccess(loc), "unsafe: " + loc);
            assertTrue(keys.add(kind + "|" + ns), "duplicate: " + kind + "|" + ns);
            if (ns.isEmpty()) assertTrue(o.has("rootElement"), "no-namespace entry needs rootElement: " + loc);
        }
    }

    @Test
    void x3dNamespacesArePresent() throws Exception {
        Set<String> ns = new HashSet<>();
        entries().forEach(e -> ns.add(e.getAsJsonObject().get("namespace").getAsString()));
        assertTrue(ns.stream().anyMatch(n -> n.contains("x3d-4.0")));
        assertTrue(ns.stream().anyMatch(n -> n.contains("x3d-3.3")));
    }
}
