package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.debugger.WatchEvaluator;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.XsltTransformationResult.TransformationMessage;

/**
 * UI-free orchestration of an interactive XSLT debug run: owns a {@link DebugSession},
 * launches {@link XsltTransformationEngine#transformWithDebugSession} on a background
 * executor, and forwards step requests. The UI subscribes to the session's
 * {@code "state"} property to react to pauses, and to this controller's message and
 * completion listeners for live {@code xsl:message} output and the run's result.
 *
 * <p>Listeners are invoked on the Saxon/executor thread; UI code must marshal to the
 * FX thread.</p>
 */
public final class XsltDebugController {

    private static final Logger logger = LogManager.getLogger(XsltDebugController.class);

    /** Everything needed to (re)launch a run; the stylesheet is re-read on every launch. */
    private record RunArgs(String xml, String inputSystemId, Supplier<String> xsltSource, String xsltSystemId,
                           Map<String, Object> parameters, OutputFormat format) { }

    private final DebugSession session = new DebugSession();
    private final Executor executor;
    private final List<Consumer<TransformationMessage>> messageListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<XsltTransformationResult>> completionListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile XsltTransformationResult lastResult;
    private volatile RunArgs lastArgs;
    private volatile String lastXslt = "";
    private volatile boolean pauseOnEntry;
    private volatile boolean restartPending;
    private volatile WatchEvaluator watchEvaluator;
    /** "Debug to cursor": a Run to Cursor target applied when the next run starts. */
    private volatile String runToOnStartPath;
    private volatile int runToOnStartLine;

    /** Uses the shared application executor. */
    public XsltDebugController() {
        this(org.fxt.freexmltoolkit.FxtGui.executorService);
    }

    /** Test/seam constructor with an injectable executor. */
    public XsltDebugController(Executor executor) {
        this.executor = executor;
        // Conditional breakpoints: XPath with xsl:if semantics against the paused state.
        session.setConditionEvaluator((condition, snapshot) -> getWatchEvaluator().test(condition, snapshot));
    }

    public DebugSession getSession() {
        return session;
    }

    public XsltTransformationResult getLastResult() {
        return lastResult;
    }

    /** @return {@code true} while a transformation is in flight (running or paused). */
    public boolean isRunning() {
        return running.get();
    }

    /** When set, the next run pauses at the first traced instruction instead of running through. */
    public void setPauseOnEntry(boolean pauseOnEntry) {
        this.pauseOnEntry = pauseOnEntry;
    }

    public boolean isPauseOnEntry() {
        return pauseOnEntry;
    }

    /** Receives every {@code xsl:message} (and Saxon warning/error) as it happens, on the Saxon thread. */
    public void addMessageListener(Consumer<TransformationMessage> listener) {
        if (listener != null) messageListeners.add(listener);
    }

    public void removeMessageListener(Consumer<TransformationMessage> listener) {
        messageListeners.remove(listener);
    }

    /** Receives the result when a run ends (finished, stopped or failed), on the executor thread. */
    public void addCompletionListener(Consumer<XsltTransformationResult> listener) {
        if (listener != null) completionListeners.add(listener);
    }

    public void removeCompletionListener(Consumer<XsltTransformationResult> listener) {
        completionListeners.remove(listener);
    }

    /**
     * Evaluates watch expressions against the paused context; bound to the processor that
     * runs the debugged stylesheet and to its namespace prefixes.
     */
    public WatchEvaluator getWatchEvaluator() {
        WatchEvaluator evaluator = watchEvaluator;
        if (evaluator == null) {
            evaluator = new WatchEvaluator(XsltTransformationEngine.getInstance().getDebugProcessor(),
                    WatchEvaluator.namespacesOf(lastXslt));
            watchEvaluator = evaluator;
        }
        return evaluator;
    }

    /** Starts the debug transform of an in-memory stylesheet (breakpoints already set on the session). */
    public void start(String xml, String xsltContent, Map<String, Object> parameters, OutputFormat format) {
        start(xml, () -> xsltContent, null, parameters, format);
    }

    /**
     * Starts the debug transform. The stylesheet text is obtained from {@code xsltSource} on
     * every launch (so a restart picks up edits saved meanwhile); {@code xsltSystemId} is the
     * stylesheet's {@code file:} URI, or {@code null} for an in-memory stylesheet.
     */
    public void start(String xml, Supplier<String> xsltSource, String xsltSystemId,
                      Map<String, Object> parameters, OutputFormat format) {
        start(xml, null, xsltSource, xsltSystemId, parameters, format);
    }

    /**
     * Like {@link #start(String, Supplier, String, Map, OutputFormat)} with the input's
     * {@code file:} URI ({@code null} for in-memory text), so input nodes report their file.
     */
    public void start(String xml, String inputSystemId, Supplier<String> xsltSource, String xsltSystemId,
                      Map<String, Object> parameters, OutputFormat format) {
        lastArgs = new RunArgs(xml, inputSystemId, xsltSource, xsltSystemId,
                parameters == null ? Map.of() : parameters, format);
        launch();
    }

    /**
     * Runs the last request again, keeping the breakpoints. While a run is in flight it is
     * stopped first and the restart happens once that run has unwound — starting the new
     * session earlier would let the old Saxon thread's cleanup close it.
     */
    public void restart() {
        if (lastArgs == null) {
            return;
        }
        if (running.get()) {
            restartPending = true;
            session.requestStop();
        } else {
            launch();
        }
    }

    private void launch() {
        RunArgs args = lastArgs;
        if (args == null) {
            return;
        }
        String xslt;
        try {
            xslt = args.xsltSource().get();
        } catch (RuntimeException e) {
            XsltTransformationResult failure = XsltTransformationResult.error(
                    "Could not read stylesheet: " + e.getMessage());
            lastResult = failure;
            session.close();
            completionListeners.forEach(l -> l.accept(failure));
            return;
        }
        lastXslt = xslt == null ? "" : xslt;
        watchEvaluator = null;
        restartPending = false;
        running.set(true);
        boolean toCursor = runToOnStartLine > 0;
        session.startSession(pauseOnEntry && !toCursor ? DebugSession.StepMode.STEP_INTO : DebugSession.StepMode.NONE);
        if (toCursor) {
            session.runTo(runToOnStartPath, runToOnStartLine);
            runToOnStartLine = 0;
        }
        executor.execute(() -> {
            XsltTransformationResult result;
            try {
                result = XsltTransformationEngine.getInstance().transformWithDebugSession(
                        args.xml(), args.inputSystemId(), lastXslt, args.xsltSystemId(), args.parameters(),
                        args.format(), session, this::publishMessage);
            } catch (RuntimeException e) {
                logger.warn("Debug run failed unexpectedly", e);
                result = XsltTransformationResult.error("Debug run failed: " + e.getMessage());
                if (session.getState() != DebugSession.State.IDLE) {
                    session.close();
                }
            }
            lastResult = result;
            running.set(false);
            for (Consumer<XsltTransformationResult> listener : completionListeners) {
                try {
                    listener.accept(result);
                } catch (RuntimeException e) {
                    logger.debug("Completion listener failed: {}", e.getMessage());
                }
            }
            if (restartPending) {
                launch();
            }
        });
    }

    private void publishMessage(TransformationMessage message) {
        for (Consumer<TransformationMessage> listener : messageListeners) {
            try {
                listener.accept(message);
            } catch (RuntimeException e) {
                logger.debug("Message listener failed: {}", e.getMessage());
            }
        }
    }

    public void continueRun() { session.requestContinue(); }
    public void stepInto()    { session.requestStepInto(); }
    public void stepOver()    { session.requestStepOver(); }
    public void stepOut()     { session.requestStepOut(); }
    public void stop()        { restartPending = false; session.requestStop(); }
    /**
     * Makes the next run start as "Run to Cursor": no pause on entry, it runs to
     * {@code filePath:line} (breakpoints on the way still pause).
     */
    public void setRunToOnStart(String filePath, int line) {
        this.runToOnStartPath = filePath == null ? "" : filePath;
        this.runToOnStartLine = Math.max(0, line);
    }

    /** Breaks into a running transformation at its next instruction. */
    public void pause()       { session.requestPause(); }

    /** Aborts any in-flight run and resets the session. */
    public void close() {
        restartPending = false;
        session.close();
    }
}
