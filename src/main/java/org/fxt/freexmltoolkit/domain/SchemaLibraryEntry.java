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

    /**
     * The identity used for bundled overrides and duplicate detection.
     *
     * <p>For a namespaced entry this is {@code kind|namespace}. No-namespace entries
     * (matched by root element or by their location, e.g. the X3D schemas) would all
     * collapse onto {@code kind|} instead, so for them the key is
     * {@code kind||rootElement|location} — which keeps several versions of the same
     * no-namespace standard distinguishable.</p>
     *
     * @return the entry's identity key
     */
    public String key() {
        return namespace.isEmpty()
                ? kind + "||" + rootElement + "|" + location
                : kind + "|" + namespace;
    }

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
