package org.fxt.freexmltoolkit.cli;

import java.io.PrintWriter;

import org.fxt.freexmltoolkit.util.VersionUtil;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Entry point of {@code fxt-cli}, the command-line interface for build servers and scripts:
 * validation, transformations, XSD documentation and PDF output without the user interface.
 *
 * <p>Exit codes are listed in {@link ExitCodes}. Log output goes to standard error so that
 * standard output carries only results and reports. No telemetry is sent from the command line.
 */
@Command(name = "fxt-cli",
        mixinStandardHelpOptions = true,
        versionProvider = FxtCli.Version.class,
        description = "FreeXmlToolkit on the command line: validate, transform, compare, merge, document schemas, convert, render PDF.",
        subcommands = {
                ValidateCommand.class,
                TransformCommand.class,
                XsdDocCommand.class,
                ConvertCommand.class,
                CompareCommand.class,
                MergeCommand.class,
                FopCommand.class,
                CommandLine.HelpCommand.class
        },
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:Success.",
                "1:Problems found (invalid documents) or the processing failed.",
                "2:Invalid command line or missing input file.",
                "3:A validation could not run (schema or Schematron does not load, file unreadable)."
        })
public final class FxtCli implements Runnable {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-q", "--quiet"}, scope = CommandLine.ScopeType.INHERIT,
            description = "Print nothing but errors and requested reports.")
    boolean quiet;

    @Option(names = "--verbose", scope = CommandLine.ScopeType.INHERIT,
            description = "Log details of the processing to standard error.")
    void setVerbose(boolean verbose) {
        if (verbose) {
            Logging.verbose();
        }
    }

    /** Prints the usage when the command is called without a subcommand. */
    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /**
     * Runs the command line and exits the JVM with its exit code (library threads must not keep
     * the process alive).
     */
    public static void main(String[] args) {
        Logging.configure();
        org.fxt.freexmltoolkit.service.AdminPolicy.install();
        System.exit(execute(args));
    }

    /**
     * Runs the command line without exiting; used by {@link #main} and the tests.
     *
     * @return the exit code
     */
    public static int execute(String... args) {
        return newCommandLine(new PrintWriter(System.out, true), new PrintWriter(System.err, true)).execute(args);
    }

    /** A configured command line writing to the given streams. */
    static CommandLine newCommandLine(PrintWriter out, PrintWriter err) {
        CommandLine commandLine = new CommandLine(new FxtCli());
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setCaseInsensitiveEnumValuesAllowed(true);
        // A usage error prints its message and a hint, not the whole help text.
        commandLine.setParameterExceptionHandler((exception, arguments) -> {
            CommandLine cmd = exception.getCommandLine();
            cmd.getErr().println("fxt-cli: " + exception.getMessage());
            cmd.getErr().println("Try '" + cmd.getCommandSpec().qualifiedName() + " --help' for more information.");
            return ExitCodes.USAGE;
        });
        commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> {
            cmd.getErr().println("fxt-cli: " + messageOf(exception));
            return ExitCodes.FAILED;
        });
        return commandLine;
    }

    static String messageOf(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    /** Version text for {@code --version}. */
    static final class Version implements IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[]{"FreeXmlToolkit " + VersionUtil.getVersion()};
        }
    }
}
