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

    /**
     * Runs a scenario for one file: the file fills {@value FxtProject#VAR_FILE} and an empty input,
     * {@value FxtProject#VAR_NAME} / {@value FxtProject#VAR_DIR} name the output after it.
     */
    public static Result run(FxtProject project, FxtProject.Scenario scenario, Path file) {
        return run(project, file == null ? scenario : project.scenarioForFile(scenario, file));
    }

    /** One file's outcome of {@link #runAll}. */
    public record FileResult(Path file, Result result) {
    }

    /**
     * Runs a scenario for several files, one after the other (on the calling thread).
     *
     * @param progress  called with the number of files done (may be {@code null})
     * @param cancelled polled before each file (may be {@code null}); a cancelled run returns the results so far
     */
    public static java.util.List<FileResult> runAll(FxtProject project, FxtProject.Scenario scenario,
                                                    java.util.List<Path> files,
                                                    java.util.function.IntConsumer progress,
                                                    java.util.function.BooleanSupplier cancelled) {
        java.util.List<FileResult> results = new java.util.ArrayList<>();
        java.util.Set<Path> written = new java.util.HashSet<>();
        for (Path file : files) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                break;
            }
            results.add(new FileResult(file, runOnce(project, scenario, file, written)));
            if (progress != null) {
                progress.accept(results.size());
            }
        }
        return results;
    }

    /**
     * Runs a scenario for one file of a series: a second file that would overwrite the output an
     * earlier one wrote (no {@code ${name}} in the output) fails instead.
     *
     * @param written the outputs written so far in this series; the new one is added
     */
    public static Result runOnce(FxtProject project, FxtProject.Scenario scenario, Path file, java.util.Set<Path> written) {
        FxtProject.Scenario prepared = project.scenarioForFile(scenario, file);
        if (prepared.output != null && !prepared.output.isBlank() && !written.add(project.resolve(prepared.output))) {
            return new Result(null, null, null, "The output " + prepared.output
                    + " was already written for another file; use ${name} or ${dir} in the scenario's output.");
        }
        return run(project, prepared);
    }

    /** Runs a scenario (on the calling thread). */
    public static Result run(FxtProject project, FxtProject.Scenario scenario) {
        if (scenario.stylesheet == null || scenario.stylesheet.isBlank()) {
            return new Result(null, null, null, "The scenario names no stylesheet, query or pipeline.");
        }
        if (scenario.input != null && scenario.input.contains(FxtProject.VAR_FILE)) {
            return new Result(null, null, null, "The scenario runs for a file (" + FxtProject.VAR_FILE
                    + "): run it for a file of the project.");
        }
        if (scenario.isPdf()) {
            return runPdf(project, scenario);
        }
        Path code = project.resolve(scenario.stylesheet);
        Path input = scenario.input == null || scenario.input.isBlank() ? null : project.resolve(scenario.input);
        Language language = languageOf(scenario.stylesheet);
        try {
            Path target = scenario.output == null || scenario.output.isBlank() ? null
                    : project.resolveWritable(scenario.output);
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
            if (target != null) {
                written = target;
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

    /** XSLT to XSL-FO with the scenario's parameters, then FOP renders the PDF to the output file. */
    private static Result runPdf(FxtProject project, FxtProject.Scenario scenario) {
        if (languageOf(scenario.stylesheet) != Language.XSLT) {
            return new Result(null, null, null, "A PDF scenario needs an XSLT stylesheet that produces XSL-FO.");
        }
        if (scenario.output == null || scenario.output.isBlank()) {
            return new Result(null, null, null, "A PDF scenario needs an output file.");
        }
        if (scenario.input == null || scenario.input.isBlank()) {
            return new Result(null, null, null, "An XSLT scenario needs an input document.");
        }
        try {
            Path target = project.resolveWritable(scenario.output);
            Path code = project.resolve(scenario.stylesheet);
            Path input = project.resolve(scenario.input);
            if (!Files.isRegularFile(code)) {
                return new Result(null, null, null, "Not found: " + code);
            }
            if (!Files.isRegularFile(input)) {
                return new Result(null, null, null, "Input not found: " + input);
            }
            Map<String, Object> parameters = new LinkedHashMap<>(scenario.parameters == null ? Map.of() : scenario.parameters);
            XsltTransformationResult fo = TransformRunner.xsltTransformResult(
                    Files.readString(input, StandardCharsets.UTF_8), input.toUri().toString(),
                    Files.readString(code, StandardCharsets.UTF_8), code.toUri().toString(), parameters, OutputFormat.XML);
            if (!fo.isSuccess()) {
                return new Result(null, null, null, fo.getErrorMessage());
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FopRunner.FopResult pdf = FopRunner.renderFo(fo.getOutputContent(), target.toFile(), FopRunner.PdfOptions.none());
            if (!pdf.ok()) {
                return new Result(null, null, null, pdf.error());
            }
            return new Result("PDF written to " + target, null, target, null);
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
