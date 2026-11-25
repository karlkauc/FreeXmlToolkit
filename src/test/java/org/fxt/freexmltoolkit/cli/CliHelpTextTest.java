package org.fxt.freexmltoolkit.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

/**
 * picocli formats header, description and footer lines with {@code String.format}: a lone {@code %} (as in
 * "above 10 %") makes it print a warning in the middle of the help text. Every such line of every command
 * must write {@code %%} or a format specifier such as {@code %n}.
 */
class CliHelpTextTest {

    /** A {@code %} that is neither {@code %%} nor followed by a conversion picocli's texts use. */
    private static final Pattern LONE_PERCENT = Pattern.compile("(?<!%)%(?![%nsd])");

    @Test
    void helpTextsHaveNoUnescapedPercentSigns() {
        List<String> offenders = new ArrayList<>();
        collect(new CommandLine(new FxtCli()), offenders);
        assertTrue(offenders.isEmpty(), "write %% in help texts: " + offenders);
    }

    private static void collect(CommandLine commandLine, List<String> offenders) {
        CommandSpec spec = commandLine.getCommandSpec();
        List<String> lines = new ArrayList<>();
        lines.addAll(List.of(spec.usageMessage().header()));
        lines.addAll(List.of(spec.usageMessage().description()));
        lines.addAll(List.of(spec.usageMessage().footer()));
        for (String line : lines) {
            if (LONE_PERCENT.matcher(line).find()) {
                offenders.add(spec.qualifiedName() + ": " + line);
            }
        }
        commandLine.getSubcommands().values().forEach(sub -> collect(sub, offenders));
    }
}
