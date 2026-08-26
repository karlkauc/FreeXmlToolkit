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

    /** user entries, then catalogs (system → uri) */
    Optional<URI> resolveSystemId(String systemId, String baseUri);

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
