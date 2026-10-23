package org.fxt.freexmltoolkit.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

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
    void nativeAcceleratorSpellsShortcutAsMeta() {
        // NSMenuFX ignores the Shortcut modifier: "Shortcut+Q" became a bare "q" and Cmd+Q never quit.
        KeyCombination quit = MacMenuIntegration.nativeAccelerator(KeyCombination.valueOf("Shortcut+Q"));
        assertEquals(new KeyCodeCombination(KeyCode.Q, KeyCombination.META_DOWN), quit);
        assertEquals(KeyCombination.ModifierValue.UP, quit.getShortcut());

        KeyCombination settings = MacMenuIntegration.nativeAccelerator(KeyCombination.valueOf("Shortcut+Shift+Comma"));
        assertEquals(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.META_DOWN, KeyCombination.SHIFT_DOWN), settings);

        KeyCombination plain = KeyCombination.valueOf("Ctrl+Meta+F");
        assertSame(plain, MacMenuIntegration.nativeAccelerator(plain), "Meta/Ctrl spellings pass through");
        assertEquals(null, MacMenuIntegration.nativeAccelerator(null));
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
