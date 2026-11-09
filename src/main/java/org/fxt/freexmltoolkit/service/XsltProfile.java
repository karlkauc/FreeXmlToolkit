package org.fxt.freexmltoolkit.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The profile of one XSLT run ({@link XsltProfilingTraceListener}): per template rule, named
 * template and function the calls, the total (inclusive) and the self time; the call tree (for the
 * flame graph); and the self time per stylesheet line.
 *
 * <p><b>Total</b> is the time from entering a component until leaving it, including everything it
 * calls; a recursive component counts only its outermost activations, so no time is counted twice.
 * <b>Self</b> is the total minus the time spent in other templates and functions it calls. A line's
 * self time is the time of the instructions on that line minus nested instructions on other lines.
 */
public final class XsltProfile {

    /** The kind of a profiled component. */
    public enum Kind {
        TEMPLATE_RULE("Template rule"), NAMED_TEMPLATE("Named template"), FUNCTION("Function");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * A template rule, named template or function.
     *
     * @param name     {@code match="…"} (with the mode when it is a named one), the template name, or {@code name#arity}
     * @param systemId the module's URI
     * @param line     the declaration's line
     */
    public record Component(Kind kind, String name, String systemId, int line) {

        /** @return the module's file name */
        public String module() {
            return fileName(systemId);
        }
    }

    /** Totals of one component. */
    public record ComponentStats(Component component, long calls, long totalNanos, long selfNanos) {
    }

    /** Self time of one stylesheet line. */
    public record LineStats(String systemId, int line, long hits, long selfNanos) {

        public String module() {
            return fileName(systemId);
        }
    }

    /** A node of the call tree: one component on one call path (direct recursion folded into one node). */
    public static final class CallNode {
        private final Component component;
        private final Map<Component, CallNode> children = new LinkedHashMap<>();
        long calls;
        long totalNanos;
        long selfNanos;

        private final String rootLabel;

        CallNode(Component component) {
            this(component, null);
        }

        /** @param rootLabel the name of the root (no component), or {@code null} for "(transformation)" */
        CallNode(Component component, String rootLabel) {
            this.component = component;
            this.rootLabel = rootLabel;
        }

        /** @return the component, or {@code null} for the root (the whole run: transformation or query body) */
        public Component component() {
            return component;
        }

        public long calls() {
            return calls;
        }

        public long totalNanos() {
            return totalNanos;
        }

        public long selfNanos() {
            return selfNanos;
        }

        /** @return the children, slowest first */
        public List<CallNode> children() {
            List<CallNode> list = new ArrayList<>(children.values());
            list.sort((a, b) -> Long.compare(b.totalNanos, a.totalNanos));
            return Collections.unmodifiableList(list);
        }

        CallNode child(Component c) {
            return children.computeIfAbsent(c, CallNode::new);
        }

        /** @return the name shown for the node */
        public String label() {
            if (component == null) {
                return rootLabel != null ? rootLabel : "(transformation)";
            }
            return component.name();
        }
    }

    private final List<ComponentStats> components;
    private final CallNode root;
    private final List<LineStats> lines;
    private final long totalNanos;
    private final int warmupRuns;

    XsltProfile(List<ComponentStats> components, CallNode root, List<LineStats> lines, long totalNanos, int warmupRuns) {
        this.components = List.copyOf(components);
        this.root = root;
        this.lines = List.copyOf(lines);
        this.totalNanos = totalNanos;
        this.warmupRuns = warmupRuns;
    }

    /** @return the components, highest self time first */
    public List<ComponentStats> components() {
        return components;
    }

    /** @return the root of the call tree; its total is the whole run */
    public CallNode root() {
        return root;
    }

    /** @return the lines, highest self time first */
    public List<LineStats> lines() {
        return lines;
    }

    /** @return the time of the measured run (transformation and serialization) */
    public long totalNanos() {
        return totalNanos;
    }

    /** @return the runs done before the measured one (warm-up of the Java JIT compiler) */
    public int warmupRuns() {
        return warmupRuns;
    }

    /** @return the number of template and function calls */
    public long totalCalls() {
        return components.stream().mapToLong(ComponentStats::calls).sum();
    }

    /** @return the last path segment of a URI or path */
    static String fileName(String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            return "";
        }
        int slash = Math.max(systemId.lastIndexOf('/'), systemId.lastIndexOf('\\'));
        return slash >= 0 ? systemId.substring(slash + 1) : systemId;
    }

    /** @return nanoseconds as milliseconds with three decimals */
    public static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    /** @return {@code part} as a percentage of the whole run, one decimal */
    public String percent(long part) {
        return totalNanos <= 0 ? "" : String.format(Locale.ROOT, "%.1f %%", 100.0 * part / totalNanos);
    }

    /** @return the profile as JSON (components, lines, call tree) for scripts and the command line */
    public String toJson() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("totalMs", totalNanos / 1_000_000.0);
        json.addProperty("warmupRuns", warmupRuns);
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        for (ComponentStats s : components) {
            com.google.gson.JsonObject o = component(s.component());
            o.addProperty("calls", s.calls());
            o.addProperty("totalMs", s.totalNanos() / 1_000_000.0);
            o.addProperty("selfMs", s.selfNanos() / 1_000_000.0);
            list.add(o);
        }
        json.add("components", list);
        com.google.gson.JsonArray lineList = new com.google.gson.JsonArray();
        for (LineStats l : lines) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("module", l.module());
            o.addProperty("line", l.line());
            o.addProperty("hits", l.hits());
            o.addProperty("selfMs", l.selfNanos() / 1_000_000.0);
            lineList.add(o);
        }
        json.add("lines", lineList);
        json.add("callTree", tree(root, 0));
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json);
    }

    private static com.google.gson.JsonObject component(Component c) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("kind", c.kind().label());
        o.addProperty("name", c.name());
        o.addProperty("module", c.module());
        o.addProperty("line", c.line());
        return o;
    }

    private static com.google.gson.JsonObject tree(CallNode node, int depth) {
        com.google.gson.JsonObject o = node.component() == null ? new com.google.gson.JsonObject() : component(node.component());
        if (node.component() == null) {
            o.addProperty("name", node.label());
        }
        o.addProperty("calls", node.calls());
        o.addProperty("totalMs", node.totalNanos() / 1_000_000.0);
        o.addProperty("selfMs", node.selfNanos() / 1_000_000.0);
        if (depth < 64 && !node.children().isEmpty()) {
            com.google.gson.JsonArray children = new com.google.gson.JsonArray();
            node.children().forEach(child -> children.add(tree(child, depth + 1)));
            o.add("children", children);
        }
        return o;
    }

    /** @return a plain-text report: the components and lines with the highest self time */
    public String toText(int limit) {
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT, "Total %s ms, %d template/function calls%s%n", millis(totalNanos),
                totalCalls(), warmupRuns > 0 ? " (after " + warmupRuns + " warm-up run" + (warmupRuns == 1 ? ")" : "s)") : ""));
        out.append(String.format(Locale.ROOT, "%n%10s %10s %8s %8s  %s%n", "Self ms", "Total ms", "Self %", "Calls", "Template / function"));
        components.stream().limit(limit).forEach(s -> out.append(String.format(Locale.ROOT, "%10s %10s %8s %8d  %s %s (%s:%d)%n",
                millis(s.selfNanos()), millis(s.totalNanos()), percent(s.selfNanos()), s.calls(),
                s.component().kind().label().toLowerCase(Locale.ROOT), s.component().name(), s.component().module(),
                s.component().line())));
        out.append(String.format(Locale.ROOT, "%n%10s %8s %8s  %s%n", "Self ms", "Self %", "Hits", "Line"));
        lines.stream().limit(limit).forEach(l -> out.append(String.format(Locale.ROOT, "%10s %8s %8d  %s:%d%n",
                millis(l.selfNanos()), percent(l.selfNanos()), l.hits(), l.module(), l.line())));
        return out.toString();
    }
}
