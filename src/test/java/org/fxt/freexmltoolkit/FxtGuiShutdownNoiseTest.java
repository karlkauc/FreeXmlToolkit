package org.fxt.freexmltoolkit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FxtGuiShutdownNoiseTest {

    private static NullPointerException traceless() {
        NullPointerException npe = new NullPointerException();
        npe.setStackTrace(new StackTraceElement[0]);
        return npe;
    }

    @Test
    void tracelessNullPointerDuringShutdownIsNoise() {
        assertTrue(FxtGui.isShutdownNoise(traceless(), true));
    }

    @Test
    void sameExceptionWhileRunningIsReported() {
        assertFalse(FxtGui.isShutdownNoise(traceless(), false));
    }

    @Test
    void exceptionsWithATraceOrOfAnotherTypeAreReported() {
        assertFalse(FxtGui.isShutdownNoise(new NullPointerException("x"), true));
        IllegalStateException other = new IllegalStateException();
        other.setStackTrace(new StackTraceElement[0]);
        assertFalse(FxtGui.isShutdownNoise(other, true));
        assertFalse(FxtGui.isShutdownNoise(null, true));
    }
}
