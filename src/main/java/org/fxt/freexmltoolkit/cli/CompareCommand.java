package org.fxt.freexmltoolkit.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.fxt.freexmltoolkit.service.FolderComparison;
import org.fxt.freexmltoolkit.service.XmlStructuralDiff;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code fxt-cli compare}: compares two XML files by structure ({@link XmlStructuralDiff}), or two
 * folders file by file ({@link FolderComparison}). Exit 0 when they match, 1 when they differ.
 */
@Command(name = "compare",
        mixinStandardHelpOptions = true,
        header = "Compare two XML files by structure, or two folders file by file.",
        footer = {
                "  # Structural comparison, elements matched by their id attribute",
                "  fxt-cli compare old.xml new.xml -k id",
                "",
                "  # Order of children does not matter; JSON result",
                "  fxt-cli compare a.xml b.xml --ignore-order -f json",
                "",
                "  # Two folders file by file",
                "  fxt-cli compare release-1/ release-2/"
        },
        description = "Compare two XML files by structure, or two folders file by file.",
        sortOptions = false)
final class CompareCommand implements Callable<Integer> {

    /** Report formats. */
    enum ReportFormat { text, json }

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Parameters(index = "0", paramLabel = "LEFT", description = "File or folder.")
    Path left;

    @Parameters(index = "1", paramLabel = "RIGHT", description = "File or folder (the same kind as LEFT).")
    Path right;

    @Option(names = "--exact-whitespace", description = "Whitespace in text counts (default: trimmed and collapsed).")
    boolean exactWhitespace;

    @Option(names = "--ignore-comments", description = "Do not compare comments.")
    boolean ignoreComments;

    @Option(names = "--ignore-order", description = "Pair elements regardless of their position.")
    boolean ignoreOrder;

    @Option(names = "--prefixes", description = "Report changed namespace prefixes.")
    boolean prefixes;

    @Option(names = {"-k", "--key"}, split = ",", paramLabel = "ATTR",
            description = "Attributes that identify an element among same-named siblings (e.g. id); repeatable.")
    List<String> keys;

    @Option(names = "--bytes", description = "Folders: compare XML files by their bytes only.")
    boolean bytesOnly;

    @Option(names = {"-f", "--format"}, paramLabel = "FORMAT", defaultValue = "text",
            description = "text (default) or json.")
    ReportFormat format;

    @Override
    public Integer call() throws IOException {
        CommandLine commandLine = spec.commandLine();
        boolean folders = Files.isDirectory(left);
        if (folders != Files.isDirectory(right)) {
            throw new CommandLine.ParameterException(commandLine, "Compare two files or two folders");
        }
        if (!folders) {
            CliSupport.requireFile(commandLine, left, "Left file");
            CliSupport.requireFile(commandLine, right, "Right file");
        }
        XmlStructuralDiff.Options xml = new XmlStructuralDiff.Options(!exactWhitespace, ignoreComments, prefixes,
                ignoreOrder, keys);
        PrintWriter out = commandLine.getOut();
        return folders ? compareFolders(out, xml) : compareFiles(commandLine, out, xml);
    }

    private int compareFiles(CommandLine commandLine, PrintWriter out, XmlStructuralDiff.Options options)
            throws IOException {
        XmlStructuralDiff.Result result = XmlStructuralDiff.compare(CliSupport.read(left), CliSupport.read(right), options);
        if (!result.compared()) {
            if (result.leftError() != null) {
                commandLine.getErr().println(left + ": error: " + result.leftError());
            }
            if (result.rightError() != null) {
                commandLine.getErr().println(right + ": error: " + result.rightError());
            }
            return ExitCodes.NOT_RUN;
        }
        if (format == ReportFormat.json) {
            JsonObject report = new JsonObject();
            report.addProperty("left", left.toString());
            report.addProperty("right", right.toString());
            report.addProperty("equivalent", result.equivalent());
            JsonArray differences = new JsonArray();
            for (XmlStructuralDiff.Difference d : result.differences()) {
                differences.add(json(d));
            }
            report.add("differences", differences);
            out.println(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
        } else {
            for (XmlStructuralDiff.Difference d : result.differences()) {
                int line = d.leftLine() > 0 ? d.leftLine() : d.rightLine();
                out.println(left + ":" + line + ": " + d.describe() + " ("
                        + (d.leftXPath().isEmpty() ? d.rightXPath() : d.leftXPath()) + ")");
            }
            out.println(result.equivalent() ? "The files are equivalent as XML."
                    : result.differences().size() + (result.differences().size() == 1 ? " difference" : " differences"));
        }
        out.flush();
        return result.equivalent() ? ExitCodes.OK : ExitCodes.FAILED;
    }

    private static JsonObject json(XmlStructuralDiff.Difference d) {
        JsonObject o = new JsonObject();
        o.addProperty("change", d.change().name().toLowerCase(java.util.Locale.ROOT));
        o.addProperty("node", d.kind().name().toLowerCase(java.util.Locale.ROOT));
        o.addProperty("name", d.name());
        o.addProperty("leftValue", d.leftValue());
        o.addProperty("rightValue", d.rightValue());
        o.addProperty("leftLine", d.leftLine());
        o.addProperty("rightLine", d.rightLine());
        o.addProperty("leftXPath", d.leftXPath());
        o.addProperty("rightXPath", d.rightXPath());
        return o;
    }

    private int compareFolders(PrintWriter out, XmlStructuralDiff.Options xml) throws IOException {
        FolderComparison.Result result = FolderComparison.compare(left, right,
                new FolderComparison.Options(!bytesOnly, xml), null, null);
        if (format == ReportFormat.json) {
            JsonObject report = new JsonObject();
            report.addProperty("left", left.toString());
            report.addProperty("right", right.toString());
            report.addProperty("same", result.same());
            JsonArray files = new JsonArray();
            for (FolderComparison.Entry e : result.entries()) {
                JsonObject o = new JsonObject();
                o.addProperty("path", e.relativePath());
                o.addProperty("status", e.status().name().toLowerCase(java.util.Locale.ROOT));
                if (e.xmlDifferences() >= 0) {
                    o.addProperty("xmlDifferences", e.xmlDifferences());
                }
                if (!e.note().isEmpty()) {
                    o.addProperty("note", e.note());
                }
                files.add(o);
            }
            report.add("files", files);
            out.println(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
        } else {
            for (FolderComparison.Entry e : result.entries()) {
                if (e.status() == FolderComparison.Status.IDENTICAL || e.status() == FolderComparison.Status.EQUIVALENT) {
                    continue;
                }
                String status = switch (e.status()) {
                    case DIFFERENT -> "differs";
                    case ONLY_LEFT -> "only left";
                    case ONLY_RIGHT -> "only right";
                    default -> e.status().label();
                };
                out.println(String.format("%-10s %s%s", status, e.relativePath(),
                        e.xmlDifferences() > 0 ? " (" + e.xmlDifferences() + " XML differences)"
                                : e.note().isEmpty() ? "" : " (" + e.note() + ")"));
            }
            out.println(Arrays.stream(FolderComparison.Status.values())
                    .map(s -> result.count(s) + " " + s.label().toLowerCase(java.util.Locale.ROOT))
                    .reduce((a, b) -> a + ", " + b).orElse("") + (result.same() ? " - the folders match." : ""));
        }
        out.flush();
        return result.same() ? ExitCodes.OK : ExitCodes.FAILED;
    }
}
