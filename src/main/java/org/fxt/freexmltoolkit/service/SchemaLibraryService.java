package org.fxt.freexmltoolkit.service;

import javafx.collections.ObservableList;
import org.fxt.freexmltoolkit.domain.SchemaCatalogRef;
import org.fxt.freexmltoolkit.domain.SchemaEntryStatus;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Schema Library: persistent namespace → schema mappings fed by user entries,
 * registered OASIS XML catalogs and a bundled list of well-known standards.
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

    // ---- resolution ---------------------------------------------------------------

    Optional<SchemaLibraryEntry> resolveNamespace(String namespace, SchemaKind kind);

    /** user entries, then catalogs (system → uri), then bundled entries (by location) */
    Optional<URI> resolveSystemId(String systemId, String baseUri);

    /**
     * Resolves an XML {@code publicId} through the registered catalogs' {@code public} entries.
     * Library entries have no public identifier, so only catalogs can match here.
     *
     * @param publicId the public identifier to look up, may be null
     * @return the mapped absolute URI, or empty on a miss
     */
    Optional<URI> resolvePublicId(String publicId);

    /**
     * Whether the library may go to the network to materialize a remote entry.
     *
     * <p>Backed by the same {@code fxt.schema.namespaceFallback} system property that gates
     * {@code XsdNodeFactory}'s namespace-URL fallback (default {@code true}); the test suite
     * sets it to {@code false}, so library hooks stay offline in tests. Local entries and
     * already-cached remote entries are served regardless of this flag.</p>
     *
     * @return true when remote library entries may be downloaded
     */
    default boolean isRemoteDownloadAllowed() {
        return Boolean.parseBoolean(System.getProperty("fxt.schema.namespaceFallback", "true"));
    }

    Optional<SchemaLibraryEntry> resolveJsonSchema(String schemaUri);

    Optional<SchemaLibraryEntry> resolveByRootElement(String localName);

    /** never throws */
    Optional<Path> materialize(SchemaLibraryEntry entry);

    /** resolveNamespace + materialize */
    Optional<Path> resolveNamespaceToFile(String namespace, SchemaKind kind);

    SchemaEntryStatus statusOf(SchemaLibraryEntry entry);

    Optional<String> lastError(SchemaLibraryEntry entry);

    /** preview entries, source CATALOG */
    List<SchemaLibraryEntry> importCatalog(Path catalogFile) throws IOException;

    /** catalog id → error text (empty when fine) */
    Map<String, String> catalogErrors();

    /** -1 when unparsable */
    int catalogEntryCount(String catalogId);

    /** prefill (namespace/$id/kind), source USER */
    Optional<SchemaLibraryEntry> entryFromFile(Path schemaFile);
}
