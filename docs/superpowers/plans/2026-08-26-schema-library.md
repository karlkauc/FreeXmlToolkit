# Schema Library, XML Catalog Support and Schema Cache UI — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A persistent namespace→schema library (user entries + OASIS XML catalogs + bundled standards) that every schema-resolution path consults first, plus a Schema Library activity with a UI for mappings, catalogs and the remote schema cache.

**Architecture:** A new `SchemaLibraryService` singleton (JSON persistence in `~/.freeXmlToolkit/schema-library.json`, own OASIS catalog parser) is placed as a facade *in front of* the four existing resolvers (Xerces/Saxon `LSResourceResolver`, V2 `XsdNodeFactory`, legacy `SchemaResolver`, Saxon `ResourceResolver`) and the `EditorHost` auto-binding. `SchemaResourceCache` becomes a registry singleton with list/remove/refresh so the new panel can manage it. No existing resolution logic is rewritten.

**Tech Stack:** Java 25 (preview), JavaFX 25 / AtlantaFX, Gson, StAX, JUnit 5 + Mockito, TestFX (Monocle headless), Gradle (Kotlin DSL).

**Spec:** `docs/superpowers/specs/2026-08-26-schema-library-design.md`

## Global Constraints

- All user-facing text in English; conversation with the user in German.
- All model/UI code follows `CLAUDE.md` + `STYLE_GUIDE.jsonc`; icons only via `IconifyIcon` with `bi-*` literals (`IconifyIconCoverageTest` must stay green).
- Never block the FX thread: disk/network work on `FxtGui.executorService`, UI updates via `Platform.runLater`.
- Every URL that may be fetched passes `PathValidator.isUrlSafeToAccess(String)`; catalog loading never touches the network.
- Existing behaviour of the four resolvers must be unchanged on a library miss (all existing tests keep passing).
- Tests never write to the real `~/.freeXmlToolkit`; use `@TempDir` and the constructors with explicit paths introduced below. Do not edit sources while a Gradle run is in progress.
- Run single test classes with the exact class name: `./gradlew test --tests "org.fxt...ClassName"` (a leading `*` wildcard forks a JVM per class and is very slow).
- Commit after each task (`git add <files> && git commit`), message prefix `feat(schema-library):`, `test(...)`, `docs(...)`.

## File map

| File | Responsibility |
|---|---|
| `domain/SchemaKind.java`, `domain/EntrySource.java`, `domain/SchemaLibraryEntry.java`, `domain/SchemaCatalogRef.java`, `domain/SchemaEntryStatus.java` | Immutable domain records/enums |
| `service/SchemaLibraryService.java` | Interface consumed by resolvers, EditorHost and UI |
| `service/SchemaLibraryServiceImpl.java` | Persistence, bundled merge, resolution order, materialize, status |
| `service/SchemaLibraryFile.java` | Gson DTO for `schema-library.json` |
| `service/catalog/SchemaCatalogParser.java`, `service/catalog/ParsedCatalog.java` | OASIS catalog parsing + matching (no network) |
| `service/XmlRootElementSniffer.java` | StAX sniff of the document element (namespace + local name) |
| `service/SchemaResourceCache.java` (modify) | Instance cache dir, `listEntries/removeEntry/refresh/pathOf`, registry singleton |
| `service/XmlService(Impl).java` (modify) | Legacy auto-detected cache listing/clearing |
| `service/xsd/SchemaResolver.java`, `controls/v2/model/XsdNodeFactory.java`, `controls/v2/model/ImportResolutionContext.java`, `service/XsltTransformationEngine.java` (modify) | Library hooks |
| `controls/shell/editor/EditorHost.java` (modify) | Auto-binding via library (XML + JSON) |
| `service/PropertiesService(Impl).java` (modify) | `schemaLibrary.autoBind.enabled` toggle |
| `controls/shell/Activity.java`, `controls/shell/UnifiedShellView.java` (modify) | New activity + panel wiring |
| `controls/shell/editor/SchemaLibraryPanel.java` | Side panel (TabPane: Mappings / Catalogs / Cache) |
| `controls/shell/editor/SchemaLibraryEntryDialog.java` | Add/Edit dialog |
| `controls/shell/editor/CatalogImportDialog.java` | Preview + select catalog entries to import |
| `controls/shell/editor/SettingsPanel.java` (modify) | SCHEMA LIBRARY card + "Manage schema cache…" link |
| `src/main/resources/schema-library/bundled.json` | Bundled namespace→URL list |
| `docs/schema-library.md` (+ nav, cross-links) | User documentation |

---

### Task 1: Domain types

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/domain/SchemaKind.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/domain/EntrySource.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/domain/SchemaLibraryEntry.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/domain/SchemaCatalogRef.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/domain/SchemaEntryStatus.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/domain/SchemaLibraryEntryTest.java`

**Interfaces:**
- Produces: `SchemaLibraryEntry(String id, String namespace, String location, SchemaKind kind, EntrySource source, boolean enabled, String description, String rootElement)` with factory `SchemaLibraryEntry.user(namespace, location, kind, description, rootElement)`, `withEnabled(boolean)`, `withSource(EntrySource)`, `isRemote()`, `key()` (= `kind + "|" + namespace`); `SchemaCatalogRef(String id, String path, boolean enabled)` with `SchemaCatalogRef.of(Path)`, `withEnabled(boolean)`; `SchemaEntryStatus` enum `LOCAL_OK, LOCAL_MISSING, CACHED, NOT_DOWNLOADED, ERROR`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLibraryEntryTest {

    @Test
    void userFactoryAssignsIdAndUserSource() {
        SchemaLibraryEntry e = SchemaLibraryEntry.user("urn:x", "/tmp/x.xsd", SchemaKind.XSD, "desc", null);
        assertNotNull(e.id());
        assertEquals(EntrySource.USER, e.source());
        assertTrue(e.enabled());
        assertEquals("XSD|urn:x", e.key());
        assertFalse(e.isRemote());
    }

    @Test
    void remoteDetectionAndWithers() {
        SchemaLibraryEntry e = SchemaLibraryEntry.user("urn:x", "https://example.org/x.xsd", SchemaKind.XSD, "", null);
        assertTrue(e.isRemote());
        SchemaLibraryEntry disabled = e.withEnabled(false);
        assertEquals(e.id(), disabled.id());
        assertFalse(disabled.enabled());
        assertEquals(EntrySource.BUNDLED, e.withSource(EntrySource.BUNDLED).source());
    }

    @Test
    void nullNamespaceBecomesEmptyString() {
        SchemaLibraryEntry e = SchemaLibraryEntry.user(null, "/tmp/x.xsd", SchemaKind.XSD, null, "root");
        assertEquals("", e.namespace());
        assertEquals("", e.description());
        assertEquals("root", e.rootElement());
    }

    @Test
    void catalogRefOfPath() {
        SchemaCatalogRef ref = SchemaCatalogRef.of(java.nio.file.Path.of("/tmp/catalog.xml"));
        assertNotNull(ref.id());
        assertTrue(ref.enabled());
        assertTrue(ref.path().endsWith("catalog.xml"));
        assertFalse(ref.withEnabled(false).enabled());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.domain.SchemaLibraryEntryTest"`
Expected: compilation FAIL (classes missing).

- [ ] **Step 3: Implement the domain types**

`SchemaKind.java`:
```java
package org.fxt.freexmltoolkit.domain;

/** Kind of schema a {@link SchemaLibraryEntry} points to. */
public enum SchemaKind {
    XSD("XSD"), JSON_SCHEMA("JSON Schema"), DTD("DTD");

    private final String label;

    SchemaKind(String label) { this.label = label; }

    public String label() { return label; }
}
```

`EntrySource.java`:
```java
package org.fxt.freexmltoolkit.domain;

/** Where a {@link SchemaLibraryEntry} comes from. */
public enum EntrySource { USER, BUNDLED, CATALOG }
```

`SchemaEntryStatus.java`:
```java
package org.fxt.freexmltoolkit.domain;

/** Availability of the schema file behind a library entry (computed without network access). */
public enum SchemaEntryStatus { LOCAL_OK, LOCAL_MISSING, CACHED, NOT_DOWNLOADED, ERROR }
```

`SchemaLibraryEntry.java`:
```java
package org.fxt.freexmltoolkit.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One namespace → schema mapping of the Schema Library.
 *
 * @param id          stable UUID
 * @param namespace   XSD target namespace or JSON {@code $schema}/{@code $id} URI; empty for no-namespace schemas
 * @param location    absolute local path or http(s) URL
 * @param kind        schema kind
 * @param source      origin of the entry
 * @param enabled     disabled entries are ignored by resolution
 * @param description free text
 * @param rootElement local name of the document element for no-namespace auto-binding, may be null
 */
public record SchemaLibraryEntry(String id, String namespace, String location, SchemaKind kind,
                                 EntrySource source, boolean enabled, String description, String rootElement) {

    public SchemaLibraryEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        namespace = namespace == null ? "" : namespace.trim();
        description = description == null ? "" : description;
        rootElement = rootElement == null || rootElement.isBlank() ? null : rootElement.trim();
    }

    /** Creates a new, enabled USER entry with a fresh id. */
    public static SchemaLibraryEntry user(String namespace, String location, SchemaKind kind,
                                          String description, String rootElement) {
        return new SchemaLibraryEntry(UUID.randomUUID().toString(), namespace, location, kind,
                EntrySource.USER, true, description, rootElement);
    }

    /** {@code kind|namespace} — the identity used for bundled overrides. */
    public String key() { return kind + "|" + namespace; }

    public boolean isRemote() {
        return location.startsWith("http://") || location.startsWith("https://");
    }

    public SchemaLibraryEntry withEnabled(boolean value) {
        return new SchemaLibraryEntry(id, namespace, location, kind, source, value, description, rootElement);
    }

    public SchemaLibraryEntry withSource(EntrySource value) {
        return new SchemaLibraryEntry(id, namespace, location, kind, value, enabled, description, rootElement);
    }
}
```

`SchemaCatalogRef.java`:
```java
package org.fxt.freexmltoolkit.domain;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** A registered OASIS XML catalog file. */
public record SchemaCatalogRef(String id, String path, boolean enabled) {

    public SchemaCatalogRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(path, "path");
    }

    public static SchemaCatalogRef of(Path file) {
        return new SchemaCatalogRef(UUID.randomUUID().toString(), file.toAbsolutePath().toString(), true);
    }

    public SchemaCatalogRef withEnabled(boolean value) {
        return new SchemaCatalogRef(id, path, value);
    }

    public Path asPath() { return Path.of(path); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.domain.SchemaLibraryEntryTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/domain/SchemaKind.java src/main/java/org/fxt/freexmltoolkit/domain/EntrySource.java src/main/java/org/fxt/freexmltoolkit/domain/SchemaLibraryEntry.java src/main/java/org/fxt/freexmltoolkit/domain/SchemaCatalogRef.java src/main/java/org/fxt/freexmltoolkit/domain/SchemaEntryStatus.java src/test/java/org/fxt/freexmltoolkit/domain/SchemaLibraryEntryTest.java
git commit -m "feat(schema-library): domain types for library entries and catalogs"
```

---

### Task 2: OASIS catalog parser and matcher

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/service/catalog/ParsedCatalog.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/service/catalog/SchemaCatalogParser.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/catalog/SchemaCatalogParserTest.java`

**Interfaces:**
- Produces: `ParsedCatalog` (record) with `Path file`, `List<Entry> entries`, `List<ParsedCatalog> next`, `Optional<String> matchSystem(String systemId)`, `Optional<String> matchUri(String uri)`, `Optional<String> matchPublic(String publicId)`, `int entryCount()` (recursive, excludes nextCatalog entries), `List<Entry> allEntries()` (recursive, flattened); `ParsedCatalog.Entry(EntryType type, String key, String target)` with `enum EntryType { SYSTEM, PUBLIC, URI, REWRITE_SYSTEM, REWRITE_URI }`; `SchemaCatalogParser.parse(Path catalog) throws CatalogParseException` (checked, extends `IOException`), `MAX_DEPTH = 10`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.service.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaCatalogParserTest {

    private static final String NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";

    private Path write(Path dir, String name, String body) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, body);
        return f;
    }

    @Test
    void parsesSystemUriPublicAndRewriteEntries(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("x3d.xsd"), "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s">
                  <system systemId="https://www.web3d.org/specifications/x3d-4.0.xsd" uri="x3d.xsd"/>
                  <uri name="http://www.web3d.org/specifications/x3d-namespace" uri="x3d.xsd"/>
                  <public publicId="-//Web3D//DTD X3D 4.0//EN" uri="x3d.xsd"/>
                  <rewriteSystem systemIdStartString="http://example.org/schemas/" rewritePrefix="schemas/"/>
                  <rewriteURI uriStartString="http://example.org/uris/" rewritePrefix="file:///opt/uris/"/>
                </catalog>
                """.formatted(NS));
        ParsedCatalog c = SchemaCatalogParser.parse(cat);

        String expected = dir.resolve("x3d.xsd").toUri().toString();
        assertEquals(expected, c.matchSystem("https://www.web3d.org/specifications/x3d-4.0.xsd").orElseThrow());
        assertEquals(expected, c.matchUri("http://www.web3d.org/specifications/x3d-namespace").orElseThrow());
        assertEquals(expected, c.matchPublic("-//Web3D//DTD X3D 4.0//EN").orElseThrow());
        assertEquals(dir.resolve("schemas/a/b.xsd").toUri().toString(),
                c.matchSystem("http://example.org/schemas/a/b.xsd").orElseThrow());
        assertEquals("file:///opt/uris/q.xsd", c.matchUri("http://example.org/uris/q.xsd").orElseThrow());
        assertTrue(c.matchSystem("http://nowhere/").isEmpty());
        assertEquals(5, c.entryCount());
    }

    @Test
    void longestRewritePrefixWins(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s">
                  <rewriteSystem systemIdStartString="http://e.org/" rewritePrefix="file:///short/"/>
                  <rewriteSystem systemIdStartString="http://e.org/deep/" rewritePrefix="file:///long/"/>
                </catalog>
                """.formatted(NS));
        assertEquals("file:///long/x.xsd",
                SchemaCatalogParser.parse(cat).matchSystem("http://e.org/deep/x.xsd").orElseThrow());
    }

    @Test
    void followsNextCatalogAndSurvivesCycles(@TempDir Path dir) throws Exception {
        Path a = write(dir, "a.xml", """
                <catalog xmlns="%s">
                  <nextCatalog catalog="b.xml"/>
                </catalog>
                """.formatted(NS));
        write(dir, "b.xml", """
                <catalog xmlns="%s">
                  <system systemId="urn:b" uri="b.xsd"/>
                  <nextCatalog catalog="a.xml"/>
                </catalog>
                """.formatted(NS));
        ParsedCatalog c = SchemaCatalogParser.parse(a);
        assertEquals(dir.resolve("b.xsd").toUri().toString(), c.matchSystem("urn:b").orElseThrow());
        assertEquals(0, c.entryCount());
        assertEquals(1, c.allEntries().size());
    }

    @Test
    void honoursXmlBase(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s" xml:base="sub/">
                  <system systemId="urn:s" uri="s.xsd"/>
                </catalog>
                """.formatted(NS));
        assertEquals(dir.resolve("sub/s.xsd").toUri().toString(),
                SchemaCatalogParser.parse(cat).matchSystem("urn:s").orElseThrow());
    }

    @Test
    void unparsableCatalogThrows(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", "<catalog><system");
        assertThrows(SchemaCatalogParser.CatalogParseException.class, () -> SchemaCatalogParser.parse(cat));
    }

    @Test
    void missingNextCatalogIsIgnored(@TempDir Path dir) throws Exception {
        Path cat = write(dir, "catalog.xml", """
                <catalog xmlns="%s"><nextCatalog catalog="missing.xml"/><uri name="u" uri="u.xsd"/></catalog>
                """.formatted(NS));
        assertEquals(1, SchemaCatalogParser.parse(cat).entryCount());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.catalog.SchemaCatalogParserTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Implement `ParsedCatalog`**

```java
package org.fxt.freexmltoolkit.service.catalog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An immutable, parsed OASIS XML catalog (plus its {@code nextCatalog} chain).
 * All targets are absolute URI strings; matching never touches the network.
 */
public record ParsedCatalog(Path file, List<Entry> entries, List<ParsedCatalog> next) {

    public enum EntryType { SYSTEM, PUBLIC, URI, REWRITE_SYSTEM, REWRITE_URI }

    /** One catalog entry: {@code key} is the systemId/publicId/uri/prefix, {@code target} the absolute URI. */
    public record Entry(EntryType type, String key, String target) { }

    public ParsedCatalog {
        entries = List.copyOf(entries);
        next = List.copyOf(next);
    }

    public Optional<String> matchSystem(String systemId) {
        return match(systemId, EntryType.SYSTEM, EntryType.REWRITE_SYSTEM);
    }

    public Optional<String> matchUri(String uri) {
        return match(uri, EntryType.URI, EntryType.REWRITE_URI);
    }

    public Optional<String> matchPublic(String publicId) {
        if (publicId == null) return Optional.empty();
        for (Entry e : entries) {
            if (e.type() == EntryType.PUBLIC && e.key().equals(publicId)) return Optional.of(e.target());
        }
        for (ParsedCatalog n : next) {
            Optional<String> r = n.matchPublic(publicId);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    /** Number of entries in this file only (nextCatalog chain excluded). */
    public int entryCount() { return entries.size(); }

    /** All entries of this catalog and its nextCatalog chain, depth-first. */
    public List<Entry> allEntries() {
        List<Entry> all = new ArrayList<>(entries);
        next.forEach(n -> all.addAll(n.allEntries()));
        return all;
    }

    private Optional<String> match(String id, EntryType exact, EntryType rewrite) {
        if (id == null || id.isBlank()) return Optional.empty();
        for (Entry e : entries) {
            if (e.type() == exact && e.key().equals(id)) return Optional.of(e.target());
        }
        Entry best = null;
        for (Entry e : entries) {
            if (e.type() == rewrite && id.startsWith(e.key())
                    && (best == null || e.key().length() > best.key().length())) {
                best = e;
            }
        }
        if (best != null) return Optional.of(best.target() + id.substring(best.key().length()));
        for (ParsedCatalog n : next) {
            Optional<String> r = n.match(id, exact, rewrite);
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Implement `SchemaCatalogParser`**

```java
package org.fxt.freexmltoolkit.service.catalog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses OASIS XML catalogs ({@code system}, {@code public}, {@code uri}, {@code rewriteSystem},
 * {@code rewriteURI}, {@code nextCatalog}, {@code xml:base}) with StAX. Never accesses the network;
 * {@code nextCatalog} is followed for local files only, with a depth cap and cycle protection.
 */
public final class SchemaCatalogParser {

    private static final Logger logger = LogManager.getLogger(SchemaCatalogParser.class);
    public static final int MAX_DEPTH = 10;
    private static final String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";

    /** Thrown when a catalog file cannot be read or is not well-formed. */
    public static class CatalogParseException extends IOException {
        public CatalogParseException(String message, Throwable cause) { super(message, cause); }
    }

    private SchemaCatalogParser() { }

    public static ParsedCatalog parse(Path catalog) throws CatalogParseException {
        return parse(catalog.toAbsolutePath().normalize(), 0, new HashSet<>());
    }

    private static ParsedCatalog parse(Path catalog, int depth, Set<Path> visited) throws CatalogParseException {
        visited.add(catalog);
        List<ParsedCatalog.Entry> entries = new ArrayList<>();
        List<Path> nextFiles = new ArrayList<>();
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(catalog)) {
            XMLStreamReader r = factory.createXMLStreamReader(in);
            Deque<URI> bases = new ArrayDeque<>();
            bases.push(catalog.toUri());
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String xmlBase = r.getAttributeValue(XMLConstants.XML_NS_URI, "base");
                    URI base = xmlBase != null ? bases.peek().resolve(xmlBase) : bases.peek();
                    bases.push(base);
                    if (!CATALOG_NS.equals(r.getNamespaceURI()) && r.getNamespaceURI() != null
                            && !r.getNamespaceURI().isEmpty()) {
                        continue;
                    }
                    switch (r.getLocalName()) {
                        case "system" -> add(entries, ParsedCatalog.EntryType.SYSTEM,
                                r.getAttributeValue(null, "systemId"), r.getAttributeValue(null, "uri"), base);
                        case "public" -> add(entries, ParsedCatalog.EntryType.PUBLIC,
                                r.getAttributeValue(null, "publicId"), r.getAttributeValue(null, "uri"), base);
                        case "uri" -> add(entries, ParsedCatalog.EntryType.URI,
                                r.getAttributeValue(null, "name"), r.getAttributeValue(null, "uri"), base);
                        case "rewriteSystem" -> add(entries, ParsedCatalog.EntryType.REWRITE_SYSTEM,
                                r.getAttributeValue(null, "systemIdStartString"),
                                r.getAttributeValue(null, "rewritePrefix"), base);
                        case "rewriteURI" -> add(entries, ParsedCatalog.EntryType.REWRITE_URI,
                                r.getAttributeValue(null, "uriStartString"),
                                r.getAttributeValue(null, "rewritePrefix"), base);
                        case "nextCatalog" -> {
                            String href = r.getAttributeValue(null, "catalog");
                            if (href != null && !href.isBlank()) {
                                URI resolved = base.resolve(href);
                                if ("file".equalsIgnoreCase(resolved.getScheme())) {
                                    nextFiles.add(Path.of(resolved).toAbsolutePath().normalize());
                                } else {
                                    logger.warn("Ignoring non-local nextCatalog '{}' in {}", href, catalog);
                                }
                            }
                        }
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    bases.pop();
                }
            }
            r.close();
        } catch (IOException | XMLStreamException | RuntimeException e) {
            throw new CatalogParseException("Cannot parse catalog " + catalog + ": " + e.getMessage(), e);
        }
        List<ParsedCatalog> next = new ArrayList<>();
        for (Path nf : nextFiles) {
            if (visited.contains(nf)) {
                logger.debug("Skipping already visited nextCatalog {}", nf);
                continue;
            }
            if (depth + 1 >= MAX_DEPTH) {
                logger.warn("nextCatalog depth cap ({}) reached at {}", MAX_DEPTH, nf);
                continue;
            }
            if (!Files.isRegularFile(nf)) {
                logger.warn("nextCatalog '{}' referenced from {} does not exist", nf, catalog);
                continue;
            }
            try {
                next.add(parse(nf, depth + 1, visited));
            } catch (CatalogParseException e) {
                logger.warn("Skipping unparsable nextCatalog {}: {}", nf, e.getMessage());
            }
        }
        return new ParsedCatalog(catalog, entries, next);
    }

    private static void add(List<ParsedCatalog.Entry> entries, ParsedCatalog.EntryType type,
                            String key, String target, URI base) {
        if (key == null || key.isBlank() || target == null || target.isBlank()) return;
        String absolute;
        try {
            absolute = base.resolve(target).toString();
        } catch (IllegalArgumentException e) {
            absolute = target;
        }
        entries.add(new ParsedCatalog.Entry(type, key, absolute));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.catalog.SchemaCatalogParserTest"`
Expected: PASS (6 tests). If `honoursXmlBase` fails because `xml:base="sub/"` resolves differently, ensure the base for the root element is pushed *after* applying its own `xml:base` (the code above does that).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/catalog src/test/java/org/fxt/freexmltoolkit/service/catalog
git commit -m "feat(schema-library): OASIS XML catalog parser and matcher"
```

---

### Task 3: `SchemaResourceCache` — instance directory, listing, removal, refresh, registry singleton

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/SchemaResourceCache.java` (fields at lines 74–79, ctor at 95, `getOrDownload` 149–200, `findCachedByTargetNamespace` 392, `clearCache` 432, `getStats` 465, `getCacheDirectory` 497, `loadExistingCache` ~551)
- Modify: `src/main/java/org/fxt/freexmltoolkit/di/ServiceRegistry.java:111` (add registration)
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolver.java:955`, `src/main/java/org/fxt/freexmltoolkit/controls/v2/model/ImportResolutionContext.java` (`schemaCache()`), `src/main/java/org/fxt/freexmltoolkit/service/NamespaceSchemaDownloader.java:87`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/SchemaResourceCacheTest.java` (add nested class)

**Interfaces:**
- Produces: `SchemaResourceCache(Path cacheDir)` (public, test seam; the no-arg ctor delegates to the default dir), `static SchemaResourceCache getInstance()`, `List<SchemaCacheEntry> listEntries()`, `boolean removeEntry(String localFilename)`, `Optional<Path> refresh(String url)`, `Path pathOf(SchemaCacheEntry entry)`, `Optional<SchemaCacheEntry> entryForUrl(String url)`.

- [ ] **Step 1: Write the failing tests** (append to `SchemaResourceCacheTest`)

```java
    @Nested
    @DisplayName("Entry management")
    class EntryManagement {

        @Test
        void listsAndRemovesEntries(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
            SchemaResourceCache c = new SchemaResourceCache(dir);
            java.nio.file.Path f = dir.resolve("abc.xsd");
            java.nio.file.Files.writeString(f, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:t'/>");
            c.getCacheIndex().addOrUpdateEntry(SchemaCacheEntry.builder()
                    .localFilename("abc.xsd").remoteUrl("https://example.org/abc.xsd")
                    .downloadTimestamp(java.time.Instant.now()).fileSizeBytes(10).build());
            c.saveIndex();

            assertEquals(1, c.listEntries().size());
            assertEquals(f, c.pathOf(c.listEntries().getFirst()));
            assertTrue(c.entryForUrl("https://example.org/abc.xsd").isPresent());

            assertTrue(c.removeEntry("abc.xsd"));
            assertFalse(java.nio.file.Files.exists(f));
            assertTrue(c.listEntries().isEmpty());
            assertFalse(c.removeEntry("abc.xsd"));
        }

        @Test
        void separateDirectoriesAreIsolated(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            SchemaResourceCache c = new SchemaResourceCache(dir.resolve("sub"));
            assertEquals(dir.resolve("sub"), c.getCacheDirectory());
            assertTrue(c.listEntries().isEmpty());
        }

        @Test
        void refreshOfUnsafeUrlIsEmpty(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            assertTrue(new SchemaResourceCache(dir).refresh("http://127.0.0.1/x.xsd").isEmpty());
        }
    }
```
Check the exact builder method names in `SchemaCacheEntry.builder()` before running (`localFilename`, `remoteUrl`, `downloadTimestamp`, `fileSizeBytes` — adjust to the real names if they differ).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.SchemaResourceCacheTest"`
Expected: compilation FAIL (no `Path` ctor).

- [ ] **Step 3: Refactor the cache**

In `SchemaResourceCache`:
1. Replace the two static finals with instance fields:
```java
    private static final Path DEFAULT_CACHE_DIR = Path.of(
            FileUtils.getUserDirectory().getAbsolutePath(), ".freeXmlToolkit", "cache", "schemas");
    private final Path cacheDir;
    private final Path indexFile;
```
2. Constructors:
```java
    public SchemaResourceCache() { this(DEFAULT_CACHE_DIR); }

    /** Creates a cache rooted at {@code cacheDir} (tests and tools). */
    public SchemaResourceCache(Path cacheDir) {
        this.cacheDir = cacheDir.toAbsolutePath().normalize();
        this.indexFile = this.cacheDir.resolve("cache-index.json");
        ... (existing HttpClient setup, directory creation, index load, loadExistingCache — replace every CACHE_DIR/INDEX_FILE usage with the fields)
    }

    private static final class Holder { static final SchemaResourceCache INSTANCE = new SchemaResourceCache(); }

    /** The application-wide cache instance (lazy). */
    public static SchemaResourceCache getInstance() { return Holder.INSTANCE; }
```
3. Replace all remaining `CACHE_DIR` → `cacheDir`, `INDEX_FILE` → `indexFile` (`grep -n "CACHE_DIR\|INDEX_FILE"` must be empty afterwards).
4. New methods:
```java
    /** Snapshot of all index entries, newest download first. */
    public synchronized List<SchemaCacheEntry> listEntries() {
        return cacheIndex.getEntries().values().stream()
                .sorted(java.util.Comparator.comparing(SchemaCacheEntry::downloadTimestamp,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    public Path pathOf(SchemaCacheEntry entry) { return cacheDir.resolve(entry.localFilename()); }

    public synchronized Optional<SchemaCacheEntry> entryForUrl(String url) {
        return Optional.ofNullable(cacheIndex.getEntryByUrl(url));
    }

    /** Deletes the cached file and its index entry. @return false if no such entry exists. */
    public synchronized boolean removeEntry(String localFilename) {
        SchemaCacheEntry entry = cacheIndex.getEntry(localFilename);
        if (entry == null) return false;
        try {
            Files.deleteIfExists(cacheDir.resolve(localFilename));
        } catch (IOException e) {
            logger.warn("Could not delete cached schema {}: {}", localFilename, e.getMessage());
        }
        urlToLocalPath.values().removeIf(p -> p.getFileName().toString().equals(localFilename));
        cacheIndex.removeEntry(localFilename);
        saveIndex();
        return true;
    }

    /** Re-downloads {@code url}, replacing the cached copy. Empty on unsafe URL or download failure. */
    public Optional<Path> refresh(String url) {
        if (!PathValidator.isUrlSafeToAccess(url)) return Optional.empty();
        removeEntry(generateFilename(url));
        try {
            return Optional.of(getOrDownload(url));
        } catch (IOException e) {
            logger.warn("Refresh failed for {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
```
Check `SchemaCacheIndex` for `getEntries()`; if the accessor is named differently (e.g. `getAllEntries()`), use that.

5. Registry: in `ServiceRegistry.initialize()` after item 11 add
```java
        // 12. SchemaResourceCache - shared on-disk cache for remote schemas
        registerFactory(SchemaResourceCache.class, SchemaResourceCache::getInstance);
```
6. Call sites: `SchemaResolver.java:955` → `this.cache = org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.SchemaResourceCache.class);`; `ImportResolutionContext.schemaCache()` → `schemaCache = ServiceRegistry.get(SchemaResourceCache.class);`; `NamespaceSchemaDownloader()` → `this(ServiceRegistry.get(ConnectionService.class), ServiceRegistry.get(SchemaResourceCache.class));`. Because `ServiceRegistry.get` throws if not initialized, guard the two non-service call sites with a fallback: create a small package-private helper in `SchemaResourceCache`:
```java
    /** Registry instance when the registry is initialized, else the lazy singleton. */
    public static SchemaResourceCache shared() {
        return org.fxt.freexmltoolkit.di.ServiceRegistry.isRegistered(SchemaResourceCache.class)
                ? org.fxt.freexmltoolkit.di.ServiceRegistry.get(SchemaResourceCache.class)
                : getInstance();
    }
```
and use `SchemaResourceCache.shared()` at all three call sites.

- [ ] **Step 4: Run the cache, resolver and downloader tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.SchemaResourceCacheTest" --tests "org.fxt.freexmltoolkit.service.NamespaceSchemaDownloaderTest" --tests "org.fxt.freexmltoolkit.service.SchemaCacheIndexTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/SchemaResourceCache.java src/main/java/org/fxt/freexmltoolkit/di/ServiceRegistry.java src/main/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolver.java src/main/java/org/fxt/freexmltoolkit/controls/v2/model/ImportResolutionContext.java src/main/java/org/fxt/freexmltoolkit/service/NamespaceSchemaDownloader.java src/test/java/org/fxt/freexmltoolkit/service/SchemaResourceCacheTest.java
git commit -m "feat(schema-library): shared SchemaResourceCache with entry listing, removal and refresh"
```

---

### Task 4: `SchemaLibraryService` — persistence, CRUD, bundled merge

**Files:**
- Create: `src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryService.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryFile.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java`
- Create: `src/main/resources/schema-library/bundled.json` (minimal; the full list is Task 17)
- Modify: `src/main/java/org/fxt/freexmltoolkit/di/ServiceRegistry.java` (registration 13)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImplTest.java`

**Interfaces:**
- Consumes: Task 1 records; `SchemaResourceCache.shared()`.
- Produces (interface `SchemaLibraryService`, all methods thread-safe):
```java
ObservableList<SchemaLibraryEntry> getEntries();            // read-only merged view (USER + BUNDLED)
List<SchemaCatalogRef> getCatalogs();
SchemaLibraryEntry addEntry(SchemaLibraryEntry entry);       // returns stored entry (source forced to USER)
boolean updateEntry(SchemaLibraryEntry entry);               // by id, USER only
boolean removeEntry(String id);                              // USER only
boolean setEnabled(String id, boolean enabled);              // USER or BUNDLED
SchemaCatalogRef addCatalog(Path catalogFile);
boolean removeCatalog(String id);
boolean setCatalogEnabled(String id, boolean enabled);
void reloadCatalogs();
Path getStorageFile();
```
  plus `SchemaLibraryServiceImpl(Path storageFile, SchemaResourceCache cache, java.util.function.Supplier<java.io.InputStream> bundledSource)` and `static SchemaLibraryServiceImpl getInstance()`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLibraryServiceImplTest {

    static final String BUNDLED = """
            {"version":1,"entries":[
              {"namespace":"http://www.w3.org/XML/1998/namespace","location":"https://www.w3.org/2001/xml.xsd",
               "kind":"XSD","description":"XML namespace"},
              {"namespace":"urn:bundled:b","location":"https://example.org/b.xsd","kind":"XSD","description":"B"}
            ]}""";

    @TempDir Path dir;
    SchemaResourceCache cache;
    SchemaLibraryServiceImpl svc;

    @BeforeEach
    void setUp() {
        cache = new SchemaResourceCache(dir.resolve("cache"));
        svc = newService();
    }

    SchemaLibraryServiceImpl newService() {
        return new SchemaLibraryServiceImpl(dir.resolve("schema-library.json"), cache,
                () -> new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void startsWithBundledEntriesOnly() {
        assertEquals(2, svc.getEntries().size());
        assertTrue(svc.getEntries().stream().allMatch(e -> e.source() == EntrySource.BUNDLED));
        assertFalse(Files.exists(svc.getStorageFile()));
    }

    @Test
    void addPersistsAndReloads() throws Exception {
        SchemaLibraryEntry added = svc.addEntry(SchemaLibraryEntry.user("urn:u", "/tmp/u.xsd", SchemaKind.XSD, "U", null));
        assertEquals(EntrySource.USER, added.source());
        assertTrue(Files.exists(svc.getStorageFile()));

        SchemaLibraryServiceImpl reloaded = newService();
        assertEquals(3, reloaded.getEntries().size());
        assertTrue(reloaded.getEntries().stream().anyMatch(e -> e.id().equals(added.id())));
    }

    @Test
    void userEntryOverridesBundledWithSameKey() {
        svc.addEntry(SchemaLibraryEntry.user("urn:bundled:b", "/tmp/local-b.xsd", SchemaKind.XSD, "mine", null));
        var matches = svc.getEntries().stream().filter(e -> e.namespace().equals("urn:bundled:b")).toList();
        assertEquals(1, matches.size());
        assertEquals(EntrySource.USER, matches.getFirst().source());
        assertEquals(2, svc.getEntries().size());
    }

    @Test
    void bundledCanBeDisabledButNotRemoved() {
        SchemaLibraryEntry b = svc.getEntries().stream().filter(e -> e.namespace().equals("urn:bundled:b")).findFirst().orElseThrow();
        assertFalse(svc.removeEntry(b.id()));
        assertTrue(svc.setEnabled(b.id(), false));
        assertFalse(svc.getEntries().stream().filter(e -> e.id().equals(b.id())).findFirst().orElseThrow().enabled());
        assertFalse(newService().getEntries().stream().filter(e -> e.namespace().equals("urn:bundled:b")).findFirst().orElseThrow().enabled());
    }

    @Test
    void updateAndRemoveUserEntry() {
        SchemaLibraryEntry a = svc.addEntry(SchemaLibraryEntry.user("urn:a", "/tmp/a.xsd", SchemaKind.XSD, "", null));
        SchemaLibraryEntry changed = new SchemaLibraryEntry(a.id(), "urn:a2", a.location(), a.kind(), a.source(), true, "x", null);
        assertTrue(svc.updateEntry(changed));
        assertEquals("urn:a2", svc.getEntries().stream().filter(e -> e.id().equals(a.id())).findFirst().orElseThrow().namespace());
        assertTrue(svc.removeEntry(a.id()));
        assertFalse(svc.updateEntry(changed));
    }

    @Test
    void catalogsRoundTrip() throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'/>");
        SchemaCatalogRef ref = svc.addCatalog(cat);
        assertEquals(1, svc.getCatalogs().size());
        assertEquals(1, newService().getCatalogs().size());
        assertTrue(svc.setCatalogEnabled(ref.id(), false));
        assertFalse(svc.getCatalogs().getFirst().enabled());
        assertTrue(svc.removeCatalog(ref.id()));
        assertTrue(svc.getCatalogs().isEmpty());
    }

    @Test
    void corruptStorageIsBackedUpAndIgnored() throws Exception {
        Files.writeString(dir.resolve("schema-library.json"), "{not json");
        SchemaLibraryServiceImpl s = newService();
        assertEquals(2, s.getEntries().size());
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("schema-library.json.broken-")));
        }
    }

    @Test
    void rejectsUnsafeUrls() {
        assertThrows(IllegalArgumentException.class, () ->
                svc.addEntry(SchemaLibraryEntry.user("urn:x", "http://127.0.0.1/x.xsd", SchemaKind.XSD, "", null)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.SchemaLibraryServiceImplTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Write the interface, DTO and implementation**

`SchemaLibraryService.java`:
```java
package org.fxt.freexmltoolkit.service;

import javafx.collections.ObservableList;
import org.fxt.freexmltoolkit.domain.SchemaCatalogRef;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;

import java.nio.file.Path;
import java.util.List;

/**
 * The Schema Library: persistent namespace → schema mappings fed by user entries,
 * registered OASIS XML catalogs and a bundled list of well-known standards.
 * Resolution methods are added in {@code SchemaLibraryResolution} (Task 5).
 */
public interface SchemaLibraryService {
    ObservableList<SchemaLibraryEntry> getEntries();
    List<SchemaCatalogRef> getCatalogs();
    SchemaLibraryEntry addEntry(SchemaLibraryEntry entry);
    boolean updateEntry(SchemaLibraryEntry entry);
    boolean removeEntry(String id);
    boolean setEnabled(String id, boolean enabled);
    SchemaCatalogRef addCatalog(Path catalogFile);
    boolean removeCatalog(String id);
    boolean setCatalogEnabled(String id, boolean enabled);
    void reloadCatalogs();
    Path getStorageFile();
}
```

`SchemaLibraryFile.java` (Gson DTO, package-private):
```java
package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.List;

/** On-disk shape of {@code schema-library.json} and {@code bundled.json}. */
final class SchemaLibraryFile {
    int version = 1;
    List<EntryDto> entries = new ArrayList<>();
    List<CatalogDto> catalogs = new ArrayList<>();
    List<String> disabledBundled = new ArrayList<>();   // keys (kind|namespace)

    static final class EntryDto {
        String id;
        String namespace;
        String location;
        String kind;
        boolean enabled = true;
        String description;
        String rootElement;
    }

    static final class CatalogDto {
        String id;
        String path;
        boolean enabled = true;
    }
}
```

`SchemaLibraryServiceImpl.java`:
```java
package org.fxt.freexmltoolkit.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.util.PathValidator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

public class SchemaLibraryServiceImpl implements SchemaLibraryService {

    private static final Logger logger = LogManager.getLogger(SchemaLibraryServiceImpl.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final String BUNDLED_RESOURCE = "/schema-library/bundled.json";

    private static final class Holder {
        static final SchemaLibraryServiceImpl INSTANCE = new SchemaLibraryServiceImpl(
                Path.of(FileUtils.getUserDirectory().getAbsolutePath(), ".freeXmlToolkit", "schema-library.json"),
                SchemaResourceCache.shared(),
                () -> SchemaLibraryServiceImpl.class.getResourceAsStream(BUNDLED_RESOURCE));
    }

    public static SchemaLibraryServiceImpl getInstance() { return Holder.INSTANCE; }

    private final Path storageFile;
    protected final SchemaResourceCache cache;
    private final Object lock = new Object();

    // mutable state, guarded by lock
    private final List<SchemaLibraryEntry> userEntries = new ArrayList<>();
    private final List<SchemaLibraryEntry> bundledEntries = new ArrayList<>();
    private final Set<String> disabledBundled = new HashSet<>();
    private final List<SchemaCatalogRef> catalogs = new ArrayList<>();

    /** Immutable snapshot of the merged view for lock-free reads (resolution, UI). */
    protected volatile List<SchemaLibraryEntry> snapshot = List.of();
    private final ObservableList<SchemaLibraryEntry> observable = FXCollections.observableArrayList();
    private final ObservableList<SchemaLibraryEntry> readOnly = FXCollections.unmodifiableObservableList(observable);

    public SchemaLibraryServiceImpl(Path storageFile, SchemaResourceCache cache, Supplier<InputStream> bundledSource) {
        this.storageFile = storageFile.toAbsolutePath();
        this.cache = cache;
        loadBundled(bundledSource);
        load();
        rebuildSnapshot();
    }

    // ---- loading / saving -------------------------------------------------------

    private void loadBundled(Supplier<InputStream> source) {
        try (InputStream in = source.get()) {
            if (in == null) { logger.warn("Bundled schema library resource not found"); return; }
            SchemaLibraryFile f = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), SchemaLibraryFile.class);
            for (SchemaLibraryFile.EntryDto d : f.entries) {
                bundledEntries.add(new SchemaLibraryEntry("bundled:" + d.kind + ":" + d.namespace, d.namespace, d.location,
                        SchemaKind.valueOf(d.kind), EntrySource.BUNDLED, true, d.description, d.rootElement));
            }
        } catch (Exception e) {
            logger.error("Cannot read bundled schema library: {}", e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(storageFile)) return;
        try (Reader r = Files.newBufferedReader(storageFile, StandardCharsets.UTF_8)) {
            SchemaLibraryFile f = Objects.requireNonNull(GSON.fromJson(r, SchemaLibraryFile.class), "empty file");
            for (SchemaLibraryFile.EntryDto d : f.entries) {
                userEntries.add(new SchemaLibraryEntry(d.id != null ? d.id : UUID.randomUUID().toString(), d.namespace,
                        d.location, SchemaKind.valueOf(d.kind), EntrySource.USER, d.enabled, d.description, d.rootElement));
            }
            for (SchemaLibraryFile.CatalogDto c : f.catalogs) {
                catalogs.add(new SchemaCatalogRef(c.id != null ? c.id : UUID.randomUUID().toString(), c.path, c.enabled));
            }
            disabledBundled.addAll(f.disabledBundled);
        } catch (Exception e) {
            Path backup = storageFile.resolveSibling(storageFile.getFileName() + ".broken-" + Instant.now().toEpochMilli());
            logger.error("Schema library file {} is unreadable ({}); moving it to {}", storageFile, e.getMessage(), backup);
            try { Files.move(storageFile, backup, StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException io) { logger.warn("Could not back up broken library file: {}", io.getMessage()); }
            userEntries.clear(); catalogs.clear(); disabledBundled.clear();
        }
    }

    private void save() {
        SchemaLibraryFile f = new SchemaLibraryFile();
        for (SchemaLibraryEntry e : userEntries) {
            SchemaLibraryFile.EntryDto d = new SchemaLibraryFile.EntryDto();
            d.id = e.id(); d.namespace = e.namespace(); d.location = e.location(); d.kind = e.kind().name();
            d.enabled = e.enabled(); d.description = e.description(); d.rootElement = e.rootElement();
            f.entries.add(d);
        }
        for (SchemaCatalogRef c : catalogs) {
            SchemaLibraryFile.CatalogDto d = new SchemaLibraryFile.CatalogDto();
            d.id = c.id(); d.path = c.path(); d.enabled = c.enabled();
            f.catalogs.add(d);
        }
        f.disabledBundled.addAll(disabledBundled);
        try {
            Files.createDirectories(storageFile.getParent());
            Path tmp = storageFile.resolveSibling(storageFile.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(f), StandardCharsets.UTF_8);
            Files.move(tmp, storageFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Cannot save schema library to {}: {}", storageFile, e.getMessage());
        }
    }

    /** Merged view: user entries first, then bundled entries not overridden by a user key. Call under lock. */
    private void rebuildSnapshot() {
        Set<String> userKeys = new HashSet<>();
        List<SchemaLibraryEntry> merged = new ArrayList<>();
        for (SchemaLibraryEntry e : userEntries) { userKeys.add(e.key()); merged.add(e); }
        for (SchemaLibraryEntry b : bundledEntries) {
            if (userKeys.contains(b.key())) continue;
            merged.add(disabledBundled.contains(b.key()) ? b.withEnabled(false) : b);
        }
        snapshot = List.copyOf(merged);
        List<SchemaLibraryEntry> copy = snapshot;
        if (javafx.application.Platform.isFxApplicationThread()) {
            observable.setAll(copy);
        } else {
            try { javafx.application.Platform.runLater(() -> observable.setAll(copy)); }
            catch (IllegalStateException toolkitNotRunning) { observable.setAll(copy); }
        }
        onSnapshotRebuilt();
    }

    /** Hook for subclasses / Task 5 (catalog resolver invalidation). */
    protected void onSnapshotRebuilt() { }

    // ---- CRUD -------------------------------------------------------------------

    @Override public ObservableList<SchemaLibraryEntry> getEntries() { return readOnly; }

    @Override public List<SchemaCatalogRef> getCatalogs() { synchronized (lock) { return List.copyOf(catalogs); } }

    @Override public Path getStorageFile() { return storageFile; }

    static void validate(SchemaLibraryEntry entry) {
        if (entry.location().isBlank()) throw new IllegalArgumentException("Location must not be empty");
        if (entry.isRemote() && !PathValidator.isUrlSafeToAccess(entry.location())) {
            throw new IllegalArgumentException("URL is not allowed (points to a private or internal network): " + entry.location());
        }
        if (entry.namespace().isEmpty() && entry.rootElement() == null) {
            throw new IllegalArgumentException("Either a namespace or a root element is required");
        }
    }

    @Override public SchemaLibraryEntry addEntry(SchemaLibraryEntry entry) {
        validate(entry);
        SchemaLibraryEntry stored = entry.withSource(EntrySource.USER);
        synchronized (lock) {
            userEntries.removeIf(e -> e.id().equals(stored.id()));
            userEntries.add(stored);
            save();
            rebuildSnapshot();
        }
        return stored;
    }

    @Override public boolean updateEntry(SchemaLibraryEntry entry) {
        validate(entry);
        synchronized (lock) {
            for (int i = 0; i < userEntries.size(); i++) {
                if (userEntries.get(i).id().equals(entry.id())) {
                    userEntries.set(i, entry.withSource(EntrySource.USER));
                    save(); rebuildSnapshot();
                    return true;
                }
            }
            return false;
        }
    }

    @Override public boolean removeEntry(String id) {
        synchronized (lock) {
            boolean removed = userEntries.removeIf(e -> e.id().equals(id));
            if (removed) { save(); rebuildSnapshot(); }
            return removed;
        }
    }

    @Override public boolean setEnabled(String id, boolean enabled) {
        synchronized (lock) {
            for (int i = 0; i < userEntries.size(); i++) {
                if (userEntries.get(i).id().equals(id)) {
                    userEntries.set(i, userEntries.get(i).withEnabled(enabled));
                    save(); rebuildSnapshot();
                    return true;
                }
            }
            for (SchemaLibraryEntry b : bundledEntries) {
                if (b.id().equals(id)) {
                    if (enabled) disabledBundled.remove(b.key()); else disabledBundled.add(b.key());
                    save(); rebuildSnapshot();
                    return true;
                }
            }
            return false;
        }
    }

    @Override public SchemaCatalogRef addCatalog(Path catalogFile) {
        SchemaCatalogRef ref = SchemaCatalogRef.of(catalogFile);
        synchronized (lock) {
            catalogs.removeIf(c -> c.path().equals(ref.path()));
            catalogs.add(ref);
            save(); rebuildSnapshot();
        }
        return ref;
    }

    @Override public boolean removeCatalog(String id) {
        synchronized (lock) {
            boolean removed = catalogs.removeIf(c -> c.id().equals(id));
            if (removed) { save(); rebuildSnapshot(); }
            return removed;
        }
    }

    @Override public boolean setCatalogEnabled(String id, boolean enabled) {
        synchronized (lock) {
            for (int i = 0; i < catalogs.size(); i++) {
                if (catalogs.get(i).id().equals(id)) {
                    catalogs.set(i, catalogs.get(i).withEnabled(enabled));
                    save(); rebuildSnapshot();
                    return true;
                }
            }
            return false;
        }
    }

    @Override public void reloadCatalogs() { synchronized (lock) { rebuildSnapshot(); } }
}
```

Minimal `src/main/resources/schema-library/bundled.json` (extended in Task 17):
```json
{
  "version": 1,
  "entries": [
    {"namespace": "http://www.w3.org/XML/1998/namespace", "location": "https://www.w3.org/2001/xml.xsd",
     "kind": "XSD", "description": "XML namespace attributes (xml:lang, xml:space, xml:base, xml:id)"}
  ]
}
```

Registry (`ServiceRegistry.initialize()`):
```java
        // 13. SchemaLibraryService - namespace → schema mappings, catalogs, bundled standards
        registerFactory(SchemaLibraryService.class, SchemaLibraryServiceImpl::getInstance);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.SchemaLibraryServiceImplTest"`
Expected: PASS (8 tests). Note: `observable.setAll` from a non-FX thread without a toolkit falls back to a direct call (the `IllegalStateException` catch); in the test JVM no toolkit is running, so this branch is exercised.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryService.java src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryFile.java src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java src/main/resources/schema-library/bundled.json src/main/java/org/fxt/freexmltoolkit/di/ServiceRegistry.java src/test/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImplTest.java
git commit -m "feat(schema-library): SchemaLibraryService with persistence, CRUD and bundled merge"
```

---

### Task 5: Resolution, materialize, status, catalog import, `entryFromFile`

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryService.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/SchemaLibraryResolutionTest.java`

**Interfaces:**
- Consumes: Task 2 `SchemaCatalogParser`/`ParsedCatalog`; Task 3 cache API.
- Produces (added to `SchemaLibraryService`):
```java
Optional<SchemaLibraryEntry> resolveNamespace(String namespace, SchemaKind kind);
Optional<URI> resolveSystemId(String systemId, String baseUri);       // user entries, then catalogs (system → uri → public)
Optional<SchemaLibraryEntry> resolveJsonSchema(String schemaUri);
Optional<SchemaLibraryEntry> resolveByRootElement(String localName);
Optional<Path> materialize(SchemaLibraryEntry entry);                 // never throws
Optional<Path> resolveNamespaceToFile(String namespace, SchemaKind kind); // resolveNamespace + materialize
SchemaEntryStatus statusOf(SchemaLibraryEntry entry);
Optional<String> lastError(SchemaLibraryEntry entry);
List<SchemaLibraryEntry> importCatalog(Path catalogFile) throws IOException;   // preview entries, source CATALOG
Map<String, String> catalogErrors();                                   // catalog id → error text (empty when fine)
int catalogEntryCount(String catalogId);                               // -1 when unparsable
Optional<SchemaLibraryEntry> entryFromFile(Path schemaFile);          // prefill (namespace/$id/kind), source USER
```

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaLibraryResolutionTest {

    static final String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";
    static final String BUNDLED = """
            {"version":1,"entries":[
              {"namespace":"urn:shared","location":"https://example.org/bundled.xsd","kind":"XSD","description":"bundled"},
              {"namespace":"","location":"https://example.org/noNs.xsd","kind":"XSD","description":"no ns","rootElement":"invoice"}
            ]}""";

    @TempDir Path dir;
    SchemaLibraryServiceImpl svc;
    Path localXsd;

    @BeforeEach
    void setUp() throws Exception {
        localXsd = dir.resolve("local.xsd");
        Files.writeString(localXsd, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:local'/>");
        svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream(BUNDLED.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void userBeatsCatalogBeatsBundled() throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, ("<catalog xmlns='%s'><uri name='urn:shared' uri='local.xsd'/></catalog>").formatted(CATALOG_NS));
        svc.addCatalog(cat);
        // catalog wins over bundled
        assertEquals(localXsd.toUri(), svc.resolveSystemId("urn:shared", null).orElseThrow());
        assertEquals(EntrySource.BUNDLED, svc.resolveNamespace("urn:shared", SchemaKind.XSD).orElseThrow().source());
        // user wins over both
        svc.addEntry(SchemaLibraryEntry.user("urn:shared", localXsd.toString(), SchemaKind.XSD, "", null));
        assertEquals(EntrySource.USER, svc.resolveNamespace("urn:shared", SchemaKind.XSD).orElseThrow().source());
    }

    @Test
    void disabledEntriesAreSkipped() {
        SchemaLibraryEntry e = svc.addEntry(SchemaLibraryEntry.user("urn:local", localXsd.toString(), SchemaKind.XSD, "", null));
        assertTrue(svc.resolveNamespace("urn:local", SchemaKind.XSD).isPresent());
        svc.setEnabled(e.id(), false);
        assertTrue(svc.resolveNamespace("urn:local", SchemaKind.XSD).isEmpty());
    }

    @Test
    void kindIsPartOfTheKey() {
        svc.addEntry(SchemaLibraryEntry.user("https://example.org/s.json", dir.resolve("s.json").toString(), SchemaKind.JSON_SCHEMA, "", null));
        assertTrue(svc.resolveJsonSchema("https://example.org/s.json").isPresent());
        assertTrue(svc.resolveNamespace("https://example.org/s.json", SchemaKind.XSD).isEmpty());
    }

    @Test
    void rootElementResolution() {
        assertEquals("invoice", svc.resolveByRootElement("invoice").orElseThrow().rootElement());
        assertTrue(svc.resolveByRootElement("order").isEmpty());
    }

    @Test
    void resolveSystemIdMatchesUserLocationAndRelativeBase() {
        svc.addEntry(SchemaLibraryEntry.user("urn:local", localXsd.toString(), SchemaKind.XSD, "", null));
        assertEquals(localXsd.toUri(), svc.resolveSystemId(localXsd.toUri().toString(), null).orElseThrow());
        assertEquals(localXsd.toUri(), svc.resolveSystemId("local.xsd", dir.toUri().toString()).orElseThrow());
        assertTrue(svc.resolveSystemId("other.xsd", dir.toUri().toString()).isEmpty());
    }

    @Test
    void materializeAndStatus() {
        SchemaLibraryEntry ok = svc.addEntry(SchemaLibraryEntry.user("urn:local", localXsd.toString(), SchemaKind.XSD, "", null));
        assertEquals(localXsd, svc.materialize(ok).orElseThrow());
        assertEquals(SchemaEntryStatus.LOCAL_OK, svc.statusOf(ok));

        SchemaLibraryEntry missing = svc.addEntry(SchemaLibraryEntry.user("urn:missing", dir.resolve("nope.xsd").toString(), SchemaKind.XSD, "", null));
        assertTrue(svc.materialize(missing).isEmpty());
        assertEquals(SchemaEntryStatus.LOCAL_MISSING, svc.statusOf(missing));

        SchemaLibraryEntry remote = svc.resolveNamespace("urn:shared", SchemaKind.XSD).orElseThrow();
        assertEquals(SchemaEntryStatus.NOT_DOWNLOADED, svc.statusOf(remote));
        assertTrue(svc.resolveNamespaceToFile("urn:missing", SchemaKind.XSD).isEmpty());
    }

    @Test
    void unparsableCatalogIsReportedNotFatal() throws Exception {
        Path bad = dir.resolve("bad.xml");
        Files.writeString(bad, "<catalog");
        SchemaCatalogRef ref = svc.addCatalog(bad);
        assertTrue(svc.resolveSystemId("urn:x", null).isEmpty());
        assertTrue(svc.catalogErrors().containsKey(ref.id()));
        assertEquals(-1, svc.catalogEntryCount(ref.id()));
    }

    @Test
    void importCatalogPreview() throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, ("<catalog xmlns='%s'><uri name='urn:a' uri='local.xsd'/>"
                + "<system systemId='https://x/y.xsd' uri='local.xsd'/>"
                + "<rewriteSystem systemIdStartString='http://p/' rewritePrefix='q/'/></catalog>").formatted(CATALOG_NS));
        var preview = svc.importCatalog(cat);
        assertEquals(2, preview.size()); // rewrite entries are not importable
        assertTrue(preview.stream().allMatch(e -> e.source() == EntrySource.CATALOG));
        assertTrue(preview.stream().anyMatch(e -> e.namespace().equals("urn:a") && e.location().equals(localXsd.toString())));
    }

    @Test
    void entryFromFilePrefillsXsdAndJson() throws Exception {
        SchemaLibraryEntry x = svc.entryFromFile(localXsd).orElseThrow();
        assertEquals("urn:local", x.namespace());
        assertEquals(SchemaKind.XSD, x.kind());
        Path json = dir.resolve("s.schema.json");
        Files.writeString(json, "{\"$id\":\"https://example.org/s.json\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}");
        SchemaLibraryEntry j = svc.entryFromFile(json).orElseThrow();
        assertEquals("https://example.org/s.json", j.namespace());
        assertEquals(SchemaKind.JSON_SCHEMA, j.kind());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.SchemaLibraryResolutionTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Add the methods**

Add the signatures from *Interfaces* to `SchemaLibraryService` (imports: `java.net.URI`, `java.util.Map`, `java.util.Optional`, `java.io.IOException`, `SchemaEntryStatus`, `SchemaKind`).

In `SchemaLibraryServiceImpl` add fields and implementations:

```java
    // ---- catalogs (parsed lazily, invalidated on mutation / mtime change) ------------
    private record LoadedCatalog(SchemaCatalogRef ref, java.nio.file.attribute.FileTime mtime,
                                 org.fxt.freexmltoolkit.service.catalog.ParsedCatalog parsed, String error) { }
    private volatile List<LoadedCatalog> loadedCatalogs = null;
    private final Map<String, String> sessionErrors = new java.util.concurrent.ConcurrentHashMap<>();   // entry id → error
    private final Map<String, Long> failedAt = new java.util.concurrent.ConcurrentHashMap<>();          // entry id → epoch millis
    static final long RETRY_AFTER_MS = 10 * 60 * 1000L;

    @Override protected void onSnapshotRebuilt() { loadedCatalogs = null; }

    private List<LoadedCatalog> catalogsLoaded() {
        List<LoadedCatalog> current = loadedCatalogs;
        List<SchemaCatalogRef> refs = getCatalogs();
        boolean stale = current == null || current.size() != refs.size();
        if (!stale) {
            for (LoadedCatalog lc : current) {
                java.nio.file.attribute.FileTime now = mtimeOf(lc.ref().asPath());
                if (!Objects.equals(now, lc.mtime())) { stale = true; break; }
            }
        }
        if (!stale) return current;
        List<LoadedCatalog> fresh = new ArrayList<>();
        for (SchemaCatalogRef ref : refs) {
            try {
                fresh.add(new LoadedCatalog(ref, mtimeOf(ref.asPath()),
                        org.fxt.freexmltoolkit.service.catalog.SchemaCatalogParser.parse(ref.asPath()), null));
            } catch (IOException e) {
                fresh.add(new LoadedCatalog(ref, mtimeOf(ref.asPath()), null, e.getMessage()));
            }
        }
        loadedCatalogs = List.copyOf(fresh);
        return loadedCatalogs;
    }

    private static java.nio.file.attribute.FileTime mtimeOf(Path p) {
        try { return Files.getLastModifiedTime(p); } catch (IOException e) { return null; }
    }

    @Override public Map<String, String> catalogErrors() {
        Map<String, String> errors = new LinkedHashMap<>();
        for (LoadedCatalog lc : catalogsLoaded()) if (lc.error() != null) errors.put(lc.ref().id(), lc.error());
        return errors;
    }

    @Override public int catalogEntryCount(String catalogId) {
        for (LoadedCatalog lc : catalogsLoaded()) {
            if (lc.ref().id().equals(catalogId)) return lc.parsed() == null ? -1 : lc.parsed().allEntries().size();
        }
        return -1;
    }

    // ---- resolution ---------------------------------------------------------------

    @Override public Optional<SchemaLibraryEntry> resolveNamespace(String namespace, SchemaKind kind) {
        if (namespace == null || namespace.isBlank() || kind == null) return Optional.empty();
        String ns = namespace.trim();
        SchemaLibraryEntry bundledHit = null;
        for (SchemaLibraryEntry e : snapshot) {                       // USER entries come first in the snapshot
            if (!e.enabled() || e.kind() != kind || !e.namespace().equals(ns)) continue;
            if (e.source() == EntrySource.USER) return Optional.of(e);
            if (bundledHit == null) bundledHit = e;
        }
        // CATALOG (uri entries keyed by namespace) sits between USER and BUNDLED
        for (LoadedCatalog lc : catalogsLoaded()) {
            if (!lc.ref().enabled() || lc.parsed() == null) continue;
            Optional<String> target = lc.parsed().matchUri(ns);
            if (target.isPresent()) {
                return Optional.of(new SchemaLibraryEntry("catalog:" + lc.ref().id() + ":" + ns, ns, toLocation(target.get()),
                        kind, EntrySource.CATALOG, true, "from catalog " + lc.ref().asPath().getFileName(), null));
            }
        }
        return Optional.ofNullable(bundledHit);
    }

    @Override public Optional<SchemaLibraryEntry> resolveJsonSchema(String schemaUri) {
        return resolveNamespace(schemaUri, SchemaKind.JSON_SCHEMA);
    }

    @Override public Optional<SchemaLibraryEntry> resolveByRootElement(String localName) {
        if (localName == null || localName.isBlank()) return Optional.empty();
        return snapshot.stream()
                .filter(e -> e.enabled() && e.kind() == SchemaKind.XSD && e.namespace().isEmpty()
                        && localName.equals(e.rootElement()))
                .findFirst();
    }

    @Override public Optional<URI> resolveSystemId(String systemId, String baseUri) {
        if (systemId == null || systemId.isBlank()) return Optional.empty();
        URI absolute = absolutize(systemId, baseUri);
        String absoluteString = absolute != null ? absolute.toString() : systemId;
        for (SchemaLibraryEntry e : snapshot) {
            if (!e.enabled() || e.source() != EntrySource.USER) continue;
            URI loc = locationUri(e);
            if (loc != null && (loc.toString().equals(absoluteString) || e.location().equals(systemId))) {
                return Optional.of(loc);
            }
        }
        for (LoadedCatalog lc : catalogsLoaded()) {
            if (!lc.ref().enabled() || lc.parsed() == null) continue;
            Optional<String> t = lc.parsed().matchSystem(systemId);
            if (t.isEmpty() && absolute != null) t = lc.parsed().matchSystem(absoluteString);
            if (t.isEmpty()) t = lc.parsed().matchUri(systemId);
            if (t.isEmpty() && absolute != null) t = lc.parsed().matchUri(absoluteString);
            if (t.isPresent()) {
                try { return Optional.of(URI.create(t.get())); } catch (IllegalArgumentException ignore) { }
            }
        }
        return Optional.empty();
    }

    private static URI absolutize(String id, String base) {
        try {
            URI u = URI.create(id);
            if (u.isAbsolute()) return u;
            return base != null && !base.isBlank() ? URI.create(base).resolve(u) : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    private static URI locationUri(SchemaLibraryEntry e) {
        try { return e.isRemote() ? URI.create(e.location()) : Path.of(e.location()).toUri(); }
        catch (RuntimeException ex) { return null; }
    }

    /** file: URIs become plain paths (entries show paths); other URIs stay as-is. */
    private static String toLocation(String uri) {
        try {
            URI u = URI.create(uri);
            return "file".equalsIgnoreCase(u.getScheme()) ? Path.of(u).toString() : uri;
        } catch (RuntimeException e) { return uri; }
    }

    // ---- materialize / status ------------------------------------------------------

    @Override public Optional<Path> materialize(SchemaLibraryEntry entry) {
        if (entry == null) return Optional.empty();
        if (!entry.isRemote()) {
            Path p = Path.of(entry.location());
            if (Files.isRegularFile(p)) { sessionErrors.remove(entry.id()); return Optional.of(p); }
            sessionErrors.put(entry.id(), "File not found: " + p);
            return Optional.empty();
        }
        if (!PathValidator.isUrlSafeToAccess(entry.location())) {
            sessionErrors.put(entry.id(), "URL not allowed: " + entry.location());
            return Optional.empty();
        }
        Long failed = failedAt.get(entry.id());
        if (failed != null && System.currentTimeMillis() - failed < RETRY_AFTER_MS && !cache.isCached(entry.location())) {
            return Optional.empty();
        }
        try {
            Path p = cache.getOrDownload(entry.location());
            sessionErrors.remove(entry.id()); failedAt.remove(entry.id());
            return Optional.of(p);
        } catch (IOException e) {
            sessionErrors.put(entry.id(), e.getMessage());
            failedAt.put(entry.id(), System.currentTimeMillis());
            logger.warn("Cannot download schema for {} from {}: {}", entry.namespace(), entry.location(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Explicit user action: forget a remembered failure so the next materialize retries. */
    public void clearFailure(SchemaLibraryEntry entry) { failedAt.remove(entry.id()); sessionErrors.remove(entry.id()); }

    @Override public Optional<Path> resolveNamespaceToFile(String namespace, SchemaKind kind) {
        return resolveNamespace(namespace, kind).flatMap(this::materialize);
    }

    @Override public SchemaEntryStatus statusOf(SchemaLibraryEntry entry) {
        if (sessionErrors.containsKey(entry.id())) return SchemaEntryStatus.ERROR;
        if (!entry.isRemote()) {
            return Files.isRegularFile(Path.of(entry.location())) ? SchemaEntryStatus.LOCAL_OK : SchemaEntryStatus.LOCAL_MISSING;
        }
        return cache.isCached(entry.location()) ? SchemaEntryStatus.CACHED : SchemaEntryStatus.NOT_DOWNLOADED;
    }

    @Override public Optional<String> lastError(SchemaLibraryEntry entry) {
        return Optional.ofNullable(sessionErrors.get(entry.id()));
    }

    // ---- import / prefill -----------------------------------------------------------

    @Override public List<SchemaLibraryEntry> importCatalog(Path catalogFile) throws IOException {
        var parsed = org.fxt.freexmltoolkit.service.catalog.SchemaCatalogParser.parse(catalogFile);
        List<SchemaLibraryEntry> out = new ArrayList<>();
        for (var e : parsed.allEntries()) {
            switch (e.type()) {
                case URI, SYSTEM -> out.add(new SchemaLibraryEntry(UUID.randomUUID().toString(), e.key(), toLocation(e.target()),
                        guessKind(e.target()), EntrySource.CATALOG, true,
                        e.type() + " entry from " + catalogFile.getFileName(), null));
                default -> { }   // public + rewrite entries are not namespace mappings
            }
        }
        return out;
    }

    private static SchemaKind guessKind(String location) {
        String l = location.toLowerCase(Locale.ROOT);
        if (l.endsWith(".json")) return SchemaKind.JSON_SCHEMA;
        if (l.endsWith(".dtd")) return SchemaKind.DTD;
        return SchemaKind.XSD;
    }

    @Override public Optional<SchemaLibraryEntry> entryFromFile(Path schemaFile) {
        if (schemaFile == null || !Files.isRegularFile(schemaFile)) return Optional.empty();
        String name = schemaFile.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".json")) {
                var root = com.google.gson.JsonParser.parseString(Files.readString(schemaFile));
                String id = root.isJsonObject() && root.getAsJsonObject().has("$id")
                        ? root.getAsJsonObject().get("$id").getAsString() : "";
                return Optional.of(SchemaLibraryEntry.user(id, schemaFile.toAbsolutePath().toString(),
                        SchemaKind.JSON_SCHEMA, "", null));
            }
            if (name.endsWith(".dtd")) {
                return Optional.of(SchemaLibraryEntry.user("", schemaFile.toAbsolutePath().toString(), SchemaKind.DTD, "", null));
            }
            String tns = XmlRootElementSniffer.targetNamespaceOf(schemaFile).orElse("");
            return Optional.of(SchemaLibraryEntry.user(tns, schemaFile.toAbsolutePath().toString(), SchemaKind.XSD, "", null));
        } catch (Exception e) {
            logger.debug("Cannot prefill entry from {}: {}", schemaFile, e.getMessage());
            return Optional.of(SchemaLibraryEntry.user("", schemaFile.toAbsolutePath().toString(), guessKind(name), "", null));
        }
    }
```

`XmlRootElementSniffer` is created in Task 6 — for this task add a minimal version now (Task 6 extends it with tests):

```java
package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.util.SecureXmlFactory;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Cheap StAX sniffing of the document element (namespace, local name) or an XSD's targetNamespace. */
public final class XmlRootElementSniffer {

    /** Document element identity. {@code namespace} is "" when the element has none. */
    public record RootElement(String namespace, String localName) { }

    private XmlRootElementSniffer() { }

    public static Optional<RootElement> sniff(String xml) {
        if (xml == null || xml.isBlank()) return Optional.empty();
        try (Reader r = new StringReader(xml)) {
            XMLStreamReader sr = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(r);
            while (sr.hasNext()) {
                if (sr.next() == XMLStreamConstants.START_ELEMENT) {
                    String ns = sr.getNamespaceURI();
                    return Optional.of(new RootElement(ns == null ? "" : ns, sr.getLocalName()));
                }
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }

    /** The {@code targetNamespace} of an XSD file, empty when absent or unreadable. */
    public static Optional<String> targetNamespaceOf(Path xsd) {
        try (InputStream in = Files.newInputStream(xsd)) {
            XMLStreamReader sr = SecureXmlFactory.createSecureXMLInputFactory().createXMLStreamReader(in);
            while (sr.hasNext()) {
                if (sr.next() == XMLStreamConstants.START_ELEMENT) {
                    String tns = sr.getAttributeValue(null, "targetNamespace");
                    return Optional.ofNullable(tns).filter(s -> !s.isBlank());
                }
            }
        } catch (Exception ignored) { }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.SchemaLibraryResolutionTest" --tests "org.fxt.freexmltoolkit.service.SchemaLibraryServiceImplTest"`
Expected: PASS (17 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryService.java src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java src/main/java/org/fxt/freexmltoolkit/service/XmlRootElementSniffer.java src/test/java/org/fxt/freexmltoolkit/service/SchemaLibraryResolutionTest.java
git commit -m "feat(schema-library): resolution order, catalogs, materialize and status"
```

---

### Task 6: Root-element sniffer tests and legacy auto-detected cache listing

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XmlRootElementSniffer.java` (from Task 5)
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XmlService.java`, `src/main/java/org/fxt/freexmltoolkit/service/XmlServiceImpl.java` (near `CACHE_DIR`, line 122)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/XmlRootElementSnifferTest.java`, `src/test/java/org/fxt/freexmltoolkit/service/XmlServiceLegacySchemaCacheTest.java`

**Interfaces:**
- Produces: `XmlService.listAutoDetectedSchemaCacheDirs()` → `List<Path>` (subdirectories of `~/.freeXmlToolkit/cache` except `schemas`), `XmlService.clearAutoDetectedSchemaCache()` → `int` deleted files; static `XmlServiceImpl.listAutoDetectedSchemaCacheDirs(Path cacheRoot)` / `clearAutoDetectedSchemaCache(Path cacheRoot)` for tests.

- [ ] **Step 1: Write the failing tests**

```java
package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XmlRootElementSnifferTest {
    @Test void namespacedRoot() {
        var r = XmlRootElementSniffer.sniff("<?xml version='1.0'?><!-- c --><x:Scene xmlns:x='http://www.web3d.org/specifications/x3d-4.0.xsd'/>").orElseThrow();
        assertEquals("http://www.web3d.org/specifications/x3d-4.0.xsd", r.namespace());
        assertEquals("Scene", r.localName());
    }
    @Test void defaultNamespaceAndNoNamespace() {
        assertEquals("urn:d", XmlRootElementSniffer.sniff("<a xmlns='urn:d'><b/></a>").orElseThrow().namespace());
        assertEquals("", XmlRootElementSniffer.sniff("<invoice/>").orElseThrow().namespace());
    }
    @Test void malformedOrEmptyIsEmpty() {
        assertTrue(XmlRootElementSniffer.sniff("<a").isEmpty());   // no START_ELEMENT before the error? see note
        assertTrue(XmlRootElementSniffer.sniff("").isEmpty());
        assertTrue(XmlRootElementSniffer.sniff("not xml").isEmpty());
    }
}
```
Note: `"<a"` may yield a START_ELEMENT for some parsers before failing; if the assertion fails, change the input to `"<"`.

```java
package org.fxt.freexmltoolkit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class XmlServiceLegacySchemaCacheTest {
    @Test void listsAndClearsMd5Dirs(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("schemas"));
        Files.writeString(root.resolve("schemas").resolve("keep.xsd"), "x");
        Path md5 = root.resolve("0123456789ABCDEF0123456789ABCDEF");
        Files.createDirectories(md5);
        Files.writeString(md5.resolve("a.xsd"), "x");
        assertEquals(java.util.List.of(md5), XmlServiceImpl.listAutoDetectedSchemaCacheDirs(root));
        assertEquals(1, XmlServiceImpl.clearAutoDetectedSchemaCache(root));
        assertFalse(Files.exists(md5));
        assertTrue(Files.exists(root.resolve("schemas").resolve("keep.xsd")));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.XmlRootElementSnifferTest" --tests "org.fxt.freexmltoolkit.service.XmlServiceLegacySchemaCacheTest"`
Expected: sniffer PASS or partially; legacy cache test compilation FAIL.

- [ ] **Step 3: Implement**

`XmlService` interface additions:
```java
    /** Directories of the legacy auto-detected schema cache ({@code ~/.freeXmlToolkit/cache/<MD5>/}). */
    java.util.List<java.nio.file.Path> listAutoDetectedSchemaCacheDirs();

    /** Deletes the legacy auto-detected schema cache. @return number of files deleted */
    int clearAutoDetectedSchemaCache();
```
`XmlServiceImpl`:
```java
    private static final java.util.regex.Pattern MD5_DIR = java.util.regex.Pattern.compile("[0-9A-Fa-f]{32}");

    @Override public java.util.List<Path> listAutoDetectedSchemaCacheDirs() {
        return listAutoDetectedSchemaCacheDirs(Path.of(CACHE_DIR));
    }

    @Override public int clearAutoDetectedSchemaCache() {
        return clearAutoDetectedSchemaCache(Path.of(CACHE_DIR));
    }

    static java.util.List<Path> listAutoDetectedSchemaCacheDirs(Path cacheRoot) {
        if (!Files.isDirectory(cacheRoot)) return java.util.List.of();
        try (var s = Files.list(cacheRoot)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> MD5_DIR.matcher(p.getFileName().toString()).matches())
                    .sorted().toList();
        } catch (IOException e) {
            logger.warn("Cannot list schema cache {}: {}", cacheRoot, e.getMessage());
            return java.util.List.of();
        }
    }

    static int clearAutoDetectedSchemaCache(Path cacheRoot) {
        int deleted = 0;
        for (Path dir : listAutoDetectedSchemaCacheDirs(cacheRoot)) {
            try (var walk = Files.walk(dir)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    if (Files.isRegularFile(p)) deleted++;
                    Files.deleteIfExists(p);
                }
            } catch (IOException e) {
                logger.warn("Cannot delete {}: {}", dir, e.getMessage());
            }
        }
        return deleted;
    }
```
If `XmlService` has other implementations/mocks in tests that fail to compile, add the two methods as `default` methods in the interface returning `List.of()` / `0` instead.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.XmlRootElementSnifferTest" --tests "org.fxt.freexmltoolkit.service.XmlServiceLegacySchemaCacheTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/XmlRootElementSniffer.java src/main/java/org/fxt/freexmltoolkit/service/XmlService.java src/main/java/org/fxt/freexmltoolkit/service/XmlServiceImpl.java src/test/java/org/fxt/freexmltoolkit/service/XmlRootElementSnifferTest.java src/test/java/org/fxt/freexmltoolkit/service/XmlServiceLegacySchemaCacheTest.java
git commit -m "feat(schema-library): root element sniffer and legacy schema cache listing"
```

---

### Task 7: Hook — validation `LSResourceResolver` (Xerces + Saxon)

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java` (add `shared()`)
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolver.java:936-1000` (`ValidationResourceResolver`)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolverLibraryHookTest.java`

**Interfaces:**
- Produces: `static SchemaLibraryService SchemaLibraryServiceImpl.shared()` — the registry instance when `ServiceRegistry.isRegistered(SchemaLibraryService.class)`, else `getInstance()`. All hooks (Tasks 7–12) use it; tests inject a temp-dir service via `ServiceRegistry.register(SchemaLibraryService.class, svc)` and call `ServiceRegistry.reset()` in `@AfterEach`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.service.xsd;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSInput;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SchemaResolverLibraryHookTest {

    @AfterEach void tearDown() { ServiceRegistry.reset(); }

    @Test
    void importWithUnresolvableLocationIsServedFromLibraryByNamespace(@TempDir Path dir) throws Exception {
        Path types = dir.resolve("lib").resolve("types.xsd");
        Files.createDirectories(types.getParent());
        Files.writeString(types, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types" elementFormDefault="qualified">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"><xs:maxLength value="3"/></xs:restriction></xs:simpleType>
                </xs:schema>
                """);
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addEntry(SchemaLibraryEntry.user("urn:types", types.toString(), SchemaKind.XSD, "", null));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        LSInput in = resolver.resolveResource("http://www.w3.org/2001/XMLSchema", "urn:types", null,
                "https://nowhere.invalid/types.xsd", dir.toUri().toString());
        assertNotNull(in, "library should serve the import");
        assertEquals(types.toUri().toString(), in.getSystemId());
        assertNotNull(in.getByteStream());
        in.getByteStream().close();
    }

    @Test
    void missWithoutLibraryEntryFallsThroughToExistingBehaviour(@TempDir Path dir) {
        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        assertNull(resolver.resolveResource("http://www.w3.org/2001/XMLSchema", "urn:none", null, "missing.xsd", dir.toUri().toString()));
    }

    @Test
    void catalogSystemEntryIsHonoured(@TempDir Path dir) throws Exception {
        Path local = dir.resolve("local.xsd");
        Files.writeString(local, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<system systemId='https://example.org/remote.xsd' uri='local.xsd'/></catalog>");
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addCatalog(cat);
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        var resolver = new SchemaResolver(XsdParseOptions.defaults()).createLSResourceResolver(dir);
        LSInput in = resolver.resolveResource("http://www.w3.org/2001/XMLSchema", null, null,
                "https://example.org/remote.xsd", null);
        assertNotNull(in);
        assertEquals(local.toUri().toString(), in.getSystemId());
        in.getByteStream().close();
    }
}
```
Check the name of the options class used by `new SchemaResolver(...)` (`XsdParseOptions.defaults()` per `SchemaResolver.java:73`) and whether `ServiceRegistry.register` requires `initialize()` first; if `get()` throws "not initialized" for other services during the test, call `ServiceRegistry.initialize()` before `register(...)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.xsd.SchemaResolverLibraryHookTest"`
Expected: first and third tests FAIL (null LSInput), second passes.

- [ ] **Step 3: Implement**

`SchemaLibraryServiceImpl`:
```java
    /** Registry-provided instance when the registry knows the service, else the lazy singleton. */
    public static SchemaLibraryService shared() {
        return org.fxt.freexmltoolkit.di.ServiceRegistry.isRegistered(SchemaLibraryService.class)
                ? org.fxt.freexmltoolkit.di.ServiceRegistry.get(SchemaLibraryService.class)
                : getInstance();
    }
```

`ValidationResourceResolver.resolveResource` — insert after the circular-import bookkeeping (`parentUris.get().putIfAbsent(...)`) and before the `try { if (systemId.startsWith("http://") ...` block:
```java
            // Schema Library first (user mappings → catalogs → bundled); a miss falls through.
            org.w3c.dom.ls.LSInput fromLibrary = resolveFromLibrary(namespaceURI, publicId, systemId, baseURI);
            if (fromLibrary != null) {
                return fromLibrary;
            }
```
and add the helper to the inner class:
```java
        private org.w3c.dom.ls.LSInput resolveFromLibrary(String namespaceURI, String publicId,
                                                          String systemId, String baseURI) {
            try {
                org.fxt.freexmltoolkit.service.SchemaLibraryService library =
                        org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
                Path target = null;
                var bySystemId = library.resolveSystemId(systemId, baseURI);
                if (bySystemId.isPresent() && "file".equalsIgnoreCase(bySystemId.get().getScheme())) {
                    target = Path.of(bySystemId.get());
                } else if (bySystemId.isPresent()) {
                    // catalog pointed to a remote URI: go through the cache (SSRF-checked there)
                    target = cache.getOrDownload(bySystemId.get().toString());
                } else if (namespaceURI != null && !namespaceURI.isBlank()) {
                    target = library.resolveNamespaceToFile(namespaceURI,
                            org.fxt.freexmltoolkit.domain.SchemaKind.XSD).orElse(null);
                }
                if (target == null || !java.nio.file.Files.isRegularFile(target)) {
                    return null;
                }
                logger.debug("Schema Library resolved '{}' (ns {}) -> {}", systemId, namespaceURI, target);
                return new LSInputImpl(publicId, target.toUri().toString(), target.toUri().toString(),
                        java.nio.file.Files.newInputStream(target));
            } catch (Exception e) {
                logger.debug("Schema Library lookup failed for '{}': {}", systemId, e.getMessage());
                return null;
            }
        }
```
Important: `LSInputImpl`'s second argument is the systemId reported to Xerces. Using the *resolved* file URI (not the original systemId) makes relative includes inside the served schema resolve against the real file location. Check the `LSInputImpl` constructor order at `SchemaResolver.java:1221` (`publicId, systemId, baseURI, byteStream`) and keep it.

- [ ] **Step 4: Run tests (hook + existing resolver tests)**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.xsd.SchemaResolverLibraryHookTest" --tests "org.fxt.freexmltoolkit.service.xsd.SchemaResolverTest"`
Expected: PASS. (If `SchemaResolverTest` does not exist, run `./gradlew test --tests "org.fxt.freexmltoolkit.service.XercesXmlValidationServiceTest"` instead — whichever exists; find with `ls src/test/java/org/fxt/freexmltoolkit/service/xsd/`.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java src/main/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolver.java src/test/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolverLibraryHookTest.java
git commit -m "feat(schema-library): consult the library in the validation LSResourceResolver"
```

---

### Task 8: Hook — V2 `XsdNodeFactory` imports and includes

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/v2/model/XsdNodeFactory.java` (`loadAndMergeImportedSchema` ~1880–1960, `inlineSchemaReference` ~502–530)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/v2/model/XsdNodeFactoryLibraryImportTest.java`

**Interfaces:**
- Consumes: `SchemaLibraryServiceImpl.shared()`, `resolveSystemId`, `resolveNamespaceToFile`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.v2.model;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XsdNodeFactoryLibraryImportTest {

    @AfterEach void tearDown() { ServiceRegistry.reset(); }

    private SchemaLibraryServiceImpl library(Path dir) {
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);
        return svc;
    }

    @Test
    void importResolvedViaLibraryNamespace(@TempDir Path dir) throws Exception {
        Path types = dir.resolve("elsewhere").resolve("types.xsd");
        Files.createDirectories(types.getParent());
        Files.writeString(types, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:types">
                  <xs:simpleType name="Code"><xs:restriction base="xs:string"/></xs:simpleType>
                </xs:schema>""");
        library(dir).addEntry(SchemaLibraryEntry.user("urn:types", types.toString(), SchemaKind.XSD, "", null));

        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:t="urn:types">
                  <xs:import namespace="urn:types" schemaLocation="missing/types.xsd"/>
                  <xs:element name="root" type="t:Code"/>
                </xs:schema>""");
        XsdNodeFactory factory = new XsdNodeFactory();
        factory.setRemoteNamespaceFallbackEnabled(false);
        XsdSchema schema = factory.fromFile(main);   // use the real loader name (fromFile / loadFromFile / parse)
        XsdImport imp = (XsdImport) schema.getChildren().stream().filter(n -> n instanceof XsdImport).findFirst().orElseThrow();
        assertTrue(imp.isResolved(), "import should be resolved through the library");
        assertNotNull(schema.getImportedSchema("urn:types"));
    }

    @Test
    void includeResolvedViaCatalogSystemId(@TempDir Path dir) throws Exception {
        Path part = dir.resolve("parts").resolve("part.xsd");
        Files.createDirectories(part.getParent());
        Files.writeString(part, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="fromInclude" type="xs:string"/>
                </xs:schema>""");
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<system systemId='https://example.org/part.xsd' uri='parts/part.xsd'/></catalog>");
        library(dir).addCatalog(cat);

        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="https://example.org/part.xsd"/>
                </xs:schema>""");
        XsdSchema schema = new XsdNodeFactory().fromFile(main);
        assertTrue(schema.getChildren().stream().anyMatch(n -> n instanceof XsdElement e && "fromInclude".equals(e.getName())));
    }
}
```
Before writing, check the real API names in `XsdNodeFactory` (file loader method), `XsdImport` (`isResolved()`/`getResolvedSchema()`), and `XsdSchema` (`getImportedSchema(String)` or `getImportedSchemas()`), and adapt the assertions — the *behaviour* to assert is: the import node is resolved and the imported schema is registered; the included element appears in the root schema.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactoryLibraryImportTest"`
Expected: FAIL (import unresolved / include skipped as remote).

- [ ] **Step 3: Implement**

In `loadAndMergeImportedSchema`, right after computing `remote`, `resolvedPath`, `canonicalKey` for the local branch (before the "Fast path" comment), add:
```java
            // Schema Library: user mappings / catalogs / bundled entries win over the declared
            // location when that location cannot be resolved locally.
            if (resolvedPath == null) {
                Path fromLibrary = resolveImportViaLibrary(namespace, schemaLocation, pending.declaringBaseDir());
                if (fromLibrary != null) {
                    resolvedPath = fromLibrary;
                    remote = false;
                    canonicalKey = canonicalKeyForFile(fromLibrary);
                    logger.info("Resolved import namespace '{}' via Schema Library: {}", namespace, fromLibrary);
                }
            }
```
(`remote` must become non-final: declare it as `boolean remote = ...` — it already is.) Add the helper:
```java
    /** Library lookup for an import: systemId/catalog first, then the namespace. Null on miss. */
    private static Path resolveImportViaLibrary(String namespace, String schemaLocation, Path declaringBaseDir) {
        try {
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            String base = declaringBaseDir != null ? declaringBaseDir.toUri().toString() : null;
            var bySystemId = library.resolveSystemId(schemaLocation, base);
            if (bySystemId.isPresent() && "file".equalsIgnoreCase(bySystemId.get().getScheme())) {
                Path p = Path.of(bySystemId.get());
                if (Files.isRegularFile(p)) return p;
            }
            if (namespace != null && !namespace.isBlank()) {
                return library.resolveNamespaceToFile(namespace, org.fxt.freexmltoolkit.domain.SchemaKind.XSD).orElse(null);
            }
        } catch (Exception e) {
            logger.debug("Schema Library lookup failed for import '{}': {}", schemaLocation, e.getMessage());
        }
        return null;
    }
```
Also cover imports **without** `schemaLocation`: change the early return at the top of the method so that, when `schemaLocation` is empty but the namespace has a library entry, the entry is used:
```java
        if (schemaLocation == null || schemaLocation.isEmpty()) {
            Path fromLibrary = resolveImportViaLibrary(namespace, null, pending.declaringBaseDir());
            if (fromLibrary == null) {
                logger.warn("Import has no schemaLocation, skipping: namespace='{}'", namespace);
                xsdImport.markResolutionFailed("No schemaLocation specified");
                return;
            }
            schemaLocation = fromLibrary.toString();
        }
```
(`schemaLocation` becomes a reassignable local — remove `final` if present.)

In `inlineSchemaReference` (includes) replace the remote-skip block with:
```java
        Path resolvedPath;
        if (schemaLocation.contains("://")) {
            resolvedPath = resolveIncludeViaLibrary(schemaLocation, baseDirectory);
            if (resolvedPath == null) {
                logger.debug("Skipping remote schema include '{}'", schemaLocation);
                if (xsdInclude != null) xsdInclude.markResolutionFailed("Remote schemas not supported");
                return;
            }
        } else {
            resolvedPath = baseDirectory.resolve(schemaLocation).normalize();
            if (!Files.exists(resolvedPath)) {
                Path fromLibrary = resolveIncludeViaLibrary(schemaLocation, baseDirectory);
                if (fromLibrary != null) resolvedPath = fromLibrary;
            }
        }
        if (!Files.exists(resolvedPath)) {
            ... existing "not found" handling unchanged ...
```
with
```java
    private static Path resolveIncludeViaLibrary(String schemaLocation, Path baseDirectory) {
        try {
            var hit = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared()
                    .resolveSystemId(schemaLocation, baseDirectory != null ? baseDirectory.toUri().toString() : null);
            if (hit.isPresent() && "file".equalsIgnoreCase(hit.get().getScheme())) {
                Path p = Path.of(hit.get());
                return Files.isRegularFile(p) ? p : null;
            }
        } catch (Exception e) {
            logger.debug("Schema Library lookup failed for include '{}': {}", schemaLocation, e.getMessage());
        }
        return null;
    }
```

- [ ] **Step 4: Run tests (new + existing factory import tests)**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactoryLibraryImportTest" --tests "org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactoryImportTest"`
(Use `ls src/test/java/org/fxt/freexmltoolkit/controls/v2/model/ | grep -i import` to find the real existing import test classes and run all of them.)
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/v2/model/XsdNodeFactory.java src/test/java/org/fxt/freexmltoolkit/controls/v2/model/XsdNodeFactoryLibraryImportTest.java
git commit -m "feat(schema-library): resolve V2 imports and includes through the library"
```

---

### Task 9: Hook — legacy `SchemaResolver.resolveReferences`

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolver.java` (`resolveInclude` ~331, `resolveImport` ~491, `resolvePath` ~626)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolverLibraryHookTest.java` (extend)

- [ ] **Step 1: Add failing tests** to `SchemaResolverLibraryHookTest`

```java
    @Test
    void legacyResolveReferencesUsesLibraryForImports(@TempDir Path dir) throws Exception {
        Path types = dir.resolve("far").resolve("types.xsd");
        Files.createDirectories(types.getParent());
        Files.writeString(types, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:types'/>");
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addEntry(SchemaLibraryEntry.user("urn:types", types.toString(), SchemaKind.XSD, "", null));
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        Path main = dir.resolve("main.xsd");
        Files.writeString(main, """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:import namespace="urn:types" schemaLocation="nowhere/types.xsd"/>
                </xs:schema>""");
        ParsedSchema parsed = new XsdParser(XsdParseOptions.defaults()).parse(main);   // real parser entry point; see note
        var imports = parsed.getResolvedImports();
        assertEquals(1, imports.size());
        assertNull(imports.getFirst().error());
        assertEquals(types, imports.getFirst().resolvedPath());
    }
```
Note: find the class that drives `SchemaResolver.resolveReferences(ParsedSchema)` (`grep -rn "resolveReferences(" src/main/java`) and use its public entry point; adapt accessor names (`getResolvedImports()`, record component names `error()`/`resolvedPath()`) to `ParsedSchema.ResolvedImport`.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.xsd.SchemaResolverLibraryHookTest"`
Expected: new test FAIL ("File not found").

- [ ] **Step 3: Implement**

In `resolveImport`, replace the local branch's existence check:
```java
                Path resolvedPath = resolvePath(schemaLocation, baseDir);
                if (!Files.exists(resolvedPath)) {
                    Path fromLibrary = libraryPathFor(namespace, schemaLocation, baseDir);
                    if (fromLibrary != null) {
                        resolvedPath = fromLibrary;
                    } else {
                        failedImportCount++;
                        return new ParsedSchema.ResolvedImport(namespace, schemaLocation, resolvedPath, null, "File not found");
                    }
                }
```
and in the `schemaLocation == null || isBlank` early return, try `libraryPathFor(namespace, null, baseDir)` first and, when non-null, continue with that path as a local import (restructure: compute `Path libraryPath` before the early return; if present set `schemaLocation = libraryPath.toString()`).

In `resolveInclude`, before the `schemaLocation.contains("://")` branch:
```java
        Path fromLibrary = libraryPathFor(null, schemaLocation, baseDir);
        if (fromLibrary != null) {
            schemaLocation = fromLibrary.toString();   // local from here on
        }
```
Helper on `SchemaResolver`:
```java
    /** Schema Library lookup (systemId/catalog, then namespace). Null on miss; never throws. */
    private static Path libraryPathFor(String namespace, String schemaLocation, Path baseDir) {
        try {
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            String base = baseDir != null ? baseDir.toUri().toString() : null;
            if (schemaLocation != null && !schemaLocation.isBlank()) {
                var hit = library.resolveSystemId(schemaLocation, base);
                if (hit.isPresent() && "file".equalsIgnoreCase(hit.get().getScheme())) {
                    Path p = Path.of(hit.get());
                    if (Files.isRegularFile(p)) return p;
                }
            }
            if (namespace != null && !namespace.isBlank()) {
                return library.resolveNamespaceToFile(namespace, org.fxt.freexmltoolkit.domain.SchemaKind.XSD).orElse(null);
            }
        } catch (Exception e) {
            logger.debug("Schema Library lookup failed for '{}': {}", schemaLocation, e.getMessage());
        }
        return null;
    }
```
`resolvePath(String, Path)` must accept an absolute path string (it should already — verify with the test).

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.xsd.SchemaResolverLibraryHookTest"` plus the existing `service/xsd` tests (`ls src/test/java/org/fxt/freexmltoolkit/service/xsd/`).
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolver.java src/test/java/org/fxt/freexmltoolkit/service/xsd/SchemaResolverLibraryHookTest.java
git commit -m "feat(schema-library): legacy SchemaResolver consults the library"
```

---

### Task 10: Hook — Saxon `ResourceResolver` for `doc()`/`document()`

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/XsltTransformationEngine.java:186-215`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/XsltTransformationEngineLibraryResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.service;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class XsltTransformationEngineLibraryResolverTest {

    @AfterEach void tearDown() { ServiceRegistry.reset(); }

    @Test
    void docOfCatalogMappedUriIsServedLocally(@TempDir Path dir) throws Exception {
        Path data = dir.resolve("data.xml");
        Files.writeString(data, "<d>42</d>");
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<uri name='https://example.org/data.xml' uri='data.xml'/></catalog>");
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addCatalog(cat);
        ServiceRegistry.reset();
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/"><xsl:value-of select="doc('https://example.org/data.xml')/d"/></xsl:template>
                </xsl:stylesheet>""";
        XsltTransformationEngine engine = new XsltTransformationEngine();
        var result = engine.transform("<x/>", xslt);      // use the real API: check the class for the simplest (xml, xslt) → result method
        assertEquals("42", result.getOutput().trim());
    }

    @Test
    void unmappedRemoteDocIsStillBlocked(@TempDir Path dir) {
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="/"><xsl:value-of select="doc('https://example.org/other.xml')/d"/></xsl:template>
                </xsl:stylesheet>""";
        XsltTransformationEngine engine = new XsltTransformationEngine();
        var result = engine.transform("<x/>", xslt);
        assertFalse(result.isSuccess());
        assertTrue(result.getErrorMessage().contains("Blocked remote resource"));
    }
```
Adapt to the real `XsltTransformationEngine` API (`grep -n "public .* transform" XsltTransformationEngine.java`, and the result type's accessors).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.XsltTransformationEngineLibraryResolverTest"`
Expected: first test FAIL (blocked), second PASS.

- [ ] **Step 3: Implement** — replace the `setResourceResolver` lambda:

```java
            config.setResourceResolver(request -> {
                if (request != null && request.uri != null) {
                    // Schema Library / catalogs first: a mapped URI is served from its local target.
                    java.util.Optional<java.net.URI> mapped = java.util.Optional.empty();
                    try {
                        mapped = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared()
                                .resolveSystemId(request.uri, request.baseUri);
                    } catch (Exception e) {
                        logger.debug("Schema Library lookup failed for {}: {}", request.uri, e.getMessage());
                    }
                    if (mapped.isPresent() && "file".equalsIgnoreCase(mapped.get().getScheme())) {
                        java.nio.file.Path p = java.nio.file.Path.of(mapped.get());
                        if (java.nio.file.Files.isRegularFile(p)) {
                            return new javax.xml.transform.stream.StreamSource(p.toFile());
                        }
                    }
                }
                if (request != null && isRemoteScheme(schemeOfRequest(request))) {
                    throw new net.sf.saxon.trans.XPathException(
                            "Blocked remote resource reference (doc()/document()): " + request.uri);
                }
                return defaultResolver != null ? defaultResolver.resolve(request) : null;
            });
```
Check the `ResourceRequest` field names in Saxon 12 (`request.uri`, `request.baseUri`, both `String`).

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.XsltTransformationEngineLibraryResolverTest" --tests "org.fxt.freexmltoolkit.service.XsltTransformationEngineTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/XsltTransformationEngine.java src/test/java/org/fxt/freexmltoolkit/service/XsltTransformationEngineLibraryResolverTest.java
git commit -m "feat(schema-library): Saxon doc() resolution honours library and catalog mappings"
```

---

### Task 11: Auto-bind toggle + `EditorHost` XML auto-binding via library

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java`, `src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java`
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java` (`detectSchemaFor` ~2586)
- Test: `src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceTest.java` (extend), `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostLibraryAutoBindTest.java`

**Interfaces:**
- Produces: `PropertiesService.isSchemaLibraryAutoBindEnabled()` (default true), `setSchemaLibraryAutoBindEnabled(boolean)`; property key `schemaLibrary.autoBind.enabled`.

- [ ] **Step 1: Write the failing tests**

Add to `PropertiesServiceTest` (follow the existing pattern there for obtaining a service instance with a temp properties file):
```java
    @Test
    void schemaLibraryAutoBindDefaultsToTrueAndRoundTrips() {
        assertTrue(service.isSchemaLibraryAutoBindEnabled());
        service.setSchemaLibraryAutoBindEnabled(false);
        assertFalse(service.isSchemaLibraryAutoBindEnabled());
        service.setSchemaLibraryAutoBindEnabled(true);
    }
```

`EditorHostLibraryAutoBindTest` (TestFX, same skeleton as `EditorHostXmlSchemaAssistTest`):
```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class EditorHostLibraryAutoBindTest {

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:lib:test"
                       xmlns="urn:lib:test" elementFormDefault="qualified">
              <xs:element name="root"><xs:complexType><xs:sequence>
                <xs:element name="alpha" type="xs:string"/>
              </xs:sequence></xs:complexType></xs:element>
            </xs:schema>
            """;

    private EditorHost host;

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        host = new EditorHost();
        stage.setScene(new Scene(host, 900, 600));
        stage.show();
    }

    @AfterEach
    void tearDown() {
        ServiceRegistry.get(PropertiesService.class).setSchemaLibraryAutoBindEnabled(true);
        ServiceRegistry.reset();
    }

    private Path registerLibraryWith(Path dir, String namespace, Path xsd) {
        var svc = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), new SchemaResourceCache(dir.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addEntry(SchemaLibraryEntry.user(namespace, xsd.toString(), SchemaKind.XSD, "", null));
        ServiceRegistry.register(SchemaLibraryService.class, svc);
        return xsd;
    }

    @Test
    void documentWithNamespaceOnlyIsBoundThroughLibrary(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("lib").resolve("test.xsd");
        Files.createDirectories(xsd.getParent());
        Files.writeString(xsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", xsd);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"><alpha>x</alpha></root>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals(xsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
        assertEquals(EditorHost.SchemaStatus.READY, host.activeSchemaStatusProperty().get());
    }

    @Test
    void manualBindingIsNotOverriddenByLibrary(@TempDir Path tmp) throws Exception {
        Path libXsd = tmp.resolve("lib.xsd");
        Files.writeString(libXsd, XSD);
        Path manualXsd = tmp.resolve("manual.xsd");
        Files.writeString(manualXsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", libXsd);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.setSchemaForActiveDocument(manualXsd.toFile()));
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.redetectSchemaForActiveDocument());
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.sleep(500, TimeUnit.MILLISECONDS);
        assertEquals(manualXsd.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
    }

    @Test
    void toggleOffDisablesLibraryAutoBind(@TempDir Path tmp) throws Exception {
        Path xsd = tmp.resolve("lib.xsd");
        Files.writeString(xsd, XSD);
        registerLibraryWith(tmp, "urn:lib:test", xsd);
        ServiceRegistry.get(PropertiesService.class).setSchemaLibraryAutoBindEnabled(false);
        Path xml = tmp.resolve("doc.xml");
        Files.writeString(xml, "<root xmlns=\"urn:lib:test\"/>\n");

        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(xml.toFile()));
        WaitForAsyncUtils.waitFor(3, TimeUnit.SECONDS, () -> host.getActiveText().map(t -> t.contains("root")).orElse(false));
        WaitForAsyncUtils.sleep(1500, TimeUnit.MILLISECONDS);
        assertNull(host.activeSchemaProperty().get());
    }
}
```
Notes: `ServiceRegistry.register` after `initialize()` must override the factory — check `register()` at `ServiceRegistry.java:134`; if it refuses to overwrite, call `ServiceRegistry.reset()` then `initialize()` then `register(...)` in `registerLibraryWith`. `PropertiesService` writes to the file given by `-Dfxt.properties.file` (Gradle test task sets it) — never the real user file.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostLibraryAutoBindTest"`
Expected: compilation FAIL (missing accessor), then the first auto-bind test fails with a null schema.

- [ ] **Step 3: Implement**

`PropertiesService`:
```java
    /** Whether documents without a schema reference are bound via the Schema Library (default true). */
    boolean isSchemaLibraryAutoBindEnabled();

    void setSchemaLibraryAutoBindEnabled(boolean enabled);
```
`PropertiesServiceImpl` (next to `isXsdAutoSaveEnabled`):
```java
    @Override
    public boolean isSchemaLibraryAutoBindEnabled() {
        return Boolean.parseBoolean(properties.getProperty("schemaLibrary.autoBind.enabled", "true"));
    }

    @Override
    public void setSchemaLibraryAutoBindEnabled(boolean enabled) {
        properties.setProperty("schemaLibrary.autoBind.enabled", String.valueOf(enabled));
        saveProperties(properties);
    }
```
If `PropertiesService` has other implementations in tests, add `default` implementations in the interface (return `true` / no-op).

`EditorHost.detectSchemaFor` — replace the `if (declared.isEmpty()) { return SchemaDetection.NOT_FOUND; }` block:
```java
            if (declared.isEmpty()) {
                // No xsi:schemaLocation — ask the Schema Library by root namespace / root element.
                File fromLibrary = schemaFromLibrary(content);
                if (fromLibrary == null) {
                    return SchemaDetection.NOT_FOUND; // the document references no schema
                }
                if (!tab.view.loadSchema(fromLibrary)) {
                    return SchemaDetection.FAILED;
                }
                return new SchemaDetection(fromLibrary, SchemaStatus.READY);
            }
```
and add:
```java
    /**
     * Schema Library lookup for a document without a schema reference: the document
     * element's namespace (or, without a namespace, its local name) is mapped to an XSD.
     * Returns null when auto-binding is disabled or nothing matches. Worker-thread safe.
     */
    private static File schemaFromLibrary(String content) {
        try {
            if (!org.fxt.freexmltoolkit.di.ServiceRegistry.get(org.fxt.freexmltoolkit.service.PropertiesService.class)
                    .isSchemaLibraryAutoBindEnabled()) {
                return null;
            }
            var root = org.fxt.freexmltoolkit.service.XmlRootElementSniffer.sniff(content).orElse(null);
            if (root == null) return null;
            var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
            var entry = root.namespace().isEmpty()
                    ? library.resolveByRootElement(root.localName())
                    : library.resolveNamespace(root.namespace(), org.fxt.freexmltoolkit.domain.SchemaKind.XSD);
            return entry.flatMap(library::materialize).map(java.nio.file.Path::toFile).orElse(null);
        } catch (Exception e) {
            return null;   // best-effort
        }
    }
```
Set `tab.lastDetectedSchemaLocation` to the library file path when found (`tab.lastDetectedSchemaLocation = fromLibrary.getAbsolutePath();`) so the reconcile short-circuit keeps working. Check `SchemaRebindPolicy.decideRebind` callers (~2476/2500) treat the new detection as `AUTO` — no change needed there, since the origin is decided by the caller, not by `detectSchemaFor`.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.PropertiesServiceTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostLibraryAutoBindTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostXmlSchemaAssistTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/PropertiesService.java src/main/java/org/fxt/freexmltoolkit/service/PropertiesServiceImpl.java src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java src/test/java/org/fxt/freexmltoolkit/service/PropertiesServiceTest.java src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostLibraryAutoBindTest.java
git commit -m "feat(schema-library): auto-bind XML documents by namespace or root element"
```

---

### Task 12: `EditorHost` JSON `$schema` via library

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/service/JsonService.java:515-535` (add `getRawSchemaIdFromJsonContent`)
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java` (`detectJsonSchemaFor` ~2627)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostJsonSchemaLibraryTest.java`, `src/test/java/org/fxt/freexmltoolkit/service/JsonServiceTest.java` (extend)

**Interfaces:**
- Produces: `JsonService.getRawSchemaIdFromJsonContent(String json)` → `Optional<String>` — the `$schema` string **without** the meta-schema filter.

- [ ] **Step 1: Write the failing tests**

`JsonServiceTest` addition:
```java
    @Test
    void rawSchemaIdKeepsMetaSchemaIds() {
        var s = new JsonService();
        String doc = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"}";
        assertTrue(s.getSchemaLocationFromJsonContent(doc).isEmpty());
        assertEquals("https://json-schema.org/draft/2020-12/schema", s.getRawSchemaIdFromJsonContent(doc).orElseThrow());
    }
```

`EditorHostJsonSchemaLibraryTest` (same skeleton as Task 11's test class — `@Start`, `@AfterEach reset`, `registerLibraryWith` but with `SchemaKind.JSON_SCHEMA`):
```java
    @Test
    void jsonSchemaUriMappedInLibraryIsBound(@TempDir Path tmp) throws Exception {
        Path schema = tmp.resolve("person.schema.json");
        Files.writeString(schema, "{\"$id\":\"https://example.org/person.json\",\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        var svc = new SchemaLibraryServiceImpl(tmp.resolve("lib.json"), new SchemaResourceCache(tmp.resolve("cache")),
                () -> new ByteArrayInputStream("{\"version\":1,\"entries\":[]}".getBytes()));
        svc.addEntry(SchemaLibraryEntry.user("https://example.org/person.json", schema.toString(), SchemaKind.JSON_SCHEMA, "", null));
        ServiceRegistry.register(SchemaLibraryService.class, svc);

        Path json = tmp.resolve("p.json");
        Files.writeString(json, "{\"$schema\":\"https://example.org/person.json\",\"name\":\"x\"}");
        WaitForAsyncUtils.waitForAsyncFx(2000, () -> host.openFile(json.toFile()));
        WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS, () -> host.activeSchemaProperty().get() != null);
        assertEquals(schema.toFile().getAbsoluteFile(), host.activeSchemaProperty().get().getAbsoluteFile());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.JsonServiceTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostJsonSchemaLibraryTest"`
Expected: compilation FAIL / null schema. (The JSON test would otherwise try to download `https://example.org/person.json` — with the library hook it must not.)

- [ ] **Step 3: Implement**

`JsonService` — refactor: extract the parsing into `getRawSchemaIdFromJsonContent` and make `getSchemaLocationFromJsonContent` apply the `META_SCHEMA_ID` filter on top of it:
```java
    public Optional<String> getRawSchemaIdFromJsonContent(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return Optional.empty();
            JsonElement schema = root.getAsJsonObject().get("$schema");
            if (schema == null || !schema.isJsonPrimitive() || !schema.getAsJsonPrimitive().isString()) return Optional.empty();
            String location = schema.getAsString().trim();
            return location.isEmpty() ? Optional.empty() : Optional.of(location);
        } catch (JsonSyntaxException e) {
            return Optional.empty();
        }
    }

    public Optional<String> getSchemaLocationFromJsonContent(String json) {
        return getRawSchemaIdFromJsonContent(json).filter(l -> !META_SCHEMA_ID.matcher(l).find());
    }
```

`EditorHost.detectJsonSchemaFor` — before `service.getSchemaLocationFromJsonContent(content)`:
```java
            // Schema Library first: a mapped $schema URI (meta-schema ids included) binds its local file.
            java.util.Optional<String> raw = service.getRawSchemaIdFromJsonContent(content);
            if (raw.isPresent() && org.fxt.freexmltoolkit.di.ServiceRegistry
                    .get(org.fxt.freexmltoolkit.service.PropertiesService.class).isSchemaLibraryAutoBindEnabled()) {
                var library = org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared();
                File mapped = library.resolveJsonSchema(raw.get()).flatMap(library::materialize)
                        .map(java.nio.file.Path::toFile).orElse(null);
                if (mapped != null) {
                    tab.lastDetectedSchemaLocation = raw.get();
                    if (!tab.view.loadSchema(mapped)) return SchemaDetection.FAILED;
                    return new SchemaDetection(mapped, SchemaStatus.READY);
                }
            }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.JsonServiceTest" --tests "org.fxt.freexmltoolkit.controls.shell.editor.EditorHostJsonSchemaLibraryTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/service/JsonService.java src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHost.java src/test/java/org/fxt/freexmltoolkit/service/JsonServiceTest.java src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/EditorHostJsonSchemaLibraryTest.java
git commit -m "feat(schema-library): bind JSON documents via mapped \$schema URIs"
```

---

### Task 13: Activity "Schema Library" + panel skeleton + Mappings tab + entry dialog

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/Activity.java:20` (add constant after `SCHEMA`)
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java:689` (`createSidePanel`)
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanel.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryEntryDialog.java`
- Modify: `src/main/resources/css/unified-shell.css` (append `fxt-lib-*` rules)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanelTest.java`

**Interfaces:**
- Produces: `Activity.SCHEMA_LIBRARY("schema-library", "Schema Library", "bi-collection")`; `SchemaLibraryPanel(EditorHost host, SchemaLibraryService library, SchemaResourceCache cache, XmlService xmlService)` + convenience ctor `SchemaLibraryPanel(EditorHost)` using `SchemaLibraryServiceImpl.shared()`, `SchemaResourceCache.shared()`, `ServiceRegistry.get(XmlService.class)`; `void showCacheTab()`; node ids: `schema-library-tabs`, `library-mappings-table`, `library-filter`, `library-add`, `library-edit`, `library-remove`, `library-toggle`, `library-add-current`, `library-download`, `library-status`; `SchemaLibraryEntryDialog extends Dialog<SchemaLibraryEntry>` with ctor `(SchemaLibraryService library, SchemaLibraryEntry existingOrNull)`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SchemaLibraryPanelTest {

    private static final String BUNDLED = """
            {"version":1,"entries":[{"namespace":"urn:bundled","location":"https://example.org/b.xsd","kind":"XSD","description":"B"}]}""";

    private Path dir;
    private SchemaLibraryServiceImpl library;
    private SchemaResourceCache cache;
    private SchemaLibraryPanel panel;
    private EditorHost host;

    @Start
    void start(Stage stage) throws Exception {
        dir = Files.createTempDirectory("schema-library-panel");
        ServiceRegistry.initialize();
        cache = new SchemaResourceCache(dir.resolve("cache"));
        library = new SchemaLibraryServiceImpl(dir.resolve("lib.json"), cache,
                () -> new ByteArrayInputStream(BUNDLED.getBytes()));
        host = new EditorHost();
        panel = new SchemaLibraryPanel(host, library, cache, ServiceRegistry.get(XmlService.class));
        stage.setScene(new Scene(panel, 480, 640));
        stage.show();
    }

    @AfterEach
    void tearDown() throws Exception {
        ServiceRegistry.reset();
        org.apache.commons.io.FileUtils.deleteDirectory(dir.toFile());
    }

    @SuppressWarnings("unchecked")
    private TableView<SchemaLibraryEntry> table(FxRobot robot) {
        return robot.lookup("#library-mappings-table").queryAs(TableView.class);
    }

    @Test
    void showsBundledEntriesAndReflectsServiceChanges(FxRobot robot) throws Exception {
        assertEquals(1, table(robot).getItems().size());
        Path xsd = dir.resolve("u.xsd");
        Files.writeString(xsd, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema' targetNamespace='urn:u'/>");
        library.addEntry(SchemaLibraryEntry.user("urn:u", xsd.toString(), SchemaKind.XSD, "mine", null));
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(2, table(robot).getItems().size());
    }

    @Test
    void filterNarrowsRows(FxRobot robot) {
        library.addEntry(SchemaLibraryEntry.user("urn:zzz", dir.resolve("z.xsd").toString(), SchemaKind.XSD, "", null));
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#library-filter").write("zzz");
        WaitForAsyncUtils.waitForFxEvents();
        assertEquals(1, table(robot).getItems().size());
        assertEquals("urn:zzz", table(robot).getItems().getFirst().namespace());
    }

    @Test
    void removeIsDisabledForBundledAndRemovesUserEntry(FxRobot robot) {
        SchemaLibraryEntry user = library.addEntry(SchemaLibraryEntry.user("urn:u2", dir.resolve("u2.xsd").toString(), SchemaKind.XSD, "", null));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> table(robot).getSelectionModel().select(
                table(robot).getItems().stream().filter(e -> e.source() == EntrySource.BUNDLED).findFirst().orElseThrow()));
        assertTrue(robot.lookup("#library-remove").queryButton().isDisabled());
        robot.interact(() -> table(robot).getSelectionModel().select(
                table(robot).getItems().stream().filter(e -> e.id().equals(user.id())).findFirst().orElseThrow()));
        assertFalse(robot.lookup("#library-remove").queryButton().isDisabled());
        robot.interact(() -> panel.removeSelectedWithoutConfirm());
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(library.getEntries().stream().noneMatch(e -> e.id().equals(user.id())));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanelTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Activity + wiring**

`Activity.java` after `SCHEMA(...)`:
```java
    SCHEMA_LIBRARY("schema-library", "Schema Library", "bi-collection"),
```
`UnifiedShellView.createSidePanel`:
```java
            case SCHEMA_LIBRARY -> {
                var lib = new org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanel(editorHost);
                schemaLibraryPanel = lib;
                yield lib;
            }
```
plus field `private org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanel schemaLibraryPanel;` and a public method used by Task 16:
```java
    /** Shows the Schema Library activity with its Cache tab selected. */
    public void showSchemaCache() {
        selectionModel.select(Activity.SCHEMA_LIBRARY);
        revealSidePanel();
        if (schemaLibraryPanel != null) schemaLibraryPanel.showCacheTab();
    }
```
Check whether `ActivityBar`/`ActivitySelectionModel` persist the last activity by id (`Activity.fromId`) — adding a constant needs no further change. Run `./gradlew test --tests "org.fxt.freexmltoolkit.controls.icons.IconifyIconCoverageTest"` after the panel exists (find the real package with `grep -rl "class IconifyIconCoverageTest" src/test`).

- [ ] **Step 4: `SchemaLibraryEntryDialog`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.nio.file.Path;

/** Add / edit one Schema Library mapping. Returns the entry (with the original id when editing). */
public class SchemaLibraryEntryDialog extends Dialog<SchemaLibraryEntry> {

    private final TextField namespace = new TextField();
    private final TextField location = new TextField();
    private final ComboBox<SchemaKind> kind = new ComboBox<>();
    private final TextField description = new TextField();
    private final TextField rootElement = new TextField();
    private final Label error = new Label();

    public SchemaLibraryEntryDialog(SchemaLibraryService library, SchemaLibraryEntry existing) {
        setTitle(existing == null ? "Add Schema Mapping" : "Edit Schema Mapping");
        setHeaderText(existing == null
                ? "Map a namespace (or JSON $schema URI) to a local schema file or URL."
                : "Change the mapping for " + (existing.namespace().isEmpty() ? existing.rootElement() : existing.namespace()));
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        kind.getItems().addAll(SchemaKind.values());
        kind.setValue(SchemaKind.XSD);
        namespace.setPromptText("http://www.example.org/ns  (empty for no-namespace XSD)");
        location.setPromptText("/path/to/schema.xsd or https://…/schema.xsd");
        rootElement.setPromptText("document element local name (optional, no-namespace XSD only)");
        error.getStyleClass().add("fxt-lib-error");
        error.setWrapText(true);

        Button browse = new Button("Browse…", new IconifyIcon("bi-folder2-open"));
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select schema file");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Schemas", "*.xsd", "*.json", "*.dtd"),
                    new FileChooser.ExtensionFilter("All files", "*.*"));
            File f = fc.showOpenDialog(getOwner());
            if (f != null) {
                location.setText(f.getAbsolutePath());
                library.entryFromFile(Path.of(f.getAbsolutePath())).ifPresent(pre -> {
                    if (namespace.getText().isBlank()) namespace.setText(pre.namespace());
                    kind.setValue(pre.kind());
                });
            }
        });
        HBox locationRow = new HBox(6, location, browse);
        HBox.setHgrow(location, Priority.ALWAYS);

        GridPane grid = new GridPane();
        grid.setHgap(8); grid.setVgap(8); grid.setPadding(new Insets(12));
        grid.addRow(0, new Label("Namespace / $schema:"), namespace);
        grid.addRow(1, new Label("Location:"), locationRow);
        grid.addRow(2, new Label("Kind:"), kind);
        grid.addRow(3, new Label("Root element:"), rootElement);
        grid.addRow(4, new Label("Description:"), description);
        grid.add(error, 0, 5, 2, 1);
        GridPane.setHgrow(namespace, Priority.ALWAYS);
        GridPane.setHgrow(locationRow, Priority.ALWAYS);
        getDialogPane().setContent(grid);
        getDialogPane().setPrefWidth(560);

        if (existing != null) {
            namespace.setText(existing.namespace());
            location.setText(existing.location());
            kind.setValue(existing.kind());
            description.setText(existing.description());
            rootElement.setText(existing.rootElement() == null ? "" : existing.rootElement());
        }

        // Validate on OK without closing when invalid.
        Button ok = (Button) getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            try {
                org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.validate(build(existing));
                error.setText("");
            } catch (IllegalArgumentException ex) {
                error.setText(ex.getMessage());
                ev.consume();
            }
        });
        setResultConverter(bt -> bt == ButtonType.OK ? build(existing) : null);
    }

    private SchemaLibraryEntry build(SchemaLibraryEntry existing) {
        String id = existing != null ? existing.id() : java.util.UUID.randomUUID().toString();
        return new SchemaLibraryEntry(id, namespace.getText(), location.getText().trim(), kind.getValue(),
                existing != null ? existing.source() : org.fxt.freexmltoolkit.domain.EntrySource.USER,
                existing == null || existing.enabled(), description.getText(), rootElement.getText());
    }
}
```
`SchemaLibraryServiceImpl.validate` must be `public static` (it is package-private in Task 4 — change it).

- [ ] **Step 5: `SchemaLibraryPanel` with the Mappings tab**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.fxt.freexmltoolkit.controls.icons.IconifyIcon;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.domain.*;
import org.fxt.freexmltoolkit.service.*;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Side panel of the Schema Library activity: namespace mappings, registered XML catalogs
 * and the remote schema cache. All disk/network work runs on the shared executor.
 */
public class SchemaLibraryPanel extends VBox {

    private final EditorHost editorHost;
    private final SchemaLibraryService library;
    private final SchemaResourceCache cache;
    private final XmlService xmlService;

    private final TabPane tabs = new TabPane();
    private final Tab mappingsTab = new Tab("Mappings");
    private final Tab catalogsTab = new Tab("Catalogs");
    private final Tab cacheTab = new Tab("Cache");

    // Mappings
    private final TextField filter = new TextField();
    private final FilteredList<SchemaLibraryEntry> filtered;
    private final TableView<SchemaLibraryEntry> mappings = new TableView<>();
    private final Label status = new Label();

    public SchemaLibraryPanel(EditorHost editorHost) {
        this(editorHost, SchemaLibraryServiceImpl.shared(), SchemaResourceCache.shared(),
                ServiceRegistry.get(XmlService.class));
    }

    public SchemaLibraryPanel(EditorHost editorHost, SchemaLibraryService library,
                              SchemaResourceCache cache, XmlService xmlService) {
        this.editorHost = editorHost;
        this.library = library;
        this.cache = cache;
        this.xmlService = xmlService;
        getStyleClass().add("fxt-schema-library-panel");

        Label title = new Label("SCHEMA LIBRARY");
        title.getStyleClass().addAll("fxt-side-panel-title", "fxt-vp-title");
        HBox header = new HBox(title);
        header.getStyleClass().add("fxt-vp-header");
        header.setAlignment(Pos.CENTER_LEFT);

        filtered = new FilteredList<>(library.getEntries());
        mappingsTab.setContent(buildMappingsTab());
        catalogsTab.setContent(new Label("Catalogs — Task 14"));   // replaced in Task 14
        cacheTab.setContent(new Label("Cache — Task 15"));         // replaced in Task 15
        for (Tab t : new Tab[]{mappingsTab, catalogsTab, cacheTab}) {
            t.setClosable(false);
            t.getStyleClass().add("utility-tab");
        }
        mappingsTab.setGraphic(new IconifyIcon("bi-diagram-3"));
        catalogsTab.setGraphic(new IconifyIcon("bi-journal-bookmark"));
        cacheTab.setGraphic(new IconifyIcon("bi-hdd"));
        tabs.setId("schema-library-tabs");
        tabs.getTabs().addAll(mappingsTab, catalogsTab, cacheTab);
        VBox.setVgrow(tabs, Priority.ALWAYS);

        status.setId("library-status");
        status.getStyleClass().add("fxt-lib-status");
        status.setWrapText(true);
        getChildren().addAll(header, tabs, status);
    }

    public void showCacheTab() { tabs.getSelectionModel().select(cacheTab); }

    // ------------------------------------------------------------------ Mappings

    private Node buildMappingsTab() {
        filter.setId("library-filter");
        filter.setPromptText("Filter namespace, location, description…");
        filter.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(q.isEmpty() ? null : e ->
                    e.namespace().toLowerCase(Locale.ROOT).contains(q)
                            || e.location().toLowerCase(Locale.ROOT).contains(q)
                            || e.description().toLowerCase(Locale.ROOT).contains(q)
                            || (e.rootElement() != null && e.rootElement().toLowerCase(Locale.ROOT).contains(q)));
        });

        mappings.setId("library-mappings-table");
        mappings.setItems(filtered);
        mappings.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        mappings.setPlaceholder(new Label("No schema mappings yet. Click Add to map a namespace to a schema."));

        TableColumn<SchemaLibraryEntry, SchemaLibraryEntry> statusCol = new TableColumn<>("");
        statusCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleObjectProperty<>(cd.getValue()));
        statusCol.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(SchemaLibraryEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setTooltip(null); return; }
                SchemaEntryStatus st = library.statusOf(item);
                IconifyIcon icon = switch (st) {
                    case LOCAL_OK, CACHED -> icon("bi-check-circle-fill", org.fxt.freexmltoolkit.controls.theme.SemanticColors.SUCCESS);
                    case NOT_DOWNLOADED -> icon("bi-cloud-download", org.fxt.freexmltoolkit.controls.theme.SemanticColors.INFO);
                    case LOCAL_MISSING -> icon("bi-exclamation-triangle-fill", org.fxt.freexmltoolkit.controls.theme.SemanticColors.WARNING);
                    case ERROR -> icon("bi-x-circle-fill", org.fxt.freexmltoolkit.controls.theme.SemanticColors.DANGER);
                };
                setGraphic(icon);
                setTooltip(new Tooltip(st.name().replace('_', ' ').toLowerCase(Locale.ROOT)
                        + library.lastError(item).map(e -> ": " + e).orElse("")));
            }
        });
        statusCol.setPrefWidth(28); statusCol.setMinWidth(28); statusCol.setMaxWidth(28);

        TableColumn<SchemaLibraryEntry, String> nsCol = new TableColumn<>("Namespace");
        nsCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().namespace().isEmpty() ? "<" + cd.getValue().rootElement() + "> (no namespace)" : cd.getValue().namespace()));
        TableColumn<SchemaLibraryEntry, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().location()));
        TableColumn<SchemaLibraryEntry, String> kindCol = new TableColumn<>("Kind");
        kindCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().kind().label()));
        TableColumn<SchemaLibraryEntry, String> srcCol = new TableColumn<>("Source");
        srcCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().source().name().toLowerCase(Locale.ROOT)));
        TableColumn<SchemaLibraryEntry, Boolean> enabledCol = new TableColumn<>("On");
        enabledCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleBooleanProperty(cd.getValue().enabled()));
        enabledCol.setCellFactory(c -> new CheckBoxTableCell<>(idx -> {
            SchemaLibraryEntry e = mappings.getItems().get(idx);
            var prop = new javafx.beans.property.SimpleBooleanProperty(e.enabled());
            prop.addListener((o, was, now) -> library.setEnabled(e.id(), now));
            return prop;
        }));
        enabledCol.setPrefWidth(40);
        mappings.getColumns().setAll(java.util.List.of(statusCol, nsCol, locCol, kindCol, srcCol, enabledCol));
        mappings.setRowFactory(tv -> {
            TableRow<SchemaLibraryEntry> row = new TableRow<>() {
                @Override protected void updateItem(SchemaLibraryEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().remove("fxt-lib-bundled");
                    if (!empty && item != null && item.source() == EntrySource.BUNDLED) getStyleClass().add("fxt-lib-bundled");
                }
            };
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) openEntry(row.getItem());
            });
            return row;
        });
        mappings.setContextMenu(mappingsContextMenu());

        var selected = mappings.getSelectionModel().selectedItemProperty();
        Button add = toolButton("library-add", "Add mapping…", "bi-plus-circle", this::addEntry);
        Button edit = toolButton("library-edit", "Edit…", "bi-pencil", this::editSelected);
        Button remove = toolButton("library-remove", "Remove", "bi-trash", this::removeSelected);
        Button toggle = toolButton("library-toggle", "Enable / disable", "bi-toggle-on", this::toggleSelected);
        Button addCurrent = toolButton("library-add-current", "Add schema of current document", "bi-file-earmark-plus", this::addCurrentSchema);
        Button download = toolButton("library-download", "Download / verify", "bi-cloud-download", this::downloadSelected);
        edit.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || selected.get().source() != EntrySource.USER, selected));
        remove.disableProperty().bind(edit.disableProperty());
        toggle.disableProperty().bind(selected.isNull());
        download.disableProperty().bind(selected.isNull());
        addCurrent.disableProperty().bind(editorHost.activeSchemaProperty().isNull());
        FlowPane tools = new FlowPane(2, 2, add, edit, remove, toggle, addCurrent, download);
        tools.getStyleClass().add("fxt-schema-tools");

        VBox box = new VBox(4, tools, filter, mappings);
        VBox.setVgrow(mappings, Priority.ALWAYS);
        return box;
    }

    private ContextMenu mappingsContextMenu() {
        MenuItem open = new MenuItem("Open schema", new IconifyIcon("bi-box-arrow-up-right"));
        open.setOnAction(e -> { var s = mappings.getSelectionModel().getSelectedItem(); if (s != null) openEntry(s); });
        MenuItem edit = new MenuItem("Edit…", new IconifyIcon("bi-pencil"));
        edit.setOnAction(e -> editSelected());
        MenuItem remove = new MenuItem("Remove", new IconifyIcon("bi-trash"));
        remove.setOnAction(e -> removeSelected());
        MenuItem copy = new MenuItem("Copy namespace", new IconifyIcon("bi-clipboard"));
        copy.setOnAction(e -> {
            var s = mappings.getSelectionModel().getSelectedItem();
            if (s != null) {
                var cc = new javafx.scene.input.ClipboardContent();
                cc.putString(s.namespace());
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
            }
        });
        return new ContextMenu(open, edit, remove, new SeparatorMenuItem(), copy);
    }

    private void addEntry() {
        new SchemaLibraryEntryDialog(library, null).showAndWait().ifPresent(e -> {
            try { library.addEntry(e); setStatus("Added mapping for " + display(e)); }
            catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
        });
    }

    private void editSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null || s.source() != EntrySource.USER) return;
        new SchemaLibraryEntryDialog(library, s).showAndWait().ifPresent(e -> {
            try { library.updateEntry(e); setStatus("Updated mapping for " + display(e)); }
            catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
        });
    }

    private void removeSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null || s.source() != EntrySource.USER) return;
        if (DialogHelper.showConfirmation("Remove Mapping", "Remove the mapping for " + display(s) + "?",
                "The schema file itself is not deleted.")) {
            removeSelectedWithoutConfirm();
        }
    }

    /** Test seam: removes the selected USER entry without the confirmation dialog. */
    void removeSelectedWithoutConfirm() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s != null && s.source() == EntrySource.USER) {
            mappings.getSelectionModel().clearSelection();
            library.removeEntry(s.id());
            setStatus("Removed mapping for " + display(s));
        }
    }

    private void toggleSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s != null) library.setEnabled(s.id(), !s.enabled());
    }

    private void addCurrentSchema() {
        File xsd = editorHost.activeSchemaProperty().get();
        if (xsd == null) return;
        library.entryFromFile(xsd.toPath()).ifPresent(pre ->
                new SchemaLibraryEntryDialog(library, null) {{
                    // prefill by re-using the edit path with a fresh id
                }}.showAndWait());
    }
```
Replace the awkward anonymous-subclass prefill above with a proper constructor overload: add to `SchemaLibraryEntryDialog` a second constructor `(SchemaLibraryService library, SchemaLibraryEntry existing, boolean asNew)` where `asNew` prefills the fields from `existing` but builds a new USER entry with a fresh id; then `addCurrentSchema` becomes:
```java
        library.entryFromFile(xsd.toPath()).ifPresent(pre ->
                new SchemaLibraryEntryDialog(library, pre, true).showAndWait().ifPresent(e -> {
                    try { library.addEntry(e); setStatus("Added mapping for " + display(e)); }
                    catch (IllegalArgumentException ex) { setStatus(ex.getMessage()); }
                }));
```
Continue the panel:
```java
    private void downloadSelected() {
        SchemaLibraryEntry s = mappings.getSelectionModel().getSelectedItem();
        if (s == null) return;
        if (library instanceof SchemaLibraryServiceImpl impl) impl.clearFailure(s);
        setStatus("Checking " + s.location() + "…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var result = library.materialize(s);
            Platform.runLater(() -> {
                setStatus(result.map(p -> "Available: " + p).orElse("Failed: " + library.lastError(s).orElse("unknown error")));
                mappings.refresh();
            });
        });
    }

    private void openEntry(SchemaLibraryEntry e) {
        setStatus("Opening " + display(e) + "…");
        org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
            var file = library.materialize(e);
            Platform.runLater(() -> file.ifPresentOrElse(p -> { editorHost.openFile(p.toFile()); setStatus(""); },
                    () -> setStatus("Cannot open: " + library.lastError(e).orElse("schema not available"))));
        });
    }

    private static String display(SchemaLibraryEntry e) {
        return e.namespace().isEmpty() ? "<" + e.rootElement() + ">" : e.namespace();
    }

    void setStatus(String text) { status.setText(text == null ? "" : text); }

    private static IconifyIcon icon(String literal, String color) {
        IconifyIcon i = new IconifyIcon(literal);
        i.setIconSize(14);
        i.iconColorProperty().bind(new javafx.beans.property.SimpleObjectProperty<>(javafx.scene.paint.Color.web(color)));
        return i;
    }

    private static Button toolButton(String id, String tooltip, String iconLiteral, Runnable action) {
        IconifyIcon i = new IconifyIcon(iconLiteral);
        i.setIconSize(16);
        Button b = new Button(null, i);
        b.setId(id);
        b.setTooltip(new Tooltip(tooltip));
        b.getStyleClass().add("fxt-tool-button");
        b.setOnAction(e -> action.run());
        return b;
    }
}
```
Check `SemanticColors` constant names (`grep -n "public static final String" src/main/java/org/fxt/freexmltoolkit/controls/theme/SemanticColors.java`) and use the real ones (`SUCCESS`, `DANGER`, `INFO`, `WARNING` or similar). Check `DialogHelper.showConfirmation(String,String,String)` exists (used by `SettingsPanel`). Verify every `bi-*` literal used here exists: `grep -c '"diagram-3"\|"journal-bookmark"\|"hdd"\|"check-circle-fill"\|"cloud-download"\|"exclamation-triangle-fill"\|"x-circle-fill"\|"plus-circle"\|"pencil"\|"trash"\|"toggle-on"\|"file-earmark-plus"\|"box-arrow-up-right"\|"clipboard"\|"collection"' src/main/resources/icons/iconify/bi.json` — replace any missing one with a bundled alternative.

CSS (`unified-shell.css`, append):
```css
/* --- Schema Library panel --------------------------------------------------- */
.fxt-schema-library-panel .table-row-cell.fxt-lib-bundled .table-cell { -fx-text-fill: -fxt-text-muted; -fx-font-style: italic; }
.fxt-lib-status { -fx-padding: 4 8 6 8; -fx-font-size: 11px; -fx-text-fill: -fxt-text-muted; }
.fxt-lib-error { -fx-text-fill: -fxt-danger; }
```
Use the token names that exist in `css/design-tokens.css` (`grep -n "fxt-text-muted\|fxt-danger" src/main/resources/css/design-tokens.css`); substitute the closest existing ones.

- [ ] **Step 6: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanelTest"` and the icon coverage test class.
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/Activity.java src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanel.java src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryEntryDialog.java src/main/java/org/fxt/freexmltoolkit/service/SchemaLibraryServiceImpl.java src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanelTest.java
git commit -m "feat(schema-library): Schema Library activity with mappings table and entry dialog"
```

---

### Task 14: Catalogs tab + import preview dialog

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanel.java`
- Create: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/CatalogImportDialog.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanelTest.java` (extend)

**Interfaces:**
- Produces: node ids `library-catalogs-list`, `library-catalog-add`, `library-catalog-remove`, `library-catalog-reload`, `library-catalog-import`; `CatalogImportDialog extends Dialog<List<SchemaLibraryEntry>>` ctor `(List<SchemaLibraryEntry> preview, Set<String> existingKeys)`; panel methods `void addCatalogFile(Path)` (test seam, no chooser) and `void refreshCatalogs()`.

- [ ] **Step 1: Add failing tests**

```java
    @Test
    void catalogsTabListsRegisteredCatalogsWithEntryCounts(FxRobot robot) throws Exception {
        Path cat = dir.resolve("catalog.xml");
        Files.writeString(cat, "<catalog xmlns='urn:oasis:names:tc:entity:xmlns:xml:catalog'>"
                + "<uri name='urn:c1' uri='c1.xsd'/><uri name='urn:c2' uri='c2.xsd'/></catalog>");
        robot.interact(() -> panel.addCatalogFile(cat));
        WaitForAsyncUtils.waitForFxEvents();
        ListView<SchemaCatalogRef> list = robot.lookup("#library-catalogs-list").queryAs(ListView.class);
        assertEquals(1, list.getItems().size());
        assertEquals(2, library.catalogEntryCount(list.getItems().getFirst().id()));
        assertTrue(robot.lookup(".fxt-lib-catalog-count").queryLabeled().getText().contains("2"));
    }

    @Test
    void unparsableCatalogShowsError(FxRobot robot) throws Exception {
        Path bad = dir.resolve("bad.xml");
        Files.writeString(bad, "<catalog");
        robot.interact(() -> panel.addCatalogFile(bad));
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(robot.lookup(".fxt-lib-catalog-error").queryAll().isEmpty());
    }
```
(add `import javafx.scene.control.ListView;`)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanelTest"`
Expected: compilation FAIL (`addCatalogFile`).

- [ ] **Step 3: Implement `CatalogImportDialog`**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.util.DialogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Lets the user pick which catalog entries to copy into the library as USER mappings. */
public class CatalogImportDialog extends Dialog<List<SchemaLibraryEntry>> {

    public CatalogImportDialog(List<SchemaLibraryEntry> preview, Set<String> existingKeys) {
        setTitle("Import Catalog Entries");
        setHeaderText(preview.size() + " namespace mapping(s) found. Entries already in the library are unchecked.");
        getDialogPane().getStylesheets().addAll(DialogHelper.getThemeStylesheets());
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        List<CheckBox> boxes = new ArrayList<>();
        VBox list = new VBox(4);
        for (SchemaLibraryEntry e : preview) {
            CheckBox cb = new CheckBox(e.namespace() + "  →  " + e.location());
            cb.setSelected(!existingKeys.contains(e.key()));
            cb.setUserData(e);
            boxes.add(cb);
            list.getChildren().add(cb);
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(600, 320);
        CheckBox all = new CheckBox("Select all");
        all.setSelected(boxes.stream().allMatch(CheckBox::isSelected));
        all.setOnAction(ev -> boxes.forEach(b -> b.setSelected(all.isSelected())));
        getDialogPane().setContent(new VBox(8, all, scroll));

        setResultConverter(bt -> {
            if (bt != ButtonType.OK) return null;
            List<SchemaLibraryEntry> chosen = new ArrayList<>();
            for (CheckBox b : boxes) if (b.isSelected()) chosen.add((SchemaLibraryEntry) b.getUserData());
            return chosen;
        });
    }
}
```

- [ ] **Step 4: Catalogs tab in the panel**

Add fields and replace `catalogsTab.setContent(new Label(...))` with `catalogsTab.setContent(buildCatalogsTab());`:
```java
    private final ListView<SchemaCatalogRef> catalogList = new ListView<>();

    private Node buildCatalogsTab() {
        catalogList.setId("library-catalogs-list");
        catalogList.setPlaceholder(new Label("No XML catalogs registered. Add an OASIS catalog.xml to map system IDs and URIs."));
        catalogList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(SchemaCatalogRef ref, boolean empty) {
                super.updateItem(ref, empty);
                if (empty || ref == null) { setGraphic(null); setText(null); return; }
                Label path = new Label(ref.path());
                path.getStyleClass().add("fxt-lib-catalog-path");
                String err = library.catalogErrors().get(ref.id());
                Label info = new Label();
                if (err != null) {
                    info.setText("Error: " + err);
                    info.getStyleClass().add("fxt-lib-catalog-error");
                } else {
                    info.setText(library.catalogEntryCount(ref.id()) + " entries" + (ref.enabled() ? "" : " (disabled)"));
                    info.getStyleClass().add("fxt-lib-catalog-count");
                }
                info.setWrapText(true);
                VBox box = new VBox(2, path, info);
                box.setOpacity(ref.enabled() ? 1.0 : 0.6);
                setGraphic(box);
            }
        });
        catalogList.setOnMouseClicked(ev -> {
            if (ev.getClickCount() == 2) {
                SchemaCatalogRef s = catalogList.getSelectionModel().getSelectedItem();
                if (s != null) editorHost.openFile(s.asPath().toFile());
            }
        });

        var selected = catalogList.getSelectionModel().selectedItemProperty();
        Button add = toolButton("library-catalog-add", "Add catalog…", "bi-plus-circle", this::chooseCatalog);
        Button remove = toolButton("library-catalog-remove", "Remove", "bi-trash", () -> {
            SchemaCatalogRef s = selected.get();
            if (s != null) { catalogList.getSelectionModel().clearSelection(); library.removeCatalog(s.id()); refreshCatalogs(); }
        });
        Button toggle = toolButton("library-catalog-toggle", "Enable / disable", "bi-toggle-on", () -> {
            SchemaCatalogRef s = selected.get();
            if (s != null) { library.setCatalogEnabled(s.id(), !s.enabled()); refreshCatalogs(); }
        });
        Button reload = toolButton("library-catalog-reload", "Reload catalogs", "bi-arrow-clockwise", () -> { library.reloadCatalogs(); refreshCatalogs(); });
        Button importBtn = toolButton("library-catalog-import", "Import entries into Mappings…", "bi-box-arrow-in-down", this::importSelectedCatalog);
        remove.disableProperty().bind(selected.isNull());
        toggle.disableProperty().bind(selected.isNull());
        importBtn.disableProperty().bind(selected.isNull());
        FlowPane tools = new FlowPane(2, 2, add, remove, toggle, reload, importBtn);
        tools.getStyleClass().add("fxt-schema-tools");

        VBox box = new VBox(4, tools, catalogList);
        VBox.setVgrow(catalogList, Priority.ALWAYS);
        refreshCatalogs();
        return box;
    }

    void refreshCatalogs() {
        catalogList.getSelectionModel().clearSelection();
        catalogList.getItems().setAll(library.getCatalogs());
    }

    private void chooseCatalog() {
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Select XML catalog");
        fc.getExtensionFilters().addAll(new javafx.stage.FileChooser.ExtensionFilter("XML catalogs", "*.xml"),
                new javafx.stage.FileChooser.ExtensionFilter("All files", "*.*"));
        File f = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (f != null) addCatalogFile(f.toPath());
    }

    /** Registers {@code catalog} (also used by tests, no chooser). */
    void addCatalogFile(Path catalog) {
        library.addCatalog(catalog);
        refreshCatalogs();
        String err = library.catalogErrors().get(library.getCatalogs().getLast().id());
        setStatus(err != null ? "Catalog added but unparsable: " + err : "Catalog added: " + catalog.getFileName());
    }

    private void importSelectedCatalog() {
        SchemaCatalogRef s = catalogList.getSelectionModel().getSelectedItem();
        if (s == null) return;
        try {
            var preview = library.importCatalog(s.asPath());
            if (preview.isEmpty()) { setStatus("No importable namespace mappings in " + s.asPath().getFileName()); return; }
            var existing = library.getEntries().stream().map(SchemaLibraryEntry::key).collect(java.util.stream.Collectors.toSet());
            new CatalogImportDialog(preview, existing).showAndWait().ifPresent(chosen -> {
                int added = 0;
                for (SchemaLibraryEntry e : chosen) {
                    try { library.addEntry(e.withSource(EntrySource.USER)); added++; }
                    catch (IllegalArgumentException ex) { setStatus("Skipped " + e.namespace() + ": " + ex.getMessage()); }
                }
                setStatus("Imported " + added + " mapping(s) from " + s.asPath().getFileName());
                tabs.getSelectionModel().select(mappingsTab);
            });
        } catch (java.io.IOException e) {
            setStatus("Cannot read catalog: " + e.getMessage());
        }
    }
```
CSS additions:
```css
.fxt-lib-catalog-path { -fx-font-weight: bold; }
.fxt-lib-catalog-count { -fx-text-fill: -fxt-text-muted; -fx-font-size: 11px; }
.fxt-lib-catalog-error { -fx-text-fill: -fxt-danger; -fx-font-size: 11px; }
```

- [ ] **Step 5: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanelTest"` + icon coverage test.
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanel.java src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/CatalogImportDialog.java src/main/resources/css/unified-shell.css src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanelTest.java
git commit -m "feat(schema-library): catalogs tab with registration, status and import preview"
```

---

### Task 15: Cache tab

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanel.java`
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanelTest.java` (extend)

**Interfaces:**
- Consumes: Task 3 `listEntries/removeEntry/refresh/pathOf/getStats/clearCache`, Task 6 `XmlService.listAutoDetectedSchemaCacheDirs/clearAutoDetectedSchemaCache`.
- Produces: node ids `library-cache-table`, `library-cache-filter`, `library-cache-open`, `library-cache-reveal`, `library-cache-refresh`, `library-cache-delete`, `library-cache-clear`, `library-cache-footer`, `library-legacy-cache-clear`; panel methods `void refreshCache()`, `void deleteSelectedCacheEntryWithoutConfirm()`.

- [ ] **Step 1: Add failing tests**

```java
    @SuppressWarnings("unchecked")
    @Test
    void cacheTabListsEntriesAndDeletes(FxRobot robot) throws Exception {
        Path f = cache.getCacheDirectory().resolve("abc.xsd");
        Files.createDirectories(f.getParent());
        Files.writeString(f, "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>");
        cache.getCacheIndex().addOrUpdateEntry(SchemaCacheEntry.builder().localFilename("abc.xsd")
                .remoteUrl("https://example.org/abc.xsd").downloadTimestamp(java.time.Instant.now()).fileSizeBytes(50).build());
        cache.saveIndex();
        robot.interact(() -> { panel.showCacheTab(); panel.refreshCache(); });
        WaitForAsyncUtils.waitForFxEvents();
        TableView<SchemaCacheEntry> table = robot.lookup("#library-cache-table").queryAs(TableView.class);
        assertEquals(1, table.getItems().size());
        assertTrue(robot.lookup("#library-cache-footer").queryLabeled().getText().contains("1 file"));

        robot.interact(() -> table.getSelectionModel().select(0));
        robot.interact(() -> panel.deleteSelectedCacheEntryWithoutConfirm());
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(table.getItems().isEmpty());
        assertFalse(Files.exists(f));
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanelTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Implement the Cache tab** (replace `cacheTab.setContent(new Label(...))` with `cacheTab.setContent(buildCacheTab());` and refresh the tab whenever it is selected: `tabs.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> { if (t == cacheTab) refreshCache(); });`)

```java
    private final ObservableList<SchemaCacheEntry> cacheEntries = FXCollections.observableArrayList();
    private final FilteredList<SchemaCacheEntry> cacheFiltered = new FilteredList<>(cacheEntries);
    private final TableView<SchemaCacheEntry> cacheTable = new TableView<>(cacheFiltered);
    private final Label cacheFooter = new Label();
    private final Label legacyInfo = new Label();

    private Node buildCacheTab() {
        TextField cacheFilter = new TextField();
        cacheFilter.setId("library-cache-filter");
        cacheFilter.setPromptText("Filter URL or namespace…");
        cacheFilter.textProperty().addListener((o, a, text) -> {
            String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            cacheFiltered.setPredicate(q.isEmpty() ? null : e ->
                    e.remoteUrl().toLowerCase(Locale.ROOT).contains(q)
                            || (e.schema() != null && e.schema().targetNamespace() != null
                                && e.schema().targetNamespace().toLowerCase(Locale.ROOT).contains(q)));
        });

        cacheTable.setId("library-cache-table");
        cacheTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        cacheTable.setPlaceholder(new Label("No cached remote schemas."));
        TableColumn<SchemaCacheEntry, String> url = new TableColumn<>("URL");
        url.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().remoteUrl()));
        TableColumn<SchemaCacheEntry, String> ns = new TableColumn<>("Target namespace");
        ns.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().schema() == null || cd.getValue().schema().targetNamespace() == null ? "" : cd.getValue().schema().targetNamespace()));
        TableColumn<SchemaCacheEntry, String> size = new TableColumn<>("Size");
        size.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                org.apache.commons.io.FileUtils.byteCountToDisplaySize(cd.getValue().fileSizeBytes())));
        TableColumn<SchemaCacheEntry, String> when = new TableColumn<>("Downloaded");
        when.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().downloadTimestamp() == null ? "" :
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                .withZone(java.time.ZoneId.systemDefault()).format(cd.getValue().downloadTimestamp())));
        TableColumn<SchemaCacheEntry, String> hits = new TableColumn<>("Hits");
        hits.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().usage() == null ? "0" : String.valueOf(cd.getValue().usage().accessCount())));
        cacheTable.getColumns().setAll(java.util.List.of(url, ns, size, when, hits));
        cacheTable.setRowFactory(tv -> {
            TableRow<SchemaCacheEntry> row = new TableRow<>();
            row.setOnMouseClicked(ev -> { if (ev.getClickCount() == 2 && !row.isEmpty()) editorHost.openFile(cache.pathOf(row.getItem()).toFile()); });
            return row;
        });

        var selected = cacheTable.getSelectionModel().selectedItemProperty();
        Button open = toolButton("library-cache-open", "Open cached file", "bi-box-arrow-up-right",
                () -> { var s = selected.get(); if (s != null) editorHost.openFile(cache.pathOf(s).toFile()); });
        Button reveal = toolButton("library-cache-reveal", "Show in system file manager", "bi-folder2-open", () -> {
            var s = selected.get();
            if (s != null) org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                try { java.awt.Desktop.getDesktop().open(cache.getCacheDirectory().toFile()); }
                catch (Exception e) { Platform.runLater(() -> setStatus("Cannot open folder: " + e.getMessage())); }
            });
        });
        Button refresh = toolButton("library-cache-refresh", "Re-download", "bi-arrow-clockwise", () -> {
            var s = selected.get();
            if (s == null) return;
            setStatus("Refreshing " + s.remoteUrl() + "…");
            org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                var result = cache.refresh(s.remoteUrl());
                Platform.runLater(() -> { setStatus(result.isPresent() ? "Refreshed " + s.remoteUrl() : "Refresh failed for " + s.remoteUrl()); refreshCache(); });
            });
        });
        Button delete = toolButton("library-cache-delete", "Delete cached file", "bi-trash", () -> {
            var s = selected.get();
            if (s != null && DialogHelper.showConfirmation("Delete Cached Schema", "Delete the cached copy of\n" + s.remoteUrl() + "?",
                    "It will be downloaded again on next use.")) {
                deleteSelectedCacheEntryWithoutConfirm();
            }
        });
        Button clear = toolButton("library-cache-clear", "Clear entire cache", "bi-x-octagon", () -> {
            if (DialogHelper.showConfirmation("Clear Schema Cache", "Delete all cached remote schemas?",
                    cache.getCacheDirectory() + "\n\nThis action cannot be undone.")) {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                    int n = cache.clearCache();
                    Platform.runLater(() -> { setStatus("Deleted " + n + " cached file(s)."); refreshCache(); });
                });
            }
        });
        open.disableProperty().bind(selected.isNull());
        refresh.disableProperty().bind(selected.isNull());
        delete.disableProperty().bind(selected.isNull());
        FlowPane tools = new FlowPane(2, 2, open, reveal, refresh, delete, clear);
        tools.getStyleClass().add("fxt-schema-tools");

        cacheFooter.setId("library-cache-footer");
        cacheFooter.getStyleClass().add("fxt-lib-status");

        // Legacy auto-detected cache (~/.freeXmlToolkit/cache/<MD5>/), read-only + clear
        legacyInfo.getStyleClass().add("fxt-lib-status");
        legacyInfo.setWrapText(true);
        Button clearLegacy = toolButton("library-legacy-cache-clear", "Clear auto-detected schema cache", "bi-trash", () -> {
            if (DialogHelper.showConfirmation("Clear Auto-detected Schemas", "Delete all schemas cached from xsi:schemaLocation downloads?",
                    "They are downloaded again when a document referencing them is opened.")) {
                org.fxt.freexmltoolkit.FxtGui.executorService.submit(() -> {
                    int n = xmlService.clearAutoDetectedSchemaCache();
                    Platform.runLater(() -> { setStatus("Deleted " + n + " file(s)."); refreshCache(); });
                });
            }
        });
        TitledPane legacy = new TitledPane("Auto-detected schemas (legacy cache)", new VBox(4, legacyInfo, clearLegacy));
        legacy.setExpanded(false);

        VBox box = new VBox(4, tools, cacheFilter, cacheTable, cacheFooter, legacy);
        VBox.setVgrow(cacheTable, Priority.ALWAYS);
        return box;
    }

    /** Reloads the cache table and footer (FX thread). */
    void refreshCache() {
        cacheTable.getSelectionModel().clearSelection();
        cacheEntries.setAll(cache.listEntries());
        var stats = cache.getStats();
        cacheFooter.setText(stats.totalFiles() + " file(s), " + stats.getTotalSizeFormatted()
                + ", hit ratio " + String.format(Locale.ROOT, "%.0f%%", stats.getHitRatio())
                + "  —  " + cache.getCacheDirectory());
        var dirs = xmlService.listAutoDetectedSchemaCacheDirs();
        legacyInfo.setText(dirs.isEmpty() ? "Empty." : dirs.size() + " cached schema folder(s) under " + dirs.getFirst().getParent());
    }

    /** Test seam: deletes the selected cache entry without confirmation. */
    void deleteSelectedCacheEntryWithoutConfirm() {
        var s = cacheTable.getSelectionModel().getSelectedItem();
        if (s == null) return;
        cacheTable.getSelectionModel().clearSelection();
        cache.removeEntry(s.localFilename());
        refreshCache();
        setStatus("Deleted cached copy of " + s.remoteUrl());
    }
```
Check `CacheStats` component names (`totalFiles()` per the existing test at `SchemaResourceCacheTest`) and `SchemaCacheEntry.usage().accessCount()` / `schema().targetNamespace()` accessor names. `ListView`/`TableView` `setAll` while a row is selected throws — hence the `clearSelection()` calls before every `setAll` (keep them).

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SchemaLibraryPanelTest"` + icon coverage test.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanel.java src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SchemaLibraryPanelTest.java
git commit -m "feat(schema-library): cache tab with listing, refresh, delete and legacy cache clearing"
```

---

### Task 16: Settings integration

**Files:**
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java` (cards list ~257, `loadSettings` ~509, `saveSettings` ~582, TEMP & CACHE card)
- Modify: `src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java` (`openSettingsTab` ~727: wire the link)
- Test: `src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanelSchemaLibraryTest.java`

**Interfaces:**
- Produces: `SettingsPanel.setManageSchemaCacheAction(Runnable)`; node ids `settings-schema-library-autobind`, `settings-manage-schema-cache`.

- [ ] **Step 1: Write the failing test**

```java
package org.fxt.freexmltoolkit.controls.shell.editor;

import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.stage.Stage;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SettingsPanelSchemaLibraryTest {

    private SettingsPanel panel;
    private final AtomicBoolean manageClicked = new AtomicBoolean();

    @Start
    void start(Stage stage) {
        ServiceRegistry.initialize();
        panel = new SettingsPanel();
        panel.setManageSchemaCacheAction(() -> manageClicked.set(true));
        stage.setScene(new Scene(panel, 900, 700));
        stage.show();
    }

    @AfterEach void tearDown() {
        ServiceRegistry.get(PropertiesService.class).setSchemaLibraryAutoBindEnabled(true);
        ServiceRegistry.reset();
    }

    @Test
    void autoBindCheckboxRoundTrips(FxRobot robot) {
        CheckBox cb = robot.lookup("#settings-schema-library-autobind").queryAs(CheckBox.class);
        assertTrue(cb.isSelected());
        robot.interact(() -> cb.setSelected(false));
        robot.interact(panel::saveSettings);
        assertFalse(ServiceRegistry.get(PropertiesService.class).isSchemaLibraryAutoBindEnabled());
    }

    @Test
    void manageLinkInvokesAction(FxRobot robot) {
        robot.interact(() -> robot.lookup("#settings-manage-schema-cache").queryAs(javafx.scene.control.Hyperlink.class).fire());
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(manageClicked.get());
    }
}
```
Check the `SettingsPanel` constructor signature (may take arguments — mirror how `UnifiedShellView.openSettingsTab` builds it).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanelSchemaLibraryTest"`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

`SettingsPanel` fields:
```java
    private final CheckBox schemaLibraryAutoBind = new CheckBox("Use the Schema Library to bind schemas automatically");
    private final Hyperlink manageSchemaCache = new Hyperlink("Manage schema cache…");
    private Runnable manageSchemaCacheAction = () -> { };

    public void setManageSchemaCacheAction(Runnable action) { this.manageSchemaCacheAction = action == null ? () -> { } : action; }
```
In the constructor before the cards list:
```java
        schemaLibraryAutoBind.setId("settings-schema-library-autobind");
        manageSchemaCache.setId("settings-manage-schema-cache");
        manageSchemaCache.setGraphic(iconGraphic("bi-collection"));
        manageSchemaCache.setOnAction(e -> manageSchemaCacheAction.run());
        Label libraryFile = new Label("Library file: " + org.fxt.freexmltoolkit.service.SchemaLibraryServiceImpl.shared().getStorageFile());
        libraryFile.setWrapText(true);
        libraryFile.getStyleClass().add("fxt-settings-hint");
```
Cards: add after the XSD card
```java
                card("SCHEMA LIBRARY", "bi-collection", "#20c997",
                        schemaLibraryAutoBind, libraryFile, manageSchemaCache),
```
and in the TEMP & CACHE card append `manageSchemaCache` is **not** duplicated (a node can have only one parent) — instead add a second hyperlink instance there: create `Hyperlink manageSchemaCache2 = new Hyperlink("Manage schema cache…")` with the same action and id `settings-manage-schema-cache-2`, and place it after `fill(clearCache)`.

`loadSettings()`: `schemaLibraryAutoBind.setSelected(props.isSchemaLibraryAutoBindEnabled());`
`saveSettings()`: `props.setSchemaLibraryAutoBindEnabled(schemaLibraryAutoBind.isSelected());`

`UnifiedShellView.openSettingsTab()` — where the `SettingsPanel` is instantiated: `settings.setManageSchemaCacheAction(this::showSchemaCache);` (method from Task 13).

Check `fxt-settings-hint` exists in the CSS; else use `fxt-lib-status`.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.controls.shell.editor.SettingsPanelSchemaLibraryTest"` plus any existing `SettingsPanel*Test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanel.java src/main/java/org/fxt/freexmltoolkit/controls/shell/UnifiedShellView.java src/test/java/org/fxt/freexmltoolkit/controls/shell/editor/SettingsPanelSchemaLibraryTest.java
git commit -m "feat(schema-library): settings card with auto-bind toggle and cache link"
```

---

### Task 17: Bundled standards list

**Files:**
- Modify: `src/main/resources/schema-library/bundled.json`
- Test: `src/test/java/org/fxt/freexmltoolkit/service/BundledSchemaLibraryTest.java`

- [ ] **Step 1: Write the failing test**

```java
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
        assertTrue(entries.size() >= 30, "expected the full bundled list, got " + entries.size());
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
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.BundledSchemaLibraryTest"`
Expected: FAIL (only 1 entry).

- [ ] **Step 3: Write `bundled.json`**

```json
{
  "version": 1,
  "entries": [
    {"namespace": "http://www.w3.org/XML/1998/namespace", "location": "https://www.w3.org/2001/xml.xsd", "kind": "XSD", "description": "XML namespace attributes (xml:lang, xml:space, xml:base, xml:id)"},
    {"namespace": "http://www.w3.org/2001/XMLSchema", "location": "https://www.w3.org/2001/XMLSchema.xsd", "kind": "XSD", "description": "XML Schema 1.0 schema for schemas"},
    {"namespace": "http://www.w3.org/2000/09/xmldsig#", "location": "https://www.w3.org/TR/2002/REC-xmldsig-core-20020212/xmldsig-core-schema.xsd", "kind": "XSD", "description": "XML Digital Signature (xmldsig-core)"},
    {"namespace": "http://www.w3.org/2001/04/xmlenc#", "location": "https://www.w3.org/TR/2002/REC-xmlenc-core-20021210/xenc-schema.xsd", "kind": "XSD", "description": "XML Encryption (xenc)"},
    {"namespace": "http://www.w3.org/1999/xlink", "location": "https://www.w3.org/1999/xlink.xsd", "kind": "XSD", "description": "XLink 1.1"},
    {"namespace": "http://www.w3.org/2001/XInclude", "location": "https://www.w3.org/2001/XInclude/XInclude.xsd", "kind": "XSD", "description": "XInclude 1.0"},
    {"namespace": "http://www.w3.org/1999/XSL/Transform", "location": "https://www.w3.org/2007/schema-for-xslt20.xsd", "kind": "XSD", "description": "XSLT 2.0/3.0 stylesheet schema"},
    {"namespace": "http://purl.oclc.org/dsdl/schematron", "location": "https://raw.githubusercontent.com/Schematron/schematron/master/trunk/schematron/code/iso-schematron.xsd", "kind": "XSD", "description": "ISO Schematron"},
    {"namespace": "http://www.w3.org/1999/xhtml", "location": "https://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd", "kind": "XSD", "description": "XHTML 1.0 Strict"},
    {"namespace": "http://www.w3.org/2000/svg", "location": "https://www.w3.org/TR/2002/WD-SVG11-20020108/SVG.xsd", "kind": "XSD", "description": "SVG 1.1"},
    {"namespace": "http://www.w3.org/1998/Math/MathML", "location": "https://www.w3.org/Math/XMLSchema/mathml3/mathml3.xsd", "kind": "XSD", "description": "MathML 3"},
    {"namespace": "http://www.w3.org/2005/Atom", "location": "https://raw.githubusercontent.com/w3c/feedvalidator/master/atom.xsd", "kind": "XSD", "description": "Atom 1.0 syndication"},
    {"namespace": "", "location": "https://raw.githubusercontent.com/w3c/feedvalidator/master/rss-2_0.xsd", "kind": "XSD", "description": "RSS 2.0 (no namespace)", "rootElement": "rss"},
    {"namespace": "http://schemas.xmlsoap.org/soap/envelope/", "location": "https://schemas.xmlsoap.org/soap/envelope/", "kind": "XSD", "description": "SOAP 1.1 envelope"},
    {"namespace": "http://www.w3.org/2003/05/soap-envelope", "location": "https://www.w3.org/2003/05/soap-envelope/", "kind": "XSD", "description": "SOAP 1.2 envelope"},
    {"namespace": "http://schemas.xmlsoap.org/wsdl/", "location": "https://schemas.xmlsoap.org/wsdl/", "kind": "XSD", "description": "WSDL 1.1"},
    {"namespace": "http://www.web3d.org/specifications/x3d-3.0.xsd", "location": "https://www.web3d.org/specifications/x3d-3.0.xsd", "kind": "XSD", "description": "X3D 3.0", "rootElement": "X3D"},
    {"namespace": "http://www.web3d.org/specifications/x3d-3.1.xsd", "location": "https://www.web3d.org/specifications/x3d-3.1.xsd", "kind": "XSD", "description": "X3D 3.1"},
    {"namespace": "http://www.web3d.org/specifications/x3d-3.2.xsd", "location": "https://www.web3d.org/specifications/x3d-3.2.xsd", "kind": "XSD", "description": "X3D 3.2"},
    {"namespace": "http://www.web3d.org/specifications/x3d-3.3.xsd", "location": "https://www.web3d.org/specifications/x3d-3.3.xsd", "kind": "XSD", "description": "X3D 3.3"},
    {"namespace": "http://www.web3d.org/specifications/x3d-4.0.xsd", "location": "https://www.web3d.org/specifications/x3d-4.0.xsd", "kind": "XSD", "description": "X3D 4.0 (Extensible 3D graphics)"},
    {"namespace": "http://www.fundsxml.org/XMLSchema/4.2.2", "location": "https://raw.githubusercontent.com/fundsxml/schema/main/FundsXML4.xsd", "kind": "XSD", "description": "FundsXML 4"},
    {"namespace": "http://www.xbrl.org/2003/instance", "location": "https://www.xbrl.org/2003/xbrl-instance-2003-12-31.xsd", "kind": "XSD", "description": "XBRL 2.1 instance"},
    {"namespace": "http://www.xbrl.org/2003/linkbase", "location": "https://www.xbrl.org/2003/xbrl-linkbase-2003-12-31.xsd", "kind": "XSD", "description": "XBRL 2.1 linkbase"},
    {"namespace": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2", "location": "https://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/maindoc/UBL-Invoice-2.1.xsd", "kind": "XSD", "description": "UBL 2.1 Invoice"},
    {"namespace": "urn:oasis:names:specification:ubl:schema:xsd:Order-2", "location": "https://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/maindoc/UBL-Order-2.1.xsd", "kind": "XSD", "description": "UBL 2.1 Order"},
    {"namespace": "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2", "location": "https://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/maindoc/UBL-CreditNote-2.1.xsd", "kind": "XSD", "description": "UBL 2.1 CreditNote"},
    {"namespace": "http://www.ebinterface.at/schema/6p1/", "location": "https://www.ebinterface.at/schema/6p1/Invoice.xsd", "kind": "XSD", "description": "ebInterface 6.1 (Austrian e-invoice)"},
    {"namespace": "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100", "location": "https://raw.githubusercontent.com/ConnectingEurope/eInvoicing-EN16931/master/cii/schema/D16B%20SCRDM%20(Subset)/uncoupled%20clm/CII/uncefact/data/standard/CrossIndustryInvoice_100pD16B.xsd", "kind": "XSD", "description": "UN/CEFACT Cross Industry Invoice D16B (ZUGFeRD/Factur-X)"},
    {"namespace": "https://json-schema.org/draft/2020-12/schema", "location": "https://json-schema.org/draft/2020-12/schema", "kind": "JSON_SCHEMA", "description": "JSON Schema 2020-12 meta-schema"},
    {"namespace": "http://json-schema.org/draft-07/schema#", "location": "https://json-schema.org/draft-07/schema", "kind": "JSON_SCHEMA", "description": "JSON Schema draft-07 meta-schema"},
    {"namespace": "https://json-schema.org/draft/2019-09/schema", "location": "https://json-schema.org/draft/2019-09/schema", "kind": "JSON_SCHEMA", "description": "JSON Schema 2019-09 meta-schema"}
  ]
}
```
Verify each URL once manually (`curl -sIL <url> | head -1` — this is a one-off check by the implementer, not a test); replace dead links with the canonical current location. The FundsXML namespace must match the version the bundled FundsXML extension uses (`grep -rn "fundsxml.org/XMLSchema" src/main/resources | head -3`) — if the schema is versionless, use the namespace the project's FundsXML samples declare. Remove any entry whose URL cannot be verified rather than shipping a dead one.

- [ ] **Step 4: Run tests**

Run: `./gradlew test --tests "org.fxt.freexmltoolkit.service.BundledSchemaLibraryTest" --tests "org.fxt.freexmltoolkit.service.SchemaLibraryServiceImplTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/schema-library/bundled.json src/test/java/org/fxt/freexmltoolkit/service/BundledSchemaLibraryTest.java
git commit -m "feat(schema-library): bundled list of well-known XML/JSON schema namespaces"
```

---

### Task 18: Documentation, CLAUDE.md/rules, full test run

**Files:**
- Create: `docs/schema-library.md`
- Modify: `docs/unified-shell.md` (activity table line 23), `docs/xsd-validation.md`, `docs/json-editor.md`, `docs/schema-support.md`, `mkdocs.yml` (nav after `Schema Support`), `README.md` (feature list), `CLAUDE.md` (shell activities sentence + Known Limitations), `.claude/rules/architecture.md` (panel table + key file locations)

- [ ] **Step 1: Write `docs/schema-library.md`**

Sections (each 1–3 paragraphs, English, screenshots referenced as `img/schema-library-mappings.png`, `img/schema-library-catalogs.png`, `img/schema-library-cache.png`):
1. *What the Schema Library is* — namespace → schema mappings, three sources (your mappings, XML catalogs, bundled standards), where it is used (auto-binding, `xs:import`/`xs:include`, JSON `$schema`, XSLT `doc()`), resolution order **your mappings → catalogs → bundled**.
2. *Mappings tab* — columns, status icons (table: ✔ available / ☁ not downloaded yet / ⚠ file missing / ✖ error), Add/Edit/Remove/Enable, "Add schema of current document", "Download / verify", double-click opens the schema, bundled rows are italic and can only be disabled.
3. *XML catalogs* — supported elements (`system`, `public`, `uri`, `rewriteSystem`, `rewriteURI`, `nextCatalog`, `xml:base`), how to register, reload, and import entries; an example catalog for X3D.
4. *Schema cache* — what is cached (`~/.freeXmlToolkit/cache/schemas`), columns, Open / Refresh / Delete / Clear, the legacy auto-detected cache group.
5. *Bundled standards* — the grouped list from Task 17 (namespaces + short description), note that files are downloaded on first use and need network access once.
6. *Settings* — "Use the Schema Library to bind schemas automatically", library file location, "Manage schema cache…".
7. *Troubleshooting* — document not bound (namespace mismatch, entry disabled, toggle off, manual binding wins), catalog error, unsafe URL rejected.

- [ ] **Step 2: Cross-links and nav**

- `docs/unified-shell.md:23`: add `Schema Library` to the activity list and a short subsection "Schema Library Panel" linking to `schema-library.md`.
- `docs/xsd-validation.md`: in the auto-detection section add the paragraph "If the document has no `xsi:schemaLocation`, the Schema Library is consulted by root namespace (or root element) — see [Schema Library](schema-library.md)."
- `docs/json-editor.md`: same for `$schema` URIs mapped in the library.
- `docs/schema-support.md`: add "XML Catalogs (OASIS)" to the supported list with a link.
- `mkdocs.yml`: `- Schema Library: schema-library.md` right after `- Schema Support: schema-support.md`.
- `README.md`: bullet "Schema Library with OASIS XML catalog support and a schema cache manager".
- `CLAUDE.md`: in the Unified Shell paragraph add "Schema Library" to the activity list; in *Known Limitations → Schema Support* append "OASIS XML catalogs are supported (system/public/uri/rewrite*/nextCatalog) via `SchemaLibraryService`."
- `.claude/rules/architecture.md`: add `SchemaLibraryPanel` to the side-panel row and a row `SchemaLibraryService / SchemaCatalogParser | namespace→schema mappings, catalogs, bundled list; consulted first by all resolvers | service/, service/catalog/`.

- [ ] **Step 3: Screenshots**

Run: `xvfb-run ./gradlew docScreenshots` (see memory note "Doc screenshot generator" — add a scene for the Schema Library activity in the screenshot generator if it enumerates activities explicitly; use a display number that is not `:99`). Verify the three PNGs exist under `docs/img/`.

- [ ] **Step 4: Full test run**

Run: `./gradlew test 2>&1 | tail -30` and check the JUnit XML results for failures (`grep -l "<failure" build/test-results/test/*.xml`). Expected: all green, including `IconifyIconCoverageTest` and `SemanticColorGuardTest`.

- [ ] **Step 5: Commit and push**

```bash
git add docs/schema-library.md docs/unified-shell.md docs/xsd-validation.md docs/json-editor.md docs/schema-support.md docs/img/schema-library-*.png mkdocs.yml README.md CLAUDE.md .claude/rules/architecture.md
git commit -m "docs: Schema Library, XML catalogs and schema cache manager"
git push
```
Then trigger the `docs-updater` and `mkdocs-nav-sync` agents as a final consistency check, and answer GitHub issue #35 (after the next release) pointing to `docs/schema-library.md`.

---

## Plan self-review

- **Spec coverage:** §3.1 → Task 1; §3.2 persistence/CRUD/bundled → Task 4; resolution/materialize/status/import/entryFromFile → Task 5; catalogs → Tasks 2 + 5; §3.3 cache → Task 3, legacy cache → Task 6; §3.4 hooks → Tasks 7 (Xerces/Saxon LS), 8 (V2), 9 (legacy), 10 (XSLT), 11 (EditorHost XML), 12 (EditorHost JSON); `SecureXmlFactory` unchanged (no task, by design); §3.5 status → Task 5; §4 UI → Tasks 13–16; §5 bundled → Task 17; §6 error handling → Tasks 4 (corrupt file), 5 (catalog errors, retry window, unsafe URL), 13 (dialog validation); §7 tests → one per task; §8 docs → Task 18.
- **Type consistency:** `SchemaLibraryServiceImpl.shared()` (Task 7) is used by Tasks 8–13, 16; `resolveNamespaceToFile` (Task 5) by Tasks 7–9, 11; `SchemaResourceCache.shared()` (Task 3) by Tasks 4, 13; `validate` becomes `public static` in Task 13; `XmlRootElementSniffer` is introduced in Task 5 and tested in Task 6.
- **Known adaptation points** (verified at implementation time, flagged in the tasks): real API names of `XsdNodeFactory` file loader / `XsdImport` resolution accessors (Task 8), the legacy parse entry point (Task 9), `XsltTransformationEngine.transform` signature (Task 10), `SettingsPanel` constructor (Task 16), `SemanticColors` constants and CSS token names (Task 13).
