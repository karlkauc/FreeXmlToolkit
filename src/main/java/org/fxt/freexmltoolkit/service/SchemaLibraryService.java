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
