package org.fxt.freexmltoolkit.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.util.PathValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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

    private SchemaLibraryServiceImpl service(Path dir) {
        return new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> SchemaLibraryServiceImpl.class.getResourceAsStream(SchemaLibraryServiceImpl.BUNDLED_RESOURCE));
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
            String rootElement = o.has("rootElement") ? o.get("rootElement").getAsString() : null;
            assertFalse(o.get("description").getAsString().isBlank(), "description missing for " + loc);
            assertTrue(loc.startsWith("https://"), "not https: " + loc);
            assertTrue(PathValidator.isUrlSafeToAccess(loc), "unsafe: " + loc);
            String key = new SchemaLibraryEntry("probe", ns, loc, kind,
                    org.fxt.freexmltoolkit.domain.EntrySource.BUNDLED, true, "d", rootElement).key();
            assertTrue(keys.add(key), "duplicate: " + key);
        }
    }

    /**
     * The JSON Schema meta-schemas must NOT be bundled: {@code EditorHost.detectJsonSchemaFor}
     * looks the raw {@code $schema} id up in the library first, so a bundled meta-schema entry
     * would bind every JSON <i>schema document</i> to the meta-schema — contradicting
     * {@link JsonService#getSchemaLocationFromJsonContent}'s rule that a dialect declaration is
     * not an instance binding. Users may still add them as USER entries.
     */
    @Test
    void jsonSchemaMetaSchemasAreNotBundled() throws Exception {
        for (var el : entries()) {
            JsonObject o = el.getAsJsonObject();
            assertNotEquals("JSON_SCHEMA", o.get("kind").getAsString(),
                    "meta-schema entries must not be bundled: " + o.get("location").getAsString());
        }
    }

    /**
     * X3D documents carry no namespace (they use {@code xsi:noNamespaceSchemaLocation}), so the
     * bundled X3D entries are no-namespace entries; 4.0 is listed first and therefore wins the
     * root-element lookup, and 3.3 is the second {@code X3D} rooted entry.
     */
    @Test
    void x3dEntriesAreNoNamespaceAndRootedOnX3d(@TempDir Path dir) throws Exception {
        for (var el : entries()) {
            JsonObject o = el.getAsJsonObject();
            if (!o.get("location").getAsString().contains("/x3d-")) continue;
            assertEquals("", o.get("namespace").getAsString(), "X3D entries must be no-namespace");
        }
        var svc = service(dir);
        SchemaLibraryEntry byRoot = svc.resolveByRootElement("X3D").orElseThrow();
        assertTrue(byRoot.location().contains("x3d-4.0.xsd"), "4.0 must win the root-element lookup: " + byRoot.location());
        assertEquals(2, svc.getEntries().stream()
                .filter(e -> "X3D".equals(e.rootElement())).count(), "4.0 and 3.3 carry rootElement X3D");
        assertTrue(svc.resolveSystemId("https://www.web3d.org/specifications/x3d-3.1.xsd", null).isPresent(),
                "older X3D versions must stay reachable by their location");
    }

    /**
     * Every bundled entry must actually be reachable — through its namespace, its root element
     * or its location as a system id — otherwise it is dead weight in the list.
     */
    @Test
    void everyBundledEntryIsReachable(@TempDir Path dir) {
        var svc = service(dir);
        for (SchemaLibraryEntry e : svc.getEntries()) {
            boolean reachable = (!e.namespace().isEmpty() && svc.resolveNamespace(e.namespace(), e.kind()).isPresent())
                    || (e.rootElement() != null && svc.resolveByRootElement(e.rootElement()).isPresent())
                    || svc.resolveSystemId(e.location(), null).isPresent();
            assertTrue(reachable, "unreachable bundled entry: " + e.location());
        }
    }
}
