package org.fxt.freexmltoolkit.service.telemetry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregated editing statistics: how often each editing command (structured editors) or
 * text-editor feature (IntelliSense, search, …) is used. Counting is per session and
 * domain; nothing is sent per keystroke — {@link #flush()} emits one {@code edit_summary}
 * event per domain ({@code meta.domain} plus one counter per command class, top
 * {@value #MAX_KEYS}), at the latest on exit.
 *
 * <p>Only command <b>class names</b> and fixed feature ids are counted — never element
 * names, values or text.
 */
public final class EditStats {

    /** Structured XSD editor (XsdCommand). */
    public static final String DOMAIN_XSD = "xsd";
    /** Structured XML editor (XmlCommand, Tree/Graphic). */
    public static final String DOMAIN_XML = "xml";
    /** Structured JSON editor (JsonCommand). */
    public static final String DOMAIN_JSON = "json";
    /** Text editor features (IntelliSense, find/replace, …). */
    public static final String DOMAIN_TEXT = "text";

    static final int MAX_KEYS = 20;
    /** A domain is flushed early once it reaches this many counted operations. */
    static final int EARLY_FLUSH_AT = 1000;

    private static final Map<String, Map<String, AtomicInteger>> COUNTS = new ConcurrentHashMap<>();

    private EditStats() {
    }

    /** A command of the given class was executed (successfully) in {@code domain}. */
    public static void executed(String domain, Class<?> commandClass) {
        count(domain, commandName(commandClass));
    }

    /** An undo step in {@code domain}. */
    public static void undone(String domain) {
        count(domain, "undo");
    }

    /** A redo step in {@code domain}. */
    public static void redone(String domain) {
        count(domain, "redo");
    }

    /**
     * A text-editor feature was used.
     *
     * @param feature fixed id, e.g. {@code completion_shown}, {@code completion_accepted}
     */
    public static void feature(String feature) {
        count(DOMAIN_TEXT, feature);
    }

    private static void count(String domain, String key) {
        if (domain == null || key == null) {
            return;
        }
        try {
            Map<String, AtomicInteger> m = COUNTS.computeIfAbsent(domain, d -> new ConcurrentHashMap<>());
            m.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
            if (total(m) >= EARLY_FLUSH_AT) {
                flushDomain(domain);
            }
        } catch (Throwable ignored) {
            // statistics must never disturb editing
        }
    }

    /** Emits one {@code edit_summary} event per domain with counts and resets them. */
    public static void flush() {
        for (String domain : List.copyOf(COUNTS.keySet())) {
            flushDomain(domain);
        }
    }

    private static void flushDomain(String domain) {
        Map<String, AtomicInteger> m = COUNTS.remove(domain);
        if (m == null || m.isEmpty()) {
            return;
        }
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        m.forEach((k, v) -> snapshot.put(k, v.get()));
        Map<String, Integer> top = topCounts(snapshot);
        int total = snapshot.values().stream().mapToInt(Integer::intValue).sum();
        try {
            Telemetry.get().track("edit_summary", TelemetryEvent.Category.ACTION, b -> {
                b.meta("domain", domain);
                top.forEach(b::meta);
                b.meta("total", total);
            });
        } catch (Throwable ignored) {
            // telemetry must never disturb the caller
        }
    }

    /**
     * The {@value #MAX_KEYS} most frequent keys (ties by name), the rest summed as
     * {@code other}.
     */
    static Map<String, Integer> topCounts(Map<String, Integer> counts) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> sorted = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .toList();
        int other = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < MAX_KEYS) {
                out.put(sorted.get(i).getKey(), sorted.get(i).getValue());
            } else {
                other += sorted.get(i).getValue();
            }
        }
        if (other > 0) {
            out.merge("other", other, Integer::sum);
        }
        return out;
    }

    /** Class simple name without the {@code Command} suffix, lower snake case. */
    static String commandName(Class<?> type) {
        if (type == null) {
            return "unknown";
        }
        String name = type.getSimpleName();
        if (name.isEmpty()) {
            return "anonymous";
        }
        if (name.endsWith("Command") && name.length() > "Command".length()) {
            name = name.substring(0, name.length() - "Command".length());
        }
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static int total(Map<String, AtomicInteger> m) {
        int sum = 0;
        for (AtomicInteger v : m.values()) {
            sum += v.get();
        }
        return sum;
    }

    /** Drops all counts (tests). */
    static void reset() {
        COUNTS.clear();
    }
}
