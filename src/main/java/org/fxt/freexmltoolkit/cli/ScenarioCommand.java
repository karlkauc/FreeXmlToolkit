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
 * the ones saved from the Transform panel — with {@link ScenarioRunner}.
 */
@Command(name = "scenario",
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
}
