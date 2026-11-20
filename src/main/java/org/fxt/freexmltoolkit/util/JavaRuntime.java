package org.fxt.freexmltoolkit.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The {@code java} program of the runtime the application runs on, to start Java tools (an external Saxon)
 * in a process of their own. The packaged application keeps only {@code bin/java} of its runtime's programs
 * (jlink task in {@code build.gradle.kts}); a run from the IDE or Gradle uses the JDK's.
 */
public final class JavaRuntime {

    private JavaRuntime() {
    }

    /** @return {@code bin/java} ({@code java.exe} on Windows) of the running runtime, empty when it has none */
    public static Optional<Path> executable() {
        String home = System.getProperty("java.home");
        if (home == null || home.isBlank()) {
            return Optional.empty();
        }
        return executableIn(Path.of(home), isWindows());
    }

    static Optional<Path> executableIn(Path javaHome, boolean windows) {
        Path java = javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
        return Files.isRegularFile(java) && (windows || Files.isExecutable(java)) ? Optional.of(java) : Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
