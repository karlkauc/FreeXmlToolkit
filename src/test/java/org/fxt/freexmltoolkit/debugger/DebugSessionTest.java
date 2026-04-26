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
}
