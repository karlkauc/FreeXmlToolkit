package org.fxt.freexmltoolkit.controls.shell.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.fxt.freexmltoolkit.service.XsltTransformationEngine;
import org.fxt.freexmltoolkit.service.XsltTransformationEngine.OutputFormat;
import org.fxt.freexmltoolkit.service.XsltTransformationResult;
import org.fxt.freexmltoolkit.service.project.FxtProject;

/**
 * Runs a transformation scenario of a project ({@link FxtProject.Scenario}): XSLT, XQuery or XProc
 * by the stylesheet's extension, with the scenario's input, parameters and output format; the result
 * is written to the scenario's output file when it names one. UI-free; used by the Transform panel,
 * the Explorer's project section and {@code fxt-cli scenario}.
 */
public final class ScenarioRunner {

    /**
     * The outcome.
     *
     * @param text    the result (also when written to a file), or {@code null} on failure
     * @param format  the output format
     * @param written the file the result was written to, or {@code null}
     * @param error   why it failed, or {@code null}
     */
    public record Result(String text, OutputFormat format, Path written, String error) {

        public boolean ok() {
            return error == null;
        }
    }

    /** The kind of a scenario's code. */
    enum Language { XSLT, XQUERY, XPROC }

    private ScenarioRunner() {
    }

    static Language languageOf(String stylesheet) {
        String name = stylesheet == null ? "" : stylesheet.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xq") || name.endsWith(".xquery") || name.endsWith(".xqy") || name.endsWith(".xql")
                || name.endsWith(".xqm")) {
            return Language.XQUERY;
        }
        if (name.endsWith(".xpl") || name.endsWith(".xproc")) {
            return Language.XPROC;
        }
        return Language.XSLT;
    }

    /** Runs a scenario (on the calling thread). */
    public static Result run(FxtProject project, FxtProject.Scenario scenario) {
        if (scenario.stylesheet == null || scenario.stylesheet.isBlank()) {
            return new Result(null, null, null, "The scenario names no stylesheet, query or pipeline.");
        }
        Path code = project.resolve(scenario.stylesheet);
        Path input = scenario.input == null || scenario.input.isBlank() ? null : project.resolve(scenario.input);
        Language language = languageOf(scenario.stylesheet);
        try {
            if (!Files.isRegularFile(code)) {
                return new Result(null, null, null, "Not found: " + code);
            }
            if (input != null && !Files.isRegularFile(input)) {
                return new Result(null, null, null, "Input not found: " + input);
            }
            if (language == Language.XSLT && input == null) {
                return new Result(null, null, null, "An XSLT scenario needs an input document.");
            }
            String codeText = Files.readString(code, StandardCharsets.UTF_8);
            String xml = input == null ? null : Files.readString(input, StandardCharsets.UTF_8);
            Map<String, Object> parameters = new LinkedHashMap<>(scenario.parameters == null ? Map.of() : scenario.parameters);
            OutputFormat format = format(scenario.outputFormat);
            String text;
            String error = null;
            switch (language) {
                case XSLT -> {
                    if (format == null) {
                        format = TransformRunner.detectXsltOutputFormat(codeText);
                    }
                    XsltTransformationResult result = TransformRunner.xsltTransformResult(xml,
                            input.toUri().toString(), codeText, code.toUri().toString(), parameters, format);
                    text = result.isSuccess() ? result.getOutputContent() : null;
                    error = result.isSuccess() ? null : result.getErrorMessage();
                }
                case XQUERY -> {
                    if (format == null) {
                        format = OutputFormat.XML;
                    }
                    XsltTransformationResult result = XsltTransformationEngine.getInstance().transformXQuery(xml,
                            input == null ? null : input.toUri().toString(), codeText, code.toUri().toString(),
                            parameters, format);
                    text = result.isSuccess() ? result.getOutputContent() : null;
                    error = result.isSuccess() ? null : result.getErrorMessage();
                }
                default -> {
                    XProcRunner.Result result = XProcRunner.runPipeline(codeText, code.toFile(), null,
                            input == null ? null : input.toFile());
                    text = result.isError() ? null : result.text();
                    error = result.isError() ? result.text().substring("ERROR:".length()).strip() : null;
                    format = result.format();
                }
            }
            if (error != null) {
                return new Result(null, format, null, error);
            }
            Path written = null;
            if (scenario.output != null && !scenario.output.isBlank()) {
                written = project.resolve(scenario.output);
                Path parent = written.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(written, text, StandardCharsets.UTF_8);
            }
            return new Result(text, format, written, null);
        } catch (IOException | RuntimeException e) {
            return new Result(null, null, null, String.valueOf(e.getMessage()));
        }
    }

    private static OutputFormat format(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return OutputFormat.valueOf(name.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
