package org.fxt.freexmltoolkit.service.fileassoc;

/**
 * Minimal persistence abstraction used by the association strategies to remember
 * previously configured default handlers (for restore on unregister). Backed by
 * {@code PropertiesService} in production; trivially faked in tests.
 */
public interface KeyValueStore {

    /**
     * @return the stored value, or null/empty if absent
     */
    String get(String key);

    void set(String key, String value);
}
