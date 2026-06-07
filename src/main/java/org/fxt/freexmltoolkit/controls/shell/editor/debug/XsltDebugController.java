package org.fxt.freexmltoolkit.controls.shell.editor.debug;

import java.util.Map;
import java.util.concurrent.Executor;

import org.fxt.freexmltoolkit.debugger.DebugSession;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;

/**
 * UI-free orchestration of an interactive XSLT debug run: owns a {@link DebugSession},
 * launches {@link XsltTransformationEngine#transformWithDebugSession} on a background
 * executor, and forwards step requests. The UI subscribes to the session's
 * {@code "state"} property to react to pauses.
 */
public final class XsltDebugController {

    private final DebugSession session = new DebugSession();
    private final Executor executor;
    private volatile XsltTransformationResult lastResult;

    /** Uses the shared application executor. */
    public XsltDebugController() {
        this(org.fxt.freexmltoolkit.FxtGui.executorService);
    }

    /** Test/seam constructor with an injectable executor. */
    public XsltDebugController(Executor executor) {
        this.executor = executor;
    }

    public DebugSession getSession() {
        return session;
    }

    public XsltTransformationResult getLastResult() {
        return lastResult;
    }

    /** Starts the debug transform on the background executor (breakpoints already set on the session). */
    public void start(String xml, String xsltContent, Map<String, Object> parameters, OutputFormat format) {
        session.startSession();
        executor.execute(() ->
                lastResult = XsltTransformationEngine.getInstance()
                        .transformWithDebugSession(xml, xsltContent, parameters, format, session));
    }

    public void continueRun() { session.requestContinue(); }
    public void stepInto()    { session.requestStepInto(); }
    public void stepOver()    { session.requestStepOver(); }
    public void stepOut()     { session.requestStepOut(); }
    public void stop()        { session.requestStop(); }

    /** Aborts any in-flight run and resets the session. */
    public void close()       { session.close(); }
}
