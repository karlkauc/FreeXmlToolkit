package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Which displayed values changed since the previous pause, per frame scope. Showing the same
 * state again (the pane fills Variables once directly and once through the frame selection,
 * or the user re-selects a frame) keeps the markers instead of comparing the state with itself.
 */
final class ChangeTracker {

    /** The values shown at the pause before and at the current one. */
    record History(Map<String, String> previous, Map<String, String> current) { }

    private static final int MAX_SCOPES = 64;

    private ChangeTracker() {
    }

    /** A bounded store of histories per scope key (least recently used scopes dropped). */
    static Map<String, History> newStore() {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, History> e) {
                return size() > MAX_SCOPES;
            }
        };
    }

    /**
     * Records {@code now} for {@code scope} and returns the names whose value differs from the
     * previous pause there. With {@code newCounts}, names the previous pause did not have count
     * as changed.
     */
    static Set<String> record(Map<String, History> store, String scope, Map<String, String> now, boolean newCounts) {
        History h = store.get(scope);
        if (h == null) {
            store.put(scope, new History(null, Map.copyOf(withoutNulls(now))));
            return Set.of();
        }
        Map<String, String> current = Map.copyOf(withoutNulls(now));
        if (!current.equals(h.current())) {
            h = new History(h.current(), current);
            store.put(scope, h);
        }
        Set<String> changed = new HashSet<>();
        if (h.previous() != null) {
            for (Map.Entry<String, String> e : h.current().entrySet()) {
                boolean known = h.previous().containsKey(e.getKey());
                if (known ? !Objects.equals(h.previous().get(e.getKey()), e.getValue()) : newCounts) {
                    changed.add(e.getKey());
                }
            }
        }
        return changed;
    }

    private static Map<String, String> withoutNulls(Map<String, String> values) {
        Map<String, String> out = new LinkedHashMap<>();
        values.forEach((k, v) -> out.put(k, v == null ? "" : v));
        return out;
    }
}
