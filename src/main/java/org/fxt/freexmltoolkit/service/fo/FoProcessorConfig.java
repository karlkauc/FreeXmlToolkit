package org.fxt.freexmltoolkit.service.fo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.fxt.freexmltoolkit.util.ExternalCommands;

/**
 * One external XSL-FO processor the user configured: a program started with an argument
 * template (see {@link ArgumentTemplate}).
 *
 * @param id             stable id (settings, scenarios and the command line may name it)
 * @param name           display name, also accepted wherever the id is
 * @param preset         the preset it started from (output parsing follows it)
 * @param executable     the program; a bare name is looked up on the {@code PATH}, empty = the
 *                       preset's program names on the {@code PATH}
 * @param arguments      the argument template
 * @param workingDir     the folder the program runs in; empty = the base folder of the run
 * @param timeoutSeconds how long a run may take (≤ 0 = the default)
 */
public record FoProcessorConfig(String id, String name, FoProcessorPreset preset, String executable,
                                String arguments, String workingDir, int timeoutSeconds) {

    /** Default time limit of one run. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public FoProcessorConfig {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        preset = preset == null ? FoProcessorPreset.CUSTOM : preset;
        name = name == null || name.isBlank() ? preset.displayName() : name.strip();
        executable = executable == null ? "" : executable.strip();
        arguments = arguments == null ? preset.arguments() : arguments.strip();
        workingDir = workingDir == null ? "" : workingDir.strip();
        timeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

    /** @return a new processor filled from a preset */
    public static FoProcessorConfig fromPreset(FoProcessorPreset preset) {
        return new FoProcessorConfig(null, preset.displayName(), preset, "", preset.arguments(), "",
                DEFAULT_TIMEOUT_SECONDS);
    }

    /** @return whether the template passes PDF metadata (title, author or subject) to the program */
    public boolean supportsMetadata() {
        Set<String> used = usedPlaceholders();
        return used.contains(ArgumentTemplate.TITLE) || used.contains(ArgumentTemplate.AUTHOR)
                || used.contains(ArgumentTemplate.SUBJECT);
    }

    /** @return whether the template can ask the program for PDF/A */
    public boolean supportsPdfA() {
        return usedPlaceholders().contains(ArgumentTemplate.PDFA);
    }

    private Set<String> usedPlaceholders() {
        try {
            return ArgumentTemplate.placeholders(arguments);
        } catch (IllegalArgumentException e) {
            return Set.of();
        }
    }

    /**
     * @return the program to start: the configured path, a configured bare name on the PATH, or
     *         the preset's program names on the PATH
     */
    public Optional<Path> resolveExecutable() {
        if (!executable.isEmpty()) {
            Path path;
            try {
                path = Path.of(executable);
            } catch (RuntimeException e) {
                return Optional.empty();
            }
            if (path.getParent() != null || path.isAbsolute()) {
                return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
            }
            return ExternalCommands.findOnPath(executable);
        }
        return ExternalCommands.findOnPath(preset.programNames().toArray(String[]::new));
    }

    /** @return whether this processor answers to the given id or name (names ignore case) */
    public boolean matches(String idOrName) {
        if (idOrName == null) {
            return false;
        }
        String key = idOrName.strip();
        return id.equals(key) || name.equalsIgnoreCase(key);
    }
}
