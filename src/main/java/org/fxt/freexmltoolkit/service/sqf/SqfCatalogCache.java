package org.fxt.freexmltoolkit.service.sqf;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.service.sqf.SqfModel.SqfCatalog;

/**
 * Process-wide cache of parsed {@link SqfCatalog}s, keyed by absolute path and
 * invalidated when the Schematron file's last-modified timestamp changes.
 * Parse failures degrade to an (equally cached) empty catalog — SQF discovery
 * must never break validation.
 */
public final class SqfCatalogCache {

    private static final Logger logger = LogManager.getLogger(SqfCatalogCache.class);
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(long lastModified, SqfCatalog catalog) {
    }

    private SqfCatalogCache() {
    }

    /**
     * @param schematronFile the Schematron file (may be {@code null} or missing)
     * @return the (possibly empty) catalog; never {@code null}, never throws
     */
    public static SqfCatalog forFile(File schematronFile) {
        if (schematronFile == null || !schematronFile.exists()) {
            return SqfCatalog.empty(schematronFile);
        }
        String key = schematronFile.getAbsolutePath();
        long lastModified = schematronFile.lastModified();
        Entry entry = CACHE.compute(key, (k, old) -> {
            if (old != null && old.lastModified() == lastModified) {
                return old;
            }
            SqfCatalog catalog;
            try {
                catalog = SqfParser.parse(schematronFile);
            } catch (SqfParseException e) {
                logger.debug("No SQF catalog for {}: {}", schematronFile, e.getMessage());
                catalog = SqfCatalog.empty(schematronFile);
            }
            return new Entry(lastModified, catalog);
        });
        return entry.catalog();
    }

    /** Clears the cache (for tests). */
    static void clear() {
        CACHE.clear();
    }
}
