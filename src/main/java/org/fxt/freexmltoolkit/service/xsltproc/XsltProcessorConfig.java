package org.fxt.freexmltoolkit.service.xsltproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.fxt.freexmltoolkit.service.external.CommandTemplate;
import org.fxt.freexmltoolkit.util.ExternalCommands;

/**
 * An external XSLT processor the user configured (Settings → XSLT Processors).
 *
 * @param id                 stable id (scenarios and the CLI may also use the name)
 * @param name               shown in the panels
 * @param preset             where the configuration started from
 * @param executable         program path or name on the PATH; empty = the preset's program names on the PATH
 * @param arguments          the argument template ({@link #TEMPLATE} names)
 * @param parameterTemplate  how one stylesheet parameter is passed ({@code {name}}, {@code {value}})
 * @param workingDir         the folder the program runs in; empty = the stylesheet's folder
 * @param timeoutSeconds     how long a run may take (≤ 0 = the default)
 */
public record XsltProcessorConfig(String id, String name, XsltProcessorPreset preset, String executable,
                                  String arguments, String parameterTemplate, String workingDir, int timeoutSeconds) {

    /** The XML input. */
    public static final String XML = "xml";
    /** The stylesheet. */
    public static final String XSL = "xsl";
    /** The result file to write. */
    public static final String OUT = "out";
    /** The folder relative references resolve against (the stylesheet's). */
    public static final String BASE_DIR = "baseDir";
    /** The stylesheet parameters, each expanded with the parameter template (several arguments). */
    public static final String PARAMS = "params";

    /** The placeholders of the argument template, in the order the dialog lists them. */
    public static final CommandTemplate TEMPLATE = new CommandTemplate(List.of(XML, XSL, OUT, BASE_DIR, PARAMS));

    /** Default time limit of one run. */
    public static final int DEFAULT_TIMEOUT_SECONDS = 300;

    public XsltProcessorConfig {
        id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id.strip();
        preset = preset == null ? XsltProcessorPreset.CUSTOM : preset;
        name = name == null || name.isBlank() ? preset.displayName() : name.strip();
        executable = executable == null ? "" : executable.strip();
        arguments = arguments == null ? preset.arguments() : arguments.strip();
        parameterTemplate = parameterTemplate == null ? preset.parameters() : parameterTemplate.strip();
        workingDir = workingDir == null ? "" : workingDir.strip();
        timeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

    /** @return a new processor filled from a preset */
    public static XsltProcessorConfig fromPreset(XsltProcessorPreset preset) {
        return new XsltProcessorConfig(null, preset.displayName(), preset, "", preset.arguments(), preset.parameters(),
                "", DEFAULT_TIMEOUT_SECONDS);
    }

    /** @return {@code null} when the argument and parameter templates are valid, else the problem in plain words */
    public String validate() {
        try {
            Set<String> used = TEMPLATE.placeholders(arguments);
            if (!used.contains(XSL)) {
                return "The arguments must contain ${xsl}, the stylesheet.";
            }
            if (!used.contains(OUT)) {
                return "The arguments must contain ${out}, the result file to write.";
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return parameterTemplate.isEmpty() ? null : CommandTemplate.validateParameters(parameterTemplate);
    }

    /** @return whether the template passes stylesheet parameters to the program */
    public boolean supportsParameters() {
        try {
            return TEMPLATE.placeholders(arguments).contains(PARAMS) && !parameterTemplate.isEmpty();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * @return the program to start: the configured path, a configured bare name on the PATH, or the
     *         preset's program ({@link XsltProcessorPreset#findProgram()}: the application's {@code java} for the
     *         Saxon presets, else the program names on the PATH)
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
        return preset.findProgram();
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
