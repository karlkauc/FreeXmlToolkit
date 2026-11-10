package org.fxt.freexmltoolkit.service.ai.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.model.XsdNodeFactory;
import org.fxt.freexmltoolkit.controls.v2.model.XsdSchema;

/**
 * Parsed schemas for the schema tools, keyed by real path and valid while none of the files the schema pulls in
 * (includes, imports) has changed. An agent asks about the same schema many times in a row; parsing a large one
 * takes seconds. Bounded (least recently used out).
 */
public final class SchemaCache {

    private static final Logger logger = LogManager.getLogger(SchemaCache.class);

    /** One cache for the process: the tools are stateless, the schemas are not. */
    public static final SchemaCache SHARED = new SchemaCache(8);

    /** A parsed schema, or why it did not parse. */
    public record Loaded(XsdSchema schema, String error) {
        public boolean ok() {
            return schema != null;
        }
    }

    private record Entry(XsdSchema schema, Map<Path, Long> stamps) {
    }

    private final Map<Path, Entry> entries;

    public SchemaCache(int capacity) {
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Path, Entry> eldest) {
                return size() > capacity;
            }
        };
    }

    /** The schema at {@code file} (a real path inside the roots), parsed or from the cache. */
    public synchronized Loaded load(Path file) {
        Entry entry = entries.get(file);
        if (entry != null && unchanged(entry.stamps())) {
            return new Loaded(entry.schema(), null);
        }
        try {
            XsdSchema schema = new XsdNodeFactory().fromFile(file);
            Map<Path, Long> stamps = new HashMap<>();
            stamps.put(file, modified(file));
            for (Path involved : schema.getAllInvolvedFiles()) {
                stamps.put(involved, modified(involved));
            }
            entries.put(file, new Entry(schema, stamps));
            return new Loaded(schema, null);
        } catch (Exception e) {
            logger.debug("Schema did not parse", e);
            return new Loaded(null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static boolean unchanged(Map<Path, Long> stamps) {
        return stamps.entrySet().stream().allMatch(s -> modified(s.getKey()) == s.getValue());
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return -1;
        }
    }
}
