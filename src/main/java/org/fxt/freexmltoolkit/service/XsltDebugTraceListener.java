package org.fxt.freexmltoolkit.service;

import net.sf.saxon.Controller;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.instruct.GlobalVariable;
import net.sf.saxon.expr.instruct.TemplateRule;
import net.sf.saxon.lib.TraceListener;
import net.sf.saxon.om.Item;
import net.sf.saxon.trace.Traceable;
import net.sf.saxon.trans.Mode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom TraceListener implementation for XSLT debugging.
 * Captures template matches, variable values, and call stack during transformation.
 *
 * <p>This listener is attached to the Saxon transformer when debugging is enabled,
 * providing real-time insight into XSLT execution.</p>
 */
public class XsltDebugTraceListener implements TraceListener {

    private static final Logger logger = LogManager.getLogger(XsltDebugTraceListener.class);

    // Collection size limits to prevent memory issues
    private static final int MAX_TEMPLATE_MATCHES = 500;
    private static final int MAX_CALL_STACK_HISTORY = 1000;
    private static final int MAX_VARIABLES = 200;

    // Collected template match information
    private final List<XsltTransformationEngine.TemplateMatchInfo> templateMatches =
            Collections.synchronizedList(new ArrayList<>());

    // Collected variable values
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
    private String currentMatchPattern;

    /**
     * Called when a traceable instruction is entered.
     * Used to track template calls, variable assignments, and call stack.
     */
    @Override
    public void enter(Traceable traceable, Map<String, Object> properties, XPathContext context) {
        try {
            String description = getTraceableDescription(traceable);
            int lineNumber = getLineNumber(traceable);

            // Track call stack
            CallStackEntry entry = new CallStackEntry(description, lineNumber, System.nanoTime());
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
            captureVariableValue(traceable, context);

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
                CallStackEntry entry = callStack.pop();

                // Record leave in history
                if (callStackHistory.size() < MAX_CALL_STACK_HISTORY) {
                    String indent = "  ".repeat(Math.min(callStack.size(), 20));
                    long durationMs = (System.nanoTime() - entry.startTime) / 1_000_000;
                    callStackHistory.add(indent + "LEAVE: " + entry.description + " (" + durationMs + "ms)");
                }
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
        currentMatchPattern = null;
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
                currentMatchPattern = pattern;

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

    private void captureVariableValue(Traceable traceable, XPathContext context) {
        try {
            if (variableValues.size() >= MAX_VARIABLES) {
                return;
            }

            String varName = null;
            Object varValue = null;

            if (traceable instanceof GlobalVariable globalVar) {
                varName = "$" + (globalVar.getVariableQName() != null
                        ? globalVar.getVariableQName().getLocalPart()
                        : "global-" + globalVar.hashCode());
                try {
                    varValue = evaluateVariableValue(globalVar, context);
                } catch (Exception e) {
                    varValue = "<evaluation error>";
                }
            }
            // Note: LocalVariable is not in the public API, we can track variables
            // through GlobalVariable and parameter tracking

            if (varName != null && varValue != null) {
                variableValues.put(varName, varValue);
                logger.trace("Variable captured: {} = {}", varName, truncate(String.valueOf(varValue), 100));
            }

        } catch (Exception e) {
            logger.debug("Error capturing variable: {}", e.getMessage());
        }
    }

    private Object evaluateVariableValue(GlobalVariable variable, XPathContext context) {
        try {
            // Get the select expression if available
            if (variable.getBody() != null) {
                return variable.getBody().toShortString();
            }
            return "<computed value>";
        } catch (Exception e) {
            return "<evaluation error: " + e.getMessage() + ">";
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

            // Return the class name as a fallback description
            return traceable.getClass().getSimpleName();

        } catch (Exception e) {
            return traceable.getClass().getSimpleName();
        }
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

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
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
    private record CallStackEntry(String description, int lineNumber, long startTime) {}
}
