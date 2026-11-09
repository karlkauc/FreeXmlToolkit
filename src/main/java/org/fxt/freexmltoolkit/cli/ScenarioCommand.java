package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

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
                "  fxt-cli scenario shop.fxtproj \"Invoice HTML\" -i orders/"
        },
        description = "Run the transformation scenarios of a project file (.fxtproj).",
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
                        + (scenario.output != null ? " > " + scenario.output : ""));
            }
            out.flush();
            return ExitCodes.OK;
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
}
