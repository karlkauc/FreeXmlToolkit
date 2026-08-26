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
