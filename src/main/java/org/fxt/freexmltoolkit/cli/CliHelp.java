package org.fxt.freexmltoolkit.cli;

import picocli.CommandLine;
import picocli.CommandLine.Model.UsageMessageSpec;

/**
 * One layout for the help of every command ({@code fxt-cli <command> --help}): section headings,
 * the examples of a command (its {@code footer}) under "Examples:", the width of the terminal and
 * a shortened synopsis for commands with many options.
 */
final class CliHelp {

    /** Commands with more options than this show {@code [OPTIONS]} in the synopsis. */
    private static final int ABBREVIATE_ABOVE = 12;

    private CliHelp() {
    }

    /** Applies the layout to {@code commandLine} and all its subcommands. */
    static void configure(CommandLine commandLine) {
        commandLine.setUsageHelpAutoWidth(true);
        commandLine.setUsageHelpLongOptionsMaxWidth(32);
        apply(commandLine);
    }

    private static void apply(CommandLine commandLine) {
        UsageMessageSpec usage = commandLine.getCommandSpec().usageMessage();
        usage.synopsisHeading("Usage: ")
                .descriptionHeading("%nDescription:%n")
                .parameterListHeading("%nParameters:%n")
                .optionListHeading("%nOptions:%n")
                .commandListHeading("%nCommands:%n");
        if (usage.footer().length > 0 && usage.footerHeading().isEmpty()) {
            usage.footerHeading("%nExamples:%n");
        }
        if (commandLine.getCommandSpec().options().size() > ABBREVIATE_ABOVE) {
            usage.abbreviateSynopsis(true);
        }
        commandLine.getSubcommands().values().forEach(CliHelp::apply);
    }
}
