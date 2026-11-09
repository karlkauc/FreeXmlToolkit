package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.controls.shell.editor.ScenarioCheck;
import org.fxt.freexmltoolkit.controls.shell.editor.ScenarioRunner;
import org.fxt.freexmltoolkit.service.project.FxtProject;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli scenario}: runs the transformation scenarios of a project file ({@code .fxtproj}) —
 * the ones saved from the Transform panel — with {@link ScenarioRunner}. With {@code --input} a
 * scenario runs once per file ({@code ${file}}, {@code ${name}}); without scenario names each file
 * runs the scenario the project binds to it.
 */
@Command(name = "scenario",
        mixinStandardHelpOptions = true,
        header = "Run the transformation scenarios of a project file (.fxtproj).",
        footer = {
                "  # List the scenarios, then run all of them",
                "  fxt-cli scenario shop.fxtproj --list",
                "  fxt-cli scenario shop.fxtproj",
                "",
                "  # One scenario for the given files",
                "  fxt-cli scenario shop.fxtproj \"Invoice HTML\" -i orders/",
                "",
                "  # Regression check against the expected results, JUnit report for CI",
                "  fxt-cli scenario shop.fxtproj --check -f junit -r scenarios.xml",
                "",
                "  # The new results are right: make them the expected ones",
                "  fxt-cli scenario shop.fxtproj \"Invoice HTML\" --accept"
        },
        description = {
                "Run the transformation scenarios of a project file (.fxtproj).",
                "--check compares each result with the scenario's expected file (XML by structure, with the "
                        + "scenario's compare settings; text line by line): exit 0 when all match, 1 when one "
                        + "differs or fails, 3 when an expected file is missing."
        },
        sortOptions = false)
final class ScenarioCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(index = "0", paramLabel = "PROJECT", description = "The project file.")
    Path projectFile;

    @Parameters(index = "1..*", arity = "0..*", paramLabel = "NAME",
            description = "Scenarios to run (default: all, in the order of the project file).")
    List<String> names = new ArrayList<>();

    @Option(names = {"-l", "--list"}, description = "List the scenarios instead of running them.")
    boolean list;

    @Option(names = {"-i", "--input"}, arity = "1..*", paramLabel = "FILE",
            description = "Run for these files or folders (folders are searched recursively, see --include): "
                    + "the scenario runs once per file. Without NAME each file runs the scenario the project binds to it.")
    List<Path> inputs = new ArrayList<>();

    @Option(names = "--include", split = ",", paramLabel = "GLOB", defaultValue = "*.xml",
            description = "File names to use inside --input folders (default: ${DEFAULT-VALUE}).")
    List<String> include;

    @Option(names = "--check", description = "Compare each result with the scenario's expected file.")
    boolean check;

    @Option(names = "--accept",
            description = "Like --check, then write each differing or missing expected file from the result.")
    boolean accept;

    /** Report formats of a check. */
    enum ReportFormat { text, junit, json }

    @Option(names = {"-f", "--report-format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "Check report: text, junit (JUnit XML) or json (default: ${DEFAULT-VALUE}).")
    ReportFormat reportFormat;

    @Option(names = {"-r", "--report"}, paramLabel = "FILE",
            description = "Write the check report to FILE (the summary still goes to standard output).")
    Path reportFile;

    @Override
    public Integer call() {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, projectFile, "Project");
        FxtProject project;
        try {
            project = FxtProject.load(projectFile);
        } catch (IOException e) {
            throw new CommandLine.ParameterException(commandLine, e.getMessage(), e, null, null);
        }
        var out = commandLine.getOut(); // NOPMD - picocli's stream, owned by CommandLine
        if (list) {
            for (FxtProject.Scenario scenario : project.scenarios()) {
                out.println(scenario.name + "\t" + scenario.stylesheet
                        + (scenario.input != null ? " < " + scenario.input : "")
                        + (scenario.output != null ? " > " + scenario.output : "")
                        + (scenario.hasExpected() ? " = " + scenario.expected : ""));
            }
            out.flush();
            return ExitCodes.OK;
        }
        if (check || accept) {
            return checkScenarios(commandLine, project, out);
        }
        if (reportFile != null || reportFormat != ReportFormat.text) {
            throw new CommandLine.ParameterException(commandLine, "--report and --report-format need --check");
        }
        if (!inputs.isEmpty()) {
            return runForFiles(commandLine, project, out);
        }
        List<FxtProject.Scenario> chosen = new ArrayList<>();
        if (names.isEmpty()) {
            chosen.addAll(project.scenarios());
        } else {
            for (String name : names) {
                chosen.add(project.scenario(name).orElseThrow(() -> new CommandLine.ParameterException(commandLine,
                        "No scenario \"" + name + "\" in " + projectFile)));
            }
        }
        if (chosen.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine, projectFile + " has no scenarios");
        }
        CliSupport.initServices();
        boolean quiet = parent != null && parent.quiet;
        int exit = ExitCodes.OK;
        for (FxtProject.Scenario scenario : chosen) {
            ScenarioRunner.Result result = ScenarioRunner.run(project, scenario);
            if (!result.ok()) {
                commandLine.getErr().println(scenario.name + ": error: " + result.error());
                exit = ExitCodes.FAILED;
            } else if (result.written() != null) {
                if (!quiet) {
                    commandLine.getErr().println(scenario.name + ": wrote " + result.written());
                }
            } else {
                out.print(result.text());
                out.flush();
            }
        }
        return exit;
    }

    /** {@code --input}: every named scenario (or each file's bound one) once per file. */
    private int runForFiles(CommandLine commandLine, FxtProject project, java.io.PrintWriter out) {
        List<Path> files = CliSupport.expand(commandLine, inputs, include);
        if (files.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "No input files (folders are searched for " + String.join(", ", include) + ")");
        }
        List<FxtProject.Scenario> named = new ArrayList<>();
        for (String name : names) {
            named.add(project.scenario(name).orElseThrow(() -> new CommandLine.ParameterException(commandLine,
                    "No scenario \"" + name + "\" in " + projectFile)));
        }
        CliSupport.initServices();
        boolean quiet = parent != null && parent.quiet;
        int exit = ExitCodes.OK;
        java.util.Set<Path> written = new java.util.HashSet<>();
        for (Path file : files) {
            List<FxtProject.Scenario> scenarios = named.isEmpty()
                    ? project.scenarioFor(file).map(List::of).orElse(List.of()) : named;
            if (scenarios.isEmpty()) {
                commandLine.getErr().println(file + ": no scenario is bound to this file in the project");
                exit = ExitCodes.FAILED;
                continue;
            }
            for (FxtProject.Scenario scenario : scenarios) {
                ScenarioRunner.Result result = ScenarioRunner.runOnce(project, scenario, file, written);
                if (!result.ok()) {
                    commandLine.getErr().println(scenario.name + " (" + file + "): error: " + result.error());
                    exit = ExitCodes.FAILED;
                } else if (result.written() != null) {
                    if (!quiet) {
                        commandLine.getErr().println(scenario.name + " (" + file + "): wrote " + result.written());
                    }
                } else {
                    out.print(result.text());
                    out.flush();
                }
            }
        }
        return exit;
    }

    /** A scenario to check, for one file or for none. */
    private record Job(FxtProject.Scenario scenario, Path file) {
    }

    /** {@code --check} / {@code --accept}: the regression check of the chosen scenarios. */
    private int checkScenarios(CommandLine commandLine, FxtProject project, java.io.PrintWriter out) {
        List<Job> jobs = new ArrayList<>();
        int exit = ExitCodes.OK;
        if (!inputs.isEmpty()) {
            List<Path> files = CliSupport.expand(commandLine, inputs, include);
            if (files.isEmpty()) {
                throw new CommandLine.ParameterException(commandLine,
                        "No input files (folders are searched for " + String.join(", ", include) + ")");
            }
            List<FxtProject.Scenario> named = scenariosByName(commandLine, project);
            for (Path file : files) {
                List<FxtProject.Scenario> scenarios = named.isEmpty()
                        ? project.scenarioFor(file).map(List::of).orElse(List.of()) : named;
                if (scenarios.isEmpty()) {
                    commandLine.getErr().println(file + ": no scenario is bound to this file in the project");
                    exit = ExitCodes.FAILED;
                }
                scenarios.forEach(scenario -> jobs.add(new Job(scenario, file)));
            }
        } else {
            List<FxtProject.Scenario> chosen = names.isEmpty() ? project.scenarios() : scenariosByName(commandLine, project);
            for (FxtProject.Scenario scenario : chosen) {
                if (!scenario.hasExpected() && names.isEmpty()) {
                    continue;
                }
                // a scenario for ${file}: each file the project binds to it
                List<Path> bound = scenario.takesFile() ? project.filesForScenario(scenario.name, 10_000) : List.of();
                if (bound.isEmpty()) {
                    jobs.add(new Job(scenario, null));
                } else {
                    bound.forEach(file -> jobs.add(new Job(scenario, file)));
                }
            }
            if (jobs.isEmpty()) {
                throw new CommandLine.ParameterException(commandLine,
                        projectFile + " has no scenario with an expected result");
            }
        }
        CliSupport.initServices();
        JUnitReport junit = new JUnitReport("fxt-cli scenario --check");
        com.google.gson.JsonArray json = new com.google.gson.JsonArray();
        int passed = 0;
        int failed = 0;
        int missing = 0;
        for (Job job : jobs) {
            long t0 = System.nanoTime();
            ScenarioCheck.Result result = ScenarioCheck.check(project, job.scenario(), job.file());
            long millis = (System.nanoTime() - t0) / 1_000_000;
            String label = job.scenario().name + (job.file() == null ? "" : " (" + job.file() + ")");
            ScenarioCheck.Status status = result.status();
            String accepted = null;
            if (accept && result.actual() != null
                    && (status == ScenarioCheck.Status.FAILED || status == ScenarioCheck.Status.NO_EXPECTED)) {
                try {
                    accepted = String.valueOf(ScenarioCheck.accept(project, job.scenario(), job.file(), result.actual()));
                    status = ScenarioCheck.Status.PASSED;
                } catch (IOException e) {
                    commandLine.getErr().println(label + ": cannot accept: " + e.getMessage());
                }
            }
            String details = differences(result);
            switch (status) {
                case PASSED -> {
                    passed++;
                    out.println((accepted != null ? "accepted " : "passed   ") + label
                            + (accepted != null ? " -> " + accepted : ""));
                }
                case FAILED -> {
                    failed++;
                    out.println("FAILED   " + label + ": " + result.summary());
                    out.print(details);
                }
                case NO_EXPECTED -> {
                    missing++;
                    out.println("MISSING  " + label + ": " + result.message());
                }
                case ERROR -> {
                    failed++;
                    out.println("ERROR    " + label + ": " + result.message());
                }
            }
            JUnitReport.Outcome outcome = switch (status) {
                case PASSED -> JUnitReport.Outcome.PASSED;
                case FAILED -> JUnitReport.Outcome.FAILED;
                case NO_EXPECTED -> JUnitReport.Outcome.SKIPPED;
                case ERROR -> JUnitReport.Outcome.ERROR;
            };
            junit.add(new JUnitReport.TestCase(job.scenario().name,
                    job.file() == null ? job.scenario().name : String.valueOf(job.file()), outcome,
                    status == ScenarioCheck.Status.PASSED ? null : result.summary(), details, millis));
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("scenario", job.scenario().name);
            if (job.file() != null) {
                o.addProperty("file", job.file().toString());
            }
            o.addProperty("status", status.name().toLowerCase(java.util.Locale.ROOT));
            if (result.expected() != null) {
                o.addProperty("expected", result.expected().toString());
            }
            if (status != ScenarioCheck.Status.PASSED && result.summary() != null) {
                o.addProperty("message", result.summary());
            }
            com.google.gson.JsonArray differences = new com.google.gson.JsonArray();
            if (status == ScenarioCheck.Status.FAILED) {
                for (var d : result.differences()) {
                    com.google.gson.JsonObject diff = new com.google.gson.JsonObject();
                    diff.addProperty("description", d.describe());
                    diff.addProperty("expectedLine", d.leftLine());
                    diff.addProperty("actualLine", d.rightLine());
                    diff.addProperty("xpath", d.leftXPath().isEmpty() ? d.rightXPath() : d.leftXPath());
                    diff.addProperty("similar", d.similar());
                    differences.add(diff);
                }
            }
            o.add("differences", differences);
            json.add(o);
        }
        out.println(passed + " passed, " + failed + " failed, " + missing + " without expected result");
        out.flush();
        if (reportFormat != ReportFormat.text) {
            String report = reportFormat == ReportFormat.junit ? junit.toXml()
                    : new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n";
            try {
                if (reportFile != null) {
                    CliSupport.write(reportFile, report);
                } else {
                    out.print(report);
                    out.flush();
                }
            } catch (IOException e) {
                commandLine.getErr().println("fxt-cli: cannot write " + reportFile + ": " + e.getMessage());
                return ExitCodes.FAILED;
            }
        }
        if (failed > 0 || exit != ExitCodes.OK) {
            return ExitCodes.FAILED;
        }
        return missing > 0 ? ExitCodes.NOT_RUN : ExitCodes.OK;
    }

    private List<FxtProject.Scenario> scenariosByName(CommandLine commandLine, FxtProject project) {
        List<FxtProject.Scenario> named = new ArrayList<>();
        for (String name : names) {
            named.add(project.scenario(name).orElseThrow(() -> new CommandLine.ParameterException(commandLine,
                    "No scenario \"" + name + "\" in " + projectFile)));
        }
        return named;
    }

    /** The differences of a failed check, one indented line each (expected line, description, location). */
    private static String differences(ScenarioCheck.Result result) {
        if (result.status() != ScenarioCheck.Status.FAILED) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (var d : result.differences()) {
            String where = d.leftXPath().isEmpty() ? d.rightXPath() : d.leftXPath();
            text.append("    ").append(d.leftLine() > 0 ? "line " + d.leftLine() + ": " : "")
                    .append(d.describe()).append(d.similar() ? " [similar]" : "")
                    .append(where.isEmpty() ? "" : " (" + where + ")").append('\n');
        }
        return text.toString();
    }
}
