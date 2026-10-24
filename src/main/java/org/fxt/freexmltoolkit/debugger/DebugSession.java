package org.fxt.freexmltoolkit.debugger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Coordinator for an interactive XSLT debug session.
 *
 * <p>The transformation runs on a background (Saxon) thread. That thread
 * calls {@link #checkAndPause} for every traceable instruction; when a
 * breakpoint or step condition matches, the call blocks on a
 * {@link Condition} until the UI thread invokes one of the
 * {@code request*} methods. State changes are published via
 * {@link PropertyChangeSupport} on the property name {@code "state"}.</p>
 *
 * <p>Saxon HE 12.9 has no native pause facility — that limitation forces
 * this thread-blocking pattern. Pauses can only happen at instruction
 * boundaries.</p>
 */
public class DebugSession implements BreakpointTarget {

    private static final Logger logger = LogManager.getLogger(DebugSession.class);

    public static final String PROP_STATE = "state";

    public enum State { IDLE, RUNNING, PAUSED, STOPPED }

    public enum StepMode { NONE, STEP_INTO, STEP_OVER, STEP_OUT }

    private final ReentrantLock pauseLock = new ReentrantLock();
    private final Condition resumeCondition = pauseLock.newCondition();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private final Set<Breakpoint> breakpoints =
            Collections.synchronizedSet(new LinkedHashSet<>());

    private volatile State state = State.IDLE;
    private volatile StepMode stepMode = StepMode.NONE;
    private volatile int stepTargetDepth = -1;
    private volatile PausedSnapshot pausedSnapshot;
    private volatile ConditionEvaluator conditionEvaluator;
    /** Why the current pause happened, when it is worth telling (a condition, a condition error). */
    private volatile String pauseNote = "";

    /**
     * Decides whether a conditional breakpoint fires: evaluates {@code condition} (XPath) at the
     * pause point described by {@code snapshot} and returns its effective boolean value.
     * Called on the Saxon thread; an exception makes the run pause and report the error.
     */
    @FunctionalInterface
    public interface ConditionEvaluator {
        boolean test(String condition, PausedSnapshot snapshot) throws Exception;
    }

    // ------------------------------------------------------------------
    // Breakpoint management
    // ------------------------------------------------------------------

    public void addBreakpoint(Breakpoint bp) {
        if (bp == null) return;
        breakpoints.removeIf(existing -> existing.filePath().equals(bp.filePath())
                && existing.lineNumber() == bp.lineNumber());
        breakpoints.add(bp);
    }

    public void removeBreakpoint(String filePath, int line) {
        String fp = SourceLocations.normalize(filePath);
        breakpoints.removeIf(b -> b.lineNumber() == line && b.filePath().equals(fp));
    }

    /** Toggle the breakpoint at {@code filePath:line}. Returns the new state (true = added). */
    @Override
    public boolean toggleBreakpoint(String filePath, int line) {
        String fp = SourceLocations.normalize(filePath);
        synchronized (breakpoints) {
            Breakpoint existing = null;
            for (Breakpoint b : breakpoints) {
                if (b.lineNumber() == line && (b.filePath() == null ? "" : b.filePath()).equals(fp)) {
                    existing = b;
                    break;
                }
            }
            if (existing != null) {
                breakpoints.remove(existing);
                return false;
            }
            breakpoints.add(new Breakpoint(fp, line, true));
            return true;
        }
    }

    @Override
    public boolean hasBreakpoint(String filePath, int line) {
        String fp = SourceLocations.normalize(filePath);
        synchronized (breakpoints) {
            for (Breakpoint b : breakpoints) {
                if (b.lineNumber() == line && (b.filePath() == null ? "" : b.filePath()).equals(fp)) {
                    return b.enabled();
                }
            }
            return false;
        }
    }

    @Override
    public String conditionAt(String filePath, int line) {
        String fp = SourceLocations.normalize(filePath);
        synchronized (breakpoints) {
            for (Breakpoint b : breakpoints) {
                if (b.lineNumber() == line && b.filePath().equals(fp)) {
                    return b.condition();
                }
            }
            return null;
        }
    }

    @Override
    public boolean setCondition(String filePath, int line, String condition) {
        if (line < 1) {
            return false;
        }
        String fp = SourceLocations.normalize(filePath);
        synchronized (breakpoints) {
            Breakpoint existing = null;
            for (Breakpoint b : breakpoints) {
                if (b.lineNumber() == line && b.filePath().equals(fp)) {
                    existing = b;
                    break;
                }
            }
            if (existing != null) {
                breakpoints.remove(existing);
                breakpoints.add(existing.withCondition(condition));
            } else {
                breakpoints.add(new Breakpoint(fp, line, true, condition));
            }
            return true;
        }
    }

    /** Sets how breakpoint conditions are evaluated ({@code null}: conditional breakpoints always pause). */
    public void setConditionEvaluator(ConditionEvaluator evaluator) {
        this.conditionEvaluator = evaluator;
    }

    /**
     * @return why the run is paused when that is worth showing — the condition that held, or a
     *         condition that failed to evaluate; {@code ""} otherwise
     */
    public String getPauseNote() {
        return pauseNote;
    }

    public Set<Breakpoint> getBreakpoints() {
        synchronized (breakpoints) {
            return Set.copyOf(breakpoints);
        }
    }

    public void clearBreakpoints() {
        breakpoints.clear();
    }

    // ------------------------------------------------------------------
    // State control (called from UI thread)
    // ------------------------------------------------------------------

    public void startSession() {
        startSession(StepMode.NONE);
    }

    /**
     * Starts (or restarts) the session with an initial step mode. {@link StepMode#STEP_INTO}
     * makes the run pause at the very first traced instruction ("pause on entry").
     * Breakpoints are kept across restarts.
     */
    public void startSession(StepMode initialMode) {
        stepMode = initialMode == null ? StepMode.NONE : initialMode;
        stepTargetDepth = -1;
        pausedSnapshot = null;
        setState(State.RUNNING);
    }

    public void requestContinue() {
        signalResume(StepMode.NONE, -1, State.RUNNING);
    }

    public void requestStepInto() {
        signalResume(StepMode.STEP_INTO, -1, State.RUNNING);
    }

    public void requestStepOver() {
        int currentDepth = pausedSnapshot != null ? pausedSnapshot.callStack().size() : 0;
        signalResume(StepMode.STEP_OVER, currentDepth, State.RUNNING);
    }

    public void requestStepOut() {
        int currentDepth = pausedSnapshot != null ? pausedSnapshot.callStack().size() : 0;
        signalResume(StepMode.STEP_OUT, currentDepth - 1, State.RUNNING);
    }

    public void requestStop() {
        signalResume(StepMode.NONE, -1, State.STOPPED);
    }

    public void close() {
        if (state != State.IDLE) {
            signalResume(StepMode.NONE, -1, State.STOPPED);
        }
        setState(State.IDLE);
    }

    private void signalResume(StepMode mode, int targetDepth, State newState) {
        pauseLock.lock();
        try {
            this.stepMode = mode;
            this.stepTargetDepth = targetDepth;
            setState(newState);
            resumeCondition.signalAll();
        } finally {
            pauseLock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Saxon-thread API (called from XsltDebugTraceListener)
    // ------------------------------------------------------------------

    /**
     * Called for every traced instruction. Blocks the calling thread when
     * the location matches a breakpoint or the active step mode requires it.
     *
     * @return {@code true} if the transformation should continue,
     *         {@code false} if a stop was requested (caller must abort).
     */
    public boolean checkAndPause(String systemId,
                                  int line,
                                  int depth,
                                  Supplier<PausedSnapshot> snapshotSupplier) {
        State currentState = state;
        if (currentState == State.STOPPED) {
            return false;
        }

        boolean pause = false;
        StepMode mode = stepMode;
        switch (mode) {
            case STEP_INTO -> pause = true;
            case STEP_OVER -> pause = depth <= stepTargetDepth;
            case STEP_OUT -> pause = depth < stepTargetDepth;
            case NONE -> pause = false;
            default -> pause = false;
        }

        String note = "";
        PausedSnapshot snapshot = null;
        if (!pause && line > 0 && !breakpoints.isEmpty()) {
            // Check breakpoints — normalise the location once, not once per breakpoint
            String normalized = normalizedLocation(systemId);
            java.util.List<String> conditions = new java.util.ArrayList<>(1);
            synchronized (breakpoints) {
                for (Breakpoint bp : breakpoints) {
                    if (bp.enabled() && bp.matchesNormalized(normalized, line)) {
                        if (!bp.hasCondition()) {
                            pause = true;
                            break;
                        }
                        conditions.add(bp.condition());
                    }
                }
            }
            if (!pause && !conditions.isEmpty()) {
                // Only conditional breakpoints here: capture the state once, test each condition.
                snapshot = captureSnapshot(snapshotSupplier, systemId, line);
                ConditionEvaluator evaluator = conditionEvaluator;
                for (String condition : conditions) {
                    if (evaluator == null) {
                        pause = true;
                        note = "condition not evaluated: " + condition;
                        break;
                    }
                    try {
                        if (evaluator.test(condition, snapshot)) {
                            pause = true;
                            note = "condition: " + condition;
                            break;
                        }
                    } catch (Exception e) {
                        pause = true;
                        note = "condition failed (" + condition + "): "
                                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                        break;
                    }
                }
            }
        }

        if (!pause) return true;

        if (snapshot == null) {
            snapshot = captureSnapshot(snapshotSupplier, systemId, line);
        }
        this.pausedSnapshot = snapshot;
        this.pauseNote = note;

        pauseLock.lock();
        try {
            // Reset step mode now; user must explicitly request the next step.
            this.stepMode = StepMode.NONE;
            this.stepTargetDepth = -1;
            setState(State.PAUSED);
            while (state == State.PAUSED) {
                resumeCondition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            pauseLock.unlock();
        }
        return state != State.STOPPED;
    }

    private static PausedSnapshot captureSnapshot(Supplier<PausedSnapshot> supplier, String systemId, int line) {
        try {
            return supplier.get();
        } catch (Exception e) {
            logger.warn("Failed to capture paused snapshot: {}", e.getMessage());
            return new PausedSnapshot(systemId, line, java.util.List.of(), java.util.List.of(), "");
        }
    }

    /** Bounded cache: Saxon reports the same handful of module systemIds for every instruction. */
    private final java.util.Map<String, String> normalizedLocations = new java.util.concurrent.ConcurrentHashMap<>();

    private String normalizedLocation(String systemId) {
        if (systemId == null || systemId.isEmpty()) {
            return "";
        }
        String cached = normalizedLocations.get(systemId);
        if (cached == null) {
            cached = SourceLocations.normalize(systemId);
            if (normalizedLocations.size() < 256) {
                normalizedLocations.put(systemId, cached);
            }
        }
        return cached;
    }

    public void notifyLeave(int depthAfterPop) {
        // Reserved for future depth-tracking refinements; currently unused
        // because checkAndPause derives depth from its caller parameter.
    }

    // ------------------------------------------------------------------
    // Accessors / observers
    // ------------------------------------------------------------------

    public State getState() {
        return state;
    }

    public StepMode getStepMode() {
        return stepMode;
    }

    public PausedSnapshot getPausedSnapshot() {
        return pausedSnapshot;
    }

    public PropertyChangeSupport getPropertyChangeSupport() {
        return pcs;
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    private void setState(State newState) {
        State old = this.state;
        if (old == newState) return;
        this.state = newState;
        pcs.firePropertyChange(PROP_STATE, old, newState);
    }
}
