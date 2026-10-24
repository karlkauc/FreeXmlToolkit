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
import java.util.function.Consumer;
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

    /**
     * Lock-free copy of the enabled breakpoints and their sorted line numbers, read by the
     * Saxon thread for every instruction; rebuilt by {@link #rearm()} after each change.
     */
    private volatile Armed armed = new Armed(new Breakpoint[0], new int[0], new int[0]);

    /**
     * The enabled breakpoints with the line each one is matched against ({@code lines}, parallel
     * to {@code breakpoints}: the end line of a multi-line start tag, see {@link StartTagLines})
     * and those lines sorted and distinct for a quick reject.
     */
    private record Armed(Breakpoint[] breakpoints, int[] lines, int[] sortedLines) { }

    /** Source text per normalized module path, for {@link StartTagLines}; {@code null} = none. */
    private volatile java.util.function.Function<String, String> sourceTexts;
    private final java.util.Map<String, int[]> tagLineMaps = new java.util.concurrent.ConcurrentHashMap<>();

    private volatile State state = State.IDLE;
    private volatile StepMode stepMode = StepMode.NONE;
    private volatile int stepTargetDepth = -1;

    /**
     * Where the run last paused (normalized path, line, call depth): a step does not stop again
     * at instructions nested in that line's execution, so F7 moves line by line.
     */
    private volatile String lastPausePath = "";
    private volatile int lastPauseLine;
    private volatile int lastPauseDepth = Integer.MAX_VALUE;
    private volatile PausedSnapshot pausedSnapshot;
    private volatile ConditionEvaluator conditionEvaluator;
    /** One-shot "Run to Cursor" target (normalised path, 1-based line); line 0 = none. */
    private volatile String runToPath = "";
    private volatile int runToLine;
    /** A logpoint's message, ready to show (file and line of the logpoint, the expanded text). */
    public record LogEvent(String filePath, int line, String text) { }

    /** Expands logpoint messages (XPath parts) and receives them; {@code null} = logpoints only pause. */
    @FunctionalInterface
    public interface LogFormatter {
        String format(String template, PausedSnapshot snapshot) throws Exception;
    }

    private volatile LogFormatter logFormatter;
    private volatile Consumer<LogEvent> logSink;

    /** Template / function / mode breakpoints (any location); silenced by Mute. */
    private final Set<SymbolBreakpoint> symbolBreakpoints = Collections.synchronizedSet(new LinkedHashSet<>());
    /** Whether {@link #symbolBreakpoints} is non-empty, readable without its lock. */
    private volatile boolean hasSymbols;
    /** Whether an input-node breakpoint is set, readable without the lock. */
    private volatile boolean hasInputNodes;

    // ----- coverage --------------------------------------------------------------------

    private volatile boolean coverageEnabled;
    /** Executions per normalized module path, indexed by 1-based line; {@code null} = no data. */
    private volatile java.util.Map<String, int[]> coverage;
    private final java.util.Map<String, java.util.List<ComponentRanges.Range>> componentRanges =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Counts which lines run (from the next run on); off by default because it costs time. */
    public void setCoverageEnabled(boolean on) { this.coverageEnabled = on; }
    public boolean isCoverageEnabled() { return coverageEnabled; }

    /** Saxon thread, at the end of a run: the executions per module (normalized path) and line. */
    public void publishCoverage(java.util.Map<String, int[]> counts) {
        this.coverage = counts == null ? null : java.util.Map.copyOf(counts);
        componentRanges.clear();
        pcs.firePropertyChange(PROP_COVERAGE, null, this.coverage);
    }

    /** Property fired when a run's coverage is available. */
    public static final String PROP_COVERAGE = "coverage";

    /** @return whether the last run recorded coverage. */
    public boolean hasCoverage() {
        return coverage != null;
    }

    @Override
    public int coverageCount(String filePath, int line) {
        java.util.Map<String, int[]> cov = coverage;
        if (cov == null || line < 1) {
            return -1;
        }
        int[] counts = cov.get(SourceLocations.normalize(filePath));
        if (counts == null) {
            return -1;
        }
        return line < counts.length ? counts[line] : 0;
    }

    @Override
    public boolean coverageDead(String filePath, int line) {
        String path = SourceLocations.normalize(filePath);
        if (coverageCount(path, line) != 0) {
            return false;
        }
        for (ComponentRanges.Range range : ranges(path)) {
            if (range.contains(line)) {
                return !ran(path, range);
            }
        }
        return false;
    }

    /** @return the templates / functions of the covered modules none of whose lines ran, as "label (file:line)". */
    public java.util.List<String> deadComponents() {
        java.util.Map<String, int[]> cov = coverage;
        java.util.List<String> dead = new java.util.ArrayList<>();
        if (cov == null) {
            return dead;
        }
        for (String path : new java.util.TreeSet<>(cov.keySet())) {
            for (ComponentRanges.Range range : ranges(path)) {
                if (!ran(path, range)) {
                    dead.add(range.label() + " (" + SourceLocations.fileName(path) + ":" + range.startLine() + ")");
                }
            }
        }
        return dead;
    }

    private boolean ran(String path, ComponentRanges.Range range) {
        for (int l = range.startLine(); l <= range.endLine(); l++) {
            if (coverageCount(path, l) > 0) {
                return true;
            }
        }
        return false;
    }

    private java.util.List<ComponentRanges.Range> ranges(String path) {
        java.util.function.Function<String, String> texts = sourceTexts;
        if (texts == null || path.isEmpty()) {
            return java.util.List.of();
        }
        return componentRanges.computeIfAbsent(path, p -> {
            try {
                return ComponentRanges.scan(texts.apply(p));
            } catch (RuntimeException e) {
                return java.util.List.of();
            }
        });
    }

    /** @return whether an (unmuted) input-node breakpoint asks for the context node to be checked; lock-free. */
    public boolean hasActiveInputNodeBreakpoints() {
        return !muted && hasInputNodes;
    }

    /**
     * Saxon thread, when an instruction runs with a new context node: the pause note when an
     * enabled input-node breakpoint names the node at {@code systemId:line:column}, else {@code null}.
     */
    public String inputNodeBreak(String systemId, int line, int column) {
        if (muted || !hasInputNodes || line < 1) {
            return null;
        }
        String path = normalizedLocation(systemId);
        synchronized (symbolBreakpoints) {
            for (SymbolBreakpoint s : symbolBreakpoints) {
                if (s.matchesInputNode(path, line, column)) {
                    return s.describe();
                }
            }
        }
        return null;
    }

    /** "Break on" options, independent of line breakpoints (but silenced by Mute). */
    private volatile boolean breakOnErrors;
    private volatile boolean breakOnTerminate;
    private volatile boolean breakOnMessages;
    /** Secondary results of the run ({@code xsl:result-document}): URI → text written so far. */
    private final java.util.Map<String, Supplier<String>> resultDocuments =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());
    /** Documents the run loaded with doc() / document() / collection(), in load order. */
    private final Set<String> loadedDocuments = Collections.synchronizedSet(new LinkedHashSet<>());
    /** Reads the output the running transformation has serialized so far (set by the engine per run). */
    private volatile Supplier<String> outputProbe;
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
        rearm();
    }

    /** Rebuilds the lock-free copy of the enabled breakpoints. */
    private void rearm() {
        synchronized (breakpoints) {
            Breakpoint[] enabled = breakpoints.stream().filter(Breakpoint::enabled).toArray(Breakpoint[]::new);
            int[] lines = new int[enabled.length];
            for (int i = 0; i < enabled.length; i++) {
                lines[i] = effectiveLine(enabled[i].filePath(), enabled[i].lineNumber());
            }
            armed = new Armed(enabled, lines, java.util.Arrays.stream(lines).distinct().sorted().toArray());
        }
    }

    /**
     * Supplies the source text of a module by its normalized path, so a breakpoint (or Run to
     * Cursor) on any line of a multi-line start tag matches the line Saxon reports for it - the
     * tag's last line. Replaces earlier texts (the controller sets it at every launch).
     */
    public void setSourceTexts(java.util.function.Function<String, String> texts) {
        this.sourceTexts = texts;
        tagLineMaps.clear();
        rearm();
    }

    /** The main stylesheet's normalized path of the current launch ({@code ""} for an in-memory one). */
    private volatile String mainSourcePath = "";

    public void setMainSourcePath(String path) {
        this.mainSourcePath = path == null ? "" : path;
    }

    public String getMainSourcePath() {
        return mainSourcePath;
    }

    /** @return the text of a module (normalized path; {@code ""} = the main stylesheet) as the run used it, or {@code null}. */
    public String sourceText(String path) {
        java.util.function.Function<String, String> texts = sourceTexts;
        try {
            return texts == null ? null : texts.apply(path == null ? "" : path);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** @return the line the debugger watches for a breakpoint at {@code filePath:line}. */
    public int effectiveLine(String filePath, int line) {
        java.util.function.Function<String, String> texts = sourceTexts;
        String path = filePath == null ? "" : filePath;
        if (texts == null || line < 1) { // an empty path stands for the main stylesheet
            return line;
        }
        int[] map = tagLineMaps.computeIfAbsent(path, p -> {
            try {
                return StartTagLines.map(texts.apply(p));
            } catch (RuntimeException e) {
                return new int[0];
            }
        });
        return StartTagLines.resolve(map, line);
    }

    public void removeBreakpoint(String filePath, int line) {
        String fp = SourceLocations.normalize(filePath);
        breakpoints.removeIf(b -> b.lineNumber() == line && b.filePath().equals(fp));
        rearm();
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
                rearm();
                return false;
            }
            breakpoints.add(new Breakpoint(fp, line, true));
            rearm();
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
            rearm();
            return true;
        }
    }

    @Override
    public boolean shiftLines(String filePath, LineShift shift) {
        if (shift == null || shift.isNeutral()) {
            return false;
        }
        String fp = SourceLocations.normalize(filePath);
        boolean changed = false;
        synchronized (breakpoints) {
            java.util.List<Breakpoint> updated = new java.util.ArrayList<>(breakpoints.size());
            java.util.Set<Integer> taken = new java.util.HashSet<>();
            for (Breakpoint bp : breakpoints) {
                if (!bp.filePath().equals(fp)) {
                    updated.add(bp);
                    continue;
                }
                int line = shift.apply(bp.lineNumber());
                if (line != bp.lineNumber()) {
                    changed = true;
                }
                if (line >= 1 && taken.add(line)) {
                    updated.add(line == bp.lineNumber() ? bp : new Breakpoint(bp.filePath(), line, bp.enabled(),
                            bp.condition(), bp.hit(), bp.logMessage(), bp.suspend()));
                } else if (line >= 1) {
                    changed = true; // two breakpoints landed on one line: keep the first
                }
            }
            if (changed) {
                breakpoints.clear();
                breakpoints.addAll(updated);
                rearm();
            }
        }
        return changed;
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
        runToLine = effectiveLine(runToPath, line);
        if (state == State.PAUSED) {
            requestContinue();
        }
        return true;
    }

    /** @return the pending Run to Cursor line, or 0 (tests). */
    public int getRunToLine() {
        return runToLine;
    }

    /**
     * Lets the engine expose the output written so far; {@code null} when no run writes. Read
     * it while paused: the Saxon thread is blocked then, so the text does not change under the
     * reader.
     */
    public void setOutputProbe(Supplier<String> probe) {
        this.outputProbe = probe;
    }

    /**
     * @return the output the run has serialized so far (may lag behind the instructions by the
     *         serializer's buffer), or {@code null} when no run is writing
     */
    public String outputSoFar() {
        Supplier<String> probe = outputProbe;
        try {
            return probe == null ? null : probe.get();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Adds (or replaces the one with the same kind and name) a template / function / mode breakpoint. */
    public void addSymbolBreakpoint(SymbolBreakpoint symbol) {
        if (symbol == null || symbol.name().isEmpty()) {
            return;
        }
        synchronized (symbolBreakpoints) {
            symbolBreakpoints.removeIf(s -> s.kind() == symbol.kind() && s.name().equals(symbol.name()));
            symbolBreakpoints.add(symbol);
            hasSymbols = true;
            hasInputNodes = symbolBreakpoints.stream().anyMatch(s -> s.kind() == SymbolBreakpoint.Kind.INPUT_NODE);
        }
    }

    public void removeSymbolBreakpoint(SymbolBreakpoint symbol) {
        synchronized (symbolBreakpoints) {
            symbolBreakpoints.removeIf(s -> s.kind() == symbol.kind() && s.name().equals(symbol.name()));
            hasSymbols = !symbolBreakpoints.isEmpty();
            hasInputNodes = symbolBreakpoints.stream().anyMatch(s -> s.kind() == SymbolBreakpoint.Kind.INPUT_NODE);
        }
    }

    /** @return the template / function / mode breakpoints, in the order they were added. */
    public java.util.List<SymbolBreakpoint> getSymbolBreakpoints() {
        synchronized (symbolBreakpoints) {
            return java.util.List.copyOf(symbolBreakpoints);
        }
    }

    /**
     * @return whether entering a template / function must be reported to {@link #symbolBreak}:
     *         a template / function / mode breakpoint is set (and not muted) or a Smart Step
     *         Into target is pending; lock-free
     */
    public boolean hasActiveSymbolBreakpoints() {
        return stepTarget != null || !muted && hasSymbols;
    }

    /** The pending Smart Step Into target (one-shot, dropped at any pause), or {@code null}. */
    private volatile StepTarget stepTarget;
    /** Set when {@link #symbolBreak} reached the step target: that pause happens even while muted. */
    private volatile boolean stepTargetReached;

    /**
     * Smart Step Into: continues the paused run until {@code target} is entered (or any other
     * pause comes first, which drops the target).
     *
     * @return {@code false} when the run is not paused
     */
    public boolean stepIntoTarget(StepTarget target) {
        if (target == null || state != State.PAUSED) {
            return false;
        }
        stepTarget = target;
        requestContinue();
        return true;
    }

    /** @return the pending Smart Step Into target, or {@code null} (tests). */
    public StepTarget getStepTarget() {
        return stepTarget;
    }

    /**
     * Saxon thread, on entering a template or function: the pause note when a symbol breakpoint
     * names it, otherwise {@code null} (also while muted).
     */
    public String symbolBreak(String match, String template, String function, String mode) {
        StepTarget target = stepTarget;
        if (target != null && target.matches(match, template, function, mode)) {
            stepTarget = null;
            stepTargetReached = true;
            return "step into " + target.label();
        }
        if (muted || !hasSymbols) {
            return null;
        }
        synchronized (symbolBreakpoints) {
            for (SymbolBreakpoint s : symbolBreakpoints) {
                if (s.matches(match, template, function, mode)) {
                    return s.describe();
                }
            }
        }
        return null;
    }

    /** Sets how logpoint messages are expanded and where they go (the debugger's Messages). */
    public void setLogHandler(LogFormatter formatter, Consumer<LogEvent> sink) {
        this.logFormatter = formatter;
        this.logSink = sink;
    }

    /** Pause when a dynamic error occurs, with the state at the failing instruction. */
    public void setBreakOnErrors(boolean on) { this.breakOnErrors = on; }
    public boolean isBreakOnErrors() { return breakOnErrors; }

    /** Error codes "break on errors" is limited to (local names, {@code XTDE*} prefixes); empty = every error. */
    private volatile java.util.List<String> breakOnErrorCodes = java.util.List.of();

    /** Limits "break on errors" to these codes ({@code null} / empty = every dynamic error). */
    public void setBreakOnErrorCodes(java.util.Collection<String> codes) {
        this.breakOnErrorCodes = codes == null ? java.util.List.of()
                : codes.stream().map(String::strip).filter(c -> !c.isEmpty())
                        .map(c -> c.toUpperCase(java.util.Locale.ROOT)).distinct().toList();
    }

    public java.util.List<String> getBreakOnErrorCodes() {
        return breakOnErrorCodes;
    }

    /** @return whether an error with {@code code} (e.g. {@code XTDE0640}, may be {@code null}) pauses. */
    public boolean breaksOnError(String code) {
        if (!breakOnErrors) {
            return false;
        }
        java.util.List<String> codes = breakOnErrorCodes;
        if (codes.isEmpty()) {
            return true;
        }
        String c = code == null ? "" : code.strip().toUpperCase(java.util.Locale.ROOT);
        for (String wanted : codes) {
            if (wanted.endsWith("*") ? c.startsWith(wanted.substring(0, wanted.length() - 1)) : c.equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Parses a code list as typed ({@code "XTDE0640, FOAR*"}: commas or spaces). */
    public static java.util.List<String> parseErrorCodes(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(text.split("[,;\\s]+")).filter(s -> !s.isBlank()).toList();
    }

    /** Pause before an {@code xsl:message} with {@code terminate="yes"} runs. */
    public void setBreakOnTerminate(boolean on) { this.breakOnTerminate = on; }
    public boolean isBreakOnTerminate() { return breakOnTerminate; }

    /** Pause before every {@code xsl:message}. */
    public void setBreakOnMessages(boolean on) { this.breakOnMessages = on; }
    public boolean isBreakOnMessages() { return breakOnMessages; }

    /**
     * Pauses unconditionally at {@code systemId:line} for a "break on" event (an error, a
     * message) — unless muted or stopped. Saxon thread; blocks like a breakpoint.
     *
     * @param note the reason shown in the status ("error FOAR0001: …", "xsl:message terminate")
     * @return {@code true} if the transformation should continue, {@code false} when stopped
     */
    public boolean pauseAt(String systemId, int line, String note, Supplier<PausedSnapshot> snapshotSupplier) {
        if (state == State.STOPPED) {
            return false;
        }
        // Muted: the run goes on without pausing.
        boolean reached = stepTargetReached;
        stepTargetReached = false;
        if (muted && !reached) {
            return true;
        }
        rememberPause(systemId, line, Integer.MAX_VALUE); // depth unknown: nothing counts as nested
        return block(captureSnapshot(snapshotSupplier, systemId, line), note == null ? "" : note);
    }

    /** Records an {@code xsl:result-document} of the run and how to read what it wrote so far. */
    public void addResultDocument(String uri, Supplier<String> text) {
        if (uri != null && text != null) {
            resultDocuments.put(uri, text);
        }
    }

    /** @return the secondary results of the run: URI → text written so far, in creation order. */
    public java.util.Map<String, String> resultDocuments() {
        java.util.Map<String, String> texts = new java.util.LinkedHashMap<>();
        synchronized (resultDocuments) {
            resultDocuments.forEach((uri, text) -> {
                try {
                    texts.put(uri, text.get());
                } catch (RuntimeException e) {
                    texts.put(uri, "");
                }
            });
        }
        return texts;
    }

    /** Records a document the run loaded ({@code doc()}, {@code document()}, {@code collection()}). */
    public void recordLoadedDocument(String uri) {
        if (uri != null && !uri.isBlank()) {
            loadedDocuments.add(uri);
        }
    }

    /** @return the documents the run loaded so far, in load order. */
    public java.util.List<String> loadedDocuments() {
        synchronized (loadedDocuments) {
            return java.util.List.copyOf(loadedDocuments);
        }
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
        rearm();
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
        lastPauseLine = 0;
        stepTarget = null;
        stepTargetReached = false;
        coverage = null;
        pausedSnapshot = null;
        resultDocuments.clear();
        loadedDocuments.clear();
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
        return checkAndPause(systemId, line, depth, snapshotSupplier, snapshotSupplier);
    }

    /**
     * Like {@link #checkAndPause(String, int, int, Supplier)}, but breakpoint conditions and
     * logpoint messages are evaluated against {@code conditionSupplier}'s snapshot (captured at
     * most once per call) and only an actual pause captures the full {@code snapshotSupplier}
     * one. The condition snapshot only needs what an XPath expression can see: the context item,
     * {@code position()}/{@code last()} and the variable values, no table rows or call stack.
     */
    public boolean checkAndPause(String systemId,
                                  int line,
                                  int depth,
                                  Supplier<PausedSnapshot> conditionSupplier,
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
        if (lastPauseLine != 0 && depth <= lastPauseDepth) {
            lastPauseLine = 0; // the paused line's execution is over: a later run of it is new
        }
        // Steps move line by line: never stop at an instruction without a line (nothing to show)
        // or at one nested in the execution of the line the run just paused at.
        if (pause && (line < 1 || continuesPausedLine(systemId, line, depth))) {
            pause = false;
        }

        String note = "";
        PausedSnapshot conditionSnapshot = null;
        int target = runToLine;
        if (!pause && target > 0 && line == target) {
            String targetPath = runToPath;
            String here = normalizedLocation(systemId);
            if (targetPath.isEmpty() || here.isEmpty() || targetPath.equals(here)) {
                pause = true;
                note = "run to cursor";
            }
        }
        Armed arm = armed;
        if (line > 0 && !muted && java.util.Arrays.binarySearch(arm.sortedLines(), line) >= 0) {
            // Check breakpoints — normalise the location once, not once per breakpoint
            String normalized = normalizedLocation(systemId);
            java.util.List<Breakpoint> matching = new java.util.ArrayList<>(1);
            Breakpoint[] bps = arm.breakpoints();
            for (int i = 0; i < bps.length; i++) {
                if (arm.lines()[i] == line && bps[i].matchesNormalized(normalized, bps[i].lineNumber())) {
                    matching.add(bps[i]);
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
                    if (conditionSnapshot == null) {
                        conditionSnapshot = captureSnapshot(conditionSupplier, systemId, line);
                    }
                    ConditionEvaluator evaluator = conditionEvaluator;
                    if (evaluator == null) {
                        failure = "condition not evaluated: " + bp.condition();
                    } else {
                        try {
                            hit = evaluator.test(bp.condition(), conditionSnapshot);
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
                if (bp.isLogpoint() && bp.hit().matches(hits)) {
                    if (conditionSnapshot == null) {
                        conditionSnapshot = captureSnapshot(conditionSupplier, systemId, line);
                    }
                    log(bp, line, conditionSnapshot);
                    if (!bp.suspend()) {
                        continue; // a logpoint that runs on
                    }
                }
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

        PausedSnapshot snapshot = captureSnapshot(snapshotSupplier, systemId, line);
        if (pauseRequested) {
            pauseRequested = false;
            if (note.isEmpty()) {
                note = "paused by request";
            }
        }
        rememberPause(systemId, line, depth);
        return block(snapshot, note);
    }

    private void rememberPause(String systemId, int line, int depth) {
        lastPausePath = normalizedLocation(systemId);
        lastPauseLine = line;
        lastPauseDepth = depth;
    }

    /** @return whether {@code line} at {@code depth} is nested in the line execution the run last paused at. */
    private boolean continuesPausedLine(String systemId, int line, int depth) {
        return line == lastPauseLine && depth > lastPauseDepth && lastPausePath.equals(normalizedLocation(systemId));
    }

    /** Publishes the pause and blocks the Saxon thread until the UI resumes or stops the run. */
    private boolean block(PausedSnapshot snapshot, String note) {
        runToLine = 0; // a one-shot target ends at any pause
        stepTarget = null;
        pauseRequested = false;
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

    /** Expands and publishes a logpoint's message (Saxon thread). */
    private void log(Breakpoint bp, int line, PausedSnapshot snapshot) {
        Consumer<LogEvent> sink = logSink;
        if (sink == null) {
            return;
        }
        String text;
        LogFormatter formatter = logFormatter;
        try {
            text = formatter == null ? bp.logMessage() : formatter.format(bp.logMessage(), snapshot);
        } catch (Exception e) {
            text = bp.logMessage() + "  {ERROR: " + e.getMessage() + "}";
        }
        try {
            sink.accept(new LogEvent(bp.filePath(), line, text));
        } catch (RuntimeException e) {
            logger.debug("Logpoint sink failed: {}", e.getMessage());
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
