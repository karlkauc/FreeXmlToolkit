package org.fxt.freexmltoolkit.service.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;

class ErrorSignatureTest {

    private static StackTraceElement frame(String cls, String method, int line) {
        return new StackTraceElement(cls, method, cls.substring(cls.lastIndexOf('.') + 1) + ".java", line);
    }

    private static <T extends Throwable> T withFrames(T t, StackTraceElement... frames) {
        t.setStackTrace(frames);
        return t;
    }

    @Test
    void messagesNeverLeakEvenWhenTheyContainPaths() {
        String secret = "C:\\Users\\alice\\secret-customer-data.xml";
        IOException cause = new IOException("Cannot read " + secret);
        IllegalStateException outer = new IllegalStateException("Failed for /home/alice/" + secret, cause);

        ErrorSignature sig = ErrorSignature.of(outer);

        assertFalse(sig.detail().contains("alice"), sig.detail());
        assertFalse(sig.detail().contains("secret"), sig.detail());
        assertFalse(sig.errorCode().contains("alice"));
        assertEquals("java.io.IOException", sig.errorCode());
        assertTrue(sig.detail().startsWith("java.lang.IllegalStateException <- java.io.IOException"), sig.detail());
    }

    @Test
    void hashIsStableWhenOnlyLineNumbersDiffer() {
        RuntimeException a = withFrames(new RuntimeException("x"),
                frame("org.fxt.freexmltoolkit.Foo", "bar", 10),
                frame("org.fxt.freexmltoolkit.Foo", "baz", 20));
        RuntimeException b = withFrames(new RuntimeException("totally different message"),
                frame("org.fxt.freexmltoolkit.Foo", "bar", 99),
                frame("org.fxt.freexmltoolkit.Foo", "baz", 123));

        ErrorSignature sa = ErrorSignature.of(a);
        ErrorSignature sb = ErrorSignature.of(b);

        assertEquals(sa.hash(), sb.hash());
        assertNotEquals(sa.detail(), sb.detail()); // line numbers are still shown in the detail
        assertTrue(sa.hash().matches("[0-9a-f]{16}"));
    }

    @Test
    void hashDiffersForDifferentFramesOrTypes() {
        RuntimeException a = withFrames(new RuntimeException(),
                frame("org.fxt.freexmltoolkit.Foo", "bar", 10));
        RuntimeException b = withFrames(new RuntimeException(),
                frame("org.fxt.freexmltoolkit.Foo", "other", 10));
        IllegalArgumentException c = withFrames(new IllegalArgumentException(),
                frame("org.fxt.freexmltoolkit.Foo", "bar", 10));

        assertNotEquals(ErrorSignature.of(a).hash(), ErrorSignature.of(b).hash());
        assertNotEquals(ErrorSignature.of(a).hash(), ErrorSignature.of(c).hash());
    }

    @Test
    void causeChainIsListedOuterFirstAndCodeIsRootMost() {
        IOException root = withFrames(new IOException("disk"), frame("java.io.FileInputStream", "open0", -2));
        UncheckedIOException mid = new UncheckedIOException("mid", root);
        RuntimeException outer = new RuntimeException("outer", mid);

        ErrorSignature sig = ErrorSignature.of(outer);

        assertEquals("java.io.IOException", sig.errorCode());
        assertTrue(sig.detail().startsWith(
                "java.lang.RuntimeException <- java.io.UncheckedIOException <- java.io.IOException"), sig.detail());
        // frames come from the root cause
        assertTrue(sig.detail().contains("java.io.FileInputStream#open0"), sig.detail());
    }

    @Test
    void foreignFrameRunsAreCollapsedAndOwnFramesPreferred() {
        RuntimeException t = withFrames(new NullPointerException(),
                frame("java.util.Objects", "requireNonNull", 1),
                frame("java.util.HashMap", "get", 2),
                frame("javafx.scene.Node", "a", 3),
                frame("javafx.scene.Node", "b", 4),
                frame("javafx.scene.Node", "c", 5),
                frame("org.fxt.freexmltoolkit.controls.Foo", "handle", 42),
                frame("com.sun.javafx.Event", "x", 6),
                frame("com.sun.javafx.Event", "y", 7));

        String detail = ErrorSignature.of(t).detail();

        assertTrue(detail.contains("at java.util.Objects#requireNonNull:1"), detail); // top frames kept
        assertTrue(detail.contains("at java.util.HashMap#get:2"), detail);
        assertTrue(detail.contains("at … (3 frames)"), detail);
        assertTrue(detail.contains("at org.fxt.freexmltoolkit.controls.Foo#handle:42"), detail);
        assertTrue(detail.endsWith("at … (2 frames)"), detail);
        assertFalse(detail.contains("javafx.scene.Node#a"), detail);
    }

    @Test
    void atMostTwentyFramesAndMaxLength() {
        StackTraceElement[] frames = new StackTraceElement[100];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = frame("org.fxt.freexmltoolkit.Deep" + i, "m", i);
        }
        RuntimeException t = withFrames(new RuntimeException(), frames);

        String detail = ErrorSignature.of(t).detail();
        long frameLines = detail.lines().filter(l -> l.startsWith("at org.fxt")).count();
        assertEquals(ErrorSignature.MAX_FRAMES, frameLines);
        assertTrue(detail.endsWith("at … (80 frames)"), detail);

        String small = ErrorSignature.of(t, 20, 100).detail();
        assertTrue(small.length() <= 100);
    }

    @Test
    void lambdaCountersAndHiddenClassIdsAreNormalized() {
        RuntimeException a = withFrames(new RuntimeException(),
                frame("org.fxt.freexmltoolkit.Foo", "lambda$run$3", 1),
                frame("org.fxt.freexmltoolkit.Foo$$Lambda/0x000001", "run", 1));
        RuntimeException b = withFrames(new RuntimeException(),
                frame("org.fxt.freexmltoolkit.Foo", "lambda$run$7", 1),
                frame("org.fxt.freexmltoolkit.Foo$$Lambda/0x0000ff", "run", 1));
        assertEquals(ErrorSignature.of(a).hash(), ErrorSignature.of(b).hash());
    }

    @Test
    void cyclicCauseChainTerminates() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        ErrorSignature sig = ErrorSignature.of(a);
        assertEquals("java.lang.RuntimeException", sig.errorCode());
    }
}
