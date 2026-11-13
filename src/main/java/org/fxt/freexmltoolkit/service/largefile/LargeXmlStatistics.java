package org.fxt.freexmltoolkit.service.largefile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * The element statistics of a large XML file in one streaming pass: how many elements there are, how
 * deep they nest, and per element path (names without positions, e.g. {@code /orders/order/item}) how
 * often it occurs, how often per parent, its attributes, whether it carries text, and a sample value.
 */
public final class LargeXmlStatistics {

    /** At most this many distinct paths are kept; the rest is only counted. */
    public static final int MAX_PATHS = 10_000;
    /** At most this many attribute names per path. */
    static final int MAX_ATTRIBUTES = 50;
    /** Samples are cut to this length. */
    static final int SAMPLE_LENGTH = 80;

    /**
     * One element path.
     *
     * @param path       the names from the root, e.g. {@code /orders/order}
     * @param depth      1 for the root
     * @param count      how many elements have this path
     * @param minPerParent the fewest occurrences in one parent (0 when some parents have none)
     * @param maxPerParent the most occurrences in one parent
     * @param attributes the attribute names seen (without namespace declarations)
     * @param hasText    whether any of them has non-whitespace text of its own
     * @param sample     the first non-blank text, cut, or empty
     */
    public record PathStats(String path, int depth, long count, long minPerParent, long maxPerParent,
                            List<String> attributes, boolean hasText, String sample) {
    }

    /**
     * The result.
     *
     * @param elements     the number of elements
     * @param attributes   the number of attributes (without namespace declarations)
     * @param maxDepth     the deepest nesting (1 = only the root)
     * @param paths        the paths in order of first appearance
     * @param droppedPaths the number of distinct paths beyond {@link #MAX_PATHS} (only counted in {@code elements})
     */
    public record Result(long elements, long attributes, int maxDepth, List<PathStats> paths, long droppedPaths) {
    }

    /** Statistics being collected for one path. */
    private static final class Entry {
        final String path;
        final int depth;
        final Entry parent;
        long count;
        long min = Long.MAX_VALUE;
        long max;
        /** parents (of the parent path) that contained at least one */
        long parentsWith;
        final Set<String> attributes = new LinkedHashSet<>();
        boolean hasText;
        String sample = "";
        final Map<String, Entry> children = new HashMap<>();
        /** the serial number of the parent element the current run of occurrences belongs to */
        long runParent = -1;
        long runCount;

        Entry(String path, int depth, Entry parent) {
            this.path = path;
            this.depth = depth;
            this.parent = parent;
        }

        /** Counts one occurrence inside the parent element with the given serial number. */
        void occurrence(long parentSerial) {
            count++;
            if (runParent != parentSerial) {
                finishRun();
                runParent = parentSerial;
            }
            runCount++;
        }

        /** Folds the occurrences in the last parent into min/max (the elements of one parent are contiguous). */
        void finishRun() {
            if (runCount > 0) {
                min = Math.min(min, runCount);
                max = Math.max(max, runCount);
                parentsWith++;
            }
            runCount = 0;
        }
    }

    private LargeXmlStatistics() {
    }

    /** Scans the whole file. */
    public static Result scan(LargeXmlStructure structure, BooleanSupplier cancelled, LongConsumer progress)
            throws IOException {
        Map<String, Entry> entries = new LinkedHashMap<>();
        Entry document = new Entry("", 0, null);
        document.count = 1;
        // the open elements: their entries (null beyond MAX_PATHS) and serial numbers
        List<Entry> stack = new ArrayList<>();
        stack.add(document);
        long[] totals = new long[3]; // elements, attributes, dropped paths
        int[] maxDepth = {0};
        Set<String> dropped = new java.util.HashSet<>();
        structure.scanAll(new LargeXmlStructure.Listener() {
            /** the serial number of each open element (index = stack position; 0 = the document) */
            long[] serials = new long[64];
            long nextSerial = 1;

            @Override
            public boolean wantsText(int depth) {
                Entry top = stack.getLast();
                return top != null && top != document && !top.hasText;
            }

            @Override
            public void text(String text) {
                Entry entry = stack.getLast();
                if (entry == null || entry == document) {
                    return;
                }
                String value = text.strip();
                if (!value.isEmpty()) {
                    entry.hasText = true;
                    if (entry.sample.isEmpty()) {
                        entry.sample = value.length() > SAMPLE_LENGTH ? value.substring(0, SAMPLE_LENGTH) + "…" : value;
                    }
                }
            }

            @Override
            public boolean start(String name, long offset, LargeXmlStructure.Tag tag, boolean selfClosing, long afterTag) {
                totals[0]++;
                Entry parent = stack.getLast();
                long parentSerial = serials[stack.size() - 1];
                Entry entry = null;
                if (parent != null) {
                    entry = parent.children.get(name);
                    if (entry == null) {
                        String path = parent.path + "/" + name;
                        if (entries.size() < MAX_PATHS) {
                            entry = new Entry(path, parent.depth + 1, parent);
                            parent.children.put(name, entry);
                            entries.put(path, entry);
                        } else if (dropped.size() < 1_000_000 && dropped.add(path)) {
                            totals[2]++;
                        }
                    }
                }
                int depth = stack.size();
                maxDepth[0] = Math.max(maxDepth[0], depth);
                String attributes = tag.text();
                if (attributes.indexOf('=') >= 0) {
                    Entry target = entry;
                    forEachAttributeName(attributes, attribute -> {
                        totals[1]++;
                        if (target != null && target.attributes.size() < MAX_ATTRIBUTES) {
                            target.attributes.add(attribute);
                        }
                    });
                }
                if (entry != null) {
                    entry.occurrence(parentSerial);
                }
                if (!selfClosing) {
                    if (stack.size() == serials.length) {
                        serials = java.util.Arrays.copyOf(serials, serials.length * 2);
                    }
                    serials[stack.size()] = nextSerial++;
                    stack.add(entry);
                }
                return true;
            }

            @Override
            public boolean end(String name, long afterTag) {
                if (stack.size() <= 1) {
                    return true;
                }
                stack.removeLast();
                return true;
            }
        }, cancelled, progress);
        entries.values().forEach(Entry::finishRun);
        List<PathStats> paths = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            long parents = entry.parent == null ? 1 : entry.parent.count;
            long min = entry.parentsWith < parents ? 0 : (entry.min == Long.MAX_VALUE ? 0 : entry.min);
            paths.add(new PathStats(entry.path, entry.depth, entry.count, min, entry.max,
                    List.copyOf(entry.attributes), entry.hasText, entry.sample));
        }
        return new Result(totals[0], totals[1], maxDepth[0], List.copyOf(paths), totals[2]);
    }

    /** Calls {@code sink} with every attribute name of a start tag's text, namespace declarations left out. */
    static void forEachAttributeName(String attributes, Consumer<String> sink) {
        int n = attributes.length();
        int i = 0;
        while (i < n) {
            while (i < n && (Character.isWhitespace(attributes.charAt(i)) || attributes.charAt(i) == '/')) {
                i++;
            }
            int nameStart = i;
            while (i < n && attributes.charAt(i) != '=' && !Character.isWhitespace(attributes.charAt(i))) {
                i++;
            }
            int nameEnd = i;
            while (i < n && Character.isWhitespace(attributes.charAt(i))) {
                i++;
            }
            if (i >= n || attributes.charAt(i) != '=') {
                return;
            }
            i++;
            while (i < n && Character.isWhitespace(attributes.charAt(i))) {
                i++;
            }
            if (i >= n) {
                return;
            }
            char quote = attributes.charAt(i);
            if (quote != '"' && quote != '\'') {
                return;
            }
            int close = attributes.indexOf(quote, i + 1);
            if (close < 0) {
                return;
            }
            i = close + 1;
            String name = attributes.substring(nameStart, nameEnd);
            if (!name.isEmpty() && !name.equals("xmlns") && !name.startsWith("xmlns:")) {
                sink.accept(name);
            }
        }
    }
}
