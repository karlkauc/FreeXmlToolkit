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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/**
 * Default {@link SchemaLibraryService} implementation.
 *
 * <p>Persists user-added entries and catalogs to {@code schema-library.json} and merges
 * them with a read-only bundled list of well-known standards ({@code /schema-library/bundled.json}).
 * A user entry with the same {@link SchemaLibraryEntry#key()} as a bundled entry overrides it;
 * bundled entries can be disabled (persisted as a key in {@code disabledBundled}) but never removed.</p>
 */
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
    /** Immutable snapshot of the registered catalogs for lock-free reads ({@code catalogsLoaded()}). */
    private volatile List<SchemaCatalogRef> catalogSnapshot = List.of();
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
        catalogSnapshot = List.copyOf(catalogs);
        List<SchemaLibraryEntry> copy = snapshot;
        if (javafx.application.Platform.isFxApplicationThread()) {
            observable.setAll(copy);
        } else {
            try { javafx.application.Platform.runLater(() -> observable.setAll(copy)); }
            catch (IllegalStateException toolkitNotRunning) { observable.setAll(copy); }
        }
        onSnapshotRebuilt();
    }

    // ---- CRUD -------------------------------------------------------------------

    @Override public ObservableList<SchemaLibraryEntry> getEntries() { return readOnly; }

    @Override public List<SchemaCatalogRef> getCatalogs() { synchronized (lock) { return List.copyOf(catalogs); } }

    @Override public Path getStorageFile() { return storageFile; }

    /** Validates an entry before it is stored. Public: used by the "Add Schema" dialog for inline validation. */
    public static void validate(SchemaLibraryEntry entry) {
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

    // ---- catalogs (parsed lazily, invalidated on mutation / mtime change) ------------
    private record LoadedCatalog(SchemaCatalogRef ref, java.nio.file.attribute.FileTime mtime,
                                 org.fxt.freexmltoolkit.service.catalog.ParsedCatalog parsed, String error) { }
    private volatile List<LoadedCatalog> loadedCatalogs = null;
    private final Map<String, String> sessionErrors = new java.util.concurrent.ConcurrentHashMap<>();   // entry id → error
    private final Map<String, Long> failedAt = new java.util.concurrent.ConcurrentHashMap<>();          // entry id → epoch millis
    // Instance (not static) so tests can shrink the window on one SchemaLibraryServiceImpl
    // without leaking mutable global state into other instances/tests.
    private long retryAfterMs = 10 * 60 * 1000L;

    /** Test hook: shrink/grow the remote-download retry window (default 10 minutes). */
    void setRetryAfterMs(long ms) { this.retryAfterMs = ms; }

    /** Invalidates the lazily-parsed catalog cache whenever the merged snapshot changes. */
    private void onSnapshotRebuilt() { loadedCatalogs = null; }

    private List<LoadedCatalog> catalogsLoaded() {
        List<LoadedCatalog> current = loadedCatalogs;
        List<SchemaCatalogRef> refs = catalogSnapshot;   // lock-free read; getCatalogs() takes `lock`
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
        // CATALOG sits between USER and BUNDLED: a registered catalog must be able to override a
        // bundled entry (spec §3.2, the X3D use case), so it is consulted before the bundled fallback.
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
            return base != null && !base.isBlank() ? normalizeFileUri(URI.create(base).resolve(u)) : null;
        } catch (IllegalArgumentException e) { return null; }
    }

    /**
     * Restores the canonical {@code file:///path} (empty-authority) form. {@link URI#resolve(String)}
     * drops the empty-authority marker when merging a relative reference into a {@code file:} base,
     * turning {@code file:///a/b} into {@code file:/a/b}; that differs from what {@link Path#toUri()}
     * produces, so normalize it back before comparing against entry locations.
     * (Same fix as {@code SchemaCatalogParser.normalizeFileUri}.)
     */
    private static URI normalizeFileUri(URI u) {
        if ("file".equalsIgnoreCase(u.getScheme()) && u.getAuthority() == null
                && u.getPath() != null && u.getPath().startsWith("/")) {
            try { return new URI(u.getScheme(), "", u.getPath(), u.getQuery(), u.getFragment()); }
            catch (java.net.URISyntaxException e) { return u; }
        }
        return u;
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
            Path p = localPathOrNull(entry.location());
            if (p == null) { sessionErrors.put(entry.id(), "Invalid path: " + entry.location()); return Optional.empty(); }
            if (Files.isRegularFile(p)) { sessionErrors.remove(entry.id()); return Optional.of(p); }
            sessionErrors.put(entry.id(), "File not found: " + p);
            return Optional.empty();
        }
        if (!PathValidator.isUrlSafeToAccess(entry.location())) {
            sessionErrors.put(entry.id(), "URL not allowed: " + entry.location());
            return Optional.empty();
        }
        Long failed = failedAt.get(entry.id());
        if (failed != null && System.currentTimeMillis() - failed < retryAfterMs && !cache.isCached(entry.location())) {
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
        // Local status always reflects the file system, never a remembered failure: a local
        // materialize() failure just means the file is (still) missing, not a distinct ERROR state.
        if (!entry.isRemote()) {
            Path p = localPathOrNull(entry.location());
            return p != null && Files.isRegularFile(p) ? SchemaEntryStatus.LOCAL_OK : SchemaEntryStatus.LOCAL_MISSING;
        }
        if (sessionErrors.containsKey(entry.id())) return SchemaEntryStatus.ERROR;
        return cache.isCached(entry.location()) ? SchemaEntryStatus.CACHED : SchemaEntryStatus.NOT_DOWNLOADED;
    }

    /** {@code Path.of(location)} without the {@link java.nio.file.InvalidPathException} risk (malformed location). */
    private static Path localPathOrNull(String location) {
        try { return Path.of(location); } catch (RuntimeException e) { return null; }
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
}
