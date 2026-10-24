package org.fxt.freexmltoolkit.debugger;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * Pure-Java tests for {@link DebugSession} state machine, breakpoint matching,
 * and pause/resume synchronisation.
 */
class DebugSessionTest {

    private static final int AWAIT_MS = 1000;

    @Test
    void initialStateIsIdle() {
        DebugSession session = new DebugSession();
        assertEquals(DebugSession.State.IDLE, session.getState());
        assertEquals(DebugSession.StepMode.NONE, session.getStepMode());
        assertNull(session.getPausedSnapshot());
    }

    @Test
    void breakpointMatchesByLineAndFile() {
        Breakpoint bp = new Breakpoint("/tmp/x.xsl", 5, true);
        assertTrue(bp.matches("/tmp/x.xsl", 5));
        assertFalse(bp.matches("/tmp/x.xsl", 6));
        // Empty systemId on listener side — fall back to line-only
        assertTrue(bp.matches("", 5));
        assertTrue(bp.matches(null, 5));
    }

    @Test
    void breakpointMatchesInMemoryWhenFilePathEmpty() {
        Breakpoint bp = new Breakpoint("", 5, true);
        assertTrue(bp.matches("", 5));
        assertTrue(bp.matches("any.xsl", 5));
        assertFalse(bp.matches("any.xsl", 6));
    }

    @Test
    void toggleAndHasBreakpoint() {
        DebugSession session = new DebugSession();
        assertFalse(session.hasBreakpoint("a.xsl", 3));
        assertTrue(session.toggleBreakpoint("a.xsl", 3));
        assertTrue(session.hasBreakpoint("a.xsl", 3));
        assertFalse(session.toggleBreakpoint("a.xsl", 3));
        assertFalse(session.hasBreakpoint("a.xsl", 3));
    }

    @Test
    void noBreakpointAndNoStepReturnsImmediately() {
        DebugSession session = new DebugSession();
        session.startSession();
        boolean result = session.checkAndPause("x.xsl", 1, 1, () -> emptySnapshot(1));
        assertTrue(result);
        assertEquals(DebugSession.State.RUNNING, session.getState());
    }

    @Test
    void breakpointCausesPauseAndResumeContinues() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("file.xsl", 4);
        session.startSession();

        CountDownLatch pausedLatch = new CountDownLatch(1);
        AtomicBoolean returnValue = new AtomicBoolean();
        AtomicReference<Throwable> error = new AtomicReference<>();

        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                pausedLatch.countDown();
            }
        });

        Thread saxon = new Thread(() -> {
            try {
                returnValue.set(session.checkAndPause("file.xsl", 4, 2, () -> emptySnapshot(4)));
            } catch (Throwable t) {
                error.set(t);
            }
        }, "test-saxon");
        saxon.start();

        assertTrue(pausedLatch.await(AWAIT_MS, TimeUnit.MILLISECONDS), "Should have paused");
        assertEquals(DebugSession.State.PAUSED, session.getState());
        assertNotNull(session.getPausedSnapshot());
        assertEquals(4, session.getPausedSnapshot().lineNumber());

        session.requestContinue();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive(), "Saxon thread should have unblocked");
        assertNull(error.get());
        assertTrue(returnValue.get());
        assertEquals(DebugSession.State.RUNNING, session.getState());
    }

    @Test
    void requestStopUnblocksWithinHalfSecond() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("a.xsl", 1);
        session.startSession();

        CountDownLatch pausedLatch = new CountDownLatch(1);
        AtomicBoolean returnValue = new AtomicBoolean(true);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) pausedLatch.countDown();
        });

        Thread saxon = new Thread(
                () -> returnValue.set(session.checkAndPause("a.xsl", 1, 1, () -> emptySnapshot(1))),
                "test-saxon");
        saxon.start();
        assertTrue(pausedLatch.await(AWAIT_MS, TimeUnit.MILLISECONDS));

        long t0 = System.currentTimeMillis();
        session.requestStop();
        saxon.join(500);
        long elapsed = System.currentTimeMillis() - t0;
        assertFalse(saxon.isAlive(), "Saxon thread did not unblock within 500ms (elapsed=" + elapsed + ")");
        assertFalse(returnValue.get(), "checkAndPause must return false after stop");
        assertEquals(DebugSession.State.STOPPED, session.getState());
    }

    @Test
    void stepIntoPausesOnAnyEnter() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.startSession();
        // Manually arm step-into without prior pause
        session.requestStepInto();

        CountDownLatch latch = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) latch.countDown();
        });

        Thread saxon = new Thread(
                () -> session.checkAndPause("any.xsl", 99, 5, () -> emptySnapshot(99)),
                "test-saxon");
        saxon.start();

        assertTrue(latch.await(AWAIT_MS, TimeUnit.MILLISECONDS), "STEP_INTO must always pause");
        session.requestContinue();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive());
    }

    @Test
    void stepOverSkipsDeeperFramesAndPausesAtSiblingDepth() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("x.xsl", 1);
        session.startSession();

        // Phase 1: pause at line 1, depth 2 (callStack has 2 frames)
        CountDownLatch firstPause = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger pauseCount = new java.util.concurrent.atomic.AtomicInteger();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                if (pauseCount.incrementAndGet() == 1) firstPause.countDown();
            }
        });
        Thread saxon = new Thread(() -> {
            // First enter — pauses on breakpoint
            session.checkAndPause("x.xsl", 1, 2, () -> snapshotWithDepth(1, 2));
            // After resume with STEP_OVER and stepTargetDepth=2:
            //   deeper enter at depth 3 must NOT pause
            session.checkAndPause("x.xsl", 50, 3, () -> snapshotWithDepth(50, 3));
            //   sibling enter at depth 2 MUST pause
            session.checkAndPause("x.xsl", 51, 2, () -> snapshotWithDepth(51, 2));
        }, "saxon");
        saxon.start();

        assertTrue(firstPause.await(AWAIT_MS, TimeUnit.MILLISECONDS), "Initial breakpoint should pause");
        // pausedSnapshot.callStack().size() is 2 → stepTargetDepth = 2
        session.requestStepOver();

        // Wait for second pause (at depth=2 sibling enter)
        long deadline = System.currentTimeMillis() + AWAIT_MS;
        while (pauseCount.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(2, pauseCount.get(), "STEP_OVER should pause at sibling-depth enter");
        assertEquals(51, session.getPausedSnapshot().lineNumber());

        session.requestStop();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive());
    }

    private static PausedSnapshot snapshotWithDepth(int line, int depth) {
        java.util.List<DebugStackFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < depth; i++) {
            frames.add(new DebugStackFrame("frame" + i, "x.xsl", line, java.util.List.of()));
        }
        return new PausedSnapshot("x.xsl", line, frames, List.of(), "");
    }

    @Test
    void closeReleasesBlockedThread() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("x.xsl", 1);
        session.startSession();

        CountDownLatch pausedLatch = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) pausedLatch.countDown();
        });
        Thread saxon = new Thread(() -> session.checkAndPause("x.xsl", 1, 1, () -> emptySnapshot(1)));
        saxon.start();
        assertTrue(pausedLatch.await(AWAIT_MS, TimeUnit.MILLISECONDS));

        session.close();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive(), "close() must unblock paused thread");
    }

    private static PausedSnapshot emptySnapshot(int line) {
        return new PausedSnapshot("", line, List.of(), List.of(), "");
    }

    // ------------------------------------------------------------------
    // Test helpers — exercise StepOver setup without requiring a full pause cycle
    // ------------------------------------------------------------------

    @Test
    void startSessionWithStepIntoPausesAtTheFirstInstruction() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.startSession(DebugSession.StepMode.STEP_INTO);
        assertEquals(DebugSession.StepMode.STEP_INTO, session.getStepMode());

        CountDownLatch pausedLatch = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) pausedLatch.countDown();
        });
        // No breakpoints at all — the very first traced instruction must still pause.
        Thread saxon = new Thread(() -> session.checkAndPause("x.xsl", 1, 1, () -> emptySnapshot(1)));
        saxon.start();
        assertTrue(pausedLatch.await(AWAIT_MS, TimeUnit.MILLISECONDS), "pause on entry");
        assertEquals(1, session.getPausedSnapshot().lineNumber());

        session.requestContinue();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive());
    }

    @Test
    void restartingASessionKeepsItsBreakpoints() {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("x.xsl", 4);
        session.startSession();
        session.close();
        session.startSession(DebugSession.StepMode.NONE);
        assertEquals(DebugSession.State.RUNNING, session.getState());
        assertTrue(session.hasBreakpoint("x.xsl", 4), "startSession() must not clear breakpoints");
    }

    // ----- conditional breakpoints -------------------------------------------------------

    @Test
    void falseConditionDoesNotPauseAndTrueOnePausesWithANote() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.setCondition("c.xsl", 4, "$n = 2");
        java.util.concurrent.atomic.AtomicInteger evaluations = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger hit = new java.util.concurrent.atomic.AtomicInteger(0);
        session.setConditionEvaluator((condition, snap) -> {
            evaluations.incrementAndGet();
            return hit.get() == 2;
        });
        session.startSession();
        CountDownLatch paused = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) paused.countDown();
        });
        Thread saxon = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                hit.set(i);
                session.checkAndPause("c.xsl", 4, 1, () -> emptySnapshot(4));
            }
        }, "test-saxon");
        saxon.start();
        assertTrue(paused.await(AWAIT_MS, TimeUnit.MILLISECONDS), "the true condition pauses");
        assertEquals(2, hit.get(), "not on the first pass, where the condition was false");
        assertEquals("condition: $n = 2", session.getPauseNote());
        session.requestContinue();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive());
        assertEquals(3, evaluations.get(), "evaluated on every pass of the line");
    }

    @Test
    void failingConditionPausesAndReportsTheError() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.setCondition("c.xsl", 4, "$missing");
        session.setConditionEvaluator((condition, snap) -> {
            throw new IllegalStateException("Variable $missing has not been declared");
        });
        session.startSession();
        CountDownLatch paused = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) paused.countDown();
        });
        Thread saxon = new Thread(() -> session.checkAndPause("c.xsl", 4, 1, () -> emptySnapshot(4)), "test-saxon");
        saxon.start();
        assertTrue(paused.await(AWAIT_MS, TimeUnit.MILLISECONDS), "an error must not silently skip the breakpoint");
        assertTrue(session.getPauseNote().startsWith("condition failed ($missing): "), session.getPauseNote());
        session.requestStop();
        saxon.join(AWAIT_MS);
    }

    @Test
    void unconditionalBreakpointPausesEvenWhenAnotherConditionIsFalse() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("", 4);
        session.setCondition("c.xsl", 4, "false()");
        // Both breakpoints of the line are evaluated (hit counts), the false one does not stop
        // the unconditional one from pausing.
        session.setConditionEvaluator((condition, snap) -> false);
        session.startSession();
        CountDownLatch paused = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) paused.countDown();
        });
        Thread saxon = new Thread(() -> session.checkAndPause("c.xsl", 4, 1, () -> emptySnapshot(4)), "test-saxon");
        saxon.start();
        assertTrue(paused.await(AWAIT_MS, TimeUnit.MILLISECONDS));
        assertEquals("", session.getPauseNote());
        session.requestStop();
        saxon.join(AWAIT_MS);
    }

    @Test
    void setConditionCreatesUpdatesAndClears() {
        DebugSession session = new DebugSession();
        assertNull(session.conditionAt("c.xsl", 4));
        assertTrue(session.setCondition("c.xsl", 4, " @id = 1 "));
        assertEquals("@id = 1", session.conditionAt("c.xsl", 4), "creates the breakpoint, trimmed");
        assertTrue(session.hasBreakpoint("c.xsl", 4));
        session.setCondition("c.xsl", 4, "");
        assertEquals("", session.conditionAt("c.xsl", 4), "blank = unconditional, breakpoint kept");
        assertEquals(1, session.getBreakpoints().size());
    }

    // ----- hit counts --------------------------------------------------------------------

    private static java.util.List<Integer> passesThatPause(DebugSession session, int passes) throws InterruptedException {
        java.util.List<Integer> paused = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger pass = new java.util.concurrent.atomic.AtomicInteger();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                paused.add(pass.get());
                session.requestContinue();
            }
        });
        session.startSession();
        Thread saxon = new Thread(() -> {
            for (int i = 1; i <= passes; i++) {
                pass.set(i);
                session.checkAndPause("h.xsl", 7, 1, () -> emptySnapshot(7));
            }
        }, "test-saxon");
        saxon.start();
        saxon.join(5 * AWAIT_MS);
        assertFalse(saxon.isAlive());
        return paused;
    }

    @Test
    void hitRulesDecideWhichPassPauses() throws InterruptedException {
        DebugSession equal = new DebugSession();
        equal.setHitCondition("h.xsl", 7, new HitCondition(HitCondition.Mode.EQUAL, 3));
        assertEquals(List.of(3), passesThatPause(equal, 6));
        assertEquals(6, equal.hitCount("h.xsl", 7), "every pass is counted");

        DebugSession atLeast = new DebugSession();
        atLeast.setHitCondition("h.xsl", 7, new HitCondition(HitCondition.Mode.AT_LEAST, 5));
        assertEquals(List.of(5, 6), passesThatPause(atLeast, 6));

        DebugSession every = new DebugSession();
        every.setHitCondition("h.xsl", 7, new HitCondition(HitCondition.Mode.MULTIPLE_OF, 2));
        assertEquals(List.of(2, 4, 6), passesThatPause(every, 6));
        assertTrue(every.getPauseNote().startsWith("hit 6 (every 2nd hit)"), every.getPauseNote());
    }

    @Test
    void onlyPassesWithATrueConditionCountAsHits() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.updateBreakpoint("h.xsl", 7, bp -> bp.withCondition("odd")
                .withHit(new HitCondition(HitCondition.Mode.EQUAL, 2)));
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        session.setConditionEvaluator((condition, snap) -> calls.incrementAndGet() % 2 == 1); // passes 1, 3, 5
        assertEquals(List.of(3), passesThatPause(session, 6), "the 2nd true pass is pass 3");
        assertEquals(3, session.hitCount("h.xsl", 7));
    }

    @Test
    void hitCountsStartOverWithEveryRun() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("h.xsl", 7);
        session.setHitCondition("h.xsl", 7, new HitCondition(HitCondition.Mode.EQUAL, 99));
        passesThatPause(session, 4);
        assertEquals(4, session.hitCount("h.xsl", 7));
        session.startSession();
        assertEquals(0, session.hitCount("h.xsl", 7), "a new run (or Restart) resets the counts");
    }

    @Test
    void nestedInstructionsOfTheSameLineAreOneHit() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.setHitCondition("n.xsl", 4, new HitCondition(HitCondition.Mode.EQUAL, 99));
        session.startSession();
        // Three loop passes over line 4: the literal element (depth 3) and the value-of nested
        // in it (depth 4) are one line execution; the next pass re-enters at depth 3.
        for (int pass = 0; pass < 3; pass++) {
            session.checkAndPause("n.xsl", 3, 2, () -> emptySnapshot(3));
            session.checkAndPause("n.xsl", 4, 3, () -> emptySnapshot(4));
            session.checkAndPause("n.xsl", 4, 4, () -> emptySnapshot(4));
        }
        assertEquals(3, session.hitCount("n.xsl", 4), "one hit per execution of the line");
        // A loop body that is only line 4 (no other traced line in between) still counts each pass.
        session.startSession();
        for (int pass = 0; pass < 4; pass++) {
            session.checkAndPause("n.xsl", 4, 3, () -> emptySnapshot(4));
        }
        assertEquals(4, session.hitCount("n.xsl", 4));
    }

    // ----- enable / disable / mute -------------------------------------------------------

    @Test
    void disabledBreakpointKeepsItsSettingsButNeverPauses() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.updateBreakpoint("h.xsl", 7, bp -> bp.withCondition("true()"));
        assertTrue(session.setEnabled("h.xsl", 7, false));
        assertFalse(session.hasBreakpoint("h.xsl", 7), "hasBreakpoint only reports enabled ones");
        assertEquals("true()", session.breakpointAt("h.xsl", 7).condition(), "settings kept");
        session.setConditionEvaluator((c, s) -> true);
        assertEquals(List.of(), passesThatPause(session, 3));
        assertEquals(0, session.hitCount("h.xsl", 7), "a disabled breakpoint counts no hits");
        assertFalse(session.setEnabled("h.xsl", 99, true), "no breakpoint on that line");
    }

    @Test
    void mutedBreakpointsDoNotPauseButStepsDo() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("h.xsl", 7);
        session.setMuted(true);
        assertTrue(session.isMuted());
        assertEquals(List.of(), passesThatPause(session, 3), "muted: the breakpoint does not stop the run");
        assertEquals(0, session.hitCount("h.xsl", 7));

        session.startSession(DebugSession.StepMode.STEP_INTO);
        CountDownLatch paused = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) paused.countDown();
        });
        Thread saxon = new Thread(() -> session.checkAndPause("h.xsl", 2, 1, () -> emptySnapshot(2)), "test-saxon");
        saxon.start();
        assertTrue(paused.await(AWAIT_MS, TimeUnit.MILLISECONDS), "stepping still pauses while muted");
        session.requestStop();
        saxon.join(AWAIT_MS);

        session.setMuted(false);
        assertEquals(List.of(1, 2), passesThatPause(session, 2), "unmuted: back to normal");
    }

    // ----- pause (break) -----------------------------------------------------------------

    @Test
    void pauseRequestBreaksIntoARunningLoop() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.startSession();
        java.util.concurrent.atomic.AtomicBoolean keepRunning = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicInteger instructions = new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch paused = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) paused.countDown();
        });
        Thread saxon = new Thread(() -> {
            while (keepRunning.get()) { // an endless recursion / loop without breakpoints
                int line = 10 + instructions.incrementAndGet() % 3;
                if (!session.checkAndPause("loop.xsl", line, 5, () -> emptySnapshot(line))) {
                    return;
                }
            }
        }, "test-saxon");
        saxon.start();
        Thread.sleep(50);
        assertEquals(DebugSession.State.RUNNING, session.getState(), "no breakpoint: it just runs");
        session.requestPause();
        assertTrue(paused.await(AWAIT_MS, TimeUnit.MILLISECONDS), "Pause breaks in at the next instruction");
        assertEquals("paused by request", session.getPauseNote());
        assertNotNull(session.getPausedSnapshot());

        // Continue runs on without pausing again.
        int before = instructions.get();
        session.requestContinue();
        Thread.sleep(50);
        assertEquals(DebugSession.State.RUNNING, session.getState());
        assertTrue(instructions.get() > before + 1, "running again");
        keepRunning.set(false);
        session.requestStop();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive());
    }

    @Test
    void pauseRequestIsIgnoredWhenNotRunning() {
        DebugSession session = new DebugSession();
        session.requestPause();
        assertEquals(DebugSession.State.IDLE, session.getState());
        assertEquals(DebugSession.StepMode.NONE, session.getStepMode(), "no step armed while idle");
    }

    // ----- run to cursor -----------------------------------------------------------------

    @Test
    void runToCursorContinuesAndStopsOnceAtTheTarget() throws InterruptedException {
        DebugSession session = new DebugSession();
        assertFalse(session.runTo("r.xsl", 5), "no session to run");
        session.toggleBreakpoint("r.xsl", 2);
        java.util.List<String> pauses = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                pauses.add(session.getPausedSnapshot().lineNumber() + ":" + session.getPauseNote());
                if (first.getCount() > 0) first.countDown(); else second.countDown();
            }
        });
        session.startSession();
        Thread saxon = new Thread(() -> {
            for (int line : new int[]{1, 2, 3, 4, 5, 6, 5}) {
                if (!session.checkAndPause("r.xsl", line, 1, () -> emptySnapshot(line))) return;
            }
        }, "test-saxon");
        saxon.start();
        assertTrue(first.await(AWAIT_MS, TimeUnit.MILLISECONDS), "the breakpoint on line 2");
        assertTrue(session.canRunTo());
        assertTrue(session.runTo("r.xsl", 5), "Run to Cursor from the pause continues");
        assertTrue(second.await(AWAIT_MS, TimeUnit.MILLISECONDS), "and stops at line 5");
        assertEquals(List.of("2:", "5:run to cursor"), pauses);
        assertEquals(0, session.getRunToLine(), "one-shot: gone once reached");
        session.requestContinue();
        saxon.join(AWAIT_MS);
        assertFalse(saxon.isAlive());
        assertEquals(2, pauses.size(), "the second pass over line 5 runs through");
    }

    @Test
    void runToCursorIsDroppedByAnEarlierPauseAndIgnoresMute() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("r.xsl", 3);
        session.setMuted(true);
        java.util.List<Integer> pauses = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                pauses.add(session.getPausedSnapshot().lineNumber());
                session.requestContinue();
            }
        });
        session.startSession();
        assertTrue(session.runTo("r.xsl", 4));
        Thread saxon = new Thread(() -> {
            for (int line : new int[]{3, 4, 4}) {
                session.checkAndPause("r.xsl", line, 1, () -> emptySnapshot(line));
            }
        }, "test-saxon");
        saxon.start();
        saxon.join(AWAIT_MS);
        assertEquals(List.of(4), pauses, "muted breakpoint on 3 skipped, the cursor target still stops, once");

        session.startSession();
        assertTrue(session.runTo("r.xsl", 9));
        session.startSession();
        assertEquals(0, session.getRunToLine(), "a new run drops a pending target");
    }

    // ----- not reached in the last run ---------------------------------------------------

    @Test
    void zeroHitsAfterACompletedRunMeanNotReached() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.toggleBreakpoint("u.xsl", 3);  // reached
        session.toggleBreakpoint("u.xsl", 9);  // never reached
        session.toggleBreakpoint("u.xsl", 10);
        session.setEnabled("u.xsl", 10, false); // disabled: not judged
        assertEquals(List.of(3), runLines(session, 3), "line 3 runs, line 9 never");
        assertFalse(session.notReachedInLastRun("u.xsl", 9), "not before the run ended");
        session.markRunEnded(true);
        assertTrue(session.notReachedInLastRun("u.xsl", 9));
        assertFalse(session.notReachedInLastRun("u.xsl", 3));
        assertFalse(session.notReachedInLastRun("u.xsl", 10), "disabled ones are not judged");
        session.markRunEnded(false);
        assertFalse(session.notReachedInLastRun("u.xsl", 9), "a stopped or failed run proves nothing");
        session.markRunEnded(true);
        session.startSession();
        assertFalse(session.notReachedInLastRun("u.xsl", 9), "a new run starts over");
    }

    /** Runs line {@code line} once through the session, continuing every pause; returns the paused lines. */
    private static List<Integer> runLines(DebugSession session, int line) throws InterruptedException {
        List<Integer> paused = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.addPropertyChangeListener(evt -> {
            if (evt.getNewValue() == DebugSession.State.PAUSED) {
                paused.add(session.getPausedSnapshot().lineNumber());
                session.requestContinue();
            }
        });
        session.startSession();
        Thread saxon = new Thread(() -> session.checkAndPause("u.xsl", line, 1, () -> emptySnapshot(line)), "test-saxon");
        saxon.start();
        saxon.join(AWAIT_MS);
        return paused;
    }

    // ----- logpoints -----------------------------------------------------------------------

    @Test
    void aLogpointLogsEveryHitWithoutPausing() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.updateBreakpoint("h.xsl", 7, bp -> bp.withLog("pass {n}", false));
        java.util.List<String> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        session.setLogHandler((template, snap) -> template.replace("{n}", Integer.toString(n.incrementAndGet())),
                event -> logged.add(event.line() + ":" + event.text()));
        assertEquals(List.of(), passesThatPause(session, 3), "never pauses");
        assertEquals(List.of("7:pass 1", "7:pass 2", "7:pass 3"), logged);
        assertEquals(3, session.hitCount("h.xsl", 7));
    }

    @Test
    void aSuspendingLogpointLogsThenPausesAndHitRulesApply() throws InterruptedException {
        DebugSession session = new DebugSession();
        session.updateBreakpoint("h.xsl", 7, bp -> bp.withLog("hit", true)
                .withHit(new HitCondition(HitCondition.Mode.MULTIPLE_OF, 2)));
        java.util.List<String> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.setLogHandler((template, snap) -> template, event -> logged.add(event.text()));
        assertEquals(List.of(2, 4), passesThatPause(session, 4), "pauses where the hit rule matches");
        assertEquals(List.of("hit", "hit"), logged, "and logs exactly there");
    }

    @Test
    void conditionsAndLogpointsUseTheConditionSnapshotOnly() {
        DebugSession session = new DebugSession();
        session.updateBreakpoint("c.xsl", 3, bp -> bp.withCondition("$never"));
        session.updateBreakpoint("c.xsl", 5, bp -> bp.withLog("log", false));
        PausedSnapshot light = emptySnapshot(3);
        java.util.concurrent.atomic.AtomicInteger lightCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger fullCalls = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<PausedSnapshot> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.setConditionEvaluator((condition, snap) -> {
            seen.add(snap);
            return false;
        });
        session.setLogHandler((template, snap) -> {
            seen.add(snap);
            return template;
        }, event -> { });
        for (int pass = 0; pass < 5; pass++) {
            for (int line : new int[]{3, 5}) {
                assertTrue(session.checkAndPause("c.xsl", line, 1,
                        () -> { lightCalls.incrementAndGet(); return light; },
                        () -> { fullCalls.incrementAndGet(); return emptySnapshot(line); }));
            }
        }
        assertEquals(10, lightCalls.get(), "one condition snapshot per pass over a breakpoint");
        assertEquals(0, fullCalls.get(), "no full snapshot without a pause");
        assertTrue(seen.stream().allMatch(snap -> snap == light), "conditions and logs see the light snapshot");
        assertEquals(10, seen.size());
    }

    @Test
    void theInstructionPathFollowsEveryBreakpointChange() {
        DebugSession session = new DebugSession();
        java.util.List<Integer> logged = new java.util.concurrent.CopyOnWriteArrayList<>();
        session.setLogHandler((template, snap) -> template, event -> logged.add(event.line()));
        session.updateBreakpoint("m.xsl", 3, bp -> bp.withLog("x", false));
        java.util.function.IntConsumer pass = line -> assertTrue(
                session.checkAndPause("m.xsl", line, 1, () -> emptySnapshot(line)));

        pass.accept(3);
        session.shiftLines("m.xsl", new LineShift(1, 0, 0, 2)); // two lines inserted above
        pass.accept(3);
        pass.accept(5);
        session.setEnabled("m.xsl", 5, false);
        pass.accept(5);
        session.setEnabled("m.xsl", 5, true);
        pass.accept(5);
        session.removeBreakpoint("m.xsl", 5);
        pass.accept(5);
        assertEquals(List.of(3, 5, 5), logged, "moved, disabled, re-enabled and removed breakpoints are honoured");
    }
}
