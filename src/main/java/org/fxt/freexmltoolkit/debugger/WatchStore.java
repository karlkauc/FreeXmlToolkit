package org.fxt.freexmltoolkit.debugger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * Persists the debugger's watch expressions per main stylesheet, so they are back in the next
 * debug session of the same file. One preference per stylesheet (key: {@code debug.watches.}
 * plus a hash of its normalised path); expressions are newline-separated and escaped like
 * breakpoint conditions.
 */
public final class WatchStore {

    static final String KEY_PREFIX = "debug.watches.";

    private WatchStore() {
    }

    /** @return the watch expressions stored for {@code stylesheetPath} (never {@code null}). */
    public static List<String> load(String stylesheetPath) {
        PropertiesService service = BreakpointStore.properties();
        return service == null ? List.of() : parse(service.get(key(stylesheetPath)));
    }

    /** Stores {@code expressions} for {@code stylesheetPath}; an empty list removes the entry. */
    public static void save(String stylesheetPath, Collection<String> expressions) {
        PropertiesService service = BreakpointStore.properties();
        if (service == null) {
            return;
        }
        try {
            service.set(key(stylesheetPath), format(expressions));
        } catch (RuntimeException ignored) {
            // not persisting watches is not worth an error
        }
    }

    static String key(String stylesheetPath) {
        return KEY_PREFIX + BreakpointStore.pathHash(stylesheetPath);
    }

    static String format(Collection<String> expressions) {
        StringBuilder sb = new StringBuilder();
        if (expressions != null) {
            for (String e : expressions) {
                if (e == null || e.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(BreakpointStore.escape(e.strip()));
            }
        }
        return sb.toString();
    }

    static List<String> parse(String stored) {
        List<String> result = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return result;
        }
        for (String line : stored.split("\n")) {
            String e = BreakpointStore.unescape(line).strip();
            if (!e.isEmpty()) {
                result.add(e);
            }
        }
        return result;
    }

    // ----- symbol breakpoints (stored the same way, per main stylesheet) ----------------------

    static final String SYMBOL_KEY_PREFIX = "debug.symbolBreakpoints.";

    /** @return the template / function / mode breakpoints stored for {@code stylesheetPath}. */
    public static List<SymbolBreakpoint> loadSymbols(String stylesheetPath) {
        PropertiesService service = BreakpointStore.properties();
        return service == null ? List.of() : parseSymbols(service.get(SYMBOL_KEY_PREFIX
                + BreakpointStore.pathHash(stylesheetPath)));
    }

    /** Stores the template / function / mode breakpoints for {@code stylesheetPath}. */
    public static void saveSymbols(String stylesheetPath, Collection<SymbolBreakpoint> symbols) {
        PropertiesService service = BreakpointStore.properties();
        if (service == null) {
            return;
        }
        try {
            service.set(SYMBOL_KEY_PREFIX + BreakpointStore.pathHash(stylesheetPath), formatSymbols(symbols));
        } catch (RuntimeException ignored) {
            // not persisting is not worth an error
        }
    }

    static String formatSymbols(Collection<SymbolBreakpoint> symbols) {
        StringBuilder sb = new StringBuilder();
        if (symbols != null) {
            for (SymbolBreakpoint s : symbols) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(s.format());
            }
        }
        return sb.toString();
    }

    static List<SymbolBreakpoint> parseSymbols(String stored) {
        List<SymbolBreakpoint> result = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return result;
        }
        for (String line : stored.split("\n")) {
            SymbolBreakpoint s = SymbolBreakpoint.parse(line);
            if (s != null && !s.name().isEmpty()) {
                result.add(s);
            }
        }
        return result;
    }
}
