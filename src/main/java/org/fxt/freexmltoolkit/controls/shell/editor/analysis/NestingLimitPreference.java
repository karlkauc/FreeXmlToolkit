package org.fxt.freexmltoolkit.controls.shell.editor.analysis;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.controls.v2.editor.statistics.XsdQualityChecker;
import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Loads and saves the deep-nesting limit of the Schema Analysis quality check
 * ({@code schema.analysis.maxElementNesting}) through the {@link PropertiesService}. When the
 * service is unavailable (isolated tests) the default applies and saving is a no-op.
 */
final class NestingLimitPreference {

    static final String KEY = "schema.analysis.maxElementNesting";
    static final int MIN = 1;
    static final int MAX = 30;

    private static final Logger logger = LogManager.getLogger(NestingLimitPreference.class);

    private NestingLimitPreference() {
    }

    /** @return the persisted limit, clamped to {@link #MIN}..{@link #MAX}, or the checker's default */
    static int load() {
        PropertiesService service = service();
        if (service == null) {
            return XsdQualityChecker.DEFAULT_MAX_ELEMENT_NESTING;
        }
        return parse(service.get(KEY));
    }

    /** @param limit the limit to persist (ignored when no service is available) */
    static void save(int limit) {
        PropertiesService service = service();
        if (service != null) {
            service.set(KEY, Integer.toString(clamp(limit)));
        }
    }

    /** Parses a stored value; blank or malformed input yields the default. */
    static int parse(String value) {
        if (value == null || value.isBlank()) {
            return XsdQualityChecker.DEFAULT_MAX_ELEMENT_NESTING;
        }
        try {
            return clamp(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return XsdQualityChecker.DEFAULT_MAX_ELEMENT_NESTING;
        }
    }

    static int clamp(int limit) {
        return Math.max(MIN, Math.min(MAX, limit));
    }

    private static PropertiesService service() {
        try {
            return ServiceRegistry.get(PropertiesService.class);
        } catch (Throwable t) {
            logger.debug("PropertiesService not available; nesting limit is not persisted", t);
            return null;
        }
    }
}
