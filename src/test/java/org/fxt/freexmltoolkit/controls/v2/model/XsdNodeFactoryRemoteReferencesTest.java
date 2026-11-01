package org.fxt.freexmltoolkit.controls.v2.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.fxt.freexmltoolkit.service.SchemaResourceCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schemas from the network resolve their relative references against their URL, not the cache. */
class XsdNodeFactoryRemoteReferencesTest {

    private static final String XS = "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";
    private static final String BASE = "http://example.org/schemas/";

    /** Serves schemas by URL from memory, under hashed file names like the real cache. */
    private static final class FakeCache extends SchemaResourceCache {
        final Map<String, String> remote = new HashMap<>();
        final Set<String> offlineOnly = new HashSet<>();
        final List<String> requests = new ArrayList<>();
        private final Path dir;

        FakeCache(Path dir) {
            super(dir);
            this.dir = dir;
        }

        @Override
        public boolean isCached(String url) {
            return remote.containsKey(url) && !offlineOnly.contains(url);
        }

        @Override
        public Path getOrDownload(String url, String referencingUrl) throws IOException {
            requests.add(url);
            String content = remote.get(url);
            if (content == null) {
                throw new IOException("404 for " + url);
            }
            Path file = dir.resolve(Integer.toHexString(url.hashCode()) + ".xsd");
            Files.writeString(file, content);
            return file;
        }
    }

    @TempDir
    Path dir;

    private FakeCache cache;
    private Path main;

    @BeforeEach
    void setUp() throws Exception {
        cache = new FakeCache(Files.createDirectories(dir.resolve("cache")));
        main = dir.resolve("main.xsd");
    }

    private XsdSchema load(boolean downloadsAllowed, String mainText) throws Exception {
        Files.writeString(main, mainText);
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setSchemaCache(cache);
        factory.setRemoteDownloadsAllowed(downloadsAllowed);
        factory.setRemoteNamespaceFallbackEnabled(false);
        lastFactory = factory;
        return factory.fromStringWithSchemaFile(mainText, main, dir);
    }

    private XsdNodeFactory lastFactory;

    private static String mainImporting(String url) {
        return "<xs:schema " + XS + "><xs:import namespace=\"urn:r\" schemaLocation=\"" + url + "\"/></xs:schema>";
    }

    private static String schema(String content) {
        return "<xs:schema " + XS + " targetNamespace=\"urn:r\">" + content + "</xs:schema>";
    }

    private static String typeNamed(String name) {
        return "<xs:complexType name=\"" + name + "\"/>";
    }

    private static boolean hasType(XsdSchema schema, String name) {
        return schema.getChildren().stream().anyMatch(n -> n instanceof XsdComplexType && name.equals(n.getName()));
    }

    private static XsdInclude includeOf(XsdSchema schema) {
        return schema.getChildren().stream().filter(XsdInclude.class::isInstance).map(XsdInclude.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    void relativeIncludeResolvesAgainstTheUrl() throws Exception {
        cache.remote.put(BASE + "gco.xsd", schema("<xs:include schemaLocation=\"basic.xsd\"/>"));
        cache.remote.put(BASE + "basic.xsd", schema(typeNamed("T")));

        XsdSchema imported = load(true, mainImporting(BASE + "gco.xsd")).getImportedSchemas().get("urn:r");

        assertTrue(hasType(imported, "T"));
        assertNull(includeOf(imported).getResolutionError());
    }

    @Test
    void nestedIncludeResolvesAgainstItsOwnUrl() throws Exception {
        cache.remote.put(BASE + "gco.xsd", schema("<xs:include schemaLocation=\"sub/a.xsd\"/>"));
        cache.remote.put(BASE + "sub/a.xsd", schema("<xs:include schemaLocation=\"b.xsd\"/>"));
        cache.remote.put(BASE + "sub/b.xsd", schema(typeNamed("Deep")));

        assertTrue(hasType(load(true, mainImporting(BASE + "gco.xsd")).getImportedSchemas().get("urn:r"), "Deep"));
    }

    @Test
    void relativeImportResolvesAgainstTheUrl() throws Exception {
        cache.remote.put(BASE + "gco.xsd", schema("<xs:import namespace=\"urn:o\" schemaLocation=\"other.xsd\"/>"));
        cache.remote.put(BASE + "other.xsd",
                "<xs:schema " + XS + " targetNamespace=\"urn:o\">" + typeNamed("O") + "</xs:schema>");

        assertTrue(hasType(load(true, mainImporting(BASE + "gco.xsd")).getImportedSchemas().get("urn:o"), "O"));
    }

    @Test
    void circularRemoteIncludesTerminate() throws Exception {
        cache.remote.put(BASE + "gco.xsd", schema("<xs:include schemaLocation=\"a.xsd\"/>"));
        cache.remote.put(BASE + "a.xsd", schema("<xs:include schemaLocation=\"b.xsd\"/>" + typeNamed("A")));
        cache.remote.put(BASE + "b.xsd", schema("<xs:include schemaLocation=\"a.xsd\"/>" + typeNamed("B")));

        XsdSchema imported = load(true, mainImporting(BASE + "gco.xsd")).getImportedSchemas().get("urn:r");

        assertTrue(hasType(imported, "A") && hasType(imported, "B"));
    }

    @Test
    void missingRemoteIncludeIsAFailureNamingTheReason() throws Exception {
        cache.remote.put(BASE + "gco.xsd", schema("<xs:include schemaLocation=\"gone.xsd\"/>"));

        XsdSchema imported = load(true, mainImporting(BASE + "gco.xsd")).getImportedSchemas().get("urn:r");

        assertTrue(includeOf(imported).getResolutionError().contains("404"), includeOf(imported).getResolutionError());
    }

    @Test
    void uncachedIncludeWaitsWhenDownloadsAreOff() throws Exception {
        cache.remote.put(BASE + "gco.xsd", schema("<xs:include schemaLocation=\"basic.xsd\"/>"));
        cache.remote.put(BASE + "basic.xsd", schema(typeNamed("T")));
        cache.offlineOnly.add(BASE + "basic.xsd");

        XsdSchema imported = load(false, mainImporting(BASE + "gco.xsd")).getImportedSchemas().get("urn:r");

        assertEquals("Remote schema not downloaded yet", includeOf(imported).getResolutionError());
        assertFalse(cache.requests.contains(BASE + "basic.xsd"), "nothing is downloaded");
        assertTrue(lastFactory.getDeferredRemoteLookups().contains(BASE + "basic.xsd"));
    }

    @Test
    void absoluteRemoteIncludeOfALocalSchemaLoads() throws Exception {
        cache.remote.put(BASE + "part.xsd", "<xs:schema " + XS + ">" + typeNamed("Part") + "</xs:schema>");

        XsdSchema schema = load(true, "<xs:schema " + XS + "><xs:include schemaLocation=\"" + BASE + "part.xsd\"/></xs:schema>");

        assertTrue(hasType(schema, "Part"));
    }

    @Test
    void uncachedRemoteIncludeOfALocalSchemaIsDeferred() throws Exception {
        cache.remote.put(BASE + "part.xsd", "<xs:schema " + XS + "/>");
        cache.offlineOnly.add(BASE + "part.xsd");

        load(false, "<xs:schema " + XS + "><xs:include schemaLocation=\"" + BASE + "part.xsd\"/></xs:schema>");

        assertTrue(lastFactory.getDeferredRemoteLookups().contains(BASE + "part.xsd"));
    }

    @Test
    void aRemoteSchemaCannotReachLocalFiles() throws Exception {
        Path secret = dir.resolve("secret.xsd");
        Files.writeString(secret, schema(typeNamed("Secret")));
        String url = secret.toUri().toString();
        cache.remote.put(BASE + "gco.xsd", schema("<xs:include schemaLocation=\"" + url + "\"/>"
                + "<xs:import namespace=\"urn:o\" schemaLocation=\"" + url + "\"/>"));

        XsdSchema root = load(true, mainImporting(BASE + "gco.xsd"));
        XsdSchema imported = root.getImportedSchemas().get("urn:r");

        assertFalse(hasType(imported, "Secret"));
        assertTrue(includeOf(imported).getResolutionError().contains("http(s)"));
        assertNull(root.getImportedSchemas().get("urn:o"), "the import is refused too");
    }

    @Test
    void localSchemasStillResolveAgainstTheirFolder() throws Exception {
        Files.writeString(dir.resolve("part.xsd"), "<xs:schema " + XS + ">" + typeNamed("Local") + "</xs:schema>");

        assertTrue(hasType(load(true, "<xs:schema " + XS + "><xs:include schemaLocation=\"part.xsd\"/></xs:schema>"), "Local"));
        assertEquals(List.of(), cache.requests);
    }
}
