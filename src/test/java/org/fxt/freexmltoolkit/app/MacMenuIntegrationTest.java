package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MacMenuIntegrationTest {

    @Test
    void nativeMenuIsOnlyInstalledOnMacOs() {
        AtomicInteger calls = new AtomicInteger();
        assertFalse(MacMenuIntegration.install("Windows 11", () -> calls.incrementAndGet() > 0));
        assertFalse(MacMenuIntegration.install("Linux", () -> calls.incrementAndGet() > 0));
        assertFalse(MacMenuIntegration.install(null, () -> calls.incrementAndGet() > 0));
        assertEquals(0, calls.get());

        assertTrue(MacMenuIntegration.install("Mac OS X", () -> calls.incrementAndGet() > 0));
        assertEquals(1, calls.get());
    }

    @Test
    void missingNativeLibraryDoesNotEscape() {
        assertFalse(MacMenuIntegration.install("Mac OS X", () -> {
            throw new UnsatisfiedLinkError("jnidispatch");
        }));
        assertFalse(MacMenuIntegration.install("Mac OS X", () -> {
            throw new NoClassDefFoundError("de/jangassen/MenuToolkit");
        }));
        assertFalse(MacMenuIntegration.install("Mac OS X", () -> {
            throw new IllegalStateException("no application");
        }));
    }
}
