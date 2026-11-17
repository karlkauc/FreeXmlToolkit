package org.fxt.freexmltoolkit.service.progress;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.fxt.freexmltoolkit.di.ServiceRegistry;
import org.fxt.freexmltoolkit.service.PropertiesService;

/**
 * How long a step that cannot count its progress (one XSLT over a whole report) took last time, per size class of
 * its input - for "usually takes about 15 s". Kept in the settings ({@code progress.duration.<step>.<sizeClass>}, in
 * memory only where there are none: tests, command line).
 */
public final class Durations {

    static final String PREFIX = "progress.duration.";
    private static final Map<String, Long> MEMORY = new ConcurrentHashMap<>();

    private Durations() {
    }

    /** @return the size class of an input: powers of four of 1 MB (0 = below 1 MB, 1 = below 4 MB …) */
    static int sizeClass(long chars) {
        int c = 0;
        for (long limit = 1L << 20; chars >= limit && c < 12; limit <<= 2) {
            c++;
        }
        return c;
    }

    /** @return the last duration of {@code step} for an input of this size, or {@code -1} when unknown */
    public static long last(String step, long inputChars) {
        String key = PREFIX + step + "." + sizeClass(inputChars);
        Long memory = MEMORY.get(key);
        if (memory != null) {
            return memory;
        }
        try {
            String value = ServiceRegistry.get(PropertiesService.class).loadProperties().getProperty(key);
            return value == null ? -1 : Long.parseLong(value.strip());
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Remembers how long {@code step} took for an input of this size. */
    public static void record(String step, long inputChars, long millis) {
        String key = PREFIX + step + "." + sizeClass(inputChars);
        MEMORY.put(key, millis);
        try {
            ServiceRegistry.get(PropertiesService.class).set(key, Long.toString(millis));
        } catch (Throwable t) {
            // no settings service (CLI, tests): memory only
        }
    }
}
