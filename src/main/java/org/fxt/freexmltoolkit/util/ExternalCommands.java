package org.fxt.freexmltoolkit.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Helpers for running programs the user installed separately (Arelle, external XSL-FO
 * processors, …): finding them on the {@code PATH} and guarding Windows batch scripts.
 */
public final class ExternalCommands {

    private ExternalCommands() {
    }

    /** @return whether this JVM runs on Windows */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Looks up a program on the {@code PATH}. On Windows each name is also tried with
     * {@code .exe}, {@code .cmd} and {@code .bat} unless it already has an extension.
     *
     * @param names program names in order of preference
     * @return the first executable file found
     */
    public static Optional<Path> findOnPath(String... names) {
        return findOnPath(System.getenv("PATH"), isWindows(), names);
    }

    static Optional<Path> findOnPath(String pathVariable, boolean windows, String... names) {
        if (pathVariable == null || pathVariable.isBlank() || names == null) {
            return Optional.empty();
        }
        for (String name : names) {
            for (String candidateName : candidates(name, windows)) {
                for (String dir : pathVariable.split(File.pathSeparator)) {
                    if (dir.isBlank()) {
                        continue;
                    }
                    try {
                        Path candidate = Path.of(dir, candidateName);
                        if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
                            return Optional.of(candidate);
                        }
                    } catch (RuntimeException ignored) {
                        // an invalid PATH entry: skip it
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static List<String> candidates(String name, boolean windows) {
        List<String> list = new ArrayList<>();
        if (name == null || name.isBlank()) {
            return list;
        }
        boolean hasExtension = name.lastIndexOf('.') > Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (windows && !hasExtension) {
            list.add(name + ".exe");
            list.add(name + ".cmd");
            list.add(name + ".bat");
        }
        list.add(name);
        return list;
    }

    /** @return whether the program is a Windows batch script ({@code .cmd} / {@code .bat}) */
    public static boolean isBatchScript(Path command) {
        String name = command == null || command.getFileName() == null ? ""
                : command.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".cmd") || name.endsWith(".bat");
    }

    /**
     * Windows runs a {@code .cmd} / {@code .bat} through {@code cmd.exe}, which reads {@code &}, {@code |},
     * {@code %} and the like in arguments as its own syntax, whatever the quoting.
     *
     * @return whether one of the arguments would be interpreted by {@code cmd.exe}
     */
    public static boolean unsafeForBatch(Path command, List<String> arguments) {
        if (!isBatchScript(command) || arguments == null) {
            return false;
        }
        for (String argument : arguments) {
            if (argument != null && argument.chars().anyMatch(c -> "&|<>^%!\"\r\n".indexOf(c) >= 0)) {
                return true;
            }
        }
        return false;
    }
}
