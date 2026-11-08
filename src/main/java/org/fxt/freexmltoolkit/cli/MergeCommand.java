package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.service.ThreeWayMerge;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli merge}: three-way merge of a base and two changed versions ({@link ThreeWayMerge}),
 * with Git's conflict markers. Exit 0 without conflicts, 1 with conflicts. Fits Git's merge driver
 * interface: {@code fxt-cli merge %O %A %B -o %A}.
 */
@Command(name = "merge",
        description = "Three-way merge of BASE, LEFT and RIGHT; conflicts get Git's markers.",
        sortOptions = false)
final class MergeCommand implements Callable<Integer> {

    @Spec
    CommandLine.Model.CommandSpec spec;

    @ParentCommand
    FxtCli parent;

    @Parameters(index = "0", paramLabel = "BASE", description = "The common ancestor.")
    Path base;

    @Parameters(index = "1", paramLabel = "LEFT", description = "One changed version (mine).")
    Path left;

    @Parameters(index = "2", paramLabel = "RIGHT", description = "The other changed version (theirs).")
    Path right;

    @Option(names = {"-o", "--output"}, paramLabel = "FILE",
            description = "Result file (may be LEFT). Without it the result goes to standard output.")
    Path output;

    @Option(names = "--exact-whitespace",
            description = "Changes that differ only in whitespace are a conflict (default for XML: not).")
    boolean exactWhitespace;

    @Option(names = "--no-base", description = "Leave the base part (|||||||) out of conflict blocks.")
    boolean noBase;

    @Option(names = {"-L", "--label"}, paramLabel = "NAME", arity = "1",
            description = "Names for the conflict markers: up to three, in the order LEFT, BASE, RIGHT (as git merge-file).")
    java.util.List<String> labels;

    @Override
    public Integer call() throws IOException {
        CommandLine commandLine = spec.commandLine();
        CliSupport.requireFile(commandLine, base, "Base");
        CliSupport.requireFile(commandLine, left, "Left");
        CliSupport.requireFile(commandLine, right, "Right");
        String baseText = CliSupport.read(base);
        String leftText = CliSupport.read(left);
        String rightText = CliSupport.read(right);
        boolean xml = XmlStructuralDiff.looksLikeXml(baseText) || XmlStructuralDiff.looksLikeXml(leftText);
        ThreeWayMerge.Result result = ThreeWayMerge.merge(baseText, leftText, rightText, xml && !exactWhitespace);
        String merged = result.text(label(0, left), label(1, base), label(2, right), !noBase);
        if (output == null) {
            commandLine.getOut().print(merged);
            commandLine.getOut().flush();
        } else {
            CliSupport.write(output, merged);
        }
        boolean quiet = parent != null && parent.quiet;
        if (result.conflicts() > 0) {
            commandLine.getErr().println(result.conflicts() + (result.conflicts() == 1 ? " conflict" : " conflicts")
                    + (output != null ? " in " + output : ""));
            return ExitCodes.FAILED;
        }
        if (xml) {
            String error = XmlStructuralDiff.wellFormednessError(merged);
            if (error != null) {
                commandLine.getErr().println("warning: the merged XML is not well-formed: " + error);
            }
        }
        if (!quiet && output != null) {
            commandLine.getErr().println("Merged without conflicts: " + output);
        }
        return ExitCodes.OK;
    }

    private String label(int index, Path file) {
        if (labels != null && labels.size() > index) {
            return labels.get(index);
        }
        return file.getFileName() == null ? file.toString() : file.getFileName().toString();
    }
}
