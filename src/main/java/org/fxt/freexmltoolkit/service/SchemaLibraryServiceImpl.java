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
}
