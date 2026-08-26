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
