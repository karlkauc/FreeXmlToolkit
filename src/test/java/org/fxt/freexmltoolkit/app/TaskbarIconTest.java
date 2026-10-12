package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TaskbarIconTest {

    @Test
    void awtIsOnlyTouchedOnMacOs() {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(TaskbarIcon.apply("Windows 11", calls::incrementAndGet));
        assertFalse(TaskbarIcon.apply("Linux", calls::incrementAndGet));
        assertFalse(TaskbarIcon.apply(null, calls::incrementAndGet));
        assertEquals(0, calls.get());

        assertTrue(TaskbarIcon.apply("Mac OS X", calls::incrementAndGet));
        assertEquals(1, calls.get());
    }

    @Test
    void awtErrorDoesNotEscape() {
        // Telemetry hash 0b8688e0cba1d881: an assistive technology that cannot be loaded
        assertFalse(TaskbarIcon.apply("Mac OS X", () -> {
            throw new java.awt.AWTError("Assistive Technology not found: com.example.Missing");
        }));
    }

    @Test
    void unsupportedPlatformAndMissingClassesDoNotEscape() {
        assertFalse(TaskbarIcon.apply("Mac OS X", () -> {
            throw new UnsupportedOperationException("no taskbar");
        }));
        assertFalse(TaskbarIcon.apply("Mac OS X", () -> {
            throw new NoClassDefFoundError("java/awt/Taskbar");
        }));
    }
}
