package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The shell status bar's memory monitor formats the JVM heap usage as "used / max MB". */
class MemoryStatusTest {

    @Test
    void formatsUsedAndMaxHeap() {
        String text = UnifiedShellView.memoryText();
        assertTrue(text.matches("\\d+ / \\d+ MB"), "memory text must be 'used / max MB', was: " + text);
        String[] parts = text.replace(" MB", "").split(" / ");
        int used = Integer.parseInt(parts[0]);
        int max = Integer.parseInt(parts[1]);
        assertTrue(used >= 0 && max > 0 && used <= max, "used=" + used + " max=" + max);
    }
}
