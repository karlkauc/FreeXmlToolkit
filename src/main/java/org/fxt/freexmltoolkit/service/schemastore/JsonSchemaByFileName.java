package org.fxt.freexmltoolkit.service.schemastore;

import java.nio.file.Path;
import java.util.Optional;

import org.fxt.freexmltoolkit.domain.EntrySource;
import org.fxt.freexmltoolkit.domain.SchemaKind;
import org.fxt.freexmltoolkit.domain.SchemaLibraryEntry;
import org.fxt.freexmltoolkit.service.SchemaLibraryService;

/**
 * The JSON Schema of a JSON or YAML file chosen by the file's name: a Schema Library entry's file
 * patterns first, then the SchemaStore catalog. Shared by the editor and {@code fxt-cli validate};
 * UI-free, downloads nothing (the caller refreshes the catalog and materializes the entry).
 */
public final class JsonSchemaByFileName {

    private JsonSchemaByFileName() {
    }

    /**
     * A schema chosen by a file name.
     *
     * @param entry       where the schema is (the library entry, or one made for a SchemaStore schema)
     * @param pattern     the pattern that matched
     * @param catalogName the SchemaStore schema's name, {@code null} for a library entry
     */
    public record Hit(SchemaLibraryEntry entry, String pattern, String catalogName) {
        /** @return whether the hit comes from the SchemaStore catalog */
        public boolean fromSchemaStore() {
            return catalogName != null;
        }
    }

    /**
     * @param file       the file (null or without a name: no hit)
     * @param library    the Schema Library, {@code null} to skip its patterns
     * @param catalog    the SchemaStore catalog, {@code null} when it is not used
     * @return the hit, empty when nothing matches
     */
    public static Optional<Hit> find(Path file, SchemaLibraryService library, SchemaStoreCatalog catalog) {
        if (file == null || file.getFileName() == null) {
            return Optional.empty();
        }
        if (library != null) {
            Optional<SchemaLibraryEntry> entry = library.resolveByFileName(file);
            if (entry.isPresent()) {
                return Optional.of(new Hit(entry.get(), FileNamePatterns.bestMatch(entry.get().fileMatch(), file), null));
            }
        }
        if (catalog == null) {
            return Optional.empty();
        }
        return catalog.match(file).map(m -> new Hit(
                new SchemaLibraryEntry("schemastore:" + m.schema().url(), "", m.schema().url(),
                        SchemaKind.JSON_SCHEMA, EntrySource.CATALOG, true, m.schema().name(), null),
                m.pattern(), m.schema().name()));
    }
}
