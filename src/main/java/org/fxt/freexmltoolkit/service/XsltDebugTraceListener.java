package org.fxt.freexmltoolkit.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.DebugStackFrame;
import org.fxt.freexmltoolkit.debugger.PausedSnapshot;
import org.fxt.freexmltoolkit.debugger.VariableBinding;
import org.fxt.freexmltoolkit.debugger.VariableScope;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.StackFrame;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.NamedTemplate;
import net.sf.saxon.expr.instruct.SlotManager;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.expr.instruct.UserFunction;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.FocusIterator;
import net.sf.saxon.om.GroundedValue;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Location;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.Mode;

/**
 * Custom TraceListener implementation for XSLT debugging.
 * Captures template matches, variable values, and call stack during transformation.
 *
 * <p>This listener is attached to the Saxon transformer when debugging is enabled,
 * providing real-time insight into XSLT execution. With a {@link DebugSession} it also
 * pauses the Saxon thread at breakpoints and builds the {@link PausedSnapshot} the
 * debugger panels show.</p>
 */
public class XsltDebugTraceListener implements TraceListener {

    private static final Logger logger = LogManager.getLogger(XsltDebugTraceListener.class);

    // Collection size limits to prevent memory issues
    private static final int MAX_TEMPLATE_MATCHES = 500;
    private static final int MAX_CALL_STACK_HISTORY = 1000;
    private static final int MAX_VARIABLES = 200;
    /** Longest sequence kept per variable in a snapshot (items beyond are dropped). */
    private static final int MAX_SNAPSHOT_ITEMS = 10_000;

    // Collected template match information
    private final List<XsltTransformationEngine.TemplateMatchInfo> templateMatches =
            Collections.synchronizedList(new ArrayList<>());

    // Collected variable values (post-hoc API: name -> expression text / value text)
    private final Map<String, Object> variableValues = new ConcurrentHashMap<>();

    // Current call stack (live during transformation)
    private final Deque<CallStackEntry> callStack = new ArrayDeque<>();

    // Call stack history (for result)
    private final List<String> callStackHistory = Collections.synchronizedList(new ArrayList<>());

    // Template execution timing
    private final Map<String, Long> templateStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> templateExecutionTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> templateExecutionCounts = new ConcurrentHashMap<>();

    // Template rule search tracking
    private long ruleSearchStartTime;

    // Global variables whose evaluation has completed (safe to read from the Bindery at a pause)
    private final Set<GlobalVariable> completedGlobals = Collections.synchronizedSet(new LinkedHashSet<>());

    // Optional interactive debug session — when non-null, enter() can pause execution.
    private final DebugSession debugSession;

    /** Default constructor — post-hoc trace collection only. */
    public XsltDebugTraceListener() {
        this(null);
    }

    /**
     * Interactive constructor. When {@code session} is non-null, {@link #enter}
     * consults the session to honor breakpoints and step requests.
     */
    public XsltDebugTraceListener(DebugSession session) {
        this.debugSession = session;
    }

    /**
     * Called when a traceable instruction is entered.
     * Used to track template calls, variable assignments, and call stack.
     */
    @Override
    public void enter(Traceable traceable, Map<String, Object> properties, XPathContext context) {
        try {
            String description = getTraceableDescription(traceable);
            int lineNumber = getLineNumber(traceable);
            String systemId = getSystemId(traceable);

            // Track call stack
            CallStackEntry entry = new CallStackEntry(description, systemId, lineNumber,
                    frameKind(traceable), traceable, System.nanoTime());
            callStack.push(entry);

            // Record in history with indentation
            if (callStackHistory.size() < MAX_CALL_STACK_HISTORY) {
                String indent = "  ".repeat(Math.min(callStack.size() - 1, 20));
                callStackHistory.add(indent + "ENTER: " + description + " at line " + lineNumber);
            }

            // Track template execution start time
            if (traceable instanceof TemplateRule templateRule) {
                String pattern = templateRule.getMatchPattern() != null
                        ? templateRule.getMatchPattern().toShortString()
                        : "unnamed-template";
                templateStartTimes.put(pattern + "_" + System.identityHashCode(templateRule), System.nanoTime());
            }

            // Capture variable values
            captureVariableValue(traceable);

            // Interactive pause — always consult the session so step-modes
            // work even at instructions with no line info (lineNumber == -1).
            // Breakpoint matching itself requires a real line; that's handled
            // by Breakpoint.matches.
            if (debugSession != null) {
                int depth = callStack.size();
                final XPathContext ctxRef = context;
                boolean alive = debugSession.checkAndPause(systemId, lineNumber, depth,
                        () -> buildPausedSnapshot(systemId, lineNumber, ctxRef));
                if (!alive) {
                    throw new XsltDebugStopException();
                }
            }

        } catch (XsltDebugStopException stop) {
            throw stop;
        } catch (Exception e) {
            logger.debug("Error in trace enter: {}", e.getMessage());
        }
    }

    /**
     * Called when a traceable instruction is left.
     * Used to calculate execution times.
     */
    @Override
    public void leave(Traceable traceable) {
        try {
            if (!callStack.isEmpty()) {
                if (debugSession != null) {
                    debugSession.notifyLeave(callStack.size() - 1);
                }
                CallStackEntry entry = callStack.pop();

                // Record leave in history
                if (callStackHistory.size() < MAX_CALL_STACK_HISTORY) {
                    String indent = "  ".repeat(Math.min(callStack.size(), 20));
                    long durationMs = (System.nanoTime() - entry.startTime) / 1_000_000;
                    callStackHistory.add(indent + "LEAVE: " + entry.description + " (" + durationMs + "ms)");
                }
            }

            if (traceable instanceof GlobalVariable globalVariable) {
                completedGlobals.add(globalVariable);
            }

            // Calculate template execution time
            if (traceable instanceof TemplateRule templateRule) {
                String pattern = templateRule.getMatchPattern() != null
                        ? templateRule.getMatchPattern().toShortString()
                        : "unnamed-template";
                String key = pattern + "_" + System.identityHashCode(templateRule);

                Long startTime = templateStartTimes.remove(key);
                if (startTime != null) {
                    long duration = System.nanoTime() - startTime;
                    templateExecutionTimes.merge(pattern, duration, Long::sum);
                    templateExecutionCounts.merge(pattern, 1, Integer::sum);
                }
            }

        } catch (Exception e) {
            logger.debug("Error in trace leave: {}", e.getMessage());
        }
    }

    /**
     * Called when template rule search begins.
     */
    @Override
    public void startRuleSearch() {
        ruleSearchStartTime = System.nanoTime();
    }

    /**
     * Called when template rule search ends.
     * Captures which template was matched.
     */
    @Override
    public void endRuleSearch(Object rule, Mode mode, Item item) {
        try {
            if (rule instanceof TemplateRule templateRule && templateMatches.size() < MAX_TEMPLATE_MATCHES) {
                String pattern = templateRule.getMatchPattern() != null
                        ? "match=\"" + templateRule.getMatchPattern().toShortString() + "\""
                        : "match=\"*\"";

                String templateName = templateRule.getObjectName() != null
                        ? templateRule.getObjectName().getLocalPart()
                        : "anonymous";

                int lineNumber = templateRule.getLineNumber();
                long executionTime = (System.nanoTime() - ruleSearchStartTime) / 1_000_000;

                // Get item context for better description
                String itemDescription = item != null ? item.toShortString() : "unknown";
                String fullName = templateName + " [processing: " + truncate(itemDescription, 50) + "]";

                XsltTransformationEngine.TemplateMatchInfo matchInfo =
                        new XsltTransformationEngine.TemplateMatchInfo(pattern, fullName, lineNumber, executionTime);

                templateMatches.add(matchInfo);

                logger.trace("Template matched: {} at line {}", pattern, lineNumber);
            }
        } catch (Exception e) {
            logger.debug("Error in endRuleSearch: {}", e.getMessage());
        }
    }

    /**
     * Called when processing of an item starts.
     */
    @Override
    public void startCurrentItem(Item item) {
        // Optional: track which items are being processed
        logger.trace("Processing item: {}", item != null ? item.toShortString() : "null");
    }

    /**
     * Called when processing of an item ends.
     */
    @Override
    public void endCurrentItem(Item item) {
        // Optional: track item processing completion
    }

    /**
     * Sets the output destination for trace output.
     * Not used as we collect data internally.
     */
    @Override
    public void setOutputDestination(net.sf.saxon.lib.Logger logger) {
        // We don't output to external logger, we collect internally
    }

    /**
     * Called at the start of transformation.
     */
    @Override
    public void open(Controller controller) {
        logger.debug("XSLT Debug Trace started");
        clearCollections();
    }

    /**
     * Called at the end of transformation.
     */
    @Override
    public void close() {
        logger.debug("XSLT Debug Trace ended. Templates matched: {}, Variables captured: {}",
                templateMatches.size(), variableValues.size());

        // Build final template match info with accumulated execution times
        buildFinalTemplateStats();
    }

    // ========== Helper Methods ==========

    private void captureVariableValue(Traceable traceable) {
        try {
            if (variableValues.size() >= MAX_VARIABLES) {
                return;
            }
            // Only the declaration (expression) is known when the variable is entered — its
            // value is being computed right now. Values are read at pause time instead.
            if (traceable instanceof GlobalVariable globalVar) {
                String varName = "$" + (globalVar.getVariableQName() != null
                        ? globalVar.getVariableQName().getLocalPart()
                        : "global-" + globalVar.hashCode());
                String expression = globalVar.getBody() != null ? globalVar.getBody().toShortString()
                        : "<computed value>";
                variableValues.put(varName, expression);
                logger.trace("Variable captured: {} = {}", varName, truncate(expression, 100));
            }
        } catch (Exception e) {
            logger.debug("Error capturing variable: {}", e.getMessage());
        }
    }

    private String getTraceableDescription(Traceable traceable) {
        if (traceable == null) {
            return "unknown";
        }

        try {
            if (traceable instanceof TemplateRule templateRule) {
                String pattern = templateRule.getMatchPattern() != null
                        ? templateRule.getMatchPattern().toShortString()
                        : "*";
                return "template match=\"" + pattern + "\"";
            }
            if (traceable instanceof NamedTemplate named) {
                return "template name=\"" + localName(named.getTemplateName()) + "\"";
            }
            if (traceable instanceof UserFunction function) {
                return "function " + localName(function.getFunctionName()) + "()";
            }
            if (traceable instanceof GlobalVariable global) {
                return "variable $" + localName(global.getVariableQName());
            }

            // Return the class name as a fallback description
            return traceable.getClass().getSimpleName();

        } catch (Exception e) {
            return traceable.getClass().getSimpleName();
        }
    }

    private static String localName(StructuredQName name) {
        return name == null ? "?" : name.getDisplayName();
    }

    private static DebugStackFrame.FrameKind frameKind(Traceable traceable) {
        if (traceable instanceof TemplateRule || traceable instanceof NamedTemplate) {
            return DebugStackFrame.FrameKind.TEMPLATE;
        }
        if (traceable instanceof UserFunction) {
            return DebugStackFrame.FrameKind.FUNCTION;
        }
        return DebugStackFrame.FrameKind.INSTRUCTION;
    }

    private int getLineNumber(Traceable traceable) {
        try {
            // Try to get location information from the traceable
            if (traceable instanceof TemplateRule templateRule) {
                return templateRule.getLineNumber();
            }
            // For other traceables, try to get location via reflection or Location interface
            if (traceable.getLocation() != null) {
                return traceable.getLocation().getLineNumber();
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String getSystemId(Traceable traceable) {
        try {
            Location location = traceable == null ? null : traceable.getLocation();
            return location == null ? "" : safeString(location.getSystemId());
        } catch (Exception e) {
            return "";
        }
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    /**
     * Snapshot the live execution state at a pause point. Must be called on
     * the Saxon thread BEFORE blocking, because XPathContext is mutable and
     * lazily evaluated variable values are bound to the live context.
     */
    private PausedSnapshot buildPausedSnapshot(String systemId, int line, XPathContext context) {
        List<VariableBinding> vars = new ArrayList<>();
        Map<QName, XdmValue> locals = new LinkedHashMap<>();
        Map<QName, XdmValue> globals = new LinkedHashMap<>();
        XdmItem contextXdm = null;
        String contextItem = "";
        int position = -1;
        int last = -1;

        // 1. Context item — always present in template execution
        try {
            Item ctxItem = context.getContextItem();
            if (ctxItem != null) {
                contextXdm = asItem(XdmValue.wrap(ctxItem));
                contextItem = truncate(ctxItem.toShortString(), 200);
                vars.add(new VariableBinding(".", truncate(ctxItem.toShortString(), VariableBinding.MAX_VALUE_LENGTH),
                        contextXdm != null ? WatchEvaluator.describeType(contextXdm) : ctxItem.getClass().getSimpleName(),
                        VariableScope.LOCAL));
            } else {
                vars.add(new VariableBinding(".", "<no context>", "", VariableScope.LOCAL));
            }
        } catch (Throwable ignored) { /* best-effort */ }

        // 2. Focus — position() and last()
        try {
            FocusIterator iter = context.getCurrentIterator(); // NOPMD - borrowed iterator owned by the Saxon context
            if (iter != null) {
                position = iter.position();
                vars.add(new VariableBinding("position()", String.valueOf(position), "xs:integer", VariableScope.LOCAL));
            }
        } catch (Throwable ignored) { /* outside focus — skip */ }
        try {
            last = context.getLast();
            vars.add(new VariableBinding("last()", String.valueOf(last), "xs:integer", VariableScope.LOCAL));
        } catch (Throwable ignored) { /* outside focus — skip */ }

        // 3. Local stack-frame slots — display all slots, including unbound
        try {
            StackFrame frame = context.getStackFrame();
            if (frame != null) {
                SlotManager slotManager = frame.getStackFrameMap();
                Sequence[] values = frame.getStackFrameValues();
                List<StructuredQName> names = slotManager != null ? slotManager.getVariableMap() : null;
                int slotCount = values == null ? 0 : values.length;
                for (int i = 0; i < slotCount; i++) {
                    Sequence value = values[i];
                    StructuredQName qname = names != null && i < names.size() ? names.get(i) : null;
                    String name = qname != null ? "$" + qname.getDisplayName() : "$$slot" + i;
                    String stringValue;
                    String typeName = "";
                    if (value == null) {
                        stringValue = "<unbound>";
                    } else {
                        try {
                            XdmValue xdm = materialize(value);
                            stringValue = WatchEvaluator.render(xdm, VariableBinding.MAX_VALUE_LENGTH);
                            typeName = WatchEvaluator.describeType(xdm);
                            if (qname != null) {
                                locals.put(new QName(qname), xdm);
                            }
                        } catch (Exception e) {
                            stringValue = "<unavailable>";
                        }
                    }
                    vars.add(new VariableBinding(name, stringValue, typeName, VariableScope.LOCAL));
                }
            }
        } catch (Throwable t) {
            logger.debug("Failed to capture local variables: {}", t.getMessage());
        }

        // 4. Globals whose evaluation has completed — read from the Bindery cache. Globals
        //    not yet evaluated are deliberately not forced here: that would change the
        //    evaluation order (and run any xsl:message they contain) at the pause.
        List<GlobalVariable> done;
        synchronized (completedGlobals) {
            done = new ArrayList<>(completedGlobals);
        }
        for (GlobalVariable global : done) {
            StructuredQName qname = global.getVariableQName();
            String name = "$" + (qname != null ? qname.getDisplayName() : "global-" + global.hashCode());
            try {
                GroundedValue value = global.evaluateVariable(context);
                XdmValue xdm = materialize(value);
                vars.add(new VariableBinding(name, WatchEvaluator.render(xdm, VariableBinding.MAX_VALUE_LENGTH),
                        WatchEvaluator.describeType(xdm), VariableScope.GLOBAL));
                if (qname != null) {
                    globals.put(new QName(qname), xdm);
                }
            } catch (Throwable t) {
                Object expression = variableValues.get(name);
                vars.add(new VariableBinding(name, expression == null ? "<unavailable>" : String.valueOf(expression),
                        "", VariableScope.GLOBAL));
            }
        }

        List<DebugStackFrame> frames = buildStackFrameSnapshot(systemId, line);
        return new PausedSnapshot(systemId, line, frames, vars, contextItem, contextXdm, locals, globals,
                position, last);
    }

    /** Grounds a (possibly lazy) sequence on the Saxon thread and caps very long sequences. */
    private static XdmValue materialize(Sequence value) throws net.sf.saxon.trans.XPathException {
        GroundedValue grounded = value.materialize();
        if (grounded.getLength() > MAX_SNAPSHOT_ITEMS) {
            grounded = grounded.subsequence(0, MAX_SNAPSHOT_ITEMS);
        }
        return XdmValue.wrap(grounded);
    }

    private static XdmItem asItem(XdmValue value) {
        if (value instanceof XdmItem item) {
            return item;
        }
        return value != null && value.size() == 1 ? value.itemAt(0) : null;
    }

    private List<DebugStackFrame> buildStackFrameSnapshot(String currentSystemId, int currentLine) {
        List<DebugStackFrame> frames = new ArrayList<>();
        // Top of stack first — matches typical debugger view ordering
        for (CallStackEntry entry : callStack) {
            String frameSystemId = entry.systemId().isEmpty() ? currentSystemId : entry.systemId();
            frames.add(new DebugStackFrame(entry.description(), frameSystemId,
                    entry.lineNumber() > 0 ? entry.lineNumber() : currentLine, List.of(), entry.kind()));
        }
        return frames;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private void clearCollections() {
        templateMatches.clear();
        variableValues.clear();
        callStack.clear();
        callStackHistory.clear();
        templateStartTimes.clear();
        templateExecutionTimes.clear();
        templateExecutionCounts.clear();
        completedGlobals.clear();
    }

    private void buildFinalTemplateStats() {
        // Update template matches with accumulated execution times
        // This is done at the end to get total times across all invocations
        for (Map.Entry<String, Long> entry : templateExecutionTimes.entrySet()) {
            String pattern = entry.getKey();
            long totalTime = entry.getValue() / 1_000_000; // Convert to ms
            int count = templateExecutionCounts.getOrDefault(pattern, 1);

            logger.debug("Template '{}': executed {} times, total time {}ms", pattern, count, totalTime);
        }
    }

    // ========== Getter Methods ==========

    /**
     * Returns the list of template matches captured during transformation.
     */
    public List<XsltTransformationEngine.TemplateMatchInfo> getTemplateMatches() {
        return new ArrayList<>(templateMatches);
    }

    /**
     * Returns the map of variable names to values captured during transformation.
     */
    public Map<String, Object> getVariableValues() {
        return new HashMap<>(variableValues);
    }

    /**
     * Returns the call stack history as a list of strings.
     */
    public List<String> getCallStack() {
        return new ArrayList<>(callStackHistory);
    }

    /**
     * Returns template execution times (pattern -> total time in nanoseconds).
     */
    public Map<String, Long> getTemplateExecutionTimes() {
        return new HashMap<>(templateExecutionTimes);
    }

    /**
     * Returns template execution counts (pattern -> number of invocations).
     */
    public Map<String, Integer> getTemplateExecutionCounts() {
        return new HashMap<>(templateExecutionCounts);
    }

    // ========== Inner Classes ==========

    /**
     * Represents an entry in the call stack.
     */
    private record CallStackEntry(String description, String systemId, int lineNumber,
                                  DebugStackFrame.FrameKind kind, Traceable traceable, long startTime) {}
}
