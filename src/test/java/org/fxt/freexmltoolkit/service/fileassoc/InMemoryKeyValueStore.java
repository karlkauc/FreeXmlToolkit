package org.fxt.freexmltoolkit.service.fileassoc;

import java.util.HashMap;
import java.util.Map;

/**
 * Test double for {@link KeyValueStore}.
 */
class InMemoryKeyValueStore implements KeyValueStore {

    final Map<String, String> values = new HashMap<>();

    @Override
    public String get(String key) {
        return values.get(key);
    }

    @Override
    public void set(String key, String value) {
        values.put(key, value);
    }
}
