package org.fxt.freexmltoolkit.debugger;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
    /** One-shot "Run to Cursor" target (normalised path, 1-based line); line 0 = none. */
    private volatile String runToPath = "";
    private volatile int runToLine;
    /** The last run ran to its end (not stopped, not failed): zero hits then mean "never reached". */
    private volatile boolean lastRunCompleted;
    /** Set by {@link #requestPause}; the next pause names it as its reason. */
    private volatile boolean pauseRequested;
    /** All breakpoints muted (steps and pause requests still work); kept across restarts. */
    private volatile boolean muted;
    /**
     * Lines whose breakpoints fired, with the call depth of the instruction that fired them
     * (innermost last). A traced instruction nested inside one on the same line (e.g. an
     * {@code xsl:value-of} inside a literal element on that line) is part of the same line
     * execution: it neither counts a hit nor pauses again. Saxon thread; reset per run.
     */
    private final java.util.ArrayDeque<Object[]> firedLines = new java.util.ArrayDeque<>();
    /** Hits per breakpoint (file + line) in the current run; reset by {@link #startSession}. */
    private final java.util.Map<String, AtomicInteger> hitCounts = new ConcurrentHashMap<>();
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
    public Breakpoint breakpointAt(String filePath, int line) {
        String fp = SourceLocations.normalize(filePath);
        synchronized (breakpoints) {
            for (Breakpoint b : breakpoints) {
                if (b.lineNumber() == line && b.filePath().equals(fp)) {
                    return b;
                }
            }
            return null;
        }
    }

    @Override
    public boolean updateBreakpoint(String filePath, int line, java.util.function.UnaryOperator<Breakpoint> change) {
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
            }
            breakpoints.add(change.apply(existing != null ? existing : new Breakpoint(fp, line, true)));
            return true;
        }
    }

    @Override
    public int hitCount(String filePath, int line) {
        AtomicInteger count = hitCounts.get(hitKey(SourceLocations.normalize(filePath), line));
        return count == null ? 0 : count.get();
    }

    /**
     * Records that the run just ended; {@code completed} = it ran to its end (successfully,
     * without Stop), so breakpoints still at zero hits were never reached.
     */
    public void markRunEnded(boolean completed) {
        this.lastRunCompleted = completed;
    }

    /** @return whether the last run ran to its end (see {@link #markRunEnded}). */
    public boolean isLastRunCompleted() {
        return lastRunCompleted;
    }

    @Override
    public boolean notReachedInLastRun(String filePath, int line) {
        if (!lastRunCompleted || muted) {
            return false;
        }
        Breakpoint bp = breakpointAt(filePath, line);
        return bp != null && bp.enabled() && hitCount(bp) == 0;
    }

    /** @return how often {@code bp} was hit in the current run. */
    public int hitCount(Breakpoint bp) {
        AtomicInteger count = hitCounts.get(hitKey(bp.filePath(), bp.lineNumber()));
        return count == null ? 0 : count.get();
    }

    private static String hitKey(String normalizedPath, int line) {
        return normalizedPath + "\u0000" + line;
    }

    @Override
    public boolean canRunTo() {
        return state == State.RUNNING || state == State.PAUSED;
    }

    /**
     * Runs to {@code filePath:line}: sets a one-shot stop there and, when paused, continues.
     * Breakpoints on the way still pause (and drop the target). Works while muted.
     */
    @Override
    public boolean runTo(String filePath, int line) {
        if (line < 1 || !canRunTo()) {
            return false;
        }
        runToPath = SourceLocations.normalize(filePath);
        runToLine = line;
        if (state == State.PAUSED) {
            requestContinue();
        }
        return true;
    }

    /** @return the pending Run to Cursor line, or 0 (tests). */
    public int getRunToLine() {
        return runToLine;
    }

    /** Mutes / unmutes all breakpoints: while muted they neither pause nor count hits. */
    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    @Override
    public boolean isMuted() {
        return muted;
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
        pauseRequested = false;
        runToLine = 0;
        lastRunCompleted = false;
        hitCounts.clear();
        synchronized (firedLines) {
            firedLines.clear();
        }
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

    /**
     * Pauses a running transformation at the next traced instruction ("Break"). Has no effect
     * unless the run is {@link State#RUNNING}. A long single evaluation (one huge XPath) pauses
     * only once it returns to the next instruction.
     */
    public void requestPause() {
        pauseLock.lock();
        try {
            if (state == State.RUNNING) {
                pauseRequested = true;
                stepMode = StepMode.STEP_INTO;
                stepTargetDepth = -1;
            }
        } finally {
            pauseLock.unlock();
        }
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
        int target = runToLine;
        if (!pause && target > 0 && line == target) {
            String targetPath = runToPath;
            String here = normalizedLocation(systemId);
            if (targetPath.isEmpty() || here.isEmpty() || targetPath.equals(here)) {
                pause = true;
                note = "run to cursor";
            }
        }
        if (line > 0 && !muted && !breakpoints.isEmpty()) {
            // Check breakpoints — normalise the location once, not once per breakpoint
            String normalized = normalizedLocation(systemId);
            java.util.List<Breakpoint> matching = new java.util.ArrayList<>(1);
            synchronized (breakpoints) {
                for (Breakpoint bp : breakpoints) {
                    if (bp.enabled() && bp.matchesNormalized(normalized, line)) {
                        matching.add(bp);
                    }
                }
            }
            if (!matching.isEmpty() && continuesFiredLine(normalized, line, depth)) {
                matching.clear(); // nested in an instruction of the same line execution
            }
            // Every matching breakpoint counts its hit (also when a step pauses here anyway):
            // a hit is a pass whose condition held.
            for (Breakpoint bp : matching) {
                boolean hit = true;
                String failure = null;
                if (bp.hasCondition()) {
                    if (snapshot == null) {
                        snapshot = captureSnapshot(snapshotSupplier, systemId, line);
                    }
                    ConditionEvaluator evaluator = conditionEvaluator;
                    if (evaluator == null) {
                        failure = "condition not evaluated: " + bp.condition();
                    } else {
                        try {
                            hit = evaluator.test(bp.condition(), snapshot);
                        } catch (Exception e) {
                            failure = "condition failed (" + bp.condition() + "): "
                                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                        }
                    }
                }
                if (failure != null) {
                    if (!pause) {
                        pause = true;
                        note = failure;
                    }
                    continue;
                }
                if (!hit) {
                    continue;
                }
                int hits = hitCounts.computeIfAbsent(hitKey(bp.filePath(), bp.lineNumber()),
                        k -> new AtomicInteger()).incrementAndGet();
                if (!pause && bp.hit().matches(hits)) {
                    pause = true;
                    StringBuilder why = new StringBuilder();
                    if (bp.hasCondition()) {
                        why.append("condition: ").append(bp.condition());
                    }
                    if (bp.hit().isSet()) {
                        if (why.length() > 0) {
                            why.append("  ·  ");
                        }
                        why.append("hit ").append(hits).append(" (").append(bp.hit().describe()).append(")");
                    }
                    note = why.toString();
                }
            }
        }

        if (!pause) return true;

        if (snapshot == null) {
            snapshot = captureSnapshot(snapshotSupplier, systemId, line);
        }
        runToLine = 0; // a one-shot target ends at any pause
        if (pauseRequested) {
            pauseRequested = false;
            if (note.isEmpty()) {
                note = "paused by request";
            }
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

    /**
     * Records that breakpoints of {@code normalized:line} are being checked at {@code depth},
     * after dropping the lines whose instructions have finished (recorded at this depth or
     * deeper: entering an instruction at {@code depth} means those were left).
     *
     * @return {@code true} when an enclosing instruction of the same line already fired, so this
     *         event belongs to that line execution
     */
    private boolean continuesFiredLine(String normalized, int line, int depth) {
        synchronized (firedLines) {
            while (!firedLines.isEmpty() && (int) firedLines.peekLast()[2] >= depth) {
                firedLines.pollLast();
            }
            Object[] top = firedLines.peekLast();
            if (top != null && (int) top[1] == line && top[0].equals(normalized)) {
                return true;
            }
            if (firedLines.size() < 512) {
                firedLines.addLast(new Object[]{normalized, line, depth});
            }
            return false;
        }
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
