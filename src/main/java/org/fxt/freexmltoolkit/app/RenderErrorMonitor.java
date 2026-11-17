package org.fxt.freexmltoolkit.app;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notices errors of JavaFX's render thread. JavaFX catches them itself ({@code PresentingPainter.run}:
 * {@code catch (Throwable) → printStackTrace(System.err)}), so they reach neither the uncaught-exception handler nor
 * the telemetry, and the FX thread keeps running - the window just stops painting (a lost Direct3D texture, an
 * exhausted texture pool). This stream sits in front of {@code System.err}, passes every line through unchanged and
 * reads the stack traces it sees: one whose frames are in Prism or the Quantum renderer is handed to the listener,
 * once per session, as a {@link RenderPipelineError} carrying the original trace.
 */
public final class RenderErrorMonitor extends PrintStream {

    /** An error of the render pipeline, rebuilt from the printed trace (class names and lines only). */
    public static final class RenderPipelineError extends RuntimeException {
        private static final long serialVersionUID = 1L;

        RenderPipelineError(String header, List<StackTraceElement> frames) {
            super(header, null, false, true);
            setStackTrace(frames.toArray(new StackTraceElement[0]));
        }
    }

    private static final Pattern HEADER = Pattern.compile("^(?:Exception in thread \"[^\"]*\" )?([\\w$]+(?:\\.[\\w$]+)+(?:Exception|Error))(?::.*)?$");
    private static final Pattern FRAME = Pattern.compile("^\\s+at (?:[\\w.$-]+/)?([\\w.$]+)\\.([\\w$<>]+)\\(([^:)]*)(?::(\\d+))?\\)$");
    private static final int MAX_FRAMES = 64;
    /** Frames read before a render-pipeline trace is reported without waiting for its end. */
    static final int REPORT_AT_FRAMES = 16;

    private final Consumer<RenderPipelineError> listener;
    private String header;
    private final List<StackTraceElement> frames = new ArrayList<>();
    private boolean reported;

    /**
     * @param target   the real stderr (everything is written there unchanged)
     * @param listener gets the first render-pipeline error of the session
     */
    public RenderErrorMonitor(OutputStream target, Consumer<RenderPipelineError> listener) {
        super(target, true);
        this.listener = listener;
    }

    /** Puts a monitor in front of {@code System.err}. */
    public static void install(Consumer<RenderPipelineError> listener) {
        if (!(System.err instanceof RenderErrorMonitor)) {
            System.setErr(new RenderErrorMonitor(System.err, listener));
        }
    }

    @Override
    public void println(String x) {
        super.println(x);
        line(x);
    }

    @Override
    public void println(Object x) {
        super.println(x);
        line(String.valueOf(x));
    }

    /** Reads one printed line (a trace arrives line by line through {@code println}). */
    synchronized void line(String text) {
        if (reported || text == null) {
            return;
        }
        Matcher frame = FRAME.matcher(text);
        if (header != null && frame.matches()) {
            if (frames.size() < MAX_FRAMES) {
                int line = frame.group(4) == null ? -1 : Integer.parseInt(frame.group(4));
                frames.add(new StackTraceElement(frame.group(1), frame.group(2), frame.group(3), line));
            }
            if (frames.size() >= REPORT_AT_FRAMES && isRenderPipeline()) {
                report(); // enough of the trace to tell it apart; the rest may never come
            }
            return;
        }
        if (header != null && !frames.isEmpty() && isRenderPipeline()) {
            report(); // the trace ended
            return;
        }
        Matcher h = HEADER.matcher(text.strip());
        header = h.matches() ? text.strip().replaceFirst("^Exception in thread \"[^\"]*\" ", "") : null;
        frames.clear();
    }

    private boolean isRenderPipeline() {
        return frames.stream().anyMatch(f -> f.getClassName().startsWith("com.sun.prism.")
                || f.getClassName().startsWith("com.sun.javafx.tk.quantum."));
    }

    private void report() {
        reported = true;
        RenderPipelineError error = new RenderPipelineError(header, List.copyOf(frames));
        header = null;
        frames.clear();
        try {
            listener.accept(error);
        } catch (RuntimeException ignored) {
            // the monitor must never break stderr
        }
    }

    /** @return whether a render-pipeline error was seen in this session */
    public synchronized boolean hasReported() {
        return reported;
    }
}
