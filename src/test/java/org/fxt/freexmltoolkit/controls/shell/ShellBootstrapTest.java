package org.fxt.freexmltoolkit.controls.shell;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ShellBootstrapTest {

    @Test
    void scheduleAndShutdownAreSafe() {
        ShellBootstrap boot = ShellBootstrap.getInstance();
        // Scheduling startup tasks must not throw (the live network check runs on a background
        // thread and is independent of this call returning).
        assertDoesNotThrow(boot::scheduleStartupTasks);
        // Shutdown must be idempotent and not throw.
        assertDoesNotThrow(boot::shutdown);
        assertDoesNotThrow(boot::shutdown);
    }
}
