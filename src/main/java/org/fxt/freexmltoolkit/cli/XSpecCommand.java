package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.service.XSpecRunner;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli xspec}: runs XSpec tests for XSLT, XQuery and Schematron ({@link XSpecRunner}).
 * Exit 0 when all pass, 1 when a test fails, 3 when the tests cannot run.
 */
@Command(name = "xspec", description = "Run XSpec tests (XSLT, XQuery, Schematron).", sortOptions = false)
final class XSpecCommand implements Callable<Integer> {

    /** Report formats written with {@code --report}. */
    enum ReportFormat { junit, html, xml }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(arity = "1..*", paramLabel = "XSPEC", description = "XSpec files (x:description).")
    List<Path> files;

    @Option(names = {"-r", "--report"}, paramLabel = "FILE",
            description = "Write a report (one XSpec file): JUnit XML, XSpec's HTML report or its XML report.")
    Path report;

    @Option(names = {"-f", "--report-format"}, paramLabel = "FORMAT", defaultValue = "junit",
            description = "junit (default), html or xml.")
    ReportFormat format;

    @Override
    public Integer call() throws Exception {
        CommandLine commandLine = spec.commandLine();
        if (report != null && files.size() > 1) {
            throw new CommandLine.ParameterException(commandLine, "--report needs a single XSpec file");
        }
        for (Path file : files) {
            CliSupport.requireFile(commandLine, file, "XSpec file");
        }
        CliSupport.initServices();
        PrintWriter out = commandLine.getOut();
        XSpecRunner runner = new XSpecRunner();
        int exit = ExitCodes.OK;
        for (Path file : files) {
            XSpecRunner.Result result = runner.run(CliSupport.read(file), file);
            if (result.error() != null) {
                commandLine.getErr().println(file + ": error: " + result.error());
                exit = Math.max(exit, ExitCodes.NOT_RUN);
                continue;
            }
            boolean quiet = parent != null && parent.quiet;
            for (XSpecRunner.Scenario scenario : result.scenarios()) {
                print(out, file, scenario, "", quiet);
            }
            out.println(file + ": " + result.summary());
            if (!result.passed()) {
                exit = Math.max(exit, ExitCodes.FAILED);
            }
            if (report != null) {
                String text = switch (format) {
                    case junit -> runner.junit(result);
                    case html -> runner.html(result);
                    case xml -> result.reportXml();
                };
                CliSupport.write(report, text);
                if (!quiet) {
                    commandLine.getErr().println("Wrote " + report);
                }
            }
        }
        out.flush();
        return exit;
    }

    /** Failed tests always; passed and pending ones unless quiet. */
    private static void print(PrintWriter out, Path file, XSpecRunner.Scenario scenario, String path, boolean quiet) {
        String label = path.isEmpty() ? scenario.label() : path + " / " + scenario.label();
        for (XSpecRunner.Test test : scenario.tests()) {
            if (test.status() == XSpecRunner.Status.FAILED) {
                out.println("FAILED   " + label + " / " + test.label());
                out.println("         expected: " + oneLine(test.expected()));
                out.println("         actual:   " + oneLine(test.actual()));
            } else if (!quiet) {
                out.println((test.status() == XSpecRunner.Status.PASSED ? "passed   " : "pending  ") + label + " / "
                        + test.label());
            }
        }
        for (XSpecRunner.Scenario child : scenario.scenarios()) {
            print(out, file, child, label, quiet);
        }
    }

    private static String oneLine(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").strip();
        return value.length() > 200 ? value.substring(0, 200) + "…" : value;
    }
}
