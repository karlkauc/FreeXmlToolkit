package org.fxt.freexmltoolkit.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The {@code java} program of a runtime: there in a JDK, missing in a runtime without programs. */
class JavaRuntimeTest {

    @TempDir
    Path home;

    @Test
    void theRunningJdkHasOne() {
        Optional<Path> java = JavaRuntime.executable();
        assertTrue(java.isPresent());
        assertTrue(java.get().startsWith(Path.of(System.getProperty("java.home"))), java.get().toString());
    }

    @Test
    void aRuntimeWithoutProgramsHasNone() throws Exception {
        Files.createDirectories(home.resolve("bin"));
        assertEquals(Optional.empty(), JavaRuntime.executableIn(home, false));
        assertEquals(Optional.empty(), JavaRuntime.executableIn(home, true));
        Path exe = Files.createFile(home.resolve("bin/java.exe"));
        assertEquals(Optional.of(exe), JavaRuntime.executableIn(home, true));
    }
}
