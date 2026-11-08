package org.fxt.freexmltoolkit.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.Mode;

/**
 * Measures an XSLT run for {@link XsltProfile}: Saxon reports entering and leaving every traced
 * instruction (the stylesheet must be compiled with tracing); this listener keeps a stack of them
 * and adds the elapsed time to the component (template rule, named template, function), its node in
 * the call tree and the instruction's line. Only time is measured, no values, so the overhead stays
 * a fraction of the debugger's. Not thread-safe: one listener per run.
 */
public final class XsltProfilingTraceListener implements TraceListener {

    /** Deeper call paths are folded into the node at this depth (deep recursion). */
    static final int MAX_TREE_DEPTH = 200;

    private static final class Frame {
        final Traceable traceable;
        final XsltProfile.Component component;   // null for plain instructions
        final XsltProfile.CallNode node;           // the call tree node of the component frame
        final String systemId;
        final int line;
        final long start;
        final boolean outermost;
        final int depth;
        long childNanos;           // time of the directly nested frames
        long childComponentNanos;  // time of nested component frames (for the component's self time)

        Frame(Traceable traceable, XsltProfile.Component component, XsltProfile.CallNode node, String systemId,
              int line, long start, boolean outermost, int depth) {
            this.traceable = traceable;
            this.component = component;
            this.node = node;
            this.systemId = systemId;
            this.line = line;
            this.start = start;
            this.outermost = outermost;
            this.depth = depth;
        }
    }

    private static final class Totals {
        long calls;
        long totalNanos;
        long selfNanos;
    }

    private static final class LineTotals {
        final String systemId;
        final int line;
        long hits;
        long selfNanos;

        LineTotals(String systemId, int line) {
            this.systemId = systemId;
            this.line = line;
        }
    }

    private final Deque<Frame> stack = new ArrayDeque<>();
    private final XsltProfile.CallNode root = new XsltProfile.CallNode(null);
    private final Map<XsltProfile.Component, Totals> totals = new HashMap<>();
    private final Map<XsltProfile.Component, Integer> active = new HashMap<>();
    private final Map<String, LineTotals> lines = new HashMap<>();
    private final Map<Traceable, String> patterns = new IdentityHashMap<>();
    private long started;
    private long finished;

    @Override
    public void setOutputDestination(net.sf.saxon.lib.Logger logger) {
        // results are collected, not printed
    }

    @Override
    public void open(Controller controller) {
        started = System.nanoTime();
    }

    @Override
    public void close() {
        finished = System.nanoTime();
    }

    @Override
    public void enter(Traceable traceable, Map<String, Object> properties, XPathContext context) {
        long now = System.nanoTime();
        Location location = traceable.getLocation();
        String systemId = location == null || location.getSystemId() == null ? "" : location.getSystemId();
        int line = location == null ? -1 : location.getLineNumber();
        XsltProfile.Component component = component(traceable, context, systemId, line);
        Frame parentComponent = nearestComponent();
        if (component == null) {
            stack.push(new Frame(traceable, null, null, systemId, line, now, false, 0));
            return;
        }
        XsltProfile.CallNode parentNode = parentComponent == null ? root : parentComponent.node;
        int depth = parentComponent == null ? 1 : parentComponent.depth + 1;
        XsltProfile.CallNode node;
        if (parentComponent != null && component.equals(parentComponent.component)) {
            node = parentNode;                       // direct recursion: one node
            depth = parentComponent.depth;
        } else if (depth > MAX_TREE_DEPTH) {
            node = parentNode;
            depth = parentComponent.depth;
        } else {
            node = parentNode.child(component);
        }
        int activations = active.merge(component, 1, Integer::sum);
        stack.push(new Frame(traceable, component, node, systemId, line, now, activations == 1, depth));
    }

    @Override
    public void leave(Traceable traceable) {
        long now = System.nanoTime();
        Frame frame = stack.poll();
        while (frame != null && frame.traceable != traceable && !stack.isEmpty()) {
            frame = stack.poll();                    // unbalanced (an error unwound frames): drop them
        }
        if (frame == null) {
            return;
        }
        long elapsed = now - frame.start;
        Frame parent = stack.peek();
        if (parent != null) {
            parent.childNanos += elapsed;
        }
        if (frame.line > 0) {
            Frame left = frame;
            String key = left.systemId + '\u0000' + left.line;
            LineTotals lineTotals = lines.computeIfAbsent(key, k -> new LineTotals(left.systemId, left.line));
            lineTotals.hits++;
            lineTotals.selfNanos += Math.max(0, elapsed - frame.childNanos);
        }
        if (frame.component == null) {
            return;
        }
        long self = Math.max(0, elapsed - frame.childComponentNanos);
        Frame enclosing = nearestComponent();
        if (enclosing != null) {
            enclosing.childComponentNanos += elapsed;
        }
        Totals t = totals.computeIfAbsent(frame.component, c -> new Totals());
        t.calls++;
        t.selfNanos += self;
        if (frame.outermost) {
            t.totalNanos += elapsed;
        }
        active.merge(frame.component, -1, Integer::sum);
        XsltProfile.CallNode node = frame.node;
        node.calls++;
        node.selfNanos += self;
        if (enclosing == null || enclosing.node != node) {
            node.totalNanos += elapsed;               // folded recursion: only the outermost call
        }
    }

    private Frame nearestComponent() {
        for (Frame frame : stack) {
            if (frame.component != null) {
                return frame;
            }
        }
        return null;
    }

    private XsltProfile.Component component(Traceable traceable, XPathContext context, String systemId, int line) {
        if (traceable instanceof TemplateRule rule) {
            String pattern = patterns.computeIfAbsent(rule,
                    r -> rule.getMatchPattern() == null ? "*" : rule.getMatchPattern().toShortString());
            return new XsltProfile.Component(XsltProfile.Kind.TEMPLATE_RULE,
                    "match=\"" + pattern + "\"" + modeSuffix(context), systemId, rule.getLineNumber());
        }
        if (traceable instanceof NamedTemplate named) {
            return new XsltProfile.Component(XsltProfile.Kind.NAMED_TEMPLATE,
                    named.getTemplateName() == null ? "?" : named.getTemplateName().getDisplayName(), systemId, line);
        }
        if (traceable instanceof UserFunction function) {
            return new XsltProfile.Component(XsltProfile.Kind.FUNCTION,
                    (function.getFunctionName() == null ? "?" : function.getFunctionName().getDisplayName())
                            + "#" + function.getArity(), systemId, line);
        }
        return null;
    }

    private static String modeSuffix(XPathContext context) {
        try {
            var mode = context == null ? null : context.getCurrentMode();
            if (mode != null && mode.getActor() != null && !mode.getActor().isUnnamedMode()
                    && mode.getActor().getModeName() != null) {
                return " mode=\"" + mode.getActor().getModeName().getDisplayName() + "\"";
            }
        } catch (RuntimeException ignored) {
            // no mode to name
        }
        return "";
    }

    @Override
    public void startCurrentItem(Item item) {
        // not measured
    }

    @Override
    public void endCurrentItem(Item item) {
        // not measured
    }

    @Override
    public void startRuleSearch() {
        // not measured
    }

    @Override
    public void endRuleSearch(Object rule, Mode mode, Item item) {
        // not measured
    }

    /**
     * @param totalNanos the measured run's time; when not positive, the time between open and close
     * @param warmupRuns the runs before the measured one
     * @return the profile of the run
     */
    public XsltProfile profile(long totalNanos, int warmupRuns) {
        long total = totalNanos > 0 ? totalNanos : Math.max(0, finished - started);
        root.calls = 1;
        root.totalNanos = total;
        long children = root.children().stream().mapToLong(XsltProfile.CallNode::totalNanos).sum();
        root.selfNanos = Math.max(0, total - children);
        List<XsltProfile.ComponentStats> components = new ArrayList<>(totals.size());
        totals.forEach((c, t) -> components.add(new XsltProfile.ComponentStats(c, t.calls, t.totalNanos, t.selfNanos)));
        components.sort((a, b) -> Long.compare(b.selfNanos(), a.selfNanos()));
        List<XsltProfile.LineStats> lineStats = new ArrayList<>(lines.size());
        lines.values().forEach(l -> lineStats.add(new XsltProfile.LineStats(l.systemId, l.line, l.hits, l.selfNanos)));
        lineStats.sort((a, b) -> Long.compare(b.selfNanos(), a.selfNanos()));
        return new XsltProfile(components, root, lineStats, total, warmupRuns);
    }
}
