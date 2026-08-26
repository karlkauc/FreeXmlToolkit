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
